    .section .data
flag:   .word   0          # 用于标记分支结果

    .section .text
    .globl _start
_start:
    # 初始化
    li      t0, 0          # 用于循环计数
    li      t1, 10         # 循环10次
    la      t2, flag       # flag变量地址

loop_start:
    add     t0, t0, zero   # 加强型空操作，易于断点和观察

    blt     t0, t1, loop_body      # 如果 t0 < t1, 进入循环主体
    j       after_loop             # 跳出循环

loop_body:
    # 每次循环给flag +2（用以判断是否循环成功执行）
    lw      t3, 0(t2)
    addi    t3, t3, 2
    sw      t3, 0(t2)
    addi    t0, t0, 1
    j       loop_start

after_loop:
    # 此时循环应该执行了10次, flag应该为20
    # 下面进行分支陷阱测试
    li      t4, 20  # 正确的循环最终值
    lw      t5, 0(t2)
    beq     t5, t4, br_pass     # 正确，跳至通过分支标记
    j       br_fail             # 否则标记失败

br_pass:
    li      a7, 1          # ecall 富有副作用：将flag设为0x12345678
    li      t6, 0x12345678
    sw      t6, 0(t2)
    .word 0xc0001073        # 结束

br_fail:
    li      t6, 0xDEAD     # 若分支预测出错，flag变为0xDEAD
    sw      t6, 0(t2)
    ecall                  # 进入异常（用于测试陷阱跳转）

# 注意：ecall根据系统不同会进入操作系统/触发异常
#       你可在仿真后观察flag的内容:
#       0x12345678  -> 分支全部正确
#       0xDEAD      -> 分支预测/判断有误
