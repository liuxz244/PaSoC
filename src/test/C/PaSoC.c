#include "PaSoC.h"
#include <stdint.h>
#include <ctype.h>

// Doxygen 风格注释

/**
 * 向UART发送单个字符
 *
 * @param ch  待发送的字符
 */
void print_char(char ch)
{
    UART_TX_REG = ch; // 发送字符
}

/**
 * 发送字符串到UART，遇到'\0'结束
 *
 * @param str  指向以'\0'结尾的字符串
 */
void print_str(const char *str)
{
    while (*str)
        print_char(*str++);
}

/**
 * 以十进制格式输出无符号整数
 *
 * @param val  待打印的无符号整数
 */
void print_dec(unsigned int val)
{
    static const unsigned int pow10[] = {
        1000000000, 100000000, 10000000, 1000000, 100000, 10000, 1000, 100, 10, 1
    };
    int started = 0;
    for (int i = 0; i < 10; ++i) {
        char digit = 0;
        while (val >= pow10[i]) { 
            val -= pow10[i];
            digit++;
        }
        if (digit > 0 || started || i == 9) {
            print_char('0' + digit);
            started = 1;
        }
    }
}

/**
 * 以十六进制格式输出无符号整数（指定输出位数）
 *
 * @param val     待打印的无符号整数
 * @param digits  输出的十六进制位数（如4表示输出4位，8表示输出8位）
 */
void print_hex(unsigned int val, int digits)
{
    for (int i = (4*digits)-4; i >= 0; i -= 4)
        print_char("0123456789ABCDEF"[(val >> i) & 0xF]);
}


/**
 * 读取CLINT的64位mtime当前值
 *
 * @return  当前mtime计数器的64位值
 */
unsigned long long read_mtime(void)
{
    unsigned int hi, lo;
    do {
        hi = MTIME_HI;
        lo = MTIME_LO;
    } while (hi != MTIME_HI); // 防止读取期间溢出
    return ((unsigned long long)hi << 32) | lo;
}

/**
 * CPU忙等待指定的时钟周期数
 *
 * @param cycles  要延时的时钟周期数
 */
void sleep_clk(unsigned long long cycles)
{
    unsigned long long start = read_mtime();
    while ((read_mtime() - start) < cycles) {
        // busy wait
    }
}

/**
 * 延时指定毫秒数，在延迟很短时有误差
 *
 * @param ms  要延时的毫秒数
 */
void sleep_ms(unsigned long ms)
{
    unsigned long cycles = 0;
    for(unsigned long i = 0; i < ms; ++i) {
        cycles += CYCLES_PER_MS;
    }
    sleep_clk(cycles);
}


/**
 * 读取GPIO输入寄存器值
 *
 * @return  当前GPIO输入引脚的值的32位无符号整数
 */
uint32_t gpio_read_in(void)
{
    return GPIO_IN_REG;
}

/**
 * 读取GPIO输出寄存器值
 *
 * @return  当前GPIO输出寄存器的32位无符号整数值
 */
uint32_t gpio_read_out(void)
{
    return GPIO_OUT_REG;
}

/**
 * 设置GPIO输出寄存器为指定值
 *
 * @param val  要写入GPIO输出寄存器的32位值
 */
void gpio_write_out(uint32_t val)
{
    GPIO_OUT_REG = val;
}

/**
 * 置位GPIO输出寄存器的某一位
 *
 * @param bit  要置1的位编号（0~31）
 */
void gpio_set_bit(int bit)
{
    GPIO_OUT_REG = GPIO_OUT_REG | (1 << bit);
}

/**
 * 清零GPIO输出寄存器的某一位
 *
 * @param bit  要清0的位编号（0~31）
 */
void gpio_clear_bit(int bit)
{
    GPIO_OUT_REG = GPIO_OUT_REG & ~(1 << bit);
}

/**
 * 翻转GPIO输出寄存器的某一位
 *
 * @param bit  要翻转的位编号（0~31）
 */
void gpio_toggle_bit(int bit)
{
    GPIO_OUT_REG = GPIO_OUT_REG ^ (1 << bit);
}

/**
 * 设置或清除GPIO输出寄存器的某一位
 *
 * @param bit     要操作的位编号（0~31）
 * @param value   要设置的值（1=置位，0=清零）
 */
void gpio_write_bit(int bit, int value)
{
    if(value)
        gpio_set_bit(bit);
    else
        gpio_clear_bit(bit);
}


/**
 * 写MTIMECMP寄存器，配置定时器下一触发时刻
 * 
 * @param[in]  val  64位定时时间（下次触发绝对时间）
 */
void write_mtimecmp(unsigned long long val)
{
    MTIMECMP_HI = 0xFFFFFFFF;
    MTIMECMP_LO = (unsigned int)(val & 0xFFFFFFFF);
    MTIMECMP_HI = (unsigned int)(val >> 32);
}

/**
 * 初始化trap向量，设置mtvec为trap_handler
 */
void trap_init(void)
{
    write_csr(mtvec, (unsigned int)trap_handler);
}

/**
 * 按参数初始化中断控制寄存器，选择性打开定时器中断和外部中断
 *
 * @param enable_timer    1: 允许定时器中断(MTIE)，0: 不允许
 * @param enable_external 1: 允许外部中断(MEIE)，  0: 不允许
 */
void interrupt_init(int enable_timer, int enable_external)
{
    unsigned int mie = read_csr(mie);  // CSR 0x304

    if (enable_timer)
        mie |= (1 << 7);               // 设置MTIE（bit 7）
    else
        mie &= ~(1 << 7);              // 清除MTIE

    if (enable_external)
        mie |= (1 << 11);              // 设置MEIE（bit 11）
    else
        mie &= ~(1 << 11);             // 清除MEIE

    write_csr(mie, mie);

    unsigned int mstatus = read_csr(mstatus); // CSR 0x300
    mstatus |= (1 << 3);    // 通常总是打开MSTATUS.MIE（bit3），允许中断
    write_csr(mstatus, mstatus);
}

/**
 * 启动或重新设置定时器触发点
 * 
 * @param[in]  interval  距现在多少个mtime周期后触发
 */
void timer_init(unsigned long long interval)
{
    unsigned long long now = read_mtime();
    write_mtimecmp(now + interval);
}

/**
 * 使能多个外部中断。
 * 
 * @param[in]  irq_mask  例：irq_mask=0x07 使能0/1/2号通道
 */
void plic_init(uint32_t irq_mask)
{
    PLIC_ENABLE = irq_mask;
}

/**
 * @brief  RISC-V 异常与中断统一入口（Trap Handler）
 *
 * 该函数作为 RISC-V 架构下的总 trap 入口，中断发生时由 mtvec 跳转执行。
 * 通过读取 mcause CSR，区分并分发到具体的中断服务函数。目前支持：
 *   - Machine 定时器中断 (mcause 最高位为1，编码7）：调用 timer_irq_handler()
 *   - Machine 外部中断   (mcause 最高位为1，编码11)：调用 external_irq_handler()
 *
 * @note  若有新的 trap 类型需要处理，可在此函数中按 mcause 分类分发。
 *
 * @see   timer_irq_handler
 * @see   external_irq_handler
 */
void __attribute__((interrupt)) trap_handler(void)
{
    unsigned int mcause = read_csr(mcause);
    if ((mcause & 0x80000000) && ((mcause & 0x1F) == 7))  {
        timer_irq_handler();   // 定时器中断
    }
    if ((mcause & 0x80000000) && ((mcause & 0x1F) == 11)) {
        external_irq_handler();  // 外部中断
    }
}


/**
 * @brief 从UART接收寄存器读取一个字符。
 * 
 * @return unsigned char 读取到的字符。
 * 
 * 该函数从UART_RX_REG读取一个字节，并调用print_char打印该字符。
 */
unsigned char get_char(void)
{   
    unsigned char ch = UART_RX_REG;
    print_char(ch);   // 打印读取到的数据
    return ch;
}

/**
 * @brief 从串口逐字符读取并保存到字符串缓冲区，直到遇到回车/换行或达到最大长度。
 * 
 * @param[in,out] buf   字符串缓冲区指针，函数会将读取到的字符串存放到该缓冲区。
 * @param[in]     maxlen 缓冲区最大长度（包含结尾\0），函数确保不超过maxlen-1个字符，并自动补\0。
 * 
 * 此函数不断调用get_char获取字符，当读取到换行('\n')或回车('\r')时输入结束。
 * 最终buf结尾会自动补\0，形成C字符串。
 */
void get_str(char *buf, int maxlen)
{
    int i = 0;
    unsigned char c;

    // 保证字符串长度不超过 maxlen-1（为 \0 留出空间）
    while (i < maxlen - 1)
    {
        c = get_char();
        // 换行('\n')或回车('\r')认为是输入结束
        if (c == '\r' || c == '\n')
        {
            break;
        }
        buf[i++] = c;
    }
    buf[i] = '\0'; // 结尾加 \0 形成C字符串
}

/**
 * @brief 从串口读取输入字符串并转为无符号10进制整数。
 * 
 * 支持自然的输入习惯：只识别连续的数字字符（'0'~'9'），中途遇到回车/换行立即结束。
 * 若输入内容不是数字，则返回0。
 * 
 * @return unsigned int 读取到的无符号整数值
 */
unsigned int get_uint(void)
{
    unsigned int value = 0;
    unsigned char c;
    int started = 0;  // 是否已经开始读取数字

    while (1)
    {
        c = get_char();
        if (c == '\r' || c == '\n')    // 回车/换行结束输入
            break;
        if (c >= '0' && c <= '9')
        {
            started = 1;
            value = value * 10 + (c - '0');
        }
        else if (started)  // 已经有数字开始，遇到非数字代表结束
        {
            break;
        }
        // 若还没开始输入数字，继续读取
    }
    return value;
}

/**
 * @brief 从串口读取输入，当做十六进制字符串并转为无符号整数。
 * 
 * 仅处理连续的十六进制数字（0-9, a-f, A-F），碰到非16进制字符或回车/换行自动结束。
 * 如输入 "1a2B\n" 返回 0x1a2b。
 *
 * @return unsigned int 读取到的十六进制数据
 */
unsigned int get_hex(void)
{
    unsigned int value = 0;
    unsigned char c;
    int started = 0;

    while (1)
    {
        c = get_char();
        if (c == '\n' || c == '\r')    // 换行/回车视为输入结束
            break;

        // 处理"0x"前缀
        if (c == 'x' || c == 'X') continue;

        // 处理'0'~'9'
        if (c >= '0' && c <= '9')
        {
            value = (value << 4) + (c - '0');
            started = 1;
        }
        // 处理'a'~'f'
        else if (c >= 'a' && c <= 'f')
        {
            value = (value << 4) + (c - 'a' + 10);
            started = 1;
        }
        // 处理'A'~'F'
        else if (c >= 'A' && c <= 'F')
        {
            value = (value << 4) + (c - 'A' + 10);
            started = 1;
        }
        // 遇到其他字符提前结束（如果已开始读数字）
        else if (started)
        {
            break;
        }
        // 未开始遇到非数字，忽略，继续读取
    }
    return value;
}


/**
 * @brief 比较两个以'\0'结尾的字符串。
 *
 * 按字典序逐字节比较字符串 s1 和 s2。
 * 
 * @param[in] s1 指向第一个字符串的指针
 * @param[in] s2 指向第二个字符串的指针
 * 
 * @return 
 *   - <0: s1 小于 s2
 *   - =0: s1 等于 s2
 *   - >0: s1 大于 s2
 */
int strcmp(const char *s1, const char *s2) 
{
    while (*s1 && *s2 && *s1 == *s2) {
        s1++; s2++;
    }
    return *(const unsigned char *)s1 - *(const unsigned char *)s2;
}

/**
 * @brief 从UART串口接收寄存器读取一个字符（无回显）。
 *
 * 本函数从UART_RX_REG读取一个字节，但不会对读取到的字符进行回显。
 * 用于实现自定义输入回显和特殊控制字符处理的场景。
 * 
 * @return 读取到的字符
 */
unsigned char get_char_noecho(void)
{
    unsigned char ch = UART_RX_REG;
    return ch;
}

/**
 * @brief 向指定的内存地址写入32位数据
 * 
 * @param addr  目标内存地址
 * @param value 要写入的数据
 */
void write_mem(uint32_t addr, uint32_t value) {
    *((volatile uint32_t *)addr) = value;
}

/**
 * @brief 从指定的内存地址读取32位数据
 * 
 * @param addr  要读取数据的内存地址
 * @return uint32_t  读取到的数据
 */
uint32_t read_mem(uint32_t addr) {
    return *((volatile uint32_t *)addr);
}

/**
 * @brief 向指定的内存地址写入16位数据。
 * @param addr 目标内存地址。应当对齐到2字节。
 * @param value 要写入的数据。
 */
void write_mem16(uint32_t addr, uint16_t value) {
    *((volatile uint16_t *)addr) = value;
}

/**
 * @brief 从指定的内存地址读取16位数据。
 * @param addr 要读取数据的内存地址。应当对齐到2字节。
 * @return 读取到的数据。
 */
uint16_t read_mem16(uint32_t addr) {
    return *((volatile uint16_t *)addr);
}

/**
 * @brief 向指定的内存地址写入8位数据。
 * @param addr 目标内存地址。
 * @param value 要写入的数据。
 */
void write_mem8(uint32_t addr, uint8_t value) {
    *((volatile uint8_t *)addr) = value;
}

/**
 * @brief 从指定的内存地址读取8位数据。
 * @param addr 要读取数据的内存地址。
 * @return 读取到的数据。
 */
uint8_t read_mem8(uint32_t addr) {
    return *((volatile uint8_t *)addr);
}
