package landmarker.ui;

public enum Mode {
    SELECT("Select", "Click a landmark, semi-landmark or curve; drag to move; Delete removes point (Shift+Delete removes whole curve)"),
    LANDMARK("Landmark", "Click to add, drag to move, Delete removes selected"),
    SCALE("Scale", "Click two points of known distance (snaps to existing landmarks)"),
    MEASURE("Measure", "Click two points to measure (snaps to existing landmarks)"),
    CURVE("Curve", "Click control points, Enter to finish (Esc cancels)"),
    OUTLINE("Outline", "Click control points around a closed shape, Enter to finish"),
    TRACE("Trace LMs", "Click existing landmarks in order, Enter to generate curve/outline");

    public final String label, hint;
    Mode(String label, String hint) { this.label = label; this.hint = hint; }
}
