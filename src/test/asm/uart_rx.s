	.file	"uart_rx.c"
	.option nopic
	.attribute arch, "rv32i2p1_zicsr2p0"
	.attribute unaligned_access, 0
	.attribute stack_align, 16
	.text
	.section	.rodata
	.align	2
.LC0:
	.string	"\nInput str: "
	.align	2
.LC1:
	.string	"\nReceived: "
	.align	2
.LC2:
	.string	"\nInput uint: "
	.align	2
.LC3:
	.string	"\nInput hex: "
	.align	2
.LC4:
	.string	"\nReceived: 0x"
	.text
	.align	2
	.globl	main
	.type	main, @function
main:
	addi	sp,sp,-48
	sw	ra,44(sp)
	sw	s0,40(sp)
	addi	s0,sp,48
.L2:
	lui	a5,%hi(.LC0)
	addi	a0,a5,%lo(.LC0)
	call	print_str
	addi	a5,s0,-36
	li	a1,10
	mv	a0,a5
	call	get_str
	lui	a5,%hi(.LC1)
	addi	a0,a5,%lo(.LC1)
	call	print_str
	addi	a5,s0,-36
	mv	a0,a5
	call	print_str
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	call	get_uint
	sw	a0,-20(s0)
	lui	a5,%hi(.LC1)
	addi	a0,a5,%lo(.LC1)
	call	print_str
	lw	a0,-20(s0)
	call	print_dec
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	call	get_hex
	sw	a0,-24(s0)
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	li	a1,4
	lw	a0,-24(s0)
	call	print_hex
	li	a0,10
	call	print_char
	j	.L2
	.size	main, .-main
	.ident	"GCC: (g1b306039ac4) 15.1.0"
	.section	.note.GNU-stack,"",@progbits
