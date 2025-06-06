#include "PaSoC.h"
#include <stdint.h>

#define GPIO_LED_NUM 4

/*
// 流水灯
int main() {
    int count = 0;
    while (1) {
        for (int i = 0; i < GPIO_LED_NUM; i++) {
            gpio_write_out(~(1 << i));
            //sleep_clk(50);
            sleep_ms(800);
        }
        count++;
        print_str("Round: ");
        print_dec(count);
        print_str("\n");
    }
    return 0;
}
*/

// GPIO输入
int main() {
    uint32_t in_val = gpio_read_in();
    print_str("gpio in: ");
    print_dec(in_val);
    print_char('\n');
    if (in_val & (1 << 3)) {
        // 第4个引脚(gpio_In(3))为高电平，测试应输入08（0000_1000）
        print_str("success\n");
    }
    return 0;
}