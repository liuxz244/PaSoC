package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
// 导入common.scala中的Instructions定义和Consts定义
import Instructions._
import Consts._


class PasoRV extends Module {
    val io = IO(
        new Bundle {
            val imem = Flipped(new ImemPortIO())
            val dbus = Flipped(new DBusPortIO())
            val exit = Output(Bool())
        }
    )

    val regfile = RegInit(VecInit(Seq.fill(32)(0.U(WORD_LEN.W))))
    regfile(2) := "h00000ff0".U  // 寄存器sp(x2)初始化，作为堆栈指针被C语言调用
    
    val csr = Module(new CSRFile())  // CSR寄存器模块
    val trap_vector = RegInit(0.U(WORD_LEN.W))

    //**********************************
    // Pipeline State Registers

    // IF/ID State
    val id_reg_pc          = RegInit(0.U(WORD_LEN.W))
    val id_reg_inst        = RegInit(0.U(WORD_LEN.W))

    // ID/EX State
    val exe_reg_pc         = RegInit(0.U(WORD_LEN.W))
    val exe_reg_inst       = RegInit(0.U(WORD_LEN.W))
    val exe_reg_wb_addr    = RegInit(0.U(ADDR_LEN.W))
    val exe_reg_op1_data   = RegInit(0.U(WORD_LEN.W))
    val exe_reg_op2_data   = RegInit(0.U(WORD_LEN.W))
    val exe_reg_rs2_data   = RegInit(0.U(WORD_LEN.W))
    val exe_reg_alu_fnc    = RegInit(0.U(ALU_FNC_LEN.W))
    val exe_reg_mem_wen    = RegInit(0.U(MEN_LEN.W))
    val exe_reg_rf_wen     = RegInit(0.U(REN_LEN.W))
    val exe_reg_wb_sel     = RegInit(0.U(WB_SEL_LEN.W))
    val exe_reg_csr_addr   = RegInit(0.U(CSR_ADDR_LEN.W))
    val exe_reg_csr_cmd    = RegInit(0.U(CSR_LEN.W))
    val exe_reg_imm_b_sext = RegInit(0.U(WORD_LEN.W))

    // EX/MEM State
    val mem_reg_pc         = RegInit(0.U(WORD_LEN.W))
    val mem_reg_inst       = RegInit(0.U(WORD_LEN.W))
    val mem_reg_wb_addr    = RegInit(0.U(ADDR_LEN.W))
    val mem_reg_rf_wen     = RegInit(0.U(REN_LEN.W))
    val mem_reg_wb_sel     = RegInit(0.U(WB_SEL_LEN.W))
    val mem_reg_csr_addr   = RegInit(0.U(CSR_ADDR_LEN.W))
    val mem_reg_csr_cmd    = RegInit(0.U(CSR_LEN.W))
    val mem_reg_alu_out    = RegInit(0.U(WORD_LEN.W))
    val mem_reg_mem_wen    = RegInit(0.U(MEN_LEN.W))
    val mem_reg_op1_data   = RegInit(0.U(WORD_LEN.W))
    val mem_reg_rs2_data   = RegInit(0.U(WORD_LEN.W))

    // MEM/WB State
    val wb_reg_pc          = RegInit(0.U(WORD_LEN.W))
    val wb_reg_inst        = RegInit(0.U(WORD_LEN.W))
    val wb_reg_wb_addr     = RegInit(0.U(ADDR_LEN.W))
    val wb_reg_rf_wen      = RegInit(0.U(REN_LEN.W))
    val wb_reg_wb_data     = RegInit(0.U(WORD_LEN.W))


    //**********************************
    // Instruction Fetch (IF) Stage

    val if_reg_pc = RegInit(START_ADDR)
    io.imem.addr := if_reg_pc
    val if_inst = io.imem.inst

    val stall_hazard = Wire(Bool())  // 出现流水线数据冒险, 需要暂停流水线
    val stall_bus    = Wire(Bool())  // 从机未准备好响应，  需要暂停流水线
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
        (if_inst === ECALL) -> trap_vector, // go to trap_vector
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

    // stall_hazard検出用にアドレスのみ一旦デコード
    val id_rs1_addr_b = id_reg_inst(19, 15)
    val id_rs2_addr_b = id_reg_inst(24, 20)

    // EXとのデータハザード→stall
    val id_rs1_data_hazard = (exe_reg_rf_wen === REN_S) && (id_rs1_addr_b =/= 0.U) && (id_rs1_addr_b === exe_reg_wb_addr)
    val id_rs2_data_hazard = (exe_reg_rf_wen === REN_S) && (id_rs2_addr_b =/= 0.U) && (id_rs2_addr_b === exe_reg_wb_addr)
    stall_hazard := (id_rs1_data_hazard || id_rs2_data_hazard)

    // branch,jump,stall時にIDをBUBBLE化
    val id_inst = Mux((exe_br_flg || exe_jmp_flg || stall_hazard), BUBBLE, id_reg_inst)  

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
                     List(ALU_X    , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
        Array(
            LW    -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, CSR_X),
            SW    -> List(ALU_ADD  , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  , CSR_X),
            ADD   -> List(ALU_ADD  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            ADDI  -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
            SUB   -> List(ALU_SUB  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            AND   -> List(ALU_AND  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            OR    -> List(ALU_OR   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            XOR   -> List(ALU_XOR  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            ANDI  -> List(ALU_AND  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
            ORI   -> List(ALU_OR   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
            XORI  -> List(ALU_XOR  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
            SLL   -> List(ALU_SLL  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            SRL   -> List(ALU_SRL  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            SRA   -> List(ALU_SRA  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            SLLI  -> List(ALU_SLL  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
            SRLI  -> List(ALU_SRL  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
            SRAI  -> List(ALU_SRA  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
            SLT   -> List(ALU_SLT  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            SLTU  -> List(ALU_SLTU , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, CSR_X),
            SLTI  -> List(ALU_SLT  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
            SLTIU -> List(ALU_SLTU , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, CSR_X),
            BEQ   -> List(BR_BEQ   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
            BNE   -> List(BR_BNE   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
            BGE   -> List(BR_BGE   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
            BGEU  -> List(BR_BGEU  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
            BLT   -> List(BR_BLT   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
            BLTU  -> List(BR_BLTU  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , CSR_X),
            JAL   -> List(ALU_ADD  , OP1_PC , OP2_IMJ, MEN_X, REN_S, WB_PC , CSR_X),
            JALR  -> List(ALU_JALR , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_PC , CSR_X),
            LUI   -> List(ALU_ADD  , OP1_X  , OP2_IMU, MEN_X, REN_S, WB_ALU, CSR_X),
            AUIPC -> List(ALU_ADD  , OP1_PC , OP2_IMU, MEN_X, REN_S, WB_ALU, CSR_X),
            CSRRW -> List(ALU_COPY1, OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_W),
            CSRRWI-> List(ALU_COPY1, OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_W),
            CSRRS -> List(ALU_COPY1, OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_S),
            CSRRSI-> List(ALU_COPY1, OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_S),
            CSRRC -> List(ALU_COPY1, OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_C),
            CSRRCI-> List(ALU_COPY1, OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, CSR_C),
            ECALL -> List(ALU_X    , OP1_X  , OP2_X  , MEN_X, REN_X, WB_X  , CSR_E)
            )
        )
    val id_alu_fnc :: id_op1_sel :: id_op2_sel :: id_mem_wen :: id_rf_wen :: id_wb_sel :: id_csr_cmd :: Nil = csignals

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

    val id_csr_addr = Mux(id_csr_cmd === CSR_E, 0x342.U(CSR_ADDR_LEN.W), id_inst(31,20))

    // ID/EX register
    when(!stall_bus) {
        exe_reg_pc            := id_reg_pc
        exe_reg_inst          := id_inst
        exe_reg_op1_data      := id_op1_data
        exe_reg_op2_data      := id_op2_data
        exe_reg_rs2_data      := id_rs2_data
        exe_reg_wb_addr       := id_wb_addr
        exe_reg_rf_wen        := id_rf_wen
        exe_reg_alu_fnc       := id_alu_fnc
        exe_reg_wb_sel        := id_wb_sel
        exe_reg_csr_addr      := id_csr_addr
        exe_reg_csr_cmd       := id_csr_cmd
        exe_reg_imm_b_sext    := id_imm_b_sext
        exe_reg_mem_wen       := id_mem_wen
    }

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


    // 由于BRAM的读取有一周期延迟，需要提前发出地址
    io.dbus.addrb := exe_alu_out
    csr.io.addrb  := exe_reg_csr_addr
    
    // EX/MEM register
    when(!stall_bus) {
        mem_reg_pc       := exe_reg_pc
        mem_reg_inst     := exe_reg_inst
        mem_reg_wb_addr  := exe_reg_wb_addr
        mem_reg_alu_out  := exe_alu_out
        mem_reg_rf_wen   := exe_reg_rf_wen
        mem_reg_wb_sel   := exe_reg_wb_sel
        mem_reg_csr_addr := exe_reg_csr_addr
        mem_reg_csr_cmd  := exe_reg_csr_cmd
        mem_reg_mem_wen  := exe_reg_mem_wen
        mem_reg_op1_data := exe_reg_op1_data
        mem_reg_rs2_data := exe_reg_rs2_data
    }
    
    //**********************************
    // Memory Access Stage

    val mem_access = (mem_reg_wb_sel === WB_MEM || mem_reg_mem_wen === MEN_S)
    stall_bus := mem_access && !io.dbus.ready  // 若为访存操作且DBus尚未返回响应, 则产生总线等待
    stall_flg := stall_hazard || stall_bus    // 全局暂停信号由数据冒险及访存等待联合产生

    io.dbus.valid := mem_access
    io.dbus.addr  := mem_reg_alu_out
    io.dbus.wen   := mem_reg_mem_wen === MEN_S
    io.dbus.wdata := mem_reg_rs2_data

    // CSR
    csr.io.addr := mem_reg_csr_addr
    csr.io.cmd  := mem_reg_csr_cmd
    
    val csr_rdata = csr.io.rdata

    val csr_wdata = MuxCase(0.U(WORD_LEN.W), Seq(
        (mem_reg_csr_cmd === CSR_W) -> mem_reg_op1_data,
        (mem_reg_csr_cmd === CSR_S) -> (csr_rdata | mem_reg_op1_data),
        (mem_reg_csr_cmd === CSR_C) -> (csr_rdata & ~mem_reg_op1_data),
        (mem_reg_csr_cmd === CSR_E) -> 11.U(WORD_LEN.W)
    ))
    csr.io.wdata := csr_wdata

    when (mem_reg_csr_addr === 0x305.U && mem_reg_csr_cmd =/= 0.U) {
        trap_vector := csr_wdata
    }
    
    mem_wb_data := MuxCase(mem_reg_alu_out, Seq(
        (mem_reg_wb_sel === WB_MEM) -> io.dbus.rdata,
        (mem_reg_wb_sel === WB_PC)  -> (mem_reg_pc + 4.U(WORD_LEN.W)),
        (mem_reg_wb_sel === WB_CSR) -> csr_rdata
    ))


    // MEM/WB regsiter
    when(!stall_bus) {
    wb_reg_pc      := mem_reg_pc
    wb_reg_inst    := mem_reg_inst
    wb_reg_wb_addr := mem_reg_wb_addr
    wb_reg_rf_wen  := mem_reg_rf_wen
    wb_reg_wb_data := mem_wb_data
    }

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