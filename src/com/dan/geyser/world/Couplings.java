package com.dan.geyser.world;

import com.dan.geyser.core.Thermal;

/**
 * Geysire, die einander auslösen. Im Upper Geyser Basin hängen viele Röhren unter der Erde
 * zusammen; die bekanntesten Paare sind hier nachgebildet:
 * <ul>
 * <li><b>Turban und Grand.</b> In den Stunden vor Grand bricht Turban alle 15 bis 25 Minuten für
 * fünf Minuten aus. Grand beginnt ein bis zwei Minuten nach dem Beginn eines Turban-Ausbruchs; während
 * Grand spielt Turban ununterbrochen, bis 6 m hoch (Wikipedia: Grand Geyser, Turban Geyser).</li>
 * <li><b>Beehive's Indicator und Beehive.</b> Der kleine Kegel 3 m neben Beehive spritzt 4,6 bis
 * 7,6 m hoch, einige Sekunden bis 30 Minuten vor Beehive, im Mittel gut 13 Minuten (Wikipedia; die
 * Ranger rechnen mit 17).</li>
 * <li><b>Fan und Mortar.</b> Sie brechen seit Jahrzehnten gemeinsam aus.</li>
 * <li><b>Splendid und Daisy.</b> Ist Daisy aktiv, schläft Splendid meist (zuletzt 1998). Ein
 * Ausbruch von Splendid verschiebt Daisys Abstand; hier wirft er Daisys Zyklus um anderthalb
 * Abstände zurück (die Richtung ist ein Modell).</li>
 * <li><b>Giantess.</b> Während ihrer seltenen, stundenlangen Ausbrüche reagieren die Quellen auf
 * Geyser Hill; Doublet Pool brach nur viermal aus, zweimal mit Giantess. Hier wallt Doublet Pool,
 * solange Giantess spielt.</li>
 * </ul>
 */
public final class Couplings {
    private final Geysers gs;
    private GeyserModel grand, turban, beehive, indicator, fan, mortar, daisy, splendid, giantess;
    private Thermal.Spring doublet;
    private double doubletT0 = Double.NaN;
    private final java.util.Map<GeyserModel, GeyserModel.Phase> last = new java.util.HashMap<>();
    private final java.util.Random rnd = new java.util.Random(1878);

    Couplings(Geysers gs) { this.gs = gs; }

    /** Die Paare einsetzen, nachdem alle Geysire da sind. */
    void init() {
        grand = gs.byName("Grand Geyser");
        turban = gs.byName("Turban Geyser");
        beehive = gs.byName("Beehive Geyser");
        indicator = gs.byName("Beehive's Indicator");
        fan = gs.byName("Fan Geyser");
        mortar = gs.byName("Mortar Geyser");
        daisy = gs.byName("Daisy Geyser");
        splendid = gs.byName("Splendid Geyser");
        giantess = gs.byName("Giantess Geyser");
        doublet = gs.thermal().byName("Doublet Pool");
        if (doublet != null) doubletT0 = doublet.t0;
        if (grand != null && turban != null) { grand.gated = true; grand.holdMax = 45 * 60; grand.waitsFor = "Turban"; }
        if (beehive != null && indicator != null) {
            beehive.gated = true; beehive.holdMax = 60 * 60; beehive.waitsFor = "Indicator";
            indicator.gated = true; indicator.holdMax = Double.MAX_VALUE;
        }
        if (mortar != null && fan != null) { mortar.gated = true; mortar.holdMax = Double.MAX_VALUE; mortar.waitsFor = "Fan"; }
    }

    /** Hat g im letzten Schritt einen Ausbruch begonnen (oder beendet)? */
    private boolean began(GeyserModel g) { return g != null && g.phase == GeyserModel.Phase.ERUPTION && last.get(g) != GeyserModel.Phase.ERUPTION; }

    private boolean ended(GeyserModel g) { return g != null && g.phase != GeyserModel.Phase.ERUPTION && last.get(g) == GeyserModel.Phase.ERUPTION; }

    /** Nach jedem Teilschritt der Modelle. */
    void step(double now) {
        if (grand != null && turban != null) {
            // Grand wartet am Siedepunkt, bis Turban beginnt, und bricht 0 bis 2 Minuten danach aus
            if (began(turban) && grand.ready && !grand.pending()) grand.release(now, rnd.nextDouble() * 120);
            // Während Grand spielt Turban ununterbrochen, bis doppelt so hoch
            if (began(grand)) turban.forceEruption(now, grand.duration, 2.0);
        }
        if (beehive != null && indicator != null) {
            // Ist Beehive bereit, spielt der Indicator; Beehive folgt nach im Mittel gut 13 Minuten
            if (beehive.ready && !beehive.pending() && beehive.phase == GeyserModel.Phase.PREPLAY) {
                double lead = Math.max(20, Math.min(1800, 13.3 * 60 + rnd.nextGaussian() * 6 * 60));
                beehive.release(now, lead);
                indicator.forceEruption(now, lead + 60, 0.8 + 0.2 * rnd.nextDouble());
            }
        }
        if (fan != null && mortar != null && began(fan)) mortar.forceEruption(now, fan.duration, 1);
        if (splendid != null && daisy != null && began(splendid) && daisy.phase != GeyserModel.Phase.ERUPTION) {
            daisy.startIn(now, splendid.duration + 1.5 * daisy.intLong);
        }
        if (giantess != null && doublet != null) {
            if (began(giantess)) { doublet.t0 = 93.5; gs.thermal().changed(doublet); }
            if (ended(giantess)) { doublet.t0 = doubletT0; gs.thermal().changed(doublet); }
        }
        for (GeyserModel g : gs.list) last.put(g, g.phase);
    }
}
