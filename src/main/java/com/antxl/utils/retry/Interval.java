package com.antxl.utils.retry;

import org.apache.commons.lang3.Validate;

import java.util.concurrent.TimeUnit;

public record Interval(long value, TimeUnit unit) {
    public Interval(long value, TimeUnit unit) {
        Validate.isTrue(value > 0, "Time specified should be positive.");
        Validate.notNull(unit, "Time specified should be positive.");
        this.value = value;
        this.unit = unit;
    }

    public Long getTimeInMs() {
        return unit.toMillis(value);
    }

    public static Interval of(long value, TimeUnit unit) {
        return new Interval(value, unit);
    }
}
