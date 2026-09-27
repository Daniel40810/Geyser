package com.dan.geyser.atom;

import com.dan.geyser.db.DbConfig;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die Elemente der sechs Ablagerungen. Mit Datenbank liest die Lupe sie aus der öffentlichen View
 * <code>am_element</code> des Schemas ATOMMODEL (nur lesend, kein eigenes Objekt nötig); ohne
 * Datenbank gilt die eingebaute Tabelle mit den Standardwerten (mittlere Atommasse nach IUPAC,
 * häufigstes Isotop, Elektronegativität nach Pauling).
 */
public final class Elements {
    private Elements() { }

    /** Ordnungszahlen, die die Lupe braucht: H, C, O, Al, Si, S, K, Ca, Fe, As, Sb. */
    public static final int[] NEEDED = {1, 6, 8, 13, 14, 16, 19, 20, 26, 33, 51};

    /** Eingebaute Werte als Rückfall. */
    public static final Map<Integer, Element> BUILTIN = new LinkedHashMap<>();

    static {
        put(1, "H", "Wasserstoff", 1.008, 0, "1", "1s1", 2.20, "Nichtmetall", new Color(236, 240, 246));
        put(6, "C", "Kohlenstoff", 12.011, 6, "2-4", "[He] 2s2 2p2", 2.55, "Nichtmetall", new Color(120, 124, 132));
        put(8, "O", "Sauerstoff", 15.999, 8, "2-6", "[He] 2s2 2p4", 3.44, "Nichtmetall", new Color(226, 76, 64));
        put(13, "Al", "Aluminium", 26.982, 14, "2-8-3", "[Ne] 3s2 3p1", 1.61, "Metall", new Color(196, 184, 196));
        put(14, "Si", "Silicium", 28.085, 14, "2-8-4", "[Ne] 3s2 3p2", 1.90, "Halbmetall", new Color(214, 188, 132));
        put(16, "S", "Schwefel", 32.06, 16, "2-8-6", "[Ne] 3s2 3p4", 2.58, "Nichtmetall", new Color(236, 206, 60));
        put(19, "K", "Kalium", 39.098, 20, "2-8-8-1", "[Ar] 4s1", 0.82, "Alkalimetall", new Color(162, 112, 214));
        put(20, "Ca", "Calcium", 40.078, 20, "2-8-8-2", "[Ar] 4s2", 1.00, "Erdalkalimetall", new Color(150, 196, 150));
        put(26, "Fe", "Eisen", 55.845, 30, "2-8-14-2", "[Ar] 3d6 4s2", 1.83, "Übergangsmetall", new Color(212, 120, 58));
        put(33, "As", "Arsen", 74.922, 42, "2-8-18-5", "[Ar] 3d10 4s2 4p3", 2.18, "Halbmetall", new Color(170, 118, 196));
        put(51, "Sb", "Antimon", 121.760, 70, "2-8-18-18-5", "[Kr] 4d10 5s2 5p3", 2.05, "Halbmetall", new Color(150, 162, 178));
    }

    private static void put(int z, String sym, String name, double mass, int n, String shells, String conf, double en, String cat, Color c) {
        BUILTIN.put(z, new Element(z, sym, name, mass, n, shells, conf, en, cat, c, false));
    }

    /** Ergebnis des Ladens: die Elemente und woher sie stammen. */
    public static final class Result {
        public final Map<Integer, Element> map;
        public final String source;
        public final boolean db;

        Result(Map<Integer, Element> map, String source, boolean db) { this.map = map; this.source = source; this.db = db; }
    }

    /**
     * Versucht die Datenbank; fehlt ein Element dort oder gibt es keine Verbindung, nimmt es den
     * eingebauten Wert. Blockiert höchstens einige Sekunden, also nicht auf dem Swing-Thread rufen.
     */
    public static Result load() {
        DbConfig cfg = DbConfig.load();
        if (cfg == null) return new Result(BUILTIN, "eingebaut · " + DbConfig.status(), false);
        StringBuilder in = new StringBuilder();
        for (int z : NEEDED) { if (in.length() > 0) in.append(','); in.append(z); }
        String sql = "SELECT atomic_number, symbol, name_de, atomic_mass, neutrons, shell_config, electron_config, "
                + "electronegativity, category_de, color_r, color_g, color_b FROM am_element WHERE atomic_number IN (" + in + ")";
        Map<Integer, Element> m = new LinkedHashMap<>(BUILTIN);
        int n = 0;
        try (Connection c = cfg.connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int z = rs.getInt(1);
                java.math.BigDecimal en = rs.getBigDecimal(8), r = rs.getBigDecimal(10), g = rs.getBigDecimal(11), b = rs.getBigDecimal(12);
                Color col = r == null || g == null || b == null ? BUILTIN.get(z).color : new Color(r.intValue(), g.intValue(), b.intValue());
                m.put(z, new Element(z, rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getInt(5), rs.getString(6), rs.getString(7),
                        en == null ? null : en.doubleValue(), rs.getString(9), col, true));
                n++;
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().split("\n")[0];
            DbConfig.status("keine Verbindung: " + msg);
            return new Result(BUILTIN, "eingebaut · " + DbConfig.status(), false);
        }
        return new Result(m, "ATOMMODEL · am_element (" + n + " von " + NEEDED.length + " Elementen) · " + DbConfig.status(), n > 0);
    }
}
