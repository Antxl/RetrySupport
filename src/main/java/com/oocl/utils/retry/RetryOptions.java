package com.oocl.utils.retry;

import java.util.function.LongFunction;
import java.util.function.Predicate;

public class RetryOptions {
    private final Class<? extends Throwable> retryFor;
    private final Integer maxRetryCount;
    private final LongFunction<Interval> nextInterval;
    private final Long executionTimeOut;
    private final Predicate<Throwable> releaseLockIndicator;

    public static final RetryOptions DEFAULT = new RetryOptions();

    private RetryOptions() {
        this(Throwable.class, null, null, null, t -> false);
    }

    private RetryOptions(Class<? extends Throwable> retryFor, Integer maxRetryCount, LongFunction<Interval> nextInterval, Long executionTimeOut, Predicate<Throwable> releaseLockIndicator) {
        this.retryFor = retryFor;
        this.maxRetryCount = maxRetryCount;
        this.nextInterval = nextInterval;
        this.executionTimeOut = executionTimeOut;
        this.releaseLockIndicator = releaseLockIndicator;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public LongFunction<Interval> getNextInterval() {
        return nextInterval;
    }

    public Long getExecutionTimeOut() {
        return executionTimeOut;
    }

    public Predicate<Throwable> getReleaseLockIndicator() {
        return releaseLockIndicator;
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
                interval.getUnit().timedWait(this, interval.getValue());
            else
                interval.getUnit().sleep(interval.getValue());
        } catch (InterruptedException e) {
            //Ignore
        }
        return interval.getTimeInMs();
    }

    public static final class Builder {
        private final RetryOptions options;

        public Builder() {
            options = DEFAULT;
        }

        private Builder(RetryOptions options, Class<? extends Throwable> exceptionClass) {
            this.options = new RetryOptions(exceptionClass, options.maxRetryCount, options.nextInterval, options.executionTimeOut, options.releaseLockIndicator);
        }

        private Builder(RetryOptions options, Integer maxRetryCount) {
            this.options = new RetryOptions(options.retryFor, maxRetryCount, options.nextInterval, options.executionTimeOut, options.releaseLockIndicator);
        }

        private Builder(RetryOptions options, LongFunction<Interval> intervalIter) {
            this.options = new RetryOptions(options.retryFor, options.maxRetryCount, intervalIter, options.executionTimeOut, options.releaseLockIndicator);
        }

        private Builder(RetryOptions options, Interval maxExecution) {
            this.options = new RetryOptions(options.retryFor, options.maxRetryCount, options.nextInterval,
                    maxExecution == null ? null : maxExecution.getTimeInMs(), options.releaseLockIndicator);
        }

        private Builder(RetryOptions options, Predicate<Throwable> maxExecution) {
            this.options = new RetryOptions(options.retryFor, options.maxRetryCount, options.nextInterval, options.executionTimeOut, maxExecution);
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
            if (shouldReleaseLock == null)
                return new Builder(options, (Throwable t) -> false);
            return new Builder(options, shouldReleaseLock);
        }

        public RetryOptions build() {
            return options;
        }
    }
}
