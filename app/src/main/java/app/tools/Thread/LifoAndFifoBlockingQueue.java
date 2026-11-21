package app.tools.Thread;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * Work queue for a thread pool. Two lanes behind one lock:
 *
 * <ul>
 *   <li><b>FIFO lane</b>: tasks wrapped in {@link FifoTask}. Always taken before the LIFO lane.
 *       Every task has a priority number: <b>1 is served first</b>, bigger numbers later. Tasks
 *       with the same number are served in arrival order.</li>
 *   <li><b>LIFO lane</b>: everything given to {@link #offer(Runnable)} without a wrapper (this is
 *       what Jetty does). The newest task is taken first.</li>
 * </ul>
 *
 * <p>Made for pools with a handful of workers: one non-fair lock, array based deques (no node
 * object per task) and a volatile counter, so {@link #size()}, {@link #isEmpty()} and
 * {@link #poll()} on an empty queue never touch the lock. Jetty calls {@code size()} on every
 * {@code execute()}, so this matters.
 *
 * <p>Iterators are weakly consistent snapshots in take order; {@code Iterator.remove()} removes
 * from the queue.
 */
public final class LifoAndFifoBlockingQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {

    /** Priority of a FIFO task when none is given. */
    public static final int DEFAULT_FIFO_PRIORITY = 5;

    /**
     * Wrap a task in this before {@link #offer(Runnable)} to send it to the FIFO lane. It is only
     * an envelope: the queue keeps, and hands out, the wrapped task itself.
     */
    public static final class FifoTask implements Runnable {
        private final Runnable task;
        private final int priority;

        public FifoTask(Runnable task) {
            this(task, DEFAULT_FIFO_PRIORITY);
        }

        /**
         * @param priority 1 is served first, bigger numbers later. Any int is accepted; a number
         *                 below 1 is simply even more urgent.
         */
        public FifoTask(Runnable task, int priority) {
            this.task = Objects.requireNonNull(task);
            this.priority = priority;
        }

        /** The wrapped task. */
        public Runnable unwrap() {
            return task;
        }

        public int priority() {
            return priority;
        }

        @Override
        public void run() {
            task.run();
        }

        @Override
        public String toString() {
            return "FifoTask[priority=" + priority + ", " + task + ']';
        }
    }

    // Non-fair on purpose: a fair lock forces a hand-off (context switch) on nearly every
    // acquisition. Task order is decided by the two lanes, not by who reaches the lock first.
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition(); // only used when a capacity is set

    private final PriorityLane fifo = new PriorityLane(); // served first
    private final ArrayDeque<Runnable> lifo = new ArrayDeque<>(); // head = newest task

    private final int capacity;
    private final boolean bounded;

    /** Tasks in both lanes. Written only while holding {@link #lock}; volatile so readers can skip the lock. */
    private volatile int count;

    public LifoAndFifoBlockingQueue() {
        this(Integer.MAX_VALUE);
    }

    public LifoAndFifoBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.bounded = capacity != Integer.MAX_VALUE;
    }

    // ------------------------------------------------------------------ insert

    /** Jetty calls this. The task goes to the LIFO lane, unless it is a {@link FifoTask}. */
    @Override
    public boolean offer(Runnable e) {
        Objects.requireNonNull(e);
        if (count >= capacity) return false; // full: no need for the lock
        lock.lock();
        try {
            if (count >= capacity) return false;
            enqueue(e);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds a task to the FIFO lane with {@link #DEFAULT_FIFO_PRIORITY}. This is only a queue: it
     * does not start a worker. With a pool use {@link LifoThreadPool#fifoWay()}, which does.
     */
    public boolean offerFifo(Runnable e) {
        return offer(new FifoTask(e));
    }

    /** Same as {@link #offerFifo(Runnable)} with a priority: 1 is served first, bigger numbers later. */
    public boolean offerFifo(Runnable e, int priority) {
        return offer(new FifoTask(e, priority));
    }

    @Override
    public void put(Runnable e) throws InterruptedException {
        Objects.requireNonNull(e);
        lock.lockInterruptibly();
        try {
            while (count >= capacity) notFull.await();
            enqueue(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean offer(Runnable e, long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(e);
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (count >= capacity) {
                if (nanos <= 0L) return false;
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(e);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Lock must be held and there must be room. */
    private void enqueue(Runnable e) {
        if (e instanceof FifoTask) {
            FifoTask f = (FifoTask) e;
            fifo.add(f.unwrap(), f.priority());
        } else {
            lifo.addFirst(e);
        }
        count++;
        notEmpty.signal();
    }

    // ------------------------------------------------------------------ remove

    @Override
    public Runnable take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            Runnable r;
            while ((r = dequeue()) == null) notEmpty.await();
            return r;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            Runnable r;
            while ((r = dequeue()) == null) {
                if (nanos <= 0L) return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            return r;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Runnable poll() {
        if (count == 0) return null; // empty: no need for the lock
        lock.lock();
        try {
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Runnable peek() {
        if (count == 0) return null;
        lock.lock();
        try {
            Runnable r = fifo.peek();
            return r != null ? r : lifo.peekFirst();
        } finally {
            lock.unlock();
        }
    }

    /** Lock must be held. FIFO lane first (lowest number, oldest), then the newest LIFO task. Null when empty. */
    private Runnable dequeue() {
        Runnable r = fifo.poll();
        if (r == null && (r = lifo.pollFirst()) == null) return null;
        count--;
        if (bounded) notFull.signal();
        return r;
    }

    // ------------------------------------------------------------------ size

    @Override
    public int size() {
        return count;
    }

    @Override
    public boolean isEmpty() {
        return count == 0;
    }

    @Override
    public int remainingCapacity() {
        return capacity - count;
    }

    // ------------------------------------------------------------------ bulk / search

    @Override
    public boolean contains(Object o) {
        if (o == null || count == 0) return false;
        lock.lock();
        try {
            return fifo.contains(o) || lifo.contains(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        if (o == null || count == 0) return false;
        lock.lock();
        try {
            if (fifo.remove(o) || lifo.removeFirstOccurrence(o)) {
                count--;
                if (bounded) notFull.signal();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes every queued task that matches, in one pass under the lock, keeping the order of
     * the rest. The filter runs while the lock is held, so keep it short.
     */
    @Override
    public boolean removeIf(Predicate<? super Runnable> filter) {
        Objects.requireNonNull(filter);
        if (count == 0) return false;
        lock.lock();
        try {
            final int before = count;
            try {
                fifo.removeIf(filter);
                dropMatching(lifo, filter);
            } finally {
                // Also right when the filter throws part-way: the counter is rebuilt from the lanes.
                count = fifo.size() + lifo.size();
                if (bounded && count < before) notFull.signalAll();
            }
            return count < before;
        } finally {
            lock.unlock();
        }
    }

    /** One O(n) pass. Nothing is changed until the filter has been applied to every element. */
    private static void dropMatching(ArrayDeque<Runnable> deque, Predicate<? super Runnable> filter) {
        final int size = deque.size();
        if (size == 0) return;
        final Runnable[] keep = new Runnable[size];
        int kept = 0;
        for (Runnable r : deque) {
            if (!filter.test(r)) keep[kept++] = r;
        }
        if (kept == size) return;
        deque.clear();
        for (int i = 0; i < kept; i++) deque.addLast(keep[i]);
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            fifo.clear();
            lifo.clear();
            count = 0;
            if (bounded) notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int drainTo(Collection<? super Runnable> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    @Override
    public int drainTo(Collection<? super Runnable> c, int maxElements) {
        Objects.requireNonNull(c);
        if (c == this) throw new IllegalArgumentException();
        if (maxElements <= 0) return 0;
        lock.lock();
        try {
            int n = 0;
            while (n < maxElements) {
                Runnable head = fifo.peek();
                if (head == null && (head = lifo.peekFirst()) == null) break;
                c.add(head); // if this throws, the task is still queued
                dequeue();
                n++;
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------ iteration

    /** Snapshot in the order the tasks would be taken: FIFO lane first, then LIFO lane. */
    @Override
    public Iterator<Runnable> iterator() {
        lock.lock();
        try {
            final Runnable[] snapshot = new Runnable[count];
            int i = fifo.copyTo(snapshot, 0);
            for (Runnable r : lifo) snapshot[i++] = r;
            return new SnapshotIterator(snapshot);
        } finally {
            lock.unlock();
        }
    }

    private final class SnapshotIterator implements Iterator<Runnable> {
        private final Runnable[] items;
        private int next;
        private Runnable last;

        SnapshotIterator(Runnable[] items) {
            this.items = items;
        }

        @Override
        public boolean hasNext() {
            return next < items.length;
        }

        @Override
        public Runnable next() {
            if (next >= items.length) throw new NoSuchElementException();
            return last = items[next++];
        }

        @Override
        public void remove() {
            if (last == null) throw new IllegalStateException();
            removeSame(last);
            last = null;
        }
    }

    /** Removes one occurrence of exactly this object (==), not of an equal one. */
    private void removeSame(Runnable target) {
        lock.lock();
        try {
            if (fifo.removeSame(target) || removeFirstSame(lifo, target)) {
                count--;
                if (bounded) notFull.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    private static boolean removeFirstSame(ArrayDeque<Runnable> deque, Runnable target) {
        for (Iterator<Runnable> it = deque.iterator(); it.hasNext(); ) {
            if (it.next() == target) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ FIFO lane

    /**
     * The FIFO lane: one arrival-ordered queue per priority number, lowest number first. Not thread
     * safe by itself, the queue's lock guards it. A priority level exists only while it holds tasks,
     * so the first level is always the one to serve.
     */
    private static final class PriorityLane {
        private final TreeMap<Integer, ArrayDeque<Runnable>> levels = new TreeMap<>();

        int size() {
            int n = 0;
            for (ArrayDeque<Runnable> d : levels.values()) n += d.size();
            return n;
        }

        void add(Runnable task, int priority) {
            ArrayDeque<Runnable> d = levels.get(priority);
            if (d == null) {
                d = new ArrayDeque<>();
                levels.put(priority, d);
            }
            d.addLast(task);
        }

        /** Oldest task of the lowest priority number, left in place. Null when empty. */
        Runnable peek() {
            return levels.isEmpty() ? null : levels.get(levels.firstKey()).peekFirst();
        }

        /** Removes and returns what {@link #peek()} shows. Null when empty. */
        Runnable poll() {
            if (levels.isEmpty()) return null;
            Integer first = levels.firstKey();
            ArrayDeque<Runnable> d = levels.get(first);
            Runnable r = d.pollFirst();
            if (d.isEmpty()) levels.remove(first);
            return r;
        }

        boolean contains(Object o) {
            for (ArrayDeque<Runnable> d : levels.values()) {
                if (d.contains(o)) return true;
            }
            return false;
        }

        boolean remove(Object o) {
            for (Iterator<ArrayDeque<Runnable>> it = levels.values().iterator(); it.hasNext(); ) {
                ArrayDeque<Runnable> d = it.next();
                if (d.removeFirstOccurrence(o)) {
                    if (d.isEmpty()) it.remove();
                    return true;
                }
            }
            return false;
        }

        boolean removeSame(Runnable target) {
            for (Iterator<ArrayDeque<Runnable>> it = levels.values().iterator(); it.hasNext(); ) {
                ArrayDeque<Runnable> d = it.next();
                if (removeFirstSame(d, target)) {
                    if (d.isEmpty()) it.remove();
                    return true;
                }
            }
            return false;
        }

        void removeIf(Predicate<? super Runnable> filter) {
            for (Iterator<ArrayDeque<Runnable>> it = levels.values().iterator(); it.hasNext(); ) {
                ArrayDeque<Runnable> d = it.next();
                dropMatching(d, filter);
                if (d.isEmpty()) it.remove();
            }
        }

        void clear() {
            levels.clear();
        }

        /** Copies every task, in take order, into {@code out} from {@code index}. Returns the next free index. */
        int copyTo(Runnable[] out, int index) {
            for (ArrayDeque<Runnable> d : levels.values()) {
                for (Runnable r : d) out[index++] = r;
            }
            return index;
        }
    }
}