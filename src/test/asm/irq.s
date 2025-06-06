	.file	"irq.c"
	.option nopic
	.attribute arch, "rv32i2p1_zicsr2p0"
	.attribute unaligned_access, 0
	.attribute stack_align, 16
	.text
	.globl	g_tick
	.section	.sbss,"aw",@nobits
	.align	2
	.type	g_tick, @object
	.size	g_tick, 4
g_tick:
	.zero	4
	.section	.rodata
	.align	2
.LC0:
	.string	"External Interrupt: "
	.align	2
.LC1:
	.string	"GPIO\n"
	.align	2
.LC2:
	.string	"Other\n"
	.text
	.align	2
	.globl	external_irq_handler
	.type	external_irq_handler, @function
external_irq_handler:
	addi	sp,sp,-32
	sw	ra,28(sp)
	sw	s0,24(sp)
	addi	s0,sp,32
	lui	a5,%hi(.LC0)
	addi	a0,a5,%lo(.LC0)
	call	print_str
	li	a5,1342177280
	addi	a5,a5,12
	lw	a5,0(a5)
	andi	a5,a5,15
	sw	a5,-20(s0)
	lw	a5,-20(s0)
	beq	a5,zero,.L5
	lw	a4,-20(s0)
	li	a5,8
	bne	a4,a5,.L3
	lui	a5,%hi(.LC1)
	addi	a0,a5,%lo(.LC1)
	call	print_str
	j	.L4
.L3:
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	nop
.L4:
	li	a5,1342177280
	addi	a5,a5,12
	lw	a4,-20(s0)
	sw	a4,0(a5)
.L5:
	nop
	lw	ra,28(sp)
	lw	s0,24(sp)
	addi	sp,sp,32
	jr	ra
	.size	external_irq_handler, .-external_irq_handler
	.section	.rodata
	.align	2
.LC3:
	.string	"Timer Interrupt: "
	.text
	.align	2
	.globl	timer_irq_handler
	.type	timer_irq_handler, @function
timer_irq_handler:
	addi	sp,sp,-16
	sw	ra,12(sp)
	sw	s0,8(sp)
	addi	s0,sp,16
	li	a0,300
	li	a1,0
	call	timer_init
	lui	a5,%hi(g_tick)
	lw	a5,%lo(g_tick)(a5)
	addi	a4,a5,1
	lui	a5,%hi(g_tick)
	sw	a4,%lo(g_tick)(a5)
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	lui	a5,%hi(g_tick)
	lw	a5,%lo(g_tick)(a5)
	li	a1,1
	mv	a0,a5
	call	print_hex
	li	a0,10
	call	print_char
	nop
	lw	ra,12(sp)
	lw	s0,8(sp)
	addi	sp,sp,16
	jr	ra
	.size	timer_irq_handler, .-timer_irq_handler
	.align	2
	.globl	main
	.type	main, @function
main:
	addi	sp,sp,-16
	sw	ra,12(sp)
	sw	s0,8(sp)
	addi	s0,sp,16
	call	trap_init
	li	a1,1
	li	a0,0
	call	interrupt_init
	li	a0,129
	call	plic_init
	li	a0,200
	li	a1,0
	call	timer_init
.L8:
	j	.L8
	.size	main, .-main
	.ident	"GCC: (g1b306039ac4) 15.1.0"
	.section	.note.GNU-stack,"",@progbits
