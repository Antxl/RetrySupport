package com.antxl.utils.retry.event;

public interface RetryEventListener {
    void preRetry(RetryEvent event) throws BreakRetryException;
}
