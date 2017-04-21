package com.alexgaiv.vmserver.parser;

public class ProgramExecutor
{
    public ProgramExecuteResult execute(String program)
    {
        ProgramExecuteResult result = new ProgramExecuteResult();
        result.success = false;
        result.programOutput = "";

        return result;
    }
}
