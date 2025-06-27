package PaSoC

import chisel3._   // chisel本体
import Consts._


class IBusPortIO extends Bundle {
    val addrb = Input( UInt(WORD_LEN.W))
    val inst  = Output(UInt(WORD_LEN.W))
}

// 数据总线接口，方向是外设的视角
class DBusPortIO extends Bundle {
    val valid = Input( Bool())  // 主机使能外设
    val addrb = Input( UInt(WORD_LEN.W))  // 在EX阶段提前发出的地址
    val addr  = Input( UInt(WORD_LEN.W))  // 字节地址不是字地址
    val wen   = Input( Bool())     // 写使能，高代表写，低代表读
    val ben   = Input( UInt(4.W))  // 字节使能（只影响写）
    val wdata = Input( UInt(WORD_LEN.W))  // 要写入外设的数据
    val rdata = Output(UInt(WORD_LEN.W))  // 从外设读到的数据
    val ready = Output(Bool())  // 外设读写完成，表示rdata有效
}

class GPIOPortIO() extends Bundle{
    val In  = Input(UInt(GPIO_LEN.W))   // GPIO输入
    val Out = Output(UInt(GPIO_LEN.W))  // GPIO输出
    val debug = Output(Bool())  // 要读取输入时拉高，仿真用
}

class OLEDLineIO extends Bundle {
    val str_line0 = Output(UInt(128.W))
    val str_line1 = Output(UInt(128.W))
    val str_line2 = Output(UInt(128.W))
    val str_line3 = Output(UInt(128.W))
}

class Sdr32bit8mIO extends Bundle {
    val o_valid  = Output(Bool())      // 请求数据操作
    val i_ready  = Input(Bool())       // 外设响应信号
    val o_addr   = Output(UInt(32.W))  // 地址
    val o_wdata  = Output(UInt(32.W))  // 写数据
    val o_wstrb  = Output(UInt(4.W))   // 写使能位，每一位对应一个字节有效，为0则读
    val i_rdata  = Input(UInt(32.W))   // 读数据
}


/*
class DDR16b128mIO extends Bundle {
    val cmd      = Output(UInt(3.W))    // 命令：0写1读
    val addr     = Output(UInt(28.W))   // 地址输入，应对齐为8的整数倍
    val cmd_en   = Output(Bool())       // 命令和地址有效信号
    val cmd_rdy  = Input(Bool())        // 可以接收命令和地址
    val wr_rdy   = Input(Bool())        // 可以接收写数据
    val wr_data  = Output(UInt(128.W))  // 写数据
    val wr_en    = Output(Bool())       // 写使能
    val wr_end   = Output(Bool())       // 当前clk是此组wr_data的最后一个clk（一般和wr_en相同）
    val wr_mask  = Output(UInt(16.W))   // wr_data屏蔽，每一个bit对应wr_data的一个字节，为1屏蔽有效
    val rd_data  = Input(UInt(128.W))   // 读数据
    val rd_valid = Input(Bool())        // 读数据有效
    val rd_end   = Input(Bool())        // 当前clk是此组rd_data的最后一个clk（一般和rd_valid相同）
    val init_cpl = Input(Bool())        // DDR IP初始化完成
}
*/
/*
// Chisel与SDRAM Verilog顶层交互
class SdramDriverPortIO(val dataWidthBits: Int = 16) extends Bundle {
    val operate_addr  = Output(UInt(26.W))  // 操作数据地址
    val operate_nums  = Output(UInt(12.W))  // 单次读写数据数量
    val send_data     = Output(UInt(dataWidthBits.W))  // 要写入的数据
    val start         = Output(Bool())  // 开始读写，持续一个高电平
    val mode          = Output(Bool())  // 0为写入，1为读取
    val rec_data      = Input(UInt(dataWidthBits.W))  // 读取到的数据
    val data_ready    = Input(Bool())  
    // 读取时每收到一次高电平，就要读取rec_data；发送时每收到一次高电平，就要将send_data换成下一个要发送的数据
    val done          = Input(Bool())  // 整个读写完成
    val idle          = Input(Bool())  // 模块空闲，可以开始读写
}
*/