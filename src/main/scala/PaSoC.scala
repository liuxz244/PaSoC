package PaSoC

import scala.FilePrepender._
// 导入scala.sacla中的prependLine方法, 往生成的.sv添加启用mem初始化的定义
import Consts._
import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import _root_.circt.stage.ChiselStage  // 生成systemverilog
import sys.process._   // 使用linux命令


class PaSoC(initHex: String, with_cache: Boolean = true) extends Module {
    val io = IO(new Bundle {
        //val inst_rx = Input(Bool())
        val gpio    = new GPIOPortIO()
        val pwm     = Output(UInt(PWM_LEN.W))
        val uart_tx = Output(Bool())
        val uart_rx = Input( Bool())
        val irq     = Input(UInt(7.W))
        val dram = Flipped(new DBusPortIO(32))
    })

    val core  = Module(new PasoRV())
    val imem  = Module(new ITCM(4096, initHex))
    val dmem  = Module(new DTCM(2048, initHex))
    val gpio  = Module(new GPIOCtrl())
    val pwm   = Module(new PWMCtrl())
    val uart  = Module(new UartCtrl())
    val plic  = Module(new PLIC())
    val clint = Module(new CLINT())

    // 添加可配置外设数量的总线选择器
    val dbus = Module(new DBusMux(7))
    core.io.ibus <> imem.io.bus
    core.io.dbus <> dbus.io.bus
    // 将dmem连接到dbusMux第1个外设端口，gcc规定不能和ITCM的地址重叠
    gpio.io.bus  <> dbus.io.devs(0)
    dmem.io.bus  <> dbus.io.devs(1)
    pwm.io.bus   <> dbus.io.devs(2)
    uart.io.bus  <> dbus.io.devs(3)
    plic.io.bus  <> dbus.io.devs(5)
    clint.io.bus <> dbus.io.devs(6)

    dmem.io.addrb  := core.io.addrb    // 对BRAM需要在提前发出地址
    plic.io.irq_in := Cat(gpio.io.irq, io.irq)  // PLIC中断源输入
    core.io.clint  := clint.io.irq     // 连接定时器中断
    core.io.plic   := plic.io.irq_out  // 连接外部中断

    // 外设输入输出
    //imem.io.rx <> io.inst_rx
    gpio.io.gpio <> io.gpio
    pwm.io.pwm   <> io.pwm
    uart.io.tx   <> io.uart_tx
    uart.io.rx   <> io.uart_rx
    
    if(with_cache) {
        val cache = Module(new SimpleCache())
        cache.io.cpu <> dbus.io.devs(4)
        cache.io.mem <> io.dram
    } else {
        dbus.io.devs(4) <> io.dram
    }

    uart.io.rx_flag := false.B
    uart.io.rx_data := 0.U
}


class PaSoCsim(initHex: String, with_cache: Boolean = true) extends Module {
    val io = IO(new Bundle {
        //val inst_rx = Input(Bool())
        val gpio    = new GPIOPortIO()
        val pwm     = Output(UInt(PWM_LEN.W))
        val uart_tx = Output(Bool())
        val uart_rx = Input( Bool())
        val irq     = Input(UInt(7.W))
        val exit    = Output(Bool())
        val rx_flag = Input( Bool())
        val rx_data = Input(UInt(8.W))
    })

    val core  = Module(new PasoRV())
    val imem  = Module(new ITCM(4096, initHex))
    val dmem  = Module(new DTCM(2048, initHex))
    val dram  = Module(new SimDRAM(8192, 128))
    val gpio  = Module(new GPIOCtrl())
    val pwm   = Module(new PWMCtrl())
    val uart  = Module(new UartCtrl())
    val plic  = Module(new PLIC())
    val clint = Module(new CLINT())

    // 添加可配置外设数量的总线选择器
    val dbus = Module(new DBusMux(7))
    core.io.ibus <> imem.io.bus
    core.io.dbus <> dbus.io.bus
    // 将dmem连接到dbusMux第1个外设端口，gcc规定不能和ITCM的地址重叠
    gpio.io.bus  <> dbus.io.devs(0)
    dmem.io.bus  <> dbus.io.devs(1)
    pwm.io.bus   <> dbus.io.devs(2)
    uart.io.bus  <> dbus.io.devs(3)
    plic.io.bus  <> dbus.io.devs(5)
    clint.io.bus <> dbus.io.devs(6)
    
    dmem.io.addrb  := core.io.addrb   // 对BRAM需要在提前发出地址
    plic.io.irq_in := Cat(gpio.io.irq, io.irq)  // PLIC中断源输入
    core.io.clint := clint.io.irq     // 连接定时器中断
    core.io.plic  := plic.io.irq_out  // 连接外部中断

    // 外设输入输出
    //imem.io.rx   <> io.inst_rx
    gpio.io.gpio   <> io.gpio
    pwm.io.pwm     <> io.pwm
    uart.io.tx     <> io.uart_tx
    uart.io.rx     <> io.uart_rx

    if(with_cache) {
        val cache = Module(new WideCache())
        cache.io.cpu <> dbus.io.devs(4)
        cache.io.mem <> dram.io.bus
    } else {
        dbus.io.devs(4) <> dram.io.bus
    }

    uart.io.rx_flag := io.rx_flag
    uart.io.rx_data := io.rx_data
    
    io.exit := core.io.exit  // 程序结束标志
}


object Main extends App {  // 生成.sv和.v的主函数
    val hexFile = sys.env.getOrElse("PASOC_INIT_HEX", " ")
    ChiselStage.emitSystemVerilogFile(
        new PaSoC(hexFile),
        Array( ),   // 将生成的Verilog代码传递给ChiselStage
        Array("-strip-debug-info","-disable-all-randomization"),
        // 禁用不可读的RANDOM和没用的注释
    )
    val filename = "PaSoC"; val svFile = s"${filename}.sv"
    convertReadmemhPathsToWindows(svFile)  // Windows路径转换
    val lineToAdd = "`define ENABLE_INITIAL_MEM_"
    prependLine(s"${filename}.sv", lineToAdd)  // 启用MEM初始化
    addRwAddrCollisionAttr(svFile)  // 给Vivado添加blockram先写设置
    val cmd = s"sv2v ${filename}.sv -w ${filename}.v"  // 要执行的linux命令
    val output = cmd.!!  // 执行命令并获取返回结果
}
