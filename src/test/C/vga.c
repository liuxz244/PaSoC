#include "PaSoC.h"
#include <stdint.h>


int main(void) {

    vga_clear(0, 0, 0);  // 黑色清屏

    sleep_ms(100);

    vga_draw_color_bars();

    return 0;
}