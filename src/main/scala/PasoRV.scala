package PaSoC

import chisel3._       // chisel本体
import chisel3.util._  // chisel功能
// 导入common.scala中的Instructions定义和Consts定义
import Instructions._
import Consts._


class PasoRV extends Module {
    val io = IO(
        new Bundle {
            val ibus = Flipped(new IBusPortIO())
            val dbus = Flipped(new DBusPortIO())
            val exit = Output(Bool())
        }
    )

    val regfile = RegInit(VecInit((0 until 32).map { i =>
        if (i == 2) "h00000ff0".U(WORD_LEN.W) else 0.U(WORD_LEN.W)
    }))  // 寄存器sp(x2)初始化，作为堆栈指针被C语言调用
    
    val csr = Module(new CSRFile())  // CSR寄存器模块
    val trap_vector = RegInit(0.U(WORD_LEN.W))

    //**********************************
    // 流水线各级寄存器

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
    val exe_reg_mem_width  = RegInit(0.U(LS_LEN.W))

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
    val wb_reg_rf_wen      = RegInit(0.U(REN_LEN.W))
    val wb_reg_wb_data     = RegInit(0.U(WORD_LEN.W))


    //**********************************
    // Instruction Fetch (IF) Stage

    val if_reg_pc = RegInit(START_ADDR)
    val if_inst = io.ibus.inst

    val stall_hazard = Wire(Bool())  // 出现流水线数据冒险, 需要暂停流水线
    val stall_bus    = Wire(Bool())  // 从机未准备好响应, 需要暂停流水线
    val stall_flg     = Wire(Bool())
    val exe_br_flg    = Wire(Bool())
    val exe_br_target = Wire(UInt(WORD_LEN.W))
    val exe_jmp_flg   = Wire(Bool())
    val exe_alu_out   = Wire(UInt(WORD_LEN.W))

    val if_pc_plus4 = if_reg_pc + 4.U(WORD_LEN.W)
    val if_pc_next = MuxCase(if_pc_plus4, Seq(
        // 优先分支＞跳转＞异常＞流水线暂停
        exe_br_flg  -> exe_br_target,
        exe_jmp_flg -> exe_alu_out,
        (if_inst === ECALL) -> trap_vector,  // ECALL进异常处理向量地址
        stall_flg   -> if_reg_pc,  // 暂停时保持原PC
    ))
    if_reg_pc := if_pc_next
    io.ibus.addrb := if_pc_next  // 因为BRAM有延迟，提前发出下一周期的地址


    // IF/ID流水线寄存器
    id_reg_pc   := Mux(stall_flg, id_reg_pc, if_reg_pc)
    id_reg_inst := MuxCase(if_inst, Seq(
        // 分支/跳转优先将旧指令清空，否则在stall时保持
        (exe_br_flg || exe_jmp_flg) -> BUBBLE,
        stall_flg -> id_reg_inst, 
    ))


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
    val id_inst = Mux((exe_br_flg || exe_jmp_flg || stall_hazard), BUBBLE, id_reg_inst)  

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
                     List(ALU_X    , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
        Array(
            LW    -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_W , CSR_X),
            LH    -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_H , CSR_X),
            LHU   -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_HU, CSR_X),
            LB    -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_B , CSR_X),
            LBU   -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_MEM, LS_BU, CSR_X),
            SW    -> List(ALU_ADD  , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  , LS_W , CSR_X),
            SH    -> List(ALU_ADD  , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  , LS_HU, CSR_X),
            SB    -> List(ALU_ADD  , OP1_RS1, OP2_IMS, MEN_S, REN_X, WB_X  , LS_BU, CSR_X),
            ADD   -> List(ALU_ADD  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            ADDI  -> List(ALU_ADD  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SUB   -> List(ALU_SUB  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            AND   -> List(ALU_AND  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            OR    -> List(ALU_OR   , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            XOR   -> List(ALU_XOR  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            ANDI  -> List(ALU_AND  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            ORI   -> List(ALU_OR   , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            XORI  -> List(ALU_XOR  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLL   -> List(ALU_SLL  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SRL   -> List(ALU_SRL  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SRA   -> List(ALU_SRA  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLLI  -> List(ALU_SLL  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SRLI  -> List(ALU_SRL  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SRAI  -> List(ALU_SRA  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLT   -> List(ALU_SLT  , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLTU  -> List(ALU_SLTU , OP1_RS1, OP2_RS2, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLTI  -> List(ALU_SLT  , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            SLTIU -> List(ALU_SLTU , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            BEQ   -> List(BR_BEQ   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BNE   -> List(BR_BNE   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BGE   -> List(BR_BGE   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BGEU  -> List(BR_BGEU  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BLT   -> List(BR_BLT   , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            BLTU  -> List(BR_BLTU  , OP1_RS1, OP2_RS2, MEN_X, REN_X, WB_X  , LS_X , CSR_X),
            JAL   -> List(ALU_ADD  , OP1_PC , OP2_IMJ, MEN_X, REN_S, WB_PC , LS_X , CSR_X),
            JALR  -> List(ALU_JALR , OP1_RS1, OP2_IMI, MEN_X, REN_S, WB_PC , LS_X , CSR_X),
            LUI   -> List(ALU_ADD  , OP1_X  , OP2_IMU, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            AUIPC -> List(ALU_ADD  , OP1_PC , OP2_IMU, MEN_X, REN_S, WB_ALU, LS_X , CSR_X),
            CSRRW -> List(ALU_COPY1, OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_W),
            CSRRWI-> List(ALU_COPY1, OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_W),
            CSRRS -> List(ALU_COPY1, OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_S),
            CSRRSI-> List(ALU_COPY1, OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_S),
            CSRRC -> List(ALU_COPY1, OP1_RS1, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_C),
            CSRRCI-> List(ALU_COPY1, OP1_IMZ, OP2_X  , MEN_X, REN_S, WB_CSR, LS_X , CSR_C),
            ECALL -> List(ALU_X    , OP1_X  , OP2_X  , MEN_X, REN_X, WB_X  , LS_X , CSR_E)
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
    when(!stall_bus) {
        exe_reg_pc         := id_reg_pc
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
    }


    //**********************************
    // Execute (EX) Stage

    // ALU运算
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

    // 分支判断及目的地址生成
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
        mem_reg_pc        := exe_reg_pc
        mem_reg_inst      := exe_reg_inst
        mem_reg_wb_addr   := exe_reg_wb_addr
        mem_reg_alu_out   := exe_alu_out
        mem_reg_rf_wen    := exe_reg_rf_wen
        mem_reg_wb_sel    := exe_reg_wb_sel
        mem_reg_csr_addr  := exe_reg_csr_addr
        mem_reg_csr_cmd   := exe_reg_csr_cmd
        mem_reg_mem_wen   := exe_reg_mem_wen
        mem_reg_op1_data  := exe_reg_op1_data
        mem_reg_rs2_data  := exe_reg_rs2_data
        mem_reg_mem_width := exe_reg_mem_width
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
    
    val dbus_rdata = Wire(UInt(WORD_LEN.W))  // 实现半字读写和字节读写
    // 如果既不是半字，也不是字节操作，默认返回整个字及全使能信号
    when ((mem_reg_mem_width =/= LS_H) && (mem_reg_mem_width =/= LS_HU) &&
          (mem_reg_mem_width =/= LS_B) && (mem_reg_mem_width =/= LS_BU)) {
        dbus_rdata := io.dbus.rdata
        io.dbus.ben := "b1111".U(4.W)
    } .otherwise {
        // 判断是否为半字操作，以及是否采用符号扩展
        val isHalf   = (mem_reg_mem_width === LS_H || mem_reg_mem_width === LS_HU)
        val isSigned = (mem_reg_mem_width === LS_H || mem_reg_mem_width === LS_B)

        when(isHalf) {
            // 半字加载：根据地址第1位选择对应的16位数据
            val halfData = Mux(mem_reg_alu_out(1), io.dbus.rdata(31,16), io.dbus.rdata(15,0))
            // 若需要符号扩展，则先将16位有符号数延伸到32位，否则零扩展到32位
            dbus_rdata := Mux(isSigned, halfData.asSInt.pad(32).asUInt, halfData.pad(32))
        } .otherwise {
            // 字节加载：用地址低2位选择对应的8位数据
            val byteData = MuxLookup(mem_reg_alu_out(1,0), 0.U(8.W))(Seq(
                "b00".U -> io.dbus.rdata(7,0),   "b01".U -> io.dbus.rdata(15,8),
                "b10".U -> io.dbus.rdata(23,16), "b11".U -> io.dbus.rdata(31,24)
            ))
            // 对于LB指令：直接从8位数进行扩展，确保有符号扩展直接取自 bit7
            dbus_rdata := Mux(isSigned, byteData.asSInt.pad(32).asUInt, byteData.pad(32))
        }

        // 字节使能信号
        io.dbus.ben := Mux(isHalf, Mux(mem_reg_alu_out(1), "b1100".U(4.W), "b0011".U(4.W)),
            MuxLookup(mem_reg_alu_out(1,0), "b1111".U(4.W))(Seq(
                "b00".U -> "b0001".U(4.W), "b01".U -> "b0010".U(4.W),
                "b10".U -> "b0100".U(4.W), "b11".U -> "b1000".U(4.W)
        )))
    }


    mem_wb_data := MuxCase(mem_reg_alu_out, Seq(
        (mem_reg_wb_sel === WB_MEM) -> dbus_rdata,
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

    // 分别获取 exe_jmp_flg 延迟 3 个和 4 个时钟周期的版本
    val exe_jmp_flg_d3 = ShiftRegister(exe_jmp_flg, 3)
    val exe_jmp_flg_d4 = ShiftRegister(exe_jmp_flg, 4)
    // 当其中任一为真时，即表示处于跳转后的第三或第四周期
    val wbForceLow = exe_jmp_flg_d3 || exe_jmp_flg_d4
    // 确实要写回时拉高 wb_have_inst
    val wb_have_inst = Mux(wbForceLow, false.B, ((wb_reg_rf_wen === REN_S) && (wb_reg_wb_addr =/= 0.U)))

    when(wb_have_inst) {
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
    printf(p"wb_reg_pc        : 0x${Hexadecimal(wb_reg_pc)}\n")
    printf(p"wb_reg_wb_data   : 0x${Hexadecimal(wb_reg_wb_data)}\n")
    printf(p"wb_have_inst     : 0x${Hexadecimal(wb_have_inst)}\n")
    printf("---------\n")
}