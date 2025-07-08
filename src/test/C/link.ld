ENTRY(_start)

MEMORY
{
    INSTR (rx) : ORIGIN = 0x00000000, LENGTH = 16K  /* 指令空间 */
    DATA  (rw) : ORIGIN = 0x10000000, LENGTH = 8K   /* 数据空间 */
    DRAM  (rw) : ORIGIN = 0x40000000, LENGTH = 8M   /* 主内存区 */
}

SECTIONS
{
    /* 指令区 */
    . = ORIGIN(INSTR);
    .text : {
        KEEP(*(.init))    /* 强制保留启动入口，防止gc丢失 */
        *(.text .text.*)  /* 所有其他代码段 */
    } > INSTR

    /* ---- 数据BRAM部分 ---- */
    . = ORIGIN(DATA);
    /* 只读数据段 */
    .rodata : {
        *(.rodata .rodata.*)
    } > DATA

    /* 已初始化读写数据段 */
    .data : {
        *(.data .data.*)
    } > DATA

    /* ---- 数据DRAM部分 ---- */
    . = ORIGIN(DRAM);
    .bss (NOLOAD) : {
        *(.bss .bss.*)
        *(COMMON)
    } > DRAM

    _end = .;  /* 堆起始 */
    
    /* 堆栈使用 DTCM */
    /*
    STACK_SIZE = 4K;
    . = ORIGIN(DATA) + LENGTH(DATA) - STACK_SIZE;
    _stack_limit = .;
    . = ORIGIN(DATA) + LENGTH(DATA);
    */
    /* 堆栈使用 DRAM */
    
    STACK_SIZE = 16K;
    STACK_TOP  = 32K;
    . = ORIGIN(DRAM) + STACK_TOP - STACK_SIZE;
    _stack_limit = .;
    . = ORIGIN(DRAM) + STACK_TOP;
    
    PROVIDE(_stack_top = .);
    PROVIDE(_sp = .);
}
