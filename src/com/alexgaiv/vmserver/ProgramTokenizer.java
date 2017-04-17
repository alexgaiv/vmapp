package com.alexgaiv.vmserver;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;

enum TokenType
{
    T_NULL,
    T_ASSIGN,
    T_EQUAL,
    T_NOTEQUAL,
    T_LESS,
    T_GREATER,
    T_LESSEQ,
    T_GREATEQ,
    T_PLUS,
    T_MINUS,
    T_OR,
    T_ASTERISK,
    T_SLASH,
    T_AND,
    T_NOT,
    T_INT,
    T_STRING,
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
    T_STR,
    T_ID,
    T_EOF
}

class Token
{
    TokenType type;
    String stringValue;
    double realValue;

    Token() { this.type = TokenType.T_NULL; }
    Token(TokenType type) { this.type = type; }

    Token(TokenType type, String value) {
        this.type = type;
        this.stringValue = value;
    }

    Token(TokenType type, double value) {
        this.type = type;
        this.realValue = value;
    }
}

class UnexpectedTokenException extends Exception
{
    UnexpectedTokenException(String tokenString, int lineNo) {
        super("Unexpected token " + tokenString + " on line" + lineNo);
    }
}

class ProgramTokenizer
{
    private StreamTokenizer tz;
    private HashSet<String> identifiersTable = new HashSet<>();
    private final static HashMap<String, TokenType> keyword2token;
    private final static HashMap<Character, TokenType> delim2token;

    static {
        keyword2token = new HashMap<>();
        delim2token = new HashMap<>();

        String[] keywords = {
                "int", "string", "if", "else", "while"
        };

        char[] delims = {
                '=', '<', '>', '+', '-', '*', '/', '!', '(', ')', '[', ']', '{', '}', ',', ';'
        };

        TokenType[] keywordsTokens = {
                TokenType.T_INT,
                TokenType.T_STRING,
                TokenType.T_IF,
                TokenType.T_ELSE,
                TokenType.T_WHILE
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

    HashSet<String> getIdentifiersTable() {
        return identifiersTable;
    }

    ProgramTokenizer(String str)
    {
        this.tz = new StreamTokenizer(new StringReader(str));

        // configure tokenizer
        tz.parseNumbers();
        tz.slashStarComments(true);
        tz.slashSlashComments(false);
        tz.quoteChar('"');
        tz.wordChars('_', '_');
        tz.ordinaryChar('/');
    }

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
                return new Token(TokenType.T_STR, tz.sval);
            case StreamTokenizer.TT_WORD:
                tt = lookupKeyword(tz.sval);
                if (tt != TokenType.T_NULL)
                    return new Token(tt);

                identifiersTable.add(tz.sval);
                return new Token(TokenType.T_ID, tz.sval); // TODO replace tz.sval by index in identifiersTable
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