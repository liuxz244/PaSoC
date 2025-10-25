#include <stdint.h>

#ifndef PASOC_H
#define PASOC_H

#define UART_BASE_ADDR   0x30000000UL  // 定义UART发送寄存器地址
#define UART_TX_REG   (*(volatile uint8_t *)(UART_BASE_ADDR + 0x00))
#define UART_RX_REG   (*(volatile uint8_t *)(UART_BASE_ADDR + 0x04))
#define UART_RX_CNT   (*(volatile uint8_t *)(UART_BASE_ADDR + 0x08))
#define CLINT_BASE_ADDR  0x60000000UL  // 定义CLINT基地址
#define MTIME_LO      (*(volatile unsigned int *)(CLINT_BASE_ADDR + 0x00))
#define MTIME_HI      (*(volatile unsigned int *)(CLINT_BASE_ADDR + 0x04))
#define MTIMECMP_LO   (*(volatile unsigned int *)(CLINT_BASE_ADDR + 0x08))
#define MTIMECMP_HI   (*(volatile unsigned int *)(CLINT_BASE_ADDR + 0x0C))
#define GPIO_BASE_ADDR   0x00000000UL  // GPIO基地址
#define GPIO_IN_REG   (*(volatile uint32_t *)(GPIO_BASE_ADDR + 0x00))
#define GPIO_OUT_REG  (*(volatile uint32_t *)(GPIO_BASE_ADDR + 0x04))
#define PLIC_BASE_ADDR   0x50000000UL
#define PLIC_PENDING  (*(volatile uint32_t *)(PLIC_BASE_ADDR + 0x00))
#define PLIC_ENABLE   (*(volatile uint32_t *)(PLIC_BASE_ADDR + 0x04))
#define PLIC_CLAIM    (*(volatile uint32_t *)(PLIC_BASE_ADDR + 0x0C))
#define VGA_BASE_ADDR    0x70000000UL
#define VGA_WIDTH        640
#define VGA_HEIGHT       480
#define VGA_MEM_DEPTH  (VGA_WIDTH * VGA_HEIGHT)


#ifndef FREQ_HZ  // 定义CPU时钟频率
#define FREQ_HZ 36000000UL  // 默认值
#endif
#define CYCLES_PER_MS (FREQ_HZ / 1000UL)

/**
 * 从CSR寄存器读取值
 * 
 * @param[in] reg CSR寄存器名字(如mstatus)
 * @return        寄存器的内容
 */
#define read_csr(reg) ({ unsigned int __tmp; asm volatile ("csrr %0, " #reg : "=r"(__tmp)); __tmp; })

/**
 * 写CSR寄存器
 * 
 * @param[in] reg CSR寄存器名字(如mtvec)
 * @param[in] val 写入的数据
 */
#define write_csr(reg, val) asm volatile ("csrw " #reg ", %0" :: "rK"(val))

void print_char(char ch);
void print_str(const char *str);
void print_dec(unsigned int val);
void print_hex(unsigned int val, int digits);

unsigned long long read_mtime(void);
void sleep_clk(unsigned long long cycles);
void sleep_ms(unsigned long ms);

uint32_t gpio_read_in(void);
uint32_t gpio_read_out(void);
void gpio_write_out(uint32_t val);
void gpio_set_bit(int bit);
void gpio_clear_bit(int bit);
void gpio_toggle_bit(int bit);
void gpio_write_bit(int bit, int value);

void write_mtimecmp(unsigned long long val);
void trap_init(void);
void interrupt_init(int enable_timer, int enable_external);
void timer_init(unsigned long long interval);
void __attribute__((interrupt)) trap_handler(void);
void timer_irq_handler(void);
void external_irq_handler(void);
void plic_init(uint32_t irq_mask);

unsigned char get_char(void);
void get_str(char *buf, int maxlen);
unsigned int get_uint(void);
unsigned int get_hex(void);

int strcmp(const char *s1, const char *s2);
unsigned char get_char_noecho(void);
void write_mem(uint32_t addr, uint32_t value);
uint32_t read_mem(uint32_t addr);
void write_mem16(uint32_t addr, uint16_t value);
uint16_t read_mem16(uint32_t addr);
void write_mem8(uint32_t addr, uint8_t value);
uint8_t read_mem8(uint32_t addr);
void vga_clear(uint8_t r, uint8_t g, uint8_t b);
void vga_draw_color_bars(void);


#endif
