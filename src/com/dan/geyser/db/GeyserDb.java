package com.dan.geyser.db;

import com.dan.geyser.atom.Mineral;

import java.awt.Color;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zugriff auf die Tabellen GEY_ im Schema aus db/db.properties. Schreibt nur über GEY_API, liest
 * Stammdaten, Zustände und Auswertungen per SQL. Eine Verbindung, Autocommit; bei einem
 * Verbindungsfehler wird beim nächsten Aufruf neu verbunden. Nicht auf dem Swing-Faden rufen.
 */
public final class GeyserDb implements AutoCloseable {
    private final DbConfig cfg;
    private Connection con;

    public GeyserDb(DbConfig cfg) { this.cfg = cfg; }

    public synchronized Connection con() throws SQLException {
        if (con == null || con.isClosed()) {
            con = cfg.connect();
            con.setAutoCommit(true);
        }
        return con;
    }

    @Override public synchronized void close() {
        try { if (con != null) con.close(); } catch (SQLException ignored) { }
        con = null;
    }

    public synchronized String apiVersion() throws SQLException {
        try (Statement st = con().createStatement(); ResultSet r = st.executeQuery("SELECT gey_api.version() FROM dual")) {
            r.next();
            return r.getString(1);
        }
    }

    // ------------------------------------------------------------ Stammdaten

    /** Was die App beim Start aus der Datenbank übernimmt. */
    public static final class Snapshot {
        public final Map<String, String> tafel = new LinkedHashMap<>();
        public final Map<String, double[]> params = new LinkedHashMap<>();
        public final Map<String, String> typ = new HashMap<>();
        public Mineral[] minerals;
        public final double[] hiF = new double[12], loF = new double[12];
        public int sources, facts, springs;
        public String api;
    }

    public synchronized Snapshot load() throws SQLException {
        Snapshot s = new Snapshot();
        Connection c = con();
        s.api = apiVersion();
        try (Statement st = c.createStatement()) {
            try (ResultSet r = st.executeQuery("SELECT code, titel, herausgeber, url, art, TO_CHAR(abgerufen, 'YYYY-MM-DD') FROM gey_source ORDER BY source_id")) {
                while (r.next()) {
                    Belege.put(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6));
                    s.sources++;
                }
            }
            try (ResultSet r = st.executeQuery("SELECT code, tafel FROM gey_site ORDER BY taste")) {
                while (r.next()) s.tafel.put(r.getString(1), r.getString(2));
            }
            try (ResultSet r = st.executeQuery("SELECT s.code, g.typ, g.schlot_r_m, g.roehre_m, g.saeule_max_m, g.kurz_min_s, g.kurz_max_s, g.lang_min_s, g.lang_max_s, "
                    + "g.anteil_kurz, g.abstand_kurz_s, g.abstand_lang_s, g.streuung_s, g.dampfphase_s, g.neigung_grad, g.stoesse_min, g.stoesse_max "
                    + "FROM gey_geyser g JOIN gey_site s ON s.site_id = g.site_id")) {
                while (r.next()) {
                    double[] p = new double[15];
                    for (int i = 0; i < 15; i++) { p[i] = r.getDouble(3 + i); if (r.wasNull()) p[i] = 0; }
                    s.params.put(r.getString(1), p);
                    s.typ.put(r.getString(1), r.getString(2));
                }
            }
            try (ResultSet r = st.executeQuery("SELECT COUNT(*) FROM gey_spring")) { r.next(); s.springs = r.getInt(1); }
            try (ResultSet r = st.executeQuery("SELECT monat, hoch_f, tief_f FROM gey_climate ORDER BY monat")) {
                while (r.next()) { s.hiF[r.getInt(1) - 1] = r.getDouble(2); s.loF[r.getInt(1) - 1] = r.getDouble(3); }
            }
            // Kennzahlen der Mineralien mit Beleg
            Map<String, List<String>> facts = new HashMap<>();
            try (ResultSet r = st.executeQuery("SELECT f.bezug, s.code, f.text FROM gey_fact f JOIN gey_source s ON s.source_id = f.source_id "
                    + "WHERE f.thema = 'MINERAL' ORDER BY f.bezug, f.reihenfolge")) {
                while (r.next()) facts.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(r.getString(2) + "|" + r.getString(3));
            }
            try (ResultSet r = st.executeQuery("SELECT COUNT(*) FROM gey_fact")) { r.next(); s.facts = r.getInt(1); }
            Map<String, List<double[]>> els = new HashMap<>();
            try (ResultSet r = st.executeQuery("SELECT m.code, me.atomic_number, me.anzahl, me.spur FROM gey_mineral_element me "
                    + "JOIN gey_mineral m ON m.mineral_id = me.mineral_id ORDER BY m.code, me.reihenfolge")) {
                while (r.next()) {
                    double cnt = r.getDouble(3);
                    boolean trace = "J".equals(r.getString(4));
                    els.computeIfAbsent(r.getString(1), k -> new ArrayList<>()).add(new double[]{r.getInt(2), trace ? 0 : cnt, trace ? 1 : 0});
                }
            }
            List<Mineral> ms = new ArrayList<>();
            try (ResultSet r = st.executeQuery("SELECT code, name, formel, art, hier, ort, text, farbe_hex FROM gey_mineral ORDER BY reihenfolge")) {
                while (r.next()) {
                    String code = r.getString(1);
                    List<double[]> e = els.getOrDefault(code, new ArrayList<>());
                    int nz = 0, nt = 0;
                    for (double[] x : e) if (x[2] > 0) nt++; else nz++;
                    int[] z = new int[nz], tr = new int[nt];
                    double[] cnt = new double[nz];
                    int iz = 0, it = 0;
                    for (double[] x : e) {
                        if (x[2] > 0) tr[it++] = (int) x[0];
                        else { z[iz] = (int) x[0]; cnt[iz++] = x[1]; }
                    }
                    String hex = r.getString(8);
                    Color col = hex == null ? Color.GRAY : Color.decode(hex);
                    List<String> f = facts.getOrDefault(code, new ArrayList<>());
                    ms.add(new Mineral(code, r.getString(2), r.getString(3), r.getString(4), "J".equals(r.getString(5)), r.getString(6), col,
                            z, cnt, tr, r.getString(7), f.toArray(new String[0])));
                }
            }
            s.minerals = ms.toArray(new Mineral[0]);
        }
        return s;
    }

    // ------------------------------------------------------------ Sitzung und Protokoll

    public synchronized long sessionBegin(String host, String user, String java, String res) throws SQLException {
        try (CallableStatement cs = con().prepareCall("{ ? = call gey_api.session_begin(?, ?, ?, ?) }")) {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setString(2, host); cs.setString(3, user); cs.setString(4, java); cs.setString(5, res);
            cs.execute();
            return cs.getLong(1);
        }
    }

    public synchronized void sessionEnd(long id, long frames, double fps, String res) throws SQLException {
        try (CallableStatement cs = con().prepareCall("{ call gey_api.session_end(?, ?, ?, ?) }")) {
            cs.setLong(1, id); cs.setLong(2, frames); cs.setDouble(3, fps); cs.setString(4, res);
            cs.execute();
        }
    }

    /** Schreibt einen Ausbruch; NaN wird zu NULL. */
    public synchronized long logEruption(long session, String geyserCode, int day, double hour, double start, double duration,
                                         double height, double predicted, double interval, boolean manual) throws SQLException {
        try (CallableStatement cs = con().prepareCall("{ ? = call gey_api.log_eruption(?, ?, ?, ?, ?, ?, ?, ?, ?, ?) }")) {
            cs.registerOutParameter(1, Types.NUMERIC);
            cs.setLong(2, session); cs.setString(3, geyserCode); cs.setInt(4, day);
            num(cs, 5, Math.max(0, Math.min(23.9999, hour))); num(cs, 6, start); num(cs, 7, duration); num(cs, 8, height);
            num(cs, 9, predicted); num(cs, 10, interval);
            cs.setString(11, manual ? "J" : "N");
            cs.execute();
            return cs.getLong(1);
        }
    }

    private static void num(PreparedStatement ps, int i, double v) throws SQLException {
        if (Double.isNaN(v) || Double.isInfinite(v)) ps.setNull(i, Types.NUMERIC); else ps.setDouble(i, Math.round(v * 10000) / 10000.0);
    }

    /** Ausbrüche aller Sitzungen, neueste zuerst (höchstens limit). */
    public synchronized List<Object[]> eruptions(int limit) throws SQLException {
        List<Object[]> out = new ArrayList<>();
        try (PreparedStatement ps = con().prepareStatement("SELECT geysir, szenentag, szenenzeit_h, dauer_s, hoehe_max_m, abstand_s, vorhersage_s, "
                + "abweichung_min, von_hand, sitzung_beginn FROM gey_eruption_v ORDER BY eruption_id DESC FETCH FIRST ? ROWS ONLY")) {
            ps.setInt(1, limit);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    Object[] row = new Object[10];
                    for (int i = 0; i < 9; i++) row[i] = r.getObject(i + 1);
                    row[9] = r.getTimestamp(10);
                    out.add(row);
                }
            }
        }
        return out;
    }

    /** Güte der Vorhersage je Geysir (GEY_PREDICTION_V). */
    public synchronized List<Object[]> prediction() throws SQLException {
        List<Object[]> out = new ArrayList<>();
        try (Statement st = con().createStatement(); ResultSet r = st.executeQuery("SELECT geysir, ausbrueche, mit_vorhersage, mittel_abw_min, "
                + "anteil_10min_proz, abstand_min_min, abstand_max_min FROM gey_prediction_v ORDER BY ausbrueche DESC, geysir")) {
            while (r.next()) {
                Object[] row = new Object[7];
                for (int i = 0; i < 7; i++) row[i] = r.getObject(i + 1);
                out.add(row);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ Zustände

    public synchronized void saveState(State s) throws SQLException {
        try (CallableStatement cs = con().prepareCall("{ call gey_api.save_state(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) }")) {
            cs.setString(1, s.name); cs.setInt(2, s.day); num(cs, 3, Math.max(0, Math.min(23.9999, s.hour))); cs.setInt(4, s.site);
            for (int i = 0; i < 6; i++) num(cs, 5 + i, s.pose[i]);
            num(cs, 11, Math.max(0, Math.min(1, s.haze))); num(cs, 12, Math.max(0, Math.min(1, s.wind)));
            cs.setString(13, s.labels ? "J" : "N"); cs.setString(14, s.tube ? "J" : "N");
            cs.setString(15, s.thermo ? "J" : "N"); cs.setString(16, s.fauna ? "J" : "N");
            cs.setString(17, s.host);
            cs.execute();
        }
    }

    public synchronized List<State> states() throws SQLException {
        List<State> out = new ArrayList<>();
        try (Statement st = con().createStatement(); ResultSet r = st.executeQuery("SELECT name, tag, stunde, ort, dreh_x, dreh_y, dreh_z, gier_grad, nick_grad, "
                + "abstand_m, dunst, wind, beschriftung, schnitt, waermebild, tiere, rechner, gespeichert FROM gey_state "
                + "ORDER BY CASE WHEN name = 'Beim Beenden' THEN 0 ELSE 1 END, gespeichert DESC")) {
            while (r.next()) {
                State s = new State();
                s.name = r.getString(1); s.day = r.getInt(2); s.hour = r.getDouble(3); s.site = r.getInt(4);
                for (int i = 0; i < 6; i++) s.pose[i] = r.getDouble(5 + i);
                s.haze = r.getDouble(11); s.wind = r.getDouble(12);
                s.labels = "J".equals(r.getString(13)); s.tube = "J".equals(r.getString(14));
                s.thermo = "J".equals(r.getString(15)); s.fauna = "J".equals(r.getString(16));
                s.host = r.getString(17); s.saved = r.getTimestamp(18);
                out.add(s);
            }
        }
        return out;
    }

    public synchronized void deleteState(String name) throws SQLException {
        try (CallableStatement cs = con().prepareCall("{ call gey_api.delete_state(?) }")) {
            cs.setString(1, name);
            cs.execute();
        }
    }

    public synchronized void deleteSession(long id) throws SQLException {
        try (PreparedStatement ps = con().prepareStatement("DELETE FROM gey_session WHERE session_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }
}
