package com.dan.geyser.world;

import com.dan.geyser.core.Animals;
import com.dan.geyser.core.Terrain;
import com.dan.geyser.core.Thermal;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bisons und Wapitis im Upper Geyser Basin. Eine Bisonherde und ein Wapitirudel grasen auf den
 * Wiesen am Rand des Beckens, weit weg von Quellen, Fluss und Wald; sie grasen eine Weile, gehen ein
 * Stück, bleiben beisammen. Im Winter stehen die Bisons auf dem warmen Boden bei den Quellen (dort
 * finden sie laut NPS Futter und ein milderes Kleinklima), vom Rudel bleiben nur wenige Kühe (im
 * Winter sind weniger als 2000 Wapitis im Park). Kälber gibt es im Sommer, das Geweih des Bullen
 * nicht zwischen Abwurf im Frühjahr und dem Nachwachsen (dann fehlt es hier ganz).
 */
public final class Fauna {
    static final class Beast {
        byte kind;
        double x, z, hx = 1, hz, tx, tz, speed, timer, step, gait, graze, scale;
        boolean walking;
    }

    private final Terrain t;
    private final Thermal th;
    private final Random rnd = new Random(1872);
    private final List<Beast> bison = new ArrayList<>(), elk = new ArrayList<>();
    private double[] bisonHome, elkHome;
    private int builtFor = -1;
    private final float[] gm = new float[5];

    public Fauna(Terrain t, Thermal th) { this.t = t; this.th = th; }

    /** Wiese: kein Sinter, kein Wald, weit vom Ufer, nicht in einer Quelle, flach. */
    boolean meadow(double x, double z) {
        t.ground((float) x, (float) z, gm);
        if (gm[3] > 0.05f || gm[4] > 0.08f || gm[0] < 18) return false;
        for (int q = 0; q < 4; q++) {
            t.ground((float) (x + (q == 0 ? 20 : q == 1 ? -20 : 0)), (float) (z + (q == 2 ? 20 : q == 3 ? -20 : 0)), gm);
            if (gm[4] > 0.05f) return false;
        }
        if (th.poolAt(x, z) != null || th.tempExact(x, z) > Thermal.ambient + 4) return false;
        double s = Math.abs(t.sample(x + 3, z) - t.sample(x - 3, z)) + Math.abs(t.sample(x, z + 3) - t.sample(x, z - 3));
        return s < 1.6;
    }

    /** Warmer Boden für den Winter: Sinter, etwas wärmer als die Luft, kein Becken, kein Abfluss. */
    boolean warmGround(double x, double z) {
        t.ground((float) x, (float) z, gm);
        if (gm[3] < 0.35f || gm[0] < 12) return false;
        if (th.poolAt(x, z) != null) return false;
        float tt = th.tempExact(x, z);
        return tt > Thermal.ambient + 1.0 && tt < Thermal.ambient + 25;
    }

    private double[] findHome(boolean winter, double[] avoid, double cx, double cz) {
        double[] best = null;
        double bd = Double.MAX_VALUE;
        for (int k = 0; k < 4000; k++) {
            double x = cx + (rnd.nextDouble() - 0.5) * 1400, z = cz + (rnd.nextDouble() - 0.5) * 1400;
            if (!(winter ? warmGround(x, z) : meadow(x, z))) continue;
            if (avoid != null && Math.hypot(x - avoid[0], z - avoid[1]) < 220) continue;
            // nahe am Weg zwischen Old Faithful und Grand, damit man sie sieht
            double d = Math.hypot(x - cx, z - cz);
            int ok = 0;
            for (int q = 0; q < 12; q++) {
                double a = q * Math.PI / 6;
                if (winter ? warmGround(x + 20 * Math.cos(a), z + 20 * Math.sin(a)) : meadow(x + 25 * Math.cos(a), z + 25 * Math.sin(a))) ok++;
            }
            if (ok < (winter ? 6 : 9)) continue;
            if (d < bd) { bd = d; best = new double[]{x, z}; }
        }
        return best;
    }

    /** Baut Herde und Rudel für die Jahreszeit (nur wenn sie wechselt). */
    void build(int day, double snow) {
        int season = snow > 0.4 ? 0 : 1;
        int key = season * 10 + (calves(day) ? 1 : 0) + (antlers(day) ? 2 : 0);
        if (key == builtFor) return;
        builtFor = key;
        bison.clear(); elk.clear();
        boolean winter = season == 0;
        bisonHome = findHome(winter, null, -420, -520);
        if (bisonHome == null && winter) { bisonHome = findHome(false, null, -420, -520); builtFor = 10 + key % 10; }
        elkHome = findHome(false, bisonHome, -700, -900);
        if (bisonHome != null) {
            int n = 9;
            for (int i = 0; i < n; i++) bison.add(beast(Animals.BISON, bisonHome, 22, 0.85 + 0.2 * rnd.nextDouble()));
            if (calves(day)) for (int i = 0; i < 3; i++) bison.add(beast(Animals.BISON_CALF, bisonHome, 18, 0.55));
        }
        if (elkHome != null) {
            int n = winter ? 3 : 6;
            for (int i = 0; i < n; i++) elk.add(beast(Animals.ELK_COW, elkHome, 20, 0.88 + 0.1 * rnd.nextDouble()));
            if (!winter) elk.add(beast(antlers(day) ? Animals.ELK_BULL : Animals.ELK_COW, elkHome, 20, 1.08));
        }
    }

    /** Kälber sind geboren und noch klein: grob Mai bis Oktober. */
    static boolean calves(int day) { return day >= 125 && day <= 290; }

    /** Geweih: fehlt nach dem Abwurf (März, April) bis es im Sommer nachgewachsen ist. */
    static boolean antlers(int day) { return day < 60 || day > 200; }

    private Beast beast(byte k, double[] home, double r, double sc) {
        Beast b = new Beast();
        b.kind = k;
        for (int tries = 0; tries < 50; tries++) {
            double a = rnd.nextDouble() * 2 * Math.PI, rr = r * Math.sqrt(rnd.nextDouble());
            b.x = home[0] + rr * Math.cos(a); b.z = home[1] + rr * Math.sin(a);
            if (ok(b)) break;
        }
        double a = rnd.nextDouble() * 2 * Math.PI;
        b.hx = Math.cos(a); b.hz = Math.sin(a);
        b.tx = b.x; b.tz = b.z;
        b.scale = sc;
        b.timer = 3 + 20 * rnd.nextDouble();
        b.graze = rnd.nextDouble();
        b.step = rnd.nextDouble() * 6.28;
        return b;
    }

    private boolean ok(Beast b) { return ok(b.kind, b.x, b.z); }

    private boolean ok(byte k, double x, double z) {
        boolean winterBison = builtFor < 10 && (k == Animals.BISON || k == Animals.BISON_CALF);
        return winterBison ? warmGround(x, z) : meadow(x, z);
    }

    /** Ein Zeitschritt: grasen, gehen, beisammen bleiben. */
    public void update(double dt, int day, double snow) {
        build(day, snow);
        step(bison, bisonHome, dt, 0.55, 26);
        step(elk, elkHome, dt, 0.85, 24);
    }

    private void step(List<Beast> herd, double[] home, double dt, double speed, double radius) {
        if (home == null) return;
        for (Beast b : herd) {
            b.timer -= dt;
            if (b.walking) {
                double dx = b.tx - b.x, dz = b.tz - b.z, d = Math.hypot(dx, dz);
                if (d < 0.6 || b.timer <= 0) {
                    b.walking = false;
                    b.timer = 8 + 25 * rnd.nextDouble();
                } else {
                    // Richtung weich nachführen
                    double wx = dx / d, wz = dz / d;
                    double k = Math.min(1, dt * 1.5);
                    b.hx += (wx - b.hx) * k; b.hz += (wz - b.hz) * k;
                    double l = Math.hypot(b.hx, b.hz);
                    b.hx /= l; b.hz /= l;
                    double v = speed * (b.kind == Animals.BISON_CALF ? 1.1 : 1) * Math.max(0, b.hx * wx + b.hz * wz);
                    double nx = b.x + b.hx * v * dt, nz = b.z + b.hz * v * dt;
                    if (ok(b.kind, nx, nz)) { b.x = nx; b.z = nz; } else { b.walking = false; b.timer = 2; }
                    b.step += dt * v * 2 * Math.PI / (1.6 * b.scale);
                }
            } else if (b.timer <= 0) {
                // neues Ziel nahe der Herde
                double cx = 0, cz = 0;
                for (Beast o : herd) { cx += o.x; cz += o.z; }
                cx /= herd.size(); cz /= herd.size();
                cx = cx * 0.7 + home[0] * 0.3; cz = cz * 0.7 + home[1] * 0.3;
                for (int tries = 0; tries < 20; tries++) {
                    double a = rnd.nextDouble() * 2 * Math.PI, r = radius * Math.sqrt(rnd.nextDouble());
                    double x = cx + r * Math.cos(a), z = cz + r * Math.sin(a);
                    if (ok(b.kind, x, z) && Math.hypot(x - b.x, z - b.z) > 3) { b.tx = x; b.tz = z; b.walking = true; b.timer = 60; break; }
                }
                if (!b.walking) b.timer = 5;
            }
            double gTarget = b.walking ? 1 : 0, zTarget = b.walking ? 0 : 1;
            b.gait += (gTarget - b.gait) * Math.min(1, dt * 2);
            b.graze += (zTarget - b.graze) * Math.min(1, dt * 0.8);
            if (!b.walking) b.step += dt * 0.3 * b.gait;
        }
    }

    /** Füllt die Tiere für das nächste Bild. */
    public void fill(Animals out) {
        out.clear();
        for (List<Beast> herd : List.of(bison, elk)) {
            for (Beast b : herd) {
                int i = out.add(b.kind, b.x, t.sample(b.x, b.z), b.z, b.hx, b.hz, b.scale);
                if (i < 0) return;
                out.step[i] = (float) b.step; out.gait[i] = (float) b.gait; out.graze[i] = (float) b.graze;
            }
        }
    }

    /** Abstand der Kamera zum nächsten Wapiti (für den Klang), und ob ein Bulle dabei ist. */
    public double nearestElk(double x, double z) {
        double d = 1e9;
        for (Beast b : elk) d = Math.min(d, Math.hypot(b.x - x, b.z - z));
        return d;
    }

    public boolean hasBull() {
        for (Beast b : elk) if (b.kind == Animals.ELK_BULL) return true;
        return false;
    }

    public double nearestBison(double x, double z) {
        double d = 1e9;
        for (Beast b : bison) d = Math.min(d, Math.hypot(b.x - x, b.z - z));
        return d;
    }

    /** Mitte von Herde (0) oder Rudel (1), für Blickpunkte; null, wenn keine da ist. */
    public double[] home(int which) { return which == 0 ? bisonHome : elkHome; }
}
