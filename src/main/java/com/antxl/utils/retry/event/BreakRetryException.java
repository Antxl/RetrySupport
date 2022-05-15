package com.antxl.utils.retry.event;

public class BreakRetryException extends Exception {
    public BreakRetryException(String message) {
        super(message);
    }

    public BreakRetryException(String message, Throwable cause) {
        super(message, cause);
    }
}
