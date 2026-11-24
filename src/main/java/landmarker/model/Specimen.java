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

    @Override public String toString() { return id != null ? id : (image != null ? image : "(unnamed)"); }
}
