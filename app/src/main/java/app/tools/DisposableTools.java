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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import androidx.annotation.CallSuper;
import autodispose2.CompletableSubscribeProxy;
import autodispose2.ObservableSubscribeProxy;
import autodispose2.SingleSubscribeProxy;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;

import static autodispose2.AutoDispose.autoDisposable;
import static app.tools.StaticFunctions.onErrorSave;

public class DisposableTools {
    //Lifo thread pool
    public static final LifoThreadPool lifoQueuedThreadPool;

    //For lifo tasks
    public static final Scheduler lifo;

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

    private static final int timeoutBackgroundTaskMS;
    private static final int timeoutUiTaskMS;

    private static final Tasker tasker = new Tasker(CompletableSubject.create());

    static {
        timeoutBackgroundTaskMS = 40000;
        timeoutUiTaskMS = 4000;

        try {
            lifoQueuedThreadPool = new LifoThreadPool(50,"LifoThreadPool", Process.THREAD_PRIORITY_BACKGROUND, timeoutBackgroundTaskMS + 15000);
            lifo = Schedulers.from(lifoQueuedThreadPool);
            forServer = lifo;
            forGenerators = lifo;
            forMediaChecking = lifo;
            forSecondMedia = lifo;
            forMainMedia = lifo;
            ioThreadPoolScheduler = Schedulers.io();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void waitMS(long milliseconds)
    {
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
                                .subscribeOn(lifo)
                                .onErrorComplete()
                )
                .subscribe();
    }

    public static Disposable addTask(Callable<Boolean> maker, Callable<String> onError, Scheduler scheduler)
    {
        return tasker.addTaskWithTimeOut(maker,onError,StaticFunctions.Empty.rC,scheduler, timeoutBackgroundTaskMS,StaticFunctions.Empty.a);
    }

    public static Disposable addTaskUI(Callable<Boolean> maker,Callable<String> onError) {
        return tasker.addTaskWithTimeOut(maker,onError,StaticFunctions.Empty.rC,AndroidSchedulers.mainThread(), timeoutUiTaskMS,StaticFunctions.Empty.a);
    }

    public static Disposable addTaskAfterWait(
            int afterMills,
            Callable<Boolean> make,
            Callable<String> onError,
            Runnable onNotStartedAndTimeOuted,
            Scheduler scheduler,
            int timeOut
    ) {
        return tasker.addTaskAfterWait(afterMills,make,onError,onNotStartedAndTimeOuted,scheduler,timeOut);
    }

    public static Disposable addTaskAfterWait(int afterMills, Action make, Callable<String> onError, Scheduler scheduler){
        return tasker.addTaskAfterWait(afterMills,make,onError,scheduler);
    }

    public static Disposable addPollingTaskWithTimeOut(
            Callable<Boolean> conditionToContinue,
            Runnable onTick,
            Runnable onComplete,
            Runnable onTimeout,
            Callable<String> onError,
            long intervalMs,
            long timeOutMS,
            Scheduler schedulerForChecker,Scheduler defaultScheduler) {
        return tasker.addPollingTaskWithTimeOut(conditionToContinue,onTick,onComplete,onTimeout,onError,intervalMs,timeOutMS,schedulerForChecker,defaultScheduler,StaticFunctions.Empty.a);
    }

    public static Disposable addTaskWithTimeOut(Callable<Boolean> maker, Callable<String> onError,Consumer<Boolean> onSuccess,Runnable onNotStartedAndTimeOuted, Scheduler scheduler, int timeOutMS)
    {
        return tasker.addTaskWithTimeOut(maker,onError,onSuccess,onNotStartedAndTimeOuted,scheduler,timeOutMS,StaticFunctions.Empty.a);
    }

    private static Disposable addTaskWithTimeOut(Callable<Boolean> maker, Callable<String> onError,Consumer<Boolean> onSuccess, Scheduler scheduler, int timeOutMS)
    {
        return tasker.addTaskWithTimeOut(maker,onError,onSuccess,StaticFunctions.Empty.r,scheduler,timeOutMS,StaticFunctions.Empty.a);
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

    public static class Tasker{
        private CompletableSubject killSignal;

        public Tasker(CompletableSubject killSignal){
            updateKillSignal(killSignal);
        }

        public void updateKillSignal(CompletableSubject killSignal){
            this.killSignal = killSignal;
        }

        public Disposable addTask(Callable<Boolean> maker, Callable<String> onError, Scheduler scheduler,Action onDisposing)
        {
            return addTaskWithTimeOut(maker,onError,StaticFunctions.Empty.rC,scheduler, timeoutBackgroundTaskMS,onDisposing);
        }

        public Disposable addTaskUI(Callable<Boolean> maker,Callable<String> onError) {
            return addTaskWithTimeOut(maker,onError,StaticFunctions.Empty.rC,AndroidSchedulers.mainThread(), timeoutUiTaskMS,StaticFunctions.Empty.a);
        }

        public Disposable addTaskAfterWait(
                int afterMills,
                Callable<Boolean> make,
                Callable<String> onError,
                Runnable onNotStartedAndTimeOuted,
                Scheduler scheduler,
                int timeOut
        ) {
            // 1. WAIT: Start the timer and wait 'afterMills'
            return s(Completable.timer(afterMills, TimeUnit.MILLISECONDS, scheduler)

                    // 2. TRY TO EXECUTE: andThen() waits for the timer above to finish before continuing
                    .andThen(
                            Single.fromCallable(make)
                                    // 3. TIMEOUT: Start the clock. If it can't finish (or can't start)
                                    // within 'timeOut', it throws a TimeoutException.
                                    .timeout(timeOut, TimeUnit.MILLISECONDS, scheduler)
                    ))
                    .subscribe(
                            // On Success: Task completed within the time limit
                            result -> {
                                // Assuming StaticFunctions.Empty.a is a Consumer
                                // StaticFunctions.Empty.a.accept(result);
                            },

                            // On Error / Timeout
                            (Throwable onError_) -> {
                                if (onError_ instanceof java.util.concurrent.TimeoutException &&
                                        onNotStartedAndTimeOuted != null) { // Swap back to StaticFunctions.Empty.r if needed

                                    // It couldn't execute in time, run the fallback runnable
                                    onNotStartedAndTimeOuted.run();

                                } else {
                                    // A real error happened during execution
                                    String errorTag = "Unknown";
                                    try {
                                        if (onError != null) {
                                            errorTag = onError.call();
                                        }
                                    } catch (Exception e) {
                                        errorTag = "ErrorResolvingName";
                                    }

                                    onErrorSave("BaseDisposable-" + errorTag + ": ", onError_);
                                }
                            }
                    );
        }

        public Disposable addTaskAfterWait(int afterMills, Action make, Callable<String> onError, Scheduler scheduler){
            return c(Completable.timer(afterMills, TimeUnit.MILLISECONDS, scheduler))
                    .subscribe(make,(onError_)->{
                        try{
                            onErrorSave("BaseDisposable-"+onError.call()+": ",onError_);
                        } catch (Exception ignored) {
                        }
                    });
        }

        public Disposable addPollingTaskWithTimeOut(
                Callable<Boolean> conditionToContinue,
                Runnable onTick,
                Runnable onComplete,
                Runnable onTimeout,
                Callable<String> onError,
                long intervalMs,
                long timeOutMS,
                Scheduler schedulerForChecker,
                Scheduler defaultScheduler,
                Action onDisposing) {

            if(timeOutMS < 1){
                return o(Observable.interval(0, intervalMs, TimeUnit.MILLISECONDS, schedulerForChecker)
                        .takeWhile(tick -> {
                            try {
                                return conditionToContinue.call();
                            } catch (Exception e) {
                                return false; // Stop polling if the condition check throws an error
                            }
                        }).observeOn(defaultScheduler).doOnDispose(onDisposing))
                        .subscribe(
                                // onNext (Replaces doOnNext)
                                tick -> {
                                    if (onTick != null) {
                                        try {
                                            onTick.run();
                                        } catch (Exception ignored) {}
                                    }
                                },
                                // onError (Replaces doOnError)
                                error -> {
                                    try {
                                        onErrorSave("PollingTask-" + onError.call() + ": ", error);
                                    } catch (Exception ignored) {}
                                },
                                // onComplete (Replaces doOnComplete)
                                () -> {
                                    if (onComplete != null) {
                                        try {
                                            onComplete.run();
                                        } catch (Exception ignored) {}
                                    }
                                }
                        );
            }

            return o(Observable.interval(0, intervalMs, TimeUnit.MILLISECONDS, schedulerForChecker)
                    // The "Self-Destruct" timer
                    .takeUntil(Observable.timer(timeOutMS, TimeUnit.MILLISECONDS, schedulerForChecker)
                            .flatMap(t -> Observable.error(new java.util.concurrent.TimeoutException())))
                    .takeWhile(tick -> {
                        try {
                            return conditionToContinue.call();
                        } catch (Exception e) {
                            return false; // Stop polling if the condition check throws an error
                        }
                    }).observeOn(defaultScheduler).doOnDispose(onDisposing))
                    .subscribe(
                            // onNext (Replaces doOnNext)
                            tick -> {
                                if (onTick != null) {
                                    try {
                                        onTick.run();
                                    } catch (Exception ignored) {}
                                }
                            },
                            // onError (Replaces doOnError)
                            error -> {
                                try {
                                    if (error instanceof java.util.concurrent.TimeoutException && onTimeout != null) {
                                        onTimeout.run();
                                    } else {
                                        onErrorSave("PollingTask-" + onError.call() + ": ", error);
                                    }
                                } catch (Exception ignored) {}
                            },
                            // onComplete (Replaces doOnComplete)
                            () -> {
                                if (onComplete != null) {
                                    try {
                                        onComplete.run();
                                    } catch (Exception ignored) {}
                                }
                            }
                    );
        }

        @CallSuper
        protected CompletableSubscribeProxy c(Completable input){
            return input.to(autoDisposable(killSignal));
        }

        @CallSuper
        protected<T> ObservableSubscribeProxy<T> o(Observable<T> input){
            return input.to(autoDisposable(killSignal));
        }

        @CallSuper
        protected<T> SingleSubscribeProxy<T> s(Single<T> input){
            return input.to(autoDisposable(killSignal));
        }

        public Disposable addTaskWithTimeOut(Callable<Boolean> maker, Callable<String> onError,Consumer<Boolean> onSuccess,Runnable onNotStartedAndTimeOuted, Scheduler scheduler, int timeOutMS,Action onDisposing)
        {
            return s(Single.fromCallable(maker)
                    .subscribeOn(scheduler) // Run the task on a background thread
                    .timeout(timeOutMS, TimeUnit.MILLISECONDS,scheduler).doOnDispose(onDisposing)) // The "Self-Destruct" timer
                    .subscribe(onSuccess, onError_ -> {
                        try{
                            if(onError_ instanceof java.util.concurrent.TimeoutException &&
                                    onNotStartedAndTimeOuted != StaticFunctions.Empty.r){
                                onNotStartedAndTimeOuted.run();
                            }
                            else
                                onErrorSave("BaseDisposable-"+onError.call()+": ",onError_);
                        } catch (Exception ignored) {
                        }
                    });
        }

        private Disposable addTaskWithTimeOut(Callable<Boolean> maker, Callable<String> onError,Consumer<Boolean> onSuccess, Scheduler scheduler, int timeOutMS,Action onDisposing)
        {
            return addTaskWithTimeOut(maker,onError,onSuccess,StaticFunctions.Empty.r,scheduler,timeOutMS,onDisposing);
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
            if (task == null) {
                return;
            }

            dispose();
            disposable = addTask(task,() -> "WaitDisposable-Error", lifo);
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
                    .delay(waitSeconds, TimeUnit.SECONDS, lifo)
                    .doOnNext(value -> {
                        afterWait.run();
                    })
                    .retryWhen(errors -> errors.delay(2, TimeUnit.SECONDS, lifo))
                    .subscribe(
                            value -> {}, // empty onNext
                            onError
                    );
        }
    }
}