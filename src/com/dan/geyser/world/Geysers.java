package com.dan.geyser.world;

import com.dan.geyser.core.Terrain;
import com.dan.geyser.core.Thermal;
import com.dan.geyser.core.Wetness;
import com.dan.geyser.effects.ParticleSystem;

/**
 * Alle Geysire der Szene: rechnet ihre Röhrenmodelle in Szenenzeit, stößt Tropfen, Gischt und Dampf
 * aus, lässt die heißen Quellen dampfen und liefert die Säulen für die Lichtstrahlen im Bildrechner.
 * Die Kennzahlen stehen bei jedem Geysir mit Quelle.
 */
public final class Geysers {
    public final java.util.List<GeyserModel> list = new java.util.ArrayList<>();
    public final Wetness.Set wet = new Wetness.Set();
    private final Terrain terrain;
    private final Thermal thermal;
    /** Säulen fürs Streulicht: je x, z, Fuß y, Kopf y, Radius, Dichte, Neigung x, Neigung z. */
    public volatile float[][] plumes = new float[0][];
    /** Sichtbarkeit von Dampf 0..1 (kalte Luft: viel) und Menge der Teilchen 0..1 (Qualität). */
    public volatile float steamVis = 0.6f, amount = 1f;

    /** Wie die Geysire einander auslösen (Turban und Grand, Indicator und Beehive, …). */
    public final Couplings couplings = new Couplings(this);

    public Geysers(Terrain t, Thermal th) { terrain = t; thermal = th; }

    Thermal thermal() { return thermal; }

    /** Grundwasser für alle Geysire setzen (1 = heute), siehe {@link GeyserModel#water}. */
    public void setWater(double w) { for (GeyserModel g : list) g.water = w; }

    /**
     * Ein beendeter Ausbruch für das Protokoll: Geysir, Beginn und Dauer (Sekunden der Geysir-Uhr),
     * größte Höhe, die Vorhersage vor dem Ausbruch (NaN: keine) und der Abstand zum vorigen Beginn
     * (NaN beim ersten). manual: von Hand ausgelöst.
     */
    public static final class Eruption {
        public final String geyser;
        public final double start, duration, maxHeight, predicted, interval;
        public final boolean manual;

        Eruption(String geyser, double start, double duration, double maxHeight, double predicted, double interval, boolean manual) {
            this.geyser = geyser; this.start = start; this.duration = duration; this.maxHeight = maxHeight;
            this.predicted = predicted; this.interval = interval; this.manual = manual;
        }
    }

    /** Wird nach jedem beendeten Ausbruch gerufen (auf dem Rechenfaden). */
    public volatile java.util.function.Consumer<Eruption> onEruption;
    private final java.util.Map<GeyserModel, double[]> track = new java.util.HashMap<>();

    /** Zustand je Geysir: letzte Phase, letzte Vorhersage, Vorhersage beim Beginn, größte Höhe, voriger Beginn. */
    private void watch(GeyserModel g, double now) {
        double[] s = track.computeIfAbsent(g, k -> new double[]{-1, Double.NaN, Double.NaN, 0, Double.NaN});
        int ph = g.phase.ordinal();
        boolean erupt = g.phase == GeyserModel.Phase.ERUPTION;
        if (!erupt && !Double.isNaN(g.predicted)) s[1] = g.predicted;
        if (erupt && s[0] != GeyserModel.Phase.ERUPTION.ordinal()) { s[2] = s[1]; s[3] = 0; }
        if (erupt) s[3] = Math.max(s[3], g.height(now));
        if (!erupt && s[0] == GeyserModel.Phase.ERUPTION.ordinal()) {
            java.util.function.Consumer<Eruption> l = g.minor ? null : onEruption;
            double interval = Double.isNaN(s[4]) ? Double.NaN : g.lastStart - s[4];
            if (l != null) l.accept(new Eruption(g.name, g.lastStart, g.duration, s[3], g.manual ? Double.NaN : s[2], interval, g.manual));
            s[4] = g.lastStart;
            s[1] = Double.NaN;
        }
        s[0] = ph;
    }

    public GeyserModel add(GeyserModel g) {
        list.add(g);
        wet.list.add(new Wetness(terrain, g.x + Math.cos(g.tiltDir) * Math.sin(Math.toRadians(g.tiltDeg)) * g.hMax * 0.5,
                g.z + Math.sin(g.tiltDir) * Math.sin(Math.toRadians(g.tiltDeg)) * g.hMax * 0.5));
        return g;
    }

    public GeyserModel byName(String n) {
        for (GeyserModel g : list) if (g.name.equals(n)) return g;
        return null;
    }

    /** Läuft gerade ein Ausbruch (Wasserphase)? */
    public GeyserModel erupting() {
        for (GeyserModel g : list) if (g.phase == GeyserModel.Phase.ERUPTION) return g;
        return null;
    }

    /**
     * Modelle um simDt Sekunden Szenenzeit weiterrechnen (in Schritten von höchstens 2 s) und für realDt
     * Sekunden Teilchen ausstoßen. Wind in m/s.
     */
    public void update(double simNow, double simDt, float realDt, ParticleSystem ps, float wx, float wz, float sun) {
        double left = simDt, t = simNow - simDt;
        while (left > 1e-9) {
            double h = Math.min(2.0, left);
            t += h;
            for (GeyserModel g : list) g.step(t, h);
            couplings.step(t);
            left -= h;
        }
        for (GeyserModel g : list) watch(g, simNow);
        wet.step(realDt, sun);
        java.util.List<float[]> pl = new java.util.ArrayList<>();
        for (int gi = 0; gi < list.size(); gi++) {
            GeyserModel g = list.get(gi);
            double hh = g.height(simNow);
            if (hh > 0.3) {
                emit(g, gi, hh, simNow, realDt, ps);
                double tr = Math.toRadians(g.tiltDeg);
                float tx = (float) (Math.cos(g.tiltDir) * Math.tan(tr)), tz = (float) (Math.sin(g.tiltDir) * Math.tan(tr));
                float rad = (float) (g.type == GeyserModel.Type.FOUNTAIN ? 4 + hh * 0.12 : 1.8 + hh * 0.07);
                pl.add(new float[]{(float) g.x, (float) g.z, (float) g.y, (float) (g.y + hh), rad, (float) (g.waterShare() * 0.05 * amount), tx, tz});
            }
            // Zwischen den Ausbrüchen dampft der Schlot leicht
            float calm = (float) (g.steamShare() * 3.0 * steamVis * realDt * amount);
            spawnSteam(ps, g.x, g.y + 0.3, g.z, 0.6, calm, 0.8f, 2.2f, 0.10f * steamVis);
        }
        plumes = pl.toArray(new float[0][]);
        // Heiße Quellen dampfen nach Fläche und Temperatur
        for (Thermal.Spring s : thermal.springs) {
            if (s.kind == Thermal.Kind.VENT || s.t0 < 45) continue;
            double rate = Math.min(28, s.area() / 30.0 * (s.t0 - 42) / 48.0) * steamVis * amount;
            float big = (float) Math.min(1, Math.sqrt(s.area()) / 60);
            spawnSteam(ps, s.x, s.y + 0.2, s.z, Math.max(s.ax, s.az) * 0.9, (float) (rate * realDt), 1.4f + 4f * big, 1.2f + 3f * big,
                    (0.055f + 0.03f * (1 - big)) * steamVis);
        }
        ps.step(realDt, wx, wz, terrain, wet);
    }

    /** Ausstoß eines Geysirs mit Säulenhöhe h. */
    private void emit(GeyserModel g, int gi, double h, double now, float dt, ParticleSystem ps) {
        boolean fountain = g.type == GeyserModel.Type.FOUNTAIN;
        double hk = Math.sqrt(Math.min(1, h / g.hMax));
        double base = (fountain ? 1300 : 950) * Math.min(1.3, 0.35 + g.hMax / 60) * amount;
        double water = g.waterShare();
        float nDrop = (float) (base * hk * water * dt), nSpray = (float) (base * 0.55 * hk * water * dt);
        float fl = Double.isNaN(g.floor) ? -1e9f : (float) g.floor;
        double tr = Math.toRadians(g.tiltDeg);
        double ax = Math.cos(g.tiltDir) * Math.sin(tr), ay = Math.cos(tr), az = Math.sin(g.tiltDir) * Math.sin(tr);
        int count = stochastic(ps, nDrop);
        for (int k = 0; k < count; k++) jet(g, gi, h, ax, ay, az, fountain, ParticleSystem.DROP, ps, fl, dt);
        count = stochastic(ps, nSpray);
        for (int k = 0; k < count; k++) jet(g, gi, h, ax, ay, az, fountain, ParticleSystem.SPRAY, ps, fl, dt);
        // Kern der Säule: dichte, schmale Wasserstränge, die sie weiß und scharf zeichnen
        count = stochastic(ps, (float) (base * 0.9 * hk * water * dt));
        for (int k = 0; k < count; k++) jet(g, gi, h, ax, ay, az, fountain, CORE, ps, fl, dt);
        // Dampf entlang der Säule, am Kopf mehr
        float nSteam = (float) (48 * g.steamShare() * steamVis * Math.min(1.4, 0.5 + h / 40) * dt * amount);
        count = stochastic(ps, nSteam);
        for (int k = 0; k < count; k++) {
            double f = Math.pow(ps.rand(), 0.35);
            double yy = h * f;
            double px = g.x + ax / Math.max(0.2, ay) * yy + (ps.rand() - 0.5) * 2, pz = g.z + az / Math.max(0.2, ay) * yy + (ps.rand() - 0.5) * 2;
            float up = (float) (3 + 9 * (1 - f) * hk);
            int i = ps.spawn(ParticleSystem.STEAM, (float) px, (float) (g.y + yy), (float) pz, (ps.rand() - 0.5f) * 2, up, (ps.rand() - 0.5f) * 2,
                    (float) (1.5 + 2.5 * f + h * 0.03), 11 + 9 * ps.rand(), (0.10f + 0.10f * (float) f) * steamVis * (0.7f + 0.6f * ps.rand()), fl, -1);
            if (i >= 0) ps.grow[i] = 0.7f + 0.8f * ps.rand();
        }
    }

    /** Ganze Zahl aus einer mittleren Anzahl (Rest als Wahrscheinlichkeit), begrenzt durch den freien Platz. */
    private static int stochastic(ParticleSystem ps, float mean) {
        int c = (int) mean;
        if (ps.rand() < mean - c) c++;
        return Math.min(c, ParticleSystem.CAP - ps.n - 2000);
    }

    /** Marke für Kernteilchen (werden als Gischt gespeichert). */
    static final byte CORE = 9;

    /** Ein Teilchen der Säule: Zielhöhe nach Verteilung (mehr nah der Spitze), Geschwindigkeit mit Luftwiderstand. */
    private static void jet(GeyserModel g, int gi, double h, double ax, double ay, double az, boolean fountain, byte kind0,
                            ParticleSystem ps, float fl, float dt) {
        boolean core = kind0 == CORE;
        byte kind = core ? ParticleSystem.SPRAY : kind0;
        double u = 0.25 + 0.75 * Math.sqrt(ps.rand());
        if (kind == ParticleSystem.SPRAY) u = 0.15 + 0.85 * ps.rand();
        if (core) u = 0.35 + 0.65 * Math.pow(ps.rand(), 0.5);
        double target = h * u;
        float k = kind == ParticleSystem.DROP ? ParticleSystem.K_DROP : ParticleSystem.K_SPRAY;
        double v = Math.sqrt((Math.exp(2 * k * target) - 1) * ParticleSystem.G / k);
        // Streuung: enger Kern, weiter Mantel; Fontänen breiter
        double sig = core ? (fountain ? 0.08 : 0.022) : fountain ? 0.2 : (ps.rand() < 0.85 ? 0.05 : 0.16);
        sig *= g.spread;
        double a1 = (ps.rand() - 0.5) * 2 * sig, a2 = (ps.rand() - 0.5) * 2 * sig;
        // Basis senkrecht zur Achse
        double bx = ay, by = -ax, bz = 0;
        double bl = Math.sqrt(bx * bx + by * by);
        if (bl < 1e-6) { bx = 1; by = 0; bz = 0; bl = 1; }
        bx /= bl; by /= bl; bz /= bl;
        double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        double dx = ax + bx * a1 + cx * a2, dy = ay + by * a1 + cy * a2, dz = az + bz * a1 + cz * a2;
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= dl; dy /= dl; dz /= dl;
        double r = g.ventR * Math.sqrt(ps.rand()) * (fountain ? 1 : 0.8), an = ps.rand() * 6.2832;
        float pre = ps.rand() * dt;
        float px = (float) (g.x + Math.cos(an) * r + dx * v * pre), py = (float) (g.y + 0.15 + dy * v * pre), pz = (float) (g.z + Math.sin(an) * r + dz * v * pre);
        double tof = 2 * Math.sqrt(2 * Math.max(1, target) / ParticleSystem.G) * 1.7 + 3;
        float size = kind == ParticleSystem.DROP ? 0.05f + 0.07f * ps.rand() : (float) (0.5 + 1.1 * ps.rand() + target * 0.012);
        float al = kind == ParticleSystem.DROP ? 0.55f : 0.20f + 0.08f * ps.rand();
        if (core) { size = (float) (0.22 + 0.3 * ps.rand() + target * 0.006); al = 0.5f + 0.2f * ps.rand(); }
        int i = ps.spawn(kind, px, py, pz, (float) (dx * v), (float) (dy * v), (float) (dz * v), size, (float) Math.min(25, tof), al, fl, gi);
        if (i >= 0 && kind == ParticleSystem.SPRAY) ps.grow[i] = core ? 0.25f + 0.2f * ps.rand() : 0.15f + 0.25f * ps.rand();
    }

    /** Dampf über einer Fläche mit Radius rad; count als mittlere Anzahl. */
    private void spawnSteam(ParticleSystem ps, double cx, double cy, double cz, double rad, float count, float s0, float growMax, float al) {
        int c = stochastic(ps, count);
        for (int k = 0; k < c; k++) {
            double r = rad * Math.sqrt(ps.rand()), an = ps.rand() * 6.2832;
            int i = ps.spawn(ParticleSystem.STEAM, (float) (cx + Math.cos(an) * r), (float) cy, (float) (cz + Math.sin(an) * r),
                    (ps.rand() - 0.5f) * 0.6f, 0.6f + 0.9f * ps.rand(), (ps.rand() - 0.5f) * 0.6f, s0 * (0.6f + 0.8f * ps.rand()),
                    9 + 9 * ps.rand(), al * (0.7f + 0.6f * ps.rand()), -1e9f, -1);
            if (i >= 0) ps.grow[i] = 0.25f + growMax * 0.35f * ps.rand();
        }
    }
}
