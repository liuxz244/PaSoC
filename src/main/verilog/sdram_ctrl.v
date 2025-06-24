module sys_sdram (
    input              clk,            // SDRAM 控制器时钟
    input              rst_n,          // 复位（低有效）
    input              i_valid,        // 输入有效信号，请求数据操作
    output reg         o_ready,        // 外设响应信号
    input      [31:0]  i_addr,         // 输入地址
    input      [31:0]  i_wdata,        // 输入写数据
    input      [3:0]   i_wstrb,        // 写使能位，每一位对应一个字节有效，为0则读
    output reg [31:0]  o_rdata,        // 输出读数据
    output reg         init_done,      // SDRAM初始化完成
    
    // SDRAM信号接口
    output reg         sdram_ras_n,    // 行地址选通信号
    output reg         sdram_cas_n,    // 列地址选通信号
    output reg         sdram_we_n,     // 写使能信号
    output reg [10:0]  sdram_addr,     // 地址总线
    output reg [1:0]   sdram_ba,       // Bank地址
    inout      [31:0]  sdram_dq,       // 数据总线
    output reg         sdram_cs_n,     // 片选
    output reg [3:0]   sdram_dm,       // 数据掩码
    output reg         sdram_cke       // 时钟使能
);

parameter CLK_CYCLE_NS = 28;           // SDRAM控制器时钟周期（ns）
parameter POWERON_DELAY_NS = 200000;   // 上电延时（ns）
parameter REFRESH_INTERVAL_NS = 15000; // 刷新间隔（ns）

// 时序参数，均为时钟周期数
parameter T_RC   = 3+1; // 行周期
parameter T_RP   = 1+1; // 预充电时间
parameter T_WR   = 2+1; // 写恢复
parameter T_MRD  = 2+1; // 模式寄存器设置时间
parameter T_RFC  = 3+1; // 刷新周期
parameter T_RCD  = 1+1; // 行地址到列地址延迟
parameter T_RRD  = 1+1; // 行激活到行激活延迟
parameter CL     = 3+1; // CAS延迟

// 控制dq方向与输出寄存器
reg [31:0] sdram_dq_r;  // dq输出数据缓存
reg        sdram_dq_ie; // dq方向使能：1-输出，0-输入
assign sdram_dq = sdram_dq_ie ? sdram_dq_r : 'hz;

// 计数器
reg [31:0] counter;           // 通用时序计数器
reg [31:0] counter_refresh;   // 用于刷新间隔计数

// 状态机：初始化→idle→访问→刷新
// 定义各阶段含义
// 0: 上电等待
// 1: 上电后预充电所有Bank
// 2: 上电后执行2次自动刷新
// 3: 上电后设置模式寄存器
// 4: 空闲
// 5: 刷新
// 6: 读操作
// 7: 写操作
reg [4:0] stage;

// 时序状态机实现
always @ (posedge clk or negedge rst_n) begin
    if (~rst_n) begin
        // 复位处理
        counter <= 0;
        counter_refresh <= 0;
        stage   <= 0;
        o_rdata <= 0;
        init_done <= 0;
    end
    else begin
        // 刷新间隔计数器
        counter_refresh <= counter_refresh + 1;
        
        //-----------------------------------------------
        // 状态0：上电等待（NOP直到达到最小初始化等待时间）
        //-----------------------------------------------
        if (stage == 'h0) begin
            sdram_cke    <= 1;
            sdram_cs_n   <= 0;
            sdram_ras_n  <= 1;
            sdram_cas_n  <= 1;
            sdram_we_n   <= 1;
            sdram_dm     <= 4'b1;
            // 等待200us
            if (counter < POWERON_DELAY_NS/CLK_CYCLE_NS) begin
                counter <= counter + 1;
            end else begin
                counter <= 0;
                // 进入下一阶段
                stage <= 1;
            end
        end

        //-----------------------------------------------
        // 状态1：预充电所有Bank
        //-----------------------------------------------
        if (stage == 'h1) begin
            if (counter == 0) begin
                // 发起预充电命令
                sdram_cke    <= 1;
                sdram_addr[10] <= 1;
                sdram_cs_n   <= 0;
                sdram_ras_n  <= 0;
                sdram_cas_n  <= 1;
                sdram_we_n   <= 0;
            end else begin
                // NOP
                sdram_cke    <= 1;
                sdram_cs_n   <= 0;
                sdram_ras_n  <= 1;
                sdram_cas_n  <= 1;
                sdram_we_n   <= 1;
            end
            // 等待tRP周期
            if (counter < T_RP) begin
                counter <= counter + 1;
            end else begin
                counter <= 0;
                // 进入下一阶段
                stage <= 2;
            end
        end

        //-----------------------------------------------
        // 状态2：执行2次自动刷新
        //-----------------------------------------------
        if (stage == 'h2) begin
            if (counter == 0) begin
                // 第一次自动刷新
                sdram_cke    <= 1;
                sdram_cs_n   <= 0;
                sdram_ras_n  <= 0;
                sdram_cas_n  <= 0;
                sdram_we_n   <= 1;
            end else if (counter == T_RFC) begin
                // 第二次自动刷新
                sdram_cke    <= 1;
                sdram_cs_n   <= 0;
                sdram_ras_n  <= 0;
                sdram_cas_n  <= 0;
                sdram_we_n   <= 1;
            end else begin
                // NOP
                sdram_cke    <= 1;
                sdram_cs_n   <= 0;
                sdram_ras_n  <= 1;
                sdram_cas_n  <= 1;
                sdram_we_n   <= 1;
            end
            // 等待2*tRFC周期
            if (counter < T_RFC*2) begin
                counter <= counter + 1;
            end else begin
                counter <= 0;
                // 进入下一阶段
                stage <= 3;
            end
        end

        //-----------------------------------------------
        // 状态3：设置模式寄存器
        //-----------------------------------------------
        if (stage == 'h3) begin
            if (counter == 0) begin
                // 设置模式寄存器
                sdram_cke    <= 1;
                sdram_cs_n   <= 0;
                sdram_ras_n  <= 0;
                sdram_cas_n  <= 0;
                sdram_we_n   <= 0;
                // 下面为SDRAM模式寄存器内容设置
                sdram_ba       <= 2'b00;
                sdram_addr[10] <= 1'b0;      // 保留
                sdram_addr[9]  <= 1;         // 单次写突发（Write burst = 1）
                sdram_addr[8:7]<= 2'b00;     // 测试模式：Normal
                sdram_addr[6:4]<= 3'b011;    // CAS Latency=3
                sdram_addr[3]  <= 1'b0;      // 顺序突发（Burst type=sequential）
                sdram_addr[2:0]<= 3'b000;    // 突发长度1
            end else begin
                // NOP
                sdram_cke   <= 1;
                sdram_cs_n  <= 0;
                sdram_ras_n <= 1;
                sdram_cas_n <= 1;
                sdram_we_n  <= 1;
            end
            // 等待tMRD周期
            if (counter < T_MRD) begin
                counter <= counter + 1;
            end else begin
                counter <= 0;
                counter_refresh <= 0;
                // 进入空闲状态
                stage <= 4;
                // 初始化完成
                init_done <= 1'b1;
            end
        end

        //-----------------------------------------------
        // 状态4：空闲（等待访问请求/到刷新间隔则转刷新/否则NOP）
        //-----------------------------------------------
        if (stage == 'h4) begin
            if (counter_refresh > REFRESH_INTERVAL_NS/CLK_CYCLE_NS) begin
                // 刷新间隔到，切换到刷新
                counter <= 0;
                counter_refresh <= 0;
                stage <= 5;
            end else if (i_valid) begin
                if (i_wstrb == 'h0) begin
                    // 读操作请求
                    counter <= 0;
                    stage <= 6;
                end else begin
                    // 写操作请求
                    counter <= 0;
                    stage <= 7;
                end
            end else begin
                // NOP
                sdram_cke   <= 1;
                sdram_cs_n  <= 0;
                sdram_ras_n <= 1;
                sdram_cas_n <= 1;
                sdram_we_n  <= 1;
            end
        end

        //-----------------------------------------------
        // 状态5：刷新流程（先预充电再自动刷新）
        //-----------------------------------------------
        if (stage == 'h5) begin
            if (counter == 0) begin
                // 先PrechargeAll
                sdram_cke   <= 1;
                sdram_addr[10] <= 1;
                sdram_cs_n  <= 0;
                sdram_ras_n <= 0;
                sdram_cas_n <= 1;
                sdram_we_n  <= 0;
                sdram_dm    <= 4'b0;
            end else if (counter == T_RP) begin
                // 然后AutoRefresh
                sdram_cke   <= 1;
                sdram_cs_n  <= 0;
                sdram_ras_n <= 0;
                sdram_cas_n <= 0;
                sdram_we_n  <= 1;
            end else begin
                // NOP
                sdram_cke   <= 1;
                sdram_cs_n  <= 0;
                sdram_ras_n <= 1;
                sdram_cas_n <= 1;
                sdram_we_n  <= 1;
            end
            // tRP + tRFC等待
            if (counter < T_RP+T_RFC) begin
                counter <= counter + 1;
            end else begin
                counter <= 0;
                stage   <= 4; // 回到空闲
            end
        end

        //-----------------------------------------------
        // 状态6：读操作流程
        //-----------------------------------------------
        if (stage == 'h6) begin
            if (counter == 0) begin
                // BankActivate: 激活行
                sdram_cke   <= 1;
                sdram_ba    <= i_addr[3:2];
                sdram_addr  <= i_addr[22:12];
                sdram_cs_n  <= 0;
                sdram_ras_n <= 0;
                sdram_cas_n <= 1;
                sdram_we_n  <= 1;
                sdram_dm    <= 4'b0;
            end else if (counter == T_RCD) begin
                // Read&Autoprecharge（读指令并自动预充电）
                sdram_cke   <= 1;
                sdram_dm    <= 4'b0;
                sdram_ba    <= i_addr[3:2];
                sdram_addr[10] <= 1;                  // A10=1，自动预充电
                sdram_addr[7:0] <= i_addr[11:4];
                sdram_cs_n  <= 0;
                sdram_ras_n <= 1;
                sdram_cas_n <= 0;
                sdram_we_n  <= 1;
                sdram_dq_ie <= 0; // 数据线输入
            end else if (counter == T_RCD+CL) begin
                // 到CAS LATENCY，数据可用
                o_rdata     <= sdram_dq;
                o_ready     <= 1;
            end else begin
                o_ready     <= 0;
                // NOP
                sdram_cke   <= 1;
                sdram_cs_n  <= 0;
                sdram_ras_n <= 1;
                sdram_cas_n <= 1;
                sdram_we_n  <= 1;
            end
            // 保证tRP延迟
            if (counter < T_RCD+CL+T_RP) begin
                counter <= counter + 1;
            end else begin
                counter <= 0;
                stage   <= 4;
            end
        end

        //-----------------------------------------------
        // 状态7：写操作流程
        //-----------------------------------------------
        if (stage == 'h7) begin
            if (counter == 0) begin
                // BankActivate: 激活行
                sdram_cke   <= 1;
                sdram_ba    <= i_addr[3:2];
                sdram_addr  <= i_addr[22:12];
                sdram_cs_n  <= 0;
                sdram_ras_n <= 0;
                sdram_cas_n <= 1;
                sdram_we_n  <= 1;
                sdram_dm    <= ~i_wstrb;  // 低电平写使能
            end else if (counter == T_RCD) begin
                // Write&Autoprecharge（写指令并自动预充电）
                sdram_cke   <= 1;
                sdram_dm    <= ~i_wstrb;
                sdram_ba    <= i_addr[3:2];
                sdram_addr[10] <= 1;
                sdram_addr[7:0] <= i_addr[11:4];
                sdram_cs_n  <= 0;
                sdram_ras_n <= 1;
                sdram_cas_n <= 0;
                sdram_we_n  <= 0;
                sdram_dq_ie <= 1;        // 数据线输出
                sdram_dq_r  <= i_wdata;  // 输出数据
                o_ready     <= 1;        // 完成
            end else begin
                o_ready     <= 0;
                // NOP
                sdram_cke   <= 1;
                sdram_cs_n  <= 0;
                sdram_ras_n <= 1;
                sdram_cas_n <= 1;
                sdram_we_n  <= 1;
            end
            if (counter < T_RCD+T_WR+T_RP) begin
                counter <= counter + 1;
            end else begin
                counter <= 0;
                stage   <= 4;
            end
        end
    end
end

endmodule
