package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import chisel3.util.experimental.loadMemoryFromFileInline  // 向存储器写入初始值
import Consts._


class Memory extends Module{  // 存储器模块
    val io = IO(new Bundle {
        val imem = new ItemPortIo()  // 例化上面定义的指令接口模板
        val dmem = new DtemPortIo()  // 例化上面定义的数据接口模板
    })

    val mem = Mem(4096,UInt(32.W))  // 生成32位宽16KB寄存器作为存储器，4096×32b=16KB

    loadMemoryFromFileInline(mem,"src/test/hex/ctest.hex")  // 向存储器写入初始程序
    /*  教程里用的8位存储器，取指时要一次取4个地址的数据，所以pc寄存器每次+4
    io.imem.inst := Cat(
        mem(io.imem.addr + 3.U(WORD_LEN.W)),
        mem(io.imem.addr + 2.U(WORD_LEN.W)),
        mem(io.imem.addr + 1.U(WORD_LEN.W)),
        mem(io.imem.addr),
    )   // 拼接4个地址存储的32位指令,使用小端排序 
    */

    val iaddr = Wire(UInt(WORD_LEN.W))
    val daddr = Wire(UInt(WORD_LEN.W))
    iaddr := (io.imem.addr >> 2.U(WORD_LEN.W))
    daddr := (io.dmem.addr >> 2.U(WORD_LEN.W))

    io.imem.inst  := mem(iaddr)
    io.dmem.rdata := mem(daddr)
    
    when(io.dmem.wen) {
        mem(daddr) := io.dmem.wdata
    }
}