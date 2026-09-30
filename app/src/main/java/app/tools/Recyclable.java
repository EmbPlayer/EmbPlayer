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

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.subjects.CompletableSubject;

public class Recyclable {

    public static class ListDisposable {
        private final String name;

        private CompletableSubject killSignal = CompletableSubject.create();

        private final DisposableTools.Tasker tasker = new DisposableTools.Tasker(killSignal);

        public ListDisposable(Class<?> name){
            this.name = name.getName()+"_";
        }

        public final void addStartAfterWait(int afterMills, Action make, Runnable onError, Scheduler scheduler, String taskName){
            tasker.addTaskAfterWait(afterMills,make,()->{
                onError.run();
                return name+taskName;
            },scheduler);
        }

        public final void add(Runnable make, Scheduler scheduler, String taskName){
            this.add(make,StaticFunctions.Empty.r,scheduler,taskName);
        }

        public final void add(Runnable make, Action onDisposing, Runnable onError, Scheduler scheduler, String taskName) {

            tasker.addTask(()->{
                make.run();
                return true;
            },()->{
                onError.run();
                return name+taskName;
            },scheduler,onDisposing);
        }

        public final void addWithOnTimeOut(Runnable make,Action onDisposing, Runnable onError,Runnable onNotStartedAndTimeOuted, Scheduler scheduler, String taskName,int timeOutMS){
            tasker.addTaskWithTimeOut(()->{
                make.run();
                return true;
            },()->{
                onError.run();
                return name+taskName;
            },StaticFunctions.Empty.rC,()->{
                onNotStartedAndTimeOuted.run();
            },scheduler,timeOutMS,onDisposing);
        }

        public final void add(Runnable make, Runnable onError, Scheduler scheduler, String taskName) {
            tasker.addTask(()->{
                make.run();
                return true;
            },()->{
                onError.run();
                return name+taskName;
            },scheduler,StaticFunctions.Empty.a);
        }

        public final void addWithOnTimeOut(Runnable make, Runnable onError,Runnable onNotStartedAndTimeOuted, Scheduler scheduler, String taskName,int timeOutMS) {
            tasker.addTaskWithTimeOut(()->{
                make.run();
                return true;
            },()->{
                onError.run();
                return name+taskName;
            },StaticFunctions.Empty.rC,onNotStartedAndTimeOuted,scheduler,timeOutMS,StaticFunctions.Empty.a);
        }

        public final void addPollingTaskWithTimeOut(
                Callable<Boolean> conditionToContinue,
                Runnable onTick,
                Runnable onComplete,
                Runnable onTimeout,
                Runnable onError,
                Action onDispose,
                long intervalMs,
                long timeOutMS,
                Scheduler schedulerForChecker,
                Scheduler defaultScheduler,
                String taskName) {

            tasker.addPollingTaskWithTimeOut(
                    conditionToContinue,
                    onTick,
                    onComplete,
                    onTimeout,
                    () -> {
                        onError.run();
                        return name + taskName;
                    },
                    intervalMs,
                    timeOutMS,
                    schedulerForChecker,
                    defaultScheduler,
                    onDispose);
        }

        public final void addUI(Runnable make, Runnable onError, String taskName) {
            tasker.addTask(()->{
                make.run();
                return true;
            },()->{
                onError.run();
                return name+taskName;
            }, AndroidSchedulers.mainThread(),StaticFunctions.Empty.a);
        }

        public final void addUI(Runnable make, String taskName){
            this.addUI(make,StaticFunctions.Empty.r,taskName);
        }

        public final void clear(){
            killSignal.onComplete();
            killSignal = CompletableSubject.create();
            tasker.updateKillSignal(killSignal);
        }
    }
}