package org.apache.accumulo.access;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

class Parser {
    enum TokenType {
        AND_OP,
        OR_OP,
        ACCESS_TOKEN,
        LPAREN,
        RPAREN
    }


    static class Tokenizer implements Iterator<TokenType> {

        PushbackReader reader;

        TokenType current;

        Tokenizer(String expression) {
            reader = new PushbackReader(new StringReader(expression));
        }

        private int nextChar(){
            try {
                return  reader.read();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean hasNext(){
            if(current != null){
                return true;
            }

            int nextChar = nextChar();

            if(nextChar == -1) {
                return false;
            }

            switch (nextChar) {
                case '&':
                    current = TokenType.AND_OP;
                    break;
                case '|':
                    current = TokenType.OR_OP;
                    break;
                case '(':
                    current = TokenType.LPAREN;
                    break;
                case ')':
                    current = TokenType.RPAREN;
                    break;
                case '"':
                    readQuotedAccessToken();
                    current = TokenType.ACCESS_TOKEN;
                    break;
                default:
                    readAccessToken(nextChar);
                    current = TokenType.ACCESS_TOKEN;
                    break;

            }

            return true;
        }


        private byte asByte(int c) {
            if(c <0 || c >= 128) {
                throw new IllegalArgumentException();
            }
            return (byte)c;
        }

        private void readAccessToken(int nextChar) {
            while(AccessExpressionImpl.isValidAuthChar(asByte(nextChar))) {
                nextChar = nextChar();
                if(nextChar == -1){
                    return;
                }
            }

            // push the char back
            try {
                reader.unread(nextChar);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void readQuotedAccessToken() {
            int count = 0;
            while(true) {
                int nextChar = nextChar();

                if(nextChar == -1){
                    throw new IllegalArgumentException("unclosed quote seen");
                }


                switch (nextChar) {
                    case '"':
                        if(count == 0){
                            throw new IllegalArgumentException("empty quote seen");
                        }
                        return;
                    case '\\': {
                        nextChar = nextChar();
                        if (nextChar != '"' && nextChar != '\\') {
                            throw new IllegalArgumentException();
                        }
                    }
                }

                count++;
            }
        }

        @Override
        public TokenType next(){
            if(!hasNext()) {
                throw new NoSuchElementException();
            }

            var tmp = current;
            current = null;
            return tmp;
        }
        TokenType peek(){
            if(!hasNext()) {
                throw new NoSuchElementException();
            }

            return current;
        }
    }



    public static void parseAccessExpression(String expression) {

        Tokenizer tokenizer = new Tokenizer(expression);

        if(!tokenizer.hasNext()) {
            return;
        }

        parseExpression(tokenizer);

        if(tokenizer.hasNext()){
            //not all input was read, so not a valid expression
            throw new IllegalArgumentException("Unconsumed token "+tokenizer.peek());
        }
    }

    private static void parseExpression(Tokenizer tokenizer) {
        if(!tokenizer.hasNext()) {
            throw new IllegalArgumentException("illegal empty expression ");
        }

        switch (tokenizer.peek()) {
            case ACCESS_TOKEN:
                // consume the access token
                tokenizer.next();
                break;
            case LPAREN:
                parseParenExpression(tokenizer);
                break;
            default:
                throw new IllegalArgumentException("illegal token type to start expression "+tokenizer.peek());
        }

        if(tokenizer.hasNext()) {
            switch (tokenizer.peek()) {
                case AND_OP:
                    parseAndExpression(tokenizer);
                    break;
                case OR_OP:
                    parseOrExpression(tokenizer);
                    break;
            }
        }
    }

    private static void parseParenExpression(Tokenizer tokenizer) {
        if(!tokenizer.hasNext() || tokenizer.next() != TokenType.LPAREN){
            throw new IllegalArgumentException();
        }

        parseExpression(tokenizer);

        if(!tokenizer.hasNext() || tokenizer.next() != TokenType.RPAREN){
            // TODO cant include token in error because next called above
            throw new IllegalArgumentException("illegal token at end of paren expression");
        }
    }

    private static void parseAndExpression(Tokenizer tokenizer) {
        if(!tokenizer.hasNext() || tokenizer.next() != TokenType.AND_OP){
            throw new IllegalArgumentException();
        }

        switch (tokenizer.peek()) {
            case ACCESS_TOKEN:
                // consume the access token
                tokenizer.next();
                break;
            case LPAREN:
                parseParenExpression(tokenizer);
                break;
            default:
                throw new IllegalArgumentException("illegal token following an and operator "+tokenizer.peek());
        }

        if(tokenizer.hasNext() && tokenizer.peek() == TokenType.AND_OP) {
            parseAndExpression(tokenizer);
        }
    }

    private static void parseOrExpression(Tokenizer tokenizer) {
        if(!tokenizer.hasNext() || tokenizer.next() != TokenType.OR_OP){
            throw new IllegalArgumentException();
        }

        switch (tokenizer.peek()) {
            case ACCESS_TOKEN:
                // consume the access token
                tokenizer.next();
                break;
            case LPAREN:
                parseParenExpression(tokenizer);
                break;
            default:
                throw new IllegalArgumentException("illegal token following an or operator "+tokenizer.peek());
        }

        if(tokenizer.hasNext() && tokenizer.peek() == TokenType.OR_OP) {
            parseOrExpression(tokenizer);
        }
    }
}
