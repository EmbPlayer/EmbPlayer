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
package app.tools.Thread;

import android.os.Process;

import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

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
 *
 * pool.fifoWay(1).execute(watchdog);                    // urgent: served before everything that waits
 * pool.fifoWay(3).execute(cleanup);                     // less urgent, still before any waiting LIFO task
 * pool.shutdown();
 * }</pre>
 *
 * <p>It can also be given to a Jetty server: {@code server.setThreadPool(pool)}.
 *
 * <p>Things to be aware of:
 * <ul>
 *   <li>Under sustained load the oldest queued tasks can starve. That is the price of LIFO.
 *       The {@link #fifoWay(int)} lane is the other way round: it is always served first, and
 *       inside it the lower numbers go first, so a steady stream of urgent tasks can keep the
 *       less urgent ones waiting. Use that lane for small, occasional work.</li>
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

    private final LifoAndFifoBlockingQueue queue;
    private final int androidPriority;

    /** Created once, so {@link #fifoWay()} costs nothing no matter how often it is called. */
    private final Executor fifoExecutor = newFifoExecutor(LifoAndFifoBlockingQueue.DEFAULT_FIFO_PRIORITY);

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
        this(threads, name, threadPriority, keepAliveMS, new LifoAndFifoBlockingQueue());
    }

    private LifoThreadPool(int threads, String name, int threadPriority, long keepAliveMS,
                           LifoAndFifoBlockingQueue q) {
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

    /**
     * Same as {@code fifoWay(LifoAndFifoBlockingQueue.DEFAULT_FIFO_PRIORITY)}, which is priority 5.
     */
    public Executor fifoWay() {
        return fifoExecutor;
    }

    /**
     * An {@link Executor} on the same workers for work that must not wait behind the LIFO tasks.
     *
     * <p>{@code priority}: <b>1 is served first</b>, bigger numbers later. Any int is accepted; a
     * number below 1 is simply even more urgent. Tasks with the same number run in arrival order.
     * Whatever the number, these tasks run before any waiting LIFO task.
     *
     * <p>It goes through {@link #execute(Runnable)}, so a worker is started on demand exactly
     * like for a normal submission. Once the pool is stopped it throws
     * {@link java.util.concurrent.RejectedExecutionException}. Keep the Executor in a field if you
     * use it often.
     */
    public Executor fifoWay(int priority) {
        return priority == LifoAndFifoBlockingQueue.DEFAULT_FIFO_PRIORITY
                ? fifoExecutor
                : newFifoExecutor(priority);
    }

    private Executor newFifoExecutor(final int priority) {
        return task -> execute(new LifoAndFifoBlockingQueue.FifoTask(task, priority));
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
            } catch (RuntimeException e) {
                // Some devices refuse priorities above the default (SecurityException). Keep the
                // default instead of letting the exception kill the app. This must never escape:
                // if it did, jettyWorker would not run, and Jetty would count a worker that does
                // not exist, until the pool believes it is full.
            }
            jettyWorker.run();
        });
    }
}