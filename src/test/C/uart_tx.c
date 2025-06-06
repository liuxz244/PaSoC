#include "PaSoC.h"
#include <stdint.h>

int main() {
    
    print_str("Hello, UART!\n");
    print_dec(2025);
    print_char('\n');
    print_hex(0xABCD, 4);
    print_char('E');
    print_char('\n');

    return 0;
}
