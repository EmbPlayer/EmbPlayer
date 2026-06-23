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
import java.util.concurrent.TimeUnit;

import androidx.annotation.CallSuper;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.BiFunction;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static app.tools.StaticFunctions.onErrorSave;

public class DisposableTools {
    // For CPU-bound parallel computations that may be long-running or need work-stealing.
    // Uses a ForkJoinPool (custom size). Not intended for fast calculations –
    // use computationScheduler() for short CPU tasks.
    public static final Scheduler forkJoinPool;

    //For Main Media
    public static final Scheduler forMainMedia;

    // For Second Media
    public static final Scheduler forSecondMedia;

    // For Media Checking
    public static final Scheduler forMediaChecking;

    // For generators
    public static final Scheduler forGenerators;

    // For blocking I/O tasks (network, disk, database) or operations that are not CPU-intensive.
    // Suitable for slower hardware because it allows unlimited thread growth.
    public static final Scheduler ioThreadPoolScheduler;

    // On Server Starting
    public static final Scheduler forServer;

    private static final int parallelism;

    static {
        boolean isHavePool;
        try {
            Class.forName("java.util.concurrent.ForkJoinPool");
            isHavePool = true;
        } catch (Throwable t) {
            isHavePool = false;
        }

        parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
        try {
            //ioThreadPoolScheduler = Schedulers.io();

            BiFunction<Integer,Boolean,Scheduler> schedulerMake;

            if(isHavePool)
                schedulerMake = (pri,fifo)->forkJoinPoolMakerDefault(pri,fifo);
            else
                schedulerMake = (pri,fifo)->forkJoinPoolMakerNew(pri,fifo);


          /*forServer = schedulerMake.apply(Thread.MAX_PRIORITY-1,false);
            forGenerators = schedulerMake.apply(Thread.MAX_PRIORITY-2,false);

            forSecondMedia = schedulerMake.apply(Thread.NORM_PRIORITY+1,false);
            forMainMedia = schedulerMake.apply(Thread.NORM_PRIORITY,false);
            forkJoinPool = schedulerMake.apply(Thread.NORM_PRIORITY-1,false);
            forMediaChecking = forkJoinPool;
            ioThreadPoolScheduler = schedulerMake.apply(Thread.NORM_PRIORITY-1,true);*/

            forkJoinPool = schedulerMake.apply(Thread.MAX_PRIORITY-2,false);
            forServer = forkJoinPool;
            forGenerators = forServer;
            forMediaChecking = forkJoinPool;

            forSecondMedia = schedulerMake.apply(Thread.NORM_PRIORITY+1,false);
            forMainMedia = schedulerMake.apply(Thread.NORM_PRIORITY,false);

            ioThreadPoolScheduler = schedulerMake.apply(Thread.NORM_PRIORITY-1,true);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void waitMS(long milliseconds)
    {
        //ErrorCodeApp.code44 = ErrorCodeApp.code44+getWebUIWaitStopReport("WaitS");
        try {
            Thread.sleep(milliseconds);
        }
        catch (InterruptedException e)
        {
            onErrorSave("Thread sleep: ",e);
        }
    }

    public static void killAll(DisposableModified... disposables) {
        Observable.fromArray(disposables)
                .flatMap(d -> Observable.fromCallable(() -> {
                                    d.reset();
                                    return true;
                                })
                                .subscribeOn(ioThreadPoolScheduler)
                                .onErrorComplete()
                )
                .subscribe();
    }

    public static Disposable addTask(Callable<Boolean> maker, Callable<String> onError, Scheduler scheduler)
    {
        return addTaskWithTimeOut(maker,onError,(onSuccess)->{},scheduler,60000);
    }

    public static Disposable addTaskUI(Callable<Boolean> maker,Callable<String> onError) {
        return addTaskWithTimeOut(maker,onError,(onSuccess)->{},AndroidSchedulers.mainThread(),4000);
    }

    public static Disposable addTaskAfterWait(int afterMills, Action make, Callable<String> onError, Scheduler scheduler){
        return Completable.timer(afterMills, TimeUnit.MILLISECONDS, Schedulers.computation())
                .observeOn(scheduler)
                .subscribe(make,(onError_)->{
                    try{
                        onErrorSave("BaseDisposable-"+onError.call()+": ",onError_);
                    } catch (Exception ignored) {
                    }
                });
    }

    private static Disposable addTaskWithTimeOut(Callable<Boolean> maker, Callable<String> onError,Consumer<Boolean> onSuccess, Scheduler scheduler, int timeOutMS)
    {
        return Single.fromCallable(maker)
                .subscribeOn(scheduler) // Run the task on a background thread
                .timeout(timeOutMS, TimeUnit.MILLISECONDS,Schedulers.computation()) // The "Self-Destruct" timer
                .subscribe(onSuccess, onError_ -> {
                    try{
                        onErrorSave("BaseDisposable-"+onError.call()+": ",onError_);
                    } catch (Exception ignored) {
                    }
                });
    }
    private static Scheduler forkJoinPoolMakerDefault(int priority,boolean fifo)
    {
        return Schedulers.from(new java.util.concurrent.ForkJoinPool(
                parallelism,
                pool -> {
                    java.util.concurrent.ForkJoinWorkerThread worker = java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    worker.setName("calc-worker-" + worker.getPoolIndex());
                    worker.setDaemon(true);
                    worker.setPriority(priority); // slightly lower than UI
                    return worker;
                },
                null,
                fifo
        ));
    }

    private static Scheduler forkJoinPoolMakerNew(int priority,boolean fifo){
        return Schedulers.from(new jersey.repackaged.jsr166e.ForkJoinPool(
                parallelism,
                pool -> {
                    jersey.repackaged.jsr166e.ForkJoinWorkerThread worker = jersey.repackaged.jsr166e.ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                    worker.setName("calc-worker-" + worker.getPoolIndex());
                    worker.setDaemon(true);
                    worker.setPriority(priority); // slightly lower than UI
                    return worker;
                },
                null,
                fifo
        ));
    }

    public static class DisposableModified {
        public Disposable disposable;

        public DisposableModified(){}

        public DisposableModified(Disposable disposable)
        {
            this.disposable = disposable;
        }

        public final void dispose() {
            if (disposable == null || disposable.isDisposed())
                return;
            disposable.dispose();
        }

        @CallSuper
        public void reset()
        {
            dispose();
        }
    }

    public static class WaitDisposable extends DisposableModified {
        public boolean started;
        private int second;

        public WaitDisposable(int Second)
        {
            super();
            setSecond(Second);
        }

        public WaitDisposable()
        {
            super();
        }

        public void start(Callable<Boolean> task)
        {
            dispose();
            disposable = addTask(()->{
                boolean output = task.call();
                disposable.dispose();
                return output;
            },() -> {
                disposable.dispose();
                return "WaitDisposable-Error";
            },ioThreadPoolScheduler);
        }

        public void startWithLongWaiting(Consumer<Disposable> BeforeWait, Runnable AfterWait, Consumer<Throwable> OnError)
        {
            dispose();
            disposable = runAfterLongWait(second,BeforeWait,AfterWait,OnError);
        }

        public void disposeGC()
        {
            if (disposable == null || disposable.isDisposed())
                return;
            disposable = null;
            System.gc();
        }

        @Override
        public void reset()
        {
            super.reset();
            //disposable = null;
            started = false;
        }

        public Disposable getAndRemove()
        {
            Disposable d = disposable;
            disposable = null;
            return d;
        }

        public void setSecond(int Second)
        {
            second = Second;
        }

        public int getSecond()
        {
            return second;
        }

        private Disposable runAfterLongWait(int waitSeconds, Consumer<Disposable> BeforeWait, Runnable AfterWait, Consumer<Throwable> OnError)
        {
            Consumer<Disposable> beforeWait = SafeCallable.createSafeConsumer(BeforeWait);

            Runnable afterWait = SafeCallable.createSafeRunnable(AfterWait);

            Consumer<Throwable> onError = SafeCallable.createSafeConsumer(OnError);

            return Observable.just(true)
                    .doOnSubscribe(beforeWait)
                    .delay(waitSeconds, TimeUnit.SECONDS)
                    .doOnNext(value -> {
                        afterWait.run();
                    })
                    .retryWhen(errors -> errors.delay(2, TimeUnit.SECONDS))
                    .subscribe(
                            value -> {}, // empty onNext
                            onError
                    );
        }
    }

    /*public static Disposable RunInNewThread(Callable<Boolean> Base, Callable<Boolean> OnError)
    {
        return BaseDisposable(Base, accepted -> {}, OnError, NewThread());
    }*/

    /**
     * Forcefully disposes a Disposable without crashing the app.
     * - Kills emissions immediately
     * - Prevents memory leaks
     * - Safe for reuse (object remains stable)
     */
    /*public static void killDisposable(Disposable disposable) {
        if (disposable == null || disposable.isDisposed()) {
            return; // Already dead
        }

        // 1. Standard disposal (non-blocking)
        disposable.dispose();

        // 2. Aggressive cancellation (RxJava 2+)
        if (disposable instanceof Subscription) {
            ((Subscription) disposable).cancel(); // Faster than dispose()
        }

        // 3. Thread interruption (for blocking operations)
        if (disposable instanceof Future) {
            ((Future<?>) disposable).cancel(true); // true = interrupt thread
        }

        // 4. Nuclear option: Force-set disposed=true via reflection
        try {
            Field disposedField = disposable.getClass().getDeclaredField("disposed");
            disposedField.setAccessible(true);
            disposedField.setBoolean(disposable, true); // Brutal but effective
        } catch (Exception e) {
            OnErrorSave("killDisposable: ",e);
            // Reflection failed? No problem, we tried.
        }
    }

    public static void killDisposables(Disposable... disposables) {
        Observable.fromArray(disposables)
                .flatMap(d -> Observable.fromCallable(() -> {
                                    killDisposable(d);
                                    return true;
                                })
                                .subscribeOn(ioThreadPoolScheduler) // Uses shared IO pool
                                .onErrorComplete()
                )
                .blockingSubscribe(); // Wait for completion
    }

    public static void KillAll(DisposableModified... disposables) {
        Observable.fromArray(disposables)
                .flatMap(d -> Observable.fromCallable(() -> {
                                    d.Reset();
                                    return true;
                                })
                                .subscribeOn(ioThreadPoolScheduler) // Uses shared IO pool
                                .onErrorComplete()
                )
                .blockingSubscribe(); // Wait for completion
    }

    public void KillAll(Runnable... runnable) {
        Observable.fromArray(runnable)
                .flatMap(d -> Observable.fromCallable(() -> {
                                    d.run();
                                    return true;
                                })
                                .subscribeOn(ioThreadPoolScheduler) // Uses shared IO pool
                                .onErrorComplete()
                )
                .blockingSubscribe(); // Wait for completion
    }

    public static String getErrorAsString(Throwable throwable) {
        if (throwable == null) {
            return "No error information available";
        }

        StringBuilder errorString = new StringBuilder();

        // Basic error information
        errorString.append("Error Type: ").append(throwable.getClass().getSimpleName()).append("\n");
        errorString.append("Error Message: ").append(throwable.getMessage()).append("\n\n");

        // Full stack trace
        errorString.append("Stack Trace:\n");
        errorString.append(getStackTraceAsString(throwable)).append("\n");

        // Root cause (if any)
        Throwable rootCause = getRootCause(throwable);
        if (rootCause != null && rootCause != throwable) {
            errorString.append("\nRoot Cause:\n");
            errorString.append("Type: ").append(rootCause.getClass().getSimpleName()).append("\n");
            errorString.append("Message: ").append(rootCause.getMessage()).append("\n");
            errorString.append("Stack Trace:\n").append(getStackTraceAsString(rootCause));
        }

        return errorString.toString();
    }

    public static String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    public static Throwable getRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }*/
    /*
    private static Disposable BaseDisposable(Callable<Boolean> Base,Consumer<Boolean> OnEnd,Callable<Boolean> OnError,Scheduler ForUsing)
    {
        return Observable.fromCallable(Base(Base,OnError))
                .subscribeOn(ForUsing) // Run the task on a background thread
                //.observeOn(UIThread()) // Optional: Observe the result on another thread
                .subscribe(SafeCallable.createSafeConsumer(OnEnd), onError -> {});
    }*/
    /*private static Callable<Boolean> advancedCallable(Callable<Boolean> maker, Callable<String> onError){
        return () -> {
            try {
                return maker.call();
            } catch (Exception e) {

                String output = onError.call();

                OnErrorSave("BaseDisposable-"+output+": ",e);

                return true;
            }
        };
    }*/
}
