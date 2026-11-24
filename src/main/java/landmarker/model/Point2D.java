package landmarker.model;

public record Point2D(double x, double y) {
    public double dist(Point2D o) { return Math.hypot(x - o.x, y - o.y); }
}
