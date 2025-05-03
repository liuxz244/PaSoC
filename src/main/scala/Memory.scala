package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import chisel3.util.experimental.loadMemoryFromFileInline  // 向存储器写入初始值
import Consts._


// 单周期指令存储器
class ITCM extends Module{
    val io = IO(new ItemPortIo())

    val mem = Mem(256, UInt(32.W))  // 生成32位宽1KB寄存器作为存储器，256×32b=1KB
    
    loadMemoryFromFileInline(mem,"src/test/hex/ctest.hex")  // 向存储器写入初始程序

    val iaddr = Wire(UInt(WORD_LEN.W))
    iaddr := (io.addr >> 2.U(WORD_LEN.W))

    io.inst  := mem(iaddr)
    
}

// 同步读数据存储器，可综合为BRAM
class DSRM extends Module{  // 存储器模块
    val io = IO(new DtemPortIo())

    val mem = SyncReadMem(256 ,UInt(32.W))  // 生成32位宽1KB寄存器作为存储器，256×32b=1KB

    val daddr = Wire(UInt(WORD_LEN.W))
    daddr := (io.addr >> 2.U(WORD_LEN.W))

    io.rdata := mem(daddr)
    
    when(io.wen) {
        mem(daddr) := io.wdata
    }
}


// 单周期数据存储器（目前的流水线已不支持）
class DTCM extends Module{
    val io = IO(new DtemPortIo())

    val mem = Mem(256 ,UInt(32.W))  // 生成32位宽1KB寄存器作为存储器，256×32b=1KB

    val daddr = Wire(UInt(WORD_LEN.W))
    daddr := (io.addr >> 2.U(WORD_LEN.W))

    io.rdata := mem(daddr)
    
    when(io.wen) {
        mem(daddr) := io.wdata
    }
}