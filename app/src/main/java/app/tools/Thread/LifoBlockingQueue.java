package app.tools.Thread;


import java.util.concurrent.LinkedBlockingDeque;

/**
 * Work queue with stack behaviour: the task added last is the first one taken.
 *
 * <p>Jetty adds work through offer() and its workers always take from the head, so
 * inserting at the head is all it takes.
 */
public class LifoBlockingQueue extends LinkedBlockingDeque<Runnable> {
    private static final long serialVersionUID = 1L;

    @Override
    public boolean offer(Runnable e) {
        return offerFirst(e);
    }
}
