package com.dan.forest.demo;

import com.dan.forest.Forest;
import com.dan.forest.ForestPlanter;
import com.dan.forest.Ground;
import com.dan.forest.LeafFall;
import com.dan.forest.Season;
import com.dan.forest.Species;
import com.dan.forest.TreeAnimator;
import com.dan.forest.TreeGenerator;
import com.dan.forest.TreeMesh;
import com.dan.forest.TreeModel;
import com.dan.forest.WindField;

/**
 * Selbstprüfung des Wald-Pakets ohne Fenster: jede Art wächst gleich aus demselben Startwert, die
 * Netze haben keine ungültigen Zahlen und bleiben im Rahmen, ohne Wind steht der Baum still, mit Wind
 * bewegt sich die Spitze mehr als der Fuß, das Laub färbt sich und fällt, fallende Blätter landen.
 * Aufruf: {@code java com.dan.forest.demo.ForestCheck}; Ende mit Code 1, wenn etwas nicht stimmt.
 */
public final class ForestCheck {
    private static int fails;

    public static void main(String[] a) {
        for (java.util.function.Supplier<Species> f : Species.ALL) {
            Species s = f.get();
            TreeModel m1 = TreeGenerator.grow(s, 42, 1), m2 = TreeGenerator.grow(s, 42, 1);
            check(s.name + ": gleicher Startwert, gleicher Baum", m1.branches.size() == m2.branches.size() && m1.leaves.size() == m2.leaves.size()
                    && m1.branches.get(m1.branches.size() - 1).x[1] == m2.branches.get(m2.branches.size() - 1).x[1]);
            check(s.name + ": Höhe in der Spanne der Art", m1.height >= s.heightMin * 0.99f && m1.height <= s.heightMax * 1.01f);
            int prevTris = Integer.MAX_VALUE;
            for (int l = 0; l < 4; l++) {
                TreeMesh t = TreeMesh.build(m1, l);
                boolean ok = t.nt > 0;
                for (int i = 0; ok && i < 3 * t.nv; i++) ok = Float.isFinite(t.pos[i]) && Float.isFinite(t.nrm[i]) && Float.isFinite(t.col[i]);
                for (int i = 0; ok && i < 3 * t.nt; i++) ok = t.tri[i] >= 0 && t.tri[i] < t.nv;
                check(s.name + " Stufe " + l + ": gültig (" + t.nt + " Dreiecke)", ok);
                check(s.name + " Stufe " + l + ": weniger Dreiecke als die Stufe davor", t.nt < prevTris);
                prevTris = t.nt;
            }
            // Wind: ohne Wind still, mit Wind bewegt sich die Spitze deutlich, der Fuß kaum
            TreeMesh t = TreeMesh.build(m1, 1);
            TreeAnimator an = new TreeAnimator(t);
            WindField.Sample calm = new WindField.Sample();
            calm.speed = 0;
            an.pose(3.3, calm, null);
            float still = maxMove(t, an.pos, 0, Float.MAX_VALUE);
            check(s.name + ": ohne Wind still", still < 1e-3f);
            WindField.Sample storm = new WindField.Sample();
            storm.speed = 14; storm.gust = 1.3f; storm.turb = 0.6f;
            an.pose(3.3, storm, null);
            float top = maxMove(t, an.pos, m1.height * 0.8f, Float.MAX_VALUE), foot = maxMove(t, an.pos, -1, 0.5f);
            check(s.name + String.format(": Sturm bewegt die Krone (%.2f m), der Fuß bleibt (%.3f m)", top, foot), top > 0.05f && foot < 0.05f && top < m1.height * 0.3f);
            // Jahreszeit
            Season summer = Season.of(s, 200, 0), winter = Season.of(s, 20, 0);
            check(s.name + ": im Sommer belaubt", summer.foliage > 0.99f);
            check(s.name + (s.deciduous ? ": im Winter kahl" : ": im Winter grün"), s.deciduous ? winter.foliage == 0 : winter.foliage == 1);
            if (s.deciduous) check(s.name + ": färbt sich im Herbst", Season.of(s, s.colorFull, 0).autumn > 0.95f);
        }
        // Wald und Laubfall
        Forest forest = new Forest(Ground.FLAT);
        int as = forest.addSpecies(Species.aspen());
        ForestPlanter.plant(forest, -30, -30, 30, 30, 5, null, new int[]{as}, new float[]{1}, 0, Ground.FLAT, 3);
        check("Wald: Bäume gesetzt (" + forest.trees.size() + ")", forest.trees.size() > 30);
        boolean spaced = true;
        for (int i = 0; i < forest.trees.size(); i++) for (int j = i + 1; j < forest.trees.size(); j++) {
            var p = forest.trees.get(i); var q = forest.trees.get(j);
            if (Math.hypot(p.x - q.x, p.z - q.z) < 1.0) spaced = false;
        }
        check("Wald: Mindestabstand eingehalten", spaced);
        forest.wind.speed = 12;
        forest.setSeason(Species.aspen().colorFull + 5, 0);
        LeafFall lf = forest.leaves;
        int maxN = 0, maxLanded = 0;
        for (int k = 0; k < 300; k++) forest.update(k / 10.0, 0.1f, 0, 1.7, 0);
        forest.setSeason(Species.aspen().bare - 3, 0);
        for (int k = 300; k < 600; k++) {
            forest.update(k / 10.0, 0.1f, 0, 1.7, 0);
            maxN = Math.max(maxN, lf.n);
            maxLanded = Math.max(maxLanded, lf.n - lf.flying());
        }
        check("Laubfall: Blätter gefallen (bis " + maxN + ", davon am Boden bis " + maxLanded + ")", maxN > 500 && maxLanded > 100);
        System.out.println(fails == 0 ? "Alles in Ordnung." : fails + " Prüfungen fehlgeschlagen.");
        if (fails > 0) System.exit(1);
    }

    private static float maxMove(TreeMesh t, float[] pos, float yMin, float yMax) {
        float m = 0;
        for (int i = 0; i < t.nv; i++) {
            float y = t.pos[3 * i + 1];
            if (y < yMin || y > yMax) continue;
            float dx = pos[3 * i] - t.pos[3 * i], dy = pos[3 * i + 1] - t.pos[3 * i + 1], dz = pos[3 * i + 2] - t.pos[3 * i + 2];
            m = Math.max(m, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return m;
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
        if (!ok) fails++;
    }
}
