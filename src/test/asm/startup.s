.globl _start
_start:
    la sp, _stack_top  # 或 la sp, _sp
    call main   # 进入main函数
    li t0, 20
1:  # 等待一会
    addi t0, t0, -1
    bnez t0, 1b
    unimp   # 退出仿真
2:  # 上板时死循环停住程序
    j 2b
