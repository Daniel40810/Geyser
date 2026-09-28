package com.dan.geyser.world;

import com.dan.forest.LeafFall;
import com.dan.geyser.core.LeafQuads;
import com.dan.geyser.core.Mat;
import com.dan.geyser.core.MeshBuilder;
import com.dan.geyser.core.Terrain;
import com.dan.river.Drift;
import com.dan.river.FlowField;
import com.dan.river.RNoise;
import com.dan.river.RiverPath;
import com.dan.river.Rock;
import com.dan.river.WaterSurface;

/**
 * Der Firehole River mit dem Fluss-Paket ({@link com.dan.river}): Strömung nach Gefälle, Breite und
 * Kurven, Felsblöcke mit Kehrwasser und Schaum, Wellen und Schaumstreifen, die mit dem Wasser ziehen,
 * und Treibgut um die Kamera. Espenlaub, das auf den Fluss fällt, treibt weiter.
 * <p>
 * Der Firehole ist in den Becken 15 bis 25 m breit, flach (um 1 m) und fließt mit rund einem Meter je
 * Sekunde; über Riffeln aus Rhyolith wird er unruhig. Die Blöcke hier sind nicht vermessen, sondern in
 * Riffelstrecken verteilt.
 */
public final class Firehole implements LeafFall.Water {
    public final RiverPath path;
    public final FlowField flow;
    public final WaterSurface surface = new WaterSurface();
    public final Drift drift = new Drift(LeafQuads.CAP);
    public final LeafQuads quads = new LeafQuads();
    private final FlowField.Flow f = new FlowField.Flow();
    public int rocks;

    Firehole(Terrain t) {
        int n = t.riverPoints();
        double[][] nodes = new double[n][];
        for (int i = 0; i < n; i++) nodes[i] = new double[]{t.riverX(i), t.riverZ(i), t.riverLevel(i), t.riverHalf(i)};
        path = new RiverPath(nodes, 5, 14);
        flow = new FlowField(path);
        flow.depth = 1.1f;
        drift.foamDensity = 4f;
        drift.foamSize = 0.07f;
        drift.leafDensity = 0.03f;
        drift.twigDensity = 0.03f;
        surface.amplitude = 1.1f;
    }

    /**
     * Felsblöcke in Riffelstrecken, nur wo das Gelände fein ist (in den Becken): als Hindernis in der
     * Strömung und als Fels im Netz.
     */
    void rocks(MeshBuilder mb, Terrain t, java.util.Random rnd) {
        float[] c = new float[6];
        for (double s = 0; s < path.length; s += 6) {
            path.at(s, c);
            if (!in(t.fine, c[0], c[1]) && !in(t.fine2, c[0], c[1]) && !in(t.fine3, c[0], c[1])) continue;
            float riffle = RNoise.value((float) (s / 140), 3.3f);
            if (riffle < 0.62f || rnd.nextDouble() > (riffle - 0.62) * 3.2) continue;
            double u = (rnd.nextDouble() * 2 - 1) * 0.75;
            double px = c[0] - c[5] * u * c[3], pz = c[1] + c[4] * u * c[3];
            double r = 0.35 + 0.8 * rnd.nextDouble() * rnd.nextDouble();
            double top = rnd.nextDouble() < 0.3 ? -0.06 : 0.08 + 0.45 * rnd.nextDouble();
            flow.add(new Rock(px, pz, r, top));
            if (top > 0) boulder(mb, px, c[2], pz, r, top, rnd);
            rocks++;
        }
    }

    private static boolean in(Terrain.Grid g, double x, double z) { return x > g.x0 + 20 && x < g.x1() - 20 && z > g.z0 + 20 && z < g.z1() - 20; }

    /** Block aus Rhyolith: flaches, unregelmäßiges Ellipsoid, das top Meter über den Spiegel ragt. */
    private static void boulder(MeshBuilder mb, double x, double level, double z, double r, double top, java.util.Random rnd) {
        double below = 0.4, ry = top + below;
        double rh = r / Math.sqrt(1 - (below / ry) * (below / ry));
        double cy = level - below;
        int nl = 7, nr = 4;
        int[][] id = new int[nr + 1][nl];
        double rot = rnd.nextDouble() * 6.28, sq = 0.75 + 0.5 * rnd.nextDouble();
        mb.swayFn = null;
        mb.swayValue = 0;
        for (int j = 0; j <= nr; j++) {
            double el = Math.PI / 2 * j / nr;            // 0 am Äquator (unter Wasser), π/2 oben
            for (int i = 0; i < nl; i++) {
                double az = 2 * Math.PI * i / nl + rot;
                double jag = 0.85 + 0.3 * RNoise.value((float) (x + i * 1.7), (float) (z + j * 2.3));
                double hx = Math.cos(az) * rh * jag * Math.cos(el), hz = Math.sin(az) * rh * jag * sq * Math.cos(el);
                double hy = ry * Math.sin(el) * (j == nr ? 1 : jag);
                double nx = Math.cos(az) * Math.cos(el) / rh, ny = Math.sin(el) / ry, nz = Math.sin(az) * Math.cos(el) / rh;
                id[j][i] = mb.v(x + hx, cy + hy, z + hz, nx, ny, nz);
            }
        }
        for (int j = 0; j < nr; j++)
            for (int i = 0; i < nl; i++) {
                int i1 = (i + 1) % nl;
                mb.tri(id[j][i], id[j + 1][i], id[j][i1], Mat.ROCK);
                mb.tri(id[j][i1], id[j + 1][i], id[j + 1][i1], Mat.ROCK);
            }
    }

    // ------------------------------------------------------------ Laub auf dem Wasser

    @Override public float level(float x, float z) {
        synchronized (f) {
            return flow.sample(x, z, f) && f.wet ? f.level : Float.NaN;
        }
    }

    @Override public boolean take(float x, float y, float z, float r, float g, float b, float size) {
        return drift.add(x, z, Drift.LEAF, r, g, b, size) && setY(y);
    }

    private boolean setY(float y) { drift.y[drift.n - 1] = y; return true; }

    // ------------------------------------------------------------ Zeitschritt

    /**
     * Treibgut bewegen und für den Bildrechner bereitlegen; Wind 0..1 (1 ≈ 9 m/s) in Richtung (wx, wz)
     * kräuselt das Wasser. Treibgut gibt es nur, wenn die Kamera nahe am Fluss ist.
     */
    public void update(double dt, double wind, double wx, double wz, double camX, double camZ) {
        surface.wind = (float) (wind * 9);
        surface.windDir = (float) Math.toDegrees(Math.atan2(wz, wx));
        boolean near;
        synchronized (f) {
            RiverPath.Loc L = f.loc;
            near = path.locate(camX, camZ, L) || nearRiver(camX, camZ);
        }
        if (near || drift.n > 0) drift.step((float) dt, flow, camX, camZ, near ? 110 : 1);
        int n = drift.quads(quads.xyz, quads.rgb);
        for (int i = 0; i < n; i++) { quads.nrm[3 * i] = 0; quads.nrm[3 * i + 1] = 1; quads.nrm[3 * i + 2] = 0; }
        quads.n = n;
    }

    /** Liegt der Fluss höchstens 110 m entfernt? (grob über acht Richtungen) */
    private boolean nearRiver(double x, double z) {
        RiverPath.Loc L = new RiverPath.Loc();
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            for (double d = 25; d <= 110; d += 42) if (path.locate(x + Math.cos(a) * d, z + Math.sin(a) * d, L)) return true;
        }
        return false;
    }
}
