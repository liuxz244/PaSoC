#include "PaSoC.h"
#include <stdint.h>


volatile unsigned int g_tick = 0;

void external_irq_handler(void)
{   
    print_str("External Interrupt: ");
    uint32_t irq_num = PLIC_CLAIM & 0xF;   // 1~8号，0为无有效中断
    if(irq_num != 0)
    {
        switch(irq_num)
        {
            case 8:   // 处理GPIO中断
                print_str("GPIO\n");
                break;
            default:  // 其它中断
                print_str("Other\n");
                break;
        }
        PLIC_CLAIM = irq_num;  // 清pending，只需把中断号写回去
    }
}

void timer_irq_handler(void) 
{
    // 重新设置下个定时点
    timer_init(300LL);
    
    // 用户自己的处理
    ++g_tick;
    print_str("Timer Interrupt: ");
    print_hex(g_tick, 1);
    print_char('\n');
}


int main(void)
{
    trap_init();  // 设置trap入口
    interrupt_init(0, 1);   // 按参数使能中断
    plic_init((1<<0) | (1<<7)); // 使能外部中断0与7
    timer_init(200LL);  // 200周期一次
    while (1) {
        // do nothing
    }
}
