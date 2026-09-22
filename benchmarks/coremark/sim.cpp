#include "VPaSoCsim.h"
#include "verilated.h"
#include <cstdlib>
#include <cstdio>
int main(int argc, char **argv) {
    VerilatedContext context;
    VPaSoCsim dut{&context};
    auto tick = [&]() { dut.clock=0; dut.eval(); context.timeInc(1); dut.clock=1; dut.eval(); context.timeInc(1); };
    dut.io_gpio_In=0; dut.io_uart_rx=1; dut.reset=1;
    for(int i=0;i<10;i++) tick();
    dut.reset=0;
    unsigned long long max=argc>1?strtoull(argv[1],nullptr,10):2000000000ULL;
    for(unsigned long long n=0;n<max;n++) {
        tick();
        if(dut.io_exit) { dut.final(); printf("\nSIM_EXIT cycles=%llu\n", n+1); return 0; }
        if(context.gotFinish()) break;
    }
    dut.final(); fprintf(stderr,"CoreMark simulation timeout or unexpected finish\n"); return 1;
}
