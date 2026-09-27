package com.dan.geyser.atom;

import com.dan.geyser.core.Mat;
import com.dan.geyser.core.Mesh;

/**
 * Zeitraffer am Kegel von Castle Geyser: hebt und senkt die Ecken von Sockel und Kegel im Netz
 * der Szene. Erst wächst der Sockel (die ältere Terrasse), dann der Kegel, wie es die Schichtfolge
 * zeigt (alte Terrasse unten, junger Kegel oben). Die Mengen sind belegt: rund 5000 t Sinter,
 * 470 bis 940 kg im Jahr; der Kegel ist um 1022 datiert. Die Dauer des Sockels folgt daraus
 * (Rechnung, keine Messung): 5000 t bei 705 kg im Jahr sind rund 7100 Jahre.
 */
public final class SinterGrowth {
    /** Jahre vor 2026, ab denen der Zeitraffer läuft, und das Jahr, in dem der Kegel beginnt. */
    public static final int START_BP = 7100, CONE_YEAR = 1022, NOW = 2026;
    public static final double MASS_T = 5000, RATE_LO = 470, RATE_HI = 940;

    private final Mesh mesh;
    private final int[] vi, ti;
    private final float[] y0, n0, fn0;
    private final double base, split;
    private double last = 1;

    /**
     * Sammelt alle Ecken der Kegel-Dreiecke im Umkreis r um (cx, cz). base ist der Fuß des Sockels,
     * split die Höhe über base, ab der der Kegel beginnt.
     */
    public SinterGrowth(Mesh m, double cx, double cz, double r, double base, double split) {
        this.mesh = m; this.base = base; this.split = split;
        boolean[] take = new boolean[m.nv];
        int nt = 0;
        java.util.List<Integer> tris = new java.util.ArrayList<>();
        for (int t = 0; t < m.nt; t++) {
            if (m.mat[t] != Mat.CONE) continue;
            int a = m.idx[3 * t];
            double dx = m.pos[3 * a] - cx, dz = m.pos[3 * a + 2] - cz;
            if (dx * dx + dz * dz > r * r || m.pos[3 * a + 1] < base - 0.5) continue;
            tris.add(t);
            for (int k = 0; k < 3; k++) take[m.idx[3 * t + k]] = true;
        }
        int n = 0;
        for (boolean b : take) if (b) n++;
        vi = new int[n];
        n = 0;
        for (int v = 0; v < m.nv; v++) if (take[v]) vi[n++] = v;
        ti = new int[tris.size()];
        for (int i = 0; i < ti.length; i++) ti[i] = tris.get(i);
        y0 = new float[vi.length];
        n0 = new float[vi.length * 3];
        for (int i = 0; i < vi.length; i++) {
            int v = vi[i];
            y0[i] = m.pos[3 * v + 1];
            System.arraycopy(m.nrm, 3 * v, n0, 3 * i, 3);
        }
        fn0 = new float[ti.length * 3];
        for (int i = 0; i < ti.length; i++) System.arraycopy(m.fn, 3 * ti[i], fn0, 3 * i, 3);
    }

    public int vertices() { return vi.length; }

    /**
     * Sockel- und Kegelanteil 0..1 zum Fortschritt u (0 = vor 7100 Jahren, 1 = heute). Die ersten
     * 55 % der Zeit zeigen die Terrasse über gut 6000 Jahre, die letzten 45 % die 1000 Jahre des Kegels.
     */
    public static double[] shares(double u) {
        double coneBP = NOW - CONE_YEAR;
        u = Math.max(0, Math.min(1, u));
        double yearsBP = u < 0.55 ? START_BP - (START_BP - coneBP) * (u / 0.55) : coneBP * (1 - (u - 0.55) / 0.45);
        double gp = Math.min(1, (START_BP - yearsBP) / (START_BP - coneBP));
        double gc = yearsBP > coneBP ? 0 : 1 - yearsBP / coneBP;
        return new double[]{gp, gc, yearsBP};
    }

    /** Wachstum setzen: Sockel gp, Kegel gc (je 0..1). Liefert die neue Höhe der Kegelspitze über base. */
    public synchronized double set(double gp, double gc) {
        gp = Math.max(0.03, Math.min(1, gp));
        gc = Math.max(0.0, Math.min(1, gc));
        double top = 0;
        for (int i = 0; i < vi.length; i++) {
            int v = vi[i];
            double h = y0[i] - base;
            double h2 = Math.min(h, split) * gp + Math.max(0, h - split) * Math.max(0.02, gc);
            mesh.pos[3 * v + 1] = (float) (base + h2);
            top = Math.max(top, h2);
            double s = h > split ? Math.max(0.02, gc) : gp;
            float nx = n0[3 * i], ny = (float) (n0[3 * i + 1] / s), nz = n0[3 * i + 2];
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            mesh.nrm[3 * v] = nx / l; mesh.nrm[3 * v + 1] = ny / l; mesh.nrm[3 * v + 2] = nz / l;
        }
        mesh.version++;
        for (int i = 0; i < ti.length; i++) {
            int t = ti[i];
            int a = mesh.idx[3 * t];
            double h = y0[java.util.Arrays.binarySearch(vi, a)] - base;
            double s = h > split ? Math.max(0.02, gc) : gp;
            float nx = fn0[3 * i], ny = (float) (fn0[3 * i + 1] / s), nz = fn0[3 * i + 2];
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            mesh.fn[3 * t] = nx / l; mesh.fn[3 * t + 1] = ny / l; mesh.fn[3 * t + 2] = nz / l;
        }
        last = gc;
        return top;
    }

    /** Alles wieder auf den heutigen Stand. */
    public void reset() { set(1, 1); }

    /** Sinter in Tonnen bis zum Jahr (vor heute) yearsBP, bei gleichmäßigem Wachstum. */
    public static double massAt(double yearsBP) {
        return MASS_T * (1 - yearsBP / START_BP);
    }
}
