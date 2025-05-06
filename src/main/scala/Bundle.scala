package PaSoC

import chisel3._       // chisel本体
import Consts._


class ImemPortIO extends Bundle {
    val addr = Input( UInt(WORD_LEN.W))
    val inst = Output(UInt(WORD_LEN.W))
}

// 数据总线接口，方向是外设的视角
class DBusPortIO extends Bundle {
    val valid = Input( Bool())  // 主机使能外设
    val addrb = Input( UInt(WORD_LEN.W))  // 在EX阶段提前发出的地址，用于读取有一周期延迟的bram,其余外设不应使用
    val addr  = Input( UInt(WORD_LEN.W))  // 在MEM阶段发出的地址，和其它信号同步
    val wen   = Input( Bool())  // 写使能
    val wdata = Input( UInt(WORD_LEN.W))  // 要写入外设的数据
    val rdata = Output(UInt(WORD_LEN.W))  // 从外设读到的数据
    val ready = Output(Bool())  // 外设读写完成，表示rdata有效
}

class GPIOPortIO() extends Bundle{
    val In  = Input(UInt(GPIO_LEN.W))   // 8位GPIO输入
    val Out = Output(UInt(GPIO_LEN.W))  // 8位GPIO输出
}

class CSRPortIO extends Bundle {
    val addrb = Input( UInt(CSR_ADDR_LEN.W))
    val addr  = Input( UInt(CSR_ADDR_LEN.W))
    val cmd   = Input( UInt(CSR_LEN.W))
    val wdata = Input( UInt(WORD_LEN.W))
    val rdata = Output(UInt(WORD_LEN.W))
}