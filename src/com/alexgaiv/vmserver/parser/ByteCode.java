package com.alexgaiv.vmserver.parser;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;

class Bytecode
{
    private ArrayList<Byte> bytecode = new ArrayList<>();

    private void putInt(int value) {
        byte[] bytes = new byte[4];
        ByteBuffer.wrap(bytes).putInt(value);
        for (byte b : bytes)
            bytecode.add(b);
    }

    private void putDouble(double value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(value);
        for (byte b : bytes)
            bytecode.add(b);
    }

    int size() { return bytecode.size(); }

    void put(OpCode opCode) {
        bytecode.add(opCode.code);
    }

    void put(OpCode opCode, int arg) {
        bytecode.add(opCode.code);
        putInt(arg);
    }

    void putValue(double value) {
        put(OpCode.ld_const);
        putDouble(value);
    }

    int putLabel() {
        int size = bytecode.size();
        putInt(0);
        return size;
    }

    void markLabel(int label) {
        int s = bytecode.size();
        byte[] bytes = new byte[4];
        ByteBuffer.wrap(bytes).putInt(s);
        for (int i = 0; i < 4; i++)
            bytecode.set(label + i, bytes[i]);
    }

    void writeToTextFile(String filename)
    {
        try (PrintWriter file = new PrintWriter(filename))
        {
            byte[] bytes = new byte[bytecode.size()];
            for (int i = 0; i < bytecode.size(); i++)
                bytes[i] = bytecode.get(i);

            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            int i = 0;
            while (i < bytecode.size())
            {
                byte op = buffer.get(i);

                OpCode opCode = OpCode.values()[op - 1];

                if (op >= OpCode.load.code && op <= OpCode.jmpz.code) {
                    int arg = buffer.getInt(i + 1);

                    file.println(opCode.toString() + " " + arg);
                    i += 5;
                }
                else if (op == OpCode.ld_const.code) {
                    double value = buffer.getDouble(i + 1);
                    file.println(opCode.toString() + " " + value);
                    i += 9;
                }
                else {
                    file.println(opCode.toString());
                    i++;
                }
            }
        }
        catch (IOException e) { }
    }
}
