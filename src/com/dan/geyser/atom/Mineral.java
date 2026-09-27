package com.dan.geyser.atom;

import java.awt.Color;
import java.util.Map;

/**
 * Eine Ablagerung der heißen Quellen: Name, Formel, Zusammensetzung (Atome je Formeleinheit),
 * wo sie vorkommt, Text, belegte Kennzahlen und Quellen. {@link #local} ist true, wenn sie in den
 * beiden Becken der Szene (Upper Geyser Basin, Midway) liegt; die übrigen zeigt die Lupe zum
 * Vergleich mit dem Ort im Park, an dem sie vorkommen.
 */
public final class Mineral {
    public final String code, name, formula, kind, place, text;
    public final boolean local;
    /** Atome je Formeleinheit: Ordnungszahl → Anzahl (auch gebrochen, etwa beim Wasser im Opal). */
    public final int[] z;
    public final double[] count;
    /** Kennzahlen als Zeilen, ihr Beleg je Zeile (Code aus {@link com.dan.geyser.db.Belege}), Quellen als "Titel|URL". */
    public final String[] facts, factSources, sources;
    public final Color swatch;
    /** Spurenelemente, die die Lupe mit nennt (Ordnungszahlen), oder leer. */
    public final int[] traces;

    /**
     * facts: Zeilen "BELEG|Text" (Code aus {@link com.dan.geyser.db.Belege#SOURCES}); die Quellen
     * ergeben sich daraus, jede einmal in der Reihenfolge des ersten Auftretens.
     */
    public Mineral(String code, String name, String formula, String kind, boolean local, String place, Color swatch,
            int[] z, double[] count, int[] traces, String text, String[] facts) {
        this.code = code; this.name = name; this.formula = formula; this.kind = kind; this.local = local; this.place = place;
        this.swatch = swatch; this.z = z; this.count = count; this.traces = traces; this.text = text;
        this.facts = new String[facts.length];
        this.factSources = new String[facts.length];
        java.util.LinkedHashSet<String> src = new java.util.LinkedHashSet<>();
        for (int i = 0; i < facts.length; i++) {
            int k = facts[i].indexOf('|');
            factSources[i] = facts[i].substring(0, k);
            this.facts[i] = facts[i].substring(k + 1);
            src.add(factSources[i]);
        }
        this.sources = new String[src.size()];
        int i = 0;
        for (String c : src) this.sources[i++] = com.dan.geyser.db.Belege.link(c);
    }

    /** Masse einer Formeleinheit (u). */
    public double formulaMass(Map<Integer, Element> el) {
        double m = 0;
        for (int i = 0; i < z.length; i++) m += count[i] * el.get(z[i]).mass;
        return m;
    }

    /** Massenanteil des i-ten Elements 0..1. */
    public double massShare(int i, Map<Integer, Element> el) {
        return count[i] * el.get(z[i]).mass / formulaMass(el);
    }
}
