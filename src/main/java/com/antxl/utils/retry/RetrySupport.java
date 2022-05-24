package com.antxl.utils.retry;

import com.antxl.utils.retry.event.BreakRetryException;
import com.antxl.utils.retry.event.RetryEvent;
import com.antxl.utils.retry.event.RetryEventListener;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RetrySupport {
    private RetrySupport() {}

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

        <P> ParameterizedRetryableRunner<P> parameter(Supplier<P> parameter);

        default <P> ParameterizedRetryableRunner<P> parameter(P parameter) {
            return parameter(() -> parameter);
        }

        RetryableRunner appendListener(RetryEventListener... listeners);
    }

    public interface ParameterizedRetryableRunner<P> extends ReadyParameterizedRetryableRunner<P> {
        ReadyParameterizedRetryableRunner<P> options(RetryOptions options);

        ParameterizedRetryableRunner<P> appendListener(RetryEventListener... listeners);
    }

    public interface ReadyRetryableRunner {
        <P> ReadyParameterizedRetryableRunner<P> parameter(Supplier<P> parameter);

        default <P> ReadyParameterizedRetryableRunner<P> parameter(P parameter) {
            return parameter(() -> parameter);
        }

        default void run(Runnable runnable) {
            decorateRunnable(runnable).run();
        }

        Runnable decorateRunnable(Runnable runnable);

        default <R> R supply(Supplier<R> supplier) {
            return decorateSupplier(supplier).get();
        }

        <R> Supplier<R> decorateSupplier(Supplier<R> supplier);

        ReadyRetryableRunner appendListener(RetryEventListener... listeners);
    }

    public interface ReadyParameterizedRetryableRunner<P> {
        default void consume(Consumer<P> runnable) {
            decorateRunnable(runnable).run();
        }

        Runnable decorateRunnable(Consumer<P> consumer);

        default <R> R process(Function<P, R> processor) {
            return decorateSupplier(processor).get();
        }

        <R> Supplier<R> decorateSupplier(Function<P, R> processor);

        ReadyParameterizedRetryableRunner<P> appendListener(RetryEventListener... listeners);
    }

    private static abstract class ListenedRetryableRunnerBase {
        @Getter
        private final List<RetryEventListener> listeners;

        protected ListenedRetryableRunnerBase(List<RetryEventListener> listeners) {
            this.listeners = listeners;
        }

        protected final List<RetryEventListener> getAppendedListeners(List<RetryEventListener> listeners) {
            if (this.listeners == null || this.listeners.isEmpty())
                return listeners;
            List<RetryEventListener> newListeners = new ArrayList<>(this.listeners);
            newListeners.addAll(listeners);
            return newListeners;
        }
    }

    private static final class RetryableRunnerImpl extends ListenedRetryableRunnerBase implements RetryableRunner {
        private final RetryOptions options;

        private RetryableRunnerImpl() {
            this(RetryOptions.DEFAULT, null);
        }

        private RetryableRunnerImpl(RetryOptions options, List<RetryEventListener> listeners) {
            super(listeners);
            this.options = Optional.ofNullable(options).orElse(RetryOptions.DEFAULT);
        }

        @Override
        public ReadyRetryableRunner options(RetryOptions options) {
            return new RetryableRunnerImpl(options, null);
        }

        @Override
        public <P> ParameterizedRetryableRunner<P> parameter(Supplier<P> parameter) {
            return new ParameterizedRetryableRunnerImpl<>(options, parameter, getListeners());
        }

        @Override
        public Runnable decorateRunnable(Runnable runnable) {
            return new RetryingContext<>(options, null, o -> {
                runnable.run();
                return null;
            }, getListeners());
        }

        @Override
        public <R> Supplier<R> decorateSupplier(Supplier<R> supplier) {
            return new RetryingContext<>(options, null, o -> supplier.get(), getListeners());
        }

        @Override
        public RetryableRunner appendListener(RetryEventListener... listeners) {
            if (listeners == null || listeners.length == 0)
                return this;
            return new RetryableRunnerImpl(options, getAppendedListeners(Arrays.asList(listeners)));
        }
    }

    private static final class ParameterizedRetryableRunnerImpl<P> extends ListenedRetryableRunnerBase implements ParameterizedRetryableRunner<P> {
        private final RetryOptions options;
        private final Supplier<P> parameter;

        private ParameterizedRetryableRunnerImpl(RetryOptions options, Supplier<P> parameter, List<RetryEventListener> listeners) {
            super(listeners);
            this.options = Optional.ofNullable(options).orElse(RetryOptions.DEFAULT);
            this.parameter = parameter;
        }

        @Override
        public ReadyParameterizedRetryableRunner<P> options(RetryOptions options) {
            return new ParameterizedRetryableRunnerImpl<>(options, parameter, getListeners());
        }

        @Override
        public Runnable decorateRunnable(Consumer<P> consumer) {
            return new RetryingContext<>(options, parameter, p -> {
                consumer.accept(p);
                return null;
            }, getListeners());
        }

        @Override
        public <R> Supplier<R> decorateSupplier(Function<P, R> processor) {
            return new RetryingContext<>(options, parameter, processor, getListeners());
        }

        @Override
        public ParameterizedRetryableRunner<P> appendListener(RetryEventListener... listeners) {
            if (listeners == null || listeners.length == 0)
                return this;
            return new ParameterizedRetryableRunnerImpl<>(options, parameter, getAppendedListeners(Arrays.asList(listeners)));
        }
    }

    private static final class RetryingContext<P, R> implements Runnable, Supplier<R> {
        private final RetryOptions options;
        private final Supplier<P> parameter;
        private final Function<P, R> runner;
        private final List<RetryEventListener> listeners;
        private final boolean hasAnyListener;
        private long startTimestamp;
        private int retriedCount;
        private long lastInterval;
        private R result;
        private Throwable t;

        private RetryingContext(RetryOptions options, Supplier<P> parameter, Function<P, R> runner, List<RetryEventListener> listeners) {
            this.options = options;
            this.parameter = parameter;
            this.runner = runner;
            this.listeners = listeners;
            hasAnyListener = listeners != null && !listeners.isEmpty();
        }

        @Override
        public void run() {
            get();
        }

        @Override
        public R get() {
            startTimestamp = System.currentTimeMillis();
            for (retriedCount = 0; tryRun(); retriedCount++) {
                lastInterval = options.preformWait(lastInterval, t);
                if (hasAnyListener) {
                    RetryEvent event = new RetryEvent(t, retriedCount, getTotalExecutionTime(), lastInterval);
                    if (fireEvent(event))
                        throw new BrokenRetryException(t);
                }
            }
            return result;
        }

        private boolean fireEvent(RetryEvent event) {
            boolean shouldBreakRetry = false;
            for (var listener: listeners) {
                try {
                    listener.preRetry(event);
                } catch (BreakRetryException e) {
                    shouldBreakRetry = true;
                } catch (Throwable ignored) {}
            }
            return shouldBreakRetry;
        }

        private boolean tryRun() {
            try {
                result = runner.apply(parameter.get());
                return false;
            } catch (Throwable t) {
                this.t = t;
                if (shouldPreformRetry(t))
                    return true;
                throw t;
            }
        }

        private boolean shouldPreformRetry(Throwable t) {
            long totalExecutionTime = getTotalExecutionTime();
            return options.shouldRetryFor(t) && options.shouldPreformRetry(retriedCount, totalExecutionTime);
        }

        private long getTotalExecutionTime() {
            return System.currentTimeMillis() - startTimestamp;
        }
    }
}
