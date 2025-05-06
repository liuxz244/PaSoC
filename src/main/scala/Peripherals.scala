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
                io.devs(i).wdata := io.bus.wdata
                io.bus.rdata     := io.devs(i).rdata
                io.bus.ready     := io.devs(i).ready
            }
        }
    }

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
        val tx   = Output(Bool())   // UART TX 引脚，空闲时高
    })

    // 计算一个波特周期对应的时钟周期数
    private val baudCntMax = (CLOCK_FREQ / BAUD_RATE).U(32.W)

    // 1) 总线地址译码：只有当 wen 且 addr == UART_TX_ADDR 时才写寄存器
    val busSel = io.bus.wen && (io.bus.addr === UART_TX_ADDR)

    // 2) 发送状态机  
    val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
    val state   = RegInit(sIdle)
    val baudCnt = RegInit(0.U(32.W))
    val bitCnt  = RegInit(0.U(3.W))
    val shiftReg= RegInit(0.U(8.W))

    // 默认输出
    io.tx := true.B     // 高电平空闲
    io.bus.rdata := 0.U
    io.bus.ready := false.B

    switch(state) {
        is(sIdle) {
            // 总线写事务完成一个周期后，产生 ready=1 并加载数据
            io.bus.ready := busSel
            when(busSel) {
                shiftReg := io.bus.wdata(7,0)  // 取低 8 位做发送字节
                state    := sStart
                baudCnt  := 0.U
            }
        }
        is(sStart) {  // 发送起始位(低电平)
            io.bus.ready := false.B
            io.tx := false.B
            when(baudCnt === baudCntMax-1.U) {
                baudCnt := 0.U
                bitCnt  := 0.U
                state   := sData
            }.otherwise {
                baudCnt := baudCnt + 1.U
            }
        }
        is(sData) {   // 发送 8 个数据位，LSB 先出
            io.bus.ready := false.B
            io.tx := shiftReg(bitCnt)
            when(baudCnt === baudCntMax-1.U) {
                baudCnt := 0.U
                when(bitCnt === 7.U) {
                    state  := sStop
                }.otherwise {
                    bitCnt := bitCnt + 1.U
                }
            }.otherwise {
                baudCnt := baudCnt + 1.U
            }
        }
        is(sStop) {   // 发送 1 个停止位(高电平)
            io.bus.ready := false.B
            io.tx := true.B
            when(baudCnt === baudCntMax-1.U) {
                baudCnt := 0.U
                state   := sIdle
            }.otherwise {
                baudCnt := baudCnt + 1.U
            }
        }
    }
}