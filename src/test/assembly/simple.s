_start:
    # 用 li 伪指令加载立即数
    li x5, 5          # x5 = 5
    li x6, 10         # x6 = 10

    # 算术运算
    add x7, x5, x6    # x7 = x5 + x6 = 15
    li x8, 3          # x8 = 3
    sub x9, x7, x8    # x9 = x7 - x8 = 12

    # 逻辑运算
    and x10, x5, x6   # x10 = 5 & 10 = 0
    or  x11, x5, x6   # x11 = 5 | 10 = 15
    xor x12, x5, x6   # x12 = 5 ^ 10 = 15

    # 立即数与寄存器相加示例
    addi x13, x9, 7   # x13 = x9 + 7 = 19

    # 程序结束
end:
    unimp