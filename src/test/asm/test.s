	.section .init
    .globl _start
_start:
    # 写入0x41424344到0x40000000
    lui   t0, 0x40000      # t0 = 0x40000000
    lui   t1, 0x41424      # t1 = 0x41424000
    addi  t1, t1, 0x344    # t1 = t1 + 0x344 = 0x41424344
    sw    t1, 0(t0)        # *(0x40000000) = 0x41424344

    # 字节读取0x40000002，将读到的字节写入0x30000000
    lui   t3, 0x30000      # t3 = 0x30000000
    lbu   t2, 2(t0)        # t2 = *(uint8_t*)(0x70000002)  应该是0x42: B
    sb    t2, 0(t3)        # *(uint8_t*)(0x30000000) = t2
    lw    t2, 0(t0)
    sw    t2, 0(t3)        # 应是ABCD

    # 半字写入0x70000002
    li    t4, 0x4546
    sh    t4, 2(t0)        
    lhu   t2, 2(t0)
    sh    t2, 0(t3)        # 应是EF
    lw    t2, 0(t0)
    sw    t2, 0(t3)        # 应是EFCD
    
    .word 0xc0001073        # 结束
