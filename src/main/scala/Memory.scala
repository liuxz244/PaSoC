package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import chisel3.util.experimental.loadMemoryFromFileInline  // 向存储器写入初始值
import Consts._


// BRAM指令存储器 + UART接收写入
class ITCM(val depth: Int, val initHex: String) extends Module {
    val io = IO(new Bundle {
        val bus = new IBusPortIO()
        val rx  = Input(Bool())  // UART RX引脚
    })

    // 1. 创建存储器
    val mem = SyncReadMem(depth, UInt(WORD_LEN.W))
    val initInst = "src/test/hex/inst/" + initHex  // 拼接完整路径
    loadMemoryFromFileInline(mem, initInst)  // 初始化内存

    // 2. 计算地址宽度
    val addrWidth = log2Ceil(depth)

    // 3. 读指令：地址转换，字节地址右移2位获得存储地址
    val iaddrb = Wire(UInt(addrWidth.W))
    iaddrb := Mux(reset.asBool, 0.U, io.bus.addrb(addrWidth + 1, 2))
    io.bus.inst := mem(iaddrb)

    
    // ------------------------------
    // 4. UART 接收器实现
    // ------------------------------

    // 计算波特率分频计数(整数)
    val baudTickCount = (CLOCK_FREQ / BAUD_RATE).U

    // UART 接收状态机定义
    val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
    val state = RegInit(sIdle)

    val baudCounter = RegInit(0.U(16.W))   // 计数波特率采样时钟
    val bitCounter  = RegInit(0.U(4.W))    // 已接收bit计数
    val rxShiftReg  = RegInit(0.U(8.W))    // 存放接收的8bit数据
    val dataReady   = RegInit(false.B)     // 数据接收完整标志信号

    // 采样中点（波特率计数一半时针）
    val baudMid = (baudTickCount >> 1)

    // UART RX采样逻辑
    switch(state) {
        is(sIdle) {
            dataReady := false.B
            baudCounter := 0.U
            bitCounter := 0.U
            when(!io.rx) {    // 检测到起始位（低电平）
                state := sStart
                baudCounter := 0.U
            }
        }

        is(sStart) {
            // 等待采样起始位中点，确认起始位有效
            when(baudCounter === baudMid) {
                when(io.rx === false.B) {  // 验证依旧是低电平才确认起始位
                    state := sData
                    baudCounter := 0.U
                    bitCounter := 0.U
                }.otherwise {
                // 起始位错误回到空闲
                    state := sIdle
                }
            }.otherwise {
                baudCounter := baudCounter + 1.U
            }
        }

        is(sData) {
            // 每个数据位采样中点采样
            when(baudCounter === baudTickCount - 1.U) {
                baudCounter := 0.U
                // 采样rx信号，存入rxShiftReg相应bit，LSB先接收
                rxShiftReg := Cat(io.rx, rxShiftReg(7,1))  // 右移接收新bit到最高位
                bitCounter := bitCounter + 1.U
                when(bitCounter === 7.U) {
                    // 8bit数据全部接收完成
                    state := sStop
                }
            }.otherwise {
                baudCounter := baudCounter + 1.U
            }
        }

        is(sStop) {
            // 采样停止位中点，校验停止位
            when(baudCounter === baudTickCount - 1.U) {
                baudCounter := 0.U
                when(io.rx === true.B) {
                dataReady := true.B   // 数据接收完成，8bit可用
                }
                // 完成后回到空闲等待下一帧
                state := sIdle
            }.otherwise {
                baudCounter := baudCounter + 1.U
            }
        }
    }

    // ------------------------------
    // 5. 四字节数据组装为一个指令，写入存储器
    // ------------------------------

    // 四字节缓冲寄存器（字节按顺序存储）
    val byteBuffer = Reg(Vec(4, UInt(8.W)))
    val byteCount = RegInit(0.U(2.W))   // 接收字节计数

    // 写地址计数器，自动递增写入
    val writeAddr = RegInit(0.U(addrWidth.W))

    // 标志写使能
    val writeEn = RegInit(false.B)

    // 数据缓存及计数逻辑
    when(dataReady) {
        byteBuffer(byteCount) := rxShiftReg
        byteCount := byteCount + 1.U
        when(byteCount === 3.U) {
            writeEn := true.B   // 接收到4字节，准备写入存储器
        }.otherwise {
            writeEn := false.B
        }
    }.otherwise {
        writeEn := false.B
    }

    // 组装4字节为32bit，最高字节放最高位
    val assembledWord = Cat(
        byteBuffer(0),
        byteBuffer(1),
        byteBuffer(2),
        byteBuffer(3)
    )

    // 写存储器同步写端口
    when(writeEn) {
        mem(writeAddr) := assembledWord
        writeAddr := writeAddr + 1.U   // 写地址递增
        byteCount := 0.U               // 重置计数
    }
    
}


// 同步读数据存储器，可综合为BRAM，支持字节/半字写入
class DTCM(val depth: Int, initHex: String) extends Module {
    val io = IO(new Bundle {
        val bus = new DBusPortIO()
    })

    val mem = SyncReadMem(depth, UInt(32.W))  // 定义BRAM存储器
    val initData = "src/test/hex/data/" + initHex  // 拼接完整路径
    loadMemoryFromFileInline(mem, initData)  //初始化内存

    val addrWidth = log2Ceil(depth)

    // 地址按字对齐（4字节对齐），去掉低2位，取有效的地址宽度
    val daddrb = Wire(UInt(addrWidth.W))
    daddrb := io.bus.addrb(addrWidth + 1, 2)
    val daddr = Wire(UInt(addrWidth.W))
    daddr := io.bus.addr(addrWidth + 1, 2)

    // 读操作（延迟被提前发出地址抵消了）
    val rdata = mem.read(daddrb)
    io.bus.rdata := rdata

    // ready 信号同步
    io.bus.ready := io.bus.valid

    // 写操作：byte enable
    val old = mem.read(daddrb)
    when(io.bus.wen) {
        val oldBytes = old.asTypeOf(Vec(4, UInt(8.W)))
        val newBytes = io.bus.wdata.asTypeOf(Vec(4, UInt(8.W)))
        val ben = io.bus.ben.asBools // 低位对应索引0，高位索引3

        val resultBytes = Wire(Vec(4, UInt(8.W)))
        for (i <- 0 until 4) {
            resultBytes(i) := Mux(ben(i), newBytes(i), oldBytes(i))
        }

        mem.write(daddr, resultBytes.asUInt)
    }
}


// SDRAM 控制器，支持全字、半字、字节读/写（只支持 8MB 32位SDRAM）
// 字节/半字写通过读-改-写实现
class SdramCtrl extends Module {
    val io = IO(new Bundle {
        val bus   = new DBusPortIO
        val sdram = new SdramPortIO
    })

    // 状态机定义
    val sIdle :: sReq :: sModifyRead :: sModifyWrite :: Nil = Enum(4)
    val state = RegInit(sIdle)

    // SDRAM请求默认赋初值
    io.sdram.o_valid := false.B
    io.sdram.o_addr  := 0.U
    io.sdram.o_wdata := 0.U
    io.sdram.o_wstrb := 0.U

    // 总线响应默认输出
    io.bus.rdata := 0.U
    io.bus.ready := false.B

    // 地址低28位接SDRAM
    val sdram_addr = Cat(0.U(4.W), io.bus.addr(27,0))

    // 操作类型判定
    val ben_fullword = io.bus.ben === "b1111".U
    val ben_halfword = (io.bus.ben === "b0011".U) || (io.bus.ben === "b1100".U)
    val ben_byte     = (io.bus.ben === "b0001".U) || (io.bus.ben === "b0010".U) ||
                       (io.bus.ben === "b0100".U) || (io.bus.ben === "b1000".U)

    // 暂存字节/半字写需要的信息
    val write_addr   = Reg(UInt(32.W))
    val write_src    = Reg(UInt(32.W))
    val write_ben    = Reg(UInt(4.W))
    val modify_rdata = Reg(UInt(32.W))

    switch(state) {
        is(sIdle) {
            io.bus.ready := false.B
            when(io.bus.valid) {
                when(io.bus.wen) { // 写操作
                    when(ben_fullword) {
                        // 直接全字写
                        io.sdram.o_valid := true.B
                        io.sdram.o_addr  := sdram_addr
                        io.sdram.o_wdata := io.bus.wdata
                        io.sdram.o_wstrb := "b1111".U
                        state := sReq
                    } .elsewhen(ben_halfword || ben_byte) {
                        // 字节/半字写，需 Read-Modify-Write
                        write_addr := sdram_addr
                        write_src  := io.bus.wdata
                        write_ben  := io.bus.ben
                        state := sModifyRead
                    }
                } .otherwise { // 读操作
                    io.sdram.o_valid := true.B
                    io.sdram.o_addr  := sdram_addr
                    io.sdram.o_wdata := 0.U
                    io.sdram.o_wstrb := 0.U
                    state := sReq
                }
            }
        }
        is(sReq) {
            io.sdram.o_valid := true.B
            io.sdram.o_addr  := sdram_addr
            io.sdram.o_wdata := io.bus.wdata
            io.sdram.o_wstrb := Mux(io.bus.wen, "b1111".U, "b0000".U)
            when(io.sdram.i_ready) {
                when(!io.bus.wen) {
                    io.bus.rdata := io.sdram.i_rdata
                    io.bus.ready := true.B
                }
                state := sIdle
            }
        }
        is(sModifyRead) {
            // 发起32位读
            io.sdram.o_valid := true.B
            io.sdram.o_addr  := write_addr
            io.sdram.o_wdata := 0.U
            io.sdram.o_wstrb := 0.U
            when(io.sdram.i_ready) {
                modify_rdata := io.sdram.i_rdata
                state := sModifyWrite
            }
        }
        is(sModifyWrite) {
            // 生成修改后的全字数据
            val new_wdata = WireDefault(modify_rdata)
            // 字节写：哪个字节有效
            when(write_ben === "b0001".U) { // byte 0
                new_wdata := Cat(modify_rdata(31,8), write_src(7,0))
            } .elsewhen(write_ben === "b0010".U) { // byte 1
                new_wdata := Cat(modify_rdata(31,16), write_src(15,8), modify_rdata(7,0))
            } .elsewhen(write_ben === "b0100".U) { // byte 2
                new_wdata := Cat(modify_rdata(31,24), write_src(23,16), modify_rdata(15,0))
            } .elsewhen(write_ben === "b1000".U) { // byte 3
                new_wdata := Cat(write_src(31,24), modify_rdata(23,0))
            }
            // 半字写
            .elsewhen(write_ben === "b0011".U) {   // halfword 0 (低半字)
                new_wdata := Cat(modify_rdata(31,16), write_src(15,0))
            } .elsewhen(write_ben === "b1100".U) { // halfword 1 (高半字)
                new_wdata := Cat(write_src(31,16), modify_rdata(15,0))
            }

            io.sdram.o_valid := true.B
            io.sdram.o_addr  := write_addr
            io.sdram.o_wdata := new_wdata
            io.sdram.o_wstrb := "b1111".U
            when(io.sdram.i_ready) {
                io.bus.ready := true.B
                state := sIdle
            }
        }
    }
}

/*
// ===== 测试发现还是有问题，最终只能实现8MB的读写=====
// SDRAM控制器：支持32MB容量 W9825G6KH和MT48LC16M16
//因为只写2个数据太少了会出错，一个32位字会分4次写，还要分别处理高8位和低8位，导致写入延迟较大。
//所有写操作都通过"读-改-写"4个16bit数据，避免单字节/半字写时丢失其余位
class SdramCtrl extends Module {
    val io = IO(new Bundle {
        val bus   = new DBusPortIO
        val sdram = new SdramDriverPortIO(16)
    })

    // 状态机
    val sIdle :: sReadReq :: sReadWait :: sModify :: sWriteReq :: sWriteWait :: sWriteDone :: sReadOut :: Nil = Enum(8)
    val state    = RegInit(sIdle)

    // 工作寄存器
    val orig_wrdata = Reg(UInt(32.W))    // 总线写入的原始32位数据
    val addr28      = Reg(UInt(28.W))    // 28位对齐地址
    val ben         = Reg(UInt(4.W))     // 字节使能
    val isWrite     = Reg(Bool())        // 是写操作
    val byteCnt     = RegInit(0.U(2.W))  // 0~3: 当前是第几个word

    // 高8位/低8位选择, 和SDRAM实际连线相关
    val useHighByte = Wire(Bool())
    useHighByte := addr28(22)  // 由24位决定SDRAM 16位高/低8位

    // 拆分32位总线数据为4个8位
    val wr_bytes = Wire(Vec(4, UInt(8.W)))
    wr_bytes := VecInit(Seq.tabulate(4)(i => (orig_wrdata >> (i*8))(7,0)))

    // 保存4x16bit
    val rdata16_vec = RegInit(VecInit(Seq.fill(4)(0.U(16.W))))

    // SDRAM接口连线
    io.sdram.operate_addr := addr28(21, 0)  // 24位寻址16MB
    io.sdram.operate_nums := 4.U
    io.sdram.start        := false.B
    io.sdram.mode         := true.B  // 1=读，0=写

    // 总线接口默认值
    io.bus.rdata := 0.U
    io.bus.ready := false.B

    // 从16位数据中选出目标操作的8位
    def get_byte_from_16(data16: UInt): UInt = 
        Mux(useHighByte, data16(15,8), data16(7,0))

    // 组装写回用的16位数据（按ben）
    val write_word_vec = Wire(Vec(4, UInt(16.W)))
    for (i <- 0 until 4) {
        val old = rdata16_vec(i)
        write_word_vec(i) := Mux(ben(i),
            Mux(useHighByte,
                Cat(wr_bytes(i), old(7,0)),
                Cat(old(15,8), wr_bytes(i))),
            old
        )
    }
    // 正在写第byteCnt个16位
    io.sdram.send_data := write_word_vec(byteCnt)

    // 把4个16位组织为32位数据（小端）
    def combine_read_vec(vec: Vec[UInt]): UInt =
        Cat(
            get_byte_from_16(vec(3)),
            get_byte_from_16(vec(2)),
            get_byte_from_16(vec(1)),
            get_byte_from_16(vec(0))
        )

    // ================= 状态机 =================
    switch(state) {
        is(sIdle) {
            byteCnt := 0.U
            when(io.bus.valid) {
                addr28      := Cat(io.bus.addr(27,2), 0.U(2.W))
                orig_wrdata := io.bus.wdata
                isWrite     := io.bus.wen
                ben         := io.bus.ben
                byteCnt     := 0.U
                when(io.sdram.idle) {
                    state := sReadReq
                }
            }
        }

        // SDRAM读请求，4x16bit
        is(sReadReq) {
            io.sdram.mode  := true.B
            io.sdram.start := true.B
            state          := sReadWait
            byteCnt        := 0.U
        }

        // SDRAM读响应，4x16bit
        is(sReadWait) {
            io.sdram.mode  := true.B
            io.sdram.start := false.B
            when(io.sdram.data_ready) {
                rdata16_vec(byteCnt) := io.sdram.rec_data
                byteCnt := byteCnt + 1.U
            }
            when(io.sdram.done) {
                byteCnt := 0.U
                when(isWrite) { state := sModify }
                .otherwise    { state := sReadOut }
            }
        }

        // 组合新数据：准备写阶段
        is(sModify) {
            byteCnt := 0.U
            when(io.sdram.idle) {
                state := sWriteReq
            }
        }

        // SDRAM写请求，4x16bit
        is(sWriteReq) {
            io.sdram.mode  := false.B
            io.sdram.start := true.B
            state          := sWriteWait
            byteCnt        := 0.U
        }

        // SDRAM写响应，4x16bit
        is(sWriteWait) {
            io.sdram.mode  := false.B
            io.sdram.start := false.B
            when(io.sdram.data_ready) {
                byteCnt := byteCnt + 1.U
            }
            when(io.sdram.done) {
                state   := sWriteDone
                byteCnt := 0.U
            }
        }

        // 写完成, 总线握手
        is(sWriteDone) {
            io.bus.ready := true.B
            state        := sIdle
        }

        // 读完成, 数据打包返回
        is(sReadOut) {
            io.bus.rdata := combine_read_vec(rdata16_vec)
            io.bus.ready := true.B
            state        := sIdle
        }
    }
}
*/
