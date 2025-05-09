    .text
    .globl _start
_start:
    # --- 1. 初始化PWM占空比 ---
    li   t0, 0x20000000      # PWM0寄存器地址
    li   t1, 191             # 75% 占空比 (假设PWM_MAX=255)
    sw   t1, 0(t0)

    addi t0, t0, 4           # PWM1寄存器地址
    li   t1, 5               # 2% 占空比
    sw   t1, 0(t0)

    # --- 2. UART 发送数据初始化 ---
    li    s6, 0x30000000     # UART发送寄存器地址
    li    s7, 0xa5           # 初始待发送数据
    li    t6, 10             # 发送次数计数器
    
    # --- 3. 初始化LED跑马灯相关变量 ---
    li    s0, 0x10000004     # GPIO输出地址，使用s0寄存器保存
    
    addi  s1, x0, 1          # LED模式控制变量s1 = 0x01

    # 跳转至LED主循环
    jal   x0, led_main_loop

# -------- LED跑马灯主循环 ---------
led_main_loop:
    # 取反s1内容，按位取反后存到s2（LED共阳，低有效点亮）
    xori  s2, s1, -1

    # 将取反的值写入GPIO寄存器
    sw    s2, 0(s0)

    # 延时循环(简单模拟延时)
    li    s3, 5000000
delay_loop:
    addi  s3, s3, -1
    bnez  s3, delay_loop

    # 判断LED模式是否为0x08(最高位)
    addi  s4, x0, 8
    beq   s1, s4, led_reset

    # 否则左移1位，流水灯效果
    slli  s1, s1, 1
    
    # LED跑马灯循环次数计数（使用s5）
    addi  s5, s5, 1
    
    # 达到一定次数（10次）后切换到UART发送，否则继续跑马灯
    li    t5, 10
    beq   s5, t5, uart_send_loop

    jal   x0, led_main_loop

led_reset:
    # 复位LED模式为0x01
    addi  s1, x0, 1
    jal   x0, led_main_loop
    
# -------- UART 发送循环 ---------
uart_send_loop:
    sw    s7, 0(s6)          # 发送数据

    addi  s7, s7, 1          # 数据递增
    addi  t6, t6, -1         # 计数器减1
    bnez  t6, uart_send_loop

# -------- 程序结束，重新进入LED循环 ---------
reloop:
    li    s5, 0
    li    t6, 10
    jal   x0, led_main_loop
