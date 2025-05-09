    .text
    .globl _start
_start:
    # 初始写入地址
    li   a0, 0x40000000        # 写入起始地址
    li   t1, 0x4000003C        # 写入结束地址（含）

    # 初始字符ASCII值 'A'
    li   t2, 0x41              # t2：当前起始字符'A'的ASCII

loop_write:
    # 生成4字节数据：4个连续字符
    # t2 当前字符
    # 构造32bit数据格式: 字节顺序为大端，这里为了方便，字节拼接如下：
    #   word = (c1 << 24) | (c2 << 16) | (c3 << 8) | c4
    # c1,c2,c3,c4为连续字符ASCII码，循环到'Z'后回到'A'

    # 计算4个字符，循环范围为'A'(0x41)->'Z'(0x5A)
    mv   t3, t2            # t3 = c1
    addi t4, t3, 1         # c2 = c1+1
    addi t5, t3, 2         # c3 = c1+2
    addi t6, t3, 3         # c4 = c1+3

    # 对字符超过'Z'(0x5A)进行循环，返回'A'(0x41)
    # 用一个辅助宏实现字符循环，但这里用代码实现

    # 字符循环检查：
    li   a1, 0x5A          # 'Z'

    blt  t4, a1, skip1
    li   t4, 0x41          # 'A'
skip1:
    blt  t5, a1, skip2
    li   t5, 0x41
skip2:
    blt  t6, a1, skip3
    li   t6, 0x41
skip3:

    # 拼接4字节: (c1 << 24) | (c2 << 16) | (c3 << 8) | c4
    sll  t3, t3, 24
    sll  t4, t4, 16
    sll  t5, t5, 8
    or   t3, t3, t4
    or   t3, t3, t5
    or   t3, t3, t6      # t3 = 32bit拼接数据

    sw   t3, 0(a0)       # 写入内存

    # 地址递增4
    addi a0, a0, 4

    # 地址判断，如果超过0x4000003C，则回到0x40000000
    bgt  a0, t1, wrap_addr

    # 字符起始值 +1，循环回到'A'
    addi t2, t2, 1
    li   a1, 0x5A
    bgt  t2, a1, reset_char

    j    loop_write

wrap_addr:
    li   a0, 0x40000000    # 地址回绕
    j    loop_write

reset_char:
    li   t2, 0x41          # 重置字符到'A'
    j    loop_write
