#include "PaSoC.h"
#include <stdint.h>


int main() {
    
    print_str("sleep\n");
    sleep_ms(1);
    print_str("awake\n");

    return 0;
}
