package PaSoC
// 要和CPU在同一包下才能找到SoC模块

import chisel3._       // chisel本体
import chiseltest._    // chisel测试
import org.scalatest.flatspec.AnyFlatSpec // scala测试
import scala.io.StdIn


// SoC测试
class HexTest extends AnyFlatSpec with ChiselScalatestTester {
    "Hex Test" should "pass" in {
        val hexFile = sys.env.getOrElse("PASOC_INIT_HEX", " ")
        println(s"hexFile used: $hexFile\n")  // 初始程序文件名
        test(new PaSoC(hexFile)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
            dut.clock.setTimeout(0)  // 关闭Chisel原本的超时机制
            var stepCount = 0
            val maxCycles = 2000
            // 当exit不为1且未超出最大步数时循环
            while(!dut.io.exit.peek().litToBoolean && stepCount < maxCycles) {
                dut.clock.step(1)  // 给一个时钟脉冲
                stepCount += 1
            }
            if (stepCount >= maxCycles) {
                println(s"\nWarning: 超出最大仿真步数 $maxCycles 后仍未退出，仿真被终止！")
            } else {
                println(s"\n仿真正常结束, 共运行 $stepCount 个周期。")
            }
        }
    }
}

// GPIO测试
class GpioTest extends AnyFlatSpec with ChiselScalatestTester {
    "Gpio Test" should "pass" in {
        val hexFile = sys.env.getOrElse("PASOC_INIT_HEX", " ")
        println(s"hexFile used: $hexFile\n")
        test(new PaSoC(hexFile)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
            dut.clock.setTimeout(0)
            var stepCount = 0
            val maxCycles = 3000
            while(!dut.io.exit.peek().litToBoolean && stepCount < maxCycles) {
                if (dut.io.gpio.debug.peek().litToBoolean) {
                    println("正在读取GPIO, 输入2位16进制数: ")  // 貌似数字给少了，只有8个输入
                    val input = StdIn.readLine().trim
                    // 校验格式
                    if(input.matches("(?i)[0-9a-f]{2}")) {
                        val value = Integer.parseInt(input, 16)
                        dut.io.gpio.In.poke(value.U)
                        println("\n")
                    } else {
                        println(s"输入无效, 将gpio.In赋为0")
                        dut.io.gpio.In.poke(0.U)
                    }
                }
                if (stepCount == 100) { dut.io.gpio.In.poke(8.U) }  // 在第101个周期更新GPIO输入
                dut.clock.step(1)  // 时钟周期增加
                stepCount += 1
            }
            if (stepCount >= maxCycles) {
                println(s"\nWarning: 超出最大仿真步数 $maxCycles 后仍未退出，仿真被终止！")
            } else {
                println(s"\n仿真正常结束, 共运行 $stepCount 个周期。")
            }
        }
    }
}

// UART测试
class UartTest extends AnyFlatSpec with ChiselScalatestTester {
    import UartTestUtils._
    import Consts._
    "Uart Test" should "pass" in {
        val hexFile = sys.env.getOrElse("PASOC_INIT_HEX", " ")
        println(s"hexFile used: $hexFile\n")  // 初始程序文件名
        test(new PaSoC(hexFile)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
            dut.clock.setTimeout(0)  // 关闭Chisel原本的超时机制
            var stepCount = 0
            val maxCycles = 5000
            // 当exit不为1且未超出最大步数时循环
            while(!dut.io.exit.peek().litToBoolean && stepCount < maxCycles) 
            {
                if (stepCount == 100) { 
                    stepCount += sendString(dut, "ABC\n6\n0x1D\n")  // 发送11个字符用22个周期
                } else {
                    dut.clock.step(1)
                    stepCount += 1
                }
            }
            if (stepCount >= maxCycles) {
                println(s"\nWarning: 超出最大仿真步数 $maxCycles 后仍未退出，仿真被终止！")
            } else {
                println(s"\n仿真正常结束, 共运行 $stepCount 个周期。")
            }
        }
    }
}