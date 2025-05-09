package PaSoC

import scala.FilePrepender._
// 导入scala.sacla中的prependLine方法, 往生成的.sv添加启用mem初始化的定义
import Consts._
import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import _root_.circt.stage.ChiselStage  // 生成systemverilog
import sys.process._   // 使用linux命令


class PaSoC extends Module {
    val io = IO(new Bundle {
        val inst_rx = Input(Bool())
        val gpio    = new GPIOPortIO()
        val pwm     = Output(UInt(PWM_LEN.W))
        val uart_tx = Output(Bool())
        val oled    = new OLEDLineIO()
        val exit    = Output(Bool())
    })

    val core    = Module(new PasoRV())
    val imem    = Module(new ITCM(1024))
    val dmem    = Module(new DTCM(4096))
    val gpio    = Module(new GPIOCtrl())
    val pwm     = Module(new PWMCtrl())
    val uart_tx = Module(new UartTxCtrl)
    val oled    = Module(new OledCtrl())

    // 添加可配置外设数量的总线选择器
    val dbus = Module(new DBusMux(5))
    core.io.ibus <> imem.io.bus
    core.io.dbus <> dbus.io.bus
    // 将dmem连接到dbusMux第0个外设端口
    dmem.io.bus    <> dbus.io.devs(0)
    gpio.io.bus    <> dbus.io.devs(1)
    pwm.io.bus     <> dbus.io.devs(2)
    uart_tx.io.bus <> dbus.io.devs(3)
    oled.io.bus    <> dbus.io.devs(4)
    // 外设输入输出
    imem.io.rx    <> io.inst_rx
    gpio.io.gpio  <> io.gpio
    pwm.io.pwm    <> io.pwm
    uart_tx.io.tx <> io.uart_tx
    oled.io.oled  <> io.oled
    // 程序结束标志
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
