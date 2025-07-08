	.file	"sdram.c"
	.option nopic
	.attribute arch, "rv32i2p1_m2p0_zicsr2p0_zmmul1p0"
	.attribute unaligned_access, 0
	.attribute stack_align, 16
	.text
	.section	.rodata
	.align	2
.LC0:
	.string	"Pattern 1: write address...\r\n"
	.align	2
.LC1:
	.string	"Error at addr="
	.align	2
.LC2:
	.string	", expected="
	.align	2
.LC3:
	.string	", actual="
	.align	2
.LC4:
	.string	"\r\n"
	.align	2
.LC5:
	.string	"\r\nSDRAM test FAILED\r\n"
	.align	2
.LC6:
	.string	"Pattern 1 OK\r\n"
	.align	2
.LC7:
	.string	"Pattern 2: write ~address...\r\n"
	.align	2
.LC8:
	.string	"Pattern 2 OK\r\n"
	.align	2
.LC9:
	.string	"SDRAM TEST PASSED.\r\n"
	.text
	.align	2
	.globl	test_sdram
	.type	test_sdram, @function
test_sdram:
	addi	sp,sp,-32
	sw	ra,28(sp)
	sw	s0,24(sp)
	addi	s0,sp,32
	lui	a5,%hi(.LC0)
	addi	a0,a5,%lo(.LC0)
	call	print_str
	li	a5,1073741824
	sw	a5,-20(s0)
	j	.L2
.L3:
	lw	a1,-20(s0)
	lw	a0,-20(s0)
	call	write_mem
	lw	a5,-20(s0)
	addi	a5,a5,4
	sw	a5,-20(s0)
.L2:
	lw	a4,-20(s0)
	li	a5,1082130432
	bltu	a4,a5,.L3
	li	a5,1073741824
	sw	a5,-20(s0)
	j	.L4
.L7:
	lw	a0,-20(s0)
	call	read_mem
	sw	a0,-28(s0)
	lw	a4,-28(s0)
	lw	a5,-20(s0)
	beq	a4,a5,.L5
	lui	a5,%hi(.LC1)
	addi	a0,a5,%lo(.LC1)
	call	print_str
	li	a1,8
	lw	a0,-20(s0)
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	li	a1,8
	lw	a0,-20(s0)
	call	print_hex
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	li	a1,8
	lw	a0,-28(s0)
	call	print_hex
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	lui	a5,%hi(.LC5)
	addi	a0,a5,%lo(.LC5)
	call	print_str
	j	.L1
.L5:
	lw	a5,-20(s0)
	addi	a5,a5,4
	sw	a5,-20(s0)
.L4:
	lw	a4,-20(s0)
	li	a5,1082130432
	bltu	a4,a5,.L7
	lui	a5,%hi(.LC6)
	addi	a0,a5,%lo(.LC6)
	call	print_str
	lui	a5,%hi(.LC7)
	addi	a0,a5,%lo(.LC7)
	call	print_str
	li	a5,1073741824
	sw	a5,-20(s0)
	j	.L8
.L9:
	lw	a5,-20(s0)
	not	a5,a5
	mv	a1,a5
	lw	a0,-20(s0)
	call	write_mem
	lw	a5,-20(s0)
	addi	a5,a5,4
	sw	a5,-20(s0)
.L8:
	lw	a4,-20(s0)
	li	a5,1082130432
	bltu	a4,a5,.L9
	li	a5,1073741824
	sw	a5,-20(s0)
	j	.L10
.L12:
	lw	a0,-20(s0)
	call	read_mem
	sw	a0,-24(s0)
	lw	a5,-20(s0)
	not	a5,a5
	lw	a4,-24(s0)
	beq	a4,a5,.L11
	lui	a5,%hi(.LC1)
	addi	a0,a5,%lo(.LC1)
	call	print_str
	li	a1,8
	lw	a0,-20(s0)
	call	print_hex
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	lw	a5,-20(s0)
	not	a5,a5
	li	a1,8
	mv	a0,a5
	call	print_hex
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	li	a1,8
	lw	a0,-24(s0)
	call	print_hex
	lui	a5,%hi(.LC5)
	addi	a0,a5,%lo(.LC5)
	call	print_str
	j	.L1
.L11:
	lw	a5,-20(s0)
	addi	a5,a5,4
	sw	a5,-20(s0)
.L10:
	lw	a4,-20(s0)
	li	a5,1082130432
	bltu	a4,a5,.L12
	lui	a5,%hi(.LC8)
	addi	a0,a5,%lo(.LC8)
	call	print_str
	lui	a5,%hi(.LC9)
	addi	a0,a5,%lo(.LC9)
	call	print_str
.L1:
	lw	ra,28(sp)
	lw	s0,24(sp)
	addi	sp,sp,32
	jr	ra
	.size	test_sdram, .-test_sdram
	.section	.rodata
	.align	2
.LC10:
	.string	"SDRAM Test Start\r\n"
	.text
	.align	2
	.globl	main
	.type	main, @function
main:
	addi	sp,sp,-16
	sw	ra,12(sp)
	sw	s0,8(sp)
	addi	s0,sp,16
	lui	a5,%hi(.LC10)
	addi	a0,a5,%lo(.LC10)
	call	print_str
	call	test_sdram
	li	a5,0
	mv	a0,a5
	lw	ra,12(sp)
	lw	s0,8(sp)
	addi	sp,sp,16
	jr	ra
	.size	main, .-main
	.ident	"GCC: (g1b306039ac4) 15.1.0"
	.section	.note.GNU-stack,"",@progbits
