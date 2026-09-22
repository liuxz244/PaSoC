ENTRY(_start)
MEMORY {
  INSTR (rx) : ORIGIN = 0, LENGTH = 16K
  DATA (rw) : ORIGIN = 0x10000000, LENGTH = 8K
}
SECTIONS {
  .text : { KEEP(*(.init)) *(.text .text.*) } > INSTR
  .data : { *(.rodata .rodata.*) *(.srodata .srodata.*) *(.data .data.*) *(.sdata .sdata.*) } > DATA
  .bss (NOLOAD) : { . = ALIGN(4); _bss_start = .; *(.bss .bss.*) *(.sbss .sbss.*) *(COMMON) . = ALIGN(4); _bss_end = .; } > DATA
  _stack_top = ORIGIN(DATA) + LENGTH(DATA);
  ASSERT(_bss_end <= _stack_top - 2048, "CoreMark requires at least 2 KiB stack")
}
