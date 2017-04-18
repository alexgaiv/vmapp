package com.alexgaiv.vmserver;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

class ProgramParseException extends Exception
{
    ProgramParseException(String message) { super(message); }
}

public class ProgramParser
{
    private Token token;
    private ProgramTokenizer tokenizer;

    private void nextToken() throws IOException, UnexpectedTokenException
    { token = tokenizer.nextToken(); }

    void parse(String program) throws ProgramParseException
    {
        tokenizer = new ProgramTokenizer(program);

        try {
            nextToken();
            PROGRAM();
        }
        catch (IOException | UnexpectedTokenException e) {
            throw new ProgramParseException(e.getMessage());
        }
    }

    private void parseError() throws UnexpectedTokenException {
        throw new UnexpectedTokenException(token.type.toString(), tokenizer.lineno());
    }

    private void expectToken(TokenType tt) throws UnexpectedTokenException {
        if (token.type != tt) parseError();
    }

    private void PROGRAM() throws IOException, UnexpectedTokenException
    {
        if (token.type == TokenType.T_LBRACE) {
            while (token.type != TokenType.T_EOF) {
                CODE();
            }
        }
        else {
            while (Token.isDataType(token.type)) {
                VAR_DECL();
            }
            while (token.type != TokenType.T_EOF) {
                CODE2();
            }
        }
    }

    private void CODE() throws IOException, UnexpectedTokenException
    {
        nextToken();

        while (Token.isDataType(token.type)) {
            VAR_DECL();
        }

        while (token.type != TokenType.T_RBRACE) {
            CODE2();
            if (token.type == TokenType.T_EOF) parseError();
        }
        nextToken();
    }

    private void CODE2() throws IOException, UnexpectedTokenException
    {
        if (token.type == TokenType.T_SEMICOLON) {
            do {
                nextToken();
            } while (token.type == TokenType.T_SEMICOLON);
        }
        else if (token.type == TokenType.T_LBRACE) {
            CODE();
        }
        else if (token.type == TokenType.T_IF) {
            nextToken();
            expectToken(TokenType.T_LPAREN);
            nextToken();
            EXPR();
            expectToken(TokenType.T_RPAREN);
            nextToken();
            CODE2();

            if (token.type == TokenType.T_ELSE) {
                nextToken();
                CODE2();
            }
        }
        else if (token.type == TokenType.T_WHILE) {
            nextToken();
            expectToken(TokenType.T_LPAREN);
            nextToken();
            EXPR();
            expectToken(TokenType.T_RPAREN);
            nextToken();
            CODE2();

        }
        else {
            EXPR();
            expectToken(TokenType.T_SEMICOLON);
            nextToken();
        }
    }

    private void VAR_DECL() throws IOException, UnexpectedTokenException
    {
        nextToken();

        VAR_DECL2();
        while (token.type == TokenType.T_COMMA) {
            nextToken();
            VAR_DECL2();
        }

        expectToken(TokenType.T_SEMICOLON);
        nextToken();
    }

    private void VAR_DECL2() throws IOException, UnexpectedTokenException
    {
        expectToken(TokenType.T_ID);
        nextToken();

        if (token.type == TokenType.T_ASSIGN) {
            nextToken();
            EXPR();
        }
        else if (token.type == TokenType.T_LBRACKET) {
            nextToken();
            expectToken(TokenType.T_NUMBER);
            nextToken();
            expectToken(TokenType.T_RBRACKET);
            nextToken();

            if (token.type == TokenType.T_ASSIGN) {
                nextToken();
                ARR_INIT();
            }
        }
    }

    private void ARR_INIT() throws IOException, UnexpectedTokenException
    {
        expectToken(TokenType.T_LBRACE);
        nextToken();
        if (token.type == TokenType.T_RBRACE) return;

        EXPR();
        while (token.type == TokenType.T_COMMA) {
            nextToken();
            EXPR();
        }
        expectToken(TokenType.T_RBRACE);
        nextToken();
    }

    private void EXPR() throws IOException, UnexpectedTokenException
    {
        EXPR2();
        while (Token.isCompareOperator(token.type)) {
            nextToken();
            EXPR2();
        }
    }

    private void EXPR2() throws IOException, UnexpectedTokenException
    {
        S();
        while (Token.isSecondOrderOperator(token.type)) {
            nextToken();
            S();
        }
    }

    private void S() throws IOException, UnexpectedTokenException
    {
        F();
        while (Token.isFirstOrderOperator(token.type)) {
            nextToken();
            F();
        }
    }

    private void F() throws IOException, UnexpectedTokenException
    {
        switch (token.type)
        {
            case T_NOT:
                nextToken();
                F();
                break;
            case T_MINUS:
                nextToken();
                F();
                break;
            case T_NUMBER:
                nextToken();
                break;
            case T_STR_LITERAL:
                nextToken();
                break;
            case T_LPAREN:
                nextToken();
                EXPR();
                expectToken(TokenType.T_RPAREN);
                nextToken();
                break;
            case T_ID:
                nextToken();
                if (token.type == TokenType.T_ASSIGN) {
                    nextToken();
                    EXPR();
                }
                else if (token.type == TokenType.T_LBRACKET) {
                    nextToken();
                    EXPR();
                    expectToken(TokenType.T_RBRACKET);
                    nextToken();

                    if (token.type == TokenType.T_ASSIGN) {
                        nextToken();
                        EXPR();
                    }
                }
                break;
            default:
                parseError();
        }
    }

    public static void main(String[] args)
    {
        ProgramParser parser = new ProgramParser();

        String programText = "";
        try {
            programText = new String(Files.readAllBytes(Paths.get("sample.txt")), Charset.defaultCharset());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            parser.parse(programText);
        }
        catch (ProgramParseException e) {
            System.out.println(e.getMessage());
        }
    }
}
