package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
// 导入common.scala中的Instructions定义和Consts定义
import Instructions._
import Consts._


class PasoRV extends Module {
    val io = IO(
        new Bundle {
            val imem = Flipped(new ImemPortIo())
            val dmem = Flipped(new DmemPortIo())
            val exit = Output(Bool())
        }
    )

    val regfile = Mem(32, UInt(WORD_LEN.W))

    //**********************************
    // Pipeline State Registers

    // IF/ID State
    val id_reg_pc             = RegInit(0.U(WORD_LEN.W))
    val id_reg_inst           = RegInit(0.U(WORD_LEN.W))

    // ID/EX State
    val exe_reg_pc            = RegInit(0.U(WORD_LEN.W))
    val exe_reg_inst          = RegInit(0.U(WORD_LEN.W))
    val exe_reg_wb_addr       = RegInit(0.U(ADDR_LEN.W))
    val exe_reg_op1_data      = RegInit(0.U(WORD_LEN.W))
    val exe_reg_op2_data      = RegInit(0.U(WORD_LEN.W))
    val exe_reg_rs2_data      = RegInit(0.U(WORD_LEN.W))
    val exe_reg_alu_fnc       = RegInit(0.U(ALU_FNC_LEN.W))
    val exe_reg_mem_wen       = RegInit(0.U(MEN_LEN.W))
    val exe_reg_rf_wen        = RegInit(0.U(REN_LEN.W))
    val exe_reg_wb_sel        = RegInit(0.U(WB_SEL_LEN.W))
    val exe_reg_imm_b_sext    = RegInit(0.U(WORD_LEN.W))

    // EX/MEM State
    val mem_reg_pc            = RegInit(0.U(WORD_LEN.W))
    val mem_reg_inst          = RegInit(0.U(WORD_LEN.W))
    val mem_reg_wb_addr       = RegInit(0.U(ADDR_LEN.W))
    val mem_reg_rf_wen        = RegInit(0.U(REN_LEN.W))
    val mem_reg_wb_sel        = RegInit(0.U(WB_SEL_LEN.W))
    val mem_reg_alu_out       = RegInit(0.U(WORD_LEN.W))

    // MEM/WB State
    val wb_reg_pc             = RegInit(0.U(WORD_LEN.W))
    val wb_reg_inst           = RegInit(0.U(WORD_LEN.W))
    val wb_reg_wb_addr        = RegInit(0.U(ADDR_LEN.W))
    val wb_reg_rf_wen         = RegInit(0.U(REN_LEN.W))
    val wb_reg_wb_data        = RegInit(0.U(WORD_LEN.W))


    //**********************************
    // Instruction Fetch (IF) Stage

    val if_reg_pc = RegInit(START_ADDR)
    io.imem.addr := if_reg_pc
    val if_inst = io.imem.inst

    val stall_flg     = Wire(Bool())
    val exe_br_flg    = Wire(Bool())
    val exe_br_target = Wire(UInt(WORD_LEN.W))
    val exe_jmp_flg   = Wire(Bool())
    val exe_alu_out   = Wire(UInt(WORD_LEN.W))

    val if_pc_plus4 = if_reg_pc + 4.U(WORD_LEN.W)
    val if_pc_next = MuxCase(if_pc_plus4, Seq(
            // 優先順位重要！ジャンプ成立とストールが同時発生した場合、ジャンプ処理を優先
        exe_br_flg  -> exe_br_target,
        exe_jmp_flg -> exe_alu_out,
        stall_flg   -> if_reg_pc, // stall
    ))
    if_reg_pc := if_pc_next


    // IF/ID Register
    id_reg_pc   := Mux(stall_flg, id_reg_pc, if_reg_pc)
    id_reg_inst := MuxCase(if_inst, Seq(
            // 優先順位重要！ジャンプ成立とストールが同時発生した場合、ジャンプ処理を優先
        (exe_br_flg || exe_jmp_flg) -> BUBBLE,
        stall_flg -> id_reg_inst, 
    ))


    //**********************************
    // Instruction Decode (ID) Stage

    // stall_flg検出用にアドレスのみ一旦デコード
    val id_rs1_addr_b = id_reg_inst(19, 15)
    val id_rs2_addr_b = id_reg_inst(24, 20)

    // EXとのデータハザード→stall
    val id_rs1_data_hazard = (exe_reg_rf_wen === REN_S) && (id_rs1_addr_b =/= 0.U) && (id_rs1_addr_b === exe_reg_wb_addr)
    val id_rs2_data_hazard = (exe_reg_rf_wen === REN_S) && (id_rs2_addr_b =/= 0.U) && (id_rs2_addr_b === exe_reg_wb_addr)
    stall_flg := (id_rs1_data_hazard || id_rs2_data_hazard)

    // branch,jump,stall時にIDをBUBBLE化
    val id_inst = Mux((exe_br_flg || exe_jmp_flg || stall_flg), BUBBLE, id_reg_inst)  

    val id_rs1_addr = id_inst(19, 15)
    val id_rs2_addr = id_inst(24, 20)
    val id_wb_addr  = id_inst(11, 7)

    val mem_wb_data = Wire(UInt(WORD_LEN.W))
    val id_rs1_data = MuxCase(regfile(id_rs1_addr), Seq(
        (id_rs1_addr === 0.U) -> 0.U(WORD_LEN.W),
        ((id_rs1_addr === mem_reg_wb_addr) && (mem_reg_rf_wen === REN_S)) -> mem_wb_data,   // MEMからフォワーディング
        ((id_rs1_addr === wb_reg_wb_addr ) && (wb_reg_rf_wen  === REN_S)) -> wb_reg_wb_data // WBからフォワーディング
    ))
    val id_rs2_data = MuxCase(regfile(id_rs2_addr),  Seq(
        (id_rs2_addr === 0.U) -> 0.U(WORD_LEN.W),
        ((id_rs2_addr === mem_reg_wb_addr) && (mem_reg_rf_wen === REN_S)) -> mem_wb_data,   // MEMからフォワーディング
        ((id_rs2_addr === wb_reg_wb_addr ) && (wb_reg_rf_wen  === REN_S)) -> wb_reg_wb_data // WBからフォワーディング
    ))

    val id_imm_i = id_inst(31, 20)
    val id_imm_i_sext = Cat(Fill(20, id_imm_i(11)), id_imm_i)
    val id_imm_s = Cat(id_inst(31, 25), id_inst(11, 7))
    val id_imm_s_sext = Cat(Fill(20, id_imm_s(11)), id_imm_s)
    val id_imm_b = Cat(id_inst(31), id_inst(7), id_inst(30, 25), id_inst(11, 8))
    val id_imm_b_sext = Cat(Fill(19, id_imm_b(11)), id_imm_b, 0.U(1.W))
    val id_imm_j = Cat(id_inst(31), id_inst(19, 12), id_inst(20), id_inst(30, 21))
    val id_imm_j_sext = Cat(Fill(11, id_imm_j(19)), id_imm_j, 0.U(1.W))
    val id_imm_u = id_inst(31,12)
    val id_imm_u_shifted = Cat(id_imm_u, Fill(12, 0.U))
    val id_imm_z = id_inst(19,15)
    val id_imm_z_uext = Cat(Fill(27, 0.U), id_imm_z)
    
    val csignals = ListLookup(id_inst,
                     List(ALU_X    , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  ),
        Array(
            LW    -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM),
            SW    -> List(ALU_ADD  , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  ),
            ADD   -> List(ALU_ADD  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            ADDI  -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU),
            SUB   -> List(ALU_SUB  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            AND   -> List(ALU_AND  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            OR    -> List(ALU_OR   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            XOR   -> List(ALU_XOR  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            ANDI  -> List(ALU_AND  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU),
            ORI   -> List(ALU_OR   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU),
            XORI  -> List(ALU_XOR  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU),
            SLL   -> List(ALU_SLL  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            SRL   -> List(ALU_SRL  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            SRA   -> List(ALU_SRA  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            SLLI  -> List(ALU_SLL  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU),
            SRLI  -> List(ALU_SRL  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU),
            SRAI  -> List(ALU_SRA  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU),
            SLT   -> List(ALU_SLT  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            SLTU  -> List(ALU_SLTU , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU),
            SLTI  -> List(ALU_SLT  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU),
            SLTIU -> List(ALU_SLTU , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU),
            BEQ   -> List(BR_BEQ   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  ),
            BNE   -> List(BR_BNE   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  ),
            BGE   -> List(BR_BGE   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  ),
            BGEU  -> List(BR_BGEU  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  ),
            BLT   -> List(BR_BLT   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  ),
            BLTU  -> List(BR_BLTU  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  ),
            JAL   -> List(ALU_ADD  , OP1_PC , OP2_IMJ, MEN_X, REN_S, WB_PC ),
            JALR  -> List(ALU_JALR , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_PC ),
            LUI   -> List(ALU_ADD  , OP1_X  , OP2_IMU, MEN_X, REN_S, WB_ALU),
            AUIPC -> List(ALU_ADD  , OP1_PC , OP2_IMU, MEN_X, REN_S, WB_ALU),
            )
        )
    val id_alu_fnc :: id_op1_sel :: id_op2_sel :: id_mem_wen :: id_rf_wen :: id_wb_sel :: Nil = csignals

    val id_op1_data = MuxCase(0.U(WORD_LEN.W), Seq(
        (id_op1_sel === OP1_RS1) -> id_rs1_data,
        (id_op1_sel === OP1_PC)  -> id_reg_pc,
        (id_op1_sel === OP1_IMZ) -> id_imm_z_uext
    ))
    val id_op2_data = MuxCase(0.U(WORD_LEN.W), Seq(
        (id_op2_sel === OP2_RS2) -> id_rs2_data,
        (id_op2_sel === OP2_IMI) -> id_imm_i_sext,
        (id_op2_sel === OP2_IMS) -> id_imm_s_sext,
        (id_op2_sel === OP2_IMJ) -> id_imm_j_sext,
        (id_op2_sel === OP2_IMU) -> id_imm_u_shifted
    ))


    // ID/EX register 
    exe_reg_pc            := id_reg_pc
    exe_reg_inst          := id_inst
    exe_reg_op1_data      := id_op1_data
    exe_reg_op2_data      := id_op2_data
    exe_reg_rs2_data      := id_rs2_data
    exe_reg_wb_addr       := id_wb_addr
    exe_reg_rf_wen        := id_rf_wen
    exe_reg_alu_fnc       := id_alu_fnc
    exe_reg_wb_sel        := id_wb_sel
    exe_reg_imm_b_sext    := id_imm_b_sext
    exe_reg_mem_wen       := id_mem_wen


    //**********************************
    // Execute (EX) Stage

    exe_alu_out := MuxCase(0.U(WORD_LEN.W), Seq(
        (exe_reg_alu_fnc === ALU_ADD)   -> (exe_reg_op1_data + exe_reg_op2_data),
        (exe_reg_alu_fnc === ALU_SUB)   -> (exe_reg_op1_data - exe_reg_op2_data),
        (exe_reg_alu_fnc === ALU_AND)   -> (exe_reg_op1_data & exe_reg_op2_data),
        (exe_reg_alu_fnc === ALU_OR)    -> (exe_reg_op1_data | exe_reg_op2_data),
        (exe_reg_alu_fnc === ALU_XOR)   -> (exe_reg_op1_data ^ exe_reg_op2_data),
        (exe_reg_alu_fnc === ALU_SLL)   -> (exe_reg_op1_data << exe_reg_op2_data(4, 0))(31, 0),
        (exe_reg_alu_fnc === ALU_SRL)   -> (exe_reg_op1_data >> exe_reg_op2_data(4, 0)).asUInt,
        (exe_reg_alu_fnc === ALU_SRA)   -> (exe_reg_op1_data.asSInt >> exe_reg_op2_data(4, 0)).asUInt,
        (exe_reg_alu_fnc === ALU_SLT)   -> (exe_reg_op1_data.asSInt < exe_reg_op2_data.asSInt).asUInt,
        (exe_reg_alu_fnc === ALU_SLTU)  -> (exe_reg_op1_data < exe_reg_op2_data).asUInt,
        (exe_reg_alu_fnc === ALU_JALR)  -> ((exe_reg_op1_data + exe_reg_op2_data) & ~1.U(WORD_LEN.W)),
        (exe_reg_alu_fnc === ALU_COPY1) -> exe_reg_op1_data
    ))

    // branch
    exe_br_flg := MuxCase(false.B, Seq(
        (exe_reg_alu_fnc === BR_BEQ)  ->  (exe_reg_op1_data === exe_reg_op2_data),
        (exe_reg_alu_fnc === BR_BNE)  -> !(exe_reg_op1_data === exe_reg_op2_data),
        (exe_reg_alu_fnc === BR_BLT)  ->  (exe_reg_op1_data.asSInt < exe_reg_op2_data.asSInt),
        (exe_reg_alu_fnc === BR_BGE)  -> !(exe_reg_op1_data.asSInt < exe_reg_op2_data.asSInt),
        (exe_reg_alu_fnc === BR_BLTU) ->  (exe_reg_op1_data < exe_reg_op2_data),
        (exe_reg_alu_fnc === BR_BGEU) -> !(exe_reg_op1_data < exe_reg_op2_data)
    ))
    exe_br_target := exe_reg_pc + exe_reg_imm_b_sext

    exe_jmp_flg := (exe_reg_wb_sel === WB_PC)


    // EX/MEM register
    mem_reg_pc         := exe_reg_pc
    mem_reg_inst       := exe_reg_inst
    mem_reg_wb_addr    := exe_reg_wb_addr
    mem_reg_alu_out    := exe_alu_out
    mem_reg_rf_wen     := exe_reg_rf_wen
    mem_reg_wb_sel     := exe_reg_wb_sel

    // 由于BRAM的读取有一周期延迟，需要提前发出地址
    io.dmem.addr  := exe_alu_out
    io.dmem.wen   := exe_reg_mem_wen
    io.dmem.wdata := exe_reg_rs2_data

    //**********************************
    // Memory Access Stage

    mem_wb_data := MuxCase(mem_reg_alu_out, Seq(
        (mem_reg_wb_sel === WB_MEM) -> io.dmem.rdata,
        (mem_reg_wb_sel === WB_PC)  -> (mem_reg_pc + 4.U(WORD_LEN.W)),
    ))


    // MEM/WB regsiter
    wb_reg_pc      := mem_reg_pc
    wb_reg_inst    := mem_reg_inst
    wb_reg_wb_addr := mem_reg_wb_addr
    wb_reg_rf_wen  := mem_reg_rf_wen
    wb_reg_wb_data := mem_wb_data


    //**********************************
    // Writeback (WB) Stage

    when(wb_reg_rf_wen === REN_S) {
        regfile(wb_reg_wb_addr) := wb_reg_wb_data
    }


    //**********************************
    // IO & Debug
    io.exit := (wb_reg_inst === UNIMP)
    printf(p"if_reg_pc        : 0x${Hexadecimal(if_reg_pc)}\n")
    printf(p"id_reg_pc        : 0x${Hexadecimal(id_reg_pc)}\n")
    printf(p"exe_reg_pc       : 0x${Hexadecimal(exe_reg_pc)}\n")
    printf(p"exe_reg_op1_data : 0x${Hexadecimal(exe_reg_op1_data)}\n")
    printf(p"exe_reg_op2_data : 0x${Hexadecimal(exe_reg_op2_data)}\n")
    printf(p"exe_alu_out      : 0x${Hexadecimal(exe_alu_out)}\n")
    printf(p"mem_reg_pc       : 0x${Hexadecimal(mem_reg_pc)}\n")
    printf(p"mem_wb_data      : 0x${Hexadecimal(mem_wb_data)}\n")
    printf(p"wb_reg_wb_data   : 0x${Hexadecimal(wb_reg_wb_data)}\n")
    printf("---------\n")
}