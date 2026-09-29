package com.dan.geyser.world;

import com.dan.geyser.core.Animals;
import com.dan.geyser.core.Terrain;
import com.dan.geyser.core.Thermal;
import com.dan.ground.Biome;
import com.dan.ground.GNoise;
import com.dan.ground.Meadow;
import com.dan.ground.Site;

/**
 * Die Bodendecke des Beckens mit dem Boden-Paket ({@link com.dan.ground}): Bergwiese mit Schwingel,
 * Weizengras und Seggen, Lupinen, Indian Paintbrush, Balsamwurzel und Glockenblumen, dazu Kies und
 * Steine, offene Erde. Die Regeln kommen aus der Bodenkarte:
 * <ul>
 *   <li>Auf Sinter und auf heißem Boden wächst nichts; am Rand der heißen Flächen stirbt das Gras ab
 *       und der Boden liegt offen (die „thermal kill zones“ der Becken).</li>
 *   <li>Am Ufer Seggen, Kies und Steine; im Wasser nichts.</li>
 *   <li>Im Wald nur wenig Gras; an steilen Hängen Geröll.</li>
 *   <li>Auf der Wiese Suhlen, in denen sich die Bisons wälzen: offene, flache Erde.</li>
 *   <li>Unter den Stegen nichts.</li>
 * </ul>
 * Besucher und Tiere treten das Gras nieder; Wind, Jahreszeit und Schnee kommen aus der Szene.
 */
public final class Sward implements Site {
    public final Meadow meadow;
    private final Terrain terrain;
    private final Thermal thermal;
    // Stege: Abschnitte in einem Gitter, für den Abstand
    private final float gc = 6;
    private final java.util.Map<Long, float[]> walks = new java.util.HashMap<>();

    private final Roadways roads;

    Sward(Terrain t, Thermal th, Roadways rw) {
        terrain = t; thermal = th; roads = rw;
        meadow = new Meadow(Biome.yellowstone(), this);
        meadow.radius = 26;
        meadow.near = 7;
        meadow.seed = 1872;
        java.util.Map<Long, java.util.List<float[]>> m = new java.util.HashMap<>();
        for (double[][] r : World.ROUTES)
            for (int i = 0; i + 1 < r.length; i++) {
                float ax = (float) r[i][0], az = (float) r[i][2], bx = (float) r[i + 1][0], bz = (float) r[i + 1][2];
                int i0 = (int) Math.floor((Math.min(ax, bx) - 2) / gc), i1 = (int) Math.floor((Math.max(ax, bx) + 2) / gc);
                int j0 = (int) Math.floor((Math.min(az, bz) - 2) / gc), j1 = (int) Math.floor((Math.max(az, bz) + 2) / gc);
                for (int j = j0; j <= j1; j++)
                    for (int ii = i0; ii <= i1; ii++) m.computeIfAbsent(key(ii, j), k -> new java.util.ArrayList<>()).add(new float[]{ax, az, bx, bz});
            }
        for (java.util.Map.Entry<Long, java.util.List<float[]>> e : m.entrySet()) {
            float[] f = new float[4 * e.getValue().size()];
            for (int k = 0; k < e.getValue().size(); k++) System.arraycopy(e.getValue().get(k), 0, f, 4 * k, 4);
            walks.put(e.getKey(), f);
        }
    }

    private static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    /** Abstand zum nächsten Steg (m), höchstens 9. */
    private float walkDist(double x, double z) {
        float[] f = walks.get(key((int) Math.floor(x / gc), (int) Math.floor(z / gc)));
        if (f == null) return 9;
        double best = 81;
        for (int k = 0; k < f.length; k += 4) {
            double ax = f[k], az = f[k + 1], dx = f[k + 2] - ax, dz = f[k + 3] - az;
            double t = ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz + 1e-9);
            t = t < 0 ? 0 : (t > 1 ? 1 : t);
            double qx = ax + dx * t - x, qz = az + dz * t - z;
            best = Math.min(best, qx * qx + qz * qz);
        }
        return (float) Math.sqrt(best);
    }

    @Override public float height(double x, double z) { return terrain.sample(x, z); }

    @Override public void cover(double x, double z, float[] out) {
        float[] gm = GM.get();
        terrain.ground((float) x, (float) z, gm);
        float bank = gm[0], sinter = gm[3], forest = gm[4];
        float fx = (float) x, fz = (float) z;
        // warmer Boden (Quellen und ihr Abfluss): kein Gras, am Rand offen
        float temp = thermal == null ? Thermal.ambient : thermal.temp(x, z);
        float hot = smooth(Thermal.ambient + 3, Thermal.ambient + 14, temp);
        float kill = smooth(Thermal.ambient + 1.5f, Thermal.ambient + 5, temp) * (1 - smooth(Thermal.ambient + 12, Thermal.ambient + 25, temp));
        // Hang
        float h = terrain.sample(x, z), hx = terrain.sample(x + 1.5, z) - h, hz = terrain.sample(x, z + 1.5) - h;
        float slope = (float) Math.sqrt(hx * hx + hz * hz) / 1.5f;
        float rocky = smooth(0.45f, 0.9f, slope);
        float wet = bank < 0.2f ? 0 : 1;                                     // im Wasser wächst nichts
        float shore = 1 - smooth(1.5f, 7, bank);
        float walk = smooth(1.2f, 2.2f, walkDist(x, z));
        // Straßen und Wege: auf der Fahrbahn nichts, am Bankett Kies
        float road = roads == null ? 30 : roads.clearance(x, z);
        float verge = 1 - smooth(-0.2f, 1.2f, road);
        walk *= smooth(-1.0f, 0.6f, road);
        float live = wet * walk * (1 - smooth(0.05f, 0.35f, sinter)) * (1 - hot);
        float n1 = GNoise.value(fx * 0.07f, fz * 0.07f), n2 = GNoise.value(fx * 0.045f + 9, fz * 0.045f + 3);
        // Suhlen der Bisons: flache Mulden mit offener Erde, verstreut über die Wiese
        float wallow = smooth(0.8f, 0.86f, GNoise.value(fx * 0.03f + 5.5f, fz * 0.03f + 1.2f)) * (1 - forest) * (1 - shore);
        out[0] = live * (0.55f + 0.45f * n1) * (1 - 0.75f * forest) * (1 - rocky) * (1 - wallow) * (1 - 0.6f * kill);
        out[1] = live * (0.12f + Math.max(0, n2 * 1.6f - 0.6f)) * (1 - forest) * (1 - rocky) * (1 - shore) * (1 - wallow) * (1 - kill);
        out[2] = wet * Math.max(walk * Math.min(1, rocky + shore * 0.9f + sinter * (1 - sinter) * 1.2f + 0.06f * (1 - sinter)), 0.5f * verge * smooth(-1.0f, -0.2f, road));
        out[3] = wet * walk * Math.min(1, wallow + kill * (1 - sinter) * 0.9f);
        out[4] = Math.max(1 - smooth(2, 18, bank), 0.3f * forest);
    }

    private static final ThreadLocal<float[]> GM = ThreadLocal.withInitial(() -> new float[5]);

    static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Ein Zeitschritt: Jahreszeit, Wind (0..1, 1 ≈ 9 m/s, Richtung wx, wz), wer das Gras niedertritt,
     * und die Kamera. Hoch über dem Boden (Luftbild) gibt es keine Bodendecke.
     */
    public void update(float t, int day, float snow, double wind, double wx, double wz, Animals an, double camX, double camY, double camZ) {
        meadow.setSeason(day, snow);
        meadow.wind.speed = (float) (wind * 9);
        meadow.wind.direction = (float) Math.toDegrees(Math.atan2(wz, wx));
        int n = an == null ? 0 : an.n;
        if (meadow.pushers.length < 4 * n) meadow.pushers = new float[4 * n + 16];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (Math.abs(an.x[i] - camX) > 40 || Math.abs(an.z[i] - camZ) > 40) continue;
            boolean person = an.kind[i] == Animals.PERSON, bison = an.kind[i] == Animals.BISON || an.kind[i] == Animals.BISON_CALF;
            meadow.pushers[4 * k] = an.x[i]; meadow.pushers[4 * k + 1] = an.z[i];
            meadow.pushers[4 * k + 2] = (person ? 0.45f : bison ? 1.3f : 0.9f) * an.scale[i];
            meadow.pushers[4 * k + 3] = person ? 0.8f : 1;
            k++;
        }
        meadow.pusherCount = k;
        float above = (float) (camY - terrain.sample(camX, camZ));
        if (above > 45) { meadow.batch.nt = 0; meadow.batch.nv = 0; return; }
        meadow.update(t, camX, camZ);
    }
}
