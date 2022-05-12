package com.oocl.utils.retry.event;

public interface RetryEventListener {
    void preRetry(RetryEvent event) throws BreakRetryException;
}
