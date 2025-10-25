package PaSoC

import Consts._
import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import chisel3.util.experimental.loadMemoryFromFileInline


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
}


// GPIO外设模块
class GPIOCtrl() extends Module {
    val io = IO(new Bundle {
        val bus   = new DBusPortIO()
        val gpio  = new GPIOPortIO()
        val irq   = Output(Bool())
    })
    val debounceEnabled = sys.env.getOrElse("PASOC_SIM", "0") == "1"  // 仿真时不启用消抖

    val gpioOutReg = RegInit(0.U(GPIO_LEN.W))
    val stableIn   = Wire(UInt(GPIO_LEN.W))

    // 与中断相关的状态寄存器
    val lastStableIn = RegInit(0.U(GPIO_LEN.W))

    if (debounceEnabled) {
        // ====== 消抖实现 ======
        val stableReg = RegInit(0.U(GPIO_LEN.W))
        val debounce_cycles = (CLOCK_FREQ / 50).U   // 20ms
        val cnt = RegInit(0.U(log2Ceil(CLOCK_FREQ / 50 + 1).W))
        val lastRawIn = RegNext(io.gpio.In)
        val diff = io.gpio.In =/= lastRawIn  // 输入有变化

        when(diff) { cnt := 0.U }
        .elsewhen(cnt < debounce_cycles) { cnt := cnt + 1.U }

        when(cnt === debounce_cycles && stableReg =/= io.gpio.In) {
            stableReg := io.gpio.In
        }
        stableIn := stableReg
    } else {
        stableIn := io.gpio.In  // 直通实现，无消抖
    }
    lastStableIn := stableIn  // 更新lastStableIn，每拍跟踪

    // 边沿检测：只对上升沿出发 (0->1)
    val risingEdge = (~lastStableIn) & stableIn
    io.irq := risingEdge.orR  // 任意一位由0变1触发中断

    io.gpio.Out := gpioOutReg
    io.bus.rdata := 0.U
    io.bus.ready := false.B
    io.gpio.debug := false.B

    when(io.bus.valid) {
        io.bus.ready := true.B
        when(!io.bus.wen) {
            switch(io.bus.addr(7, 0)) {
                is("h00".U) {
                    io.bus.rdata := stableIn.pad(WORD_LEN)
                    io.gpio.debug := true.B
                }
                is("h04".U) { io.bus.rdata := gpioOutReg.pad(WORD_LEN) }
            }
        }.otherwise {
            switch(io.bus.addr(7, 0)) {
                is("h04".U) { gpioOutReg := io.bus.wdata(GPIO_LEN-1, 0) }
            }
        }
    }
}


// PWM外设模块，生成PWM_LEN路PWM信号
class PWMCtrl() extends Module {
    val io = IO(new Bundle {
        val bus = new DBusPortIO
        val pwm = Output(UInt(PWM_LEN.W))  // PWM输出信号
    })

    // 宽度参数
    val DUTY_WIDTH = log2Ceil(PWM_MAX + 1)

    // N 路占空比寄存器
    val dutyRegs = RegInit(VecInit(Seq.fill(PWM_LEN)(0.U(DUTY_WIDTH.W))))

    // 计数器
    val counter = RegInit(0.U(DUTY_WIDTH.W))
    counter := Mux(counter === PWM_MAX.U, 0.U, counter + 1.U)

    // 只判断低 8 位地址
    // 通道号 (如: PWM 0寄存器偏移为0x00, PWM 1为0x04, ...)
    val addrOffset = io.bus.addr(7, 0)
    val inRange = ((addrOffset & "h03".U) === 0.U) && (addrOffset < (PWM_LEN * 4).U)

    // 计算索引（通道号）
    val channelIndex = (addrOffset >> 2)(log2Ceil(PWM_LEN)-1, 0)

    // 总线默认值
    io.bus.ready := false.B
    io.bus.rdata := 0.U

    when(io.bus.valid && inRange) {
        io.bus.ready := true.B
        when(io.bus.wen) {
            // 写入占空比，限制最大值为PWM_MAX
            dutyRegs(channelIndex) := io.bus.wdata(DUTY_WIDTH-1, 0).min(PWM_MAX.U)
        }.otherwise {
            // 读出寄存器值，pad到WORD_LEN
            io.bus.rdata := dutyRegs(channelIndex).pad(WORD_LEN)
        }
    }

    // PWM信号生成
    val pwmVec = VecInit(
        (0 until PWM_LEN).map(i => counter < dutyRegs(i))
    )
    io.pwm := pwmVec.asUInt // 高位在左
}


// UART外设
class UartCtrl extends Module {
    val io = IO(new Bundle {
        val bus = new DBusPortIO
        val tx  = Output(Bool())
        val rx  = Input( Bool())
    })
    
    val sim = sys.env.getOrElse("PASOC_SIM", "0") == "1"  // 仿真模式标志
    
    val baudCntMax  = (CLOCK_FREQ / BAUD_RATE).U(32.W)  // 计算波特率
    val fifoDepth = 32  // FIFO缓冲字节数

    // 发送FIFO
    val txFifo = Module(new Queue(UInt(8.W), fifoDepth))
    // 接收FIFO
    val rxFifo = Module(new Queue(UInt(8.W), fifoDepth))

    //------------------------------------------------------------
    // 总线写数据字节分解，只处理ben为0001, 0011, 1111三种情况
    //    (写入0x00为TX，读0x04为RX DATA，读0x08为RX COUNT) 
    //------------------------------------------------------------
    val bus_valid = io.bus.valid
    val addr = io.bus.addr(7, 0)
    // 总线各功能判定
    val is_tx_write =  io.bus.wen && (addr === "h00".U) && bus_valid  // 写TX
    val is_tx_read  = !io.bus.wen && (addr === "h00".U) && bus_valid  // 读TX FIFO数量
    val is_rx_read  = !io.bus.wen && (addr === "h04".U) && bus_valid  // 读RX FIFO弹出数据
    val is_rx_count = !io.bus.wen && (addr === "h08".U) && bus_valid  // 读RX FIFO计数

    val bytes = VecInit(Seq.tabulate(4)(i => io.bus.wdata(8*i+7, 8*i)))

    val write_num   = Wire(UInt(3.W))
    val write_bytes = Wire(Vec(4, UInt(8.W)))
    write_num      := 0.U
    write_bytes    := VecInit(Seq.fill(4)(0.U(8.W)))
    switch(io.bus.ben) {
        is("b0001".U) { write_num := 1.U; write_bytes(0) := bytes(0) }
        is("b0011".U) { write_num := 2.U
                        write_bytes(0) := bytes(1); write_bytes(1) := bytes(0) }
        is("b1111".U) { write_num := 4.U
                        write_bytes(0) := bytes(3); write_bytes(1) := bytes(2)
                        write_bytes(2) := bytes(1); write_bytes(3) := bytes(0) }
    }
    val tx_fifo_space = (fifoDepth.U - txFifo.io.count)
    val tx_can_write  = (write_num =/= 0.U) && (tx_fifo_space >= write_num)

    //------------------------------------------------------------
    // 发送流水线状态机
    //------------------------------------------------------------
    val wr_valid    = RegInit(false.B)
    val write_count = RegInit(0.U(3.W))
    val wr_bytes    = Reg(Vec(4, UInt(8.W)))
    val wr_idx      = Wire(UInt(2.W))
    wr_idx := write_num - write_count

    when(is_tx_write && !wr_valid && tx_can_write) {
        for(i <- 0 until 4) { wr_bytes(i) := write_bytes(i) }
        write_count := write_num
        wr_valid := true.B
    }

    // 默认输出
    txFifo.io.enq.valid := false.B
    txFifo.io.enq.bits  := 0.U
    io.bus.ready      := false.B
    io.bus.rdata      := 0.U

    when(wr_valid && (write_count > 0.U)) {
        txFifo.io.enq.valid := true.B
        txFifo.io.enq.bits  := wr_bytes(wr_idx)
        when(txFifo.io.enq.ready) {
            write_count := write_count - 1.U
            when(write_count === 1.U) {
                wr_valid := false.B
                io.bus.ready := true.B
            }
        }
    } .elsewhen(is_tx_read) {
        io.bus.ready := true.B
        io.bus.rdata := Cat(0.U(28.W), txFifo.io.count)
    } .elsewhen(is_tx_write && !wr_valid && (write_num =/= 0.U) && !tx_can_write) {
        io.bus.ready := false.B
    }

    //============================================================
    //   串口接收状态机 & FIFO（1倍采样，简易版）
    //============================================================
    val rsIdle :: rsStart :: rsData :: rsStop :: Nil = Enum(4)
    val rxState = RegInit(rsIdle)

    val rxBaudCnt = RegInit(0.U(32.W))
    val rxShiftReg = RegInit(0.U(8.W))
    val rxBitCnt = RegInit(0.U(3.W))
    val rxDataRdy = WireInit(false.B)

    val rxSync = RegNext(RegNext(io.rx))
    rxFifo.io.enq.valid := false.B
    rxFifo.io.enq.bits  := 0.U

    rxDataRdy := false.B
    switch(rxState) {
        is(rsIdle) {
            rxBaudCnt := 0.U
            when(!rxSync) { // 检测到起始位
                rxState := rsStart
                rxBaudCnt := 0.U
            }
        }
        is(rsStart) {
            rxBaudCnt := rxBaudCnt + 1.U
            // 等待半个bit后，跳到数据区
            when(rxBaudCnt === (baudCntMax >> 1)) {
                rxBaudCnt := 0.U
                rxBitCnt := 0.U
                rxState := rsData
            }
        }
        is(rsData) {
            rxBaudCnt := rxBaudCnt + 1.U
            when(rxBaudCnt === baudCntMax - 1.U) {
                rxShiftReg := (rxSync.asUInt << 7) | (rxShiftReg >> 1)
                rxBaudCnt := 0.U
                when(rxBitCnt === 7.U) {
                    rxState := rsStop
                }.otherwise {
                    rxBitCnt := rxBitCnt + 1.U
                }
            }
        }
        is(rsStop) {
            rxBaudCnt := rxBaudCnt + 1.U
            when(rxBaudCnt === baudCntMax - 1.U) {
                rxState := rsIdle
                rxBaudCnt := 0.U
                when(rxSync) { rxDataRdy := true.B } // stop bit=1
            }
        }
    }

    // 接收1字节, 入FIFO
    when(rxDataRdy && rxFifo.io.enq.ready) {
        rxFifo.io.enq.valid := true.B
        rxFifo.io.enq.bits := rxShiftReg
    }

    // RX 总线操作（0x04读内容，0x08读深度）
    rxFifo.io.deq.ready := false.B // 默认
    when(is_rx_read) {
        io.bus.ready := rxFifo.io.deq.valid
        io.bus.rdata := Mux(rxFifo.io.deq.valid, Cat(0.U(24.W), rxFifo.io.deq.bits), 0.U)
        rxFifo.io.deq.ready := io.bus.ready && bus_valid // 只在握手时出队
    } .elsewhen(is_rx_count) {
        io.bus.ready := true.B
        io.bus.rdata := Cat(0.U(28.W), rxFifo.io.count)
    }
    
    //============================================================
    //   UART发送主状态机
    //============================================================
    val tsIdle :: tsStart :: tsData :: tsStop :: Nil = Enum(4)
    val txState    = RegInit(tsIdle)
    val txBaudCnt  = RegInit(0.U(32.W))
    val txBitCnt   = RegInit(0.U(3.W))
    val txShiftReg = RegInit(0.U(8.W))
    txFifo.io.deq.ready := false.B
    io.tx := true.B

    switch(txState) {
        is(tsIdle) {
            io.tx := true.B
            when(txFifo.io.deq.valid) {
                if(sim) { printf("%c", txFifo.io.deq.bits) }
                txState := tsStart
                txShiftReg := txFifo.io.deq.bits
                txFifo.io.deq.ready := true.B
                txBaudCnt := 0.U
            }
        }
        is(tsStart) {
            io.tx := false.B // 起始位
            when(txBaudCnt === baudCntMax - 1.U) {
                txBaudCnt := 0.U
                txBitCnt := 0.U
                txState := tsData
            } .otherwise {
                txBaudCnt := txBaudCnt + 1.U
            }
        }
        is(tsData) {
            io.tx := txShiftReg(txBitCnt)
            when(txBaudCnt === baudCntMax - 1.U) {
                txBaudCnt := 0.U
                when(txBitCnt === 7.U) { txState := tsStop }
                .otherwise { txBitCnt := txBitCnt + 1.U }
            } .otherwise {
                txBaudCnt := txBaudCnt + 1.U
            }
        }
        is(tsStop) {
            io.tx := true.B // 停止位
            when(txBaudCnt === baudCntMax - 1.U) {
                txBaudCnt := 0.U
                txState := tsIdle
            } .otherwise {
                txBaudCnt := txBaudCnt + 1.U
            }
        }
    }
}


// OLED显示内容输出外设，真正控制OLED屏幕的代码在Verilog里
// TODO: 大小端序需要重写
class OledCtrl extends Module {
    val io = IO(new Bundle {
        val bus  = new DBusPortIO
        val oled = new OLEDLineIO
    })

    // 4 行 × 4 段× 32bit
    val lines = RegInit(VecInit(Seq.fill(4)(VecInit(Seq.fill(4)(0.U(32.W))))))

    // 只解码低8位
    val addrOffset = io.bus.addr(7,0)
    // offset范围是0x00~0x3F（4行×4段×4字节=64B）
    val validAccess = (addrOffset < 64.U) //&& ((addrOffset & "h3".U) === 0.U)

    // 行/段索引
    val lineIdx = addrOffset(5,4)  // 00/01/10/11
    val segIdx  = addrOffset(3,2)  // 00/01/10/11

    // 写操作，按byte mask
    when(io.bus.valid && io.bus.wen && validAccess) {
        val old = lines(lineIdx)(segIdx)
        val masks = Wire(Vec(4, Bool()))
        for(i <- 0 until 4) { masks(i) := io.bus.ben(i) }
        val newData = Cat(
            Mux(masks(3), io.bus.wdata(31,24), old(31,24)),
            Mux(masks(2), io.bus.wdata(23,16), old(23,16)),
            Mux(masks(1), io.bus.wdata(15,8),  old(15,8)),
            Mux(masks(0), io.bus.wdata(7,0),   old(7,0))
        )
        lines(lineIdx)(segIdx) := newData
    }

    // 读操作
    val readData = WireDefault(0.U(32.W))
    when(io.bus.valid && !io.bus.wen && validAccess) {
        readData := lines(lineIdx)(segIdx)
    }

    io.bus.rdata := readData
    io.bus.ready := io.bus.valid && validAccess

    // 把每一行4个段拼接到OLED输出
    for(i <- 0 until 4) {
        io.oled.elements("str_line" + i) := Cat(lines(i)(0), lines(i)(1), lines(i)(2), lines(i)(3))
    }
}


class PLIC extends Module {
    val io = IO(new Bundle {
        val irq_in  = Input(UInt(8.W))  // 外部中断源请求表
        val irq_out = Output(Bool())    // 最高优先级中断输出到CPU
        val bus     = new DBusPortIO    // 总线接口
    })

    // ----------------------
    // 8路pending寄存器（只读），外部触发，产生IRQ
    val pending = RegInit(0.U(8.W))
    // 每路使能
    val enable  = RegInit(0.U(8.W))
    // claim后清除pending
    val complete = WireInit(0.U(8.W))
    // claim寄存器
    val claim_reg = RegInit(0.U(4.W)) // 0=无中断, 1~8表示中断编号

    // priority略, 固定优先级: 0路最高，7最低

    // 外部中断输入采样
    val irq_level = io.irq_in        // 可改为同步脉冲采样: io.irq_in & ~RegNext(io.irq_in)
    pending := pending | irq_level   // 有请求时置位

    // 寻找最高优先级pending且enabled的中断
    val pending_masked = pending & enable
    val irq_vec = VecInit((0 until 8).map(i => pending_masked(i)))
    val irq_prio = Wire(UInt(4.W))
    // 取第一个为1的通道号(1~8)，否则为0
    irq_prio := MuxCase(0.U, (1 until 9).map(i =>
        (irq_vec(i-1)) -> i.U
    ))
    io.irq_out := irq_prio =/= 0.U
    claim_reg := irq_prio   // 通常CPU收到irq后，读取claim寄存器获得中断号

    // 读取mapping
    val rdata = WireDefault(0.U(32.W))
    switch(io.bus.addr(7, 0)) {
        is("h00".U) { rdata := pending }
        is("h04".U) { rdata := enable }
        is("h0C".U) { rdata := claim_reg }
        // is("h08".U) { rdata := ... } // priority
    }
    io.bus.rdata := rdata

    // 写mapping
    when (io.bus.valid && io.bus.wen) {
        switch(io.bus.addr(7, 0)) {
        is("h04".U) {  // enable
            enable := io.bus.wdata(7,0)
        }
        is("h0C".U) {  // complete
            // 写claim/complete，wdata[3:0]为complete, 1~8号
            // pending清除相应位
            val clear_num = io.bus.wdata(3,0)
            when(clear_num > 0.U && clear_num <= 8.U) {
                pending := pending & ~(1.U << (clear_num-1.U))
            }
        }
        // 其它写无效
        }
    }

    io.bus.ready := true.B

}


class CLINT extends Module {
    val io = IO(new Bundle {
        val bus = new DBusPortIO()  // 使用数据总线接口
        val irq = Output(Bool())    // 定时器中断信号
    })

    // 定义64位mtime和mtimecmp 
    val mtime    = RegInit(0.U(64.W))  // mtime  MMIO映射    低32位在 0x00-0x03，高32位在 0x04-0x07
    val mtimecmp = RegInit("x_ffffffffffffffff".U(64.W))  // 低32位在 0x08-0x0B，高32位在 0x0C-0x0F

    mtime := mtime + 1.U  // 定时器递增
    val timeup = (mtime >= mtimecmp)  // 达到/超过设定时间
    io.irq := timeup  // 触发中断  

    io.bus.ready := false.B  // 初始化 ready 信号为 false
    io.bus.rdata := 0.U      // 初始化 rdata 为 0

    // 处理读写请求
    when(io.bus.valid) {
        io.bus.ready := true.B  // 总线有效时拉高 ready
        val addr = io.bus.addr(7,0)  // 只使用了地址的低 8 位
        when(io.bus.wen) {
            switch(addr) {
            is("h00".U) { mtime := Cat(mtime(63, 32), io.bus.wdata) }
            is("h04".U) { mtime := Cat(io.bus.wdata,  mtime(31, 0)) }
            is("h08".U) { mtimecmp := Cat(mtimecmp(63, 32), io.bus.wdata) }
            is("h0C".U) { mtimecmp := Cat(io.bus.wdata,  mtimecmp(31, 0)) }
            }
        }.otherwise {
            io.bus.rdata := MuxCase(0.U, Seq(
                (addr === "h00".U) -> mtime(31, 0),
                (addr === "h04".U) -> mtime(63, 32),
                (addr === "h08".U) -> mtimecmp(31, 0),
                (addr === "h0C".U) -> mtimecmp(63, 32),
                (addr === "h10".U) -> Mux(timeup, 1.U, 0.U)
            ))
        }
    }
}


// VGA显示外设，需要大量BRAM
// TODO: 支持字节/半字访问
class VGACtrl extends Module {
    val io = IO(new Bundle {
        val vga = new VGASignalIO()
        val bus = new DBusPortIO()
        val addrb = Input(UInt(WORD_LEN.W))
    })

    // VGA 分辨率设置
    val h_frontporch = 96.U
    val h_active     = 144.U
    val h_backporch  = 784.U
    val h_total      = 800.U

    val v_frontporch = 2.U
    val v_active     = 35.U
    val v_backporch  = 515.U
    val v_total      = 525.U

    val x_cnt = RegInit(1.U(10.W))
    val y_cnt = RegInit(1.U(10.W))

    when (x_cnt === h_total) {
        x_cnt := 1.U
        when (y_cnt === v_total) {
        y_cnt := 1.U
        }.otherwise {
        y_cnt := y_cnt + 1.U
        }
    }.otherwise {
        x_cnt := x_cnt + 1.U
    }

    // 同步信号
    io.vga.hsync := x_cnt > h_frontporch
    io.vga.vsync := y_cnt > v_frontporch

    val h_valid = (x_cnt > h_active) && (x_cnt <= h_backporch)
    val v_valid = (y_cnt > v_active) && (y_cnt <= v_backporch)
    io.vga.valid := h_valid && v_valid

    val h_addr = Mux(h_valid, x_cnt - 145.U, 0.U(10.W))
    val v_addr = Mux(v_valid, y_cnt - 36.U, 0.U(10.W))

    // 显存（32-bit 宽度, 每像素占1字）
    val WIDTH  = 640
    val HEIGHT = 480
    val MEM_DEPTH = WIDTH * HEIGHT
    val mem = SyncReadMem(MEM_DEPTH, UInt(32.W))  // BRAM风格
    loadMemoryFromFileInline(mem, "nvboard/picture.hex")

    // 显存读口
    val vga_addr = v_addr * WIDTH.U + h_addr
    val vga_data = mem.read(vga_addr) // 同步读

    io.vga.r := vga_data(23,16)
    io.vga.g := vga_data(15,8)
    io.vga.b := vga_data(7,0)

    // 数据总线访问口
    val addrWidth = log2Ceil(MEM_DEPTH)
    val daddrb = io.addrb(addrWidth, 2) // 每像素4字节对齐访问
    io.bus.rdata := 0.U
    io.bus.ready := false.B
    
    // DBus读写
    when (io.bus.valid) {
        io.bus.ready := true.B
        when (io.bus.wen) { // 写
            mem.write(daddrb, io.bus.wdata)
        }
    }
    io.bus.rdata := mem.read(daddrb)  // 在valid来前就要开始读
}
