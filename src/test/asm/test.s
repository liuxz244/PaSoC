	.section .init
    .globl _start
_start:
    # 写入0x12345678到0x70000000
    lui   t0, 0x71000           # t0 = 0x70000000
    lui   t1, 0x12345           # t1 = 0x12345000
    addi  t1, t1, 0x678         # t1 = t1 + 0x678 = 0x12345678
    sw    t1, 0(t0)             # *(0x70000000) = 0x12345678

    # 字节读取0x70000002，将读到的字节写入0x30000000
    lui   t3, 0x30000           # t3 = 0x30000000
    lbu   t2, 2(t0)             # t2 = *(uint8_t*)(0x70000002)
    sb    t2, 0(t3)             # *(uint8_t*)(0x30000000) = t2
    lw    t2, 0(t0)
    sw    t2, 0(t3)

    # 半字写入0x70000002
    li    t4, 0xABCD
    sh    t4, 2(t0)
    nop
    lhu   t2, 2(t0)
    sh    t2, 0(t3)
    lw    t2, 0(t0)
    sw    t2, 0(t3)
    
1:  # 上板时死循环停住程序
    j 1b
