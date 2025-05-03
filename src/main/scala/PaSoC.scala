package PaSoC

import scala.FilePrepender._
// 导入scala.sacla中的prependLine方法, 往生成的.sv添加启用mem初始化的定义
import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import _root_.circt.stage.ChiselStage  // 生成systemverilog
import sys.process._   // 使用linux命令


class PaSoC extends Module {
    val io = IO(new Bundle {
        val exit = Output(Bool())
    })
    val core   = Module(new PasoRV())
    val memory = Module(new Memory())
    core.io.imem <> memory.io.imem
    core.io.dmem <> memory.io.dmem
    io.exit := core.io.exit
}


object Main extends App {  // 生成.sv和.v的主函数
    ChiselStage.emitSystemVerilogFile(
        new PaSoC,
        Array( ),   // 不知道有什么用，但没了这个下面一行就要报错
        Array("-strip-debug-info","-disable-all-randomization"),
        // 禁用不可读的RANDOM和没用的注释
    )
    val filename = "PaSoC"
    val lineToAdd = "`define ENABLE_INITIAL_MEM_"
    prependLine(s"${filename}.sv", lineToAdd)
    val cmd = s"sv2v ${filename}.sv -w ${filename}.v"  // 要执行的linux命令
    val output = cmd.!!  // 执行命令并获取返回结果
}
