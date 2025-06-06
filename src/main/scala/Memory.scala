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