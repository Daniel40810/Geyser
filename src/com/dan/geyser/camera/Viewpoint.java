package com.dan.geyser.camera;

import com.dan.geyser.core.Terrain;
import com.dan.geyser.world.World;

/**
 * Feste Blickpunkte mit Namen: Augpunkt und Blickziel, dazu der Ort (0 Upper Geyser Basin, 1
 * Midway, 2 Lower Geyser Basin). Die Augpunkte auf den Stegen stehen 1,7 m über den Brettern; die Lage der Geysire folgt
 * den Koordinaten in {@link com.dan.geyser.world.Sites}.
 */
public final class Viewpoint {
    public final String name, note;
    public final double[] pose;      // ex, ey, ez, tx, ty, tz
    public final int site;

    public Viewpoint(String name, String note, int site, double[] pose) {
        this.name = name; this.note = note; this.site = site; this.pose = pose;
    }

    /** Namen in der Reihenfolge von {@link #all}, schon vor dem Bau bekannt (für das Bedienfeld). */
    public static final String[] NAMES = {"Übersicht", "Old Faithful · Bänke", "Geyser Hill · Beehive", "Castle Geyser", "Grand Geyser",
            "Riverside Geyser", "Morning Glory Pool", "Luftbild", "Grand Prismatic · Aussicht", "Excelsior · Steg", "Grand Prismatic · Luftbild",
            "Fountain Paint Pot"};

    private static Viewpoint[] all;

    /** Die Blickpunkte (einmal nach dem Bau des Geländes angelegt). */
    public static synchronized Viewpoint[] all(Terrain t) {
        if (all != null) return all;
        all = new Viewpoint[]{
                new Viewpoint("Übersicht", "Das Upper Geyser Basin von Südosten, Old Faithful vorn", 0,
                        orbit(-520, t.sample(-520, -700) + 8, -700, 62, 19, 1750)),
                new Viewpoint("Old Faithful · Bänke", "Vom Halbrund der Stege, 90 m vor dem Kegel", 0,
                        new double[]{44, deck(t, 44, 76), 76, 0, 16, 0}),
                new Viewpoint("Geyser Hill · Beehive", "Vom Rundweg über Geyser Hill auf den Kegel von Beehive", 0,
                        new double[]{-110, deck(t, -110, -235), -235, -143, 10, -280}),
                new Viewpoint("Castle Geyser", "Vom Hauptweg auf den Kegel mit den Zinnen", 0,
                        new double[]{-622, deck(t, -622, -262), -262, -678, 6, -331}),
                new Viewpoint("Grand Geyser", "Vom Steg an der Böschung auf das Becken", 0,
                        new double[]{-760, deck(t, -760, -738), -738, -806, 6, -681}),
                new Viewpoint("Riverside Geyser", "Vom Westufer über den Firehole", 0,
                        new double[]{-1040, deck(t, -1040, -1440), -1440, -983, -4, -1453}),
                new Viewpoint("Morning Glory Pool", "Am Ende des Weges, von oben in die Quelle", 0,
                        new double[]{-1196, deck(t, -1196, -1604) + 1.2, -1604, -1226, -16, -1618}),
                new Viewpoint("Luftbild", "Das ganze Becken aus 350 m Höhe", 0,
                        new double[]{-180, 360, 260, -720, -10, -920}),
                new Viewpoint("Grand Prismatic · Aussicht", "Von der Plattform am Hang südlich der Quelle", 1,
                        new double[]{World.OVERLOOK[0], World.OVERLOOK[1] + 1.7, World.OVERLOOK[2], -789, -24, -7171}),
                new Viewpoint("Excelsior · Steg", "Am Rand des Kraters, 1985 zuletzt ausgebrochen", 1,
                        new double[]{-662, deck(t, -662, -7262), -7262, -694, -26, -7317}),
                new Viewpoint("Grand Prismatic · Luftbild", "Die Farbringe von oben", 1,
                        new double[]{-640, 190, -7020, -789, -24, -7171}),
                new Viewpoint("Fountain Paint Pot", "Vom Steg in die Schlammtöpfe des Lower Geyser Basin", 2,
                        new double[]{World.LOWER[0] - 26, deck(t, World.LOWER[0] - 26, World.LOWER[1] + 20) + 1.0, World.LOWER[1] + 20,
                                World.LOWER[0], t.sample(World.LOWER[0], World.LOWER[1]), World.LOWER[1]})};
        return all;
    }

    /** Pose im Orbit-Maß (Drehpunkt, Gier, Nick, Abstand) als Augpunkt und Blickziel. */
    static double[] orbit(double tx, double ty, double tz, double yawDeg, double pitchDeg, double d) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
        return new double[]{tx + d * Math.cos(p) * Math.sin(y), ty + d * Math.sin(p), tz + d * Math.cos(p) * Math.cos(y), tx, ty, tz};
    }

    /** Augenhöhe auf dem nächsten Steg (1,7 m über den Brettern), sonst über dem Boden. */
    static double deck(Terrain t, double x, double z) {
        double best = 30, y = t.sample(x, z) + 0.45;
        for (double[][] r : World.ROUTES) {
            for (double[] p : r) {
                double d = Math.hypot(p[0] - x, p[2] - z);
                if (d < best) { best = d; y = p[1]; }
            }
        }
        return y + 1.7;
    }
}
