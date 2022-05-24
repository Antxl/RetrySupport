package com.antxl.utils.retry.event;

public record RetryEvent(Throwable cause, int retriedCount, long currentRunningMs, long lastInterval) {
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
