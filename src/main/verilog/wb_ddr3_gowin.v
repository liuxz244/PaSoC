module wb_ddr3(
    // Wishbone接口
    input            wb_cyc_i,
    input            wb_stb_i,
    input            wb_we_i,
    input   [31:0]   wb_adr_i,
    input   [15:0]   wb_sel_i,
    input   [127:0]  wb_dat_i,
    output  [127:0]  wb_dat_o,
    output           wb_ack_o,
    // 时钟及复位、初始化
    output           clk_36m,
    input            pll_lock,
    input            sys_rst_n,
    input            sys_clk,
    input            clk_144m,
    output           init_cpl,
    // DDR3物理层信号
    output  [13:0]   ddr_addr,
    output  [2:0]    ddr_bank,
    output           ddr_cs,
    output           ddr_ras,
    output           ddr_cas,
    output           ddr_we,
    output           ddr_ck,
    output           ddr_ck_n,
    output           ddr_cke,
    output           ddr_odt,
    output           ddr_rst_n,
    output  [1:0]    ddr_dm,
    inout   [15:0]   ddr_dq,
    inout   [1:0]    ddr_dqs,
    inout   [1:0]    ddr_dqs_n
);

    // Wishbone请求信号
    wire wb_rd_req = wb_cyc_i && wb_stb_i && ~wb_we_i;
    wire wb_wr_req = wb_cyc_i && wb_stb_i &&  wb_we_i;
    // 地址对齐
    wire [27:0] ddr_addr_align = {wb_adr_i[27:24], 1'b0, wb_adr_i[23:4], 3'b0};

    // 命令等直连，组合生成
    wire [2:0]   app_cmd = wb_rd_req ? 3'b001 : 
                           wb_wr_req ? 3'b000 : 
                           3'b000;
    wire [27:0]  app_addr    = ddr_addr_align;
    wire [127:0] app_wdf_data= wb_dat_i;
    wire [15:0]  app_wdf_mask= ~wb_sel_i;

    // 状态机
    localparam S_IDLE    = 2'd0,
               S_WAIT_RD = 2'd1;
    reg  [1:0]  state, state_next;

    wire rst_n = pll_lock && sys_rst_n;

    // DDR3 IP接口信号
    wire           app_cmd_rdy;
    wire           app_wdf_rdy;
    wire  [127:0]  app_rd_data;
    wire           app_rd_data_valid;
    wire           app_rd_data_end;

    // ack逻辑
    wire wb_wr_ack = wb_wr_req && init_cpl && app_cmd_rdy && app_wdf_rdy && (state == S_IDLE);
    wire wb_rd_ack = (state == S_WAIT_RD) && app_rd_data_valid && app_rd_data_end;
    assign wb_ack_o = wb_wr_ack || wb_rd_ack;
    assign wb_dat_o = app_rd_data;

    // 状态机组合判断
    always @(*) begin
        state_next = state;
        case(state)
            S_IDLE: begin
                if(wb_rd_req && init_cpl && app_cmd_rdy) begin
                    state_next = S_WAIT_RD;
                end
                else if(wb_wr_req && init_cpl && app_cmd_rdy && app_wdf_rdy) begin
                    state_next = S_IDLE; // 其实S_IDLE->S_IDLE
                end
            end
            S_WAIT_RD: begin
                if(app_rd_data_valid && app_rd_data_end)
                    state_next = S_IDLE;
            end
            default:
                state_next = S_IDLE;
        endcase
    end

    // 顺序部分：只管状态
    always @(posedge clk_36m or negedge rst_n) begin
        if (!rst_n) begin
            state <= S_IDLE;
        end
        else begin
            state <= state_next;
            // 复位处理
            if (~init_cpl) begin
                state <= S_IDLE;
            end
        end
    end

    // ---- 组合输出，条件成立即拉高 ----
    assign app_cmd_en   = (
        (state == S_IDLE) && wb_rd_req && init_cpl && app_cmd_rdy
    ) || (
        (state == S_IDLE) && wb_wr_req && init_cpl && app_cmd_rdy && app_wdf_rdy
    );
    assign app_wdf_wren = (state == S_IDLE) && wb_wr_req && init_cpl && app_cmd_rdy && app_wdf_rdy;
    assign app_wdf_end  = (state == S_IDLE) && wb_wr_req && init_cpl && app_cmd_rdy && app_wdf_rdy;

    // DDR3 Wrapper
    DDR3_Memory_Interface_Top u_ddr3_ip(
        .clk            (sys_clk),
        .memory_clk     (clk_144m),
        .pll_lock       (pll_lock),
        .rst_n          (sys_rst_n),
        .clk_out        (clk_36m),

        .cmd            (app_cmd),
        .addr           (app_addr),
        .cmd_en         (app_cmd_en),
        .cmd_ready      (app_cmd_rdy),
        .wr_data_rdy    (app_wdf_rdy),
        .wr_data        (app_wdf_data),
        .wr_data_en     (app_wdf_wren),
        .wr_data_end    (app_wdf_end),
        .wr_data_mask   (app_wdf_mask),
        .rd_data        (app_rd_data),
        .rd_data_valid  (app_rd_data_valid),
        .rd_data_end    (app_rd_data_end),
        .burst          (1'b0),
        .sr_req         (1'b0),
        .ref_req        (1'b0),
        .sr_ack         (),
        .ref_ack        (),
        .ddr_rst        (),
        .init_calib_complete(init_cpl),
        
        .O_ddr_addr     (ddr_addr),
        .O_ddr_ba       (ddr_bank),
        .O_ddr_cs_n     (ddr_cs),
        .O_ddr_ras_n    (ddr_ras),
        .O_ddr_cas_n    (ddr_cas),
        .O_ddr_we_n     (ddr_we),
        .O_ddr_clk      (ddr_ck),
        .O_ddr_clk_n    (ddr_ck_n),
        .O_ddr_cke      (ddr_cke),
        .O_ddr_odt      (ddr_odt),
        .O_ddr_reset_n  (ddr_rst_n),
        .O_ddr_dqm      (ddr_dm),
        .IO_ddr_dq      (ddr_dq),
        .IO_ddr_dqs     (ddr_dqs),
        .IO_ddr_dqs_n   (ddr_dqs_n)
    );

endmodule
