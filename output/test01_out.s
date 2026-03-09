.data
string_access_violation: .asciiz "Access Violation"
string_illegal_div_by_0: .asciiz "Illegal Division By Zero"
string_invalid_ptr_dref: .asciiz "Invalid Pointer Dereference"

.text
IsPrime:
# prologue
subu $sp, $sp, 4
sw $ra, 0($sp)
subu $sp, $sp, 4
sw $fp, 0($sp)
move $fp, $sp
subu $sp, $sp, 4
sw $t0, 0($sp)
subu $sp, $sp, 4
sw $t1, 0($sp)
subu $sp, $sp, 4
sw $t2, 0($sp)
subu $sp, $sp, 4
sw $t3, 0($sp)
subu $sp, $sp, 4
sw $t4, 0($sp)
subu $sp, $sp, 4
sw $t5, 0($sp)
subu $sp, $sp, 4
sw $t6, 0($sp)
subu $sp, $sp, 4
sw $t7, 0($sp)
subu $sp, $sp, 4
sw $t8, 0($sp)
subu $sp, $sp, 4
sw $t9, 0($sp)
subu $sp, $sp, 8
li $t0, 2
sw $t0, -44($fp)
li $t0, 2
sw $t0, -48($fp)
Label_1_start_while:
lw $t0, -44($fp)
lw $t1, 8($fp)
slt $t0, $t0, $t1
beq $t0, $zero, Label_0_end_while
li $t0, 2
sw $t0, -48($fp)
Label_3_start_while:
lw $t0, -48($fp)
lw $t1, 8($fp)
slt $t0, $t0, $t1
beq $t0, $zero, Label_2_end_while
lw $t1, -44($fp)
lw $t0, -48($fp)
mul $t1, $t1, $t0
li $s0, 32767
ble $t1, $s0, sat_hi_0
move $t1, $s0
sat_hi_0:
li $s0, -32768
bge $t1, $s0, sat_lo_1
move $t1, $s0
sat_lo_1:
lw $t0, 8($fp)
seq $t0, $t1, $t0
beq $t0, $zero, Label_4_end_if
li $t0, 0
move $v0, $t0
j IsPrime_epilogue
Label_4_end_if:
lw $t0, -48($fp)
li $t1, 1
add $t0, $t0, $t1
li $s0, 32767
ble $t0, $s0, sat_hi_2
move $t0, $s0
sat_hi_2:
li $s0, -32768
bge $t0, $s0, sat_lo_3
move $t0, $s0
sat_lo_3:
sw $t0, -48($fp)
j Label_3_start_while
Label_2_end_while:
lw $t1, -44($fp)
li $t0, 1
add $t0, $t1, $t0
li $s0, 32767
ble $t0, $s0, sat_hi_4
move $t0, $s0
sat_hi_4:
li $s0, -32768
bge $t0, $s0, sat_lo_5
move $t0, $s0
sat_lo_5:
sw $t0, -44($fp)
j Label_1_start_while
Label_0_end_while:
li $t0, 1
move $v0, $t0
j IsPrime_epilogue
IsPrime_epilogue:
# epilogue
lw $t0, -4($fp)
lw $t1, -8($fp)
lw $t2, -12($fp)
lw $t3, -16($fp)
lw $t4, -20($fp)
lw $t5, -24($fp)
lw $t6, -28($fp)
lw $t7, -32($fp)
lw $t8, -36($fp)
lw $t9, -40($fp)
move $sp, $fp
lw $fp, 0($sp)
addu $sp, $sp, 4
lw $ra, 0($sp)
addu $sp, $sp, 4
jr $ra
PrintPrimes:
# prologue
subu $sp, $sp, 4
sw $ra, 0($sp)
subu $sp, $sp, 4
sw $fp, 0($sp)
move $fp, $sp
subu $sp, $sp, 4
sw $t0, 0($sp)
subu $sp, $sp, 4
sw $t1, 0($sp)
subu $sp, $sp, 4
sw $t2, 0($sp)
subu $sp, $sp, 4
sw $t3, 0($sp)
subu $sp, $sp, 4
sw $t4, 0($sp)
subu $sp, $sp, 4
sw $t5, 0($sp)
subu $sp, $sp, 4
sw $t6, 0($sp)
subu $sp, $sp, 4
sw $t7, 0($sp)
subu $sp, $sp, 4
sw $t8, 0($sp)
subu $sp, $sp, 4
sw $t9, 0($sp)
subu $sp, $sp, 4
lw $t0, 8($fp)
sw $t0, -44($fp)
Label_6_start_while:
lw $t2, -44($fp)
lw $t1, 12($fp)
li $t0, 1
add $t0, $t1, $t0
li $s0, 32767
ble $t0, $s0, sat_hi_6
move $t0, $s0
sat_hi_6:
li $s0, -32768
bge $t0, $s0, sat_lo_7
move $t0, $s0
sat_lo_7:
slt $t0, $t2, $t0
beq $t0, $zero, Label_5_end_while
lw $t0, -44($fp)
subu $sp, $sp, 4
sw $t0, 0($sp)
jal IsPrime
addu $sp, $sp, 4
move $t0, $v0
beq $t0, $zero, Label_7_end_if
lw $t0, -44($fp)
move $a0, $t0
li $v0, 1
syscall
li $a0, 32
li $v0, 11
syscall
Label_7_end_if:
lw $t0, -44($fp)
li $t1, 1
add $t0, $t0, $t1
li $s0, 32767
ble $t0, $s0, sat_hi_8
move $t0, $s0
sat_hi_8:
li $s0, -32768
bge $t0, $s0, sat_lo_9
move $t0, $s0
sat_lo_9:
sw $t0, -44($fp)
j Label_6_start_while
Label_5_end_while:
PrintPrimes_epilogue:
# epilogue
lw $t0, -4($fp)
lw $t1, -8($fp)
lw $t2, -12($fp)
lw $t3, -16($fp)
lw $t4, -20($fp)
lw $t5, -24($fp)
lw $t6, -28($fp)
lw $t7, -32($fp)
lw $t8, -36($fp)
lw $t9, -40($fp)
move $sp, $fp
lw $fp, 0($sp)
addu $sp, $sp, 4
lw $ra, 0($sp)
addu $sp, $sp, 4
jr $ra
user_main:
# prologue
subu $sp, $sp, 4
sw $ra, 0($sp)
subu $sp, $sp, 4
sw $fp, 0($sp)
move $fp, $sp
subu $sp, $sp, 4
sw $t0, 0($sp)
subu $sp, $sp, 4
sw $t1, 0($sp)
subu $sp, $sp, 4
sw $t2, 0($sp)
subu $sp, $sp, 4
sw $t3, 0($sp)
subu $sp, $sp, 4
sw $t4, 0($sp)
subu $sp, $sp, 4
sw $t5, 0($sp)
subu $sp, $sp, 4
sw $t6, 0($sp)
subu $sp, $sp, 4
sw $t7, 0($sp)
subu $sp, $sp, 4
sw $t8, 0($sp)
subu $sp, $sp, 4
sw $t9, 0($sp)
subu $sp, $sp, 0
li $t0, 2
li $t1, 100
subu $sp, $sp, 4
sw $t1, 0($sp)
subu $sp, $sp, 4
sw $t0, 0($sp)
jal PrintPrimes
addu $sp, $sp, 8
user_main_epilogue:
# epilogue
lw $t0, -4($fp)
lw $t1, -8($fp)
lw $t2, -12($fp)
lw $t3, -16($fp)
lw $t4, -20($fp)
lw $t5, -24($fp)
lw $t6, -28($fp)
lw $t7, -32($fp)
lw $t8, -36($fp)
lw $t9, -40($fp)
move $sp, $fp
lw $fp, 0($sp)
addu $sp, $sp, 4
lw $ra, 0($sp)
addu $sp, $sp, 4
jr $ra
main:
jal user_main
li $v0, 10
syscall
__nil_handler:
la $a0, string_invalid_ptr_dref
li $v0, 4
syscall
li $v0, 10
syscall
__div_by_zero_handler:
la $a0, string_illegal_div_by_0
li $v0, 4
syscall
li $v0, 10
syscall
__bounds_handler:
la $a0, string_access_violation
li $v0, 4
syscall
li $v0, 10
syscall
__str_concat:
move $s4, $a0
move $s5, $a1
li $s2, 0
move $s0, $s4
__sc_l1:
lb $s3, 0($s0)
beq $s3, $zero, __sc_l1_done
addu $s0, $s0, 1
addu $s2, $s2, 1
j __sc_l1
__sc_l1_done:
move $s1, $s5
__sc_l2:
lb $s3, 0($s1)
beq $s3, $zero, __sc_l2_done
addu $s1, $s1, 1
addu $s2, $s2, 1
j __sc_l2
__sc_l2_done:
addu $a0, $s2, 1
li $v0, 9
syscall
move $s3, $v0
move $s0, $s4
move $s1, $s3
__sc_cp1:
lb $s2, 0($s0)
beq $s2, $zero, __sc_cp1_done
sb $s2, 0($s1)
addu $s0, $s0, 1
addu $s1, $s1, 1
j __sc_cp1
__sc_cp1_done:
move $s0, $s5
__sc_cp2:
lb $s2, 0($s0)
sb $s2, 0($s1)
beq $s2, $zero, __sc_cp2_done
addu $s0, $s0, 1
addu $s1, $s1, 1
j __sc_cp2
__sc_cp2_done:
move $v0, $s3
jr $ra
