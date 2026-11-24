package landmarker.model;

import java.util.ArrayList;
import java.util.List;

/** A semi-landmark curve (open) or outline (closed). */
public class Curve {
    public final List<Point2D> points = new ArrayList<>();
    public boolean closed;

    public Curve(boolean closed) { this.closed = closed; }
    public Curve(List<Point2D> pts, boolean closed) { this.points.addAll(pts); this.closed = closed; }

    public Curve copy() { return new Curve(points, closed); }
}
