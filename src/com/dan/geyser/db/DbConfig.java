package com.dan.geyser.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Verbindungsdaten aus <code>db/db.properties</code> im Projektordner (oder aus der Datei, die die
 * Systemeigenschaft <code>geyser.db</code> nennt): <code>url</code>, <code>user</code>,
 * <code>password</code>. Fehlt die Datei oder ist die Datenbank nicht erreichbar, läuft die App ohne
 * sie weiter; {@link #status()} sagt, warum.
 */
public final class DbConfig {
    public final String url, user, password;
    private static volatile String lastStatus = "nicht versucht";

    private DbConfig(String url, String user, String password) {
        this.url = url; this.user = user; this.password = password;
    }

    /** Liest die Datei; null, wenn sie fehlt oder unvollständig ist. */
    public static DbConfig load() {
        File f = new File(System.getProperty("geyser.db", "db/db.properties"));
        if (!f.isFile()) { lastStatus = "keine Datei " + f.getPath(); return null; }
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            lastStatus = f.getPath() + " nicht lesbar: " + e.getMessage();
            return null;
        }
        String url = p.getProperty("url", p.getProperty("db.url"));
        String user = p.getProperty("user", p.getProperty("db.user"));
        String pw = p.getProperty("password", p.getProperty("db.password"));
        if (url == null || user == null || pw == null) { lastStatus = f.getPath() + " unvollständig (url, user, password)"; return null; }
        return new DbConfig(url.trim(), user.trim(), pw);
    }

    /** Öffnet eine Verbindung (höchstens 4 s Wartezeit beim Anmelden). */
    public Connection connect() throws SQLException {
        DriverManager.setLoginTimeout(4);
        Connection c = DriverManager.getConnection(url, user, password);
        lastStatus = "verbunden als " + user + " (" + url.replaceAll(".*@//?", "") + ")";
        return c;
    }

    public static void status(String s) { lastStatus = s; }

    /** Letzter Stand der Verbindung, für Hinweise in der Oberfläche. */
    public static String status() { return lastStatus; }
}
