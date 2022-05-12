package com.oocl.utils.retry;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RetrySupport {
    public static void runWithRetry(Runnable runnable) {
        runWithRetry().run(runnable);
    }

    public static <R> R runWithRetry(Supplier<R> supplier) {
        return runWithRetry().supply(supplier);
    }

    public static RetryableRunner runWithRetry() {
        return new RetryableRunnerImpl();
    }

    public static ReadyRetryableRunner runWithRetry(RetryOptions options) {
        return new RetryableRunnerImpl().options(options);
    }

    public interface RetryableRunner extends ReadyRetryableRunner {
        ReadyRetryableRunner options(RetryOptions options);

        <P> ParameterizedRetryableRunner<P> parameter(P parameter);
    }

    public interface ParameterizedRetryableRunner<P> extends ReadyParameterizedRetryableRunner<P> {
        ReadyParameterizedRetryableRunner<P> options(RetryOptions options);
    }

    public interface ReadyRetryableRunner {
        <P> ReadyParameterizedRetryableRunner<P> parameter(P parameter);

        void run(Runnable runnable);

        <R> R supply(Supplier<R> supplier);
    }

    public interface ReadyParameterizedRetryableRunner<P> {
        void consume(Consumer<P> consumer);

        <R> R process(Function<P, R> processor);
    }

    private static final class RetryableRunnerImpl implements RetryableRunner {
        private final RetryOptions options;

        private RetryableRunnerImpl() {
            this(RetryOptions.DEFAULT);
        }

        private RetryableRunnerImpl(RetryOptions options) {
            this.options = Optional.ofNullable(options).orElse(RetryOptions.DEFAULT);
        }

        @Override
        public ReadyRetryableRunner options(RetryOptions options) {
            return new RetryableRunnerImpl(options);
        }

        @Override
        public <P> ParameterizedRetryableRunner<P> parameter(P parameter) {
            return new ParameterizedRetryableRunnerImpl<>(options, parameter);
        }

        @Override
        public void run(Runnable runnable) {
            new RetryingContext<>(options, null, o -> {
                runnable.run();
                return null;
            }).run();
        }

        @Override
        public <R> R supply(Supplier<R> supplier) {
            return new RetryingContext<>(options, null, o -> supplier.get()).run();
        }
    }

    private static final class ParameterizedRetryableRunnerImpl<P> implements ParameterizedRetryableRunner<P> {
        private final RetryOptions options;
        private final P parameter;

        private ParameterizedRetryableRunnerImpl(RetryOptions options, P parameter) {
            this.options = Optional.ofNullable(options).orElse(RetryOptions.DEFAULT);
            this.parameter = parameter;
        }

        @Override
        public ReadyParameterizedRetryableRunner<P> options(RetryOptions options) {
            return new ParameterizedRetryableRunnerImpl<>(options, parameter);
        }

        @Override
        public void consume(Consumer<P> consumer) {
            new RetryingContext<>(options, parameter, p -> {
                consumer.accept(p);
                return null;
            }).run();
        }

        @Override
        public <R> R process(Function<P, R> processor) {
            return new RetryingContext<>(options, parameter, processor).run();
        }
    }

    private static final class RetryingContext<P, R> {
        private final RetryOptions options;
        private final P parameter;
        private final Function<P, R> runner;
        private long startTimestamp;
        private int retriedCount;
        private long lastInterval;
        private R result;
        private Throwable t;

        private RetryingContext(RetryOptions options, P parameter, Function<P, R> runner) {
            this.options = options;
            this.parameter = parameter;
            this.runner = runner;
        }

        public R run() {
            startTimestamp = System.currentTimeMillis();
            for (retriedCount = 0; tryRun(); retriedCount++)
                lastInterval = options.preformWait(lastInterval, t);
            return result;
        }

        private boolean tryRun() {
            try {
                result = runner.apply(parameter);
                return false;
            } catch (Throwable t) {
                this.t = t;
                if (shouldPreformRetry(t))
                    return true;
                throw t;
            }
        }

        private boolean shouldPreformRetry(Throwable t) {
            long totalExecutionTime = System.currentTimeMillis() - startTimestamp;
            return options.shouldRetryFor(t) && options.shouldPreformRetry(retriedCount, totalExecutionTime);
        }
    }
}
