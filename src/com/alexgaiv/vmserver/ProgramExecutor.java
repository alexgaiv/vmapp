package com.alexgaiv.vmserver;

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

        return result;
    }
}
