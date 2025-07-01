package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import chisel3.util.experimental.loadMemoryFromFileInline  // 向存储器写入初始值
import chisel3.dontTouch  // 不要优化掉信号
import Consts._


// BRAM指令存储器 + UART接收写入
class ITCM(val depth: Int, val initHex: String) extends Module {
    val io = IO(new Bundle {
        val bus = new IBusPortIO()
        //val rx  = Input(Bool())  // UART RX引脚
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

    /*
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
    */
}


// 同步读数据存储器，可综合为BRAM，支持字节/半字写入
class DTCM(val depth: Int, initHex: String) extends Module {
    val io = IO(new Bundle {
        val bus   = new DBusPortIO()
        val addrb = Input(UInt(WORD_LEN.W))
    })

    val mem = SyncReadMem(depth, UInt(32.W))  // 定义BRAM存储器
    val initData = "src/test/hex/data/" + initHex  // 拼接完整路径
    loadMemoryFromFileInline(mem, initData)  //初始化内存

    val addrWidth = log2Ceil(depth)

    // 地址按字对齐（4字节对齐），去掉低2位，取有效的地址宽度
    val daddrb = Wire(UInt(addrWidth.W))
    daddrb := io.addrb(addrWidth + 1, 2)
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
class SdrNodqm8M extends Module {
    val io = IO(new Bundle {
        val bus   = new DBusPortIO
        val sdram = new Sdr32bit8mIO
    })

    // 状态机
    val sIdle :: sReq :: sModifyRead :: sModifyWrite :: Nil = Enum(4)
    val state = RegInit(sIdle)
    // SDRAM请求默认
    io.sdram.o_valid := false.B; io.sdram.o_addr := 0.U
    io.sdram.o_wdata := 0.U;    io.sdram.o_wstrb := 0.U
    // 总线响应默认
    io.bus.rdata := 0.U; io.bus.ready := false.B
    // 地址低28位
    val sdram_addr = Cat(0.U(4.W), io.bus.addr(27,0))
    // 操作类型
    val ben_fullword = io.bus.ben === "b1111".U
    val ben_halfword = (io.bus.ben === "b0011".U) || (io.bus.ben === "b1100".U)
    val ben_byte     = (io.bus.ben === "b0001".U) || (io.bus.ben === "b0010".U) ||
                       (io.bus.ben === "b0100".U) || (io.bus.ben === "b1000".U)
    // 写临时寄存
    val write_addr = Reg(UInt(32.W)); val write_src = Reg(UInt(32.W))
    val write_ben = Reg(UInt(4.W));   val modify_rdata = Reg(UInt(32.W))

    switch(state) {
        is(sIdle) {
            io.bus.ready := false.B
            when(io.bus.valid) {
                when(io.bus.wen) { // 写
                    when(ben_fullword) {
                        io.sdram.o_valid := true.B
                        io.sdram.o_addr  := sdram_addr
                        io.sdram.o_wdata := io.bus.wdata
                        io.sdram.o_wstrb := "b1111".U
                        state := sReq
                    } .elsewhen(ben_halfword || ben_byte) {
                        write_addr := sdram_addr
                        write_src  := io.bus.wdata
                        write_ben  := io.bus.ben
                        state := sModifyRead
                    }
                } .otherwise { // 读
                    io.sdram.o_valid := true.B
                    io.sdram.o_addr  := sdram_addr
                    state := sReq
                }
            }
        }
        is(sReq) {
            io.sdram.o_valid := true.B
            io.sdram.o_addr := sdram_addr
            io.sdram.o_wdata := io.bus.wdata
            io.sdram.o_wstrb := Mux(io.bus.wen, "b1111".U, 0.U)
            when(io.sdram.i_ready) {
                when(!io.bus.wen) { io.bus.rdata := io.sdram.i_rdata }
                io.bus.ready := true.B; state := sIdle
            }
        }
        is(sModifyRead) {
            // 发32位读
            io.sdram.o_valid := true.B
            io.sdram.o_addr  := write_addr
            when(io.sdram.i_ready) {
                modify_rdata := io.sdram.i_rdata
                state := sModifyWrite
            }
        }
        is(sModifyWrite) {
            // 修改后全字数据
            val new_wdata = WireDefault(modify_rdata)
            switch(write_ben) {
            is("b0001".U) { new_wdata := Cat(modify_rdata(31,8), write_src(7,0)) }
            is("b0010".U) { new_wdata := Cat(modify_rdata(31,16), write_src(15,8), modify_rdata(7,0)) }
            is("b0100".U) { new_wdata := Cat(modify_rdata(31,24), write_src(23,16), modify_rdata(15,0)) }
            is("b1000".U) { new_wdata := Cat(write_src(31,24), modify_rdata(23,0)) }
            is("b0011".U) { new_wdata := Cat(modify_rdata(31,16), write_src(15,0)) }
            is("b1100".U) { new_wdata := Cat(write_src(31,16), modify_rdata(15,0)) }
            }
            io.sdram.o_valid := true.B;    io.sdram.o_addr := write_addr
            io.sdram.o_wdata := new_wdata; io.sdram.o_wstrb := "b1111".U
            when(io.sdram.i_ready) { io.bus.ready := true.B; state := sIdle }
        }
    }
}

// 大体同上，但使用o_wstrb和DQM实现直接的半字/字节写入
// 写延迟4周期，读延迟8周期
class SdrEmbed8M extends Module {
    val io = IO(new Bundle {
        val bus   = new DBusPortIO
        val sdram = new Sdr32bit8mIO
    })

    val sIdle :: sReq :: Nil = Enum(2)
    val state = RegInit(sIdle)

    // 默认赋初值
    io.sdram.o_valid := false.B
    io.sdram.o_addr  := 0.U
    io.sdram.o_wdata := 0.U
    io.sdram.o_wstrb := 0.U
    io.bus.rdata := 0.U
    io.bus.ready := false.B

    val sdram_addr = Cat(0.U(4.W), io.bus.addr(27,0))

    switch(state) {
        is(sIdle) {
            when(io.bus.valid) {
                io.sdram.o_valid := true.B
                io.sdram.o_addr  := sdram_addr
                io.sdram.o_wdata := io.bus.wdata
                io.sdram.o_wstrb := Mux(io.bus.wen, io.bus.ben, 0.U)
                state := sReq
            }
        }
        is(sReq) {
            io.sdram.o_valid := true.B
            io.sdram.o_addr  := sdram_addr
            io.sdram.o_wdata := io.bus.wdata
            io.sdram.o_wstrb := Mux(io.bus.wen, io.bus.ben, 0.U)
            when(io.sdram.i_ready) {
                io.bus.rdata := Mux(io.bus.wen, 0.U, io.sdram.i_rdata)
                io.bus.ready := true.B
                state := sIdle
            }
        }
    }
}

/*
class SimpleCache(
    val LINE_SIZE:  Int = 16,
    val LINE_BTYES: Int = 4
) extends Module {
    val io = IO(new Bundle {
        val cpu = new DBusPortIO()
        val mem = Flipped(new DBusPortIO())
    })

    // 计算参数
    val IDX_BITS = log2Ceil(LINE_SIZE)
    val TAG_BITS = 32 - log2Ceil(LINE_BTYES) - IDX_BITS

    // Cache行和标签
    val valid = RegInit(VecInit(Seq.fill(LINE_SIZE)(false.B)))
    val tags  = Reg(Vec(LINE_SIZE, UInt(TAG_BITS.W)))
    val data  = Reg(Vec(LINE_SIZE, UInt(WORD_LEN.W)))

    // 地址分解
    val addr = io.cpu.addr
    val idx  = addr(log2Ceil(LINE_BTYES) + IDX_BITS - 1, log2Ceil(LINE_BTYES))
    val tag  = addr(31, log2Ceil(LINE_BTYES) + IDX_BITS)

    // 命中判断
    val hit = valid(idx) && (tags(idx) === tag)

    // 默认输出
    io.cpu.rdata := 0.U
    io.cpu.ready := false.B

    // 内存端
    io.mem.valid := false.B
    io.mem.addr  := io.cpu.addr
    io.mem.addrb := io.cpu.addrb
    io.mem.wen   := io.cpu.wen
    io.mem.ben   := io.cpu.ben
    io.mem.wdata := io.cpu.wdata

    // 读命中
    val readHit = io.cpu.valid && !io.cpu.wen && hit
    dontTouch(readHit)

    when(io.cpu.valid) {
        when(io.cpu.wen) {
            // 写操作（写直达，写透）
            io.mem.valid := true.B
            io.mem.wen   := true.B
            io.mem.addr  := io.cpu.addr
            io.mem.ben   := io.cpu.ben
            io.mem.wdata := io.cpu.wdata
            io.cpu.ready := io.mem.ready

            // 写cache（只有写命中才同时写）
            when(hit) {
                val newWord = Wire(UInt(WORD_LEN.W))
                // 处理字节写 mask
                val wmask = Wire(Vec(4, Bool()))
                for(i <- 0 until 4) {
                    wmask(i) := io.cpu.ben(i)
                }
                // 多路组合字节覆盖
                newWord :=
                    Cat(
                        Mux(wmask(3), io.cpu.wdata(31,24), data(idx)(31,24)),
                        Mux(wmask(2), io.cpu.wdata(23,16), data(idx)(23,16)),
                        Mux(wmask(1), io.cpu.wdata(15,8),  data(idx)(15,8)),
                        Mux(wmask(0), io.cpu.wdata(7,0),   data(idx)(7,0))
                    )
                data(idx) := newWord
            }
        } .otherwise {
            // 读
            when(hit) {
                io.cpu.rdata := data(idx)
                io.cpu.ready := true.B
            } .otherwise {
                // miss 直通内存
                io.mem.valid := true.B
                io.mem.wen   := false.B
                io.mem.addr  := io.cpu.addr
                io.mem.ben   := io.cpu.ben
                io.cpu.ready := io.mem.ready
                io.cpu.rdata := io.mem.rdata
                // miss返回后写入cache
                when(io.mem.ready) {
                    valid(idx) := true.B
                    tags(idx)  := tag
                    data(idx)  := io.mem.rdata
                }
            }
        }
    }
}
*/

class SimpleCache(
    val LINE_SIZE:  Int = 16,
    val LINE_BTYES: Int = 4
) extends Module {
    val io = IO(new Bundle {
        val cpu = new DBusPortIO(32)
        val mem = Flipped(new DBusPortIO(32))
    })

    // 计算参数
    val IDX_BITS = log2Ceil(LINE_SIZE)
    val TAG_BITS = 32 - log2Ceil(LINE_BTYES) - IDX_BITS

    // Cache行和标签
    val valid = RegInit(VecInit(Seq.fill(LINE_SIZE)(false.B)))
    val tags  = Reg(Vec(LINE_SIZE, UInt(TAG_BITS.W)))
    val data  = Reg(Vec(LINE_SIZE, UInt(WORD_LEN.W)))

    // stage1: 解码并采样
    val s1_valid = io.cpu.valid

    val addr_s1 = io.cpu.addr
    val idx_s1  = addr_s1(log2Ceil(LINE_BTYES) + IDX_BITS - 1, log2Ceil(LINE_BTYES))
    val tag_s1  = addr_s1(31, log2Ceil(LINE_BTYES) + IDX_BITS)
    val wen_s1  = io.cpu.wen
    val ben_s1  = io.cpu.ben
    val wdata_s1 = io.cpu.wdata

    val hit_s1 = valid(idx_s1) && (tags(idx_s1) === tag_s1)
    val data_s1 = data(idx_s1)
    val tags_s1 = tags(idx_s1)

    // ---- pipeline寄存器 ----
    val s1_fire = s1_valid // 控制信号，可以后续拓展
    
    val s2_valid = RegNext(s1_fire, false.B)
    val idx_s2   = RegNext(idx_s1)
    val tag_s2   = RegNext(tag_s1)
    val wen_s2   = RegNext(wen_s1)
    val ben_s2   = RegNext(ben_s1)
    val wdata_s2 = RegNext(wdata_s1)
    val hit_s2   = RegNext(hit_s1)
    val data_s2  = RegNext(data_s1)
    val tags_s2  = RegNext(tags_s1)
    val addr_s2  = RegNext(addr_s1)
    // 这里只采样一次，不用对mem端再寄存

    io.cpu.rdata   := 0.U
    io.cpu.ready   := false.B

    io.mem.valid := false.B
    io.mem.wen   := false.B
    io.mem.addr  := addr_s2
    io.mem.ben   := ben_s2
    io.mem.wdata := wdata_s2

    val s2_done = RegNext(io.mem.ready, false.B)  // mem.ready拉高后结束mem.valid
    val rh_done = RegInit(false.B)  // 使读命中时的cpu.ready只拉高一拍
    rh_done := false.B  // 默认值
    // ----- stage2: 正常cache控制 -----
    when(s2_valid && !s2_done && !rh_done) {
        when(wen_s2) {
            // 写操作（写直达，写透）
            io.mem.valid := true.B
            io.mem.wen   := true.B
            io.cpu.ready := io.mem.ready
            // 写cache（只有写命中才同时写）
            when(hit_s2) {
                val newWord = Wire(UInt(WORD_LEN.W))
                val wmask = Wire(Vec(4, Bool()))
                for(i <- 0 until 4) {
                    wmask(i) := ben_s2(i)
                }
                newWord :=
                    Cat(
                        Mux(wmask(3), wdata_s2(31,24), data_s2(31,24)),
                        Mux(wmask(2), wdata_s2(23,16), data_s2(23,16)),
                        Mux(wmask(1), wdata_s2(15,8),  data_s2(15,8)),
                        Mux(wmask(0), wdata_s2(7,0),   data_s2(7,0))
                    )
                data(idx_s2) := newWord
                io.mem.wdata := newWord
                io.mem.ben   := "b1111".U
            }
        } .otherwise {
            // 读操作
            when(hit_s2) {
                io.cpu.rdata := data_s2
                io.cpu.ready := true.B
                rh_done := true.B
            } .otherwise {
                // miss发起访存
                io.mem.valid := true.B
                io.mem.wen   := false.B
                io.cpu.ready := io.mem.ready
                io.cpu.rdata := io.mem.rdata
                // miss返回后写入cache
                when(io.mem.ready) {
                    valid(idx_s2) := true.B
                    tags(idx_s2)  := tag_s2
                    data(idx_s2)  := io.mem.rdata
                }
            }
        }
    }
}


// 仿真用有延迟ram
class SimDRAM(depth: Int, DATA_WIDTH: Int = 32) extends Module {
    val io = IO(new Bundle {
        val bus = new DBusPortIO(DATA_WIDTH)
    })
    val BYTE_CNT = DATA_WIDTH / 8

    // 注意：使用ByteAddress, 所以ram大小为depth字（不是字节）
    val ram = Mem(depth, UInt(DATA_WIDTH.W))

    // 记录最后一次请求的相关信息与计数
    val doing   = RegInit(false.B)
    val cnt     = RegInit(0.U(2.W))
    val wenReg  = Reg(Bool())
    val addrReg = Reg(UInt(log2Ceil(depth).W))
    val wdataReg= Reg(UInt(DATA_WIDTH.W))
    val benReg  = Reg(UInt(BYTE_CNT.W))

    // 默认输出
    io.bus.rdata := 0.U
    io.bus.ready := false.B

    // address translation: 以字为单位
    val addr_word = io.bus.addr(log2Ceil(depth * BYTE_CNT)-1, log2Ceil(BYTE_CNT))  // 字节对齐

    when(io.bus.valid && !doing) {
        // 拉高请求，采样
        doing    := true.B
        cnt      := 0.U
        wenReg   := io.bus.wen
        addrReg  := addr_word
        wdataReg := io.bus.wdata
        benReg   := io.bus.ben
    }
    when(doing) {
        cnt := cnt + 1.U
        when(cnt === 3.U) {  // 四周期延迟完成
            doing := false.B
            io.bus.ready := true.B
            when(wenReg) {
                // 写操作，片选ben
                // 写Mask（按byte写）
                val old = ram.read(addrReg)
                val wmask = VecInit(Seq.tabulate(BYTE_CNT)(i =>
                    benReg(i)
                ))
                val wmaskData = Cat(
                    (0 until BYTE_CNT).reverse.map{i =>
                        Mux(benReg(i), wdataReg(8*(i+1)-1,8*i), old(8*(i+1)-1,8*i))
                    }
                )
                ram.write(addrReg, wmaskData)
            }
            .otherwise {
                // 读操作
                io.bus.rdata := ram.read(addrReg)
            }
        }
    }
}


class WideCache(
    val LINE_SIZE :  Int = 16,  // 行数
    val LINE_BYTES : Int = 16   // 每行4字
) extends Module {
    val WORD_LEN = 32
    val MEM_DATA_LEN = 128
    val MEM_DATA_WORDS = MEM_DATA_LEN / WORD_LEN  // =4
    val IDX_BITS = log2Ceil(LINE_SIZE)
    val TAG_BITS = 32 - log2Ceil(LINE_BYTES) - IDX_BITS
    val OFFSET_BITS = log2Ceil(LINE_BYTES) // 16B=log2(16)=4
    val WORD_OFFSET_BITS = log2Ceil(MEM_DATA_WORDS) // 2

    val io = IO(new Bundle {
        val cpu = new DBusPortIO(32)
        val mem = Flipped(new DBusPortIO(128))
    })

    // ---- 存储结构 ----
    val valid = RegInit(VecInit(Seq.fill(LINE_SIZE)(false.B)))
    val tags  = Reg(Vec(LINE_SIZE, UInt(TAG_BITS.W)))
    // data一行128bit
    val data  = Reg(Vec(LINE_SIZE, UInt(128.W)))

    // ---- 采样stage 1 ----
    val s1_valid = io.cpu.valid
    val addr_s1 = io.cpu.addr
    val idx_s1  = addr_s1(OFFSET_BITS + IDX_BITS - 1, OFFSET_BITS)
    val tag_s1  = addr_s1(31, OFFSET_BITS + IDX_BITS)
    val offset_s1 = addr_s1(OFFSET_BITS-1, 0)  // 行内字节偏移 [3:0] 范围 0~15
    val line_offset_s1 = addr_s1(OFFSET_BITS-1, 2) // 128bit一行内的哪个word: [3:2]，即0~3
    val wen_s1  = io.cpu.wen
    val ben_s1  = io.cpu.ben
    val wdata_s1 = io.cpu.wdata

    val hit_s1 = valid(idx_s1) && (tags(idx_s1) === tag_s1)
    val data_s1 = data(idx_s1)
    val tags_s1 = tags(idx_s1)

    // --- Pipeline Reg ---
    val s1_fire = s1_valid

    val s2_valid = RegNext(s1_fire, false.B)
    val idx_s2   = RegNext(idx_s1)
    val tag_s2   = RegNext(tag_s1)
    val wen_s2   = RegNext(wen_s1)
    val ben_s2   = RegNext(ben_s1)
    val wdata_s2 = RegNext(wdata_s1)
    val hit_s2   = RegNext(hit_s1)
    val data_s2  = RegNext(data_s1)
    val tags_s2  = RegNext(tags_s1)
    val addr_s2  = RegNext(addr_s1)
    val offset_s2 = RegNext(offset_s1)
    val line_offset_s2 = RegNext(line_offset_s1)

    val s2_done = RegNext(io.mem.ready, false.B)
    val rh_done = RegInit(false.B)
    rh_done := false.B

    // ---- 默认输出 ----
    io.cpu.rdata := 0.U
    io.cpu.ready := false.B

    io.mem.valid := false.B
    io.mem.wen := false.B
    io.mem.addr := Cat(addr_s2(31, OFFSET_BITS), 0.U(OFFSET_BITS.W)) // 16B对齐
    io.mem.ben := 0.U(16.W)
    io.mem.wdata := 0.U(128.W)

    // --- 把128bit分割成4个word ---
    def getWord(dword: UInt, idx: UInt): UInt = {
        require(dword.getWidth == 128)
        val slices = Wire(Vec(4, UInt(32.W)))
        for (i <- 0 until 4) {
            slices(i) := dword(32*(i+1)-1, 32*i)
        }
        slices(idx)
    }

    // 拼装128bit新数据, 改idx指定的word, 其他保持不变，ben决定字节mask
    def updateWord128(orig: UInt, idx: UInt, neww: UInt, ben: UInt): UInt = {
        val slices = Wire(Vec(4, UInt(32.W)))
        for (i <- 0 until 4) {
            val oldw = orig(32*(i+1)-1, 32*i)
            val wmask = Wire(Vec(4, Bool()))
            for (j <- 0 until 4) {
                wmask(j) := ben(j)
            }
            val tempw = Cat(
                Mux(wmask(3), neww(31,24), oldw(31,24)),
                Mux(wmask(2), neww(23,16), oldw(23,16)),
                Mux(wmask(1), neww(15,8),  oldw(15,8)),
                Mux(wmask(0), neww(7,0),   oldw(7,0))
            )
            slices(i) := Mux(idx === i.U, tempw, oldw)
        }
        slices.reverse.reduce(Cat(_, _))
    }

    // ---- stage2: 控制 ----
    when(s2_valid && !s2_done && !rh_done) {
        when(wen_s2) {
            // ---- 写操作 ----
            when(hit_s2) {
                // 命中: 直接更新data中的对应word
                data(idx_s2) := updateWord128(data(idx_s2), line_offset_s2, wdata_s2, ben_s2)
            }
            // 写穿透: 要同步发128bit写
            io.mem.valid := true.B
            io.mem.wen := true.B
            io.mem.addr := Cat(addr_s2(31, OFFSET_BITS), 0.U(OFFSET_BITS.W))
            // 拼装128bit写掩码ben: 只对目标word生效
            val mem_ben = Wire(UInt(16.W))
            mem_ben := (0 until 4).map{i =>
                Mux(line_offset_s2 === i.U, (ben_s2 << (i*4)).asUInt, 0.U)
            }.reduce(_|_)
            io.mem.ben := mem_ben
            // 写数据：目标word为wdata，其余为原值（这里直接填wdata即可，DDR3必须支持掩码写）
            val mem_wdata = Wire(UInt(128.W))
            mem_wdata := updateWord128(data(idx_s2), line_offset_s2, wdata_s2, ben_s2)
            io.mem.wdata := mem_wdata
            io.cpu.ready := io.mem.ready // 等待mem

        }.otherwise {
            // ---- 读操作 ----
            when(hit_s2) {
                // 命中：直接读出
                io.cpu.rdata := getWord(data(idx_s2), line_offset_s2)
                io.cpu.ready := true.B
                rh_done := true.B
            }.otherwise {
                // miss：发起mem读128bit
                io.mem.valid := true.B
                io.mem.wen := false.B
                io.mem.addr := Cat(addr_s2(31, OFFSET_BITS), 0.U(OFFSET_BITS.W))
                io.cpu.ready := io.mem.ready // 等待mem

                // 返回后，写入cache并给CPU
                when(io.mem.ready) {
                    valid(idx_s2) := true.B
                    tags(idx_s2) := tag_s2
                    data(idx_s2) := io.mem.rdata
                }
                io.cpu.rdata := getWord(io.mem.rdata, line_offset_s2) // 直接取mem来的对应word
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
