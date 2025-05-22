package PaSoC

import Consts._
import chisel3._       // chisel本体
import chisel3.util._  // chisel功能


// 外设选择器
class DBusMux(val nDevices: Int = 4) extends Module {
    require(nDevices > 0 && nDevices <= 16, "设备数量必须在1-16之间")
    
    val io = IO(new Bundle {
        val bus  = new DBusPortIO  // 总线接口，作为slave接受请求
        val devs = Flipped(Vec(nDevices, new DBusPortIO))   // 可配置外设数量
    })

    // 计算需要的地址位宽
    val devBits = log2Ceil(nDevices)
    // 设备选择线：根据设备数量决定使用哪些地址位
    // 0x00000000 ~ 0x10000000 ~ 0x20000000 ~ ......
    val devSel = io.bus.addr(27 + devBits, 28)
    
    // 默认关闭所有设备使能
    for (i <- 0 until nDevices) {
        io.devs(i).valid := false.B
        io.devs(i).wen   := false.B
        io.devs(i).addrb := 0.U
        io.devs(i).addr  := 0.U
        io.devs(i).wdata := 0.U
        io.devs(i).ben   := 0.U
    }

    // 用于保存读取数据的默认值
    val defaultRdata = 0.U(32.W)
    val defaultReady = false.B
    io.bus.rdata := defaultRdata
    io.bus.ready := defaultReady

    // 根据devSel选中设备，传递bus信号
    when(devSel < nDevices.U) {
        // 只有当选择的设备在有效范围内时才进行操作
        for (i <- 0 until nDevices) {
            when(devSel === i.U) {
                io.devs(i).valid := io.bus.valid
                io.devs(i).addr  := io.bus.addr
                io.devs(i).wen   := io.bus.wen
                io.devs(i).ben   := io.bus.ben
                io.devs(i).wdata := io.bus.wdata
                io.bus.rdata     := io.devs(i).rdata
                io.bus.ready     := io.devs(i).ready
            }
        }
    }

    // 所有设备都要接收提前发出的地址
    for (i <- 0 until nDevices) {
        io.devs(i).addrb := io.bus.addrb
    }
}


// GPIO外设模块
class GPIOCtrl() extends Module {
    val io = IO(new Bundle {
        val bus  = new DBusPortIO()  // 外设总线接口
        val gpio = new GPIOPortIO()    // GPIO接口
    })

    // 使用寄存器保存GPIO输出值，复位为0
    val gpioOutReg = RegInit(0.U(GPIO_LEN.W))

    // 默认输出信号
    io.gpio.Out := gpioOutReg
    
    // 默认总线响应信号
    io.bus.rdata := 0.U
    io.bus.ready := false.B

    // 总线有效且地址匹配时的读写操作
    when(io.bus.valid) {
        // 读操作，wen = false
        when(!io.bus.wen) {
            // 根据地址返回数据
            when(io.bus.addr === GPIO_IN_ADDR) {
                // 返回输入状态，低位有效，高位补0
                io.bus.rdata := io.gpio.In.pad(WORD_LEN)  // pad用于零扩展到32位
                io.bus.ready := true.B
            } .elsewhen(io.bus.addr === GPIO_OUT_ADDR) {
                // 返回当前输出寄存器的值
                io.bus.rdata := gpioOutReg.pad(WORD_LEN)
                io.bus.ready := true.B
            } .otherwise {
                // 地址不可识别，ready任为false或可附加异常处理
                io.bus.ready := false.B
            }
        } .otherwise {  // 写操作，wen = true
            when(io.bus.addr === GPIO_OUT_ADDR) {
                // 只更新低8位有效
                gpioOutReg := io.bus.wdata(GPIO_LEN-1, 0)
                io.bus.ready := true.B
            } .otherwise {
                io.bus.ready := false.B
            }
        }
    } .otherwise {
        io.bus.ready := false.B
    }
}


// PWM外设模块，生成PWM_LEN路PWM信号
class PWMCtrl() extends Module {
    val io = IO(new Bundle {
        val bus = new DBusPortIO
        val pwm = Output(UInt(PWM_LEN.W))  // PWM输出信号
    })

    // 占空比寄存器数组，存储各通道占空比，宽度为PWM_MAX位宽的log2
    val dutyRegs = RegInit(VecInit(Seq.fill(PWM_LEN)(0.U(log2Ceil(PWM_MAX + 1).W))))

    // 计数器，循环计数PWM周期
    val counter = RegInit(0.U(log2Ceil(PWM_MAX + 1).W))
    counter := Mux(counter === PWM_MAX.U, 0.U, counter + 1.U)

    // 地址对比判断，判断访问是否在PWM外设寄存器空间内
    // 假设外设空间为 PWM_BASE_ADDR -> PWM_BASE_ADDR + 4*(PWM_LEN -1)
    val addrOffset = io.bus.addr - PWM_BASE_ADDR
    val inRange = (io.bus.addr >= PWM_BASE_ADDR) && (addrOffset < (PWM_LEN * 4).U) && ((addrOffset & 3.U) === 0.U)

    // 计算访问的索引（通道号）
    val channelIndex = (addrOffset >> 2)(log2Ceil(PWM_LEN) - 1, 0) // 右移2位，转换成word索引

    // 默认信号
    io.bus.ready := false.B
    io.bus.rdata := 0.U

    // 读写逻辑
    when(io.bus.valid && inRange) {
        io.bus.ready := true.B
        when(io.bus.wen) {
            // 写操作，写入占空比寄存器，限制最大值为PWM_MAX
            dutyRegs(channelIndex) := io.bus.wdata(log2Ceil(PWM_MAX + 1) - 1, 0).min(PWM_MAX.U)
        }.otherwise {
        // 读操作，返回寄存器值，扩展成WORD_LEN位宽
            io.bus.rdata := dutyRegs(channelIndex).pad(WORD_LEN)
        }
    }

    // PWM信号产生
    // PWM输出为：当计数器小于占空比寄存器的值时输出高电平，否则低电平
    // 产生一个 Vec[Bool] 存放各通道的高低电平
    val pwmVec = VecInit(
        (0 until PWM_LEN).map(i => counter < dutyRegs(i))
    )
    // 将 Vec[Bool] 串联成一个 UInt，高位在最左端
    io.pwm := pwmVec.asUInt
}


// UART 发送器外设
class UartTxCtrl extends Module {
    val io = IO(new Bundle {
        val bus = new DBusPortIO
        val tx  = Output(Bool())
    })

    // 计算一个波特周期对应的时钟周期数
    private val baudCntMax  = (CLOCK_FREQ / BAUD_RATE).U(32.W)
    // FIFO，深度由参数配置
    private val fifoDepth   = 32
    val fifo = Module(new Queue(UInt(8.W), fifoDepth))

    //------------------------------------------------------------
    // 总线写数据字节分解，只处理ben为0001, 0011, 1111三种情况
    //------------------------------------------------------------
    val bus_valid = io.bus.valid
    val is_write = io.bus.wen && (io.bus.addr === UART_TX_ADDR) && bus_valid
    val is_read  = !io.bus.wen && (io.bus.addr === UART_TX_ADDR) && bus_valid
    val bytes = VecInit(Seq.tabulate(4)(i => io.bus.wdata(8*i+7, 8*i)))

    // 根据ben类型确定要写入字节数和内容
    val write_num   = Wire(UInt(3.W))       // 需写入FIFO的字节数
    val write_bytes = Wire(Vec(4, UInt(8.W))) // 最多4字节，不足默认为0
    write_num      := 0.U
    write_bytes    := VecInit(Seq.fill(4)(0.U(8.W)))
    switch(io.bus.ben) {
        is("b0001".U) { // sb
            write_num := 1.U
            write_bytes(0) := bytes(0)
        }
        is("b0011".U) { // sh (低16位)
            write_num := 2.U
            write_bytes(0) := bytes(0)
            write_bytes(1) := bytes(1)
        }
        is("b1111".U) { // sw
            write_num := 4.U
            write_bytes(0) := bytes(0); write_bytes(1) := bytes(1)
            write_bytes(2) := bytes(2); write_bytes(3) := bytes(3)
        }
    }

    // 获取FIFO剩余空间，判断是否可全部写入
    val fifo_space = (fifoDepth.U - fifo.io.count)
    val can_write  = (write_num =/= 0.U) && (fifo_space >= write_num)

    //------------------------------------------------------------
    // 写入流水线状态机（一次bus事务对应1~4拍流水入队，IO ready最后一拍拉高）
    //------------------------------------------------------------
    val wr_valid    = RegInit(false.B)          // 当前事务是否在流水写入
    val write_count = RegInit(0.U(3.W))         // 剩余要写多少字节
    val wr_bytes    = Reg(Vec(4, UInt(8.W)))    // 需写入的内容
    val wr_idx      = Wire(UInt(2.W))           // 当前写入字节的索引
    wr_idx := write_num - write_count

    when(is_write && !wr_valid && can_write) {
        for(i <- 0 until 4) { wr_bytes(i) := write_bytes(i) }
        write_count := write_num
        wr_valid := true.B
    }

    // 默认输出
    fifo.io.enq.valid := false.B
    fifo.io.enq.bits  := 0.U
    io.bus.ready      := false.B
    io.bus.rdata      := 0.U

    when(wr_valid && (write_count > 0.U)) {
        fifo.io.enq.valid := true.B
        fifo.io.enq.bits  := wr_bytes(wr_idx)
        when(fifo.io.enq.ready) {
            write_count := write_count - 1.U
            when(write_count === 1.U) {
                wr_valid := false.B      // 本轮写入完成
                io.bus.ready := true.B   // 拉高ready，结束事务
            }
        }
    } .elsewhen(is_read) {
        io.bus.ready := true.B
        // 返回FIFO的当前计数，低4位
        io.bus.rdata := Cat(0.U(28.W), fifo.io.count)
    } .elsewhen(is_write && !wr_valid && (write_num =/= 0.U) && !can_write) {
        // 空间不足，ready拉低，CPU自动等待
        io.bus.ready := false.B
    }

    //------------------------------------------------------------
    // UART发送主状态机
    //------------------------------------------------------------
    val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
    val state    = RegInit(sIdle)
    val baudCnt  = RegInit(0.U(32.W))
    val bitCnt   = RegInit(0.U(3.W))
    val shiftReg = RegInit(0.U(8.W))
    fifo.io.deq.ready := false.B
    io.tx := true.B // 默认高电平空闲

    switch(state) {
        is(sIdle) {
            io.tx := true.B
            when(fifo.io.deq.valid) {
                state := sStart
                shiftReg := fifo.io.deq.bits
                fifo.io.deq.ready := true.B
                baudCnt := 0.U
        }}
        is(sStart) {
            io.tx := false.B // 起始位
            when(baudCnt === baudCntMax - 1.U) {
                baudCnt := 0.U
                bitCnt := 0.U
                state := sData
            } .otherwise {
                baudCnt := baudCnt + 1.U
        }}
        is(sData) {
            io.tx := shiftReg(bitCnt)
            when(baudCnt === baudCntMax - 1.U) {
                baudCnt := 0.U
                when(bitCnt === 7.U) { state := sStop }
                .otherwise { bitCnt := bitCnt + 1.U }
            } .otherwise {
                baudCnt := baudCnt + 1.U
        }}
        is(sStop) {
            io.tx := true.B // 停止位
            when(baudCnt === baudCntMax - 1.U) {
                baudCnt := 0.U
                state := sIdle
            } .otherwise {
                baudCnt := baudCnt + 1.U
        }}
    }
}


// OLED显示内容输出外设，真正控制OLED屏幕的代码在Verilog模块库里
class OledCtrl extends Module {
    val io = IO(new Bundle {
        val bus  = new DBusPortIO
        val oled = new OLEDLineIO
    })

    val lines = RegInit(VecInit(Seq.fill(4)(VecInit(Seq.fill(4)(0.U(32.W))))))
    val addr_offset = (io.bus.addr - OLED_BASE_ADDR) >> 2
    val lineIdx = addr_offset(3, 2)
    val segIdx  = addr_offset(1, 0)

    // 写入部分：用掩码拼接，支持不同粒度的写
    when(io.bus.wen && (io.bus.addr >= OLED_BASE_ADDR) && (io.bus.addr < OLED_BASE_ADDR + 64.U)) {
        val old = lines(lineIdx)(segIdx)
        // 生成每字节mask
        val byteMask = Wire(Vec(4, Bool()))
        for(i <- 0 until 4) {byteMask(i) := io.bus.ben(i)}
        // 拼接新数据
        val newData = Cat(
            Mux(byteMask(3), io.bus.wdata(31,24), old(31,24)), Mux(byteMask(2), io.bus.wdata(23,16), old(23,16)),
            Mux(byteMask(1), io.bus.wdata(15,8), old(15,8)),   Mux(byteMask(0), io.bus.wdata(7,0), old(7,0))
        )
        lines(lineIdx)(segIdx) := newData
    }

    // 读取部分
    val read_data = WireDefault(0.U(32.W))
    when((io.bus.addr >= OLED_BASE_ADDR) && (io.bus.addr < OLED_BASE_ADDR + 64.U)) {
        read_data := lines(lineIdx)(segIdx)
    }

    io.bus.rdata := read_data
    io.bus.ready := io.bus.valid

    for(i <- 0 until 4) {
        io.oled.elements("str_line" + i) := Cat(lines(i)(0), lines(i)(1), lines(i)(2), lines(i)(3))
    }
}

