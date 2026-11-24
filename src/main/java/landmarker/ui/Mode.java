package landmarker.ui;

public enum Mode {
    LANDMARK("Landmark", "Click to add, drag to move, Delete removes selected"),
    SCALE("Scale", "Click two points of known distance"),
    MEASURE("Measure", "Click two points to measure; click a curve/outline to select it"),
    CURVE("Curve", "Click control points, Enter to finish (Esc cancels)"),
    OUTLINE("Outline", "Click control points around a closed shape, Enter to finish"),
    TRACE("Trace LMs", "Click existing landmarks in order, Enter to generate curve/outline");

    public final String label, hint;
    Mode(String label, String hint) { this.label = label; this.hint = hint; }
}
