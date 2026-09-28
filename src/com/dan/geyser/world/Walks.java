package com.dan.geyser.world;

import com.dan.geyser.core.Mat;
import com.dan.geyser.core.MeshBuilder;
import com.dan.geyser.core.Terrain;

/**
 * Stege, Brücken und der Aussichtspunkt. Die Wege folgen den Beschreibungen des Parks: ein Halbrund
 * um Old Faithful, über eine Brücke nach Geyser Hill mit einer Runde um Beehive, auf dem Südwestufer
 * an Castle vorbei, über den Fluss zu Grand, zurück auf das Westufer gegenüber von Riverside und bis
 * zum Ende bei Morning Glory; in Midway über den Fluss, am Krater von Excelsior entlang und um Grand
 * Prismatic, dazu die Plattform am Hang südlich davon; im Lower Geyser Basin eine Runde um den
 * Fountain Paint Pot. Die Linienführung ist genähert.
 */
final class Walks {
    private Walks() { }

    static final double WIDTH = 2.4, STEP = 2.5;

    static void build(MeshBuilder mb, Terrain t, double[] of, double[] bh, double[] ca, double[] gr, double[] rs, double[] mg,
                      double[] gps, double[] exc, double[] fpp) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        World.ROUTES.clear();
        java.util.List<double[]> arc = new java.util.ArrayList<>();
        for (int a = -25; a <= 205; a += 6) {
            double r = Math.toRadians(a);
            arc.add(new double[]{of[0] + 88 * Math.cos(r), of[1] + 88 * Math.sin(r)});
        }
        walk(mb, t, arc);
        double[] e = arc.get(0), w = arc.get(arc.size() - 1);
        walk(mb, t, pts(e[0], e[1], 87, -66, 113, -113, 60, -200));
        walk(mb, t, pts(60, -200, -40, -290, -130, -330, -215, -305, -200, -255, -110, -235, -20, -215, 60, -200));
        walk(mb, t, pts(w[0], w[1], -230, -120, -420, -205, -600, -270, -700, -265, -740, -380, -777, -460, -743, -489,
                -790, -560, -765, -640, -770, -730, -830, -860, -890, -1040, -925, -1128, -929, -1154, -969, -1146,
                -1000, -1240, -1040, -1440, -1110, -1560, -1182, -1622, -1200, -1632));
        java.util.List<double[]> mw = new java.util.ArrayList<>();
        mw.add(new double[]{-585, -7256});
        mw.add(new double[]{-632, -7250});
        mw.add(new double[]{-662, -7262});
        mw.add(new double[]{-700, -7262});
        for (int a = -55; a <= 60; a += 5) {
            double r = Math.toRadians(a);
            mw.add(new double[]{gps[0] + 82 * Math.cos(r), gps[1] + 82 * Math.sin(r)});
        }
        walk(mb, t, mw);
        // Fountain Paint Pot: Runde um die Schlammtöpfe, vom Parkplatz im Westen her
        java.util.List<double[]> fp = new java.util.ArrayList<>();
        fp.add(new double[]{fpp[0] - 150, fpp[1] + 70});
        fp.add(new double[]{fpp[0] - 80, fpp[1] + 50});
        for (int a = 160; a >= -160; a -= 8) {
            double r = Math.toRadians(a);
            fp.add(new double[]{fpp[0] + 34 * Math.cos(r), fpp[1] + 30 * Math.sin(r)});
        }
        walk(mb, t, fp);
        overlook(mb, t, -900, -6880, gps);
        mb.maxEdge = keep;
    }

    private static java.util.List<double[]> pts(double... v) {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < v.length; i += 2) l.add(new double[]{v[i], v[i + 1]});
        return l;
    }

    /** Ein Steg entlang der Punkte: alle 2,5 m ein Querschnitt, Decke geglättet über dem Boden, über Wasser als Brücke. */
    static void walk(MeshBuilder mb, Terrain t, java.util.List<double[]> in) {
        java.util.List<double[]> p = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < in.size(); i++) {
            double[] a = in.get(i), b = in.get(i + 1);
            double l = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int n = Math.max(1, (int) Math.ceil(l / STEP));
            for (int k = 0; k < n; k++) p.add(new double[]{a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n});
        }
        p.add(in.get(in.size() - 1));
        int n = p.size();
        double[] y = new double[n], gnd = new double[n], sx = new double[n], sz = new double[n];
        boolean[] bridge = new boolean[n];
        double[] o = new double[6];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double dx = p.get(b)[0] - p.get(a)[0], dz = p.get(b)[1] - p.get(a)[1], l = Math.hypot(dx, dz);
            sx[i] = -dz / l; sz[i] = dx / l;
            double x = p.get(i)[0], z = p.get(i)[1];
            double g = Math.max(t.sample(x, z), Math.max(t.sample(x + sx[i] * WIDTH / 2, z + sz[i] * WIDTH / 2), t.sample(x - sx[i] * WIDTH / 2, z - sz[i] * WIDTH / 2)));
            t.nearest(x, z, o);
            if (o[0] < o[2] + 3) { bridge[i] = true; g = Math.max(g, o[1] + 1.0); }
            gnd[i] = g;
        }
        for (int i = 0; i < n; i++) {
            double s = 0, c = 0;
            for (int k = -3; k <= 3; k++) { int j = Math.max(0, Math.min(n - 1, i + k)); s += gnd[j]; c++; }
            y[i] = Math.max(gnd[i] + (bridge[i] ? 0.35 : 0.3), s / c + 0.45);
        }
        double[][] deck = new double[n][];
        for (int i = 0; i < n; i++) deck[i] = new double[]{p.get(i)[0], y[i], p.get(i)[1]};
        World.ROUTES.add(deck);
        for (int i = 0; i + 1 < n; i++) {
            double[] a = p.get(i), b = p.get(i + 1);
            double h = WIDTH / 2;
            double[] al = {a[0] + sx[i] * h, y[i], a[1] + sz[i] * h}, ar = {a[0] - sx[i] * h, y[i], a[1] - sz[i] * h};
            double[] bl = {b[0] + sx[i + 1] * h, y[i + 1], b[1] + sz[i + 1] * h}, br = {b[0] - sx[i + 1] * h, y[i + 1], b[1] - sz[i + 1] * h};
            quad(mb, ar, br, bl, al, 0, 1, 0, Mat.BOARD);
            // Seitenbretter
            quad(mb, al, bl, off(bl, -0.28), off(al, -0.28), sx[i], 0, sz[i], Mat.BOARD);
            quad(mb, br, ar, off(ar, -0.28), off(br, -0.28), -sx[i], 0, -sz[i], Mat.BOARD);
            boolean br0 = bridge[i] || bridge[i + 1];
            if (i % 3 == 0) {
                for (int sd = -1; sd <= 1; sd += 2) {
                    double px = a[0] + sx[i] * h * 0.85 * sd, pz = a[1] + sz[i] * h * 0.85 * sd;
                    double gy = t.sample(px, pz);
                    if (br0) t.nearest(px, pz, o);
                    double bottom = br0 ? Math.min(gy, o[1] - 1.5) : gy - 0.3;
                    mb.box(px - 0.07, bottom, pz - 0.07, px + 0.07, y[i] - 0.05, pz + 0.07, Mat.WOOD, false);
                    if (br0) mb.box(px - 0.05, y[i], pz - 0.05, px + 0.05, y[i] + 1.0, pz + 0.05, Mat.WOOD, false);
                }
            }
            if (br0) {
                for (int sd = -1; sd <= 1; sd += 2) {
                    double[] ra = {a[0] + sx[i] * h * 0.9 * sd, y[i] + 0.95, a[1] + sz[i] * h * 0.9 * sd};
                    double[] rb = {b[0] + sx[i + 1] * h * 0.9 * sd, y[i + 1] + 0.95, b[1] + sz[i + 1] * h * 0.9 * sd};
                    rail(mb, ra, rb);
                }
            }
        }
    }

    private static double[] off(double[] p, double dy) { return new double[]{p[0], p[1] + dy, p[2]}; }

    private static void quad(MeshBuilder mb, double[] a, double[] b, double[] c, double[] d, double nx, double ny, double nz, int m) {
        int i0 = mb.v(a[0], a[1], a[2], nx, ny, nz), i1 = mb.v(b[0], b[1], b[2], nx, ny, nz);
        int i2 = mb.v(c[0], c[1], c[2], nx, ny, nz), i3 = mb.v(d[0], d[1], d[2], nx, ny, nz);
        mb.tri(i0, i1, i2, m);
        mb.tri(i0, i2, i3, m);
    }

    /** Handlauf: flacher Balken von a nach b, von beiden Seiten sichtbar. */
    private static void rail(MeshBuilder mb, double[] a, double[] b) {
        int keep = mb.group;
        mb.group = 1;
        quad(mb, off(a, 0.06), off(b, 0.06), off(b, -0.06), off(a, -0.06), 0.3, 0, 0.3, Mat.WOOD);
        mb.group = keep;
    }

    /** Plattform am Hang: 8 × 5 m, zum Becken hin mit Geländer. */
    private static void overlook(MeshBuilder mb, Terrain t, double cx, double cz, double[] look) {
        double dx = look[0] - cx, dz = look[1] - cz, l = Math.hypot(dx, dz);
        dx /= l; dz /= l;
        double px = -dz, pz = dx;
        double g = -1e9;
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) g = Math.max(g, t.sample(cx + dx * 2.5 * i + px * 4 * j, cz + dz * 2.5 * i + pz * 4 * j));
        double y = g + 0.4;
        World.OVERLOOK = new double[]{cx, y, cz};
        double[] a = {cx - dx * 2.5 - px * 4, y, cz - dz * 2.5 - pz * 4}, b = {cx - dx * 2.5 + px * 4, y, cz - dz * 2.5 + pz * 4};
        double[] c = {cx + dx * 2.5 + px * 4, y, cz + dz * 2.5 + pz * 4}, d = {cx + dx * 2.5 - px * 4, y, cz + dz * 2.5 - pz * 4};
        quad(mb, a, d, c, b, 0, 1, 0, Mat.BOARD);
        double[][] corners = {a, b, c, d};
        for (double[] q : corners) mb.box(q[0] - 0.08, t.sample(q[0], q[2]) - 0.3, q[2] - 0.08, q[0] + 0.08, y + 1.0, q[2] + 0.08, Mat.WOOD, false);
        rail(mb, new double[]{c[0], y + 0.95, c[2]}, new double[]{d[0], y + 0.95, d[2]});
        rail(mb, new double[]{b[0], y + 0.95, b[2]}, new double[]{c[0], y + 0.95, c[2]});
        rail(mb, new double[]{d[0], y + 0.95, d[2]}, new double[]{a[0], y + 0.95, a[2]});
    }
}
