package com.oocl.utils.retry.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RetryEvent {
    private final Throwable cause;
    private final int retriedCount;
    private final long currentRunningMs;
    private final long lastInterval;

    @Override
    public String toString() {
        return "RetryEvent: {" +
                "cause: [" + cause.getClass().getSimpleName() + ": " + cause.getMessage() +
                "], retried count: " + retriedCount +
                ", current running time: " + currentRunningMs + "ms" +
                ", last waiting interval: " + lastInterval +
                "ms}";
    }
}
