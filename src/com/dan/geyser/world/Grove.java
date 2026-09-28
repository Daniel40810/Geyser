package com.dan.geyser.world;

import com.dan.forest.LeafFall;
import com.dan.forest.Season;
import com.dan.forest.Species;
import com.dan.forest.TreeGenerator;
import com.dan.forest.TreeMesh;
import com.dan.forest.WindField;
import com.dan.geyser.core.LeafQuads;
import com.dan.geyser.core.Mat;
import com.dan.geyser.core.Materials;
import com.dan.geyser.core.Mesh;
import com.dan.geyser.core.MeshBuilder;
import com.dan.geyser.core.Terrain;

/**
 * Die Bäume des Beckens aus dem Wald-Paket ({@link com.dan.forest}): Drehkiefern und Espenhaine.
 * <ul>
 *   <li>Kiefern in der Tiefe des Waldes stehen als Silhouette (Stamm und Krone mit den Stufen der
 *       Quirle, gut 50 Dreiecke); am Waldrand nahe den Stegen mit Ästen und Nadelballen.</li>
 *   <li>Espen stehen in Hainen am Waldrand, mit weißer Rinde und Blattbüscheln, die im Wind zittern.
 *       Sie treiben im Juni aus, werden im September goldgelb und verlieren bis Mitte Oktober ihr Laub.
 *       Ein Hain ist in der Natur ein einziger Klon aus einer Wurzel und färbt sich gemeinsam.</li>
 *   <li>Im Herbst lösen sich Blätter in der Nähe der Kamera, segeln im Wind und bleiben eine Weile
 *       liegen.</li>
 * </ul>
 * Die Bäume sind Teil des festen Szenennetzes; die Jahreszeit verschiebt nur die Ecken der Blätter
 * (Blätter, die noch nicht ausgetrieben oder schon gefallen sind, schrumpfen auf ihren Stiel).
 */
public final class Grove {
    /** Espe, fürs Becken mit weniger, dafür größeren Büscheln (spart Dreiecke). */
    final Species aspen;
    private static TreeMesh[] PINES_FAR, PINES_NEAR, ASPENS, ASPENS_MID, ASPENS_FAR;

    // Ecken der Blätter im Szenennetz: Nummer, Stiel (3), Abstand zum Stiel in Ruhe (3), Schwelle fürs Fallen
    private int nLv;
    private int[] lv = new int[4096];
    private float[] piv = new float[3 * 4096], off = new float[3 * 4096], thr = new float[4096];
    // Büschel für den Laubfall: Mitte (3), Schwelle, Ton
    private int nCl;
    private float[] cl = new float[5 * 1024];

    private Mesh mesh;
    public int aspens, pinesNear, pinesFar;

    private Season season;
    private int appliedDay = -1;
    private float appliedSnow = -1;

    public final LeafFall fall = new LeafFall(LeafQuads.CAP);
    public final LeafQuads quads = new LeafQuads();
    private final WindField wind = new WindField();
    private final java.util.Random rnd = new java.util.Random(290);
    private int[] near = new int[0];
    private int nNear;
    private double nearX = Double.NaN, nearZ = Double.NaN;
    private double clock, carry;

    Grove() {
        aspen = Species.aspen();
        aspen.leafDensity *= 0.35f;
        aspen.cluster *= 1.55f;
        // im trockenen Hochland etwas matter als die Voreinstellung
        aspen.summer = new float[]{0.06f, 0.15f, 0.035f};
        aspen.spring = new float[]{0.13f, 0.28f, 0.05f};
    }

    private static synchronized void variants() {
        if (PINES_FAR != null) return;
        Species pine = Species.lodgepolePine(), as = new Grove().aspen, pineNear = Species.lodgepolePine();
        pineNear.count[1] *= 0.6f;                       // weniger, dafür dichtere Quirläste
        PINES_FAR = new TreeMesh[8];
        PINES_NEAR = new TreeMesh[6];
        ASPENS = new TreeMesh[6];
        ASPENS_MID = new TreeMesh[6];
        ASPENS_FAR = new TreeMesh[6];
        for (int i = 0; i < PINES_FAR.length; i++) PINES_FAR[i] = TreeMesh.silhouette(TreeGenerator.grow(pine, 1872 + i * 31L, 1), 5, 5);
        for (int i = 0; i < PINES_NEAR.length; i++) PINES_NEAR[i] = TreeMesh.build(TreeGenerator.grow(pineNear, 2210 + i * 37L, 1), 2);
        for (int i = 0; i < ASPENS.length; i++) {
            com.dan.forest.TreeModel tm = TreeGenerator.grow(as, 1959 + i * 41L, 1);
            ASPENS[i] = TreeMesh.build(tm, 1);
            ASPENS_MID[i] = TreeMesh.build(tm, 1, 2);
            ASPENS_FAR[i] = TreeMesh.build(tm, 2);
        }
    }

    // ------------------------------------------------------------ Bau

    /** Drehkiefer: nahe = mit Ästen und Nadelballen, sonst als Silhouette; 13 bis 24 m hoch. */
    void pine(MeshBuilder mb, double x, double y, double z, java.util.Random rnd, boolean detailed) {
        variants();
        TreeMesh[] set = detailed ? PINES_NEAR : PINES_FAR;
        TreeMesh m = set[rnd.nextInt(set.length)];
        double H = 13 + 11 * rnd.nextDouble();
        emit(mb, m, x, y, z, H, rnd.nextDouble() * 2 * Math.PI, 0.35, 1.6, false);
        if (detailed) pinesNear++; else pinesFar++;
    }

    /**
     * Espe, 10 bis 18 m hoch. detail 0: Blattbüschel an allen Zweigen, 1: Blattbüschel an den
     * Hauptästen (die feinen Zweige fehlen), 2: ein Laubballen je Hauptast. Blätter und Ballen werden
     * für die Jahreszeit vermerkt.
     */
    void aspen(MeshBuilder mb, double x, double y, double z, java.util.Random rnd, int detail) {
        variants();
        TreeMesh[] set = detail <= 0 ? ASPENS : detail == 1 ? ASPENS_MID : ASPENS_FAR;
        TreeMesh m = set[rnd.nextInt(set.length)];
        double H = 10 + 8 * rnd.nextDouble();
        emit(mb, m, x, y, z, H, rnd.nextDouble() * 2 * Math.PI, 0.5, 1.4, true);
        aspens++;
    }

    private void emit(MeshBuilder mb, TreeMesh m, double x, double y, double z, double H, double yaw, double sway, double pw, boolean broadleaf) {
        double k = H / m.model.height, cs = Math.cos(yaw), sn = Math.sin(yaw);
        final double yb = y;
        mb.swayFn = (px, py, pz) -> sway * Math.pow(Math.max(0, (py - yb) / H), pw);
        int keep = mb.group;
        mb.group = 1;                                    // von beiden Seiten sichtbar
        int[] id = new int[m.nv];
        for (int i = 0; i < m.nv; i++) {
            double px = m.pos[3 * i], py = m.pos[3 * i + 1], pz = m.pos[3 * i + 2];
            double nx = m.nrm[3 * i], ny = m.nrm[3 * i + 1], nz = m.nrm[3 * i + 2];
            boolean leaf = broadleaf && m.part[i] != TreeMesh.BARK;
            mb.flutterValue = leaf ? (m.part[i] == TreeMesh.LEAF ? 0.05 : 0.03) : 0;
            id[i] = mb.v(x + (px * cs - pz * sn) * k, y - 0.3 + py * k, z + (px * sn + pz * cs) * k, nx * cs - nz * sn, ny, nx * sn + nz * cs);
        }
        mb.flutterValue = 0;
        for (int t = 0; t < m.nt; t++) {
            int a = m.tri[3 * t], b = m.tri[3 * t + 1], c = m.tri[3 * t + 2];
            int mat = m.part[a] == TreeMesh.BARK ? (broadleaf ? Mat.WHITEBARK : Mat.BARK) : broadleaf ? Mat.LEAVES : Mat.NEEDLES;
            mb.tri(id[a], id[b], id[c], mat);
        }
        mb.group = keep;
        mb.swayFn = null;
        if (!broadleaf) return;
        // Laubballen (ferne Espen): jede zusammenhängende Folge von Ecken eines Asts ist ein Ballen
        for (int i = 0; i < m.nv; ) {
            if (m.part[i] != TreeMesh.CLUMP) { i++; continue; }
            int j = i;
            while (j < m.nv && m.part[j] == TreeMesh.CLUMP && m.bone[j] == m.bone[i]) j++;
            double sx = 0, sy = 0, sz = 0;
            for (int q = i; q < j; q++) { sx += m.pos[3 * q]; sy += m.pos[3 * q + 1]; sz += m.pos[3 * q + 2]; }
            sx /= j - i; sy /= j - i; sz /= j - i;
            record(id, m, i, j, x + (sx * cs - sz * sn) * k, y - 0.3 + sy * k, z + (sx * sn + sz * cs) * k,
                    0.15f + 0.8f * rnd.nextFloat(), rnd.nextFloat(), x, y, z, k, cs, sn);
            i = j;
        }
        // Blätter vermerken: jede Ecke mit ihrem Stiel (der Mitte des Büschels)
        for (int li = 0; li < m.leafIds.length; li++) {
            int c = m.leafVertex[li], e = c;
            while (e < m.nv && m.leaf[e] == li && m.part[e] == TreeMesh.LEAF) e++;
            com.dan.forest.TreeModel.LeafSpot ls = m.model.leaves.get(m.leafIds[li]);
            record(id, m, c, e, x + (m.pos[3 * c] * cs - m.pos[3 * c + 2] * sn) * k, y - 0.3 + m.pos[3 * c + 1] * k,
                    z + (m.pos[3 * c] * sn + m.pos[3 * c + 2] * cs) * k, ls.drop, ls.tone, x, y, z, k, cs, sn);
        }
    }

    /** Ecken i0..i1 der Vorlage m als ein Büschel mit Stiel (cx, cy, cz), Schwelle fürs Fallen und Ton. */
    private void record(int[] id, TreeMesh m, int i0, int i1, double cx, double cy, double cz, float drop, float tone,
                        double x, double y, double z, double k, double cs, double sn) {
        if (5 * nCl + 5 > cl.length) cl = java.util.Arrays.copyOf(cl, cl.length * 2);
        cl[5 * nCl] = (float) cx; cl[5 * nCl + 1] = (float) cy; cl[5 * nCl + 2] = (float) cz; cl[5 * nCl + 3] = drop; cl[5 * nCl + 4] = tone;
        nCl++;
        for (int i = i0; i < i1; i++) {
            if (nLv == lv.length) {
                lv = java.util.Arrays.copyOf(lv, nLv * 2); thr = java.util.Arrays.copyOf(thr, nLv * 2);
                piv = java.util.Arrays.copyOf(piv, 6 * nLv); off = java.util.Arrays.copyOf(off, 6 * nLv);
            }
            double px = x + (m.pos[3 * i] * cs - m.pos[3 * i + 2] * sn) * k, py = y - 0.3 + m.pos[3 * i + 1] * k,
                    pz = z + (m.pos[3 * i] * sn + m.pos[3 * i + 2] * cs) * k;
            lv[nLv] = id[i];
            piv[3 * nLv] = (float) cx; piv[3 * nLv + 1] = (float) cy; piv[3 * nLv + 2] = (float) cz;
            off[3 * nLv] = (float) (px - cx); off[3 * nLv + 1] = (float) (py - cy); off[3 * nLv + 2] = (float) (pz - cz);
            thr[nLv] = drop;
            nLv++;
        }
    }

    /** Das fertige Szenennetz; erst danach wirkt die Jahreszeit. */
    void attach(Mesh m) { mesh = m; appliedDay = -1; }

    /** Wie viele Blattbüschel die Espen tragen (für Prüfungen). */
    public int clusters() { return nCl; }

    // ------------------------------------------------------------ Jahreszeit

    /**
     * Tag im Jahr (1..365) und Schnee 0..1: Farbe des Laubs und welche Blätter an den Bäumen sind.
     * Schiebt die Ecken nur, wenn sich etwas sichtbar ändert.
     */
    public void setSeason(int day, float snow) {
        if (day == appliedDay && Math.abs(snow - appliedSnow) < 0.02f) return;
        Season s = Season.of(aspen, day, snow);
        float[] lut = new float[3 * 8], c = new float[3];
        for (int i = 0; i < 8; i++) {
            s.leafColor(aspen, i / 7f, c);
            lut[3 * i] = c[0]; lut[3 * i + 1] = c[1]; lut[3 * i + 2] = c[2];
        }
        Materials.leafLut = lut;
        Season old = season;
        season = s;
        appliedDay = day; appliedSnow = snow;
        Mesh m = mesh;
        if (m == null) return;
        if (old != null && Math.abs(old.foliage - s.foliage) < 1e-3f && Math.abs(old.drop - s.drop) < 1e-3f) return;
        float[] p = m.pos;
        float lim = Math.min(1, s.drop);
        for (int k = 0; k < nLv; k++) {
            float sc = thr[k] < lim ? 0 : s.foliage;
            int v = 3 * lv[k];
            p[v] = piv[3 * k] + off[3 * k] * sc;
            p[v + 1] = piv[3 * k + 1] + off[3 * k + 1] * sc;
            p[v + 2] = piv[3 * k + 2] + off[3 * k + 2] * sc;
        }
        m.version++;
    }

    // ------------------------------------------------------------ Laubfall

    /**
     * Ein Zeitschritt: im Herbst lösen sich Blätter der Büschel in bis zu 110 m um die Kamera,
     * stärker bei Wind; alle Blätter segeln und liegen. windK 0..1 (1 ≈ 9 m/s), (wx, wz) die Richtung.
     */
    public void update(double dt, double windK, double wx, double wz, double camX, double camZ, Terrain terrain) {
        clock += dt;
        wind.speed = (float) (windK * 9);
        wind.direction = (float) Math.toDegrees(Math.atan2(wz, wx));
        wind.gustiness = 0.6f;
        Season s = season;
        if (s != null && s.foliage > 0 && s.autumn > 0.2f && s.drop < 1) {
            if (Double.isNaN(nearX) || Math.hypot(camX - nearX, camZ - nearZ) > 15) findNear(camX, camZ);
            if (nNear > 0) {
                float lim = Math.min(1, s.drop);
                // je Büschel in Reichweite im Mittel alle paar Minuten ein Blatt, bei Sturm viel öfter
                carry += dt * nNear * 0.004 * (0.3 + 2.5 * windK) * Math.min(1, s.autumn * 1.5);
                int tries = 0;
                while (carry >= 1 && tries++ < 200) {
                    int c = near[rnd.nextInt(nNear)];
                    if (cl[5 * c + 3] < lim) continue;
                    carry -= 1;
                    float[] col = new float[3];
                    s.leafColor(aspen, cl[5 * c + 4], col);
                    float r = aspen.cluster * 0.5f;
                    fall.add(cl[5 * c] + (rnd.nextFloat() - 0.5f) * r, cl[5 * c + 1] + (rnd.nextFloat() - 0.5f) * r, cl[5 * c + 2] + (rnd.nextFloat() - 0.5f) * r,
                            col[0], col[1], col[2], 0.07f + 0.02f * rnd.nextFloat());
                }
                if (carry > 5) carry = 5;
            }
        }
        if (fall.n > 0) fall.step((float) dt, wind, clock, (x, z) -> terrain.sample(x, z));
        quads.n = fall.quads(quads.xyz, quads.rgb, quads.nrm);
    }

    private void findNear(double x, double z) {
        nearX = x; nearZ = z;
        if (near.length < nCl) near = new int[nCl];
        nNear = 0;
        for (int i = 0; i < nCl; i++) {
            double dx = cl[5 * i] - x, dz = cl[5 * i + 2] - z;
            if (dx * dx + dz * dz < 110 * 110) near[nNear++] = i;
        }
    }
}
