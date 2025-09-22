package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
// 导入common.scala中的Instructions定义和Consts定义
import Instructions._
import Consts._


class PasoRV extends Module {
    val io = IO(new Bundle {
        val ibus  = Flipped(new IBusPortIO())
        val dbus  = Flipped(new DBusPortIO())
        val addrb = Output(UInt(WORD_LEN.W))  // 在EX阶段提前发出的地址
        val plic  = Input( Bool())  // 外部中断
        val clint = Input( Bool())  // 定时器中断
        val exit  = Output(Bool())
    })

    val regfile = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))  // rv32i的32个寄存器

    // 只实现必须的几个CSR寄存器
    val mstatus  = RegInit(0.U(WORD_LEN.W))  // 中断状态，默认关闭MIE中断使能
    val mtvec    = RegInit(0.U(WORD_LEN.W))  // 需要程序自己设置中断函数入口
    val mepc     = RegInit(0.U(WORD_LEN.W))  // 保持终端前正在处理的pc(MEM阶段)
    val mcause   = RegInit(0.U(WORD_LEN.W))  // 产生中断的原因
    val mie      = RegInit(0.U(WORD_LEN.W))  // 中断使能寄存器


    //**********************************
    // 流水线各级寄存器

    // IF/ID State
    val id_reg_pc          = RegInit(0.U(WORD_LEN.W))
    val id_reg_inst        = RegInit(0.U(WORD_LEN.W))
    val id_reg_pred_br     = RegInit(false.B)

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
    val exe_reg_mem_width  = RegInit(0.U(LS_LEN.W))
    val exe_reg_pred_br    = RegInit(false.B)

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
    val mem_reg_mem_width  = RegInit(0.U(LS_LEN.W))

    // MEM/WB State
    val wb_reg_pc          = RegInit(0.U(WORD_LEN.W))
    val wb_reg_inst        = RegInit(0.U(WORD_LEN.W))
    val wb_reg_wb_addr     = RegInit(0.U(ADDR_LEN.W))
    val wb_reg_rf_wen      = RegInit(0.U(REN_LEN.W ))
    val wb_reg_wb_data     = RegInit(0.U(WORD_LEN.W))


    //**********************************
    // Interrupt Signal Capture and Pipeline Flush

    val timer_irq_pending = io.clint  // 定时器中断标志
    val timer_irq_enable  = mie(MTIE) === 1.U  // 是否启用定时器中断
    val timer_irq = timer_irq_pending && timer_irq_enable && (mstatus(MIE) === 1.U) // MTIE&全局MIE

    val ext_irq_pending = io.plic  // 外部中断标志
    val ext_irq_enable = mie(MEIE) === 1.U  // 是否启用外部中断
    val ext_irq = ext_irq_pending && ext_irq_enable && (mstatus(MIE) === 1.U)

    // 优先级，定时器高于外部
    val irq_pending    = timer_irq || ext_irq
    val take_timer_irq = timer_irq
    val take_ext_irq   = (!timer_irq) && ext_irq

    // 中断返回指令
    val is_mret = (mem_reg_csr_cmd === CSR_R)


    //**********************************
    // Instruction Fetch (IF) Stage

    val if_reg_pc = RegInit(START_ADDR)
    val if_pc_plus4 = if_reg_pc + 4.U(WORD_LEN.W)
    val if_inst = Mux(if_reg_pc(2), io.ibus.inst(63, 32), io.ibus.inst(31, 0))
    val pred_negfail = Wire(Bool())  // 预测不分支，但实际要分支
    val pred_posfail = Wire(Bool())  // 预测要分支，但实际不分支

    val stall_hazard = Wire(Bool())  // 出现流水线数据冒险, 需要暂停流水线
    val stall_bus    = Wire(Bool())  // 从机未准备好响应, 需要暂停流水线
    val stall_div    = Wire(Bool())  // 除法器正在运算
    val stall_mul    = Wire(Bool())
    val stall_alu    = Wire(Bool())
    val stall_flg    = Wire(Bool())
    val exe_br_flg   = Wire(Bool())
    val exe_br_tag   = Wire(UInt(WORD_LEN.W))
    val exe_jmp_flg  = Wire(Bool())
    val exe_alu_out  = Wire(UInt(WORD_LEN.W))

    val bht = Module(new GShare(128));  // 分支历史表，仅当是分支指令时才能查询，防止普通指令误触
    bht.io.query_pc := if_reg_pc;   val if_pred_br  = bht.io.predict_taken;  
    val if_is_branch = (if_inst(6,0) === "b1100011".U);  bht.io.query := if_is_branch
    // 计算分支预测的目标地址
    val if_imm_b = Cat(if_inst(31), if_inst(7), if_inst(30, 25), if_inst(11, 8))
    val if_imm_b_sext = Cat(Fill(19, if_imm_b(11)), if_imm_b, 0.U(1.W))
    val pred_tag = if_reg_pc + if_imm_b_sext

    val pc_redirect = (irq_pending || exe_jmp_flg || pred_negfail || pred_posfail)
    val if_pc_next  = MuxCase(if_pc_plus4, Seq(
        // 优先中断＞分支＞跳转＞异常＞流水线暂停
        irq_pending  -> mtvec,  // mtvec应设置为中断处理函数的入口
        //exe_br_flg   -> exe_br_tag,
        pred_negfail -> exe_br_tag,
        pred_posfail -> (exe_reg_pc + 4.U),
        exe_jmp_flg  -> exe_alu_out,
        is_mret      -> mepc,   // 中断返回
        (if_inst === ECALL) -> 1998.U,//mtvec,  // ECALL进异常处理向量地址
        stall_flg    -> if_reg_pc,  // 暂停时保持原PC
        if_pred_br   -> pred_tag,   // 预测出的目标地址
    ))
    if_reg_pc := if_pc_next
    io.ibus.addrb := if_pc_next  // 因为BRAM有延迟，提前发出下一周期的地址


    // IF/ID流水线寄存器
    id_reg_pc   := Mux(stall_flg, id_reg_pc, Mux(pc_redirect, exe_reg_pc, if_reg_pc))
    id_reg_inst := MuxCase(if_inst, Seq(
        // 分支/跳转优先将旧指令清空，否则在stall时保持
        pc_redirect -> BUBBLE,
        stall_flg -> id_reg_inst, 
    ))
    id_reg_pred_br := Mux(stall_flg, id_reg_pred_br, if_pred_br)

    //**********************************
    // Instruction Decode (ID) Stage

    // 数据冒险判断用
    val id_rs1_addr_b = id_reg_inst(19, 15)
    val id_rs2_addr_b = id_reg_inst(24, 20)

    // EX阶段有写后读时需要暂停
    val id_rs1_data_hazard = (exe_reg_rf_wen === REN_S) && (id_rs1_addr_b =/= 0.U) && (id_rs1_addr_b === exe_reg_wb_addr)
    val id_rs2_data_hazard = (exe_reg_rf_wen === REN_S) && (id_rs2_addr_b =/= 0.U) && (id_rs2_addr_b === exe_reg_wb_addr)
    stall_hazard := (id_rs1_data_hazard || id_rs2_data_hazard)

    // 分支/跳转时清空旧指令
    val id_inst = Mux((pc_redirect || stall_hazard), BUBBLE, id_reg_inst)  

    val id_rs1_addr = id_inst(19, 15)
    val id_rs2_addr = id_inst(24, 20)
    val id_wb_addr  = id_inst(11, 7)

    // MEM/WB阶段有写后读时不等待写回，直通读取
    val mem_wb_data = Wire(UInt(WORD_LEN.W))
    val id_rs1_data = MuxCase(regfile(id_rs1_addr), Seq(
        (id_rs1_addr === 0.U) -> 0.U(WORD_LEN.W),
        ((id_rs1_addr === mem_reg_wb_addr) && (mem_reg_rf_wen === REN_S)) -> mem_wb_data,
        ((id_rs1_addr === wb_reg_wb_addr ) && (wb_reg_rf_wen  === REN_S)) -> wb_reg_wb_data
    ))
    val id_rs2_data = MuxCase(regfile(id_rs2_addr),  Seq(
        (id_rs2_addr === 0.U) -> 0.U(WORD_LEN.W),
        ((id_rs2_addr === mem_reg_wb_addr) && (mem_reg_rf_wen === REN_S)) -> mem_wb_data,
        ((id_rs2_addr === wb_reg_wb_addr ) && (wb_reg_rf_wen  === REN_S)) -> wb_reg_wb_data
    ))

    // 立即数生成
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
    
    // 指令译码
    val csignals = ListLookup(id_inst,
                     List(ALU_X     , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
        Array(
            LW    -> List(ALU_ADD   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_W , CSR_X),
            LH    -> List(ALU_ADD   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_H , CSR_X),
            LHU   -> List(ALU_ADD   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_HU, CSR_X),
            LB    -> List(ALU_ADD   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_B , CSR_X),
            LBU   -> List(ALU_ADD   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_BU, CSR_X),
            SW    -> List(ALU_ADD   , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  , LS_W , CSR_X),
            SH    -> List(ALU_ADD   , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  , LS_HU, CSR_X),
            SB    -> List(ALU_ADD   , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  , LS_BU, CSR_X),
            ADD   -> List(ALU_ADD   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            ADDI  -> List(ALU_ADD   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SUB   -> List(ALU_SUB   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            AND   -> List(ALU_AND   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            OR    -> List(ALU_OR    , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            XOR   -> List(ALU_XOR   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            ANDI  -> List(ALU_AND   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            ORI   -> List(ALU_OR    , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            XORI  -> List(ALU_XOR   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLL   -> List(ALU_SLL   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SRL   -> List(ALU_SRL   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SRA   -> List(ALU_SRA   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLLI  -> List(ALU_SLL   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SRLI  -> List(ALU_SRL   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SRAI  -> List(ALU_SRA   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLT   -> List(ALU_SLT   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLTU  -> List(ALU_SLTU  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLTI  -> List(ALU_SLT   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLTIU -> List(ALU_SLTU  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            BEQ   -> List(BR_BEQ    , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BNE   -> List(BR_BNE    , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BGE   -> List(BR_BGE    , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BGEU  -> List(BR_BGEU   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BLT   -> List(BR_BLT    , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BLTU  -> List(BR_BLTU   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            JAL   -> List(ALU_ADD   , OP1_PC , OP2_IMJ, MEN_X, REN_S, WB_PC , LS_X , CSR_X),
            JALR  -> List(ALU_JALR  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_PC , LS_X , CSR_X),
            LUI   -> List(ALU_ADD   , OP1_X  , OP2_IMU, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            AUIPC -> List(ALU_ADD   , OP1_PC , OP2_IMU, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            CSRRW -> List(ALU_COPY1 , OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_W),
            CSRRWI-> List(ALU_COPY1 , OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_W),
            CSRRS -> List(ALU_COPY1 , OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_S),
            CSRRSI-> List(ALU_COPY1 , OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_S),
            CSRRC -> List(ALU_COPY1 , OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_C),
            CSRRCI-> List(ALU_COPY1 , OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_C),
            ECALL -> List(ALU_X     , OP1_X  , OP2_X  , MEN_X, REN_X, WB_X  , LS_X , CSR_E),
            MRET  -> List(ALU_X     , OP1_X  , OP2_X  , MEN_X, REN_X, WB_X  , LS_X , CSR_R),
            MUL   -> List(ALU_MUL   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            MULH  -> List(ALU_MULH  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            MULHSU-> List(ALU_MULHSU, OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            MULHU -> List(ALU_MULHU , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            DIV   -> List(ALU_DIV   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            DIVU  -> List(ALU_DIVU  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            REM   -> List(ALU_REM   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            REMU  -> List(ALU_REMU  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            )
        )
    val id_alu_fnc :: id_op1_sel :: id_op2_sel :: id_mem_wen :: id_rf_wen :: id_wb_sel :: id_mem_width :: id_csr_cmd :: Nil = csignals

    // 提取操作数
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
    when(!(stall_bus || stall_alu)) {
        exe_reg_pc         := Mux(pc_redirect, exe_reg_pc, id_reg_pc)
        exe_reg_inst       := id_inst
        exe_reg_op1_data   := id_op1_data
        exe_reg_op2_data   := id_op2_data
        exe_reg_rs2_data   := id_rs2_data
        exe_reg_wb_addr    := id_wb_addr
        exe_reg_rf_wen     := id_rf_wen
        exe_reg_alu_fnc    := id_alu_fnc
        exe_reg_wb_sel     := id_wb_sel
        exe_reg_csr_addr   := id_csr_addr
        exe_reg_csr_cmd    := id_csr_cmd
        exe_reg_imm_b_sext := id_imm_b_sext
        exe_reg_mem_wen    := id_mem_wen
        exe_reg_mem_width  := id_mem_width
        exe_reg_pred_br    := Mux(stall_flg, exe_reg_pred_br, id_reg_pred_br)
    }


    //**********************************
    // Execute (EX) Stage
    
    val multiplier = Module(new MulModule())
    multiplier.io.op1_data := exe_reg_op1_data; multiplier.io.op2_data := exe_reg_op2_data
    multiplier.io.alu_fnc  := exe_reg_alu_fnc;  stall_mul := multiplier.io.stall
    val is_mul = (exe_reg_alu_fnc === ALU_MUL || exe_reg_alu_fnc === ALU_MULH || exe_reg_alu_fnc === ALU_MULHSU || exe_reg_alu_fnc === ALU_MULHU);  multiplier.io.isMul := is_mul
    val exe_alu_mul = multiplier.io.mul_out
    
    val divider = Module(new DivModule())
    divider.io.op1_data := exe_reg_op1_data; divider.io.op2_data := exe_reg_op2_data
    divider.io.alu_fnc  := exe_reg_alu_fnc;  stall_div := divider.io.stall
    val is_div = (exe_reg_alu_fnc === ALU_DIVU || exe_reg_alu_fnc === ALU_REMU || exe_reg_alu_fnc === ALU_DIV || exe_reg_alu_fnc === ALU_REM);   divider.io.isDiv := is_div
    val exe_alu_div = divider.io.div_out

    stall_alu := stall_div || stall_mul

    val exe_alu_add   = (exe_reg_op1_data + exe_reg_op2_data)
    val exe_alu_equal = (exe_reg_op1_data === exe_reg_op2_data)
    val exe_alu_slt   = (exe_reg_op1_data.asSInt < exe_reg_op2_data.asSInt).asUInt
    val exe_alu_sltu  = (exe_reg_op1_data < exe_reg_op2_data).asUInt

    // ALU运算
    exe_alu_out := MuxCase(0.U(WORD_LEN.W), Seq(
        (exe_reg_alu_fnc === ALU_ADD)    -> exe_alu_add,
        (exe_reg_alu_fnc === ALU_SUB)    -> (exe_reg_op1_data - exe_reg_op2_data),
        (exe_reg_alu_fnc === ALU_AND)    -> (exe_reg_op1_data & exe_reg_op2_data),
        (exe_reg_alu_fnc === ALU_OR)     -> (exe_reg_op1_data | exe_reg_op2_data),
        (exe_reg_alu_fnc === ALU_XOR)    -> (exe_reg_op1_data ^ exe_reg_op2_data),
        (exe_reg_alu_fnc === ALU_SLL)    -> (exe_reg_op1_data << exe_reg_op2_data(4, 0))(31, 0),
        (exe_reg_alu_fnc === ALU_SRL)    -> (exe_reg_op1_data >> exe_reg_op2_data(4, 0)).asUInt,
        (exe_reg_alu_fnc === ALU_SRA)    -> (exe_reg_op1_data.asSInt >> exe_reg_op2_data(4, 0)).asUInt,
        (exe_reg_alu_fnc === ALU_SLT)    -> exe_alu_slt,
        (exe_reg_alu_fnc === ALU_SLTU)   -> exe_alu_sltu,
        (exe_reg_alu_fnc === ALU_JALR)   -> (exe_alu_add & ~1.U(WORD_LEN.W)),
        (exe_reg_alu_fnc === ALU_COPY1)  -> exe_reg_op1_data,
        (is_mul) -> exe_alu_mul,
        (is_div) -> exe_alu_div
    ))

    val exe_is_branch = (exe_reg_inst(6,0) === "b1100011".U)  // 分支型指令
    // 分支判断及目的地址生成
    exe_br_flg := MuxCase(false.B, Seq(
        (exe_reg_alu_fnc === BR_BEQ)  ->  exe_alu_equal,
        (exe_reg_alu_fnc === BR_BNE)  -> !exe_alu_equal,
        (exe_reg_alu_fnc === BR_BLT)  ->  exe_alu_slt,
        (exe_reg_alu_fnc === BR_BGE)  -> !exe_alu_slt,
        (exe_reg_alu_fnc === BR_BLTU) ->  exe_alu_sltu,
        (exe_reg_alu_fnc === BR_BGEU) -> !exe_alu_sltu
    ))
    pred_negfail := exe_br_flg && (exe_br_tag =/= id_reg_pc)
    pred_posfail := exe_reg_pred_br && !exe_br_flg && exe_is_branch
    exe_br_tag := exe_reg_pc + exe_reg_imm_b_sext

    exe_jmp_flg := (exe_reg_wb_sel === WB_PC) && !stall_bus

    bht.io.update := exe_is_branch && !stall_flg  // 根据实际情况更新分支历史
    bht.io.update_pc := exe_reg_pc;  bht.io.update_taken := exe_br_flg

    // 由于BRAM的读取有一周期延迟，需要提前发出地址
    io.addrb := exe_alu_out

    // EX/MEM register
    when (!(stall_bus || stall_alu)) {
        // 用mux选择是否冲刷流水线
        mem_reg_pc        := Mux(irq_pending,  0.U,    exe_reg_pc       )
        mem_reg_inst      := Mux(irq_pending,  BUBBLE, exe_reg_inst     )
        mem_reg_wb_addr   := Mux(irq_pending,  0.U,    exe_reg_wb_addr  )
        mem_reg_alu_out   := Mux(irq_pending,  0.U,    exe_alu_out      )
        mem_reg_rf_wen    := Mux(irq_pending,  REN_X,  exe_reg_rf_wen   )
        mem_reg_wb_sel    := Mux(irq_pending,  WB_X,   exe_reg_wb_sel   )
        mem_reg_csr_addr  := Mux(irq_pending,  0.U,    exe_reg_csr_addr )
        mem_reg_csr_cmd   := Mux(irq_pending,  CSR_X,  exe_reg_csr_cmd  )
        mem_reg_mem_wen   := Mux(irq_pending,  MEN_X,  exe_reg_mem_wen  )
        mem_reg_op1_data  := Mux(irq_pending,  0.U,    exe_reg_op1_data )
        mem_reg_rs2_data  := Mux(irq_pending,  0.U,    exe_reg_rs2_data )
        mem_reg_mem_width := Mux(irq_pending,  LS_X,   exe_reg_mem_width)
    }
    

    //**********************************
    // Memory Access Stage

    val mem_access = (mem_reg_wb_sel === WB_MEM || mem_reg_mem_wen === MEN_S)
    stall_bus := mem_access && !io.dbus.ready  // 若为访存操作且DBus尚未返回响应, 则产生总线等待
    stall_flg := stall_hazard || stall_bus || stall_alu  // 全局暂停信号联合产生

    val isH  = (mem_reg_mem_width === LS_H || mem_reg_mem_width === LS_HU)
    val isB  = (mem_reg_mem_width === LS_B || mem_reg_mem_width === LS_BU)
    val isS  = (mem_reg_mem_width === LS_H || mem_reg_mem_width === LS_B)
    val word_wdata  = mem_reg_rs2_data            // 对应SW
    val half_wdata  = Fill(2, word_wdata(15, 0))  // 对应SH
    val byte_wdata  = Fill(4, word_wdata(7, 0))   // 对应SB
    
    io.dbus.valid := mem_access
    io.dbus.addr  := mem_reg_alu_out
    io.dbus.wen   := mem_reg_mem_wen === MEN_S
    io.dbus.wdata := Mux(isB, byte_wdata, Mux(isH, half_wdata, word_wdata))

    val halfword = Mux(mem_reg_alu_out(1), io.dbus.rdata(31,16), io.dbus.rdata(15,0))
    val byte  = MuxLookup(mem_reg_alu_out(1,0), 0.U(8.W))(Seq(
        "b00".U -> io.dbus.rdata(7,0),   "b01".U -> io.dbus.rdata(15,8),
        "b10".U -> io.dbus.rdata(23,16), "b11".U -> io.dbus.rdata(31,24)
    ))

    io.dbus.ben := Mux(isH, Mux(mem_reg_alu_out(1), "b1100".U, "b0011".U),
        Mux(isB, MuxLookup(mem_reg_alu_out(1,0), "b0001".U)(Seq(
            "b00".U -> "b0001".U, "b01".U -> "b0010".U,
            "b10".U -> "b0100".U, "b11".U -> "b1000".U)
        ), "b1111".U)
    )

    val dbus_rdata = Mux(isH, Mux(isS, halfword.asSInt.pad(32).asUInt, halfword.pad(32)),
        Mux(isB, Mux(isS, byte.asSInt.pad(32).asUInt, byte.pad(32)), io.dbus.rdata)
    )


    val csr_rdata = MuxCase(0.U(WORD_LEN.W), Seq(
        (mem_reg_csr_addr === 0x300.U) -> mstatus,
        (mem_reg_csr_addr === 0x304.U) -> mie,
        (mem_reg_csr_addr === 0x305.U) -> mtvec,
        (mem_reg_csr_addr === 0x341.U) -> mepc,
        (mem_reg_csr_addr === 0x342.U) -> mcause,
    ))

    mem_wb_data := MuxCase(mem_reg_alu_out, Seq(
        (mem_reg_wb_sel === WB_MEM) -> dbus_rdata,
        (mem_reg_wb_sel === WB_PC)  -> (mem_reg_pc + 4.U(WORD_LEN.W)),
        (mem_reg_wb_sel === WB_CSR) -> csr_rdata
    ))

    val csr_wdata = MuxCase(0.U(WORD_LEN.W), Seq(
        (mem_reg_csr_cmd === CSR_W) -> mem_reg_op1_data,
        (mem_reg_csr_cmd === CSR_S) -> (csr_rdata | mem_reg_op1_data),
        (mem_reg_csr_cmd === CSR_C) -> (csr_rdata & ~mem_reg_op1_data),
        (mem_reg_csr_cmd === CSR_E) -> 11.U(WORD_LEN.W)
    ))
    
    when(mem_reg_csr_cmd > 0.U){
        switch(mem_reg_csr_addr) {
            is(0x300.U) { mstatus  := csr_wdata }
            is(0x304.U) { mie      := csr_wdata }
            is(0x305.U) { mtvec    := csr_wdata }
            is(0x341.U) { mepc     := csr_wdata }
            is(0x342.U) { mcause   := csr_wdata }
        }
    }  

    // 中断处理
    when (irq_pending) { // 保存异常现场
        mepc := mem_reg_pc // 发生中断时MEM及之前阶段的指令被抛弃
        when(take_timer_irq) {
            mcause := "h80000007".U // 定时器中断
        }.elsewhen(take_ext_irq) {
            mcause := "h8000000b".U // 外部中断
        }
        mstatus := mstatus.bitSet(MPIE, mstatus(MIE)).bitSet(MIE, false.B) // 关MIE
    }

    // mret指令，中断返回: 为防止过早的判断与跳转/分支冲突，才放在MEM阶段
    when(is_mret) {
        mstatus := mstatus.bitSet(MIE, mstatus(MPIE)).bitSet(MPIE, true.B) // 恢复MIE
    }


    // MEM/WB regsiter
    when(!(stall_bus || stall_alu)) {
        wb_reg_pc      := mem_reg_pc
        wb_reg_inst    := mem_reg_inst
        wb_reg_wb_addr := mem_reg_wb_addr
        wb_reg_rf_wen  := mem_reg_rf_wen
        wb_reg_wb_data := mem_wb_data
    }

    //**********************************
    // Writeback (WB) Stage

    // 分别获取 exe_jmp_flg 延迟 3 个和 4 个时钟周期的版本
    val exe_jmp_flg_d3 = ShiftRegister(exe_jmp_flg, 3)
    val exe_jmp_flg_d4 = ShiftRegister(exe_jmp_flg, 4)
    // 当其中任一为真时，即表示处于跳转后的第三或第四周期
    val wbForceLow = exe_jmp_flg_d3 || exe_jmp_flg_d4
    // 确实要写回时拉高 wb_have_inst
    val wb_have_inst = Mux(wbForceLow, false.B, 
        ((wb_reg_rf_wen === REN_S) && (wb_reg_wb_addr =/= 0.U) && !(stall_bus || stall_alu))
    )
    when(wb_have_inst) {
        regfile(wb_reg_wb_addr) := wb_reg_wb_data
    }

    //**********************************
    // IO & Debug
    io.exit := (wb_reg_inst === UNIMP)  // 退出仿真

    val debugEnabled = sys.env.getOrElse("PASORV_DEBUG", "0") == "1"
    if (debugEnabled) {
        printf(p"if_reg_pc        : 0x${Hexadecimal(if_reg_pc)}\n")
        printf(p"id_reg_pc        : 0x${Hexadecimal(id_reg_pc)}\n")
        printf(p"exe_reg_pc       : 0x${Hexadecimal(exe_reg_pc)}\n")
        printf(p"exe_reg_op1_data : 0x${Hexadecimal(exe_reg_op1_data)}\n")
        printf(p"exe_reg_op2_data : 0x${Hexadecimal(exe_reg_op2_data)}\n")
        printf(p"exe_alu_out      : 0x${Hexadecimal(exe_alu_out)}\n")
        printf(p"mem_reg_pc       : 0x${Hexadecimal(mem_reg_pc)}\n")
        printf(p"mem_wb_data      : 0x${Hexadecimal(mem_wb_data)}\n")
        printf(p"wb_reg_pc        : 0x${Hexadecimal(wb_reg_pc)}\n")
        printf(p"wb_reg_wb_data   : 0x${Hexadecimal(wb_reg_wb_data)}\n")
        printf(p"wb_have_inst     : 0x${Hexadecimal(wb_have_inst)}\n")
        printf("---------\n")
    }
}
