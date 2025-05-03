package PaSoC

import chisel3._       // chisel本体
import Consts._


class ImemPortIo extends Bundle {
    val addr = Input(UInt(WORD_LEN.W))
    val inst = Output(UInt(WORD_LEN.W))
}

class DmemPortIo extends Bundle {
    val addr  = Input(UInt(WORD_LEN.W))
    val rdata = Output(UInt(WORD_LEN.W))
    val wen   = Input(Bool())
    val wdata = Input(UInt(WORD_LEN.W))
}
