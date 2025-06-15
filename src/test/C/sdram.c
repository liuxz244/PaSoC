#include "PaSoC.h"
#include <stdint.h>

#define SDRAM_BASE   0x40000000U
#define SDRAM_END    0x40800000U   // 0x407FFFFF + 1
#define SDRAM_STEP   4             // 步进4字节(32位)

/**
 * @brief 对SDRAM区间做写入-&读验证测试
 *        两轮测试: 1. 地址值写入测试 2. 反码写入测试
 *
 * 打印进度，发现错误时立即报告并停止。
 */
void test_sdram(void)
{
    unsigned int addr;

    // 第一轮:写入"地址本身"
    print_str("Pattern 1: write address...\r\n");
    for(addr = SDRAM_BASE; addr < SDRAM_END; addr += SDRAM_STEP)
        write_mem(addr, addr);

    // 验证"地址本身"模式
    for(addr = SDRAM_BASE; addr < SDRAM_END; addr += SDRAM_STEP){
        unsigned int val = read_mem(addr);
        if(val != addr){
            print_str("Error at addr=");
            print_hex(addr, 8);    // 8位16进制
            print_str(", expected=");
            print_hex(addr, 8);
            print_str(", actual=");
            print_hex(val, 8);
            print_str("\r\n");
            print_str("\r\nSDRAM test FAILED\r\n");
            return;
        }
    }
    print_str("Pattern 1 OK\r\n");

    // 第二轮:写入~"地址本身"（按位取反）
    print_str("Pattern 2: write ~address...\r\n");
    for(addr = SDRAM_BASE; addr < SDRAM_END; addr += SDRAM_STEP)
        write_mem(addr, ~addr);

    // 验证"反码"模式
    for(addr = SDRAM_BASE; addr < SDRAM_END; addr += SDRAM_STEP){
        unsigned int val = read_mem(addr);
        if(val != ~addr){
            print_str("Error at addr=");
            print_hex(addr, 8);
            print_str(", expected=");
            print_hex(~addr, 8);
            print_str(", actual=");
            print_hex(val, 8);
            print_str("\r\nSDRAM test FAILED\r\n");
            return;
        }
    }
    print_str("Pattern 2 OK\r\n");

    print_str("SDRAM TEST PASSED.\r\n");
}

int main() {

    print_str("SDRAM Test Start\r\n");

    test_sdram();

    return 0;
}