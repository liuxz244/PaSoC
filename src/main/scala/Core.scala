package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
import chisel3.util.experimental.loadMemoryFromFileInline
import Consts._


class DivModule extends Module {
    val io = IO(new Bundle {
        val op1_data = Input(UInt(WORD_LEN.W))
        val op2_data = Input(UInt(WORD_LEN.W))
        val alu_fnc  = Input(UInt(ALU_FNC_LEN.W))
        val isDiv    = Input(Bool())
        val div_out  = Output(UInt(WORD_LEN.W))
        val stall    = Output(Bool())
    })

    val sIdle :: sCalc :: sDone :: Nil = Enum(3)
    val state = RegInit(sIdle)

    val dividend   = RegInit(0.U(WORD_LEN.W))
    val divisor    = RegInit(0.U(WORD_LEN.W))
    val quotient   = RegInit(0.U(WORD_LEN.W))
    val remainder  = RegInit(0.U(WORD_LEN.W))
    val cnt        = RegInit(0.U(6.W))

    // 标记需要处理的操作是有符号还是无符号
    val is_signed = io.alu_fnc === ALU_DIV || io.alu_fnc === ALU_REM

    // 输入的正负号
    val op1_sign = io.op1_data(31);  val op2_sign = io.op2_data(31)

    // "绝对值"形式输入
    val abs_op1 = Wire(UInt(WORD_LEN.W))
    abs_op1 := Mux(is_signed && op1_sign, (~io.op1_data).asUInt + 1.U, io.op1_data)
    val abs_op2 = Wire(UInt(WORD_LEN.W))
    abs_op2 := Mux(is_signed && op2_sign, (~io.op2_data).asUInt + 1.U, io.op2_data)

    // 记录符号以便最后修正
    val quotient_neg = RegInit(false.B)
    val remainder_neg = RegInit(false.B)

    // 启动条件
    val launch = (state === sIdle) && io.isDiv
    io.stall  := (state === sCalc) || launch

    val div_out_next = WireDefault(0.U(WORD_LEN.W))
    val div_out_reg  = RegInit(0.U(WORD_LEN.W))
    io.div_out := div_out_reg  // 默认从寄存器输出

    switch (state) {
        is (sIdle) {
            quotient  := 0.U
            remainder := 0.U
            cnt       := 0.U
            quotient_neg := false.B
            remainder_neg := false.B

            when (io.alu_fnc === ALU_DIVU || io.alu_fnc === ALU_REMU) {
                dividend  := io.op1_data
                divisor   := io.op2_data
                state := sCalc
            }
            .elsewhen(is_signed) {
                dividend  := abs_op1
                divisor   := abs_op2
                quotient_neg := op1_sign ^ op2_sign
                remainder_neg := op1_sign
                state := sCalc
            }
        }
        is (sCalc) {
            val temp_rem = Cat(remainder(30,0), dividend(31)).asUInt
            val sub      = temp_rem - divisor
            when(divisor === 0.U){
                quotient := Fill(WORD_LEN, 1.U)
                remainder := dividend
                state := sDone
            }
            .otherwise {
                when (sub(31) === 0.U) {
                    remainder := sub
                    quotient := Cat(quotient(30,0), 1.U)
                } .otherwise {
                    remainder := temp_rem
                    quotient := Cat(quotient(30,0), 0.U)
                }
                dividend := Cat(dividend(30,0), 0.U)
                cnt := cnt + 1.U
                when(cnt === 31.U){
                    state := sDone
                }
            }
        }
        is (sDone) {
            state := sIdle
            // 用组合逻辑生成输出
            when (io.alu_fnc === ALU_DIVU) {
                div_out_next := quotient
            }.elsewhen(io.alu_fnc === ALU_REMU) {
                div_out_next := remainder(31,0)
            }.elsewhen(io.alu_fnc === ALU_DIV) {
                val signed_quotient = Mux(quotient_neg,
                    (~quotient).asUInt + 1.U, // 商要取负
                    quotient)
                val overflow = (io.op1_data === "h80000000".U && io.op2_data === "hffffffff".U)
                div_out_next := Mux(overflow, "h80000000".U, signed_quotient)
            }.elsewhen(io.alu_fnc === ALU_REM) {
                val signed_remainder = Mux(remainder_neg,
                    (~remainder).asUInt + 1.U, // 余数要取负
                    remainder)
                val overflow = (io.op1_data === "h80000000".U && io.op2_data === "hffffffff".U)
                div_out_next := Mux(overflow, 0.U, signed_remainder)
            }
        }
    }

    // ========== 输出寄存器写入控制 ==========
    when (state === sDone) {
        div_out_reg := div_out_next  // 只在sDone写结果
        io.div_out  := div_out_next 
    }  // 其余情况下div_out_reg保持
}


class MulModule extends Module {
    val io = IO(new Bundle {
        val op1_data = Input(UInt(WORD_LEN.W))
        val op2_data = Input(UInt(WORD_LEN.W))
        val alu_fnc  = Input(UInt(ALU_FNC_LEN.W))
        val isMul    = Input(Bool())
        val mul_out  = Output(UInt(WORD_LEN.W))
        val stall    = Output(Bool())
    })

    // 状态指示
    val idle :: busy :: Nil = Enum(2)
    val state = RegInit(idle)

    // 把第一阶段所有乘法结果算好收集打拍
    val muls_reg     = Reg(UInt(64.W))
    val mulhsu_reg   = Reg(UInt(32.W))
    val mulhu_reg    = Reg(UInt(32.W))
    val alu_fnc_reg  = Reg(UInt(ALU_FNC_LEN.W))
    val mul_out_reg  = RegInit(0.U(WORD_LEN.W))
    val mul_out_comb = Wire(UInt(WORD_LEN.W))

    io.stall   := false.B     // 默认
    io.mul_out := mul_out_reg // 默认
    mul_out_comb := 0.U

    switch(state) {
        is(idle) {
            when(io.isMul) {
                // 第一拍: 计算结果
                muls_reg   := (io.op1_data.asSInt * io.op2_data.asSInt).asUInt
                mulhsu_reg := (io.op1_data.asSInt * io.op2_data).asUInt(63,32)
                mulhu_reg  := (io.op1_data * io.op2_data)(63,32)
                alu_fnc_reg := io.alu_fnc

                io.stall := true.B
                state := busy
            }
        }
        is(busy) {
            // 第二拍：根据锁存的 alu_fnc 选择输出
            mul_out_comb := MuxCase(0.U(WORD_LEN.W), Seq(
                (alu_fnc_reg === ALU_MUL)    -> muls_reg(31,0), 
                (alu_fnc_reg === ALU_MULH)   -> muls_reg(63,32), 
                (alu_fnc_reg === ALU_MULHSU) -> mulhsu_reg, 
                (alu_fnc_reg === ALU_MULHU)  -> mulhu_reg, 
            ))
            io.mul_out  := mul_out_comb
            mul_out_reg := mul_out_comb
            state := idle // 完成输出，下个周期可接收新输入
        }
    }
}


// 2位分支历史表
class BHT(val tableSize: Int = 64) extends Module {
    val idxWidth = log2Ceil(tableSize)
    val io = IO(new Bundle {
        // 查询
        val query         = Input(Bool())
        val query_pc      = Input(UInt(WORD_LEN.W))
        val predict_taken = Output(Bool())
        // 更新
        val update        = Input(Bool())
        val update_pc     = Input(UInt(WORD_LEN.W))
        val update_taken  = Input(Bool())
    })

    // 2位饱和计数器的 bht 表项，初始值一般为 2 (Weakly Taken) 或 0 (Strongly Not Taken)
    val bhtTable = Mem(tableSize, UInt(2.W))
    loadMemoryFromFileInline(bhtTable, "src/test/hex/bht.hex")  // 初始化BHT

    val query_idx = io.query_pc(idxWidth+1, 2)
    val bhtTable_read = bhtTable.read(query_idx)
    // 最高位决定预测
    io.predict_taken := Mux(io.query, bhtTable_read(1), false.B)

    when(io.update) {
        val update_idx = io.update_pc(idxWidth+1, 2)
        val counter = bhtTable.read(update_idx)

        val next_val = Mux(io.update_taken,
            Mux(counter === 3.U, 3.U, counter + 1.U),
            Mux(counter === 0.U, 0.U, counter - 1.U)
        )
        bhtTable.write(update_idx, next_val)
    }
}