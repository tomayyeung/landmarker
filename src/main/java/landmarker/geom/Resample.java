package landmarker.geom;

import landmarker.model.Point2D;
import java.util.ArrayList;
import java.util.List;

/** Catmull-Rom spline through control points, resampled to N points equally spaced by arc length. */
public final class Resample {
    private Resample() {}
    private static final int SEG_STEPS = 50;

    public static List<Point2D> spline(List<Point2D> ctrl, boolean closed) {
        int n = ctrl.size();
        List<Point2D> out = new ArrayList<>();
        if (n < 2) { out.addAll(ctrl); return out; }
        int segs = closed ? n : n - 1;
        for (int i = 0; i < segs; i++) {
            Point2D p0 = get(ctrl, i - 1, closed), p1 = get(ctrl, i, closed),
                    p2 = get(ctrl, i + 1, closed), p3 = get(ctrl, i + 2, closed);
            for (int k = 0; k < SEG_STEPS; k++) {
                double t = k / (double) SEG_STEPS, t2 = t * t, t3 = t2 * t;
                double x = 0.5 * (2 * p1.x() + (-p0.x() + p2.x()) * t
                        + (2 * p0.x() - 5 * p1.x() + 4 * p2.x() - p3.x()) * t2
                        + (-p0.x() + 3 * p1.x() - 3 * p2.x() + p3.x()) * t3);
                double y = 0.5 * (2 * p1.y() + (-p0.y() + p2.y()) * t
                        + (2 * p0.y() - 5 * p1.y() + 4 * p2.y() - p3.y()) * t2
                        + (-p0.y() + 3 * p1.y() - 3 * p2.y() + p3.y()) * t3);
                out.add(new Point2D(x, y));
            }
        }
        out.add(closed ? ctrl.get(0) : ctrl.get(n - 1));
        return out;
    }

    private static Point2D get(List<Point2D> c, int i, boolean closed) {
        int n = c.size();
        if (closed) return c.get(((i % n) + n) % n);
        return c.get(Math.max(0, Math.min(n - 1, i)));
    }

    /** Resample a polyline to count points equally spaced by arc length. For closed, the polyline is assumed to return to start and endpoint isn't duplicated. */
    public static List<Point2D> byArcLength(List<Point2D> poly, int count, boolean closed) {
        List<Point2D> out = new ArrayList<>();
        if (poly.isEmpty() || count <= 0) return out;
        if (count == 1 || poly.size() == 1) { out.add(poly.get(0)); return out; }
        double[] cum = new double[poly.size()];
        for (int i = 1; i < poly.size(); i++) cum[i] = cum[i - 1] + poly.get(i - 1).dist(poly.get(i));
        double total = cum[cum.length - 1];
        int divisions = closed ? count : count - 1;
        int seg = 1;
        for (int k = 0; k < count; k++) {
            double target = total * k / divisions;
            while (seg < poly.size() - 1 && cum[seg] < target) seg++;
            double segLen = cum[seg] - cum[seg - 1];
            double t = segLen == 0 ? 0 : (target - cum[seg - 1]) / segLen;
            Point2D a = poly.get(seg - 1), b = poly.get(seg);
            out.add(new Point2D(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t));
        }
        return out;
    }

    public static List<Point2D> smoothResample(List<Point2D> ctrl, int count, boolean closed) {
        return byArcLength(spline(ctrl, closed), count, closed);
    }
}
