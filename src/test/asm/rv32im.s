	.file	"rv32im.c"
	.option nopic
	.attribute arch, "rv32i2p1_m2p0_zicsr2p0_zmmul1p0"
	.attribute unaligned_access, 0
	.attribute stack_align, 16
	.text
	.section	.rodata
	.align	2
.LC0:
	.string	"\n==== RV32IM ====\n"
	.align	2
.LC1:
	.string	"add: "
	.align	2
.LC2:
	.string	" + "
	.align	2
.LC3:
	.string	" = "
	.align	2
.LC4:
	.string	"\n"
	.align	2
.LC5:
	.string	"sub: "
	.align	2
.LC6:
	.string	" - "
	.align	2
.LC7:
	.string	"and: "
	.align	2
.LC8:
	.string	" & "
	.align	2
.LC9:
	.string	"or:  "
	.align	2
.LC10:
	.string	" | "
	.align	2
.LC11:
	.string	"xor: "
	.align	2
.LC12:
	.string	" ^ "
	.align	2
.LC13:
	.string	"sll: "
	.align	2
.LC14:
	.string	" << 1"
	.align	2
.LC15:
	.string	"srl: "
	.align	2
.LC16:
	.string	" >> 1"
	.align	2
.LC17:
	.string	"slt: "
	.align	2
.LC18:
	.string	" < "
	.align	2
.LC19:
	.string	"mul: "
	.align	2
.LC20:
	.string	" * "
	.align	2
.LC21:
	.string	"div: "
	.align	2
.LC22:
	.string	" / "
	.align	2
.LC23:
	.string	"rem: "
	.align	2
.LC24:
	.string	" % "
	.align	2
.LC25:
	.string	"==== Test End ====\n"
	.text
	.align	2
	.globl	test_rv32im
	.type	test_rv32im, @function
test_rv32im:
	addi	sp,sp,-32
	sw	ra,28(sp)
	sw	s0,24(sp)
	addi	s0,sp,32
	li	a5,42
	sw	a5,-20(s0)
	li	a5,7
	sw	a5,-24(s0)
	lui	a5,%hi(.LC0)
	addi	a0,a5,%lo(.LC0)
	call	print_str
	lw	a5,-20(s0)
	lw	a4,-24(s0)
 #APP
# 12 "src/test/C/rv32im.c" 1
	add a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC1)
	addi	a0,a5,%lo(.LC1)
	call	print_str
	lw	a0,-20(s0)
	call	print_dec
	lui	a5,%hi(.LC2)
	addi	a0,a5,%lo(.LC2)
	call	print_str
	lw	a0,-24(s0)
	call	print_dec
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	lw	a0,-28(s0)
	call	print_dec
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	lw	a5,-20(s0)
	lw	a4,-24(s0)
 #APP
# 16 "src/test/C/rv32im.c" 1
	sub a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC5)
	addi	a0,a5,%lo(.LC5)
	call	print_str
	lw	a0,-20(s0)
	call	print_dec
	lui	a5,%hi(.LC6)
	addi	a0,a5,%lo(.LC6)
	call	print_str
	lw	a0,-24(s0)
	call	print_dec
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	lw	a0,-28(s0)
	call	print_dec
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	lw	a5,-20(s0)
	lw	a4,-24(s0)
 #APP
# 21 "src/test/C/rv32im.c" 1
	and a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC7)
	addi	a0,a5,%lo(.LC7)
	call	print_str
	li	a1,8
	lw	a0,-20(s0)
	call	print_hex
	lui	a5,%hi(.LC8)
	addi	a0,a5,%lo(.LC8)
	call	print_str
	li	a1,8
	lw	a0,-24(s0)
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
	lw	a5,-20(s0)
	lw	a4,-24(s0)
 #APP
# 25 "src/test/C/rv32im.c" 1
	or a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC9)
	addi	a0,a5,%lo(.LC9)
	call	print_str
	li	a1,8
	lw	a0,-20(s0)
	call	print_hex
	lui	a5,%hi(.LC10)
	addi	a0,a5,%lo(.LC10)
	call	print_str
	li	a1,8
	lw	a0,-24(s0)
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
	lw	a5,-20(s0)
	lw	a4,-24(s0)
 #APP
# 29 "src/test/C/rv32im.c" 1
	xor a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC11)
	addi	a0,a5,%lo(.LC11)
	call	print_str
	li	a1,8
	lw	a0,-20(s0)
	call	print_hex
	lui	a5,%hi(.LC12)
	addi	a0,a5,%lo(.LC12)
	call	print_str
	li	a1,8
	lw	a0,-24(s0)
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
	lw	a5,-20(s0)
	li	a4,1
 #APP
# 34 "src/test/C/rv32im.c" 1
	sll a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC13)
	addi	a0,a5,%lo(.LC13)
	call	print_str
	lw	a0,-20(s0)
	call	print_dec
	lui	a5,%hi(.LC14)
	addi	a0,a5,%lo(.LC14)
	call	print_str
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	lw	a0,-28(s0)
	call	print_dec
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	lw	a5,-20(s0)
	li	a4,1
 #APP
# 38 "src/test/C/rv32im.c" 1
	srl a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC15)
	addi	a0,a5,%lo(.LC15)
	call	print_str
	lw	a0,-20(s0)
	call	print_dec
	lui	a5,%hi(.LC16)
	addi	a0,a5,%lo(.LC16)
	call	print_str
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	lw	a0,-28(s0)
	call	print_dec
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	lw	a5,-20(s0)
	lw	a4,-24(s0)
 #APP
# 43 "src/test/C/rv32im.c" 1
	slt a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC17)
	addi	a0,a5,%lo(.LC17)
	call	print_str
	lw	a0,-20(s0)
	call	print_dec
	lui	a5,%hi(.LC18)
	addi	a0,a5,%lo(.LC18)
	call	print_str
	lw	a0,-24(s0)
	call	print_dec
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	lw	a0,-28(s0)
	call	print_dec
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	lw	a5,-20(s0)
	lw	a4,-24(s0)
 #APP
# 48 "src/test/C/rv32im.c" 1
	mul a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC19)
	addi	a0,a5,%lo(.LC19)
	call	print_str
	lw	a0,-20(s0)
	call	print_dec
	lui	a5,%hi(.LC20)
	addi	a0,a5,%lo(.LC20)
	call	print_str
	lw	a0,-24(s0)
	call	print_dec
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	lw	a0,-28(s0)
	call	print_dec
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	lw	a5,-20(s0)
	lw	a4,-24(s0)
 #APP
# 53 "src/test/C/rv32im.c" 1
	div a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC21)
	addi	a0,a5,%lo(.LC21)
	call	print_str
	lw	a0,-20(s0)
	call	print_dec
	lui	a5,%hi(.LC22)
	addi	a0,a5,%lo(.LC22)
	call	print_str
	lw	a0,-24(s0)
	call	print_dec
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	lw	a0,-28(s0)
	call	print_dec
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	lw	a5,-20(s0)
	lw	a4,-24(s0)
 #APP
# 57 "src/test/C/rv32im.c" 1
	rem a5, a5, a4
# 0 "" 2
 #NO_APP
	sw	a5,-28(s0)
	lui	a5,%hi(.LC23)
	addi	a0,a5,%lo(.LC23)
	call	print_str
	lw	a0,-20(s0)
	call	print_dec
	lui	a5,%hi(.LC24)
	addi	a0,a5,%lo(.LC24)
	call	print_str
	lw	a0,-24(s0)
	call	print_dec
	lui	a5,%hi(.LC3)
	addi	a0,a5,%lo(.LC3)
	call	print_str
	lw	a0,-28(s0)
	call	print_dec
	lui	a5,%hi(.LC4)
	addi	a0,a5,%lo(.LC4)
	call	print_str
	lui	a5,%hi(.LC25)
	addi	a0,a5,%lo(.LC25)
	call	print_str
	nop
	lw	ra,28(sp)
	lw	s0,24(sp)
	addi	sp,sp,32
	jr	ra
	.size	test_rv32im, .-test_rv32im
	.align	2
	.globl	main
	.type	main, @function
main:
	addi	sp,sp,-16
	sw	ra,12(sp)
	sw	s0,8(sp)
	addi	s0,sp,16
	call	test_rv32im
	li	a5,0
	mv	a0,a5
	lw	ra,12(sp)
	lw	s0,8(sp)
	addi	sp,sp,16
	jr	ra
	.size	main, .-main
	.ident	"GCC: () 15.1.0"
	.section	.note.GNU-stack,"",@progbits
