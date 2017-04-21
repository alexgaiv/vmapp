package com.alexgaiv.vmserver.parser;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

enum TokenType
{
    T_NULL,
    T_NOT("!",    OpCode.not),
    T_ASSIGN("=", OpCode.store),

    // compare operators
    T_EQUAL("==",    OpCode.eq),
    T_NOTEQUAL("!=", OpCode.noteq),
    T_LESS("<",      OpCode.lss),
    T_GREATER(">",   OpCode.grt),
    T_LESSEQ("<=",   OpCode.lsseq),
    T_GREATEQ(">=",  OpCode.grteq),

    // second order operators
    T_PLUS("+",  OpCode.add),
    T_MINUS("-", OpCode.sub),
    T_OR("||",   OpCode.or),

    // first order operators
    T_ASTERISK("*", OpCode.mul),
    T_SLASH("/",    OpCode.div),
    T_AND("&&",     OpCode.and),

    // data types
    T_REAL,
    T_STRING,

    T_PRINT,
    T_PRINTLN,
    T_SQRT,

    T_IF,
    T_ELSE,
    T_WHILE,
    T_LPAREN,
    T_RPAREN,
    T_LBRACKET,
    T_RBRACKET,
    T_LBRACE,
    T_RBRACE,
    T_COMMA,
    T_SEMICOLON,
    T_NUMBER,
    T_STR_LITERAL,
    T_ID,
    T_EOF;

    private String opSymbol = null;
    private OpCode opCode = null;

    TokenType() { }

    TokenType(String opSymbol, OpCode opCode) {
        this.opSymbol = opSymbol;
        this.opCode = opCode;
    }

    String getOperatorSymbol() { return opSymbol != null ? opSymbol : ""; }

    public OpCode getOpCode() { return opCode; }

    boolean isBooleanOperator() {
        return this == TokenType.T_AND || this == TokenType.T_OR || this == TokenType.T_NOT;
    }

    int getOperatorPriority()
    {
        if (this == T_ASTERISK || this == T_SLASH)
            return 1;
        if (this == T_PLUS || this == T_MINUS)
            return 2;
        else if (compareTo(TokenType.T_EQUAL) >= 0 && compareTo(TokenType.T_GREATEQ) <= 0)
            return 3;
        if (this == T_AND || this == T_OR)
            return 4;

        return -1;
    }

    boolean isDataType() {
        return this == TokenType.T_REAL || this == TokenType.T_STRING;
    }
}

class Token
{
    TokenType type;
    double realValue;
    int stringId;
    Identifier identifier;

    Token(TokenType type) { this.type = type; }

    Token(TokenType type, int stringId) {
        this.type = type;
        this.stringId = stringId;
    }

    Token(TokenType type, double value) {
        this.type = type;
        this.realValue = value;
    }

    Token(TokenType type, Identifier identifier) {
        this.type = type;
        this.identifier = identifier;
    }
}

class UnexpectedTokenException extends ProgramParseException
{
    UnexpectedTokenException(String tokenString, int lineno) {
        super("Unexpected token " + tokenString, lineno);
    }
}

class ProgramTokenizer
{
    private StreamTokenizer tz;
    private IdentifiersTable identifiers;
    private HashMap<Integer, String> stringTable;
    private int lastStringId;
    private final static HashMap<String, TokenType> keyword2token;
    private final static HashMap<Character, TokenType> delim2token;

    static {
        keyword2token = new HashMap<>();
        delim2token = new HashMap<>();

        String[] keywords = {
            "real", "string", "if", "else", "while", "print", "println", "sqrt"
        };

        char[] delims = {
                '=', '<', '>', '+', '-', '*', '/', '!', '(', ')', '[', ']', '{', '}', ',', ';'
        };

        TokenType[] keywordsTokens = {
                TokenType.T_REAL,
                TokenType.T_STRING,
                TokenType.T_IF,
                TokenType.T_ELSE,
                TokenType.T_WHILE,
                TokenType.T_PRINT,
                TokenType.T_PRINTLN,
                TokenType.T_SQRT
        };

        TokenType[] delimsTokens = {
                TokenType.T_ASSIGN,
                TokenType.T_LESS,
                TokenType.T_GREATER,
                TokenType.T_PLUS,
                TokenType.T_MINUS,
                TokenType.T_ASTERISK,
                TokenType.T_SLASH,
                TokenType.T_NOT,
                TokenType.T_LPAREN,
                TokenType.T_RPAREN,
                TokenType.T_LBRACKET,
                TokenType.T_RBRACKET,
                TokenType.T_LBRACE,
                TokenType.T_RBRACE,
                TokenType.T_COMMA,
                TokenType.T_SEMICOLON
        };

        for (int i = 0; i < keywords.length; i++)
            keyword2token.put(keywords[i], keywordsTokens[i]);
        for (int i = 0; i < delims.length; i++)
            delim2token.put(delims[i], delimsTokens[i]);
    }

    private TokenType lookupKeyword(String keyword) {
        return keyword2token.getOrDefault(keyword, TokenType.T_NULL);
    }
    private TokenType lookupDelim(char delim) {
        return delim2token.getOrDefault(delim, TokenType.T_NULL);
    }

    ProgramTokenizer(String str)
    {
        tz = new StreamTokenizer(new StringReader(str));
        identifiers = new IdentifiersTable();
        stringTable = new HashMap<>();
        lastStringId = 0;

        stringTable.put(lastStringId, "\n");

        tz.parseNumbers();
        tz.slashStarComments(true);
        tz.slashSlashComments(true);
        tz.quoteChar('"');
        tz.wordChars('_', '_');
        tz.ordinaryChar('/');
        tz.ordinaryChar('-');
    }

    int lineno() { return tz.lineno(); }
    HashMap<Integer, String> getStringTable() { return stringTable; }

    Token nextToken() throws IOException, UnexpectedTokenException
    {
        int tokenType = tz.nextToken();
        TokenType tt;

        switch (tokenType)
        {
            case StreamTokenizer.TT_EOF:
                return new Token(TokenType.T_EOF);
            case StreamTokenizer.TT_NUMBER:
                return new Token(TokenType.T_NUMBER, tz.nval);
            case '"':
                int stringId = -1;
                for (Map.Entry<Integer, String> e : stringTable.entrySet()) {
                    if (e.getValue().equals(tz.sval)) {
                        stringId = e.getKey();
                        break;
                    }
                }
                if (stringId == -1) {
                    stringId = ++lastStringId;
                    stringTable.put(stringId, tz.sval);
                }
                return new Token(TokenType.T_STR_LITERAL, stringId);
            case StreamTokenizer.TT_WORD:
                tt = lookupKeyword(tz.sval);
                if (tt != TokenType.T_NULL)
                    return new Token(tt);

                Identifier i = new Identifier(tz.sval);
                i = identifiers.addIfNotExists(i);
                return new Token(TokenType.T_ID, i);
            case '=':
                // note: '= =' is counting as '=='
                tokenType = tz.nextToken();
                if (tokenType == '=')
                    return new Token(TokenType.T_EQUAL);
                tz.pushBack();
                return new Token(TokenType.T_ASSIGN);
            case '!':
                tokenType = tz.nextToken();
                if (tokenType == '=')
                    return new Token(TokenType.T_NOTEQUAL);
                tz.pushBack();
                return new Token(TokenType.T_NOT);
            case '<':
                tokenType = tz.nextToken();
                if (tokenType == '=')
                    return new Token(TokenType.T_LESSEQ);
                tz.pushBack();
                return new Token(TokenType.T_LESS);
            case '>':
                tokenType = tz.nextToken();
                if (tokenType == '=')
                    return new Token(TokenType.T_GREATEQ);
                tz.pushBack();
                return new Token(TokenType.T_GREATER);
            case '&':
                tokenType = tz.nextToken();
                if (tokenType == '&')
                    return new Token(TokenType.T_AND);
                tz.pushBack();
                throw new UnexpectedTokenException("&", tz.lineno());
            case '|':
                tokenType = tz.nextToken();
                if (tokenType == '|')
                    return new Token(TokenType.T_OR);
                tz.pushBack();
                throw new UnexpectedTokenException("|", tz.lineno());
            default:
                tt = lookupDelim((char)tokenType);
                if (tt == TokenType.T_NULL)
                    throw new UnexpectedTokenException(String.valueOf((char)tokenType), tz.lineno());
                return new Token(tt);
        }
    }
}