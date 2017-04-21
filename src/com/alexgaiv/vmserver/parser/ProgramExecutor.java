package com.alexgaiv.vmserver.parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

class ProgramExecuteException extends Exception
{
    ProgramExecuteException(String message) {
        super("Runtime error: " + message);
    }
}

public class ProgramExecutor
{
    private ArrayList<Double> stack;

    public ProgramExecuteResult execute(String program)
    {
        ProgramExecuteResult result = new ProgramExecuteResult();
        ProgramParser parser = new ProgramParser();
        StringBuilder output = new StringBuilder();

        try
        {
            parser.parse(program);

            Bytecode bytecode = parser.getBytecode();
            ByteBuffer buffer = bytecode.toByteBuffer();
            HashMap<Integer, String> stringTable = parser.getStringTable();
            stack = new ArrayList<>();

            int ip = 0; // instruction pointer

            while (ip < bytecode.size())
            {
                byte op = buffer.get(ip);
                OpCode opCode = OpCode.values()[op - 1];

                switch (opCode)
                {
                    case load: {
                        int address = buffer.getInt(ip + 1);
                        pushStack(stack.get(address));
                        ip += 5;
                        break;
                    }
                    case store: {
                        int address = buffer.getInt(ip + 1);
                        stack.set(address, popStack());
                        ip += 5;
                        break;
                    }
                    case ld_arr: {
                        int arrayAddress = buffer.getInt(ip + 1);
                        int index = (int)popStack();
                        int arraySize = (int)(double)stack.get(arrayAddress);
                        if (index >= arraySize)
                            throw new ProgramExecuteException("Array index was out of bounds");

                        pushStack(stack.get(arrayAddress + index + 1));
                        ip += 5;
                        break;
                    }
                    case st_arr: {
                        int arrayAddress = buffer.getInt(ip + 1);
                        double value = popStack();
                        int index = (int)popStack();
                        int arraySize = (int)(double)stack.get(arrayAddress);
                        if (index >= arraySize)
                            throw new ProgramExecuteException("Array index was out of bounds");

                        stack.set(arrayAddress + index + 1, value);
                        ip += 5;
                        break;
                    }
                    case subsp: {
                        int amount = buffer.getInt(ip + 1);
                        for (int i = 0; i < amount; i++)
                            pushStack(0.0);
                        ip += 5;
                        break;
                    }
                    case addsp: {
                        int amount = buffer.getInt(ip + 1);
                        for (int i = 0; i < amount; i++)
                            popStack();
                        ip += 5;
                        break;
                    }
                    case jmp: {
                        ip = buffer.getInt(ip + 1);
                        break;
                    }
                    case jmpz: {
                        double value = popStack();
                        if (value == 0.0)
                            ip = buffer.getInt(ip + 1);
                        else ip += 5;
                        break;
                    }
                    case ld_const: {
                        double value = buffer.getDouble(ip + 1);
                        pushStack(value);
                        ip += 9;
                        break;
                    }
                    case eq:
                        pushStack(popStack() == popStack() ? 1.0 : 0.0); ip++;
                        break;
                    case noteq:
                        pushStack(popStack() != popStack() ? 1.0 : 0.0); ip++;
                        break;
                    case lss: {
                        double right = popStack();
                        double left = popStack();
                        pushStack(left < right ? 1.0 : 0.0);
                        ip++;
                        break;
                    }
                    case grt: {
                        double right = popStack();
                        double left = popStack();
                        pushStack(left > right ? 1.0 : 0.0);
                        ip++;
                        break;
                    }
                    case lsseq: {
                        double right = popStack();
                        double left = popStack();
                        pushStack(left <= right ? 1.0 : 0.0);
                        ip++;
                        break;
                    }
                    case grteq: {
                        double right = popStack();
                        double left = popStack();
                        pushStack(left >= right ? 1.0 : 0.0);
                        ip++;
                        break;
                    }
                    case add:
                        pushStack(popStack() + popStack()); ip++;
                        break;
                    case sub: {
                        double right = popStack();
                        double left = popStack();
                        pushStack(left - right);
                        ip++;
                        break;
                    }
                    case mul:
                        pushStack(popStack() * popStack()); ip++;
                        break;
                    case div: {
                        double right = popStack();
                        double left = popStack();
                        pushStack(left / right);
                        ip++;
                        break;
                    }
                    case or:
                        pushStack(popStack() != 0.0 || popStack() != 0.0 ? 1.0 : 0.0); ip++;
                        break;
                    case and:
                        pushStack(popStack() != 0.0 && popStack() != 0.0 ? 1.0 : 0.0); ip++;
                        break;
                    case not: {
                        pushStack(popStack() != 0.0 ? 0.0 : 1.0);
                        ip++;
                        break;
                    }
                    case neg: {
                        pushStack(-popStack());
                        ip++;
                        break;
                    }
                    case print_real:
                        output.append(popStack());
                        ip++;
                        break;
                    case print_arr:
                        int arrayAddress = (int)(double)popStack();
                        int arraySize = (int)(double)stack.get(arrayAddress); // cannot be zero

                        output.append("[");
                        output.append(stack.get(arrayAddress + 1));
                        for (int i = 1; i < arraySize; i++) {
                            output.append(", ").append(stack.get(arrayAddress + i + 1));
                        }
                        output.append("]");
                        ip++;
                        break;
                    case print_str:
                        int stringId = (int)(double)popStack();
                        String string = stringTable.get(stringId);
                        if (string == null) {
                            throw new ProgramExecuteException("Invalid string pointer");
                        }
                        output.append(string);
                        ip++;
                        break;
                    case sqrt:
                        pushStack(Math.sqrt(popStack()));
                        ip++;
                        break;
                    default:
                        throw new ProgramExecuteException("Unknown instruction");

                }
            }

            result.success = true;
            result.programOutput = output.toString();
        }
        catch (ProgramParseException | ProgramExecuteException e) {
            result.success = false;
            result.errorMessage = e.getMessage();
        }

        stack = null;
        return result;
    }

    private void pushStack(double value) {
        stack.add(value);
    }

    private double popStack() {
        return stack.remove(stack.size() - 1);
    }

    public static void main(String[] args)
    {
        ProgramExecutor executor = new ProgramExecutor();

        String programText;
        try {
            programText = new String(Files.readAllBytes(Paths.get("sample.txt")), Charset.defaultCharset());
            ProgramExecuteResult result = executor.execute(programText);
            System.out.printf(result.success ? result.programOutput : result.errorMessage);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
