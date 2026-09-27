package com.dan.geyser.atom;

import java.awt.Color;

/**
 * Ein chemisches Element, wie die Mineral-Lupe es braucht: Ordnungszahl, Symbol, Name, mittlere
 * Atommasse, Neutronen des häufigsten Isotops, Elektronen je Schale (K, L, M, …), Konfiguration,
 * Elektronegativität nach Pauling, Gruppe und Farbe. Kommt aus der öffentlichen View
 * <code>am_element</code> des ATOMMODEL oder, ohne Datenbank, aus {@link Elements#BUILTIN}.
 */
public final class Element {
    public final int z, neutrons;
    public final String symbol, name, config, category;
    public final double mass;
    public final Double en;
    public final int[] shells;
    public final Color color;
    /** true, wenn die Werte aus der Datenbank stammen. */
    public final boolean fromDb;

    public Element(int z, String symbol, String name, double mass, int neutrons, String shellConfig, String config,
                   Double en, String category, Color color, boolean fromDb) {
        this.z = z; this.symbol = symbol; this.name = name; this.mass = mass; this.neutrons = neutrons;
        this.shells = parseShells(shellConfig); this.config = config; this.en = en; this.category = category;
        this.color = color; this.fromDb = fromDb;
    }

    /** "2-8-4" → {2, 8, 4}. */
    static int[] parseShells(String s) {
        if (s == null || s.trim().isEmpty()) return new int[0];
        String[] p = s.trim().split("[-,\\s]+");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i]);
        return r;
    }

    public String shellText() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < shells.length; i++) { if (i > 0) b.append('-'); b.append(shells[i]); }
        return b.toString();
    }
}
