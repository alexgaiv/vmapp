package com.alexgaiv.vmserver.parser;

import java.util.HashMap;

enum VariableType
{
    VT_NOT_SET(""),
    VT_REAL("real"),
    VT_STRING("string"),
    VT_ARRAY("array"),
    VT_BOOL("bool");

    private String typeString;

    VariableType(String typeString) { this.typeString = typeString; }

    @Override
    public String toString() { return typeString; }
}

class Variable
{
    VariableType type, arrayElementType;
    int size;
    int address;
    Identifier identifier;
    int prevAssignedVarIndex;
    int scopeFlagIndex;

    static Variable getScopeFlag() {
        return new Variable(null, null, -1);
    }

    boolean isScopeFlag() { return identifier == null; }

    Variable(VariableType type, Identifier identifier, int prevAssignedVarIndex)
    {
        this.type = type;
        this.identifier = identifier;
        this.prevAssignedVarIndex = prevAssignedVarIndex;
        this.scopeFlagIndex = -1;
        this.size = 1;
        this.address = 0;
    }
}

class Identifier
{
    String name;
    int assignedVarIndex;

    Identifier(String name) {
        this.name = name;
        this.assignedVarIndex = -1;
    }

    boolean isVariableAssigned() {
        return assignedVarIndex != -1;
    }

    @Override
    public boolean equals(Object i) {
        return i instanceof Identifier && name.equals(((Identifier) i).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}


class IdentifiersTable
{
    private HashMap<Identifier, Identifier> identifiers = new HashMap<>();

    Identifier addIfNotExists(Identifier i) {
        Identifier i2 = identifiers.get(i);
        if (i2 == null) {
            identifiers.put(i, i);
            return i;
        }
        return i2;
    }
}