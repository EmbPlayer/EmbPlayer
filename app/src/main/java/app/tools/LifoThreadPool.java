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

import android.os.Process;

import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Jetty {@link QueuedThreadPool} of up to {@code threads} workers that runs the most recently
 * submitted waiting task first (LIFO).
 *
 * <p>Only tasks that actually have to wait are reordered: while fewer than {@code threads}
 * workers exist, a new submission starts a worker straight away. With one thread and task A
 * running, submitting B, C, D executes them in the order A, D, C, B.
 *
 * <pre>{@code
 * LifoThreadPool pool = new LifoThreadPool(4, "thumbs");
 *
 * pool.execute(() -> loadThumbnail(id));                // fire and forget
 * Future<Bitmap> f = pool.submit(() -> decode(file));   // a Future works too
 * f.cancel(true);
 * pool.purge();                                         // drop work that became stale
 * pool.shutdown();
 * }</pre>
 *
 * <p>It can also be given to a Jetty server: {@code server.setThreadPool(pool)}.
 *
 * <p>Things to be aware of:
 * <ul>
 *   <li>Under sustained load the oldest queued tasks can starve. That is the price of LIFO.</li>
 *   <li>A task cancelled through its Future never runs, but a small empty entry stays in the
 *       queue until a worker reaches it or you call {@link #purge()}.</li>
 *   <li>Jetty starts with no workers here (min 0) and grows on demand up to {@code threads}.
 *       Idle workers exit after the keep-alive time, at most one per idle period.</li>
 *   <li>If used as the Jetty server pool, its acceptor and selector loops hold threads
 *       permanently, so {@code threads} must be larger than those plus what you need for
 *       requests.</li>
 * </ul>
 */
public final class LifoThreadPool extends QueuedThreadPool {

    private static final long DEFAULT_KEEP_ALIVE_MS = 60000L;

    private final LifoBlockingQueue queue;
    private final int androidPriority;

    /** Workers are named "lifo-pool-N" and run at {@link Process#THREAD_PRIORITY_BACKGROUND}. */
    public LifoThreadPool(int threads) {
        this(threads, "lifo-pool");
    }

    /** Workers run at {@link Process#THREAD_PRIORITY_BACKGROUND}. */
    public LifoThreadPool(int threads, String name) {
        this(threads, name, Process.THREAD_PRIORITY_BACKGROUND, DEFAULT_KEEP_ALIVE_MS);
    }

    /**
     * @param threads        maximum number of worker threads (at least 1)
     * @param name           prefix for thread names, visible in Logcat and the profiler
     * @param threadPriority an {@code android.os.Process.THREAD_PRIORITY_*} value for each worker.
     *                       THREAD_PRIORITY_BACKGROUND (used by the shorter constructors) makes
     *                       Android give the workers only a small share of the CPU while the app
     *                       is busy, often on the slower cores only. Use THREAD_PRIORITY_DEFAULT
     *                       for full speed. If the device refuses a priority above the default,
     *                       the workers keep the default one.
     * @param keepAliveMS    how long an idle worker waits before it exits
     */
    public LifoThreadPool(int threads, String name, int threadPriority, long keepAliveMS) {
        this(threads, name, threadPriority, keepAliveMS, new LifoBlockingQueue());
    }

    private LifoThreadPool(int threads, String name, int threadPriority, long keepAliveMS,
                           LifoBlockingQueue q) {
        super(q);
        this.queue = q;
        this.androidPriority = threadPriority;

        setName(name);
        setMinThreads(0);                     // min 0, so every idle worker may exit
        setMaxThreads(Math.max(1, threads));  // grows on demand up to this
        setMaxIdleTimeMs((int) Math.min(Integer.MAX_VALUE, keepAliveMS));
        try {
            start();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot start " + name, e);
        }
    }

    /** Runs a task and returns a Future for it. */
    public <T> Future<T> submit(Callable<T> task) {
        FutureTask<T> f = new FutureTask<>(task);
        execute(f);
        return f;
    }

    /** Runs a task and returns a Future for it. */
    public Future<?> submit(Runnable task) {
        FutureTask<Void> f = new FutureTask<>(task, null);
        execute(f);
        return f;
    }

    /** Removes queued tasks that were cancelled through their Future. */
    public void purge() {
        queue.removeIf(r -> r instanceof Future && ((Future<?>) r).isCancelled());
    }

    /** Stops the pool. Queued tasks that have not started are dropped. */
    public void shutdown() {
        try {
            stop();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Jetty calls this for every new worker. The Android priority is set on the worker itself,
     * because Jetty's own setThreadsPriority only knows Java priorities (1-10).
     */
    @Override
    protected Thread newThread(final Runnable jettyWorker) {
        return new Thread(() -> {
            try {
                Process.setThreadPriority(androidPriority);
            } catch (SecurityException e) {
                // Some devices refuse priorities above the default. Keep the default
                // instead of letting the exception kill the app.
            }
            jettyWorker.run();
        });
    }

    /**
     * Work queue with stack behaviour: the task added last is the first one taken.
     *
     * <p>Jetty adds work through offer() and its workers always take from the head, so
     * inserting at the head is all it takes.
     */
    public static class LifoBlockingQueue extends LinkedBlockingDeque<Runnable> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean offer(Runnable e) {
            return offerFirst(e);
        }
    }
}