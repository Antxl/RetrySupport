package com.antxl.utils.retry;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.TimeUnit;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor(staticName = "of")
public final class Interval {
    private final long value;
    private final TimeUnit unit;

    Long getTimeInMs() {
        return unit.toMillis(value);
    }
}
