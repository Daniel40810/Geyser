package com.dan.geyser.camera;

import com.dan.geyser.core.Terrain;
import com.dan.geyser.effects.DayNightCycle;
import com.dan.geyser.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Die fertigen Programme: vier Kamerafahrten, der Rundgang auf den Stegen und das Drehbuch
 * „Ein Tag am Old Faithful“. Die Uhrzeiten im Drehbuch rechnet es aus dem Sonnenstand des
 * gewählten Tages: Sonnenaufgang, die Stunde, in der die Sonne 20° hoch steht (dann liegt der
 * Regenbogen gut sichtbar in der Gischt), Sonnenuntergang. Die Kamera für den Regenbogen steht so,
 * dass der Gegenpunkt der Sonne rund 40° neben dem Geysir liegt, der Bogen also durch die Säule
 * läuft. {@link #clearance} fährt alle Wege ab und meldet, wie nah sie dem Boden kommen.
 */
public final class Regie {
    private Regie() { }

    public static final String[] FLIGHTS = {"Kranfahrt am Old Faithful", "Den Firehole hinab", "Um Grand Prismatic", "Von Midway ins Upper Basin"};

    /** Geysir-Orte (x, Höhe der Säulenmitte, z). */
    static final double[] OF = {0, 18, 0}, BEEHIVE = {-143, 14, -280}, CASTLE = {-678, 8, -331}, GRAND = {-806, 12, -681},
            GPS = {-789, -24, -7171};

    // ------------------------------------------------------------ Fahrten

    public static Director.Program flight(Terrain t, int i) {
        Director.Program pr = new Director.Program(FLIGHTS[i]);
        switch (i) {
            case 0: {
                CameraPath p = new CameraPath()
                        .add(0, 52, Viewpoint.deck(t, 52, 70), 70, 0, 8, 0)
                        .add(8, 66, 16, 92, 0, 22, 0)
                        .add(16, 86, 62, 118, 0, 30, 0)
                        .add(24, 50, 150, 200, -120, 20, -220)
                        .add(33, -120, 280, 260, -520, 0, -720);
                pr.add(new Director.Shot(p, Double.NaN, Double.NaN, "Old Faithful",
                        "Vom Steg bis über das Becken: Säule 32 bis 56 m, Ausbruch 1½ bis 5 Minuten.", "Wikipedia: Old Faithful")
                        .site(0).trigger("Old Faithful", 2.5));
                return pr;
            }
            case 1: {
                CameraPath p = new CameraPath();
                double tt = 0, lastX = Double.NaN, lastZ = 0;
                int n = t.riverPoints();
                List<double[]> pts = new ArrayList<>();
                for (int k = 0; k < n; k++) {
                    double x = t.riverX(k), z = t.riverZ(k);
                    if (z > 120 || z < -1700) continue;
                    if (!Double.isNaN(lastX) && Math.hypot(x - lastX, z - lastZ) < 110) continue;
                    pts.add(new double[]{x, t.riverLevel(k), z});
                    lastX = x; lastZ = z;
                }
                for (int k = 0; k < pts.size(); k++) {
                    double[] a = pts.get(k), b = pts.get(Math.min(pts.size() - 1, k + 2));
                    double[] c = k + 2 < pts.size() ? b : new double[]{a[0] - 150, a[1], a[2] - 150};
                    double h = Math.max(a[1] + 48, t.sample(a[0], a[2]) + 40);
                    p.add(tt, a[0], h, a[2], c[0], c[1] + 10, c[2]);
                    tt += 4.2;
                }
                pr.add(new Director.Shot(p, Double.NaN, Double.NaN, "Firehole River",
                        "Die Quellen des Beckens fließen in den Firehole; im Winter dampft er und friert nicht zu.", null).site(0));
                return pr;
            }
            case 2: {
                CameraPath p = new CameraPath();
                double r = 240;
                for (int k = 0; k <= 12; k++) {
                    double a = Math.toRadians(200 + k * 30);
                    p.add(k * 3.6, GPS[0] + r * Math.sin(a), 120 - 30 * Math.sin(k * Math.PI / 12), GPS[2] + r * Math.cos(a), GPS[0], GPS[1], GPS[2]);
                }
                pr.add(new Director.Shot(p, Double.NaN, Double.NaN, "Grand Prismatic Spring",
                        "Yellowstones größte heiße Quelle: 60 bis 100 m Durchmesser, mehr als 37 m tief. Die Farbringe sind Bakterienmatten, nach der Temperatur sortiert.",
                        "NPS: Grand Prismatic Spring").site(1));
                return pr;
            }
            default: {
                CameraPath a = new CameraPath()
                        .add(0, -900, t.sample(-900, -6880) + 60, -6780, GPS[0], GPS[1], GPS[2])
                        .add(7, -820, 180, -6900, -700, 40, -6300)
                        .add(14, -1100, 330, -6200, -1500, 80, -4200);
                CameraPath b = new CameraPath()
                        .add(0, -1450, 380, -2600, -700, 20, -700)
                        .add(8, -900, 250, -1700, -500, 10, -500)
                        .add(16, -520, 150, -1100, 0, 15, 0)
                        .add(24, -180, 80, -420, 0, 18, 0);
                pr.add(new Director.Shot(a, Double.NaN, Double.NaN, "Midway Geyser Basin", "6,5 km flussabwärts vom Upper Basin", null).site(1).fades(false, true));
                pr.add(new Director.Shot(b, Double.NaN, Double.NaN, "Upper Geyser Basin", "Den Firehole hinauf bis Old Faithful", null).site(0).fades(true, false)
                        .trigger("Old Faithful", 14));
                return pr;
            }
        }
    }

    // ------------------------------------------------------------ Rundgang

    static final double EYE = 1.7, WALK = 3.2;

    /**
     * Rundgang auf den Stegen: das Halbrund um Old Faithful (der gleich ausbricht), über die Brücke
     * nach Geyser Hill und einmal um den Hügel mit Beehive. Tempo zügig (3,2 m/s), der Blick geht
     * voraus und wendet sich den Geysiren zu, wenn man an ihnen vorbeikommt.
     */
    public static Director.Program walk(Terrain t) {
        List<double[]> feet = new ArrayList<>();
        if (World.ROUTES.size() >= 3) {
            double[][] arc = World.ROUTES.get(0);
            for (int i = arc.length - 1; i >= 0; i--) feet.add(arc[i]);
            for (double[] p : World.ROUTES.get(1)) feet.add(p);
            for (double[] p : World.ROUTES.get(2)) feet.add(p);
        }
        CameraPath p = new CameraPath();
        double tt = 0, acc = 99;
        double[] prev = null;
        for (int i = 0; i < feet.size(); i++) {
            double[] f = feet.get(i);
            if (prev != null) {
                double d = Math.hypot(f[0] - prev[0], f[2] - prev[2]);
                tt += d / WALK;
                acc += d;
            }
            prev = f;
            if (acc < 6 && i < feet.size() - 1) continue;
            acc = 0;
            // voraus: 18 m weiter auf dem Weg
            double[] ah = f;
            double run = 0;
            for (int j = i + 1; j < feet.size() && run < 18; j++) { run += Math.hypot(feet.get(j)[0] - feet.get(j - 1)[0], feet.get(j)[2] - feet.get(j - 1)[2]); ah = feet.get(j); }
            double tx = ah[0], ty = ah[1] + EYE - 1.0, tz = ah[2];
            if (ah == f) { tx = f[0] - 10; tz = f[2]; }
            // Hinwendung zu Old Faithful und Beehive
            double[][] att = {OF, BEEHIVE};
            for (double[] g : att) {
                double dg = Math.hypot(g[0] - f[0], g[2] - f[2]);
                double w = 0.75 * (1 - smooth(60, 170, dg));
                if (w <= 0) continue;
                double lx = tx - f[0], ly = ty - f[1] - EYE, lz = tz - f[2], ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
                double gx = g[0] - f[0], gy = g[1] - f[1] - EYE, gz = g[2] - f[2], gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
                double mx = lx / ll * (1 - w) + gx / gl * w, my = ly / ll * (1 - w) + gy / gl * w, mz = lz / ll * (1 - w) + gz / gl * w;
                tx = f[0] + mx * 30; ty = f[1] + EYE + my * 30; tz = f[2] + mz * 30;
            }
            p.add(tt, f[0], f[1] + EYE, f[2], tx, ty, tz);
        }
        Director.Program pr = new Director.Program("Rundgang");
        pr.add(new Director.Shot(p, Double.NaN, Double.NaN, "Rundgang",
                "Auf den Stegen um Old Faithful und über Geyser Hill. Die Stege schützen die dünne Sinterkruste – und die Besucher.", null)
                .site(0).trigger("Old Faithful", 25));
        return pr;
    }

    // ------------------------------------------------------------ Drehbuch

    /** Stunde (Ortszeit), zu der die Sonne am Tag d vormittags (am=true) oder nachmittags die Höhe el erreicht; NaN, wenn nie. */
    public static double hourAt(int d, double el, boolean am) {
        DayNightCycle dc = new DayNightCycle();
        double prevH = am ? 3 : 12, prevE;
        dc.set(d, prevH);
        prevE = dc.elevationDeg;
        for (double h = prevH + 0.05; h <= prevH + 10.5; h += 0.05) {
            dc.set(d, h);
            double e = dc.elevationDeg;
            if (am ? (prevE < el && e >= el) : (prevE > el && e <= el)) return h - 0.05 * (e - el) / (e - prevE);
            prevE = e;
        }
        return Double.NaN;
    }

    /** Höchster Sonnenstand am Tag d. */
    static double maxElevation(int d) {
        DayNightCycle dc = new DayNightCycle();
        double m = -90;
        for (double h = 8; h <= 17; h += 0.1) { dc.set(d, h); m = Math.max(m, dc.elevationDeg); }
        return m;
    }

    /** Waagrechte Richtung zur Sonne (x, z) am Tag d zur Stunde h. */
    static double[] sunXZ(int d, double h) {
        DayNightCycle dc = new DayNightCycle();
        dc.set(d, h);
        double x = dc.dir[0], z = dc.dir[2], l = Math.hypot(x, z);
        return new double[]{x / l, z / l};
    }

    /**
     * Das Drehbuch „Ein Tag am Old Faithful“: vom Morgengrauen über den Regenbogen im Vormittagslicht,
     * Castle, Grand und Grand Prismatic bis zum Abend im Gegenlicht und zur Nacht unter der
     * Milchstraße. Knapp drei Minuten; Tafeln mit Quellen.
     */
    public static Director.Program script(Terrain t, int day) {
        Director.Program pr = new Director.Program("Ein Tag am Old Faithful");
        double rise = hourAt(day, -0.8, true), set = hourAt(day, -0.8, false);
        if (Double.isNaN(rise)) rise = 7; if (Double.isNaN(set)) set = 18;
        double bowEl = Math.min(22, maxElevation(day) * 0.75);
        double bowH = hourAt(day, bowEl, true);
        if (Double.isNaN(bowH)) bowH = rise + 2;

        // 1 Morgengrauen: über das Becken, Dampfsäulen in der kalten Luft
        CameraPath p1 = new CameraPath()
                .add(0, 420, 190, 360, -300, 0, -500)
                .add(18, 180, 120, 200, -420, 0, -620);
        pr.add(new Director.Shot(p1, rise - 0.45, rise + 0.15, "Upper Geyser Basin",
                "Auf einer Quadratmeile liegen mindestens 150 Geysire, die dichteste Ansammlung der Welt. In der kalten Morgenluft stehen die Dampfsäulen hoch über den Quellen.",
                "NPS, Old Faithful Virtual Visitor Center").site(0).fades(true, true));

        // 2 Old Faithful vom Steg: Ausbruch
        double[] sb = sunXZ(day, bowH);
        CameraPath p2 = new CameraPath()
                .add(0, 24, t.sample(24, 62) + 1.8, 62, 0, 9, 0)
                .add(22, 40, t.sample(40, 50) + 4, 50, 0, 26, 0);
        pr.add(new Director.Shot(p2, bowH - 0.08, bowH - 0.02, "Old Faithful",
                "Benannt am 18. September 1870 von Henry D. Washburn. Ein Ausbruch dauert 1½ bis 5 Minuten und wirft 14 000 bis 32 000 Liter Wasser 32 bis 56 m hoch.",
                "Wikipedia: Old Faithful").site(0).trigger("Old Faithful", 5).fades(true, true));

        // 3 Regenbogen: Sonne im Rücken, Gegenpunkt 40° neben dem Geysir
        double ax = -sb[0], az = -sb[1];
        double best = -1e9, fx = 0, fz = 0;
        for (int sg = -1; sg <= 1; sg += 2) {
            double a = Math.toRadians(40 * sg), c = Math.cos(a), s = Math.sin(a);
            double vx = ax * c - az * s, vz = ax * s + az * c;          // Blickrichtung zum Geysir
            double ez = -vz * 115;                                       // Auge = Geysir − Blick · 115 m
            if (ez > best) { best = ez; fx = vx; fz = vz; }
        }
        double ex0 = -fx * 115, ez0 = -fz * 115, ex1 = -fx * 95 + fz * 12, ez1 = -fz * 95 - fx * 12;
        CameraPath p3 = new CameraPath()
                .add(0, ex0, t.sample(ex0, ez0) + 2.4, ez0, 0, 17, 0)
                .add(20, ex1, t.sample(ex1, ez1) + 2.8, ez1, 0, 20, 0);
        pr.add(new Director.Shot(p3, bowH, bowH + 0.03, "Regenbogen in der Gischt",
                "Die Sonne im Rücken: Das Licht wird in den Tropfen gebrochen und gespiegelt und kommt unter rund 42° zum Gegenpunkt der Sonne zurück, Rot außen, Violett innen. Darüber, blasser und umgekehrt, der Nebenbogen bei gut 50°.",
                "Wikipedia: Rainbow").site(0).fades(true, true));

        // 4 Castle Geyser
        CameraPath p4 = new CameraPath()
                .add(0, -610, Viewpoint.deck(t, -610, -258), -258, -678, 5, -331)
                .add(20, -640, 34, -236, -678, 16, -331);
        pr.add(new Director.Shot(p4, 11.0, 11.1, "Castle Geyser",
                "Der Sinterkegel ist nach der Radiokarbonmethode rund 1000 Jahre alt. Rund 20 Minuten Wasser bis 27 m hoch, danach 30 bis 40 Minuten eine laute Dampfphase.",
                "Wikipedia: Castle Geyser").site(0).trigger("Castle Geyser", 2).fades(true, true));

        // 5 Grand Geyser
        CameraPath p5 = new CameraPath()
                .add(0, -735, Viewpoint.deck(t, -735, -760), -760, -806, 10, -681)
                .add(20, -700, 60, -780, -806, 30, -681);
        pr.add(new Director.Shot(p5, 14.0, 14.1, "Grand Geyser",
                "Ein Fontänengeysir: Er bricht aus einem weiten Becken in einer Folge von Stößen aus, bis 61 m hoch, alle 6 bis 7 Stunden.",
                "NPS; Wikipedia: Grand Geyser").site(0).trigger("Grand Geyser", 1.5).fades(true, true));

        // 6 Grand Prismatic
        double[] ov = World.OVERLOOK;
        CameraPath p6 = new CameraPath()
                .add(0, ov[0], ov[1] + 1.7, ov[2], GPS[0], GPS[1], GPS[2])
                .add(9, ov[0] + 20, ov[1] + 30, ov[2] - 30, GPS[0], GPS[1], GPS[2])
                .add(22, -640, 170, -7010, GPS[0], GPS[1], GPS[2]);
        pr.add(new Director.Shot(p6, 15.0, 15.15, "Grand Prismatic Spring",
                "Yellowstones größte heiße Quelle, 60 bis 100 m im Durchmesser und mehr als 37 m tief. Die Farbringe sind Bakterienmatten, nach der Temperatur sortiert.",
                "NPS: Grand Prismatic Spring").site(1).fades(true, true));

        // 7 Abend: Old Faithful im Gegenlicht
        double[] ss = sunXZ(day, set - 0.6);
        double ex7 = -ss[0] * 150 + ss[1] * 25, ez7 = -ss[1] * 150 - ss[0] * 25;
        CameraPath p7 = new CameraPath()
                .add(0, ex7, t.sample(ex7, ez7) + 2.5, ez7, 0, 16, 0)
                .add(22, ex7 * 0.85, t.sample(ex7 * 0.85, ez7 * 0.85) + 6, ez7 * 0.85, 0, 24, 0);
        pr.add(new Director.Shot(p7, set - 0.75, set - 0.35, "Die Vorhersage",
                "Die Ranger sagen Old Faithful aus der Dauer des letzten Ausbruchs voraus, auf ±10 Minuten: Nach Ausbrüchen unter 2½ Minuten kommt der nächste früher, nach längeren später.",
                "Wikipedia: Old Faithful; NPS").site(0).trigger("Old Faithful", 4).fades(true, true));

        // 8 Nacht: unter der Milchstraße
        double[] sn = sunXZ(day, set - 0.6);
        CameraPath p8 = new CameraPath()
                .add(0, 70, Viewpoint.deck(t, 70, 50), 50, 0, 25, 0)
                .add(22, 60, Viewpoint.deck(t, 60, 62), 62, -sn[0] * 40, 110, -sn[1] * 40 - 60);
        pr.add(new Director.Shot(p8, 23.4, 23.6, "Nacht über dem Becken",
                "Die hellen Sterne stehen an ihren Örtern für 2026, dazu das Band der Milchstraße mit dem dunklen Großen Riss.", null)
                .site(0).trigger("Old Faithful", 3).fades(true, true));
        return pr;
    }

    // ------------------------------------------------------------ Prüfung

    /** Kleinster Abstand des Auges über dem Gelände entlang eines Programms (alle 0,1 s). */
    public static double clearance(Terrain t, Director.Program p) {
        double min = 1e9;
        double[] o = new double[6];
        for (Director.Shot s : p.shots) {
            for (double u = 0; u <= s.path.duration(); u += 0.1) {
                s.path.sample(u, o);
                min = Math.min(min, o[1] - t.sample(o[0], o[2]));
            }
        }
        return min;
    }

    private static double smooth(double a, double b, double x) {
        double u = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return u * u * (3 - 2 * u);
    }
}
