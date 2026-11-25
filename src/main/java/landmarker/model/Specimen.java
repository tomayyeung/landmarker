package landmarker.model;

import java.util.ArrayList;
import java.util.List;

public class Specimen {
    public final List<Point2D> landmarks = new ArrayList<>();
    public final List<Curve> curves = new ArrayList<>();    // open
    public final List<Curve> outlines = new ArrayList<>();  // closed
    public String image;
    public String id;
    public Double scale;      // units per pixel, null if not set
    public String comment;

    public Specimen copy() {
        Specimen s = new Specimen();
        s.landmarks.addAll(landmarks);
        curves.forEach(c -> s.curves.add(c.copy()));
        outlines.forEach(c -> s.outlines.add(c.copy()));
        s.image = image; s.id = id; s.scale = scale; s.comment = comment;
        return s;
    }

    /** Restore state from a snapshot (used for undo). */
    public void restore(Specimen o) {
        landmarks.clear(); landmarks.addAll(o.landmarks);
        curves.clear(); o.curves.forEach(c -> curves.add(c.copy()));
        outlines.clear(); o.outlines.forEach(c -> outlines.add(c.copy()));
        image = o.image; id = o.id; scale = o.scale; comment = o.comment;
    }

    /** Points closer than this (pixels) to an existing landmark are treated as duplicates. */
    public static final double DUP_EPS = 1e-3;

    /**
     * Turn a curve's/outline's semi-landmarks into real landmarks, appended in curve order.
     * Points coinciding with an existing landmark are skipped. The curve is removed.
     * @return number of landmarks added
     */
    public int curveToLandmarks(Curve c) {
        int added = 0;
        for (Point2D p : c.points) {
            boolean dup = false;
            for (Point2D q : landmarks) if (q.dist(p) <= DUP_EPS) { dup = true; break; }
            if (!dup) { landmarks.add(p); added++; }
        }
        curves.remove(c);
        outlines.remove(c);
        return added;
    }

    /** Convert every curve, then every outline, to landmarks. @return number of landmarks added */
    public int allCurvesToLandmarks() {
        int added = 0;
        for (Curve c : new ArrayList<>(curves)) added += curveToLandmarks(c);
        for (Curve c : new ArrayList<>(outlines)) added += curveToLandmarks(c);
        return added;
    }

    @Override public String toString() { return id != null ? id : (image != null ? image : "(unnamed)"); }
}
