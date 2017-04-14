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
        result.programOutput = "not implemented";

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result;
    }
}
