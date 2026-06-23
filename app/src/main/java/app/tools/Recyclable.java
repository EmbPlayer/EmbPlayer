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

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;

import static app.tools.DisposableTools.addTask;
import static app.tools.DisposableTools.addTaskAfterWait;

public class Recyclable {
    private static class RList<T> {

        // Stores the actual elements. Removed elements will be set to null.
        private final List<T> elements;

        // Stores the indices that have been freed up by removals.
        private final Queue<Integer> freeIndices;

        public RList() {
            this.elements = new ArrayList<>();
            // PriorityQueue ensures we always reuse the lowest available index first
            this.freeIndices = new PriorityQueue<>();
        }

        /**
         * Performs a bulk cleanup of all active items and resets the list state.
         * * Each non-null item is passed to the provided consumer (e.g., for disposal)
         * before the internal storage and free-index queue are wiped clean.
         * * @param onClear A consumer logic to execute for every active item (e.g., Disposable::dispose).
         */
        private final void clear(Consumer<T> onClear) {
            // 1. Iterate through the elements directly
            for (int i = 0; i < elements.size(); i++) {
                T cur = elements.get(i);

                // 2. Pass non-null elements to the consumer
                if (cur != null) {
                    onClear.accept(cur);
                }
            }

            // 3. Wipe the collections once at the end
            elements.clear();
            freeIndices.clear();
        }

        /**
         * Adds an item to the list, prioritizing the reuse of previously freed indices.
         * <p>
         * This method accepts a factory function rather than a direct object instance.
         * The assigned index is passed into this function, allowing the newly created
         * item to be aware of its exact position in the list (e.g., for storing its own ID).
         * <p>
         * If there are free indices available (from previous removals), the lowest available
         * index is reused. If no free indices exist, the item is appended to the end of the list.
         *
         * @param item A function that takes the assigned index as input and returns the item to be stored.
         */
        private void add(Function<Integer,T> item) {
            if (!freeIndices.isEmpty()) {
                // Reuse the lowest available index
                int targetIndex = freeIndices.poll();
                elements.set(targetIndex, item.apply(targetIndex));
            } else {
                // No free indices, append to the end of the list
                elements.add(item.apply(elements.size()));
            }
        }

        /**
         * Removes an item at the specified index and frees up that index for future use.
         * @param index The index of the item to remove.
         * @return The removed item, or null if it was already empty.
         */
        private T remove(int index) {
            if (index < 0 || index >= elements.size()) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + elements.size());
            }

            T removedItem = elements.get(index);

            // Only free the index if there was actually an item there
            if (removedItem != null) {
                elements.set(index, null); // Clear the reference to avoid memory leaks
                freeIndices.offer(index);  // Store the index to be reused later
            }

            return removedItem;
        }

        /**
         * Retrieves an item at a specific index.
         * @param index The index to retrieve.
         * @return The item, or null if the slot is empty (removed).
         */
        public T get(int index) {
            if (index < 0 || index >= elements.size()) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + elements.size());
            }
            return elements.get(index);
        }

        /**
         * Prints the internal state for debugging purposes.
         */
        public void printState() {
            System.out.println("Elements: " + elements);
            System.out.println("Free Indices Queue: " + freeIndices);
            System.out.println("---");
        }
    }

    public static class ListDisposable {
        private final String name;

        private final RList<Disposable> list = new RList<>();

        public ListDisposable(Class<?> name){
            this.name = name.getName()+"_";
        }

        public final void addStartAfterTimeout(int afterMills, Runnable make, Runnable onError, Scheduler scheduler, String taskName){
            list.add((index) -> addTaskAfterWait(afterMills,()->{
                make.run();
                remove(index);
            },()->{
                onError.run();
                remove(index);
                return name+taskName;
            },scheduler));
        }

        public final void add(Runnable make,Runnable onDisposing, Runnable onError, Scheduler scheduler, String taskName) {
            list.add((index)->new DisposableOnDisposing(makeDisposable(index,make,onError,scheduler,taskName),onDisposing));
        }

        private Disposable makeDisposable(int index,Runnable make, Runnable onError, Scheduler scheduler, String taskName){
            return addTask(()->{
                make.run();
                remove(index);
                return true;
            },()->{
                onError.run();
                remove(index);
                return name+taskName;
            },scheduler);
        }

        public final void add(Runnable make, Runnable onError, Scheduler scheduler, String taskName) {
            list.add((index)->makeDisposable(index,make,onError,scheduler,taskName));
        }

        public final void add(Runnable make, Scheduler scheduler, String taskName){
            this.add(make,()->{},scheduler,taskName);
        }

        public final void addUI(Runnable make, Runnable onError, String taskName) {
            list.add((index)->addTask(()->{
                make.run();
                remove(index);
                return true;
            },()->{
                onError.run();
                remove(index);
                return name+taskName;
            }, AndroidSchedulers.mainThread()));
        }

        public final void addUI(Runnable make, String taskName){
            this.addUI(make,()->{},taskName);
        }

        public final void clear(){
            list.clear((task)->{

                if(task == null || task.isDisposed())
                    return;

                task.dispose();

            });
        }

        private void remove(int index){

            Disposable current = null;
            try{
                current = list.remove(index);
            }
            finally {
                if(current!=null)
                    current.dispose();
            }
        }

        private static class DisposableOnDisposing implements Disposable{
            private final Disposable disposable;
            private final Runnable onClosing;

            private DisposableOnDisposing(Disposable disposable, Runnable onClosing){
                this.disposable = disposable;
                this.onClosing = onClosing;
            }

            @Override
            public void dispose() {
                onClosing.run();
                disposable.dispose();
            }

            @Override
            public boolean isDisposed() {
                return disposable.isDisposed();
            }
        }
    }
}
