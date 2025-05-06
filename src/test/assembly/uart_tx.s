_start:
    li   t0, 0x30000000     # UART_TX_ADDR 立即数加载
    li   t1, 0xa5           # 初始待发送数据
    li   t2, 10             # 循环计数器：发送 10 次

loop:
    sw   t1, 0(t0)          # 写数据到 UART 外设（模块中根据使能配置只发送部分字节）
    addi t1, t1, 1          # 数据递增，便于观察连续变化
    addi t2, t2, -1         # 循环计数器递减
    bnez t2, loop           # 计数器非零则继续循环

finish:
    j finish                # 无限循环，表示程序结束
