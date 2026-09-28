package com.dan.geyser.db;

import com.dan.geyser.atom.Mineral;
import com.dan.geyser.atom.Minerals;
import com.dan.geyser.core.Thermal;
import com.dan.geyser.effects.Climate;
import com.dan.geyser.world.Basin;
import com.dan.geyser.world.GeyserModel;
import com.dan.geyser.world.Sites;
import com.dan.geyser.world.World;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Erzeugt <code>db/sql/03_inhalte.sql</code> aus dem Code: Belege, Kennzahlen, Stellen, Kennwerte der
 * Geysire (aus dem gebauten Becken), Quellen, Mineralien mit Elementen und die Klimanormalwerte. So
 * stehen in der Datenbank genau die Werte, mit denen die App ohne Datenbank rechnet; der Einrichter
 * vergleicht beides. Aufruf: Rechtsklick, Run File (Arbeitsordner = Projektordner).
 */
public final class Inhalte {
    private Inhalte() { }

    /** Codes der Quellen im Temperaturfeld, nach Namen. */
    static String springCode(String name) {
        switch (name) {
            case "Old Faithful": return "OLD_FAITHFUL";
            case "Beehive Geyser": return "BEEHIVE";
            case "Castle Geyser": return "CASTLE";
            case "Crested Pool": return "CRESTED";
            case "Grand Geyser": return "GRAND";
            case "Riverside Geyser": return "RIVERSIDE";
            case "Morning Glory Pool": return "MORNING_GLORY";
            case "Doublet Pool": return "DOUBLET";
            case "Heart Spring": return "HEART";
            case "Grand Prismatic Spring": return "GRAND_PRISMATIC";
            case "Excelsior Geyser Crater": return "EXCELSIOR";
            case "Daisy Geyser": return "DAISY";
            case "Splendid Geyser": return "SPLENDID";
            case "Grotto Geyser": return "GROTTO";
            case "Fan Geyser": return "FAN";
            case "Mortar Geyser": return "MORTAR";
            case "Turban Geyser": return "TURBAN";
            case "Beehive's Indicator": return "BEEHIVE_INDICATOR";
            case "Fountain Paint Pot": return "FOUNTAIN_PAINT_POT";
            case "Giantess Geyser": return "GIANTESS";
            default: return name.toUpperCase(Locale.ROOT).replace("Ä", "AE").replace("Ö", "OE").replace("Ü", "UE").replace("ß", "SS")
                    .replaceAll("[^A-Z0-9]+", "_");
        }
    }

    /** Quellen, deren Lage im Code nur genähert ist (nicht aus Koordinaten). */
    static boolean approx(String code) {
        return code.equals("CRESTED") || code.equals("DOUBLET") || code.equals("HEART") || code.equals("MORTAR") || code.equals("TURBAN")
                || code.equals("BEEHIVE_INDICATOR") || code.startsWith("SCHLAMMTOPF");
    }

    static String q(String s) { return s == null ? "NULL" : "'" + s.replace("'", "''") + "'"; }

    static String n(double v) {
        if (Double.isNaN(v)) return "NULL";
        String t = String.format(Locale.ROOT, "%.7f", v);
        t = t.contains(".") ? t.replaceAll("0+$", "").replaceAll("\\.$", "") : t;
        return t;
    }

    static String src(String code) { return "(SELECT source_id FROM gey_source WHERE code = " + q(code) + ")"; }

    static String point(double lat, double lon) {
        return "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(" + n(lon) + ", " + n(lat) + ", NULL), NULL, NULL)";
    }

    public static void main(String[] a) throws Exception {
        World w = Basin.build();
        File out = new File("db/sql/03_inhalte.sql");
        out.getParentFile().mkdirs();
        try (PrintWriter p = new PrintWriter(out, StandardCharsets.UTF_8)) {
            write(p, w);
        }
        System.out.println("Geschrieben: " + out.getAbsolutePath());
    }

    static void write(PrintWriter p, World w) {
        p.println("-- =====================================================================");
        p.println("-- Geyser · Phase 9 · 03 Inhalte");
        p.println("-- Erzeugt aus dem Code von com.dan.geyser.db.Inhalte, nicht von Hand ändern.");
        p.println("-- Belege, Kennzahlen, Stellen, Geysire, Quellen, Mineralien, Klima.");
        p.println("-- =====================================================================");
        p.println();
        p.println("-- ------------------------------------------------------------- Belege");
        for (Belege.Source s : Belege.SOURCES.values()) {
            stmt(p, "INSERT INTO gey_source (code, titel, herausgeber, url, art, abgerufen) VALUES ("
                    + q(s.code) + ", " + q(s.title) + ", " + q(s.publisher) + ", " + q(s.url) + ", " + q(s.kind)
                    + ", DATE '" + s.accessed + "')");
        }
        p.println("-- ------------------------------------------------------------- Kennzahlen");
        int ord = 0;
        for (Belege.Fact f : Belege.FACTS) {
            stmt(p, "INSERT INTO gey_fact (thema, bezug, text, wert, einheit, reihenfolge, source_id) VALUES ("
                    + q(f.topic) + ", " + q(f.subject) + ", " + q(f.text) + ", " + (f.value == null ? "NULL" : n(f.value)) + ", "
                    + q(f.unit) + ", " + (++ord) + ", " + src(f.source) + ")");
        }
        for (Mineral m : Minerals.ALL) {
            for (int i = 0; i < m.facts.length; i++) {
                stmt(p, "INSERT INTO gey_fact (thema, bezug, text, wert, einheit, reihenfolge, source_id) VALUES ('MINERAL', "
                        + q(m.code) + ", " + q(m.facts[i]) + ", NULL, NULL, " + (i + 1) + ", " + src(m.factSources[i]) + ")");
            }
        }
        p.println("-- ------------------------------------------------------------- Stellen");
        for (int i = 0; i < Sites.ALL.length; i++) {
            Sites.Site s = Sites.ALL[i];
            stmt(p, "INSERT INTO gey_site (code, name, art, becken, breite, laenge, hoehe_m, lage, saeule_max_m, tafel, taste) VALUES ("
                    + q(Sites.CODES[i]) + ", " + q(s.name) + ", " + q(s.kind == Sites.Kind.GEYSER ? "GEYSIR" : "QUELLE") + ", "
                    + q(s.basin == 1 ? "MIDWAY" : s.basin == 2 ? "LOWER" : "UPPER") + ", " + n(s.lat) + ", " + n(s.lon) + ", " + n(s.elevation) + ", "
                    + point(s.lat, s.lon) + ", " + n(s.height) + ", " + q(s.line) + ", " + (i < 7 ? String.valueOf(i + 1) : "NULL") + ")");
        }
        p.println("-- ------------------------------------------------------------- Geysire");
        for (GeyserModel g : w.geysers.list) {
            int si = Sites.index(g.name);
            if (si < 0) continue;                 // Nebengeysire (Turban, Indicator, Mortar, Splendid) ohne eigene Stelle
            double[] v = g.params();
            stmt(p, "INSERT INTO gey_geyser (site_id, typ, schlot_r_m, roehre_m, saeule_max_m, kurz_min_s, kurz_max_s, lang_min_s, lang_max_s, "
                    + "anteil_kurz, abstand_kurz_s, abstand_lang_s, streuung_s, dampfphase_s, neigung_grad, stoesse_min, stoesse_max) VALUES ("
                    + "(SELECT site_id FROM gey_site WHERE code = " + q(Sites.CODES[si]) + "), "
                    + q(g.type == GeyserModel.Type.FOUNTAIN ? "FONTAENE" : "KEGEL") + ", "
                    + n(v[0]) + ", " + n(v[1]) + ", " + n(v[2]) + ", " + n(v[3]) + ", " + n(v[4]) + ", " + n(v[5]) + ", " + n(v[6]) + ", "
                    + n(v[7]) + ", " + n(v[8]) + ", " + n(v[9]) + ", " + n(v[10]) + ", " + n(v[11]) + ", " + n(v[12]) + ", "
                    + n(v[13]) + ", " + n(v[14]) + ")");
        }
        p.println("-- ------------------------------------------------------------- Quellen");
        for (Thermal.Spring s : w.scene.thermal.springs) {
            String code = springCode(s.name);
            double lat = Sites.LAT0 - s.z / Sites.M_LAT, lon = Sites.LON0 + s.x / Sites.M_LON;
            int si = Sites.index(s.name);
            String site = si >= 0 ? "(SELECT site_id FROM gey_site WHERE code = " + q(Sites.CODES[si]) + ")" : "NULL";
            String art = s.kind == Thermal.Kind.POOL ? "BECKEN" : s.kind == Thermal.Kind.VENT ? "SCHLOT" : s.kind == Thermal.Kind.MUD ? "SCHLAMM" : "KRATER";
            stmt(p, "INSERT INTO gey_spring (code, name, art, site_id, temp_c, abfall_k, halbachse_x_m, halbachse_z_m, tiefe_m, abfluss_m, saum_m, lage, genaehert) VALUES ("
                    + q(code) + ", " + q(s.name) + ", " + q(art) + ", " + site + ", " + n(round1(s.t0)) + ", " + n(round1(s.drop)) + ", "
                    + n(round1(s.ax)) + ", " + n(round1(s.az)) + ", " + n(round1(s.depth)) + ", " + n(round1(s.runLen)) + ", " + n(round1(s.apron)) + ", "
                    + point(lat, lon) + ", " + q(approx(code) ? "J" : "N") + ")");
        }
        p.println("-- ------------------------------------------------------------- Mineralien");
        int mo = 0;
        for (Mineral m : Minerals.ALL) {
            String hex = String.format("#%02X%02X%02X", m.swatch.getRed(), m.swatch.getGreen(), m.swatch.getBlue());
            stmt(p, "INSERT INTO gey_mineral (code, name, formel, art, hier, ort, text, farbe_hex, reihenfolge) VALUES ("
                    + q(m.code) + ", " + q(m.name) + ", " + q(m.formula) + ", " + q(m.kind) + ", " + q(m.local ? "J" : "N") + ", "
                    + q(m.place) + ", " + q(m.text) + ", " + q(hex) + ", " + (++mo) + ")");
            String mid = "(SELECT mineral_id FROM gey_mineral WHERE code = " + q(m.code) + ")";
            int eo = 0;
            for (int i = 0; i < m.z.length; i++) {
                stmt(p, "INSERT INTO gey_mineral_element (mineral_id, atomic_number, anzahl, spur, reihenfolge) VALUES ("
                        + mid + ", " + m.z[i] + ", " + String.format(Locale.ROOT, "%.4f", m.count[i]) + ", 'N', " + (++eo) + ")");
            }
            for (int tz : m.traces) {
                stmt(p, "INSERT INTO gey_mineral_element (mineral_id, atomic_number, anzahl, spur, reihenfolge) VALUES ("
                        + mid + ", " + tz + ", NULL, 'J', " + (++eo) + ")");
            }
        }
        p.println("-- ------------------------------------------------------------- Klima");
        for (int i = 0; i < 12; i++) {
            stmt(p, "INSERT INTO gey_climate (monat, hoch_f, tief_f, source_id) VALUES (" + (i + 1) + ", " + n(Climate.HI_F[i]) + ", "
                    + n(Climate.LO_F[i]) + ", " + src("CR_CLIMATE") + ")");
        }
        stmt(p, "COMMIT");
    }

    static double round1(double v) { return Math.round(v * 10) / 10.0; }

    private static void stmt(PrintWriter p, String s) {
        p.println(s);
        p.println("/");
    }

    /** Erwartete Zeilenzahlen für den Durchstich. */
    public static int facts() {
        int n = Belege.FACTS.length;
        for (Mineral m : Minerals.ALL) n += m.facts.length;
        return n;
    }

    public static int mineralElements() {
        int n = 0;
        for (Mineral m : Minerals.ALL) n += m.z.length + m.traces.length;
        return n;
    }
}
