package com.alexgaiv.vmserver.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

class ProgramParseException extends Exception
{
    ProgramParseException(String message, int lineno) {
        super(String.format("[line %d] %s", lineno, message));
    }
}

class ProgramParser
{
    private Bytecode bytecode;
    private Token token;
    private ProgramTokenizer tokenizer;
    private ArrayList<Variable> variables;
    private VariableType expressionType;
    private int scopeFlagIndex;
    private int stackPointer;

    private final static String wrongArgsTypesMessage =
            "Wrong argument types for operator `%s`:\nRequired (%s, %s), got (%s, %s)";
    private final static String wrongArgTypeMessage =
            "Wrong argument type for operator `%s`:\nRequired %s, got %s";

    Bytecode getBytecode() { return bytecode; }
    HashMap<Integer, String> getStringTable() { return tokenizer.getStringTable(); }

    void parse(String program) throws ProgramParseException
    {
        bytecode = new Bytecode();
        tokenizer = new ProgramTokenizer(program);
        variables = new ArrayList<>();
        expressionType = VariableType.VT_NOT_SET;
        scopeFlagIndex = -1;
        stackPointer = 0;

        try {
            nextToken();
            PROGRAM();
        }
        catch (IOException e) {
            throw new ProgramParseException(e.getMessage(), tokenizer.lineno());
        }
    }

    private void nextToken() throws IOException, UnexpectedTokenException
    { token = tokenizer.nextToken(); }

    private void parseError() throws UnexpectedTokenException {
        throw new UnexpectedTokenException(token.type.toString(), tokenizer.lineno());
    }

    private void expectToken(TokenType tt) throws UnexpectedTokenException {
        if (token.type != tt) parseError();
    }

    private boolean isVarInScope(Identifier identifier) {
        return identifier.assignedVarIndex > scopeFlagIndex;
    }

    private Variable newVariable(VariableType type, Identifier identifier)
            throws ProgramParseException
    {
        if (isVarInScope(identifier))
            throw new ProgramParseException(
                "Variable " + identifier.name +
                " is already declared in current scope", tokenizer.lineno());

        Variable v = new Variable(type, identifier, identifier.assignedVarIndex);
        v.address = stackPointer;
        v.scopeFlagIndex = scopeFlagIndex;

        stackPointer += v.size;

        int i = variables.size();
        variables.add(v);
        identifier.assignedVarIndex = i;
        return v;
    }

    private Variable newArray(int size, VariableType elementType, Identifier identifier)
            throws ProgramParseException
    {
        if (isVarInScope(identifier))
            throw new ProgramParseException(
                "Variable " + identifier.name +
                " is already declared in current scope", tokenizer.lineno());

        Variable v = new Variable(VariableType.VT_ARRAY, identifier, identifier.assignedVarIndex);
        v.address = stackPointer;
        v.arrayElementType = elementType;
        v.scopeFlagIndex = scopeFlagIndex;

        v.size = size + 1;
        stackPointer += v.size;

        bytecode.putValue(size);
        bytecode.put(OpCode.store, v.address);

        int i = variables.size();
        variables.add(v);
        identifier.assignedVarIndex = i;
        return v;
    }

    private void enterScope() {
        scopeFlagIndex = variables.size();
        variables.add(Variable.getScopeFlag());
    }

    private void exitScope()
    {
        int i = variables.size();
        int size = 0;
        while (i-- > 0)
        {
            Variable v = variables.get(i);
            if (v.isScopeFlag()) {
                variables.remove(i);
                if (i >= 1)
                    scopeFlagIndex = variables.get(i - 1).scopeFlagIndex;
                else scopeFlagIndex = -1;
                break;
            }
            v.identifier.assignedVarIndex = v.prevAssignedVarIndex;
            size += v.size;
            variables.remove(i);
        }

        if (size != 0) {
            stackPointer -= size;
            bytecode.put(OpCode.addsp, size);
        }
    }

    private void checkArgumentType(VariableType type, TokenType op)
        throws ProgramParseException
    {
        if (op.isBooleanOperator())
        {
            if (type != VariableType.VT_BOOL) {
                String message = String.format(wrongArgTypeMessage, op.getOperatorSymbol(),
                        "bool", type.toString());
                throw new ProgramParseException(message, tokenizer.lineno());
            }
        }
        else
            if (type != VariableType.VT_REAL) {
                String message = String.format(wrongArgTypeMessage, op.getOperatorSymbol(),
                        "real", type.toString());
                throw new ProgramParseException(message, tokenizer.lineno());
            }
    }

    private void checkArgumentsTypes(VariableType leftType, VariableType rightType, TokenType op)
        throws ProgramParseException
    {
        if (leftType != rightType)
        {
            String message;
            if (op == TokenType.T_ASSIGN) {
                message = String.format("Cannot assign expression of type `%s` to variable of type `%s`",
                    rightType.toString(), leftType.toString());
            }
            else {
                String requiredType = op.isBooleanOperator() ? "bool" : "real";
                message = String.format(
                    wrongArgsTypesMessage,
                    op.getOperatorSymbol(), requiredType, requiredType, leftType, rightType);
            }

            throw new ProgramParseException(message, tokenizer.lineno());
        }
    }

    private void processDeclaration()
        throws IOException, ProgramParseException
    {
        boolean varDeclaration = token.type.isDataType();

        int sp = 0, l = 0;
        if (varDeclaration) {
            sp = stackPointer;
            bytecode.put(OpCode.subsp);
            l = bytecode.size();
            bytecode.putInt(0);
        }

        while (token.type.isDataType()) {
            VAR_DECL();
        }

        if (varDeclaration) {
            int varSize = stackPointer - sp;
            bytecode.putInt(l, varSize);
        }
    }

    private void PROGRAM() throws IOException, ProgramParseException
    {
        if (token.type == TokenType.T_LBRACE) {
            while (token.type != TokenType.T_EOF) {
                CODE();
            }
        }
        else {
            enterScope();
            processDeclaration();

            while (token.type != TokenType.T_EOF) {
                CODE2();
            }
            exitScope();
        }
    }

    private void CODE() throws IOException, ProgramParseException
    {
        enterScope();
        nextToken();
        processDeclaration();

        while (token.type != TokenType.T_RBRACE) {
            CODE2();
            if (token.type == TokenType.T_EOF) parseError();
        }
        nextToken();
        exitScope();
    }

    private void CODE2() throws IOException, ProgramParseException
    {
        if (token.type == TokenType.T_SEMICOLON) {
            do {
                nextToken();
            } while (token.type == TokenType.T_SEMICOLON);
        }
        else if (token.type == TokenType.T_LBRACE) {
            CODE();
        }
        // COND (jmpz l1) STM1 (jmp l2) [l1] STM2 [l2]
        // COND (jmpz l1) STM1 [l1]
        else if (token.type == TokenType.T_IF)
        {
            nextToken();
            expectToken(TokenType.T_LPAREN);
            nextToken();
            EXPR();
            expectToken(TokenType.T_RPAREN);

            if (expressionType != VariableType.VT_BOOL) {
                throw new ProgramParseException("Condition must have boolean type", tokenizer.lineno());
            }

            bytecode.put(OpCode.jmpz);
            int l1 = bytecode.putLabel();

            nextToken();
            CODE2();

            if (token.type == TokenType.T_ELSE)
            {
                bytecode.put(OpCode.jmp);
                int l2 = bytecode.putLabel();
                bytecode.markLabel(l1);

                nextToken();
                CODE2();

                bytecode.markLabel(l2);
            }
            else {
                bytecode.markLabel(l1);
            }
        }
        // [l0] COND (jmpz l1) STM (jmp l0) [l1]
        else if (token.type == TokenType.T_WHILE) {
            nextToken();
            expectToken(TokenType.T_LPAREN);
            nextToken();

            int l0 = bytecode.size();
            EXPR();
            expectToken(TokenType.T_RPAREN);

            if (expressionType != VariableType.VT_BOOL) {
                throw new ProgramParseException("Condition must have boolean type", tokenizer.lineno());
            }

            bytecode.put(OpCode.jmpz);
            int l1 = bytecode.putLabel();

            nextToken();
            CODE2();

            bytecode.put(OpCode.jmp, l0);
            bytecode.markLabel(l1);

        }
        else if (token.type == TokenType.T_PRINT || token.type == TokenType.T_PRINTLN)
        {
            TokenType tt = token.type;
            nextToken();
            EXPR();
            expectToken(TokenType.T_SEMICOLON);

            switch (expressionType)
            {
                case VT_REAL:
                case VT_BOOL:
                    bytecode.put(OpCode.print_real);
                    break;
                case VT_STRING:
                    bytecode.put(OpCode.print_str);
                    break;
                case VT_ARRAY:
                    bytecode.put(OpCode.print_arr);
                    break;
                default:
                    throw new ProgramParseException(
                        "`Out` argument must be real, boolean, string or array", tokenizer.lineno());
            }

            if (tt == TokenType.T_PRINTLN) {
                bytecode.putValue(0.0);
                bytecode.put(OpCode.print_str);
            }

            expressionType = VariableType.VT_NOT_SET;
            nextToken();
        }
        else {
            EXPR();
            expectToken(TokenType.T_SEMICOLON);
            nextToken();
        }
    }

    private void VAR_DECL() throws IOException, ProgramParseException
    {
        VariableType dataType = token.type == TokenType.T_REAL ? VariableType.VT_REAL : VariableType.VT_STRING;

        nextToken();
        VAR_DECL2(dataType);
        while (token.type == TokenType.T_COMMA) {
            nextToken();
            VAR_DECL2(dataType);
        }

        expectToken(TokenType.T_SEMICOLON);
        nextToken();
    }

    private void VAR_DECL2(VariableType dataType) throws IOException, ProgramParseException
    {
        expectToken(TokenType.T_ID);
        Identifier identifier = token.identifier;
        nextToken();

        if (token.type == TokenType.T_LBRACKET) {
            nextToken();
            expectToken(TokenType.T_NUMBER);

            int arraySize = (int)token.realValue;
            if (token.realValue != arraySize) {
                throw new ProgramParseException("Array size must be an integer", tokenizer.lineno());
            }
            if (arraySize <= 0) {
                throw new ProgramParseException("Array size must be greater than zero", tokenizer.lineno());
            }

            nextToken();
            expectToken(TokenType.T_RBRACKET);
            nextToken();

            newArray(arraySize, dataType, identifier);
        }
        else {
            newVariable(dataType, identifier);
            Variable variable = variables.get(identifier.assignedVarIndex);

            if (token.type == TokenType.T_ASSIGN) {
                nextToken();
                EXPR();

                checkArgumentsTypes(dataType, expressionType, TokenType.T_ASSIGN);
                bytecode.put(OpCode.store, variable.address);
            }
        }
    }

    private void EXPR() throws IOException, ProgramParseException
    {
        EXPR2();
        TokenType tt = token.type;
        VariableType leftExpressionType = expressionType;

        while (tt.getOperatorPriority() == 4) {
            nextToken();
            EXPR2();
            checkArgumentsTypes(leftExpressionType, expressionType, tt);
            bytecode.put(tt.getOpCode());

            expressionType = VariableType.VT_BOOL;
            tt = token.type;
        }
    }

    private void EXPR2() throws IOException, ProgramParseException
    {
        EXPR3();
        TokenType tt = token.type;
        VariableType leftExpressionType = expressionType;

        while (tt.getOperatorPriority() == 3) {
            nextToken();
            EXPR3();
            checkArgumentsTypes(leftExpressionType, expressionType, tt);
            bytecode.put(tt.getOpCode());

            expressionType = VariableType.VT_BOOL;
            tt = token.type;
        }
    }

    private void EXPR3() throws IOException, ProgramParseException
    {
        EXPR4();
        TokenType tt = token.type;
        VariableType leftExpressionType = expressionType;

        while (tt.getOperatorPriority() == 2) {
            nextToken();
            EXPR4();
            checkArgumentsTypes(leftExpressionType, expressionType, tt);
            bytecode.put(tt.getOpCode());

            expressionType = tt.isBooleanOperator() ? VariableType.VT_BOOL : VariableType.VT_REAL;
            tt = token.type;
        }
    }

    private void EXPR4() throws IOException, ProgramParseException
    {
        F();
        TokenType tt = token.type;
        VariableType leftExpressionType = expressionType;

        while (tt.getOperatorPriority() == 1) {
            nextToken();
            F();
            checkArgumentsTypes(leftExpressionType, expressionType, tt);
            bytecode.put(tt.getOpCode());

            expressionType = tt.isBooleanOperator() ? VariableType.VT_BOOL : VariableType.VT_REAL;
            tt = token.type;
        }
    }

    private void F() throws IOException, ProgramParseException
    {
        switch (token.type)
        {
            case T_NOT:
                nextToken();
                F();
                checkArgumentType(expressionType, TokenType.T_NOT);
                bytecode.put(OpCode.not);
                break;
            case T_MINUS:
                nextToken();
                F();
                checkArgumentType(expressionType, TokenType.T_MINUS);
                bytecode.put(OpCode.neg);
                break;
            case T_PLUS:
                nextToken();
                F();
                checkArgumentType(expressionType, TokenType.T_PLUS);
                break;
            case T_NUMBER:
                bytecode.putValue(token.realValue);
                nextToken();
                expressionType = VariableType.VT_REAL;
                break;
            case T_STR_LITERAL:
                bytecode.putValue(token.stringId);
                nextToken();
                expressionType = VariableType.VT_STRING;
                break;
            case T_LPAREN:
                nextToken();
                EXPR();
                expectToken(TokenType.T_RPAREN);
                nextToken();
                break;
            case T_SQRT:
                nextToken();
                expectToken(TokenType.T_LPAREN);
                nextToken();
                EXPR();
                expectToken(TokenType.T_RPAREN);

                bytecode.put(OpCode.sqrt);
                expressionType = VariableType.VT_REAL;
                nextToken();
                break;
            case T_ID:
                Identifier identifier = token.identifier;
                if (!identifier.isVariableAssigned()) {
                    throw new ProgramParseException(
                            "Undeclared identifier " + identifier.name, tokenizer.lineno());
                }
                Variable variable = variables.get(identifier.assignedVarIndex);

                nextToken();
                if (token.type == TokenType.T_ASSIGN) {
                    nextToken();
                    EXPR();
                    checkArgumentsTypes(variable.type, expressionType, TokenType.T_ASSIGN);
                    bytecode.put(OpCode.store, variable.address);
                    expressionType = VariableType.VT_NOT_SET;
                }
                else if (token.type == TokenType.T_LBRACKET) {
                    nextToken();
                    EXPR();
                    expectToken(TokenType.T_RBRACKET);
                    nextToken();

                    if (expressionType != VariableType.VT_REAL) {
                        throw new ProgramParseException("Array index must be an number", tokenizer.lineno());
                    }

                    if (token.type == TokenType.T_ASSIGN) {
                        nextToken();
                        EXPR();
                        checkArgumentsTypes(variable.arrayElementType, expressionType, TokenType.T_ASSIGN);
                        bytecode.put(OpCode.st_arr, variable.address);
                        expressionType = VariableType.VT_NOT_SET;
                    }
                    else {
                        if (variable.arrayElementType == VariableType.VT_REAL) {
                            bytecode.put(OpCode.ld_arr, variable.address);
                        }
                        expressionType = variable.arrayElementType;
                    }
                }
                else {
                    if (variable.type == VariableType.VT_ARRAY) {
                        bytecode.putValue((double)variable.address);
                    }
                    else {
                        bytecode.put(OpCode.load, variable.address);
                    }
                    expressionType = variable.type;
                }
                break;
            default:
                parseError();
        }
    }
}
