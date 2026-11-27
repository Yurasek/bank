package org.example.exception;

public class TokenReuseDetectedException extends RuntimeException{
    public TokenReuseDetectedException(String message) {
        super(message);
    }
}
