module wb_sdram (
        // Wishbone slave interface
        input              wb_clk_i,      // Wishbone时钟
        input              wb_rst_i,      // Wishbone同步复位，高有效
        input      [31:0]  wb_adr_i,      // Wishbone地址
        input      [31:0]  wb_dat_i,      // Wishbone写数据
        output reg [31:0]  wb_dat_o,      // Wishbone读数据
        input      [3:0]   wb_sel_i,      // Wishbone字节使能
        input              wb_we_i,       // Wishbone写使能
        input              wb_cyc_i,      // Wishbone事务周期
        input              wb_stb_i,      // Wishbone选通信号
        output reg         wb_ack_o,      // Wishbone完成应答

        // SDRAM状态及接口信号
        output reg         init_done,     // SDRAM初始化完成
        output reg         sdram_ras_n,   // 行地址选通信号
        output reg         sdram_cas_n,   // 列地址选通信号
        output reg         sdram_we_n,    // 写使能信号
        output reg [10:0]  sdram_addr,    // 地址总线
        output reg [1:0]   sdram_ba,      // Bank地址
        inout      [31:0]  sdram_dq,      // 数据总线
        output reg         sdram_cs_n,    // 片选信号
        output reg [3:0]   sdram_dm,      // 数据掩码
        output reg         sdram_cke      // 时钟使能
    );

    // SDRAM参数
    parameter CLK_CYCLE_NS        = 28;      // SDRAM控制器时钟周期（ns）
    parameter POWERON_DELAY_NS    = 200000;  // 上电延时（ns）
    parameter REFRESH_INTERVAL_NS = 15000;   // 刷新间隔（ns）

    // 时序参数，均为时钟周期数
    parameter T_RC   = 3+1; // 行周期
    parameter T_RP   = 1+1; // 预充电时间
    parameter T_WR   = 2+1; // 写恢复
    parameter T_MRD  = 2+1; // 模式寄存器设置时间
    parameter T_RFC  = 3+1; // 刷新周期
    parameter T_RCD  = 1+1; // 行地址到列地址延迟
    parameter T_RRD  = 1+1; // 行激活到行激活延迟
    parameter CL     = 3+1; // CAS延迟

    // 计数器与寄存器
    reg [31:0] counter;
    reg [31:0] counter_refresh;
    reg [4:0]  stage;

    // SDRAM dq方向控制
    reg [31:0] sdram_dq_r;   // dq输出缓存
    reg        sdram_dq_ie;  // dq方向: 1-输出, 0-输入
    assign     sdram_dq = sdram_dq_ie ? sdram_dq_r : 32'bz;

    // Wishbone到内部信号映射
    wire           req_valid = wb_cyc_i & wb_stb_i;
    wire           req_write = wb_we_i;
    wire   [3:0]   req_wstrb = wb_we_i ? wb_sel_i : 4'b0;
    wire  [31:0]   req_addr  = wb_adr_i;
    wire  [31:0]   req_wdata = wb_dat_i;

    always @ (posedge wb_clk_i or posedge wb_rst_i) begin
        if (wb_rst_i) begin
            counter     <= 0;
            counter_refresh <= 0;
            stage       <= 0;
            wb_dat_o    <= 0;
            wb_ack_o    <= 0;
            init_done   <= 0;
            sdram_ras_n <= 1;
            sdram_cas_n <= 1;
            sdram_we_n  <= 1;
            sdram_addr  <= 11'b0;
            sdram_cs_n  <= 1;
            sdram_ba    <= 2'b0;
            sdram_dm    <= 4'b1111;
            sdram_cke   <= 0;
            sdram_dq_r  <= 0;
            sdram_dq_ie <= 0;
        end
        else begin
            // 刷新计数
            counter_refresh <= counter_refresh + 1;

            // -------- 状态0：上电等待 --------
            if (stage == 0) begin
                sdram_cke    <= 1;
                sdram_cs_n   <= 0;
                sdram_ras_n  <= 1;
                sdram_cas_n  <= 1;
                sdram_we_n   <= 1;
                sdram_dm     <= 4'b1111;
                if (counter < POWERON_DELAY_NS/CLK_CYCLE_NS) begin
                    counter <= counter + 1;
                end
                else begin
                    counter <= 0;
                    stage <= 1;
                end
            end

            // -------- 状态1：预充电全部Bank --------
            else if (stage == 1) begin
                if (counter == 0) begin
                    sdram_cke    <= 1;
                    sdram_addr[10] <= 1;
                    sdram_cs_n   <= 0;
                    sdram_ras_n  <= 0;
                    sdram_cas_n  <= 1;
                    sdram_we_n   <= 0;
                end
                else begin
                    sdram_cke    <= 1;
                    sdram_cs_n   <= 0;
                    sdram_ras_n  <= 1;
                    sdram_cas_n  <= 1;
                    sdram_we_n   <= 1;
                end
                if (counter < T_RP) begin
                    counter <= counter + 1;
                end
                else begin
                    counter <= 0;
                    stage <= 2;
                end
            end

            // -------- 状态2：两次自动刷新 --------
            else if (stage == 2) begin
                if (counter == 0) begin
                    sdram_cke    <= 1;
                    sdram_cs_n   <= 0;
                    sdram_ras_n  <= 0;
                    sdram_cas_n  <= 0;
                    sdram_we_n   <= 1;
                end
                else if (counter == T_RFC) begin
                    sdram_cke    <= 1;
                    sdram_cs_n   <= 0;
                    sdram_ras_n  <= 0;
                    sdram_cas_n  <= 0;
                    sdram_we_n   <= 1;
                end
                else begin
                    sdram_cke    <= 1;
                    sdram_cs_n   <= 0;
                    sdram_ras_n  <= 1;
                    sdram_cas_n  <= 1;
                    sdram_we_n   <= 1;
                end
                if (counter < T_RFC*2) begin
                    counter <= counter + 1;
                end
                else begin
                    counter <= 0;
                    stage <= 3;
                end
            end

            // -------- 状态3：模式寄存器设置 --------
            else if (stage == 3) begin
                if (counter == 0) begin
                    sdram_cke    <= 1;
                    sdram_cs_n   <= 0;
                    sdram_ras_n  <= 0;
                    sdram_cas_n  <= 0;
                    sdram_we_n   <= 0;
                    sdram_ba       <= 2'b00;
                    sdram_addr[10] <= 1'b0;
                    sdram_addr[9]  <= 1;          // Write burst: Single
                    sdram_addr[8:7]<= 2'b00;
                    sdram_addr[6:4]<= 3'b011;     // CAS Latency=3
                    sdram_addr[3]  <= 1'b0;
                    sdram_addr[2:0]<= 3'b000;     // Burst Length=1
                end
                else begin
                    sdram_cke   <= 1;
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 1;
                    sdram_cas_n <= 1;
                    sdram_we_n  <= 1;
                end
                if (counter < T_MRD) begin
                    counter <= counter + 1;
                end
                else begin
                    counter <= 0;
                    counter_refresh <= 0;
                    stage <= 4;
                    init_done <= 1;
                end
            end

            // -------- 状态4：空闲/请求/刷新 --------
            else if (stage == 4) begin
                wb_ack_o <= 0;
                sdram_dq_ie <= 0;
                if (counter_refresh > REFRESH_INTERVAL_NS/CLK_CYCLE_NS) begin
                    counter <= 0;
                    counter_refresh <= 0;
                    stage <= 5;
                end
                else if (req_valid) begin
                    if (req_write) begin
                        counter <= 0;
                        stage <= 7; // 写
                    end
                    else begin
                        counter <= 0;
                        stage <= 6; // 读
                    end
                end
                else begin
                    sdram_cke   <= 1;
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 1;
                    sdram_cas_n <= 1;
                    sdram_we_n  <= 1;
                end
            end

            // -------- 状态5：刷新 --------
            else if (stage == 5) begin
                if (counter == 0) begin
                    sdram_cke   <= 1;
                    sdram_addr[10] <= 1;
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 0;
                    sdram_cas_n <= 1;
                    sdram_we_n  <= 0;
                    sdram_dm    <= 4'b0000;
                end
                else if (counter == T_RP) begin
                    sdram_cke   <= 1;
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 0;
                    sdram_cas_n <= 0;
                    sdram_we_n  <= 1;
                end
                else begin
                    sdram_cke   <= 1;
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 1;
                    sdram_cas_n <= 1;
                    sdram_we_n  <= 1;
                end
                if (counter < T_RP+T_RFC) begin
                    counter <= counter + 1;
                end
                else begin
                    counter <= 0;
                    stage   <= 4;
                end
            end

            // -------- 状态6：读流程 --------
            else if (stage == 6) begin
                if (counter == 0) begin
                    sdram_cke   <= 1;
                    sdram_ba    <= req_addr[3:2];
                    sdram_addr  <= req_addr[22:12];
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 0;
                    sdram_cas_n <= 1;
                    sdram_we_n  <= 1;
                    sdram_dm    <= 4'b0000;
                end
                else if (counter == T_RCD) begin
                    sdram_cke   <= 1;
                    sdram_dm    <= 4'b0000;
                    sdram_ba    <= req_addr[3:2];
                    sdram_addr[10] <= 1;                  // A10=1, 自动预充电
                    sdram_addr[9:8]  <= req_addr[24:23];  // 可能按实际SDRAM磊加
                    sdram_addr[7:0] <= req_addr[11:4];
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 1;
                    sdram_cas_n <= 0;
                    sdram_we_n  <= 1;
                    sdram_dq_ie <= 0; // dq输入
                end
                else if (counter == T_RCD+CL) begin
                    wb_dat_o    <= sdram_dq;
                    wb_ack_o    <= 1;
                end
                else begin
                    wb_ack_o    <= 0;
                    sdram_cke   <= 1;
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 1;
                    sdram_cas_n <= 1;
                    sdram_we_n  <= 1;
                end
                if (counter < T_RCD+CL+T_RP) begin
                    counter <= counter + 1;
                end
                else begin
                    counter <= 0;
                    wb_ack_o <= 0;
                    stage   <= 4;
                end
            end

            // -------- 状态7：写流程 --------
            else if (stage == 7) begin
                if (counter == 0) begin
                    sdram_cke   <= 1;
                    sdram_ba    <= req_addr[3:2];
                    sdram_addr  <= req_addr[22:12];
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 0;
                    sdram_cas_n <= 1;
                    sdram_we_n  <= 1;
                    sdram_dm    <= ~req_wstrb;
                end
                else if (counter == T_RCD) begin
                    sdram_cke   <= 1;
                    sdram_dm    <= ~req_wstrb;
                    sdram_ba    <= req_addr[3:2];
                    sdram_addr[10] <= 1;
                    sdram_addr[9:8]  <= req_addr[24:23]; // 按需映射
                    sdram_addr[7:0] <= req_addr[11:4];
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 1;
                    sdram_cas_n <= 0;
                    sdram_we_n  <= 0;
                    sdram_dq_ie <= 1;
                    sdram_dq_r  <= req_wdata;
                    wb_ack_o    <= 1;
                end
                else begin
                    wb_ack_o    <= 0;
                    sdram_cke   <= 1;
                    sdram_cs_n  <= 0;
                    sdram_ras_n <= 1;
                    sdram_cas_n <= 1;
                    sdram_we_n  <= 1;
                end
                if (counter < T_RCD+T_WR+T_RP) begin
                    counter <= counter + 1;
                end
                else begin
                    counter <= 0;
                    wb_ack_o <= 0;
                    sdram_dq_ie <= 0;
                    stage   <= 4;
                end
            end

        end
    end

endmodule
