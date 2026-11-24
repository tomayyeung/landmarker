package landmarker.geom;

import landmarker.model.Point2D;
import java.util.List;

public final class Measure {
    private Measure() {}

    public static double distance(Point2D a, Point2D b) { return a.dist(b); }

    public static double polylineLength(List<Point2D> pts) {
        double s = 0;
        for (int i = 1; i < pts.size(); i++) s += pts.get(i - 1).dist(pts.get(i));
        return s;
    }

    public static double perimeter(List<Point2D> pts) {
        if (pts.size() < 2) return 0;
        return polylineLength(pts) + pts.get(pts.size() - 1).dist(pts.get(0));
    }

    /** Shoelace formula; absolute enclosed area. */
    public static double area(List<Point2D> pts) {
        double s = 0;
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            Point2D a = pts.get(i), b = pts.get((i + 1) % n);
            s += a.x() * b.y() - b.x() * a.y();
        }
        return Math.abs(s) / 2;
    }

    public static double scaleLength(double px, Double scale) { return scale == null ? px : px * scale; }
    public static double scaleArea(double px2, Double scale) { return scale == null ? px2 : px2 * scale * scale; }
}
