package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import chisel3.util.experimental.loadMemoryFromFileInline  // 向存储器写入初始值
import Consts._


// 单周期指令存储器
class ITCM(val depth: Int) extends Module{
    val io = IO(new ImemPortIO())

    val mem = Mem(256, UInt(32.W))  // 生成32位宽1KB寄存器作为存储器，256×32b=1KB
    loadMemoryFromFileInline(mem,"/media/lxz/KP200pro/FPGA/Chisel/PaSoC/src/test/hex/uart_tx.hex")  
    // 向存储器写入初始程序

    // 计算地址位宽，地址作为字节地址，读写地址对齐为32位字（4字节），所以地址右移2位
    val addrWidth = log2Ceil(depth)  // 地址位宽 = 内存深度的log2

    val iaddr = Wire(UInt(addrWidth.W))
    iaddr := io.addr(addrWidth + 1, 2)

    io.inst  := mem(iaddr)

}

// 同步读数据存储器，可综合为BRAM
class DSRM(val depth: Int) extends Module{  // 存储器模块
    val io = IO(new Bundle{
        val bus = new DBusPortIO()
    })
    
    val mem = SyncReadMem(depth ,UInt(32.W))  // 生成32位宽存储器，可被综合为BRAM

    // 计算地址位宽，地址作为字节地址，读写地址对齐为32位字（4字节），所以地址右移2位
    val addrWidth = log2Ceil(depth)  // 地址位宽 = 内存深度的log2

    // 在EX阶段提前发出的读取地址，因为SyncReadMem要等一周期才能取到数据
    val daddrb = Wire(UInt(addrWidth.W))
    daddrb := io.bus.addrb(addrWidth + 1, 2) 

    val daddr = Wire(UInt(addrWidth.W))
    daddr := io.bus.addr(addrWidth + 1, 2)

    io.bus.rdata := mem(daddrb)
    io.bus.ready := io.bus.valid
    
    when(io.bus.wen) {
        mem(daddr) := io.bus.wdata
    }
}


// 单周期数据存储器
class DTCM(val depth: Int) extends Module{
    val io = IO(new Bundle{
        val bus = new DBusPortIO()
    })

    val mem = Mem(depth ,UInt(32.W))  // 生成32位宽寄存器作为存储器，会占用大量LUT

    // 计算地址位宽，地址作为字节地址，读写地址对齐为32位字（4字节），所以地址右移2位
    val addrWidth = log2Ceil(depth)  // 地址位宽 = 内存深度的log2

    val daddr = Wire(UInt(addrWidth.W))
    daddr := io.bus.addr(addrWidth + 1, 2)

    io.bus.rdata := mem(daddr)
    io.bus.ready := io.bus.valid
    
    when(io.bus.wen) {
        mem(daddr) := io.bus.wdata
    }
}


// CSR寄存器
class CSRFile extends Module {
  val io = IO(new CSRPortIO)

  val csr_regfile = SyncReadMem(4096, UInt(WORD_LEN.W))

  // 读取操作
  io.rdata := csr_regfile(io.addrb)

  // 写操作，写命令类型为csr_cmd
  when(io.cmd =/= 0.U) {
    csr_regfile(io.addr) := io.wdata
  }
}
