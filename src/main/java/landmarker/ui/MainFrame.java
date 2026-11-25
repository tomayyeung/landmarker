package landmarker.ui;

import landmarker.geom.Measure;
import landmarker.io.TpsReader;
import landmarker.io.TpsWriter;
import landmarker.model.Point2D;
import landmarker.model.Specimen;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public class MainFrame extends JFrame {
    final UndoManager undo = new UndoManager();
    private final DefaultListModel<Specimen> specimens = new DefaultListModel<>();
    private final JList<Specimen> specimenList = new JList<>(specimens);
    private final ImageCanvas canvas = new ImageCanvas(this);
    private final JLabel statusBar = new JLabel(" ");
    private final JLabel cursorLabel = new JLabel(" ");
    private final JTextField idField = new JTextField(), commentField = new JTextField();
    private final JLabel scaleLabel = new JLabel("-");
    private final JButton applyScaleBtn = new JButton("Apply scale to all specimens");
    private final JTextArea infoArea = new JTextArea(6, 20);
    private final DefaultListModel<String> lmModel = new DefaultListModel<>();
    private final JList<String> lmList = new JList<>(lmModel);
    private final JCheckBoxMenuItem flipY = new JCheckBoxMenuItem("Flip Y on save/load (TPS origin bottom-left)", true);

    /** Absolute image file per specimen; image height cache. */
    private final Map<Specimen, File> imageFiles = new IdentityHashMap<>();
    private final Map<Specimen, BufferedImage> images = new IdentityHashMap<>();
    private File tpsFile;
    private String units = "mm";
    private boolean dirty, updatingFields;

    public MainFrame() {
        super("Landmarker");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { if (confirmDiscard()) System.exit(0); }
        });
        setJMenuBar(buildMenu());
        add(buildToolbar(), BorderLayout.NORTH);

        specimenList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        specimenList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelected(); });
        JScrollPane left = new JScrollPane(specimenList);
        left.setPreferredSize(new Dimension(180, 0));
        left.setBorder(BorderFactory.createTitledBorder("Specimens"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left,
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, buildInfoPanel()));
        ((JSplitPane) split.getRightComponent()).setResizeWeight(1);
        add(split, BorderLayout.CENTER);

        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        status.add(statusBar, BorderLayout.CENTER);
        status.add(cursorLabel, BorderLayout.EAST);
        add(status, BorderLayout.SOUTH);

        canvas.addComponentListener(new ComponentAdapter() {
            private boolean first = true;
            @Override public void componentResized(ComponentEvent e) { if (first) { first = false; canvas.fit(); } }
        });
        setSize(1300, 850);
        setLocationRelativeTo(null);
        status(Mode.LANDMARK.hint);
    }

    // ---------- UI construction ----------

    private JMenuBar buildMenu() {
        int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(item("Open Images…", KeyStroke.getKeyStroke(KeyEvent.VK_O, mod), e -> openImages()));
        file.add(item("Open TPS…", KeyStroke.getKeyStroke(KeyEvent.VK_O, mod | InputEvent.SHIFT_DOWN_MASK), e -> openTps()));
        file.addSeparator();
        file.add(item("Save TPS", KeyStroke.getKeyStroke(KeyEvent.VK_S, mod), e -> save(false)));
        file.add(item("Save TPS As…", KeyStroke.getKeyStroke(KeyEvent.VK_S, mod | InputEvent.SHIFT_DOWN_MASK), e -> save(true)));
        file.addSeparator();
        file.add(item("Remove Specimen", null, e -> removeSpecimen()));
        file.add(flipY);
        mb.add(file);

        JMenu edit = new JMenu("Edit");
        edit.add(item("Undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, mod), e -> doUndo(false)));
        edit.add(item("Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, mod | InputEvent.SHIFT_DOWN_MASK), e -> doUndo(true)));
        edit.addSeparator();
        edit.add(item("Delete Selected", null, e -> canvas.deleteSelected()));
        edit.addSeparator();
        edit.add(item("Convert Selected Curve to Landmarks", KeyStroke.getKeyStroke(KeyEvent.VK_L, mod), e -> canvas.convertSelectedCurve()));
        edit.add(item("Convert All Curves to Landmarks (This Specimen)", null, e -> canvas.convertAllCurves()));
        edit.add(item("Convert All Curves to Landmarks (All Specimens)…", null, e -> convertAllSpecimens()));
        edit.addSeparator();
        edit.add(item("Clear Scale", null, e -> { Specimen s = current(); if (s != null) { undo.save(s); s.scale = null; changed(); } }));
        edit.add(item("Apply Scale to All Specimens", null, e -> applyScaleToAll()));
        mb.add(edit);

        JMenu view = new JMenu("View");
        view.add(item("Fit to Window", KeyStroke.getKeyStroke(KeyEvent.VK_0, mod), e -> canvas.fit()));
        view.add(item("Previous Specimen", KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, mod), e -> step(-1)));
        view.add(item("Next Specimen", KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, mod), e -> step(1)));
        mb.add(view);
        return mb;
    }

    private JMenuItem item(String name, KeyStroke ks, ActionListener l) {
        JMenuItem it = new JMenuItem(name);
        if (ks != null) it.setAccelerator(ks);
        it.addActionListener(l);
        return it;
    }

    private JToolBar buildToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        ButtonGroup grp = new ButtonGroup();
        for (Mode m : Mode.values()) {
            JToggleButton b = new JToggleButton(m.label);
            b.setToolTipText(m.hint);
            b.setFocusable(false);
            b.addActionListener(e -> { canvas.setMode(m); status(m.hint); canvas.requestFocusInWindow(); });
            if (m == Mode.LANDMARK) b.setSelected(true);
            grp.add(b); tb.add(b);
        }
        tb.addSeparator();
        tb.add(new JLabel("  Pan: right-drag or ⌥-drag · Zoom: wheel · F: fit  "));
        return tb;
    }

    private JComponent buildInfoPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel meta = new JPanel(new GridBagLayout());
        meta.setBorder(BorderFactory.createTitledBorder("Specimen"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(2, 2, 2, 2);
        c.gridy = 0; c.gridx = 0; meta.add(new JLabel("ID"), c);
        c.gridx = 1; c.weightx = 1; meta.add(idField, c);
        c.gridy = 1; c.gridx = 0; c.weightx = 0; meta.add(new JLabel("Comment"), c);
        c.gridx = 1; c.weightx = 1; meta.add(commentField, c);
        c.gridy = 2; c.gridx = 0; c.weightx = 0; meta.add(new JLabel("Scale"), c);
        c.gridx = 1; meta.add(scaleLabel, c);
        c.gridy = 3; c.gridx = 1;
        applyScaleBtn.setToolTipText("Copy this specimen's scale to every specimen in the project");
        applyScaleBtn.addActionListener(e -> applyScaleToAll());
        meta.add(applyScaleBtn, c);
        bind(idField, (s, v) -> { s.id = v.isBlank() ? null : v; specimenList.repaint(); });
        bind(commentField, (s, v) -> s.comment = v.isBlank() ? null : v);
        p.add(meta);

        JPanel lm = new JPanel(new BorderLayout());
        lm.setBorder(BorderFactory.createTitledBorder("Landmarks"));
        lmList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        lmList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lmList.getSelectedIndex() >= 0) {
                canvas.selectedLandmark = lmList.getSelectedIndex(); canvas.clearCurveSelection(); canvas.repaint();
            }
        });
        lm.add(new JScrollPane(lmList), BorderLayout.CENTER);
        JPanel btns = new JPanel(new GridLayout(1, 3, 2, 2));
        JButton up = new JButton("▲"), down = new JButton("▼"), del = new JButton("Delete");
        up.setToolTipText("Move landmark earlier (renumber)");
        down.setToolTipText("Move landmark later (renumber)");
        up.addActionListener(e -> moveLandmark(-1));
        down.addActionListener(e -> moveLandmark(1));
        del.addActionListener(e -> { canvas.clearCurveSelection(); canvas.deleteSelected(); });
        btns.add(up); btns.add(down); btns.add(del);
        lm.add(btns, BorderLayout.SOUTH);
        lm.setPreferredSize(new Dimension(260, 320));
        p.add(lm);

        infoArea.setEditable(false);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane info = new JScrollPane(infoArea);
        info.setBorder(BorderFactory.createTitledBorder("Measurements"));
        p.add(info);
        p.setPreferredSize(new Dimension(280, 0));
        return p;
    }

    private interface Setter { void set(Specimen s, String v); }

    private void bind(JTextField f, Setter setter) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            void upd() { Specimen s = current(); if (s != null && !updatingFields) { setter.set(s, f.getText()); dirty = true; } }
            public void insertUpdate(DocumentEvent e) { upd(); }
            public void removeUpdate(DocumentEvent e) { upd(); }
            public void changedUpdate(DocumentEvent e) { upd(); }
        });
    }

    // ---------- State ----------

    Specimen current() { return specimenList.getSelectedValue(); }

    void status(String s) { statusBar.setText(s); }

    void showCursor(Point2D p) {
        cursorLabel.setText(p == null ? " " : String.format(Locale.ROOT, "x %.1f  y %.1f px", p.x(), p.y()));
    }

    /** Called after any change to the current specimen's geometry. */
    void changed() { dirty = true; refreshInfo(); canvas.repaint(); }

    private void refreshInfo() {
        Specimen s = current();
        updatingFields = true;
        idField.setText(s == null || s.id == null ? "" : s.id);
        commentField.setText(s == null || s.comment == null ? "" : s.comment);
        updatingFields = false;
        applyScaleBtn.setEnabled(s != null && s.scale != null && specimens.size() > 1);
        scaleLabel.setText(s == null || s.scale == null ? "not set (pixels)" :
                String.format(Locale.ROOT, "%.6g %s/px", s.scale, units));

        int sel = canvas.selectedLandmark;
        lmModel.clear();
        if (s != null) for (int i = 0; i < s.landmarks.size(); i++) {
            Point2D p = s.landmarks.get(i);
            lmModel.addElement(String.format(Locale.ROOT, "%3d  %8.1f %8.1f", i + 1, p.x(), p.y()));
        }
        if (sel >= 0 && sel < lmModel.size()) lmList.setSelectedIndex(sel);

        StringBuilder sb = new StringBuilder();
        if (s != null) {
            sb.append("Landmarks: ").append(s.landmarks.size()).append('\n');
            sb.append("Curves: ").append(s.curves.size()).append("   Outlines: ").append(s.outlines.size()).append('\n');
            if (s.landmarks.size() >= 2)
                sb.append("LM path length: ").append(formatLength(Measure.polylineLength(s.landmarks))).append('\n');
            if (s.landmarks.size() >= 3) {
                sb.append("LM polygon perimeter: ").append(formatLength(Measure.perimeter(s.landmarks))).append('\n');
                sb.append("LM polygon area: ").append(formatArea(Measure.area(s.landmarks))).append('\n');
            }
            String selInfo = canvas.selectionInfo();
            if (selInfo != null) sb.append("\nSelected ").append(selInfo);
        }
        infoArea.setText(sb.toString());
    }

    String formatLength(double px) {
        Specimen s = current();
        if (s == null || s.scale == null) return String.format(Locale.ROOT, "%.2f px", px);
        return String.format(Locale.ROOT, "%.3f %s (%.1f px)", Measure.scaleLength(px, s.scale), units, px);
    }

    String formatArea(double px2) {
        Specimen s = current();
        if (s == null || s.scale == null) return String.format(Locale.ROOT, "%.1f px²", px2);
        return String.format(Locale.ROOT, "%.3f %s²", Measure.scaleArea(px2, s.scale), units);
    }

    void askScale(double px) {
        Specimen s = current();
        if (s == null || px <= 0) return;
        JTextField len = new JTextField(8), unit = new JTextField(units, 5);
        JPanel p = new JPanel(new GridLayout(0, 2, 4, 4));
        p.add(new JLabel(String.format(Locale.ROOT, "Pixel distance: %.2f", px))); p.add(new JLabel());
        p.add(new JLabel("Real length:")); p.add(len);
        p.add(new JLabel("Units:")); p.add(unit);
        if (JOptionPane.showConfirmDialog(this, p, "Set Scale", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            double real = Double.parseDouble(len.getText().trim());
            if (real <= 0) throw new NumberFormatException();
            undo.save(s);
            s.scale = real / px;
            if (!unit.getText().isBlank()) units = unit.getText().trim();
            status(String.format(Locale.ROOT, "Scale set: %.6g %s per pixel", s.scale, units));
            changed();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a positive number.");
        }
    }

    private void applyScaleToAll() {
        Specimen s = current();
        if (s == null || s.scale == null) { status("Current specimen has no scale"); return; }
        int others = 0;
        for (int i = 0; i < specimens.size(); i++) {
            Double sc = specimens.get(i).scale;
            if (specimens.get(i) != s && sc != null && !sc.equals(s.scale)) others++;
        }
        String msg = String.format(Locale.ROOT, "Set scale %.6g %s/px on all %d specimens?", s.scale, units, specimens.size())
                + (others > 0 ? "\n" + others + " specimen(s) already have a different scale and will be overwritten." : "");
        if (JOptionPane.showConfirmDialog(this, msg, "Apply Scale", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        for (int i = 0; i < specimens.size(); i++) specimens.get(i).scale = s.scale;
        dirty = true;
        status("Scale applied to all " + specimens.size() + " specimens (not undoable)");
    }

    private void convertAllSpecimens() {
        List<Specimen> targets = new ArrayList<>();
        int nCurves = 0, total = 0;
        for (int i = 0; i < specimens.size(); i++) {
            Specimen s = specimens.get(i);
            if (s.curves.isEmpty() && s.outlines.isEmpty()) continue;
            targets.add(s);
            nCurves += s.curves.size() + s.outlines.size();
            for (var c : s.curves) total += c.points.size();
            for (var c : s.outlines) total += c.points.size();
        }
        if (targets.isEmpty()) { status("No curves or outlines to convert"); return; }
        String msg = "Convert " + nCurves + " curve(s)/outline(s) in " + targets.size()
                + " specimen(s) to landmarks?\nThe curves will be removed.";
        if (JOptionPane.showConfirmDialog(this, msg, "Convert Curves", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        undo.saveAll(targets);
        int added = 0;
        for (Specimen s : targets) added += s.allCurvesToLandmarks();
        canvas.clearCurveSelection();
        changed();
        status("Converted " + nCurves + " curve(s)/outline(s) in " + targets.size() + " specimen(s) to " + added
                + " landmark(s)" + (total > added ? " (" + (total - added) + " duplicate(s) skipped)" : ""));
    }

    private void moveLandmark(int dir) {
        Specimen s = current();
        int i = lmList.getSelectedIndex(), j = i + dir;
        if (s == null || i < 0 || j < 0 || j >= s.landmarks.size()) return;
        undo.save(s);
        Collections.swap(s.landmarks, i, j);
        canvas.selectedLandmark = j;
        changed();
    }

    private void doUndo(boolean redo) {
        Specimen s = redo ? undo.redo() : undo.undo();
        if (s == null) { status("Nothing to " + (redo ? "redo" : "undo")); return; }
        if (s != current()) specimenList.setSelectedValue(s, true);
        canvas.clearCurveSelection();
        canvas.selectedLandmark = Math.min(canvas.selectedLandmark, s.landmarks.size() - 1);
        changed();
    }

    private void step(int d) {
        int i = specimenList.getSelectedIndex() + d;
        if (i >= 0 && i < specimens.size()) specimenList.setSelectedIndex(i);
    }

    private void showSelected() {
        Specimen s = current();
        canvas.setSpecimen(s, s == null ? null : loadImage(s));
        refreshInfo();
        setTitle("Landmarker" + (tpsFile != null ? " — " + tpsFile.getName() : "") + (s != null ? " — " + s : ""));
    }

    private BufferedImage loadImage(Specimen s) {
        if (images.containsKey(s)) return images.get(s);
        File f = imageFiles.get(s);
        BufferedImage img = null;
        if (f != null && f.isFile()) {
            try { img = ImageIO.read(f); } catch (IOException ex) { status("Could not read image: " + ex.getMessage()); }
        }
        images.put(s, img);
        return img;
    }

    private void removeSpecimen() {
        Specimen s = current();
        if (s == null) return;
        if (JOptionPane.showConfirmDialog(this, "Remove " + s + " from the project?", "Remove",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        specimens.removeElement(s);
        imageFiles.remove(s); images.remove(s);
        dirty = true;
        if (!specimens.isEmpty()) specimenList.setSelectedIndex(0);
    }

    // ---------- Files ----------

    private File lastDir = new File(System.getProperty("user.home"));

    private void openImages() {
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new FileNameExtensionFilter("Images", ImageIO.getReaderFileSuffixes()));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        lastDir = fc.getCurrentDirectory();
        int first = specimens.size();
        for (File f : fc.getSelectedFiles()) {
            Specimen s = new Specimen();
            s.image = f.getName();
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            s.id = dot > 0 ? name.substring(0, dot) : name;
            imageFiles.put(s, f.getAbsoluteFile());
            specimens.addElement(s);
        }
        if (specimens.size() > first) { dirty = true; specimenList.setSelectedIndex(first); }
    }

    private void openTps() {
        if (!confirmDiscard()) return;
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setFileFilter(new FileNameExtensionFilter("TPS files", "tps", "TPS", "txt"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        lastDir = fc.getCurrentDirectory();
        File f = fc.getSelectedFile();
        try {
            TpsReader r = new TpsReader();
            List<Specimen> list = r.read(f.toPath());
            specimens.clear(); imageFiles.clear(); images.clear();
            List<String> warnings = new ArrayList<>(new LinkedHashSet<>(r.warnings));
            File dir = f.getAbsoluteFile().getParentFile();
            for (Specimen s : list) {
                if (s.image != null) {
                    File img = new File(s.image);
                    if (!img.isAbsolute()) img = new File(dir, s.image);
                    if (!img.isFile()) img = new File(dir, new File(s.image).getName());
                    imageFiles.put(s, img);
                }
                if (flipY.isSelected()) {
                    BufferedImage img = loadImage(s);
                    if (img != null) TpsReader.flipY(s, img.getHeight());
                    else warnings.add("Image missing for " + s + "; Y not flipped");
                }
                specimens.addElement(s);
            }
            tpsFile = f; dirty = false;
            if (!specimens.isEmpty()) specimenList.setSelectedIndex(0);
            status("Loaded " + list.size() + " specimens from " + f.getName());
            if (!warnings.isEmpty()) JOptionPane.showMessageDialog(this, String.join("\n", warnings), "Warnings", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to read TPS: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean save(boolean as) {
        if (specimens.isEmpty()) { status("Nothing to save"); return false; }
        File f = tpsFile;
        if (as || f == null) {
            JFileChooser fc = new JFileChooser(lastDir);
            fc.setFileFilter(new FileNameExtensionFilter("TPS files", "tps"));
            File dir0 = tpsFile != null ? tpsFile.getParentFile() : lastDir;
            Specimen cur = current();
            String base = cur != null ? cur.toString().replaceAll("[\\\\/:*?\"<>|]", "_") : "landmarks";
            if (base.toLowerCase(Locale.ROOT).endsWith(".tps")) base = base.substring(0, base.length() - 4);
            fc.setSelectedFile(new File(dir0, base + ".tps"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
            f = fc.getSelectedFile();
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".tps")) f = new File(f.getPath() + ".tps");
            if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " exists. Overwrite?", "Save",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return false;
        }
        Path dir = f.getAbsoluteFile().getParentFile().toPath();
        List<Specimen> out = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < specimens.size(); i++) {
            Specimen src = specimens.get(i), s = src.copy();
            File img = imageFiles.get(src);
            if (img != null) {
                Path ip = img.toPath().toAbsolutePath();
                s.image = ip.startsWith(dir) ? dir.relativize(ip).toString() : ip.toString();
            }
            if (flipY.isSelected()) {
                BufferedImage bi = loadImage(src);
                if (bi != null) TpsReader.flipY(s, bi.getHeight());
                else warnings.add("Image missing for " + src + "; Y not flipped");
            }
            out.add(s);
        }
        try {
            TpsWriter.write(f.toPath(), out);
            tpsFile = f; dirty = false;
            status("Saved " + out.size() + " specimens to " + f.getName());
            if (!warnings.isEmpty()) JOptionPane.showMessageDialog(this, String.join("\n", warnings), "Warnings", JOptionPane.WARNING_MESSAGE);
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean confirmDiscard() {
        if (!dirty) return true;
        int r = JOptionPane.showConfirmDialog(this, "Save changes first?", "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION);
        if (r == JOptionPane.YES_OPTION) return save(false);
        return r == JOptionPane.NO_OPTION;
    }
}
