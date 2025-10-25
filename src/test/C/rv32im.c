#include "PaSoC.h"
#include <stdint.h>

void test_rv32im(void)
{
    uint32_t a = 42, b = 7;
    uint32_t res, res_hi;

    print_str("\n==== RV32IM ====\n");

    // ===== RV32I 基本算术 =====
    asm volatile ("add %0, %1, %2" : "=r"(res) : "r"(a), "r"(b));
    print_str("add: "); print_dec(a); print_str(" + "); print_dec(b);
    print_str(" = "); print_dec(res); print_str("\n");

    asm volatile ("sub %0, %1, %2" : "=r"(res) : "r"(a), "r"(b));
    print_str("sub: "); print_dec(a); print_str(" - "); print_dec(b);
    print_str(" = "); print_dec(res); print_str("\n");

    // ===== RV32I 逻辑运算 =====
    asm volatile ("and %0, %1, %2" : "=r"(res) : "r"(a), "r"(b));
    print_str("and: "); print_hex(a, 8); print_str(" & "); print_hex(b, 8);
    print_str(" = "); print_hex(res, 8); print_str("\n");

    asm volatile ("or %0, %1, %2" : "=r"(res) : "r"(a), "r"(b));
    print_str("or:  "); print_hex(a, 8); print_str(" | "); print_hex(b, 8);
    print_str(" = "); print_hex(res, 8); print_str("\n");

    asm volatile ("xor %0, %1, %2" : "=r"(res) : "r"(a), "r"(b));
    print_str("xor: "); print_hex(a, 8); print_str(" ^ "); print_hex(b, 8);
    print_str(" = "); print_hex(res, 8); print_str("\n");

    // ===== RV32I 移位运算 =====
    asm volatile ("sll %0, %1, %2" : "=r"(res) : "r"(a), "r"(1));
    print_str("sll: "); print_dec(a); print_str(" << 1");
    print_str(" = "); print_dec(res); print_str("\n");

    asm volatile ("srl %0, %1, %2" : "=r"(res) : "r"(a), "r"(1));
    print_str("srl: "); print_dec(a); print_str(" >> 1");
    print_str(" = "); print_dec(res); print_str("\n");

    // ===== RV32I 比较指令 =====
    asm volatile ("slt %0, %1, %2" : "=r"(res) : "r"(a), "r"(b));
    print_str("slt: "); print_dec(a); print_str(" < "); print_dec(b);
    print_str(" = "); print_dec(res); print_str("\n");

    // ===== RV32M 乘法 =====
    asm volatile ("mul %0, %1, %2" : "=r"(res) : "r"(a), "r"(b));
    print_str("mul: "); print_dec(a); print_str(" * "); print_dec(b);
    print_str(" = "); print_dec(res); print_str("\n");

    // ===== RV32M 除法和余数 =====
    asm volatile ("div %0, %1, %2" : "=r"(res) : "r"(a), "r"(b));
    print_str("div: "); print_dec(a); print_str(" / "); print_dec(b);
    print_str(" = "); print_dec(res); print_str("\n");

    asm volatile ("rem %0, %1, %2" : "=r"(res) : "r"(a), "r"(b));
    print_str("rem: "); print_dec(a); print_str(" % "); print_dec(b);
    print_str(" = "); print_dec(res); print_str("\n");

    print_str("==== Test End ====\n");
}

int main(void)
{
    test_rv32im();

    return 0;
}
