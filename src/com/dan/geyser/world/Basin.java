package com.dan.geyser.world;

import com.dan.geyser.core.Mat;
import com.dan.geyser.core.MeshBuilder;
import com.dan.geyser.core.Noise;
import com.dan.geyser.core.Scene;
import com.dan.geyser.core.Terrain;
import com.dan.geyser.core.Thermal;

/**
 * Baut die Welt: Gelände (vier Gitter), Firehole River, die Sinterkegel der Geysire, die Becken der
 * heißen Quellen mit Geyseritrand, Wald aus Drehkiefern mit toten Stämmen am Sinterrand, Stege und
 * Brücken, den Aussichtspunkt über Grand Prismatic; dazu die Quellen fürs Temperaturfeld und die
 * Röhrenmodelle der Geysire.
 */
public final class Basin {
    private Basin() { }

    /** Wie weit Geyseritkegel über den Boden ragen: y des Schlots über dem Boden. */
    static final double OF_TOP = 1.35, BEEHIVE_TOP = 1.25, CASTLE_TOP = 4.1, RIVERSIDE_TOP = 1.3;
    static final double DAISY_TOP = 0.9, SPLENDID_TOP = 0.5, GROTTO_TOP = 2.4, FAN_TOP = 0.7, MORTAR_TOP = 1.1, TURBAN_TOP = 0.9,
            INDICATOR_TOP = 0.6;

    /**
     * Lage der Nebengeysire, die keine eigene Stelle haben, relativ zu ihrem Nachbarn (m): Turban
     * liegt am Rand von Grands Becken, Beehive's Indicator rund 3 m neben Beehive (Wikipedia), Mortar
     * ein paar Meter von Fan. Die genauen Richtungen sind genähert.
     */
    static final double[] TURBAN_OFF = {-9, -7}, INDICATOR_OFF = {3, 0.5}, MORTAR_OFF = {-6, 9};
    /** Talboden im Lower Geyser Basin (y); rund 10 m unter der Höhe des Fountain Paint Pot, damit er zum Fluss passt. */
    static final double LOWER_FLOOR = -23;

    /** Splendid Geyser: 44,4702049° N, 110,8446526° W (Wikipedia). */
    static final double SPLENDID_LAT = 44.4702049, SPLENDID_LON = -110.8446526;

    public static World build() {
        Terrain t = new Terrain();
        double[] of = {0, 0}, bh = xz(1), ca = xz(2), gr = xz(3), rs = xz(4), mg = xz(5), gps = xz(6);
        double[] exc = {Sites.x(Sites.EXC_LON), Sites.z(Sites.EXC_LAT)};
        double[] crested = {ca[0] + 26, ca[1] - 24}, doublet = {bh[0] - 48, bh[1] - 32}, heart = {bh[0] + 18, bh[1] - 14};
        double[] dy = xz(7), gt = xz(8), fan = xz(9), gi = xz(10);
        double[] spl = {Sites.x(SPLENDID_LON), Sites.z(SPLENDID_LAT)};
        double[] tu = {gr[0] + TURBAN_OFF[0], gr[1] + TURBAN_OFF[1]}, ind = {bh[0] + INDICATOR_OFF[0], bh[1] + INDICATOR_OFF[1]};
        double[] mo = {fan[0] + MORTAR_OFF[0], fan[1] + MORTAR_OFF[1]};
        double[] fpp = xz(11);
        // Geebnete Stellen mit bekannter Höhe (m über 2240 m)
        t.pad(of[0], of[1], 45, 0, 40);
        t.pad(bh[0], bh[1], 22, 4, 32);
        t.pad(ca[0], ca[1], 40, -5, 30);
        t.pad(gr[0], gr[1], 24, -6, 26);
        t.pad(mg[0], mg[1], 9, -15, 22);
        t.bump(-900, -6880, 140, 34);   // Hügel des Aussichtspunkts südlich von Grand Prismatic
        t.pad(gps[0], gps[1], 72, -24, 70);
        t.pad(exc[0], exc[1], 58, Sites.EXC_ELEV - 2240, 26);
        t.pad(exc[0], exc[1], 40, -32.5, 7);
        double rsLevel = t.exact(rs[0], rs[1]);
        t.pad(rs[0], rs[1], 6, rsLevel, 10);
        double dbl = t.exact(doublet[0], doublet[1]), hrt = t.exact(heart[0], heart[1]);
        t.pad(doublet[0], doublet[1], 5, dbl, 8);
        t.pad(heart[0], heart[1], 3, hrt, 6);
        // Die Stellen ab Daisy: Boden vor Ort, im Umkreis geebnet (Höhen über dem Meer unbekannt)
        // Die Daisy-Gruppe liegt auf einer Sinterterrasse über dem Talboden; das Gelände hier hätte sie am
        // Hang, darum eine weite Terrasse auf rund 2236 m (Höhe genähert)
        double dyL = -4;
        t.pad(dy[0] + 8, dy[1] - 12, 70, dyL, 150);
        t.pad(-1190, -1160, 90, -6.5, 70);            // offen zum Talboden hin
        double gtL = t.exact(gt[0], gt[1]), fanL = t.exact(fan[0], fan[1]), giL = t.exact(gi[0], gi[1]);
        t.pad(gt[0], gt[1], 10, gtL, 10);
        t.pad(fan[0], fan[1], 12, fanL, 8);
        t.pad(gi[0], gi[1], 10, giL, 10);
        // Lower Geyser Basin: ein weiter, flacher Talboden östlich des Firehole. Das Gelände hier hätte
        // an seiner Stelle ein Plateau; die Höhe ist dem Talboden angeglichen (Fountain Paint Pot 2227 m)
        t.pad(1300, -10000, 700, LOWER_FLOOR, 520);
        t.build();

        // ------------------------------------------------------------ Quellen fürs Temperaturfeld
        Thermal th = new Thermal();
        t.thermal = th;
        th.add(new Thermal.Spring("Old Faithful", Thermal.Kind.VENT, of[0], of[1], 0, 0, 0, 92, 0, toRiver(t, of), 48, 7, 16, 0)).y = OF_TOP;
        th.add(new Thermal.Spring("Beehive Geyser", Thermal.Kind.VENT, bh[0], bh[1], 0, 0, 0, 92, 0, toRiver(t, bh), 26, 4, 10, 0)).y = 4 + BEEHIVE_TOP;
        th.add(new Thermal.Spring("Castle Geyser", Thermal.Kind.VENT, ca[0], ca[1], 0, 0, 0, 92, 0, toRiver(t, ca), 46, 9, 18, 0)).y = -5 + CASTLE_TOP;
        th.add(new Thermal.Spring("Crested Pool", Thermal.Kind.POOL, crested[0], crested[1], 4.4, 4.0, 12, 92, 3, toRiver(t, crested), 10, 3, 10, 0.86)).y = -5 + 0.08;
        th.add(new Thermal.Spring("Grand Geyser", Thermal.Kind.POOL, gr[0], gr[1], 5.2, 4.3, 6, 92, 5, toRiver(t, gr), 42, 6, 14, 0.8)).y = -6 + 0.08;
        th.add(new Thermal.Spring("Riverside Geyser", Thermal.Kind.VENT, rs[0], rs[1], 0, 0, 0, 92, 0, toRiver(t, rs), 14, 3, 8, 0)).y = rsLevel + RIVERSIDE_TOP;
        th.add(new Thermal.Spring("Morning Glory Pool", Thermal.Kind.POOL, mg[0], mg[1], 3.6, 3.2, 7, 69.8, 5, toRiver(t, mg), 6, 2.2, 9, 0.85)).y = -15 + 0.08;
        th.add(new Thermal.Spring("Doublet Pool", Thermal.Kind.POOL, doublet[0], doublet[1], 3.4, 2.3, 5, 88, 6, toRiver(t, doublet), 16, 3, 9, 0.8)).y = dbl + 0.08;
        th.add(new Thermal.Spring("Heart Spring", Thermal.Kind.POOL, heart[0], heart[1], 1.7, 1.2, 4, 90, 4, toRiver(t, heart), 8, 2, 6, 0.8)).y = hrt + 0.08;
        th.add(new Thermal.Spring("Grand Prismatic Spring", Thermal.Kind.POOL, gps[0], gps[1], 55, 55, 50, 70, 7, toRiver(t, gps), 150, 55, 44, 0.82)).y = -24 + 0.08;
        th.add(new Thermal.Spring("Daisy Geyser", Thermal.Kind.VENT, dy[0], dy[1], 0, 0, 0, 92, 0, toRiver(t, dy), 24, 4, 10, 0)).y = dyL + DAISY_TOP;
        th.add(new Thermal.Spring("Splendid Geyser", Thermal.Kind.VENT, spl[0], spl[1], 0, 0, 0, 84, 0, toRiver(t, spl), 18, 3, 8, 0)).y = dyL + SPLENDID_TOP;
        th.add(new Thermal.Spring("Grotto Geyser", Thermal.Kind.VENT, gt[0], gt[1], 0, 0, 0, 92, 0, toRiver(t, gt), 26, 5, 12, 0)).y = gtL + GROTTO_TOP;
        th.add(new Thermal.Spring("Fan Geyser", Thermal.Kind.VENT, fan[0], fan[1], 0, 0, 0, 92, 0, toRiver(t, fan), 12, 3, 8, 0)).y = fanL + FAN_TOP;
        th.add(new Thermal.Spring("Mortar Geyser", Thermal.Kind.VENT, mo[0], mo[1], 0, 0, 0, 92, 0, toRiver(t, mo), 10, 2.5, 8, 0)).y = fanL + MORTAR_TOP;
        th.add(new Thermal.Spring("Turban Geyser", Thermal.Kind.VENT, tu[0], tu[1], 0, 0, 0, 92, 0, toRiver(t, tu), 10, 2, 6, 0)).y = -6 + TURBAN_TOP;
        th.add(new Thermal.Spring("Beehive's Indicator", Thermal.Kind.VENT, ind[0], ind[1], 0, 0, 0, 92, 0, toRiver(t, ind), 6, 1.5, 6, 0)).y = 4 + INDICATOR_TOP;
        for (Thermal.Spring sp : th.springs) if (sp.kind == Thermal.Kind.VENT) sp.mats = 0.5;
        // Fountain Paint Pot: ein großer Schlammtopf und kleinere daneben (Lage der kleinen genähert)
        th.add(new Thermal.Spring("Fountain Paint Pot", Thermal.Kind.MUD, fpp[0], fpp[1], 7, 5.5, 1.2, 88, 5, Math.PI, 3, 2.5, 8, 0.7)).y = LOWER_FLOOR + 0.05;
        th.add(new Thermal.Spring("Schlammtopf Nord", Thermal.Kind.MUD, fpp[0] + 17, fpp[1] - 11, 2.4, 2.0, 0.8, 86, 4, Math.PI, 1.5, 1.2, 6, 0.7)).y = LOWER_FLOOR + 0.05;
        th.add(new Thermal.Spring("Schlammtopf West", Thermal.Kind.MUD, fpp[0] - 15, fpp[1] + 12, 1.7, 1.4, 0.6, 87, 4, Math.PI, 1.5, 1.2, 6, 0.7)).y = LOWER_FLOOR + 0.05;
        th.add(new Thermal.Spring("Schlammtopf Süd", Thermal.Kind.MUD, fpp[0] + 9, fpp[1] + 16, 3.0, 2.3, 0.8, 85, 4, Math.PI, 1.5, 1.2, 6, 0.7)).y = LOWER_FLOOR + 0.05;
        // Schlammtöpfe haben kaum Abfluss: wenig Matten am Rand
        for (Thermal.Spring sp : th.springs) if (sp.kind == Thermal.Kind.MUD) sp.mats = 0.25;
        // Giantess bricht aus einem weiten Becken aus, wie Grand
        th.add(new Thermal.Spring("Giantess Geyser", Thermal.Kind.POOL, gi[0], gi[1], 4.6, 3.8, 8, 92, 4, toRiver(t, gi), 30, 5, 12, 0.8)).y = giL + 0.08;
        th.add(new Thermal.Spring("Excelsior Geyser Crater", Thermal.Kind.CRATER, exc[0], exc[1], 44, 41, 20, 93, 3, toRiver(t, exc), 55, 8, 22, 0.9)).y = -31;

        // ------------------------------------------------------------ Netz
        MeshBuilder mb = new MeshBuilder();
        mb.nearX0 = -1800; mb.nearX1 = 700; mb.nearZ0 = -8100; mb.nearZ1 = 700;
        grid(mb, t, t.fine, true);
        grid(mb, t, t.fine2, true);
        grid(mb, t, t.fine3, true);
        grid(mb, t, t.mid, true);
        grid(mb, t, t.far, false);
        river(mb, t);
        Firehole fh = new Firehole(t);
        fh.rocks(mb, t, new java.util.Random(1807));
        // Kegel und Hügel der Geysire
        formation(mb, of[0], 0, of[1], 13, 10.5, OF_TOP, 0.75, 0.55, 0.6, 0.12, 0, 0, 1.9, 11);
        formation(mb, bh[0], 4, bh[1], 0.95, 0.95, BEEHIVE_TOP, 0.2, 0.35, 0.08, 0.06, 0, 0, 1.6, 23);
        formation(mb, ca[0], -5, ca[1], 18, 18, 1.1, 0, 0, 0, 0.22, 0, 0, 1.3, 31);        // Sockel von Castle
        formation(mb, ca[0], -5 + 1.0, ca[1], 5.6, 5.0, CASTLE_TOP - 1.0, 0.9, 0.9, 0.35, 0.3, 9, 0.75, 1.1, 37);
        formation(mb, rs[0], rsLevel, rs[1], 3.2, 2.6, RIVERSIDE_TOP, 0.45, 0.5, 0.2, 0.18, 0, 0, 1.2, 41);
        formation(mb, dy[0], dyL, dy[1], 3.4, 2.8, DAISY_TOP, 0.4, 0.4, 0.15, 0.14, 0, 0, 1.4, 43);
        formation(mb, spl[0], dyL, spl[1], 4.5, 4.0, SPLENDID_TOP, 1.1, 0.4, 0.12, 0.08, 0, 0, 1.8, 47);
        // Grotto: niedriger Hügel mit hohen, knorrigen Sinterbuckeln (übersinterte Baumstämme)
        formation(mb, gt[0], gtL, gt[1], 6.5, 5.0, 1.0, 0, 0, 0, 0.25, 0, 0, 1.6, 53);
        formation(mb, gt[0], gtL + 0.8, gt[1], 3.4, 2.8, GROTTO_TOP - 0.8, 0.9, 1.0, 0.3, 0.35, 5, 1.1, 1.0, 59);
        formation(mb, fan[0], fanL, fan[1], 3.0, 2.4, FAN_TOP, 0.35, 0.3, 0.12, 0.2, 0, 0, 1.3, 61);
        formation(mb, mo[0], fanL, mo[1], 2.2, 2.0, MORTAR_TOP, 0.3, 0.4, 0.15, 0.2, 0, 0, 1.1, 67);
        formation(mb, tu[0], -6, tu[1], 1.8, 1.6, TURBAN_TOP, 0.25, 0.3, 0.1, 0.15, 0, 0, 1.2, 71);
        formation(mb, ind[0], 4, ind[1], 0.8, 0.7, INDICATOR_TOP, 0.12, 0.2, 0.05, 0.25, 0, 0, 1.1, 73);
        // Becken mit Geyseritrand und Wasser
        for (Thermal.Spring s : th.springs) {
            if (s.kind == Thermal.Kind.VENT) continue;
            if (s.kind == Thermal.Kind.POOL) rim(mb, s, s.ax > 30 ? 0.08 : 0.28, s.ax > 30 ? 4 : Math.max(0.6, s.ax * 0.35));
            if (s.kind == Thermal.Kind.MUD) rim(mb, s, 0.18, Math.max(0.8, s.ax * 0.3));
            water(mb, s);
        }
        // Wald, tote Stämme, Stege
        java.util.Random rnd = new java.util.Random(1872);
        Grove grove = new Grove();
        int[] counts = forest(mb, t, th, rnd, grove);
        Walks.build(mb, t, of, bh, ca, gr, rs, mg, gps, exc, fpp);
        Sward sward = new Sward(t, th);

        Scene sc = new Scene("Upper Geyser Basin", mb.build(64), t, th);
        grove.attach(sc.mesh);
        sc.trees = counts[0];
        sc.snags = counts[1];
        double[] tops = {OF_TOP + 1.5, 4 + BEEHIVE_TOP + 1.5, -5 + CASTLE_TOP + 1.5, -6 + 2.5, rsLevel + RIVERSIDE_TOP + 1.5, -15 + 2.5, -24 + 3,
                dyL + DAISY_TOP + 1.5, gtL + GROTTO_TOP + 1.5, fanL + FAN_TOP + 1.5, giL + 2.5, LOWER_FLOOR + 2.5};
        for (int i = 0; i < Sites.ALL.length; i++) {
            Sites.Site s = Sites.ALL[i];
            sc.markers.add(new Scene.Marker(s.name, s.line, s.x(), tops[i], s.z(), s.basin));
        }

        // ------------------------------------------------------------ Geysire
        Geysers gs = new Geysers(t, th);
        String nps = "NPS; Wikipedia";
        GeyserModel g;
        g = gs.add(new GeyserModel("Old Faithful", GeyserModel.Type.CONE, of[0], OF_TOP, of[1], 0.6, 22, 55,
                90, 150, 150, 300, 0.3, 65 * 60, 91 * 60, 10 * 60, 100, 0, 0, 0, 0, nps, 11));
        g = gs.add(new GeyserModel("Beehive Geyser", GeyserModel.Type.CONE, bh[0], 4 + BEEHIVE_TOP, bh[1], 0.22, 15, 61,
                0, 0, 250, 330, 0, 0, 16 * 3600, 8 * 3600, 150, 0, 0, 0, 0, nps, 23));
        g = gs.add(new GeyserModel("Castle Geyser", GeyserModel.Type.CONE, ca[0], -5 + CASTLE_TOP, ca[1], 0.5, 20, 27,
                0, 0, 18 * 60, 22 * 60, 0, 0, 16 * 3600, 3600, 35 * 60, 0, 0, 0, 0, nps, 31));
        g = gs.add(new GeyserModel("Grand Geyser", GeyserModel.Type.FOUNTAIN, gr[0], -6 + 0.1, gr[1], 3.8, 12, 61,
                0, 0, 9 * 60, 12 * 60, 0, 0, 6.5 * 3600, 0.5 * 3600, 60, 0, 0, 2, 4, nps, 37));
        double dirR = toRiver(t, rs);
        g = gs.add(new GeyserModel("Riverside Geyser", GeyserModel.Type.CONE, rs[0], rsLevel + RIVERSIDE_TOP, rs[1], 0.4, 15, 23,
                0, 0, 18 * 60, 22 * 60, 0, 0, 6 * 3600, 0.75 * 3600, 6 * 60, 28, dirR, 0, 0, nps, 41));
        double[] o = new double[6];
        t.nearest(rs[0], rs[1], o);
        g.floor = o[1];
        // Weitere Geysire (Kennzahlen: Wikipedia zu jedem Geysir; Neigungsrichtungen genähert)
        String wiki = "Wikipedia";
        gs.add(new GeyserModel("Daisy Geyser", GeyserModel.Type.CONE, dy[0], dyL + DAISY_TOP, dy[1], 0.35, 12, 23,
                0, 0, 180, 240, 0, 0, 150 * 60, 30 * 60, 60, 25, toRiver(t, dy) + 0.6, 0, 0, wiki, 43));
        g = gs.add(new GeyserModel("Splendid Geyser", GeyserModel.Type.FOUNTAIN, spl[0], dyL + SPLENDID_TOP, spl[1], 1.4, 15, 61,
                0, 0, 540, 720, 0, 0, 20 * 365.25 * 86400, 5 * 365.25 * 86400, 120, 0, 0, 2, 4, wiki, 47));
        gs.add(new GeyserModel("Grotto Geyser", GeyserModel.Type.CONE, gt[0], gtL + GROTTO_TOP, gt[1], 0.5, 10, 3,
                3600, 7200, 10 * 3600, 26 * 3600, 0.83, 6.5 * 3600, 36 * 3600, 3600, 1800, 0, 0, 0, 0, wiki, 53));
        g = gs.add(new GeyserModel("Fan Geyser", GeyserModel.Type.CONE, fan[0], fanL + FAN_TOP, fan[1], 0.5, 14, 38,
                0, 0, 25 * 60, 35 * 60, 0, 0, 5 * 86400, 2 * 86400, 600, 22, toRiver(t, fan), 0, 0, wiki, 61));
        g.spread = 3;
        g = gs.add(new GeyserModel("Mortar Geyser", GeyserModel.Type.CONE, mo[0], fanL + MORTAR_TOP, mo[1], 0.4, 12, 24,
                0, 0, 25 * 60, 35 * 60, 0, 0, 5 * 86400, 2 * 86400, 600, 8, toRiver(t, mo), 0, 0, wiki, 67));
        g.minor = true;
        g = gs.add(new GeyserModel("Giantess Geyser", GeyserModel.Type.FOUNTAIN, gi[0], giL + 0.1, gi[1], 3.0, 20, 61,
                0, 0, 4 * 3600, 48 * 3600, 0, 0, 91 * 86400, 45 * 86400, 3600, 0, 0, 2, 4, wiki, 79));
        g.burstsPerHour = 2;
        g = gs.add(new GeyserModel("Turban Geyser", GeyserModel.Type.CONE, tu[0], -6 + TURBAN_TOP, tu[1], 0.3, 6, 3,
                0, 0, 280, 320, 0, 0, 20 * 60, 5 * 60, 30, 0, 0, 0, 0, wiki, 71));
        g.minor = true;
        g.refill = 120;
        g = gs.add(new GeyserModel("Beehive's Indicator", GeyserModel.Type.CONE, ind[0], 4 + INDICATOR_TOP, ind[1], 0.12, 5, 7.6,
                0, 0, 60, 90, 0, 0, 16 * 3600, 8 * 3600, 20, 0, 0, 0, 0, wiki, 73));
        g.minor = true;
        gs.couplings.init();
        grove.fall.water = fh;
        return new World(sc, gs, grove, fh, sward);
    }

    private static double[] xz(int i) { return new double[]{Sites.ALL[i].x(), Sites.ALL[i].z()}; }

    /** Richtung (Bogenmaß) zum nächsten Punkt am Fluss: dorthin läuft das Wasser ab. */
    static double toRiver(Terrain t, double[] p) {
        double best = Double.MAX_VALUE, bx = 0, bz = 0;
        for (int i = 0; i < t.riverPoints(); i++) {
            double dx = t.riverX(i) - p[0], dz = t.riverZ(i) - p[1], d = dx * dx + dz * dz;
            if (d < best) { best = d; bx = dx; bz = dz; }
        }
        return Math.atan2(bz, bx);
    }

    // ------------------------------------------------------------ Gelände und Fluss

    /** Ein Höhengitter als Dreiecke, ohne die Löcher; mit skirt hängt am Außenrand ein Saum gegen Ritzen. */
    private static void grid(MeshBuilder mb, Terrain t, Terrain.Grid g, boolean skirt) {
        int w = g.nx + 1;
        float[] sky = skyOf(t, g);
        mb.skyFn = (x, y, z, nx, ny, nz) -> {
            int i = (int) Math.round((x - g.x0) / g.cell), j = (int) Math.round((z - g.z0) / g.cell);
            return sky[Math.max(0, Math.min(g.nz, j)) * w + Math.max(0, Math.min(g.nx, i))];
        };
        int[] id = new int[w * (g.nz + 1)];
        java.util.Arrays.fill(id, -1);
        for (int j = 0; j < g.nz; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (g.inHole(i, j)) continue;
                int a = node(mb, g, id, i, j), b = node(mb, g, id, i + 1, j), c = node(mb, g, id, i + 1, j + 1), d = node(mb, g, id, i, j + 1);
                mb.tri(a, b, c, Mat.TERRAIN);
                mb.tri(a, c, d, Mat.TERRAIN);
            }
        }
        // Säume an den Lochrändern: wo das feinere Gitter tiefer liegt, schließt dieser Saum den Spalt
        int keep0 = mb.group;
        mb.group = 1;
        for (int j = 0; j < g.nz; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (g.inHole(i, j)) continue;
                if (i + 1 < g.nx && g.inHole(i + 1, j)) holeSkirt(mb, g, id, i + 1, j, i + 1, j + 1);
                if (i > 0 && g.inHole(i - 1, j)) holeSkirt(mb, g, id, i, j, i, j + 1);
                if (j + 1 < g.nz && g.inHole(i, j + 1)) holeSkirt(mb, g, id, i, j + 1, i + 1, j + 1);
                if (j > 0 && g.inHole(i, j - 1)) holeSkirt(mb, g, id, i, j, i + 1, j);
            }
        }
        mb.group = keep0;
        if (!skirt) { mb.skyFn = null; return; }
        // Saum: am Außenrand senkrecht 12 m hinunter, von beiden Seiten sichtbar
        int keep = mb.group;
        mb.group = 1;
        for (int side = 0; side < 4; side++) {
            int n = side < 2 ? g.nx : g.nz;
            for (int k = 0; k < n; k++) {
                int i0, j0, i1, j1;
                switch (side) {
                    case 0: i0 = k; j0 = 0; i1 = k + 1; j1 = 0; break;
                    case 1: i0 = k; j0 = g.nz; i1 = k + 1; j1 = g.nz; break;
                    case 2: i0 = 0; j0 = k; i1 = 0; j1 = k + 1; break;
                    default: i0 = g.nx; j0 = k; i1 = g.nx; j1 = k + 1;
                }
                int a = node(mb, g, id, i0, j0), b = node(mb, g, id, i1, j1);
                int p = j0 * w + i0, q = j1 * w + i1;
                int c = mb.v(g.x0 + i1 * g.cell, g.h[q] - 12, g.z0 + j1 * g.cell, g.nxs[q], g.nys[q], g.nzs[q]);
                int d = mb.v(g.x0 + i0 * g.cell, g.h[p] - 12, g.z0 + j0 * g.cell, g.nxs[p], g.nys[p], g.nzs[p]);
                mb.tri(a, b, c, Mat.TERRAIN);
                mb.tri(a, c, d, Mat.TERRAIN);
            }
        }
        mb.group = keep;
        mb.skyFn = null;
    }

    /** Senkrechter Saum 12 m hinunter entlang der Kante (i0,j0)–(i1,j1). */
    private static void holeSkirt(MeshBuilder mb, Terrain.Grid g, int[] id, int i0, int j0, int i1, int j1) {
        int w = g.nx + 1;
        int a = node(mb, g, id, i0, j0), b = node(mb, g, id, i1, j1);
        int p = j0 * w + i0, q = j1 * w + i1;
        int c = mb.v(g.x0 + i1 * g.cell, g.h[q] - 12, g.z0 + j1 * g.cell, g.nxs[q], g.nys[q], g.nzs[q]);
        int d = mb.v(g.x0 + i0 * g.cell, g.h[p] - 12, g.z0 + j0 * g.cell, g.nxs[p], g.nys[p], g.nzs[p]);
        mb.tri(a, b, c, Mat.TERRAIN);
        mb.tri(a, c, d, Mat.TERRAIN);
    }

    private static int node(MeshBuilder mb, Terrain.Grid g, int[] id, int i, int j) {
        int p = j * (g.nx + 1) + i;
        if (id[p] < 0) id[p] = mb.v(g.x0 + i * g.cell, g.h[p], g.z0 + j * g.cell, g.nxs[p], g.nys[p], g.nzs[p]);
        return id[p];
    }

    /** Himmelssicht der Geländeknoten; das Raster wird parallel vorgerechnet. */
    private static float[] skyOf(Terrain t, Terrain.Grid g) {
        int w = g.nx + 1;
        float[] s = new float[w * (g.nz + 1)];
        java.util.stream.IntStream.range(0, g.nz + 1).parallel().forEach(j -> {
            for (int i = 0; i < w; i++) s[j * w + i] = t.skyView(g.x0 + i * g.cell, g.z0 + j * g.cell, g.h[j * w + i]);
        });
        return s;
    }

    /** Der Fluss als Wasserband auf Höhe des Wasserspiegels; wo der Boden höher liegt, hebt sich das Band. */
    private static void river(MeshBuilder mb, Terrain t) {
        int n = t.riverPoints();
        int[] prev = null;
        for (int i = 0; i < n; i++) {
            double x = t.riverX(i), z = t.riverZ(i);
            if (x < t.mid.x0 + 10 || x > t.mid.x1() - 10 || z < t.mid.z0 + 10 || z > t.mid.z1() - 10) { prev = null; continue; }
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double dx = t.riverX(b) - t.riverX(a), dz = t.riverZ(b) - t.riverZ(a);
            double l = Math.hypot(dx, dz);
            double px = -dz / l, pz = dx / l;
            double half = t.riverHalf(i) * 1.25;
            double y = t.riverLevel(i);
            double lift = Math.max(0, t.sample(x, z) + 0.3 - y);
            y += lift;
            int[] cur = {
                    mb.v(x + px * half, y, z + pz * half, 0, 1, 0),
                    mb.v(x, y, z, 0, 1, 0),
                    mb.v(x - px * half, y, z - pz * half, 0, 1, 0)};
            if (prev != null) {
                for (int k = 0; k < 2; k++) {
                    mb.tri(prev[k], cur[k], cur[k + 1], Mat.RIVER);
                    mb.tri(prev[k], cur[k + 1], prev[k + 1], Mat.RIVER);
                }
            }
            prev = cur;
        }
    }

    // ------------------------------------------------------------ Kegel

    /** Fläche aus einer Punktfunktion; Normalen aus den Ableitungen. */
    interface PosFn { void pos(double u, double v, double[] p); }

    static void surface(MeshBuilder mb, PosFn f, double u0, double u1, int nu, double v0, double v1, int nv, int m) {
        double[] p = new double[3], a = new double[3], b = new double[3];
        int[] g = new int[(nu + 1) * (nv + 1)];
        double du = (u1 - u0) * 1e-3, dv = (v1 - v0) * 1e-3;
        for (int j = 0; j <= nv; j++) {
            double v = v0 + (v1 - v0) * j / nv;
            for (int i = 0; i <= nu; i++) {
                double u = u0 + (u1 - u0) * i / nu;
                f.pos(u + du, v, a); f.pos(u - du, v, b);
                double ux = a[0] - b[0], uy = a[1] - b[1], uz = a[2] - b[2];
                f.pos(u, v + dv, a); f.pos(u, v - dv, b);
                double vx = a[0] - b[0], vy = a[1] - b[1], vz = a[2] - b[2];
                double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                if (ny < 0) { nx = -nx; ny = -ny; nz = -nz; }
                f.pos(u, v, p);
                g[j * (nu + 1) + i] = mb.v(p[0], p[1], p[2], nx, ny, nz);
            }
        }
        for (int j = 0; j < nv; j++) {
            for (int i = 0; i < nu; i++) {
                int q = j * (nu + 1) + i;
                int a0 = g[q], b0 = g[q + 1], c0 = g[q + nu + 2], d0 = g[q + nu + 1];
                mb.tri(a0, c0, b0, m);
                mb.tri(a0, d0, c0, m);
            }
        }
    }

    /**
     * Sinterkegel oder Hügel um (cx, cz) auf Höhe base: Halbachsen rx, rz, Höhe h, Krater mit Radius cr,
     * Kratertiefe cd und Wall rh, Unebenheit bump, Zinnen (Anzahl, Höhe) wie bei Castle, Form p
     * (1 = Kegel, 2 = Kuppel), seed. Der Fuß reicht 0,3 m in den Boden.
     */
    static void formation(MeshBuilder mb, double cx, double base, double cz, double rx, double rz, double h, double cr, double cd,
                          double rh, double bump, int crenels, double crenH, double p, int seed) {
        double R = Math.max(rx, rz);
        PosFn f = (u, v, out) -> {
            // v: 0 Mitte, 1 Fuß; u: Winkel. Ringe zur Mitte hin dichter (für Krater und Zinnen)
            v = Math.pow(Math.max(0, v), 1.6);
            double r = v * R;
            double cu = Math.cos(u), su = Math.sin(u);
            double wob = 1 + 0.12 * (Noise.fbm((float) (cu * 1.3 + seed), (float) (su * 1.3), 0.5f, 3) - 0.5) * 2;
            double k = Math.min(1, v);
            double prof = Math.pow(Math.max(0, 1 - Math.pow(k, p)), 1.1);
            double y = h * prof - 0.3 * smooth(0.85, 1, v);
            if (cr > 0) {
                double rr = r / Math.max(0.05, cr);
                y += rh * Math.exp(-(rr - 1) * (rr - 1) / 0.18);
                if (rr < 1) y -= cd * (1 - rr * rr);
            }
            if (crenels > 0) {
                double sq = Math.sin(u * crenels + seed);
                double c = smooth(-0.2, 0.35, sq);
                y += crenH * c * smooth(0.55, 0.2, v) * (0.7 + 0.6 * Noise.fbm((float) (u * 3), 1.1f, (float) seed, 2));
            }
            y += bump * h * (Noise.fbm((float) (cx * 0.1 + cu * v * 3), (float) (cz * 0.1 + su * v * 3), (float) (seed + v * 2), 3) - 0.5) * smooth(0.02, 0.2, v);
            out[0] = cx + cu * v * rx * wob;
            out[1] = base + y;
            out[2] = cz + su * v * rz * wob;
        };
        int nu = Math.max(24, (int) (R * 6)), nv = Math.max(10, (int) (R * 2.2));
        nu = Math.min(nu, 96);
        nv = Math.min(nv, 36);
        surface(mb, f, 0, 2 * Math.PI, nu, 0, 1.08, nv, Mat.CONE);
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------ Becken

    /** Geyseritrand um ein Becken: innen am Wasser, Wulst der Höhe lip, außen in den Boden, Breite w. */
    static void rim(MeshBuilder mb, Thermal.Spring s, double lip, double w) {
        PosFn f = (u, v, out) -> {
            double cu = Math.cos(u), su = Math.sin(u);
            double scal = 1 + 0.05 * Math.sin(u * 23 + s.x) + 0.04 * (Noise.fbm((float) (cu * 2 + s.x * 0.01), (float) (su * 2), 3.3f, 2) - 0.5);
            double r0x = s.ax * scal, r0z = s.az * scal;
            double off = v * w;
            double y = s.y - 0.1 + (lip + 0.1) * Math.pow(Math.max(0, Math.sin(Math.PI * Math.max(0, Math.min(1, v * 1.4)))), 0.8) - 0.35 * smooth(0.7, 1, v);
            out[0] = s.x + cu * (r0x + off);
            out[1] = y;
            out[2] = s.z + su * (r0z + off);
        };
        int nu = (int) Math.min(220, Math.max(28, Math.max(s.ax, s.az) * 7));
        surface(mb, f, 0, 2 * Math.PI, nu, 0, 1, 4, Mat.RIM);
    }

    /** Wasserspiegel als Scheibe mit Ringen, etwas unter den Rand geschoben. */
    static void water(MeshBuilder mb, Thermal.Spring s) {
        int nu = (int) Math.min(160, Math.max(20, Math.max(s.ax, s.az) * 3));
        int rings = (int) Math.min(24, Math.max(3, Math.max(s.ax, s.az) / 3));
        double ext = s.kind == Thermal.Kind.CRATER ? 1.06 : 1.01;
        int c = mb.v(s.x, s.y, s.z, 0, 1, 0);
        int[] prev = null;
        for (int r = 1; r <= rings; r++) {
            double f = ext * r / (double) rings;
            int[] cur = new int[nu];
            for (int i = 0; i < nu; i++) {
                double a = 2 * Math.PI * i / nu;
                cur[i] = mb.v(s.x + Math.cos(a) * s.ax * f, s.y, s.z + Math.sin(a) * s.az * f, 0, 1, 0);
            }
            for (int i = 0; i < nu; i++) {
                int j = (i + 1) % nu;
                if (prev == null) mb.tri(c, cur[j], cur[i], Mat.POOL);
                else { mb.tri(prev[i], prev[j], cur[j], Mat.POOL); mb.tri(prev[i], cur[j], cur[i], Mat.POOL); }
            }
            prev = cur;
        }
    }

    // ------------------------------------------------------------ Wald

    /**
     * Drehkiefern entlang der Waldränder (bis 280 m in den Wald, dahinter trägt die Bodenfarbe),
     * am Rand Espenhaine, tote Stämme am Sinterrand. Die Bäume setzt {@link Grove}. Liefert die Anzahl
     * Bäume und Stämme.
     */
    static int[] forest(MeshBuilder mb, Terrain t, Thermal th, java.util.Random rnd, Grove grove) {
        double[][] sites = new double[Sites.ALL.length][];
        for (int i = 0; i < sites.length; i++) sites[i] = new double[]{Sites.ALL[i].x(), Sites.ALL[i].z()};
        double keepEdge = mb.maxEdge;
        mb.maxEdge = 1e9;
        float[] gm = new float[5];
        int trees = 0, snags = 0;
        double[][] boxes = {{t.fine.x0 + 20, t.fine.x1() - 20, t.fine.z0 + 20, t.fine.z1() - 20}, {t.fine2.x0 + 20, t.fine2.x1() - 20, t.fine2.z0 + 20, t.fine2.z1() - 20},
                {t.fine3.x0 + 20, t.fine3.x1() - 20, t.fine3.z0 + 20, t.fine3.z1() - 20}};
        double step = 10.5;
        for (double[] bx : boxes) {
            for (double z = bx[2]; z < bx[3]; z += step) {
                for (double x = bx[0]; x < bx[1]; x += step) {
                    double px = x + (rnd.nextDouble() - 0.5) * step * 0.9, pz = z + (rnd.nextDouble() - 0.5) * step * 0.9;
                    t.ground((float) px, (float) pz, gm);
                    float fo = gm[4], sn = gm[3], bank = gm[0];
                    if (th.tempExact(px, pz) > Thermal.ambient + 4) continue;
                    if (fo > 0.5f && sn < 0.08f && bank > 12) {
                        float e = t.forestEdge(px, pz);
                        if (e < 0 || e > 280) continue;
                        if (rnd.nextDouble() > 0.9 - e / 400.0) continue;
                        double y = t.sample(px, pz);
                        // Espenhaine: Flecken am Waldrand, wo es feuchter ist (in der Natur je ein Klon)
                        float hain = com.dan.forest.Noise2.value((float) (px * 0.011 + 40), (float) (pz * 0.011 + 17));
                        double ds = 1e9;
                        for (double[] q : sites) ds = Math.min(ds, Math.hypot(px - q[0], pz - q[1]));
                        if (hain > 0.74f && e < 35 && ds < 450 && rnd.nextDouble() < 0.85) grove.aspen(mb, px, y, pz, rnd, ds < 200 ? 0 : ds < 330 ? 1 : 2);
                        else grove.pine(mb, px, y, pz, rnd, e < 25 && ds < 200);
                        trees++;
                    } else if (sn > 0.12f && sn < 0.55f && fo < 0.5f && bank > 8 && rnd.nextDouble() < 0.035) {
                        snag(mb, px, t.sample(px, pz), pz, rnd);
                        snags++;
                    }
                }
            }
        }
        mb.maxEdge = keepEdge;
        mb.swayFn = null;
        mb.swayValue = 0;
        return new int[]{trees, snags};
    }

    /** Tote Kiefer: grauer Stamm, oben abgebrochen, ein paar Aststummel; unten weiß („Bobby Socks“). */
    static void snag(MeshBuilder mb, double x, double y, double z, java.util.Random rnd) {
        double H = 5 + 9 * rnd.nextDouble();
        double lean = rnd.nextDouble() * 0.12, dir = rnd.nextDouble() * 6.28;
        double tx = x + Math.cos(dir) * lean * H, tz = z + Math.sin(dir) * lean * H;
        mb.swayFn = null;
        mb.swayValue = 0;
        double r = 0.14 + 0.01 * H;
        prism(mb, x, y - 0.3, z, tx, y + H, tz, r, r * 0.45, 5, Mat.SNAG, rnd.nextDouble());
        int stubs = 2 + rnd.nextInt(4);
        for (int k = 0; k < stubs; k++) {
            double f = 0.4 + 0.5 * rnd.nextDouble(), a = rnd.nextDouble() * 6.28, len = 0.6 + 1.2 * rnd.nextDouble();
            double sx = x + (tx - x) * f, sy = y + H * f, sz = z + (tz - z) * f;
            prism(mb, sx, sy, sz, sx + Math.cos(a) * len, sy + len * 0.35, sz + Math.sin(a) * len, 0.05, 0.02, 3, Mat.SNAG, 0);
        }
    }

    /** Schräges Prisma von a nach b mit Radien ra, rb und n Seiten (ohne Deckel). */
    static void prism(MeshBuilder mb, double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, int n, int m, double rot) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= l; dy /= l; dz /= l;
        double ux = 1, uy = 0, uz = 0;
        if (Math.abs(dx) > 0.9) { ux = 0; uz = 1; }
        double px = uy * dz - uz * dy, py = uz * dx - ux * dz, pz = ux * dy - uy * dx;
        double pl = Math.sqrt(px * px + py * py + pz * pz);
        px /= pl; py /= pl; pz /= pl;
        double qx = dy * pz - dz * py, qy = dz * px - dx * pz, qz = dx * py - dy * px;
        int[] lo = new int[n], hi = new int[n];
        for (int i = 0; i < n; i++) {
            double a = rot + 2 * Math.PI * i / n, c = Math.cos(a), s = Math.sin(a);
            double nx = px * c + qx * s, ny = py * c + qy * s, nz = pz * c + qz * s;
            lo[i] = mb.v(ax + nx * ra, ay + ny * ra, az + nz * ra, nx, ny, nz);
            hi[i] = mb.v(bx + nx * rb, by + ny * rb, bz + nz * rb, nx, ny, nz);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            mb.tri(lo[i], lo[j], hi[j], m);
            mb.tri(lo[i], hi[j], hi[i], m);
        }
    }
}
