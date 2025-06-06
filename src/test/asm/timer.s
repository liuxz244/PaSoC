    .section .text
    .globl _start
_start:
    li sp, 0x10000ffc  # 设置栈指针

    # 设置中断入口
    la t0, isr
    csrw mtvec, t0

    # 关全局中断
    li t1, 0x08        # 1 << 3, MIE位
    csrrc zero, mstatus, t1

    # 关所有中断
    li t2, 0
    csrw mie, t2

    # 读取mtime低高
    li t0, 0x60000000  # t0=0x60000000
    lw t1, 0(t0)       # t1=mtime low
    lw t2, 4(t0)       # t2=mtime high

    # 加500触发
    li t3, 500         # 时钟频率不同可调
    add t1, t1, t3

    # 写回mtimecmp
    sw t1, 8(t0)       # mtimecmp low
    sw t2, 12(t0)      # mtimecmp high

    # 开定时器中断
    li t1, 0x80
    csrw mie, t1

    # 开全局中断
    li t1, 0x08
    csrrs zero, mstatus, t1

    # 主循环
1:  j 1b


#-------- 定时器中断处理函数 --------
    .align 2
isr:
    # 保存寄存器
    addi sp, sp, -20   # 5个寄存器*4字节
    sw a0,  0(sp)
    sw t0,  4(sp)
    sw t1,  8(sp)
    sw t2, 12(sp)
    sw t3, 16(sp)

    li a0, 0x12345678  # 做个标记，或调试断点

    li t0, 0x60000000
    lw t1, 0(t0)
    lw t2, 4(t0)
    li t3, 500
    add t1, t1, t3
    sw t1, 8(t0)
    sw t2, 12(t0)

    # 恢复寄存器
    lw a0,  0(sp)
    lw t0,  4(sp)
    lw t1,  8(sp)
    lw t2, 12(sp)
    lw t3, 16(sp)
    addi sp, sp, 20

    mret
