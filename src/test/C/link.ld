ENTRY(_start)

MEMORY
{
    INSTR (rx) : ORIGIN = 0x00000000, LENGTH = 8K  /* 指令空间 */
    DATA  (rw) : ORIGIN = 0x10000000, LENGTH = 8K  /* 数据空间 */
}

SECTIONS
{
    /* 指令区 */
    . = ORIGIN(INSTR);
    .text : {
        KEEP(*(.init))    /* 强制保留启动入口，防止gc丢失 */
        *(.text .text.*)  /* 所有其他代码段 */
    } > INSTR

     /* ---- 数据部分 ---- */
    . = ORIGIN(DATA);
    /* 只读数据段 */
    .rodata : {
        *(.rodata .rodata.*)
    } > DATA

    /* 已初始化读写数据段 */
    .data : {
        *(.data .data.*)
    } > DATA

    /* 未初始化变量段 */
    .bss : {
        *(.bss .bss.*)
        *(COMMON)
    } > DATA

    /* 栈设置 */
    _end = .;  /* 堆起始 */
    STACK_SIZE = 4K;  /* 可以自己调节需要多少栈 */
    . = ORIGIN(DATA) + LENGTH(DATA) - STACK_SIZE;
    _stack_limit = .;
    . = ORIGIN(DATA) + LENGTH(DATA);
    PROVIDE(_stack_top = .);
    PROVIDE(_sp = .);
}
