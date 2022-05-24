package com.antxl.utils.retry;

import java.util.function.LongFunction;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNullElseGet;

public record RetryOptions(Class<? extends Throwable> retryFor, Integer maxRetryCount, 
                           LongFunction<Interval> nextInterval, Long executionTimeOut, 
                           Predicate<Throwable> releaseLockIndicator) {
    public static final RetryOptions DEFAULT = new RetryOptions();

    private RetryOptions() {
        this(Throwable.class, null, null, null, t -> false);
    }

    public boolean shouldRetryFor(Throwable t) {
        return retryFor.isInstance(t);
    }

    boolean shouldPreformRetry(int currentRetryCount, long currentExecutionTime) {
        return shouldPreformRetryRegardingTimes(currentRetryCount) && shouldPreformRetryRegardingDuration(currentExecutionTime);
    }

    private boolean shouldPreformRetryRegardingTimes(int currentRetryCount) {
        return maxRetryCount == null || currentRetryCount < maxRetryCount;
    }

    private boolean shouldPreformRetryRegardingDuration(long currentExecutionTime) {
        return executionTimeOut == null || currentExecutionTime < executionTimeOut;
    }

    long preformWait(long lastIntervalInMs, Throwable t) {
        Interval interval;
        if (nextInterval == null || (interval = nextInterval.apply(lastIntervalInMs)) == null)
            return 0;
        try {
            if (releaseLockIndicator.test(t))
                interval.unit().timedWait(this, interval.value());
            else
                interval.unit().sleep(interval.value());
        } catch (InterruptedException ignored) {}
        return interval.getTimeInMs();
    }

    public record Builder(RetryOptions options) {
        public Builder() {
            this(DEFAULT);
        }

        private Builder(RetryOptions options, Class<? extends Throwable> exceptionClass) {
            this(new RetryOptions(exceptionClass, options.maxRetryCount, options.nextInterval, options.executionTimeOut, options.releaseLockIndicator));
        }

        private Builder(RetryOptions options, Integer maxRetryCount) {
            this(new RetryOptions(options.retryFor, maxRetryCount, options.nextInterval, options.executionTimeOut, options.releaseLockIndicator));
        }

        private Builder(RetryOptions options, LongFunction<Interval> intervalIter) {
            this(new RetryOptions(options.retryFor, options.maxRetryCount, intervalIter, options.executionTimeOut, options.releaseLockIndicator));
        }

        private Builder(RetryOptions options, Interval maxExecution) {
            this(new RetryOptions(options.retryFor, options.maxRetryCount, options.nextInterval,
                    maxExecution == null ? null : maxExecution.getTimeInMs(), options.releaseLockIndicator));
        }

        private Builder(RetryOptions options, Predicate<Throwable> maxExecution) {
            this(new RetryOptions(options.retryFor, options.maxRetryCount, options.nextInterval, options.executionTimeOut, maxExecution));
        }

        public Builder retryFor(Class<? extends Throwable> exceptionClass) {
            if (exceptionClass == null || exceptionClass.equals(options.retryFor))
                return this;
            return new Builder(options, exceptionClass);
        }

        public Builder maxRetry(int count) {
            if (count <= 0 && options.maxRetryCount == null)
                return this;
            if (count > 0 && options.maxRetryCount != null && count == options.maxRetryCount)
                return this;
            return new Builder(options, count > 0 ? count : null);
        }

        public Builder interval(Interval interval) {
            if (interval == null && options.nextInterval == null)
                return this;
            return interval(o -> interval);
        }

        public Builder interval(LongFunction<Interval> intervalIter) {
            if (intervalIter == null && options.nextInterval == null)
                return this;
            return new Builder(options, intervalIter);
        }

        public Builder executionTimeOut(Interval interval) {
            if (interval == null && options.executionTimeOut == null)
                return this;
            if (interval != null && interval.getTimeInMs().equals(options.executionTimeOut))
                return this;
            return new Builder(options, interval);
        }

        public Builder shouldReleaseLock(boolean shouldReleaseLock) {
            return shouldReleaseLock(t -> shouldReleaseLock);
        }

        public Builder shouldReleaseLock(Predicate<Throwable> shouldReleaseLock) {
            return new Builder(options, requireNonNullElseGet(shouldReleaseLock, () -> (Throwable t) -> false));
        }

        public RetryOptions build() {
            return options;
        }
    }
}
