package com.alexgaiv.vmserver.parser;

enum OpCode
{
    load(1),
    store(2),
    ld_arr(3),
    st_arr(4),
    subsp(5),
    addsp(6),
    jmp(7),
    jmpz(8),

    ld_const(9),

    eq(10),
    noteq(11),
    lss(12),
    grt(13),
    lsseq(14),
    grteq(15),
    add(16),
    sub(17),
    mul(18),
    div(19),
    or(20),
    and(21),
    not(22),
    neg(23),
    print_real(24),
    print_str(25),
    print_arr(26),
    sqrt(27);

    byte code;
    OpCode(int code) { this.code = (byte)code; }
}
