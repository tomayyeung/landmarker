package landmarker;

import landmarker.geom.Measure;
import landmarker.geom.Resample;
import landmarker.io.TpsReader;
import landmarker.io.TpsWriter;
import landmarker.model.Point2D;
import landmarker.model.Specimen;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    static final String SAMPLE = """
            LM=3
            102.5 245.0
            150.2 310.8
            210.0 298.4
            CURVES=2
            POINTS=5
            105.0 248.2
            112.4 256.9
            121.0 268.3
            134.5 285.1
            148.0 308.4
            POINTS=4
            208.1 295.0
            199.4 276.2
            191.0 250.5
            185.6 220.1
            IMAGE=specimen_001.jpg
            ID=SPEC001
            SCALE=0.0152
            """;

    @Test void tpsRoundTrip() throws Exception {
        List<Specimen> a = new TpsReader().parse(SAMPLE.lines().toList());
        assertEquals(1, a.size());
        Specimen s = a.get(0);
        assertEquals(3, s.landmarks.size());
        assertEquals(2, s.curves.size());
        assertEquals(5, s.curves.get(0).points.size());
        assertEquals(4, s.curves.get(1).points.size());
        assertEquals("SPEC001", s.id);
        assertEquals(0.0152, s.scale, 1e-12);
        assertEquals(new Point2D(185.6, 220.1), s.curves.get(1).points.get(3));

        String out = TpsWriter.format(a);
        List<Specimen> b = new TpsReader().parse(out.lines().toList());
        assertEquals(TpsWriter.format(b), out);
        assertTrue(out.contains("SCALE=0.0152\n"));
    }

    @Test void multipleSpecimensAndOutlines() throws Exception {
        String txt = "LM=1\n1 2\nOUTLINES=1\nPOINTS=3\n0 0\n1 0\n0 1\nID=A\nCOMMENT=hello\n\nlm=0\nid=B\n";
        List<Specimen> l = new TpsReader().parse(txt.lines().toList());
        assertEquals(2, l.size());
        assertEquals(1, l.get(0).outlines.size());
        assertTrue(l.get(0).outlines.get(0).closed);
        assertEquals("hello", l.get(0).comment);
        assertEquals("B", l.get(1).id);
    }

    @Test void measurements() {
        List<Point2D> sq = List.of(new Point2D(0, 0), new Point2D(1, 0), new Point2D(1, 1), new Point2D(0, 1));
        assertEquals(1.0, Measure.area(sq), 1e-12);
        assertEquals(4.0, Measure.perimeter(sq), 1e-12);
        assertEquals(3.0, Measure.polylineLength(sq), 1e-12);
        assertEquals(8.0, Measure.scaleLength(4, 2.0), 1e-12);
        assertEquals(4.0, Measure.scaleArea(1, 2.0), 1e-12);
    }

    @Test void resampleEvenSpacing() {
        List<Point2D> line = List.of(new Point2D(0, 0), new Point2D(3, 0), new Point2D(10, 0));
        List<Point2D> r = Resample.byArcLength(line, 11, false);
        assertEquals(11, r.size());
        for (int i = 0; i < 11; i++) assertEquals(i, r.get(i).x(), 1e-9);

        List<Point2D> sq = List.of(new Point2D(0, 0), new Point2D(10, 0), new Point2D(10, 10), new Point2D(0, 10));
        List<Point2D> c = Resample.smoothResample(sq, 20, true);
        assertEquals(20, c.size());
        double mean = Measure.perimeter(c) / 20;
        for (int i = 0; i < 20; i++)
            assertEquals(mean, c.get(i).dist(c.get((i + 1) % 20)), mean * 0.1);
    }

    @Test void flipIsSelfInverse() {
        Specimen s = new Specimen();
        s.landmarks.add(new Point2D(5, 30));
        TpsReader.flipY(s, 100);
        assertEquals(70, s.landmarks.get(0).y(), 1e-12);
        TpsReader.flipY(s, 100);
        assertEquals(30, s.landmarks.get(0).y(), 1e-12);
    }
}
