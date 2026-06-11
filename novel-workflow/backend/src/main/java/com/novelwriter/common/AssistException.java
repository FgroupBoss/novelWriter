package com.novelwriter.common;

public class AssistException extends RuntimeException {

    public AssistException(String message) {
        super(message);
    }

    public AssistException(String message, Throwable cause) {
        super(message, cause);
    }
}
