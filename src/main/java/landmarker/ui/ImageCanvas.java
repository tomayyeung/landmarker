package landmarker.ui;

import landmarker.geom.Measure;
import landmarker.geom.Resample;
import landmarker.model.Curve;
import landmarker.model.Point2D;
import landmarker.model.Specimen;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ImageCanvas extends JComponent {
    private final MainFrame app;
    private BufferedImage image;
    private Specimen spec;
    private double zoom = 1, offX = 0, offY = 0;
    Mode mode = Mode.LANDMARK;

    int selectedLandmark = -1;
    Curve selectedCurve = null;
    /** Index of selected semi-landmark within selectedCurve, or -1 if the whole curve is selected. */
    int selectedCurvePoint = -1;
    private final List<Point2D> pending = new ArrayList<>();
    private final List<Integer> traceIdx = new ArrayList<>();
    private Point2D hover;
    private String measureText;

    private Point dragStart; private double dragOffX, dragOffY;
    private boolean panning, movingLandmark, movingCurvePoint, dragUndoSaved;
    private static final double HIT_PX = 8;

    public ImageCanvas(MainFrame app) {
        this.app = app;
        setFocusable(true);
        setBackground(Color.DARK_GRAY);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); pressed(e); }
            @Override public void mouseDragged(MouseEvent e) { dragged(e); }
            @Override public void mouseReleased(MouseEvent e) { panning = false; movingLandmark = false; movingCurvePoint = false; }
            @Override public void mouseMoved(MouseEvent e) { hover = toImage(e.getPoint()); app.showCursor(hover); repaint(); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                double f = Math.pow(1.1, -e.getPreciseWheelRotation());
                zoomAt(e.getPoint(), f);
            }
        };
        addMouseListener(m); addMouseMotionListener(m); addMouseWheelListener(m);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { key(e); }
        });
    }

    public void setSpecimen(Specimen s, BufferedImage img) {
        spec = s; image = img;
        selectedLandmark = -1; clearCurveSelection();
        clearPending();
        fit();
    }

    public void setMode(Mode m) { mode = m; clearPending(); repaint(); }

    void clearCurveSelection() { selectedCurve = null; selectedCurvePoint = -1; }

    void clearPending() { pending.clear(); traceIdx.clear(); measureText = null; repaint(); }

    public void fit() {
        if (image == null || getWidth() == 0) { zoom = 1; offX = offY = 0; repaint(); return; }
        zoom = Math.min(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
        offX = (getWidth() - image.getWidth() * zoom) / 2;
        offY = (getHeight() - image.getHeight() * zoom) / 2;
        repaint();
    }

    void zoomAt(Point p, double f) {
        Point2D ip = toImage(p);
        zoom = Math.max(0.02, Math.min(50, zoom * f));
        offX = p.x - ip.x() * zoom; offY = p.y - ip.y() * zoom;
        repaint();
    }

    private Point2D toImage(Point p) { return new Point2D((p.x - offX) / zoom, (p.y - offY) / zoom); }
    private java.awt.geom.Point2D.Double toScreen(Point2D p) {
        return new java.awt.geom.Point2D.Double(p.x() * zoom + offX, p.y() * zoom + offY);
    }

    private int hitLandmark(Point2D ip) {
        if (spec == null) return -1;
        double r = HIT_PX / zoom; int best = -1; double bd = r;
        for (int i = 0; i < spec.landmarks.size(); i++) {
            double d = spec.landmarks.get(i).dist(ip);
            if (d <= bd) { bd = d; best = i; }
        }
        return best;
    }

    private record CurveHit(Curve curve, int index) {}

    /** Nearest semi-landmark point within hit radius. */
    private CurveHit hitCurvePoint(Point2D ip) {
        if (spec == null) return null;
        double bd = HIT_PX / zoom; CurveHit best = null;
        for (List<Curve> list : List.of(spec.curves, spec.outlines))
            for (Curve c : list)
                for (int i = 0; i < c.points.size(); i++) {
                    double d = c.points.get(i).dist(ip);
                    if (d <= bd) { bd = d; best = new CurveHit(c, i); }
                }
        return best;
    }

    /** Snap to an existing landmark or semi-landmark if one is under the cursor. */
    private Point2D snap(Point2D ip) {
        int lm = hitLandmark(ip);
        if (lm >= 0) return spec.landmarks.get(lm);
        CurveHit ch = hitCurvePoint(ip);
        if (ch != null) return ch.curve.points.get(ch.index);
        return ip;
    }

    private Curve hitCurve(Point2D ip) {
        if (spec == null) return null;
        double r = HIT_PX / zoom;
        for (List<Curve> list : List.of(spec.curves, spec.outlines))
            for (Curve c : list) {
                int n = c.points.size();
                int segs = c.closed ? n : n - 1;
                for (int i = 0; i < segs; i++) {
                    Point2D a = c.points.get(i), b = c.points.get((i + 1) % n);
                    if (Line2D.ptSegDist(a.x(), a.y(), b.x(), b.y(), ip.x(), ip.y()) <= r) return c;
                }
            }
        return null;
    }

    private void pressed(MouseEvent e) {
        dragStart = e.getPoint(); dragOffX = offX; dragOffY = offY;
        if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e) || e.isAltDown()) {
            panning = true; return;
        }
        if (spec == null) return;
        Point2D ip = toImage(e.getPoint());
        dragUndoSaved = false;
        switch (mode) {
            case SELECT -> {
                int hit = hitLandmark(ip);
                CurveHit ch;
                Curve c;
                if (hit >= 0) {
                    selectedLandmark = hit; clearCurveSelection(); movingLandmark = true;
                } else if ((ch = hitCurvePoint(ip)) != null) {
                    selectedLandmark = -1; selectedCurve = ch.curve; selectedCurvePoint = ch.index;
                    movingCurvePoint = true;
                } else if ((c = hitCurve(ip)) != null) {
                    selectedLandmark = -1; selectedCurve = c; selectedCurvePoint = -1;
                } else {
                    selectedLandmark = -1; clearCurveSelection();
                }
                app.changed();
            }
            case LANDMARK -> {
                int hit = hitLandmark(ip);
                if (hit >= 0) {
                    selectedLandmark = hit;
                    movingLandmark = true;
                    app.changed();
                } else {
                    app.undo.save(spec);
                    spec.landmarks.add(ip);
                    selectedLandmark = spec.landmarks.size() - 1;
                    app.changed();
                }
            }
            case SCALE -> {
                pending.add(snap(ip));
                if (pending.size() == 2) {
                    double px = pending.get(0).dist(pending.get(1));
                    repaint();
                    app.askScale(px);
                    pending.clear();
                }
            }
            case MEASURE -> {
                if (pending.size() >= 2) pending.clear();
                pending.add(snap(ip));
                if (pending.size() == 2) {
                    double px = pending.get(0).dist(pending.get(1));
                    measureText = app.formatLength(px);
                    app.status("Distance: " + measureText);
                } else measureText = null;
                app.changed();
            }
            case CURVE, OUTLINE -> pending.add(ip);
            case TRACE -> {
                int hit = hitLandmark(ip);
                if (hit >= 0 && !traceIdx.contains(hit)) traceIdx.add(hit);
            }
        }
        repaint();
    }

    private void dragged(MouseEvent e) {
        if (panning) {
            offX = dragOffX + (e.getX() - dragStart.x);
            offY = dragOffY + (e.getY() - dragStart.y);
            repaint();
        } else if (spec != null && (movingLandmark || movingCurvePoint)) {
            if (!dragUndoSaved) { app.undo.save(spec); dragUndoSaved = true; }
            Point2D ip = toImage(e.getPoint());
            if (movingLandmark && selectedLandmark >= 0) spec.landmarks.set(selectedLandmark, ip);
            else if (movingCurvePoint && selectedCurve != null && selectedCurvePoint >= 0)
                selectedCurve.points.set(selectedCurvePoint, ip);
            app.changed();
        }
    }

    private void key(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> clearPending();
            case KeyEvent.VK_ENTER -> finish();
            case KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE -> {
                if (e.isShiftDown() && selectedCurve != null) selectedCurvePoint = -1;
                deleteSelected();
            }
            case KeyEvent.VK_F -> fit();
            case KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS -> zoomAt(new Point(getWidth() / 2, getHeight() / 2), 1.25);
            case KeyEvent.VK_MINUS -> zoomAt(new Point(getWidth() / 2, getHeight() / 2), 0.8);
            default -> {}
        }
    }

    void deleteSelected() {
        if (spec == null) return;
        if (!pending.isEmpty() && (mode == Mode.CURVE || mode == Mode.OUTLINE)) {
            pending.remove(pending.size() - 1); repaint(); return;
        }
        if (selectedCurve != null) {
            app.undo.save(spec);
            int min = selectedCurve.closed ? 3 : 2;
            if (selectedCurvePoint >= 0 && selectedCurve.points.size() > min) {
                selectedCurve.points.remove(selectedCurvePoint);
                selectedCurvePoint = Math.min(selectedCurvePoint, selectedCurve.points.size() - 1);
            } else {
                spec.curves.remove(selectedCurve); spec.outlines.remove(selectedCurve);
                clearCurveSelection();
            }
            app.changed();
        } else if (selectedLandmark >= 0 && selectedLandmark < spec.landmarks.size()) {
            app.undo.save(spec);
            spec.landmarks.remove(selectedLandmark);
            selectedLandmark = Math.min(selectedLandmark, spec.landmarks.size() - 1);
            app.changed();
        }
    }

    private void finish() {
        if (spec == null) return;
        List<Point2D> ctrl;
        boolean closed;
        if (mode == Mode.CURVE || mode == Mode.OUTLINE) {
            ctrl = new ArrayList<>(pending); closed = mode == Mode.OUTLINE;
        } else if (mode == Mode.TRACE) {
            ctrl = new ArrayList<>(); for (int i : traceIdx) ctrl.add(spec.landmarks.get(i));
            int r = JOptionPane.showOptionDialog(this, "Create an open curve or closed outline?", "Trace",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new String[]{"Curve", "Outline", "Cancel"}, "Curve");
            if (r < 0 || r == 2) return;
            closed = r == 1;
        } else return;
        if (ctrl.size() < (closed ? 3 : 2)) { app.status("Need at least " + (closed ? 3 : 2) + " points"); return; }
        String def = String.valueOf(Math.max(ctrl.size(), closed ? 20 : 10));
        String s = JOptionPane.showInputDialog(this, "Number of semi-landmarks to generate:", def);
        if (s == null) return;
        int n;
        try { n = Integer.parseInt(s.trim()); } catch (NumberFormatException ex) { app.status("Invalid number"); return; }
        if (n < 2) { app.status("Need at least 2 points"); return; }
        app.undo.save(spec);
        Curve c = new Curve(Resample.smoothResample(ctrl, n, closed), closed);
        (closed ? spec.outlines : spec.curves).add(c);
        selectedCurve = c; selectedCurvePoint = -1;
        clearPending();
        app.changed();
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                zoom < 1 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (image != null) {
            AffineTransform at = new AffineTransform(zoom, 0, 0, zoom, offX, offY);
            g.drawImage(image, at, null);
        } else {
            g.setColor(Color.LIGHT_GRAY);
            String msg = spec == null ? "File > Open Images… to begin" : "Image not found: " + spec.image;
            g.drawString(msg, 20, 30);
        }
        if (spec == null) { g.dispose(); return; }

        g.setStroke(new BasicStroke(2f));
        for (Curve c : spec.curves) drawCurve(g, c, new Color(0, 200, 255));
        for (Curve c : spec.outlines) drawCurve(g, c, new Color(255, 160, 0));

        g.setFont(getFont().deriveFont(Font.BOLD, 12f));
        for (int i = 0; i < spec.landmarks.size(); i++) {
            var p = toScreen(spec.landmarks.get(i));
            boolean sel = i == selectedLandmark && (mode == Mode.LANDMARK || mode == Mode.SELECT);
            boolean traced = traceIdx.contains(i);
            g.setColor(Color.BLACK);
            g.fill(new Ellipse2D.Double(p.x - 6, p.y - 6, 12, 12));
            g.setColor(sel ? Color.YELLOW : traced ? Color.MAGENTA : Color.RED);
            g.fill(new Ellipse2D.Double(p.x - 4.5, p.y - 4.5, 9, 9));
            String lbl = String.valueOf(i + 1);
            g.setColor(Color.BLACK); g.drawString(lbl, (float) p.x + 8, (float) p.y - 5);
            g.setColor(Color.WHITE); g.drawString(lbl, (float) p.x + 7, (float) p.y - 6);
        }

        // highlight the point that a click would snap to / select
        if (hover != null && (mode == Mode.SELECT || mode == Mode.SCALE || mode == Mode.MEASURE || mode == Mode.TRACE)) {
            Point2D h = mode == Mode.TRACE ? (hitLandmark(hover) >= 0 ? spec.landmarks.get(hitLandmark(hover)) : null) : snap(hover);
            if (h != null && h != hover) {
                var p = toScreen(h);
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(1.5f));
                g.draw(new Ellipse2D.Double(p.x - 9, p.y - 9, 18, 18));
            }
        }

        // pending / in-progress geometry
        g.setColor(Color.GREEN);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{6, 4}, 0));
        List<Point2D> preview = new ArrayList<>(pending);
        if (mode == Mode.TRACE) for (int i : traceIdx) preview.add(spec.landmarks.get(i));
        if ((mode == Mode.CURVE || mode == Mode.OUTLINE || mode == Mode.TRACE) && preview.size() >= 2) {
            drawPoly(g, Resample.spline(preview, mode == Mode.OUTLINE), false);
        } else if ((mode == Mode.SCALE || mode == Mode.MEASURE) && pending.size() == 1 && hover != null) {
            drawPoly(g, List.of(pending.get(0), hover), false);
        } else if (pending.size() >= 2) drawPoly(g, pending, false);
        for (Point2D q : pending) {
            var p = toScreen(q); g.fill(new Ellipse2D.Double(p.x - 3, p.y - 3, 6, 6));
        }
        if (measureText != null && pending.size() == 2) {
            var p = toScreen(pending.get(1));
            g.setColor(Color.BLACK); g.drawString(measureText, (float) p.x + 9, (float) p.y + 15);
            g.setColor(Color.GREEN); g.drawString(measureText, (float) p.x + 8, (float) p.y + 14);
        }
        g.dispose();
    }

    private void drawCurve(Graphics2D g, Curve c, Color col) {
        boolean sel = c == selectedCurve;
        g.setColor(sel ? Color.YELLOW : col);
        g.setStroke(new BasicStroke(sel ? 3f : 2f));
        drawPoly(g, c.points, c.closed);
        for (int i = 0; i < c.points.size(); i++) {
            var p = toScreen(c.points.get(i));
            if (sel && i == selectedCurvePoint) {
                g.setColor(Color.BLACK); g.fill(new Ellipse2D.Double(p.x - 6, p.y - 6, 12, 12));
                g.setColor(Color.YELLOW); g.fill(new Ellipse2D.Double(p.x - 4.5, p.y - 4.5, 9, 9));
                g.setColor(col);
            } else g.draw(new Ellipse2D.Double(p.x - 3, p.y - 3, 6, 6));
        }
    }

    private void drawPoly(Graphics2D g, List<Point2D> pts, boolean closed) {
        if (pts.size() < 2) return;
        Path2D path = new Path2D.Double();
        var p0 = toScreen(pts.get(0)); path.moveTo(p0.x, p0.y);
        for (int i = 1; i < pts.size(); i++) { var p = toScreen(pts.get(i)); path.lineTo(p.x, p.y); }
        if (closed) path.closePath();
        g.draw(path);
    }

    /** Info text for the currently selected curve/outline. */
    String selectionInfo() {
        if (selectedCurve == null || spec == null) return null;
        List<Point2D> p = selectedCurve.points;
        StringBuilder sb = new StringBuilder();
        sb.append(selectedCurve.closed ? "Outline" : "Curve").append(" (").append(p.size()).append(" pts)\n");
        if (selectedCurvePoint >= 0 && selectedCurvePoint < p.size()) {
            Point2D q = p.get(selectedCurvePoint);
            sb.append(String.format(Locale.ROOT, "Point %d: %.1f, %.1f\n", selectedCurvePoint + 1, q.x(), q.y()));
        }
        if (selectedCurve.closed) {
            sb.append("Perimeter: ").append(app.formatLength(Measure.perimeter(p))).append('\n');
            sb.append("Area: ").append(app.formatArea(Measure.area(p))).append('\n');
        } else {
            sb.append("Length: ").append(app.formatLength(Measure.polylineLength(p))).append('\n');
        }
        return sb.toString();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(900, 700); }

    static String fmt(double d) { return String.format(Locale.ROOT, "%.3f", d); }
}
