package PaSoC
// 要和CPU在同一包下才能找到SoC模块

import chisel3._       // chisel本体
import chiseltest._    // chisel测试
import org.scalatest.flatspec.AnyFlatSpec // scala测试

// CPU测试
class HexTest extends AnyFlatSpec with ChiselScalatestTester {
    "Hex Test" should "pass" in {
        test(new PaSoC).withAnnotations(Seq(WriteVcdAnnotation))
        { dut =>  // dut是PaSoC的例化
            // 当exit不为1时循环
            while(!dut.io.exit.peek().litToBoolean) {
                // 信号名.peek()获取信号值
                // litToBoolean()将chisel的Bool型转为scala的Boolean型
                dut.clock.step(1)  // 给一个时钟脉冲
            }
        }
    }
}

// 中断测试
class IrqTest extends AnyFlatSpec with ChiselScalatestTester {
    "Irq Test" should "pass" in {
        test(new PaSoC).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
            var clkCount  = 0
            val maxCycles = 1000 // 最多1000个时钟周期
            var timeout   = false

            dut.io.irq.poke(false.B)
            // 当exit没到 或 没到最大周期数
            while(!dut.io.exit.peek().litToBoolean && clkCount < maxCycles) {
                if (clkCount % 100 == 99) {
                    dut.io.irq.poke(true.B)
                } else {
                    dut.io.irq.poke(false.B)
                }
                dut.clock.step(1)
                clkCount += 1
            }

            if (clkCount >= maxCycles) {
                // 你可以用 fail 抛错
                fail(s"Timeout! 仿真超过 $maxCycles cycles")
            }
        }
    }
}
