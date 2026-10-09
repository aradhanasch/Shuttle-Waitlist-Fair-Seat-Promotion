package com.example.shuttle.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
