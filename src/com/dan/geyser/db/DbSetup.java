package com.dan.geyser.db;

import com.dan.geyser.atom.Mineral;
import com.dan.geyser.atom.Minerals;
import com.dan.geyser.effects.Climate;
import com.dan.geyser.world.Basin;
import com.dan.geyser.world.GeyserModel;
import com.dan.geyser.world.Sites;
import com.dan.geyser.world.World;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Einrichter für die Tabellen GEY_ im Schema aus db/db.properties (Aufbau wie bei Caracalla).
 * <ul>
 *   <li>ohne Argument: 03_inhalte.sql aus dem Code erzeugen, 01 bis 04 ausführen, dann prüfen</li>
 *   <li><code>neu</code>: vorher 99_abbau (alles mit GEY_ weg), dann wie oben</li>
 *   <li><code>pruefen</code>: nur der Durchstich</li>
 *   <li><code>abbau</code>: nur 99_abbau</li>
 * </ul>
 * Das Protokoll steht auf der Konsole und in db/einrichtung.txt. In NetBeans: Rechtsklick auf die
 * Datei, „Run File“ (ohne Argument; der Arbeitsordner ist der Projektordner).
 */
public final class DbSetup {
    private static PrintWriter log;
    private static int problems;

    public static void main(String[] a) throws Exception {
        String mode = a.length > 0 ? a[0] : "";
        DbConfig cfg = DbConfig.load();
        File out = new File("db/einrichtung.txt");
        out.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {
            log = w;
            say("Geyser · Einrichtung der Datenbank · " + java.time.LocalDateTime.now().withNano(0) + (mode.isEmpty() ? "" : " · " + mode));
            if (cfg == null) {
                say("FEHLER: db/db.properties fehlt oder ist unvollständig (url, user, password). " + DbConfig.status());
                return;
            }
            say("Verbindung: " + cfg.url + " als " + cfg.user);
            say("Becken wird gebaut (für Inhalte und Vergleich) …");
            World world = Basin.build();
            if (mode.isEmpty() || mode.equals("neu")) {
                try (PrintWriter p = new PrintWriter(new File("db/sql/03_inhalte.sql"), StandardCharsets.UTF_8)) {
                    Inhalte.write(p, world);
                }
                say("db/sql/03_inhalte.sql aus dem Code erzeugt.");
            }
            try (Connection c = cfg.connect()) {
                c.setAutoCommit(false);
                if (mode.equals("neu") || mode.equals("abbau")) run(c, "db/sql/99_abbau.sql");
                if (mode.isEmpty() || mode.equals("neu")) {
                    for (String f : new String[]{"db/sql/01_tabellen.sql", "db/sql/02_spatial.sql", "db/sql/03_inhalte.sql", "db/sql/04_api.sql"}) {
                        if (!run(c, f)) { say("Abbruch nach " + f + "."); return; }
                    }
                }
                if (!mode.equals("abbau")) check(c, cfg, world);
            }
            say(problems == 0 ? "ERGEBNIS: alles in Ordnung." : "ERGEBNIS: " + problems + " Problem(e), siehe oben.");
        } finally {
            System.out.println("Protokoll: " + out.getAbsolutePath());
        }
    }

    private static void say(String s) {
        System.out.println(s);
        log.println(s);
        log.flush();
    }

    /** Führt ein Skript aus; Anweisungen enden mit einer Zeile "/". */
    static boolean run(Connection c, String file) throws Exception {
        List<String> stmts = split(new String(Files.readAllBytes(new File(file).toPath()), StandardCharsets.UTF_8));
        say("");
        say("== " + file + " (" + stmts.size() + " Anweisungen)");
        int ok = 0;
        try (Statement st = c.createStatement()) {
            st.setEscapeProcessing(false);   // Trigger mit :new und Texte mit {…} unverändert an die Datenbank
            for (String s : stmts) {
                try {
                    st.execute(s);
                    ok++;
                } catch (SQLException e) {
                    problems++;
                    say("  FEHLER " + e.getErrorCode() + ": " + e.getMessage().trim());
                    say("  in: " + s.substring(0, Math.min(160, s.length())).replace('\n', ' '));
                    c.rollback();
                    return false;
                }
            }
        }
        c.commit();
        say("  " + ok + " ausgeführt");
        // Package übersetzt?
        if (file.endsWith("04_api.sql")) {
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(
                    "SELECT name || ' ' || type || ' Zeile ' || line || ': ' || text FROM user_errors WHERE name LIKE 'GEY\\_%' ESCAPE '\\' ORDER BY name, type, sequence")) {
                while (r.next()) { problems++; say("  ÜBERSETZUNGSFEHLER " + r.getString(1)); }
            }
        }
        return true;
    }

    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : text.split("\r?\n")) {
            String t = line.trim();
            if (t.equals("/")) {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s);
                cur.setLength(0);
                continue;
            }
            if (cur.length() == 0 && (t.isEmpty() || t.startsWith("--"))) continue;
            cur.append(line).append('\n');
        }
        return out;
    }

    // ------------------------------------------------------------ Durchstich

    private static long one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }

    private static void expect(Connection c, String what, String sql, long want) {
        try {
            long got = one(c, sql);
            boolean good = want < 0 ? got > 0 : got == want;
            if (!good) problems++;
            say(String.format("  %-46s %6d %s", what, got, good ? "ok" : "ERWARTET " + (want < 0 ? "> 0" : String.valueOf(want))));
        } catch (SQLException e) {
            problems++;
            say(String.format("  %-46s FEHLER %s", what, DbService.msg(e)));
        }
    }

    private static void sayCheck(String what, boolean good, String info) {
        if (!good) problems++;
        say(String.format("  %-46s %s %s", what, good ? "ok" : "FEHLER", info));
    }

    private static void check(Connection c, DbConfig cfg, World world) throws Exception {
        say("");
        say("== Durchstich");
        expect(c, "Belege", "SELECT COUNT(*) FROM gey_source", Belege.SOURCES.size());
        expect(c, "Kennzahlen", "SELECT COUNT(*) FROM gey_fact", Inhalte.facts());
        expect(c, "Kennzahlen ohne Beleg", "SELECT COUNT(*) FROM gey_fact WHERE source_id IS NULL", 0);
        expect(c, "Stellen", "SELECT COUNT(*) FROM gey_site", Sites.ALL.length);
        int geyserSites = 0;
        for (GeyserModel g : world.geysers.list) if (Sites.index(g.name) >= 0) geyserSites++;
        expect(c, "Geysire", "SELECT COUNT(*) FROM gey_geyser", geyserSites);
        expect(c, "Quellen", "SELECT COUNT(*) FROM gey_spring", world.scene.thermal.springs.size());
        expect(c, "  davon mit genäherter Lage", "SELECT COUNT(*) FROM gey_spring WHERE genaehert = 'J'", 3);
        expect(c, "Mineralien", "SELECT COUNT(*) FROM gey_mineral", Minerals.ALL.length);
        expect(c, "Mineral-Elemente", "SELECT COUNT(*) FROM gey_mineral_element", Inhalte.mineralElements());
        expect(c, "Mineral-Elemente ohne Treffer in am_element", "SELECT COUNT(*) FROM gey_mineral_check_v WHERE pruefung = 'FEHLT'", 0);
        expect(c, "Klima-Monate", "SELECT COUNT(*) FROM gey_climate", 12);
        expect(c, "ungültige Geometrien", "SELECT (SELECT COUNT(*) FROM gey_site WHERE MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(lage, 0.05) <> 'TRUE') "
                + "+ (SELECT COUNT(*) FROM gey_spring WHERE MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(lage, 0.05) <> 'TRUE') FROM dual", 0);
        expect(c, "Spatial-Metadaten GEY_", "SELECT COUNT(*) FROM user_sdo_geom_metadata WHERE table_name LIKE 'GEY\\_%' ESCAPE '\\'", 2);
        expect(c, "Spatial-Indizes GEY_", "SELECT COUNT(*) FROM user_indexes WHERE index_name LIKE 'GEY\\_%' ESCAPE '\\' AND ityp_name = 'SPATIAL_INDEX_V2'", 2);
        // Stellen im Umkreis von 1,5 km um Old Faithful: im Code nachgerechnet
        int near = 0;
        for (Sites.Site s : Sites.ALL) if (Math.hypot(s.x(), s.z()) < 1500) near++;
        expect(c, "Stellen bis 1,5 km um Old Faithful (Index)", "SELECT COUNT(*) FROM gey_site s, gey_site o WHERE o.code = 'OLD_FAITHFUL' "
                + "AND MDSYS.SDO_WITHIN_DISTANCE(s.lage, o.lage, 'distance=1500 unit=M') = 'TRUE'", near);
        expect(c, "Quellen in Midway bis 400 m um Grand Prismatic", "SELECT COUNT(*) FROM gey_spring s, gey_site o WHERE o.code = 'GRAND_PRISMATIC' "
                + "AND MDSYS.SDO_WITHIN_DISTANCE(s.lage, o.lage, 'distance=400 unit=M') = 'TRUE'", 2);
        expect(c, "Ungültige Objekte GEY_", "SELECT COUNT(*) FROM user_objects WHERE object_name LIKE 'GEY\\_%' ESCAPE '\\' AND status <> 'VALID'", 0);
        say("  Abstand zu Old Faithful (GEY_SITE_V):");
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT name, abstand_of_m FROM gey_site_v ORDER BY abstand_of_m")) {
            while (r.next()) {
                String name = r.getString(1);
                int i = Sites.index(name);
                double code = i < 0 ? Double.NaN : Math.hypot(Sites.ALL[i].x(), Sites.ALL[i].z());
                say(String.format(Locale.GERMANY, "    %-26s %7d m   (Szene %6.0f m)", name, r.getLong(2), code));
            }
        }

        // Laden wie die App und mit dem Code vergleichen
        GeyserDb db = new GeyserDb(cfg);
        try {
            GeyserDb.Snapshot s = db.load();
            sayCheck("GEY_API", s.api != null && s.api.startsWith("GEY_API"), s.api);
            int same = 0;
            for (int i = 0; i < Sites.ALL.length; i++) if (Sites.ALL[i].line.equals(s.tafel.get(Sites.CODES[i]))) same++;
            sayCheck("Tafeltexte gleich wie im Code", same == Sites.ALL.length, same + " von " + Sites.ALL.length);
            int sp = 0;
            int withSite = 0;
            for (GeyserModel g : world.geysers.list) {
                if (Sites.index(g.name) < 0) continue;
                withSite++;
                double[] d = s.params.get(Sites.CODES[Sites.index(g.name)]), p = g.params();
                boolean eq = d != null;
                for (int i = 0; eq && i < p.length; i++) eq = Math.abs(p[i] - d[i]) < 1e-6;
                if (eq) sp++;
            }
            sayCheck("Kennwerte der Geysire gleich wie im Code", sp == withSite, sp + " von " + withSite);
            int sm = 0;
            for (int i = 0; i < Minerals.ALL.length && i < s.minerals.length; i++) if (sameMineral(Minerals.ALL[i], s.minerals[i])) sm++;
            sayCheck("Mineralien gleich wie im Code", sm == Minerals.ALL.length && s.minerals.length == Minerals.ALL.length, sm + " von " + Minerals.ALL.length);
            boolean cl = true;
            for (int i = 0; i < 12; i++) cl &= Math.abs(s.hiF[i] - Climate.HI_F[i]) < 1e-9 && Math.abs(s.loF[i] - Climate.LO_F[i]) < 1e-9;
            sayCheck("Klima gleich wie im Code", cl, "12 Monate");

            // Schreiben, lesen, löschen
            long ses = db.sessionBegin("Durchstich", "DbSetup", System.getProperty("java.version"), "0x0");
            long er = db.logEruption(ses, "OLD_FAITHFUL", 269, 9.5, 1000, 180, 48.5, 1000 - 240, 5400, false);
            long nat;
            double dev;
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(
                    "SELECT COUNT(*), MAX(abweichung_min) FROM gey_eruption_v WHERE session_id = " + ses)) {
                r.next(); nat = r.getLong(1); dev = r.getDouble(2);
            }
            db.sessionEnd(ses, 123, 30.5, "0x0");
            db.deleteSession(ses);
            long left = one(c, "SELECT COUNT(*) FROM gey_eruption WHERE session_id = " + ses);
            sayCheck("Sitzung, Ausbruch, Abweichung, löschen", er > 0 && nat == 1 && Math.abs(dev - 4.0) < 1e-6 && left == 0,
                    String.format(Locale.GERMANY, "Abweichung %.1f min, danach %d übrig", dev, left));
            State t = new State();
            t.name = "Durchstich-Test"; t.day = 20; t.hour = 7.25; t.site = 1; t.pose = new double[]{1, 2, 3, 45, 20, 150};
            t.thermo = true; t.host = "DbSetup";
            db.saveState(t);
            t.hour = 8.5;
            db.saveState(t);   // zweites Mal: ersetzt
            State back = null;
            int named = 0;
            for (State x : db.states()) if (x.name.equals(t.name)) { back = x; named++; }
            db.deleteState(t.name);
            boolean gone = db.states().stream().noneMatch(x -> x.name.equals(t.name));
            sayCheck("Zustand speichern, ersetzen, laden, löschen", back != null && named == 1 && Math.abs(back.hour - 8.5) < 1e-6 && back.thermo
                    && back.site == 1 && Math.abs(back.pose[5] - 150) < 1e-6 && gone, back == null ? "nicht gefunden" : (gone ? "ok" : "nicht gelöscht"));
            List<Object[]> pr = db.prediction();
            sayCheck("Sicht der Vorhersage", pr.size() == world.geysers.list.size(), pr.size() + " Geysire");
        } catch (SQLException e) {
            problems++;
            say("  FEHLER beim Laden wie die App: " + DbService.msg(e));
        } finally {
            db.close();
        }
    }

    private static boolean sameMineral(Mineral a, Mineral b) {
        if (!a.code.equals(b.code) || !a.name.equals(b.name) || !a.formula.equals(b.formula) || a.local != b.local) return false;
        if (!a.text.equals(b.text) || a.z.length != b.z.length || a.traces.length != b.traces.length || a.facts.length != b.facts.length) return false;
        for (int i = 0; i < a.z.length; i++) if (a.z[i] != b.z[i] || Math.abs(a.count[i] - b.count[i]) > 1e-4) return false;
        for (int i = 0; i < a.traces.length; i++) if (a.traces[i] != b.traces[i]) return false;
        for (int i = 0; i < a.facts.length; i++) if (!a.facts[i].equals(b.facts[i]) || !a.factSources[i].equals(b.factSources[i])) return false;
        return a.swatch.getRGB() == b.swatch.getRGB();
    }

    private DbSetup() { }
}
