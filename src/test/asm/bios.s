	.file	"bios.c"
	.option nopic
	.attribute arch, "rv32i2p1_m2p0_zicsr2p0_zmmul1p0"
	.attribute unaligned_access, 0
	.attribute stack_align, 16
	.text
	.align	2
	.globl	get_str_with_history
	.type	get_str_with_history, @function
get_str_with_history:
	addi	sp,sp,-48
	sw	ra,44(sp)
	sw	s0,40(sp)
	addi	s0,sp,48
	sw	a0,-36(s0)
	sw	a1,-40(s0)
	sw	a2,-44(s0)
	sw	zero,-20(s0)
	sw	zero,-24(s0)
.L19:
	call	get_char_noecho
	mv	a5,a0
	sb	a5,-29(s0)
	lbu	a4,-29(s0)
	li	a5,27
	bne	a4,a5,.L2
	call	get_char_noecho
	mv	a5,a0
	sb	a5,-30(s0)
	lbu	a4,-30(s0)
	li	a5,91
	bne	a4,a5,.L21
	call	get_char_noecho
	mv	a5,a0
	sb	a5,-31(s0)
	lbu	a4,-31(s0)
	li	a5,65
	bne	a4,a5,.L4
	j	.L5
.L6:
	li	a0,8
	call	print_char
	li	a0,32
	call	print_char
	li	a0,8
	call	print_char
	lw	a5,-20(s0)
	addi	a5,a5,-1
	sw	a5,-20(s0)
.L5:
	lw	a5,-20(s0)
	bgt	a5,zero,.L6
	sw	zero,-28(s0)
	j	.L7
.L9:
	lw	a5,-28(s0)
	lw	a4,-44(s0)
	add	a4,a4,a5
	lw	a5,-28(s0)
	lw	a3,-36(s0)
	add	a5,a3,a5
	lbu	a4,0(a4)
	sb	a4,0(a5)
	lw	a5,-28(s0)
	lw	a4,-36(s0)
	add	a5,a4,a5
	lbu	a5,0(a5)
	mv	a0,a5
	call	print_char
	lw	a5,-28(s0)
	addi	a5,a5,1
	sw	a5,-28(s0)
.L7:
	lw	a5,-28(s0)
	lw	a4,-44(s0)
	add	a5,a4,a5
	lbu	a5,0(a5)
	beq	a5,zero,.L8
	lw	a5,-40(s0)
	addi	a5,a5,-1
	lw	a4,-28(s0)
	blt	a4,a5,.L9
.L8:
	lw	a5,-28(s0)
	sw	a5,-20(s0)
	lw	a5,-20(s0)
	lw	a4,-36(s0)
	add	a5,a4,a5
	sb	zero,0(a5)
	li	a5,1
	sw	a5,-24(s0)
	j	.L10
.L4:
	lbu	a4,-31(s0)
	li	a5,66
	bne	a4,a5,.L21
	lw	a5,-24(s0)
	beq	a5,zero,.L22
	j	.L12
.L13:
	li	a0,8
	call	print_char
	li	a0,32
	call	print_char
	li	a0,8
	call	print_char
	lw	a5,-20(s0)
	addi	a5,a5,-1
	sw	a5,-20(s0)
.L12:
	lw	a5,-20(s0)
	bgt	a5,zero,.L13
	lw	a5,-20(s0)
	lw	a4,-36(s0)
	add	a5,a4,a5
	sb	zero,0(a5)
	sw	zero,-24(s0)
	j	.L22
.L2:
	lbu	a4,-29(s0)
	li	a5,13
	beq	a4,a5,.L14
	lbu	a4,-29(s0)
	li	a5,10
	beq	a4,a5,.L14
	lbu	a4,-29(s0)
	li	a5,8
	beq	a4,a5,.L15
	lbu	a4,-29(s0)
	li	a5,127
	bne	a4,a5,.L16
.L15:
	lw	a5,-20(s0)
	ble	a5,zero,.L17
	lw	a5,-20(s0)
	addi	a5,a5,-1
	sw	a5,-20(s0)
	li	a0,8
	call	print_char
	li	a0,32
	call	print_char
	li	a0,8
	call	print_char
.L17:
	lw	a5,-24(s0)
	beq	a5,zero,.L10
	sw	zero,-24(s0)
	j	.L10
.L16:
	lw	a5,-40(s0)
	addi	a5,a5,-1
	lw	a4,-20(s0)
	bge	a4,a5,.L19
	lbu	a4,-29(s0)
	li	a5,31
	bleu	a4,a5,.L19
	lbu	a4,-29(s0)
	li	a5,126
	bgtu	a4,a5,.L19
	lw	a5,-20(s0)
	addi	a4,a5,1
	sw	a4,-20(s0)
	mv	a4,a5
	lw	a5,-36(s0)
	add	a5,a5,a4
	lbu	a4,-29(s0)
	sb	a4,0(a5)
	lbu	a5,-29(s0)
	mv	a0,a5
	call	print_char
	lw	a5,-24(s0)
	beq	a5,zero,.L19
	sw	zero,-24(s0)
	j	.L19
.L21:
	nop
	j	.L19
.L22:
	nop
.L10:
	j	.L19
.L14:
	lw	a5,-20(s0)
	lw	a4,-36(s0)
	add	a5,a4,a5
	sb	zero,0(a5)
	lw	a5,-20(s0)
	mv	a0,a5
	lw	ra,44(sp)
	lw	s0,40(sp)
	addi	sp,sp,48
	jr	ra
	.size	get_str_with_history, .-get_str_with_history
	.align	2
	.globl	parse_command
	.type	parse_command, @function
parse_command:
	addi	sp,sp,-48
	sw	ra,44(sp)
	sw	s0,40(sp)
	addi	s0,sp,48
	sw	a0,-36(s0)
	sw	a1,-40(s0)
	j	.L24
.L25:
	lw	a5,-36(s0)
	addi	a5,a5,1
	sw	a5,-36(s0)
.L24:
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,32
	beq	a4,a5,.L25
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,9
	beq	a4,a5,.L25
	sw	zero,-20(s0)
	sw	zero,-20(s0)
	j	.L26
.L28:
	lw	a4,-36(s0)
	addi	a5,a4,1
	sw	a5,-36(s0)
	lw	a5,-20(s0)
	addi	a3,a5,1
	sw	a3,-20(s0)
	lbu	a4,0(a4)
	lw	a3,-40(s0)
	add	a5,a3,a5
	sb	a4,0(a5)
.L26:
	lw	a5,-36(s0)
	lbu	a5,0(a5)
	beq	a5,zero,.L27
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,32
	beq	a4,a5,.L27
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,9
	beq	a4,a5,.L27
	lw	a4,-20(s0)
	li	a5,14
	ble	a4,a5,.L28
.L27:
	lw	a4,-40(s0)
	lw	a5,-20(s0)
	add	a5,a4,a5
	sb	zero,0(a5)
	j	.L29
.L30:
	lw	a5,-36(s0)
	addi	a5,a5,1
	sw	a5,-36(s0)
.L29:
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,32
	beq	a4,a5,.L30
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,9
	beq	a4,a5,.L30
	sw	zero,-20(s0)
	j	.L31
.L33:
	lw	a4,-36(s0)
	addi	a5,a4,1
	sw	a5,-36(s0)
	lw	a5,-20(s0)
	addi	a3,a5,1
	sw	a3,-20(s0)
	lbu	a4,0(a4)
	lw	a3,-40(s0)
	add	a5,a3,a5
	sb	a4,16(a5)
.L31:
	lw	a5,-36(s0)
	lbu	a5,0(a5)
	beq	a5,zero,.L32
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,32
	beq	a4,a5,.L32
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,9
	beq	a4,a5,.L32
	lw	a4,-20(s0)
	li	a5,14
	ble	a4,a5,.L33
.L32:
	lw	a4,-40(s0)
	lw	a5,-20(s0)
	add	a5,a4,a5
	sb	zero,16(a5)
	j	.L34
.L35:
	lw	a5,-36(s0)
	addi	a5,a5,1
	sw	a5,-36(s0)
.L34:
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,32
	beq	a4,a5,.L35
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,9
	beq	a4,a5,.L35
	sw	zero,-20(s0)
	j	.L36
.L38:
	lw	a4,-36(s0)
	addi	a5,a4,1
	sw	a5,-36(s0)
	lw	a5,-20(s0)
	addi	a3,a5,1
	sw	a3,-20(s0)
	lbu	a4,0(a4)
	lw	a3,-40(s0)
	add	a5,a3,a5
	sb	a4,32(a5)
.L36:
	lw	a5,-36(s0)
	lbu	a5,0(a5)
	beq	a5,zero,.L37
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,32
	beq	a4,a5,.L37
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,9
	beq	a4,a5,.L37
	lw	a4,-20(s0)
	li	a5,14
	ble	a4,a5,.L38
.L37:
	lw	a4,-40(s0)
	lw	a5,-20(s0)
	add	a5,a4,a5
	sb	zero,32(a5)
	j	.L39
.L40:
	lw	a5,-36(s0)
	addi	a5,a5,1
	sw	a5,-36(s0)
.L39:
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,32
	beq	a4,a5,.L40
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,9
	beq	a4,a5,.L40
	sw	zero,-20(s0)
	j	.L41
.L43:
	lw	a4,-36(s0)
	addi	a5,a4,1
	sw	a5,-36(s0)
	lw	a5,-20(s0)
	addi	a3,a5,1
	sw	a3,-20(s0)
	lbu	a4,0(a4)
	lw	a3,-40(s0)
	add	a5,a3,a5
	sb	a4,48(a5)
.L41:
	lw	a5,-36(s0)
	lbu	a5,0(a5)
	beq	a5,zero,.L42
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,32
	beq	a4,a5,.L42
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,9
	beq	a4,a5,.L42
	lw	a4,-20(s0)
	li	a5,14
	ble	a4,a5,.L43
.L42:
	lw	a4,-40(s0)
	lw	a5,-20(s0)
	add	a5,a4,a5
	sb	zero,48(a5)
	li	a5,0
	mv	a0,a5
	lw	ra,44(sp)
	lw	s0,40(sp)
	addi	sp,sp,48
	jr	ra
	.size	parse_command, .-parse_command
	.align	2
	.globl	parse_hex
	.type	parse_hex, @function
parse_hex:
	addi	sp,sp,-48
	sw	ra,44(sp)
	sw	s0,40(sp)
	addi	s0,sp,48
	sw	a0,-36(s0)
	sw	a1,-40(s0)
	lw	a5,-36(s0)
	beq	a5,zero,.L46
	lw	a5,-40(s0)
	bne	a5,zero,.L47
.L46:
	li	a5,-1
	j	.L48
.L47:
	lw	a5,-40(s0)
	sw	zero,0(a5)
	lw	a5,-36(s0)
	lbu	a4,0(a5)
	li	a5,48
	bne	a4,a5,.L49
	lw	a5,-36(s0)
	addi	a5,a5,1
	lbu	a4,0(a5)
	li	a5,120
	beq	a4,a5,.L50
	lw	a5,-36(s0)
	addi	a5,a5,1
	lbu	a4,0(a5)
	li	a5,88
	bne	a4,a5,.L49
.L50:
	lw	a5,-36(s0)
	addi	a5,a5,2
	sw	a5,-36(s0)
.L49:
	sw	zero,-20(s0)
	j	.L51
.L55:
	lw	a5,-36(s0)
	addi	a4,a5,1
	sw	a4,-36(s0)
	lbu	a5,0(a5)
	sb	a5,-21(s0)
	lbu	a4,-21(s0)
	li	a5,47
	bleu	a4,a5,.L52
	lbu	a4,-21(s0)
	li	a5,57
	bgtu	a4,a5,.L52
	lw	a5,-40(s0)
	lw	a5,0(a5)
	slli	a4,a5,4
	lbu	a5,-21(s0)
	add	a5,a4,a5
	addi	a4,a5,-48
	lw	a5,-40(s0)
	sw	a4,0(a5)
	li	a5,1
	sw	a5,-20(s0)
	j	.L51
.L52:
	lbu	a4,-21(s0)
	li	a5,96
	bleu	a4,a5,.L53
	lbu	a4,-21(s0)
	li	a5,102
	bgtu	a4,a5,.L53
	lw	a5,-40(s0)
	lw	a5,0(a5)
	slli	a4,a5,4
	lbu	a5,-21(s0)
	add	a5,a4,a5
	addi	a4,a5,-87
	lw	a5,-40(s0)
	sw	a4,0(a5)
	li	a5,1
	sw	a5,-20(s0)
	j	.L51
.L53:
	lbu	a4,-21(s0)
	li	a5,64
	bleu	a4,a5,.L54
	lbu	a4,-21(s0)
	li	a5,70
	bgtu	a4,a5,.L54
	lw	a5,-40(s0)
	lw	a5,0(a5)
	slli	a4,a5,4
	lbu	a5,-21(s0)
	add	a5,a4,a5
	addi	a4,a5,-55
	lw	a5,-40(s0)
	sw	a4,0(a5)
	li	a5,1
	sw	a5,-20(s0)
.L51:
	lw	a5,-36(s0)
	lbu	a5,0(a5)
	bne	a5,zero,.L55
.L54:
	lw	a5,-20(s0)
	beq	a5,zero,.L56
	li	a5,0
	j	.L48
.L56:
	li	a5,-1
.L48:
	mv	a0,a5
	lw	ra,44(sp)
	lw	s0,40(sp)
	addi	sp,sp,48
	jr	ra
	.size	parse_hex, .-parse_hex
	.section	.rodata
	.align	2
.LC0:
	.string	"in"
	.align	2
.LC1:
	.string	"\r\nGPIO IN value: 0x"
	.align	2
.LC2:
	.string	"\r\n"
	.align	2
.LC3:
	.string	"out"
	.align	2
.LC4:
	.string	"\r\nError: argument missing. Usage: gpio out <hex_value>\r\n"
	.align	2
.LC5:
	.string	"\r\nError: invalid hex argument. Usage: gpio out <hex_value>\r\n"
	.align	2
.LC6:
	.string	"\r\nSet GPIO OUT to 0x"
	.align	2
.LC7:
	.string	"\r\nGPIO output updated!\r\n"
	.align	2
.LC8:
	.string	"\r\nError: unknown subcmd. Usage: gpio <in|out> <hex_value>\r\n"
	.text
	.align	2
	.globl	handle_gpio_command
	.type	handle_gpio_command, @function
handle_gpio_command:
	addi	sp,sp,-48
	sw	ra,44(sp)
	sw	s0,40(sp)
	addi	s0,sp,48
	sw	a0,-36(s0)
	lw	a5,-36(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC0)
	addi	a1,a5,%lo(.LC0)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L59
	call	gpio_read_in
	sw	a0,-20(s0)
	lui	a5,%hi(.LC1)
	addi	a0,a5,%lo(.LC1)
	call	print_str
	li	a1,8
	lw	a0,-20(s0)
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	j	.L58
.L59:
	lw	a5,-36(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC3)
	addi	a1,a5,%lo(.LC3)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L61
	lw	a5,-36(s0)
	lbu	a5,32(a5)
	bne	a5,zero,.L62
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	j	.L58
.L62:
	lw	a5,-36(s0)
	addi	a5,a5,32
	addi	a4,s0,-24
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L64
	lui	a5,%hi(.LC5)
	addi	a0,a5,%lo(.LC5)
	call	print_str
	j	.L58
.L64:
	lw	a5,-24(s0)
	mv	a0,a5
	call	gpio_write_out
	lui	a5,%hi(.LC6)
	addi	a0,a5,%lo(.LC6)
	call	print_str
	lw	a5,-24(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC7)
	addi	a0,a5,%lo(.LC7)
	call	print_str
	j	.L58
.L61:
	lui	a5,%hi(.LC8)
	addi	a0,a5,%lo(.LC8)
	call	print_str
.L58:
	lw	ra,44(sp)
	lw	s0,40(sp)
	addi	sp,sp,48
	jr	ra
	.size	handle_gpio_command, .-handle_gpio_command
	.section	.rodata
	.align	2
.LC9:
	.string	"read"
	.align	2
.LC10:
	.string	"read32"
	.align	2
.LC11:
	.string	"\r\nError: invalid address. Usage: mem read[32] <addr>\r\n"
	.align	2
.LC12:
	.string	"\r\nMEM32[0x"
	.align	2
.LC13:
	.string	"] = 0x"
	.align	2
.LC14:
	.string	"read16"
	.align	2
.LC15:
	.string	"\r\nError: invalid address. Usage: mem read16 <addr>\r\n"
	.align	2
.LC16:
	.string	"\r\nMEM16[0x"
	.align	2
.LC17:
	.string	"read8"
	.align	2
.LC18:
	.string	"\r\nError: invalid address. Usage: mem read8 <addr>\r\n"
	.align	2
.LC19:
	.string	"\r\nMEM8[0x"
	.align	2
.LC20:
	.string	"write"
	.align	2
.LC21:
	.string	"write32"
	.align	2
.LC22:
	.string	"\r\nError: incomplete args. Usage: mem write[32] <addr> <hex_value>\r\n"
	.align	2
.LC23:
	.string	"\r\nError: invalid address or value. Usage: mem write[32] <addr> <hex_value>\r\n"
	.align	2
.LC24:
	.string	"] <= 0x"
	.align	2
.LC25:
	.string	"\r\nMemory write32 done!\r\n"
	.align	2
.LC26:
	.string	"write16"
	.align	2
.LC27:
	.string	"\r\nError: incomplete args. Usage: mem write16 <addr> <hex_value>\r\n"
	.align	2
.LC28:
	.string	"\r\nError: invalid address or value. Usage: mem write16 <addr> <hex_value>\r\n"
	.align	2
.LC29:
	.string	"\r\nMemory write16 done!\r\n"
	.align	2
.LC30:
	.string	"write8"
	.align	2
.LC31:
	.string	"\r\nError: incomplete args. Usage: mem write8 <addr> <hex_value>\r\n"
	.align	2
.LC32:
	.string	"\r\nError: invalid address or value. Usage: mem write8 <addr> <hex_value>\r\n"
	.align	2
.LC33:
	.string	"\r\nMemory write8 done!\r\n"
	.align	2
.LC34:
	.string	"\r\nError: unknown subcmd. Usage:\r\n    mem <read|write[8|16|32]> <addr> [hex_value]\r\n"
	.text
	.align	2
	.globl	handle_mem_command
	.type	handle_mem_command, @function
handle_mem_command:
	addi	sp,sp,-80
	sw	ra,76(sp)
	sw	s0,72(sp)
	addi	s0,sp,80
	sw	a0,-68(s0)
	lw	a5,-68(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC9)
	addi	a1,a5,%lo(.LC9)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	beq	a5,zero,.L66
	lw	a5,-68(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC10)
	addi	a1,a5,%lo(.LC10)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L67
.L66:
	lw	a5,-68(s0)
	addi	a5,a5,32
	addi	a4,s0,-32
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L68
	lui	a5,%hi(.LC11)
	addi	a0,a5,%lo(.LC11)
	call	print_str
	j	.L65
.L68:
	lw	a5,-32(s0)
	mv	a0,a5
	call	read_mem
	sw	a0,-28(s0)
	lui	a5,%hi(.LC12)
	addi	a0,a5,%lo(.LC12)
	call	print_str
	lw	a5,-32(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC13)
	addi	a0,a5,%lo(.LC13)
	call	print_str
	li	a1,8
	lw	a0,-28(s0)
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	j	.L65
.L67:
	lw	a5,-68(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC14)
	addi	a1,a5,%lo(.LC14)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L71
	lw	a5,-68(s0)
	addi	a5,a5,32
	addi	a4,s0,-36
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L72
	lui	a5,%hi(.LC15)
	addi	a0,a5,%lo(.LC15)
	call	print_str
	j	.L65
.L72:
	lw	a5,-36(s0)
	mv	a0,a5
	call	read_mem16
	mv	a5,a0
	sh	a5,-24(s0)
	lui	a5,%hi(.LC16)
	addi	a0,a5,%lo(.LC16)
	call	print_str
	lw	a5,-36(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC13)
	addi	a0,a5,%lo(.LC13)
	call	print_str
	lhu	a5,-24(s0)
	li	a1,4
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	j	.L65
.L71:
	lw	a5,-68(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC17)
	addi	a1,a5,%lo(.LC17)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L74
	lw	a5,-68(s0)
	addi	a5,a5,32
	addi	a4,s0,-40
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L75
	lui	a5,%hi(.LC18)
	addi	a0,a5,%lo(.LC18)
	call	print_str
	j	.L65
.L75:
	lw	a5,-40(s0)
	mv	a0,a5
	call	read_mem8
	mv	a5,a0
	sb	a5,-21(s0)
	lui	a5,%hi(.LC19)
	addi	a0,a5,%lo(.LC19)
	call	print_str
	lw	a5,-40(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC13)
	addi	a0,a5,%lo(.LC13)
	call	print_str
	lbu	a5,-21(s0)
	li	a1,2
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	j	.L65
.L74:
	lw	a5,-68(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC20)
	addi	a1,a5,%lo(.LC20)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	beq	a5,zero,.L77
	lw	a5,-68(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC21)
	addi	a1,a5,%lo(.LC21)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L78
.L77:
	lw	a5,-68(s0)
	lbu	a5,32(a5)
	beq	a5,zero,.L79
	lw	a5,-68(s0)
	lbu	a5,48(a5)
	bne	a5,zero,.L80
.L79:
	lui	a5,%hi(.LC22)
	addi	a0,a5,%lo(.LC22)
	call	print_str
	j	.L65
.L80:
	lw	a5,-68(s0)
	addi	a5,a5,32
	addi	a4,s0,-44
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	bne	a5,zero,.L82
	lw	a5,-68(s0)
	addi	a5,a5,48
	addi	a4,s0,-48
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L83
.L82:
	lui	a5,%hi(.LC23)
	addi	a0,a5,%lo(.LC23)
	call	print_str
	j	.L65
.L83:
	lw	a5,-44(s0)
	lw	a4,-48(s0)
	mv	a1,a4
	mv	a0,a5
	call	write_mem
	lui	a5,%hi(.LC12)
	addi	a0,a5,%lo(.LC12)
	call	print_str
	lw	a5,-44(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC24)
	addi	a0,a5,%lo(.LC24)
	call	print_str
	lw	a5,-48(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC25)
	addi	a0,a5,%lo(.LC25)
	call	print_str
	j	.L65
.L78:
	lw	a5,-68(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC26)
	addi	a1,a5,%lo(.LC26)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L84
	lw	a5,-68(s0)
	lbu	a5,32(a5)
	beq	a5,zero,.L85
	lw	a5,-68(s0)
	lbu	a5,48(a5)
	bne	a5,zero,.L86
.L85:
	lui	a5,%hi(.LC27)
	addi	a0,a5,%lo(.LC27)
	call	print_str
	j	.L65
.L86:
	lw	a5,-68(s0)
	addi	a5,a5,32
	addi	a4,s0,-52
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	bne	a5,zero,.L88
	lw	a5,-68(s0)
	addi	a5,a5,48
	addi	a4,s0,-56
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L89
.L88:
	lui	a5,%hi(.LC28)
	addi	a0,a5,%lo(.LC28)
	call	print_str
	j	.L65
.L89:
	lw	a5,-56(s0)
	sh	a5,-20(s0)
	lw	a5,-52(s0)
	lhu	a4,-20(s0)
	mv	a1,a4
	mv	a0,a5
	call	write_mem16
	lui	a5,%hi(.LC16)
	addi	a0,a5,%lo(.LC16)
	call	print_str
	lw	a5,-52(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC24)
	addi	a0,a5,%lo(.LC24)
	call	print_str
	lhu	a5,-20(s0)
	li	a1,4
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC29)
	addi	a0,a5,%lo(.LC29)
	call	print_str
	j	.L65
.L84:
	lw	a5,-68(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC30)
	addi	a1,a5,%lo(.LC30)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L90
	lw	a5,-68(s0)
	lbu	a5,32(a5)
	beq	a5,zero,.L91
	lw	a5,-68(s0)
	lbu	a5,48(a5)
	bne	a5,zero,.L92
.L91:
	lui	a5,%hi(.LC31)
	addi	a0,a5,%lo(.LC31)
	call	print_str
	j	.L65
.L92:
	lw	a5,-68(s0)
	addi	a5,a5,32
	addi	a4,s0,-60
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	bne	a5,zero,.L94
	lw	a5,-68(s0)
	addi	a5,a5,48
	addi	a4,s0,-64
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L95
.L94:
	lui	a5,%hi(.LC32)
	addi	a0,a5,%lo(.LC32)
	call	print_str
	j	.L65
.L95:
	lw	a5,-64(s0)
	sb	a5,-17(s0)
	lw	a5,-60(s0)
	lbu	a4,-17(s0)
	mv	a1,a4
	mv	a0,a5
	call	write_mem8
	lui	a5,%hi(.LC19)
	addi	a0,a5,%lo(.LC19)
	call	print_str
	lw	a5,-60(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC24)
	addi	a0,a5,%lo(.LC24)
	call	print_str
	lbu	a5,-17(s0)
	li	a1,2
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC33)
	addi	a0,a5,%lo(.LC33)
	call	print_str
	j	.L65
.L90:
	lui	a5,%hi(.LC34)
	addi	a0,a5,%lo(.LC34)
	call	print_str
.L65:
	lw	ra,76(sp)
	lw	s0,72(sp)
	addi	sp,sp,80
	jr	ra
	.size	handle_mem_command, .-handle_mem_command
	.section	.rodata
	.align	2
.LC35:
	.string	"set"
	.align	2
.LC36:
	.string	"\r\nError: incomplete args. Usage: pwm set <channel> <duty>\r\n"
	.align	2
.LC37:
	.string	"\r\nError: invalid channel argument\r\n"
	.align	2
.LC38:
	.string	"\r\nError: invalid duty argument\r\n"
	.align	2
.LC39:
	.string	"\r\nError: channel out of range\r\n"
	.align	2
.LC40:
	.string	"\r\nPWM channel "
	.align	2
.LC41:
	.string	" duty set to 0x"
	.align	2
.LC42:
	.string	"\r\nError: unknown subcmd. Usage: pwm set <channel> <duty>\r\n"
	.text
	.align	2
	.globl	handle_pwm_command
	.type	handle_pwm_command, @function
handle_pwm_command:
	addi	sp,sp,-48
	sw	ra,44(sp)
	sw	s0,40(sp)
	addi	s0,sp,48
	sw	a0,-36(s0)
	lw	a5,-36(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC35)
	addi	a1,a5,%lo(.LC35)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L97
	lw	a5,-36(s0)
	lbu	a5,32(a5)
	beq	a5,zero,.L98
	lw	a5,-36(s0)
	lbu	a5,48(a5)
	bne	a5,zero,.L99
.L98:
	lui	a5,%hi(.LC36)
	addi	a0,a5,%lo(.LC36)
	call	print_str
	j	.L96
.L99:
	sw	zero,-20(s0)
	sw	zero,-24(s0)
	lw	a5,-36(s0)
	addi	a5,a5,32
	addi	a4,s0,-20
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L101
	lw	a5,-36(s0)
	addi	a5,a5,32
	addi	a4,s0,-20
	mv	a1,a4
	mv	a0,a5
	call	parse_str_uint
	mv	a5,a0
	beq	a5,zero,.L101
	lui	a5,%hi(.LC37)
	addi	a0,a5,%lo(.LC37)
	call	print_str
	j	.L96
.L101:
	lw	a5,-36(s0)
	addi	a5,a5,48
	addi	a4,s0,-24
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L102
	lw	a5,-36(s0)
	addi	a5,a5,48
	addi	a4,s0,-24
	mv	a1,a4
	mv	a0,a5
	call	parse_str_uint
	mv	a5,a0
	beq	a5,zero,.L102
	lui	a5,%hi(.LC38)
	addi	a0,a5,%lo(.LC38)
	call	print_str
	j	.L96
.L102:
	lw	a4,-20(s0)
	li	a5,1
	bleu	a4,a5,.L103
	lui	a5,%hi(.LC39)
	addi	a0,a5,%lo(.LC39)
	call	print_str
	j	.L96
.L103:
	lw	a5,-20(s0)
	lw	a4,-24(s0)
	mv	a1,a4
	mv	a0,a5
	call	pwm_write_duty
	lui	a5,%hi(.LC40)
	addi	a0,a5,%lo(.LC40)
	call	print_str
	lw	a5,-20(s0)
	li	a1,2
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC41)
	addi	a0,a5,%lo(.LC41)
	call	print_str
	lw	a5,-24(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	j	.L96
.L97:
	lui	a5,%hi(.LC42)
	addi	a0,a5,%lo(.LC42)
	call	print_str
.L96:
	lw	ra,44(sp)
	lw	s0,40(sp)
	addi	sp,sp,48
	jr	ra
	.size	handle_pwm_command, .-handle_pwm_command
	.section	.rodata
	.align	2
.LC43:
	.string	"clear"
	.align	2
.LC44:
	.string	"\r\nError: invalid color format. Usage: vga clear 0x112233\r\n"
	.align	2
.LC45:
	.string	"\r\nVGA cleared with color: 0x"
	.align	2
.LC46:
	.string	"bars"
	.align	2
.LC47:
	.string	"\r\nVGA color bars drawn!\r\n"
	.align	2
.LC48:
	.string	"\r\nError: unknown VGA subcmd. Usage:\r\n    vga clear <hex_color>   e.g. vga clear 0x112233\r\n    vga bars\r\n"
	.text
	.align	2
	.globl	handle_vga_command
	.type	handle_vga_command, @function
handle_vga_command:
	addi	sp,sp,-48
	sw	ra,44(sp)
	sw	s0,40(sp)
	addi	s0,sp,48
	sw	a0,-36(s0)
	lw	a5,-36(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC43)
	addi	a1,a5,%lo(.LC43)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L106
	lw	a5,-36(s0)
	lbu	a5,32(a5)
	bne	a5,zero,.L107
	sw	zero,-24(s0)
	j	.L108
.L107:
	lw	a5,-36(s0)
	addi	a5,a5,32
	addi	a4,s0,-24
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L108
	lui	a5,%hi(.LC44)
	addi	a0,a5,%lo(.LC44)
	call	print_str
	j	.L105
.L108:
	lw	a5,-24(s0)
	srli	a5,a5,16
	sb	a5,-17(s0)
	lw	a5,-24(s0)
	srli	a5,a5,8
	sb	a5,-18(s0)
	lw	a5,-24(s0)
	sb	a5,-19(s0)
	lbu	a3,-19(s0)
	lbu	a4,-18(s0)
	lbu	a5,-17(s0)
	mv	a2,a3
	mv	a1,a4
	mv	a0,a5
	call	vga_clear
	lui	a5,%hi(.LC45)
	addi	a0,a5,%lo(.LC45)
	call	print_str
	lw	a5,-24(s0)
	li	a1,6
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	j	.L105
.L106:
	lw	a5,-36(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC46)
	addi	a1,a5,%lo(.LC46)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L110
	call	vga_draw_color_bars
	lui	a5,%hi(.LC47)
	addi	a0,a5,%lo(.LC47)
	call	print_str
	j	.L105
.L110:
	lui	a5,%hi(.LC48)
	addi	a0,a5,%lo(.LC48)
	call	print_str
.L105:
	lw	ra,44(sp)
	lw	s0,40(sp)
	addi	sp,sp,48
	jr	ra
	.size	handle_vga_command, .-handle_vga_command
	.section	.rodata
	.align	2
.LC49:
	.string	"\r\nUsage: timer set <interval>\r\n"
	.align	2
.LC50:
	.string	"\r\nError: invalid interval value\r\n"
	.align	2
.LC51:
	.string	"\r\nTimer interrupt set! Will trigger after "
	.align	2
.LC52:
	.string	" cycles\r\n"
	.align	2
.LC53:
	.string	"\r\nUnknown subcmd. Usage: timer set <interval>\r\n"
	.text
	.align	2
	.globl	handle_timer_command
	.type	handle_timer_command, @function
handle_timer_command:
	addi	sp,sp,-48
	sw	ra,44(sp)
	sw	s0,40(sp)
	sw	s2,36(sp)
	sw	s3,32(sp)
	addi	s0,sp,48
	sw	a0,-36(s0)
	lw	a5,-36(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC35)
	addi	a1,a5,%lo(.LC35)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L112
	lw	a5,-36(s0)
	lbu	a5,32(a5)
	bne	a5,zero,.L113
	lui	a5,%hi(.LC49)
	addi	a0,a5,%lo(.LC49)
	call	print_str
	j	.L111
.L113:
	sw	zero,-20(s0)
	lw	a5,-36(s0)
	addi	a5,a5,32
	addi	a4,s0,-20
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L115
	lw	a5,-36(s0)
	addi	a5,a5,32
	addi	a4,s0,-20
	mv	a1,a4
	mv	a0,a5
	call	parse_str_uint
	mv	a5,a0
	beq	a5,zero,.L115
	lui	a5,%hi(.LC50)
	addi	a0,a5,%lo(.LC50)
	call	print_str
	j	.L111
.L115:
	lw	a5,-20(s0)
	mv	s2,a5
	li	s3,0
	mv	a0,s2
	mv	a1,s3
	call	timer_init
	lui	a5,%hi(.LC51)
	addi	a0,a5,%lo(.LC51)
	call	print_str
	lw	a5,-20(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC52)
	addi	a0,a5,%lo(.LC52)
	call	print_str
	j	.L111
.L112:
	lui	a5,%hi(.LC53)
	addi	a0,a5,%lo(.LC53)
	call	print_str
.L111:
	lw	ra,44(sp)
	lw	s0,40(sp)
	lw	s2,36(sp)
	lw	s3,32(sp)
	addi	sp,sp,48
	jr	ra
	.size	handle_timer_command, .-handle_timer_command
	.section	.rodata
	.align	2
.LC54:
	.string	"gpio"
	.align	2
.LC55:
	.string	"mem"
	.align	2
.LC56:
	.string	"pwm"
	.align	2
.LC57:
	.string	"vga"
	.align	2
.LC58:
	.string	"timer"
	.align	2
.LC59:
	.string	"help"
	.align	2
.LC60:
	.string	"\r\nSupported Command:\r\n    gpio <in|out> [hex_value]\r\n    mem <read|write[8|16|32]> <addr> [hex_value]\r\n    pwm set <channel> <duty>\r\n    vga <clear|bars> [hex_color]\r\n    timer set <interval>\r\n"
	.align	2
.LC61:
	.string	"\r\nUnknown command. Type 'help'\r\n"
	.text
	.align	2
	.globl	dispatch_command
	.type	dispatch_command, @function
dispatch_command:
	addi	sp,sp,-32
	sw	ra,28(sp)
	sw	s0,24(sp)
	addi	s0,sp,32
	sw	a0,-20(s0)
	lw	a4,-20(s0)
	lui	a5,%hi(.LC54)
	addi	a1,a5,%lo(.LC54)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L118
	lw	a0,-20(s0)
	call	handle_gpio_command
	j	.L125
.L118:
	lw	a4,-20(s0)
	lui	a5,%hi(.LC55)
	addi	a1,a5,%lo(.LC55)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L120
	lw	a0,-20(s0)
	call	handle_mem_command
	j	.L125
.L120:
	lw	a4,-20(s0)
	lui	a5,%hi(.LC56)
	addi	a1,a5,%lo(.LC56)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L121
	lw	a0,-20(s0)
	call	handle_pwm_command
	j	.L125
.L121:
	lw	a4,-20(s0)
	lui	a5,%hi(.LC57)
	addi	a1,a5,%lo(.LC57)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L122
	lw	a0,-20(s0)
	call	handle_vga_command
	j	.L125
.L122:
	lw	a4,-20(s0)
	lui	a5,%hi(.LC58)
	addi	a1,a5,%lo(.LC58)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L123
	lw	a0,-20(s0)
	call	handle_timer_command
	j	.L125
.L123:
	lw	a4,-20(s0)
	lui	a5,%hi(.LC59)
	addi	a1,a5,%lo(.LC59)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L124
	lui	a5,%hi(.LC60)
	addi	a0,a5,%lo(.LC60)
	call	print_str
	j	.L125
.L124:
	lui	a5,%hi(.LC61)
	addi	a0,a5,%lo(.LC61)
	call	print_str
.L125:
	nop
	lw	ra,28(sp)
	lw	s0,24(sp)
	addi	sp,sp,32
	jr	ra
	.size	dispatch_command, .-dispatch_command
	.section	.rodata
	.align	2
.LC62:
	.string	"> "
	.text
	.align	2
	.globl	command_handler
	.type	command_handler, @function
command_handler:
	addi	sp,sp,-128
	sw	ra,124(sp)
	sw	s0,120(sp)
	addi	s0,sp,128
	lui	a5,%hi(.LC62)
	addi	a0,a5,%lo(.LC62)
	call	print_str
	addi	a4,s0,-60
	lui	a5,%hi(last_cmd.0)
	addi	a2,a5,%lo(last_cmd.0)
	li	a1,32
	mv	a0,a4
	call	get_str_with_history
	sw	zero,-20(s0)
	sw	zero,-24(s0)
	j	.L127
.L130:
	lw	a5,-24(s0)
	addi	a5,a5,-16
	add	a5,a5,s0
	lbu	a4,-44(a5)
	li	a5,32
	beq	a4,a5,.L128
	lw	a5,-24(s0)
	addi	a5,a5,-16
	add	a5,a5,s0
	lbu	a4,-44(a5)
	li	a5,9
	beq	a4,a5,.L128
	li	a5,1
	sw	a5,-20(s0)
	j	.L129
.L128:
	lw	a5,-24(s0)
	addi	a5,a5,1
	sw	a5,-24(s0)
.L127:
	lw	a5,-24(s0)
	addi	a5,a5,-16
	add	a5,a5,s0
	lbu	a5,-44(a5)
	bne	a5,zero,.L130
.L129:
	lw	a5,-20(s0)
	beq	a5,zero,.L131
	sw	zero,-28(s0)
	j	.L132
.L134:
	lw	a5,-28(s0)
	addi	a5,a5,-16
	add	a5,a5,s0
	lbu	a4,-44(a5)
	lui	a5,%hi(last_cmd.0)
	addi	a3,a5,%lo(last_cmd.0)
	lw	a5,-28(s0)
	add	a5,a3,a5
	sb	a4,0(a5)
	lw	a5,-28(s0)
	addi	a5,a5,-16
	add	a5,a5,s0
	lbu	a5,-44(a5)
	beq	a5,zero,.L135
	lw	a5,-28(s0)
	addi	a5,a5,1
	sw	a5,-28(s0)
.L132:
	lw	a4,-28(s0)
	li	a5,31
	bleu	a4,a5,.L134
	j	.L131
.L135:
	nop
.L131:
	addi	a4,s0,-124
	addi	a5,s0,-60
	mv	a1,a4
	mv	a0,a5
	call	parse_command
	addi	a5,s0,-124
	mv	a0,a5
	call	dispatch_command
	nop
	lw	ra,124(sp)
	lw	s0,120(sp)
	addi	sp,sp,128
	jr	ra
	.size	command_handler, .-command_handler
	.section	.rodata
	.align	2
.LC63:
	.string	"\r\n[IRQ] External interrupt triggered!\r\n"
	.text
	.align	2
	.globl	external_irq_handler
	.type	external_irq_handler, @function
external_irq_handler:
	addi	sp,sp,-16
	sw	ra,12(sp)
	sw	s0,8(sp)
	addi	s0,sp,16
	lui	a5,%hi(.LC63)
	addi	a0,a5,%lo(.LC63)
	call	print_str
	nop
	lw	ra,12(sp)
	lw	s0,8(sp)
	addi	sp,sp,16
	jr	ra
	.size	external_irq_handler, .-external_irq_handler
	.section	.rodata
	.align	2
.LC64:
	.string	"\r\n[IRQ] Timer interrupt triggered!\r\n"
	.align	2
.LC65:
	.string	"[IRQ] mepc="
	.text
	.align	2
	.globl	timer_irq_handler
	.type	timer_irq_handler, @function
timer_irq_handler:
	addi	sp,sp,-32
	sw	ra,28(sp)
	sw	s0,24(sp)
	addi	s0,sp,32
	lui	a5,%hi(.LC64)
	addi	a0,a5,%lo(.LC64)
	call	print_str
 #APP
# 523 "src/test/C/bios.c" 1
	csrr a5, mepc
# 0 "" 2
 #NO_APP
	sw	a5,-20(s0)
	lw	a5,-20(s0)
	sw	a5,-24(s0)
	lui	a5,%hi(.LC65)
	addi	a0,a5,%lo(.LC65)
	call	print_str
	li	a1,8
	lw	a0,-24(s0)
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	li	a0,1410064384
	addi	a0,a0,1023
	li	a1,2
	call	timer_init
	nop
	lw	ra,28(sp)
	lw	s0,24(sp)
	addi	sp,sp,32
	jr	ra
	.size	timer_irq_handler, .-timer_irq_handler
	.section	.rodata
	.align	2
.LC66:
	.string	"Welcome to PaSoC UART console!\r\n"
	.text
	.align	2
	.globl	main
	.type	main, @function
main:
	addi	sp,sp,-16
	sw	ra,12(sp)
	sw	s0,8(sp)
	addi	s0,sp,16
	call	trap_init
	li	a1,0
	li	a0,1
	call	interrupt_init
	lui	a5,%hi(.LC66)
	addi	a0,a5,%lo(.LC66)
	call	print_str
.L139:
	call	command_handler
	j	.L139
	.size	main, .-main
	.local	last_cmd.0
	.comm	last_cmd.0,32,4
	.ident	"GCC: () 15.1.0"
	.section	.note.GNU-stack,"",@progbits
