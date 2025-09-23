    .section .init
    .globl _start
_start:
    # 主程序初始化
    li      sp, 0x10000ffc # 设置栈指针

    li      t1, 0x10000100 # counter地址
    sw      zero, 0(t1)    # counter清零

    # 配置PLIC
    li      t0, 0x50000004 # enable寄存器地址
    li      t1, 0x80       # 启用最高位中断
    sw      t1, 0(t0)

    # 软件设mtvec中断入口
    la      t0, isr_handler
    csrw    mtvec, t0
    # 打开外部中断使能
    li      t1, 0x800      # MEIE在第11位
    csrw    mie, t1
    # 打开全局中断使能
    csrs    mstatus, 0x8   # 0x8是MIE位的掩码（二进制为00001000，即bit3）

main_loop:
    nop
    j       main_loop

    .section .text
    .globl isr_handler
isr_handler:
    # == 保存调用者用到的寄存器 ==
    addi    sp, sp, -16
    sw      t0, 0(sp)
    sw      t1, 4(sp)
    sw      t2, 8(sp)
    sw      t3, 12(sp)

    # == 读PLIC claim，判断来源 ==
    li      t0, 0x5000000C
    lw      t1, 0(t0)     # t1 = 中断号 (1~8, 0无中断)

    # 简例：只要有中断就counter+1
    li      t2, 0x10000100
    lw      t3, 0(t2)
    addi    t3, t3, 1
    sw      t3, 0(t2)

    # ......可以根据t1分支到不同的处理
    # beq     t1, 1, do_irq1
    # beq     t1, 2, do_irq2
    # ...

    # == 写回PLIC complete通知已处理 ==
    sw      t1, 0(t0)     # 只需要把claim号写回即可

    # == 恢复寄存器 ==
    lw      t0, 0(sp)
    lw      t1, 4(sp)
    lw      t2, 8(sp)
    lw      t3, 12(sp)
    addi    sp, sp, 16

    mret

# 特殊说明：
# - 如果正常程序、isr_handler有用到更多寄存器，要扩展保护
# - 若某中断要响应不同内容，可用t1分发
# - 如果有软件请求强制中断，可用PLIC的pending(软置位)实现
