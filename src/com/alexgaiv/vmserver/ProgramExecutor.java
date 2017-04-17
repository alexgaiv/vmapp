package com.alexgaiv.vmserver;

import java.io.IOException;

class ProgramExecuteResult
{
    boolean success = false;
    String programOutput = "";
}

class ProgramExecutor
{
    ProgramExecuteResult execute(String program)
    {
        ProgramExecuteResult result = new ProgramExecuteResult();
        result.success = false;
        result.programOutput = "";

        ProgramTokenizer tokenizer = new ProgramTokenizer(program);

        try {

            Token token = new Token();
            while (token.type != TokenType.T_EOF) {
                token = tokenizer.nextToken();
            }
        }
        catch (IOException e) {
            //
        }
        catch (UnexpectedTokenException e) {
            //
        }

        return result;
    }

    public static void main(String[] args)
    {
        //ProgramExecutor exec = new ProgramExecutor();
        //exec.execute("/ = a == != < > <= >= + - || * && ! int string if else while ( ) [ ] { } , ; 42 43.34 \"abcd\" a_23re331");
    }
}
