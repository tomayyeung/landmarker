package landmarker.io;

import landmarker.model.Curve;
import landmarker.model.Point2D;
import landmarker.model.Specimen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Writes specimens as stored (callers flip Y beforehand if needed). */
public final class TpsWriter {
    private TpsWriter() {}

    public static String format(List<Specimen> specimens) {
        StringBuilder sb = new StringBuilder();
        for (Specimen s : specimens) {
            sb.append("LM=").append(s.landmarks.size()).append('\n');
            pts(sb, s.landmarks);
            curves(sb, "CURVES", s.curves);
            curves(sb, "OUTLINES", s.outlines);
            if (s.image != null) sb.append("IMAGE=").append(s.image).append('\n');
            if (s.id != null) sb.append("ID=").append(s.id).append('\n');
            if (s.scale != null) sb.append("SCALE=").append(num(s.scale)).append('\n');
            if (s.comment != null && !s.comment.isBlank())
                sb.append("COMMENT=").append(s.comment.replace('\n', ' ')).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void curves(StringBuilder sb, String tag, List<Curve> cs) {
        if (cs.isEmpty()) return;
        sb.append(tag).append('=').append(cs.size()).append('\n');
        for (Curve c : cs) {
            sb.append("POINTS=").append(c.points.size()).append('\n');
            pts(sb, c.points);
        }
    }

    private static void pts(StringBuilder sb, List<Point2D> pts) {
        for (Point2D p : pts) sb.append(String.format(Locale.ROOT, "%.3f %.3f%n", p.x(), p.y()));
    }

    private static String num(double d) {
        String s = String.format(Locale.ROOT, "%.10f", d).replaceAll("0+$", "");
        return s.endsWith(".") ? s + "0" : s;
    }

    public static void write(Path p, List<Specimen> specimens) throws IOException {
        Files.writeString(p, format(specimens));
    }
}
