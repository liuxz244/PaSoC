    .section .text
    .globl _start

_start:
    # 正数除以正数
    li      t0, 25          # t0 = 25
    li      t1, 7           # t1 = 7

    divu    t2, t0, t1      # t2 = t0 / t1 (无符号除法: 25 / 7 = 3)
    remu    t3, t0, t1      # t3 = t0 % t1 (无符号余数: 25 % 7 = 4)
    div     t4, t0, t1      # t4 = t0 / t1 (有符号除法: 25 / 7 = 3)
    rem     t5, t0, t1      # t5 = t0 % t1 (有符号余数: 25 % 7 = 4)

    # 负数测试: 正数/负数
    li      t0, 25
    li      t1, -7

    div     t6, t0, t1      # t6 = 25 / -7 = -3
    rem     s0, t0, t1      # t7 = 25 % -7 = 4

    # 负数测试: 负数/正数
    li      t0, -25
    li      t1, 7

    div     s1, t0, t1      # s0 = -25 / 7 = -3 (0xFFFFFFFD)
    rem     s2, t0, t1      # s1 = -25 % 7 = -4 (0xFFFFFFFC)

    # 负数测试: 负数/负数
    li      t0, -25
    li      t1, -7

    div     s3, t0, t1      # s2 = -25 / -7 = 3
    rem     s4, t0, t1      # s3 = -25 % -7 = -4

    .word 0xc0001073        # 结束
