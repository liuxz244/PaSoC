	.file	"bios.c"
	.option nopic
	.attribute arch, "rv32i2p1_zicsr2p0"
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
	.string	"\r\nError: invalid address. Usage: mem read <addr>\r\n"
	.align	2
.LC11:
	.string	"\r\nMEM[0x"
	.align	2
.LC12:
	.string	"] = 0x"
	.align	2
.LC13:
	.string	"write"
	.align	2
.LC14:
	.string	"\r\nError: incomplete arguments. Usage: mem write <addr> <hex_value>\r\n"
	.align	2
.LC15:
	.string	"\r\nError: invalid address or value. Usage: mem write <addr> <hex_value>\r\n"
	.align	2
.LC16:
	.string	"] <= 0x"
	.align	2
.LC17:
	.string	"\r\nMemory write done!\r\n"
	.align	2
.LC18:
	.string	"\r\nError: unknown subcmd. Usage: mem <read|write> <addr> [hex_value]\r\n"
	.text
	.align	2
	.globl	handle_mem_command
	.type	handle_mem_command, @function
handle_mem_command:
	addi	sp,sp,-48
	sw	ra,44(sp)
	sw	s0,40(sp)
	addi	s0,sp,48
	sw	a0,-36(s0)
	lw	a5,-36(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC9)
	addi	a1,a5,%lo(.LC9)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L66
	lw	a5,-36(s0)
	addi	a5,a5,32
	addi	a4,s0,-24
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L67
	lui	a5,%hi(.LC10)
	addi	a0,a5,%lo(.LC10)
	call	print_str
	j	.L65
.L67:
	lw	a5,-24(s0)
	mv	a0,a5
	call	read_mem
	sw	a0,-20(s0)
	lui	a5,%hi(.LC11)
	addi	a0,a5,%lo(.LC11)
	call	print_str
	lw	a5,-24(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC12)
	addi	a0,a5,%lo(.LC12)
	call	print_str
	li	a1,8
	lw	a0,-20(s0)
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	j	.L65
.L66:
	lw	a5,-36(s0)
	addi	a4,a5,16
	lui	a5,%hi(.LC13)
	addi	a1,a5,%lo(.LC13)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L69
	lw	a5,-36(s0)
	lbu	a5,32(a5)
	beq	a5,zero,.L70
	lw	a5,-36(s0)
	lbu	a5,48(a5)
	bne	a5,zero,.L71
.L70:
	lui	a5,%hi(.LC14)
	addi	a0,a5,%lo(.LC14)
	call	print_str
	j	.L65
.L71:
	lw	a5,-36(s0)
	addi	a5,a5,32
	addi	a4,s0,-28
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	bne	a5,zero,.L73
	lw	a5,-36(s0)
	addi	a5,a5,48
	addi	a4,s0,-32
	mv	a1,a4
	mv	a0,a5
	call	parse_hex
	mv	a5,a0
	beq	a5,zero,.L74
.L73:
	lui	a5,%hi(.LC15)
	addi	a0,a5,%lo(.LC15)
	call	print_str
	j	.L65
.L74:
	lw	a5,-28(s0)
	lw	a4,-32(s0)
	mv	a1,a4
	mv	a0,a5
	call	write_mem
	lui	a5,%hi(.LC11)
	addi	a0,a5,%lo(.LC11)
	call	print_str
	lw	a5,-28(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC16)
	addi	a0,a5,%lo(.LC16)
	call	print_str
	lw	a5,-32(s0)
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC17)
	addi	a0,a5,%lo(.LC17)
	call	print_str
	j	.L65
.L69:
	lui	a5,%hi(.LC18)
	addi	a0,a5,%lo(.LC18)
	call	print_str
.L65:
	lw	ra,44(sp)
	lw	s0,40(sp)
	addi	sp,sp,48
	jr	ra
	.size	handle_mem_command, .-handle_mem_command
	.section	.rodata
	.align	2
.LC19:
	.string	"gpio"
	.align	2
.LC20:
	.string	"mem"
	.align	2
.LC21:
	.string	"help"
	.align	2
.LC22:
	.string	"\r\nSupported Command:\r\n    gpio <in|out> [hex_value]\r\n    mem <read|write> <addr> [hex_value]\r\n"
	.align	2
.LC23:
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
	lui	a5,%hi(.LC19)
	addi	a1,a5,%lo(.LC19)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L76
	lw	a0,-20(s0)
	call	handle_gpio_command
	j	.L80
.L76:
	lw	a4,-20(s0)
	lui	a5,%hi(.LC20)
	addi	a1,a5,%lo(.LC20)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L78
	lw	a0,-20(s0)
	call	handle_mem_command
	j	.L80
.L78:
	lw	a4,-20(s0)
	lui	a5,%hi(.LC21)
	addi	a1,a5,%lo(.LC21)
	mv	a0,a4
	call	strcmp
	mv	a5,a0
	bne	a5,zero,.L79
	lui	a5,%hi(.LC22)
	addi	a0,a5,%lo(.LC22)
	call	print_str
	j	.L80
.L79:
	lui	a5,%hi(.LC23)
	addi	a0,a5,%lo(.LC23)
	call	print_str
.L80:
	nop
	lw	ra,28(sp)
	lw	s0,24(sp)
	addi	sp,sp,32
	jr	ra
	.size	dispatch_command, .-dispatch_command
	.section	.rodata
	.align	2
.LC24:
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
	lui	a5,%hi(.LC24)
	addi	a0,a5,%lo(.LC24)
	call	print_str
	addi	a4,s0,-60
	lui	a5,%hi(last_cmd.0)
	addi	a2,a5,%lo(last_cmd.0)
	li	a1,32
	mv	a0,a4
	call	get_str_with_history
	sw	zero,-20(s0)
	sw	zero,-24(s0)
	j	.L82
.L85:
	lw	a5,-24(s0)
	addi	a5,a5,-16
	add	a5,a5,s0
	lbu	a4,-44(a5)
	li	a5,32
	beq	a4,a5,.L83
	lw	a5,-24(s0)
	addi	a5,a5,-16
	add	a5,a5,s0
	lbu	a4,-44(a5)
	li	a5,9
	beq	a4,a5,.L83
	li	a5,1
	sw	a5,-20(s0)
	j	.L84
.L83:
	lw	a5,-24(s0)
	addi	a5,a5,1
	sw	a5,-24(s0)
.L82:
	lw	a5,-24(s0)
	addi	a5,a5,-16
	add	a5,a5,s0
	lbu	a5,-44(a5)
	bne	a5,zero,.L85
.L84:
	lw	a5,-20(s0)
	beq	a5,zero,.L86
	sw	zero,-28(s0)
	j	.L87
.L89:
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
	beq	a5,zero,.L90
	lw	a5,-28(s0)
	addi	a5,a5,1
	sw	a5,-28(s0)
.L87:
	lw	a4,-28(s0)
	li	a5,31
	bleu	a4,a5,.L89
	j	.L86
.L90:
	nop
.L86:
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
.LC25:
	.string	"Welcome to PaSoC BIOS console!\r\n"
	.text
	.align	2
	.globl	main
	.type	main, @function
main:
	addi	sp,sp,-16
	sw	ra,12(sp)
	sw	s0,8(sp)
	addi	s0,sp,16
	lui	a5,%hi(.LC25)
	addi	a0,a5,%lo(.LC25)
	call	print_str
.L92:
	call	command_handler
	j	.L92
	.size	main, .-main
	.local	last_cmd.0
	.comm	last_cmd.0,32,4
	.ident	"GCC: (g1b306039ac4) 15.1.0"
	.section	.note.GNU-stack,"",@progbits
