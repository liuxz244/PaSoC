#include "PaSoC.h"
#include <stdint.h>


typedef struct {
    char main[16];    // 主命令, 如 "gpio"
    char subcmd[16];  // 子命令, 如 "in", "out"
    char param1[16];   // 参数, 如 "0x1234"
    char param2[16];
} Command;


/**
 * @brief 从控制台读取一行字符串，支持方向键上/下访问历史命令。
 *
 * 该函数实现了基础命令行编辑行为，包括退格、上键获取历史指令、下键清除历史。
 * 字符的输入以回车（'\r'或'\n'）结束。函数仅支持简单的行编辑，不支持光标左右移动。
 *
 * @param[in,out] buf      用于存放用户输入的字符缓冲区，返回输入字符串（以\0结尾）。
 * @param[in]     maxlen   缓冲区最大长度
 * @param[in]     last_cmd 上一条历史命令字符串, 用于用户按上方向键时回显
 * @return 实际输入的字符数（不含结尾的\0）
 */
int get_str_with_history(char *buf, int maxlen, const char *last_cmd)
{
    int i = 0;
    unsigned char c;
    int showing_history = 0; // 0-输入的是新内容；1-正显示last_cmd

    while (1) {
        c = get_char_noecho();
        // 处理方向键: ESC [
        if (c == 0x1b) { // ESC
            unsigned char c2 = get_char_noecho();
            if (c2 == '[') {
                unsigned char c3 = get_char_noecho();
                if (c3 == 'A') { // 上方向键
                    // 清除当前输入（退格擦除已输入内容）
                    while (i > 0) {
                        print_char('\b');
                        print_char(' ');
                        print_char('\b');
                        i--;
                    }
                    // 恢复历史命令
                    int j = 0;
                    while (last_cmd[j] && j < maxlen-1) {
                        buf[j] = last_cmd[j];
                        print_char(buf[j]);
                        j++;
                    }
                    i = j;
                    buf[i] = '\0';
                    showing_history = 1; // 设为正在显示历史
                    continue;
                }
                else if (c3 == 'B') { // 下方向键
                    // 只在显示历史时才响应
                    if (showing_history) {
                        // 清除历史命令（退格擦除已输入内容）
                        while (i > 0) {
                            print_char('\b');
                            print_char(' ');
                            print_char('\b');
                            i--;
                        }
                        buf[i] = '\0';
                        showing_history = 0; // 回到新输入状态
                    }
                    continue;
                }
                // 可支持左右方向键，略
            }
            continue; // 跳过其他ESC序列
        }
        if (c == '\r' || c == '\n') {
            break;
        }
        else if (c == 0x08 || c == 0x7F) { // 退格
            if (i > 0) {
                i--;
                print_char('\b');
                print_char(' ');
                print_char('\b');
            }
            if (showing_history) showing_history = 0; // 一旦有编辑就不是历史状态
        }
        else if (i < maxlen - 1 && c >= 0x20 && c <= 0x7E) {
            buf[i++] = c;
            print_char(c);
            if (showing_history) showing_history = 0;
        }
        // 其他特殊字符忽略
    }
    buf[i] = '\0';
    return i;
}

/**
 * @brief 解析用户输入的命令字符串，分割为主命令、子命令和参数。
 *
 * 输入格式一般为："main subcmd param"（各部分以空格或制表符分隔）。
 * 结果分别填充到cmd结构体中。
 *
 * @param[in]  input   输入的命令字符串（以\0结尾）
 * @param[out] cmd     解析后的命令结构体指针，成员被填写
 *
 * @retval 0   解析成功
 */
int parse_command(const char *input, Command *cmd)
{
    // 跳过前置空白
    while(*input == ' ' || *input == '\t') input++;
    int n = 0;

    // 解析主命令
    n = 0;
    while(*input && *input != ' ' && *input != '\t' && n < (int)sizeof(cmd->main)-1)
        cmd->main[n++] = *(input++);
    cmd->main[n] = 0;
    while(*input == ' ' || *input == '\t') input++;

    // 子命令
    n = 0;
    while(*input && *input != ' ' && *input != '\t' && n < (int)sizeof(cmd->subcmd)-1)
        cmd->subcmd[n++] = *(input++);
    cmd->subcmd[n] = 0;
    while(*input == ' ' || *input == '\t') input++;

    // 解析参数1
    n = 0;
    while(*input && *input != ' ' && *input != '\t' && n < (int)sizeof(cmd->param1)-1)
        cmd->param1[n++] = *(input++);
    cmd->param1[n] = 0;
    while(*input == ' ' || *input == '\t') input++;

    // 解析参数2
    n = 0;
    while(*input && *input != ' ' && *input != '\t' && n < (int)sizeof(cmd->param2)-1)
        cmd->param2[n++] = *(input++);
    cmd->param2[n] = 0;
    // 如果还有更多参数，可以继续扩展

    return 0; // ok
}

/**
 * @brief 解析十六进制字符串为32位无符号数
 *
 * 支持前缀"0x"或"0X"，跳过无效字符，遇到第一个非法字符停止。
 *
 * @param[in]  str      指向字符串的指针
 * @param[out] value    输出结果指针
 * @retval  0   成功，*value被写入
 * @retval -1  失败（无合法数字）
 */
int parse_hex(const char *str, uint32_t *value)
{
    if (!str || !value) return -1;
    *value = 0;
    // 跳过0x/0X
    if (str[0] == '0' && (str[1]=='x' || str[1]=='X')) str += 2;
    int started = 0;

    while (*str) {
        char c = *str++;
        if (c >= '0' && c <= '9') { *value = (*value << 4) + (c - '0'); started = 1; }
        else if (c >= 'a' && c <= 'f') { *value = (*value << 4) + (c - 'a' + 10); started = 1; }
        else if (c >= 'A' && c <= 'F') { *value = (*value << 4) + (c - 'A' + 10); started = 1; }
        else break;
    }
    return started ? 0 : -1;
}

/**
 * @brief 处理GPIO相关子命令，支持"in"读取和"out"设置GPIO值。
 *
 * 当subcmd为"in"时，读取GPIO输入寄存器打印值。
 * 当subcmd为"out"时，解析参数为十六进制数写入GPIO输出寄存器。
 *
 * @param[in] cmd  已解析的Command结构体（主命令已确定为"gpio"）
 */
void handle_gpio_command(const Command *cmd)
{
    if (strcmp(cmd->subcmd, "in") == 0) {
        uint32_t value = gpio_read_in();
        print_str("\r\nGPIO IN value: 0x");
        print_hex(value, 8);
        print_str("\r\n");
    }
    else if (strcmp(cmd->subcmd, "out") == 0) {
        uint32_t value;
        if (!cmd->param1[0]) {
            print_str("\r\nError: argument missing. Usage: gpio out <hex_value>\r\n");
            return;
        }
        if (parse_hex(cmd->param1, &value) != 0) {
            print_str("\r\nError: invalid hex argument. Usage: gpio out <hex_value>\r\n");
            return;
        }

        gpio_write_out(value);
        print_str("\r\nSet GPIO OUT to 0x"); print_hex(value, 8);
        print_str("\r\nGPIO output updated!\r\n");
    }
    else {
        print_str("\r\nError: unknown subcmd. Usage: gpio <in|out> <hex_value>\r\n");
    }
}

/**
 * @brief 处理内存读写命令，支持8/16/32位读写
 * 
 * 请确保在8/16位读写时地址是对齐的，否则可能会读写出错
 * 
 * 格式: mem <read|write[8|16|32]> <addr> [hex_value]
 *   - mem read[8|16|32] <addr>
 *   - mem write[8|16|32] <addr> <hex_value>
 *
 * @param[in] cmd  已解析的Command结构体（主命令已为"mem"）
 * @note
 *   - 缺省的 read/write 等同于 read32/write32。
 */
void handle_mem_command(const Command *cmd)
{
    // --- Read Commands ---
    // read/read32
    if (strcmp(cmd->subcmd, "read") == 0 || strcmp(cmd->subcmd, "read32") == 0) {
        uint32_t addr;
        if (parse_hex(cmd->param1, &addr) != 0) {
            print_str("\r\nError: invalid address. Usage: mem read[32] <addr>\r\n");
            return;
        }
        uint32_t value = read_mem(addr);
        print_str("\r\nMEM32[0x"); print_hex(addr, 8);
        print_str("] = 0x"); print_hex(value, 8); print_str("\r\n");
        return;
    } 
    // read16
    if (strcmp(cmd->subcmd, "read16") == 0) {
        uint32_t addr;
        if (parse_hex(cmd->param1, &addr) != 0) {
            print_str("\r\nError: invalid address. Usage: mem read16 <addr>\r\n");
            return;
        }
        uint16_t value = read_mem16(addr);
        print_str("\r\nMEM16[0x"); print_hex(addr, 8);
        print_str("] = 0x"); print_hex(value, 4); print_str("\r\n");
        return;
    }
    // read8
    if (strcmp(cmd->subcmd, "read8") == 0) {
        uint32_t addr;
        if (parse_hex(cmd->param1, &addr) != 0) {
            print_str("\r\nError: invalid address. Usage: mem read8 <addr>\r\n");
            return;
        }
        uint8_t value = read_mem8(addr);
        print_str("\r\nMEM8[0x"); print_hex(addr, 8);
        print_str("] = 0x"); print_hex(value, 2); print_str("\r\n");
        return;
    }

    // --- Write Commands ---
    // write/write32
    if (strcmp(cmd->subcmd, "write") == 0 || strcmp(cmd->subcmd, "write32") == 0) {
        uint32_t addr, value;
        if (cmd->param1[0]==0 || cmd->param2[0]==0) {
            print_str("\r\nError: incomplete args. Usage: mem write[32] <addr> <hex_value>\r\n");
            return;
        }
        if (parse_hex(cmd->param1, &addr) != 0 || parse_hex(cmd->param2, &value) != 0) {
            print_str("\r\nError: invalid address or value. Usage: mem write[32] <addr> <hex_value>\r\n");
            return;
        }
        write_mem(addr, value);
        print_str("\r\nMEM32[0x"); print_hex(addr, 8);
        print_str("] <= 0x"); print_hex(value, 8);
        print_str("\r\nMemory write32 done!\r\n");
        return;
    }
    // write16
    if (strcmp(cmd->subcmd, "write16") == 0) {
        uint32_t addr, tmp;
        uint16_t value;
        if (cmd->param1[0] == 0 || cmd->param2[0] == 0) {
            print_str("\r\nError: incomplete args. Usage: mem write16 <addr> <hex_value>\r\n");
            return;
        }
        if (parse_hex(cmd->param1, &addr) != 0 || parse_hex(cmd->param2, &tmp) != 0) {
            print_str("\r\nError: invalid address or value. Usage: mem write16 <addr> <hex_value>\r\n");
            return;
        }
        value = (uint16_t)tmp;
        write_mem16(addr, value);
        print_str("\r\nMEM16[0x"); print_hex(addr, 8);
        print_str("] <= 0x"); print_hex(value, 4);
        print_str("\r\nMemory write16 done!\r\n");
        return;
    }

    // write8
    if (strcmp(cmd->subcmd, "write8") == 0) {
        uint32_t addr, tmp;
        uint8_t value;
        if (cmd->param1[0] == 0 || cmd->param2[0] == 0) {
            print_str("\r\nError: incomplete args. Usage: mem write8 <addr> <hex_value>\r\n");
            return;
        }
        if (parse_hex(cmd->param1, &addr) != 0 || parse_hex(cmd->param2, &tmp) != 0) {
            print_str("\r\nError: invalid address or value. Usage: mem write8 <addr> <hex_value>\r\n");
            return;
        }
        value = (uint8_t)tmp;
        write_mem8(addr, value);
        print_str("\r\nMEM8[0x"); print_hex(addr, 8);
        print_str("] <= 0x"); print_hex(value, 2);
        print_str("\r\nMemory write8 done!\r\n");
        return;
    }

    // --- Unknown subcmd ---
    print_str("\r\nError: unknown subcmd. Usage:\r\n"
              "    mem <read|write[8|16|32]> <addr> [hex_value]\r\n");
}


/**
 * @brief 根据主命令调用具体处理函数。
 *
 * 当前支持 "help"、"gpio"、"mem" 命令，根据cmd->main选择对应处理流程。
 * 未知命令时输出帮助提示。
 *
 * @param[in] cmd  已解析的Command结构体指针
 */
void dispatch_command(const Command *cmd)
{
    if(strcmp(cmd->main, "gpio")==0) {
        handle_gpio_command(cmd);
    }
    else if(strcmp(cmd->main, "mem")==0) {
        handle_mem_command(cmd);
    }
    else if(strcmp(cmd->main, "help")==0) {
        print_str("\r\nSupported Command:\r\n"
                  "    gpio <in|out> [hex_value]\r\n"
                  "    mem <read|write[8|16|32]> <addr> [hex_value]\r\n");
    }
    else {
        print_str("\r\nUnknown command. Type 'help'\r\n");
    }
}

/**
 * @brief 命令处理主循环函数。等待用户输入命令并处理。
 *
 * 该函数打印命令行提示符，读取并处理用户输入，支持一条历史命令的上/下键切换。
 * 用户输入经过解析和派发处理，实现简单的命令行交互功能。
 */
void command_handler(void)
{
    static char last_cmd[32] = "";
    char buf[32];
    print_str("> ");
    get_str_with_history(buf, sizeof(buf), last_cmd);

    // 记录上一条命令
    int not_empty = 0;
    for(int ii=0; buf[ii]!='\0'; ++ii) {
        if(buf[ii] != ' ' && buf[ii]!='\t') { not_empty = 1; break; }
    }
    if(not_empty) {
        for (int j = 0; j < sizeof(last_cmd); ++j) {
            last_cmd[j] = buf[j];
            if (buf[j] == '\0') break;
        }
    }

    Command cmd;
    parse_command(buf, &cmd);
    dispatch_command(&cmd);
}


// 上电运行的main函数
int main()
{
    print_str("Welcome to PaSoC BIOS console!\r\n");

    while (1) {
        command_handler();
    }

    return 0;
}
