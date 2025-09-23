#include "PaSoC.h"
#include <stdint.h>


int main() {
    
    unsigned char str[10];
    unsigned int uint;
    unsigned int hex;

    while (1) {
        print_str("\nInput str: ");
        get_str(str, 10);
        print_str("\nReceived: ");
        print_str(str);
        print_str("\nInput uint: ");
        uint = get_uint();
        print_str("\nReceived: ");
        print_dec(uint);
        print_str("\nInput hex: ");
        hex = get_hex();
        print_str("\nReceived: 0x");
        print_hex(hex, 4);
        print_char('\n');
    }

    return 0;
}