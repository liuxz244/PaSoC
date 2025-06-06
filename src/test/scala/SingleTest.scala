package PaSoC

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random
import Consts._


object UartTestUtils {
    /** 向UART rx口注入一个字节（含起始/终止位） */
    def sendByteToRx(
        c: Module,   // 或 c: RawModule
        byte: Byte,
        baudPeriod: Int
    ): Unit = {
        val bits = Seq(0) ++ (0 until 8).map(i => ((byte >> i) & 1)) ++ Seq(1)
        def pokeRx(bit: Int): Unit = c match {
            case u: UartCtrl => u.io.rx.poke(bit.B)
            case p: PaSoC    => p.io.uart_rx.poke(bit.B)
            case _           => throw new Exception("Unknown UART module!")
        }
        for (bit <- bits) {
            pokeRx(bit)
            c.clock.step(baudPeriod)
        }
        pokeRx(1) // idle
        c.clock.step(baudPeriod)
    }

    def sendString(dut: PaSoC, str: String): Int = {
        var localStep = 0
        for(ch <- str) {
            dut.io.rx_flag.poke(true.B)
            dut.io.rx_data.poke(ch.toInt.U)
            dut.clock.step()
            localStep += 1
            dut.io.rx_flag.poke(false.B)
            dut.clock.step()
            localStep += 1
        }
        localStep
    }

}


class UartCtrlTest extends AnyFlatSpec with ChiselScalatestTester {
    import UartTestUtils._

    behavior of "UartCtrl"
    it should "receive uart data correctly" in {
        test(new UartCtrl).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
            c.clock.setTimeout(2000)  // 将超时延长到2000个clk

            // 1. idle初始：rx为高电平
            c.io.rx.poke(true.B)
            // 确保复位完成
            c.clock.step(20)

            // 2. 发送数据到rx
            val testByte: Byte = 0x3D
            val baudPeriod = (CLOCK_FREQ / BAUD_RATE).toInt
            sendByteToRx(c, testByte, baudPeriod)

            // 3. 验证rxFifo有新数据
            // 读寄存器0x08 (RxCount)，正好应该为1
            c.io.bus.valid.poke(true.B)
            c.io.bus.addr.poke("h08".U) // RxCount地址
            c.io.bus.wen.poke(false.B)
            c.clock.step()
            while(!c.io.bus.ready.peek().litToBoolean) { c.clock.step() }
            assert(c.io.bus.rdata.peek().litValue == 1)

            // 4. 读数据
            c.clock.step()
            c.io.bus.addr.poke("h04".U) // RxData
            while(!c.io.bus.ready.peek().litToBoolean) { c.clock.step() }
            assert((c.io.bus.rdata.peek().litValue & 0xff) == (testByte & 0xFF))
            c.clock.step()

            // 6. RxFifo应变回空
            c.io.bus.addr.poke("h08".U)
            c.clock.step()
            while(!c.io.bus.ready.peek().litToBoolean) { c.clock.step() }
            assert(c.io.bus.rdata.peek().litValue == 0)
        }
    }
}
