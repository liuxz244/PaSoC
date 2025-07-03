module wb_ddr3 (
    // Wishbone master接口（异步时钟域）
    input            wb_clk_i,  // Wishbone主侧时钟
    input            wb_rst_i,  // Wishbone主侧复位（高有效）
    input            wb_cyc_i,
    input            wb_stb_i,
    input            wb_we_i,
    input   [31:0]   wb_adr_i,
    input   [15:0]   wb_sel_i,
    input   [127:0]  wb_dat_i,
    output  [127:0]  wb_dat_o,
    output           wb_ack_o,
    // 时钟及复位（DDR/MIG口）
    input            sys_clk,     // 参考时钟(200MHz)
    input            sys_rst_n,   // 低有效复位
    output           init_cpl,    // DDR初始化完成
    output           ui_clk,      // MIG应用口时钟
    output           ui_rst,      // 高有效应用复位
    // DDR3管脚
    output  [14:0]   ddr3_addr,
    output  [2:0]    ddr3_ba,
    output           ddr3_ras_n,
    output           ddr3_cas_n,
    output           ddr3_we_n,
    output  [0:0]    ddr3_ck_p,
    output  [0:0]    ddr3_ck_n,
    output  [0:0]    ddr3_cke,
    output  [0:0]    ddr3_odt,
    output           ddr3_reset_n,
    output  [1:0]    ddr3_dm,
    inout   [15:0]   ddr3_dq,
    inout   [1:0]    ddr3_dqs_p,
    inout   [1:0]    ddr3_dqs_n
);

    // Wishbone Async Reg信号定义
    wire [31:0]    wbs_adr_o;
    wire [127:0]   wbs_dat_o;
    wire [127:0]   wbs_dat_i;
    wire [15:0]    wbs_sel_o;
    wire           wbs_we_o;
    wire           wbs_stb_o;
    wire           wbs_ack_i;
    wire           wbs_cyc_o;

    wb_async_reg #(
        .DATA_WIDTH   (128),
        .ADDR_WIDTH   (32),
        .SELECT_WIDTH (16)
    ) u_wb_async_reg (
        // master port (wishbone主时钟域)
        .wbm_clk   (wb_clk_i),
        .wbm_rst   (wb_rst_i),
        .wbm_adr_i (wb_adr_i),
        .wbm_dat_i (wb_dat_i),
        .wbm_dat_o (wb_dat_o),
        .wbm_we_i  (wb_we_i),
        .wbm_sel_i (wb_sel_i),
        .wbm_stb_i (wb_stb_i),
        .wbm_ack_o (wb_ack_o),
        .wbm_err_o (),         // 不用
        .wbm_rty_o (),         // 不用
        .wbm_cyc_i (wb_cyc_i),
        // slave port (DDR/MIG时钟域)
        .wbs_clk   (ui_clk),
        .wbs_rst   (ui_rst),
        .wbs_adr_o (wbs_adr_o),
        .wbs_dat_i (wbs_dat_i),
        .wbs_dat_o (wbs_dat_o),
        .wbs_we_o  (wbs_we_o),
        .wbs_sel_o (wbs_sel_o),
        .wbs_stb_o (wbs_stb_o),
        .wbs_ack_i (wbs_ack_i),
        .wbs_err_i (1'b0),     // 不用
        .wbs_rty_i (1'b0),     // 不用
        .wbs_cyc_o (wbs_cyc_o)
    );

    // MIG 应用口 信号
    wire  [28:0]   app_addr;
    wire  [2:0]    app_cmd;
    wire           app_en;
    wire           app_rdy;
    wire           app_wdf_rdy;
    wire  [127:0]  app_wdf_data;
    wire  [15:0]   app_wdf_mask;
    wire           app_wdf_end;
    wire           app_wdf_wren;
    wire  [127:0]  app_rd_data;
    wire           app_rd_data_valid;
    wire           app_rd_data_end;

    // 状态机定义
    localparam S_IDLE    = 2'd0,
               S_WAIT_RD = 2'd1;
    reg  [1:0]  state, state_next;

    // Wishbone请求信号（异步桥后的wbs信号）
    wire wb_rd_req = wbs_cyc_o && wbs_stb_o && ~wbs_we_o;
    wire wb_wr_req = wbs_cyc_o && wbs_stb_o &&  wbs_we_o;

    // 地址对齐
    wire [28:0] ddr_addr_align = {1'b0, wbs_adr_o[28:4], 3'b0}; // 128bit 16B对齐

    // 命令生成
    assign app_cmd = wb_rd_req ? 3'b001 :   // 读
                     wb_wr_req ? 3'b000 :   // 写
                     3'b000;                // 空操作
    assign app_addr     = ddr_addr_align;
    assign app_wdf_data = wbs_dat_o;
    assign app_wdf_mask = ~wbs_sel_o;

    // ack逻辑
    wire wb_wr_ack = (state == S_IDLE) && wb_wr_req && init_cpl && app_rdy && app_wdf_rdy;
    wire wb_rd_ack = (state == S_WAIT_RD) && app_rd_data_valid && app_rd_data_end;
    assign wbs_ack_i = wb_wr_ack || wb_rd_ack;
    assign wbs_dat_i = app_rd_data;

    // 应用口控制信号
    assign app_en   = (
        (state == S_IDLE) && wb_rd_req && init_cpl && app_rdy
    ) || (
        (state == S_IDLE) && wb_wr_req && init_cpl && app_rdy && app_wdf_rdy
    );
    assign app_wdf_wren = (state == S_IDLE) && wb_wr_req && init_cpl && app_rdy && app_wdf_rdy;
    assign app_wdf_end  = (state == S_IDLE) && wb_wr_req && init_cpl && app_rdy && app_wdf_rdy;

    // 状态机组合判断
    always @(*) begin
        state_next = state;
        case(state)
            S_IDLE: begin
                if(wb_rd_req && init_cpl && app_rdy) begin
                    state_next = S_WAIT_RD;
                end
                // 写操作可直接完成，状态不变
            end
            S_WAIT_RD: begin
                if(app_rd_data_valid && app_rd_data_end)
                    state_next = S_IDLE;
            end
            default:
                state_next = S_IDLE;
        endcase
    end

    // 状态机时序
    always @(posedge ui_clk) begin
        if (ui_rst) begin
            state <= S_IDLE;
        end
        else begin
            state <= state_next;
            if (~init_cpl) begin
                state <= S_IDLE;
            end
        end
    end

    //---------------- MIG DDR3 实例 ---------------------
    mig_ddr3 u_mig_ddr3 (
        // DDR3 PHY
        .ddr3_addr          (ddr3_addr),
        .ddr3_ba            (ddr3_ba),
        .ddr3_ras_n         (ddr3_ras_n),
        .ddr3_cas_n         (ddr3_cas_n),
        .ddr3_we_n          (ddr3_we_n),
        .ddr3_ck_p          (ddr3_ck_p),
        .ddr3_ck_n          (ddr3_ck_n),
        .ddr3_cke           (ddr3_cke),
        .ddr3_odt           (ddr3_odt),
        .ddr3_reset_n       (ddr3_reset_n),
        .ddr3_dm            (ddr3_dm),
        .ddr3_dq            (ddr3_dq),
        .ddr3_dqs_p         (ddr3_dqs_p),
        .ddr3_dqs_n         (ddr3_dqs_n),
        // 应用口
        .app_addr           (app_addr),
        .app_cmd            (app_cmd),
        .app_en             (app_en),
        .app_wdf_data       (app_wdf_data),
        .app_wdf_end        (app_wdf_end),
        .app_wdf_wren       (app_wdf_wren),
        .app_rd_data        (app_rd_data),
        .app_rd_data_end    (app_rd_data_end),
        .app_rd_data_valid  (app_rd_data_valid),
        .app_rdy            (app_rdy),
        .app_wdf_rdy        (app_wdf_rdy),
        .app_sr_req         (1'b0),
        .app_ref_req        (1'b0),
        .app_zq_req         (1'b0),
        .app_sr_active      (),
        .app_ref_ack        (),
        .app_zq_ack         (),
        // 时钟及复位
        .ui_clk             (ui_clk),
        .ui_clk_sync_rst    (ui_rst),
        .app_wdf_mask       (app_wdf_mask),
        .sys_clk_i          (sys_clk),
        .sys_rst            (sys_rst_n),
        .init_calib_complete(init_cpl)
    );

endmodule
