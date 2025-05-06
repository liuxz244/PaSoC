    .text
    .globl _start
_start:
    li   t0, 0x20000000       # PWM第0路寄存器地址
    li   t1, 127              # 占空比值，50%  假设PWM_MAX=255，127为50%占空比

    sw   t1, 0(t0)            # 写占空比寄存器，使PWM0输出50%占空比的PWM波形

    addi t0, t0, 4            # PWM第1路寄存器地址
    li   t1, 63               # 占空比值，25%  假设PWM_MAX=255，63为25%占空比

    sw   t1, 0(t0)            # 写占空比寄存器，使PWM1输出25%占空比的PWM波形
loop:
    j    loop                 # 无限循环，保持占空比不变
