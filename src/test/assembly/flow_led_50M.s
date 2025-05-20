_start:
    # 将 GPIO 输出地址 0x10000004 载入 t0
    # li 是伪指令，会被展开为lui+addi,lui写入高位，addi加上低位
    li    t0, 0x10000004

    # 初始化 LED 模式，设 t2 = 0x01
    addi   t2, x0, 1

main_loop:
    # 取反 t2 内容，按位取反后存入 t1 (开发板上LED是低有效点亮)
    xori  t1, t2, -1        # t1 = ~t2

    # 将取反后的值写入 GPIO 输出寄存器
    sw    t1, 0(t0)

    # 延时循环：这里的值和具体的时钟周期数无关
    li     t3, 5000000      # t3 = 5000000
delay_loop:
    addi   t3, t3, -1      # 计数器 -1
    bnez   t3, delay_loop  # 若 t3 不为 0 则继续延时

    # 判断当前 LED 模式是否为 8 (最高位点亮)
    addi   t4, x0, 8    # t4 = 8
    beq    t2, t4, reset_led

    # 否则左移 1 位，实现流水效果
    slli   t2, t2, 1
    jal    x0, main_loop  # 跳回主循环

reset_led:
    # 当 t2 == 8 时，重置 LED 模式为 0x01
    addi   t2, x0, 1
    jal    x0, main_loop  # 跳回主循环
