package landmarker.io;

import landmarker.model.Curve;
import landmarker.model.Point2D;
import landmarker.model.Specimen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Reads TPS files. Coordinates are returned exactly as stored in the file;
 * use {@link #flipY} to convert from TPS (origin bottom-left) to image pixels.
 */
public final class TpsReader {
    public final List<String> warnings = new ArrayList<>();

    public List<Specimen> read(Path p) throws IOException { return parse(Files.readAllLines(p)); }

    public List<Specimen> parse(List<String> raw) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String l : raw) if (!l.isBlank()) lines.add(l.strip());
        List<Specimen> out = new ArrayList<>();
        Specimen cur = null;
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            int eq = line.indexOf('=');
            if (eq < 0) throw new IOException("Unexpected line " + (i + 1) + ": " + line);
            String tag = line.substring(0, eq).trim().toUpperCase(Locale.ROOT);
            String val = line.substring(eq + 1).trim();
            i++;
            switch (tag) {
                case "LM", "LM3" -> {
                    cur = new Specimen();
                    out.add(cur);
                    int n = Integer.parseInt(val);
                    if (tag.equals("LM3")) warnings.add("LM3 data: z coordinates dropped");
                    i = readPoints(lines, i, n, cur.landmarks);
                }
                case "CURVES", "OUTLINES" -> {
                    if (cur == null) { cur = new Specimen(); out.add(cur); }
                    boolean closed = tag.equals("OUTLINES");
                    int nc = Integer.parseInt(val);
                    for (int c = 0; c < nc; c++) {
                        String pl = lines.get(i++);
                        if (!pl.toUpperCase(Locale.ROOT).startsWith("POINTS="))
                            throw new IOException("Expected POINTS= but got: " + pl);
                        int np = Integer.parseInt(pl.substring(7).trim());
                        Curve cv = new Curve(closed);
                        i = readPoints(lines, i, np, cv.points);
                        (closed ? cur.outlines : cur.curves).add(cv);
                    }
                }
                default -> {
                    if (cur == null) { cur = new Specimen(); out.add(cur); }
                    switch (tag) {
                        case "IMAGE" -> cur.image = val;
                        case "ID" -> cur.id = val;
                        case "SCALE" -> cur.scale = Double.parseDouble(val);
                        case "COMMENT" -> cur.comment = val;
                        default -> warnings.add("Ignored unknown tag: " + tag);
                    }
                }
            }
        }
        return out;
    }

    private int readPoints(List<String> lines, int i, int n, List<Point2D> dst) throws IOException {
        for (int k = 0; k < n; k++) {
            if (i >= lines.size()) throw new IOException("Unexpected end of file reading coordinates");
            String[] parts = lines.get(i++).split("[\\s,]+");
            if (parts.length < 2) throw new IOException("Bad coordinate line: " + lines.get(i - 1));
            dst.add(new Point2D(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
        }
        return i;
    }

    /** Flip all y coordinates: y -> height - y. Self-inverse, used for both read and write. */
    public static void flipY(Specimen s, double height) {
        s.landmarks.replaceAll(p -> new Point2D(p.x(), height - p.y()));
        for (Curve c : s.curves) c.points.replaceAll(p -> new Point2D(p.x(), height - p.y()));
        for (Curve c : s.outlines) c.points.replaceAll(p -> new Point2D(p.x(), height - p.y()));
    }
}
