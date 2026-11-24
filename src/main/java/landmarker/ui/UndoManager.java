package landmarker.ui;

import landmarker.model.Specimen;
import java.util.ArrayDeque;
import java.util.Deque;

/** Snapshot-based undo/redo for a single specimen's state. */
public class UndoManager {
    private record Entry(Specimen target, Specimen snapshot) {}
    private final Deque<Entry> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private static final int LIMIT = 200;

    /** Call before mutating target. */
    public void save(Specimen target) {
        undo.push(new Entry(target, target.copy()));
        if (undo.size() > LIMIT) undo.removeLast();
        redo.clear();
    }

    public Specimen undo() { return swap(undo, redo); }
    public Specimen redo() { return swap(redo, undo); }

    private Specimen swap(Deque<Entry> from, Deque<Entry> to) {
        if (from.isEmpty()) return null;
        Entry e = from.pop();
        to.push(new Entry(e.target, e.target.copy()));
        e.target.restore(e.snapshot);
        return e.target;
    }
}
