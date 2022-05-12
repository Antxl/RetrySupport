package com.oocl.utils.retry;

import org.apache.commons.lang3.Validate;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class Interval {
    private final long value;
    private final TimeUnit unit;

    private Interval(long value, TimeUnit unit) {
        Validate.isTrue(value > 0, "0 or negative time is not allowed");
        Validate.notNull(unit, "Time unit cannot be null");
        this.value = value;
        this.unit = unit;
    }

    public long getValue() {
        return value;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    Long getTimeInMs() {
        return unit.toMillis(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Interval interval = (Interval) o;
        return value == interval.value && unit == interval.unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, unit);
    }

    public static Interval of(long interval, TimeUnit unit) {
        return new Interval(interval, unit);
    }
}
