_start:
    # 主程序初始化
    li      t1, 0x100      # 设置counter在DTCM的地址
    sw      zero, 0(t1)    # counter = 0
    li      t1, 0x200      # 假设正常程序修改了t1的值
    li      t2, 0x300

    # CPU默认已打开中断使能

main_loop:
    nop                    # 等待外部中断（实际可加别的指令）
    j       main_loop      # 无限循环


# 外部中断服务程序
isr_handler:
    # 进入时已是Machine模式，MIE已被自动关
    addi sp, sp, -8        # 为栈分配空间
    sw   t1,  0(sp)        # 保存正常程序的t1寄存器
    sw   t2,  4(sp)

    # 下面简单地将counter加1
    li      t1, 0x100      # 设置counter在DTCM的地址
    lw      t2, 0(t1)
    addi    t2, t2, 1
    sw      t2, 0(t1)

    lw   t1,  0(sp)        # 恢复t1寄存器供正常程序使用
    lw   t2,  4(sp)
    addi sp, sp, 8         # 弹栈

    # 用mret返回正常程序
    mret
