package landmarker.ui;

import landmarker.model.Specimen;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Snapshot-based undo/redo. Each step covers one or more specimens. */
public class UndoManager {
    private record Snap(Specimen target, Specimen snapshot) {}
    private record Entry(List<Snap> snaps) {}
    private final Deque<Entry> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private static final int LIMIT = 200;

    /** Call before mutating target. */
    public void save(Specimen target) { saveAll(List.of(target)); }

    /** Call before mutating several specimens as one undoable step. */
    public void saveAll(List<Specimen> targets) {
        if (targets.isEmpty()) return;
        undo.push(snapshot(targets));
        if (undo.size() > LIMIT) undo.removeLast();
        redo.clear();
    }

    public Specimen undo() { return swap(undo, redo); }
    public Specimen redo() { return swap(redo, undo); }

    private static Entry snapshot(List<Specimen> targets) {
        List<Snap> l = new ArrayList<>();
        for (Specimen s : targets) l.add(new Snap(s, s.copy()));
        return new Entry(l);
    }

    /** Restores all specimens in the entry; returns the first one. */
    private Specimen swap(Deque<Entry> from, Deque<Entry> to) {
        if (from.isEmpty()) return null;
        Entry e = from.pop();
        to.push(snapshot(e.snaps.stream().map(Snap::target).toList()));
        for (Snap s : e.snaps) s.target.restore(s.snapshot);
        return e.snaps.get(0).target;
    }
}
