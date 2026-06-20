/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026-present Emre Hyuseinov (plaxir) <plaxirstudio@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.tools;

import java.util.concurrent.Callable;

import io.reactivex.rxjava3.functions.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SafeCallable {

    // Static factory method for Callable
    public static <T> Callable<T> createSafeCallable(Callable<T> originalCallable) {
        return new Callable<T>() {
            @Override
            public T call() {
                try {
                    return originalCallable.call();
                } catch (Exception e) {
                    throw new RuntimeException("Program should have restarted but didn't", e);
                }
            }
        };
    }

    // Static factory method for Consumer
    public static <U> Consumer<U> createSafeConsumer(Consumer<U> originalConsumer) {
        return new Consumer<U>() {
            @Override
            public void accept(U item) {
                try {
                    originalConsumer.accept(item);
                } catch (Throwable e) {
                    throw new RuntimeException("Program should have restarted but didn't", e);
                }
            }
        };
    }

    // Static factory method for Function
    public static <U, R> Function<U, R> createSafeFunction(Function<U, R> originalFunction) {
        return new Function<U, R>() {
            @Override
            public R apply(U input) {
                try {
                    return originalFunction.apply(input);
                } catch (Exception e) {
                    throw new RuntimeException("Program should have restarted but didn't", e);
                }
            }
        };
    }

    // Static factory method for Runnable
    public static Runnable createSafeRunnable(Runnable originalRunnable) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    originalRunnable.run();
                } catch (Exception e) {
                    throw new RuntimeException("Program should have restarted but didn't", e);
                }
            }
        };
    }

    // Static factory method for Supplier
    public static <T> Supplier<T> createSafeSupplier(Supplier<T> originalSupplier) {
        return new Supplier<T>() {
            @Override
            public T get() {
                try {
                    return originalSupplier.get();
                } catch (Exception e) {
                    throw new RuntimeException("Program should have restarted but didn't", e);
                }
            }
        };
    }

    // Static method to run multiple elements with safety
    public static void runSafely(Runnable... tasks) {
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (Exception e) {
                throw new RuntimeException("Program should have restarted but didn't", e);
            }
        }
    }

    // Static method to process multiple items with a consumer
    public static <U> void processItemsSafely(Consumer<U> processor, U... items) throws Throwable {
        Consumer<U> safeProcessor = createSafeConsumer(processor);
        for (U item : items) {
            safeProcessor.accept(item);
        }
    }
}