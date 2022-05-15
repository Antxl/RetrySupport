package com.antxl.utils.retry;

final class BrokenRetryException extends RuntimeException {
    BrokenRetryException(Throwable cause) {
        super("Retry was cancelled intentionally", cause);
    }
}
