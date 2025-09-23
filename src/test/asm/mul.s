    .section .text
    .globl _start

_start:
    # 初始化寄存器
    li      t0, 7         # t0 = 7
    li      t1, -3        # t1 = -3

    # 测试 MUL 指令（低位乘积）
    mul     t2, t0, t1    # t2 = t0 * t1，期望 t2 为 -21，补码为 0xFFFFFFEB

    # 测试 MULH 指令（高位乘积）
    mulh    t3, t0, t1    # t3 = 高 32 位乘积，期望为 0xFFFFFFFF（-1的32位补码）

    # 测试 MULHSU 指令（有符号乘无符号，取高位乘积）
    mulhsu  t4, t1, t0    # t4 = 有符号-3乘以无符号7的高32位，期望结果为 0xFFFFFFFF

    # 测试 MULHU 指令（均作为无符号数处理，取高位乘积）
    mulhu   t5, t0, t1    # t5 = 无符号7乘以无符号-3的高32位，期望结果为 0x00000006

    .word 0xc0001073      # 程序结束，退出仿真
