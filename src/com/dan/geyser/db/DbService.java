package com.dan.geyser.db;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Die Datenbank für die App: ein eigener Faden, auf dem alles läuft, damit die Bildschleife nie
 * wartet. Beim Start verbindet er, prüft GEY_API, lädt die Stammdaten (die App übernimmt sie) und
 * beginnt eine Sitzung. Danach schreibt er Ausbrüche und Zustände im Hintergrund. Ohne
 * db/db.properties, ohne Verbindung oder ohne eingerichtete Tabellen läuft die App wie bisher;
 * {@link #status()} sagt, warum.
 */
public final class DbService {
    private final ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Geyser-DB");
        t.setDaemon(true);
        return t;
    });
    private volatile GeyserDb db;
    private volatile long session = -1;
    private volatile String status = "Datenbank: wird verbunden …";
    private volatile Consumer<String> statusListener = s -> { };

    public String status() { return status; }
    public boolean ready() { return db != null && session > 0; }
    public void setStatusListener(Consumer<String> l) { statusListener = l; l.accept(status); }

    private void status(String s) { status = s; statusListener.accept(s); }

    /** Verbinden, Stammdaten laden (onLoad), Sitzung beginnen. */
    public void start(String host, String user, String java, String res, Consumer<GeyserDb.Snapshot> onLoad) {
        ex.submit(() -> {
            DbConfig cfg = DbConfig.load();
            if (cfg == null) { status("Ohne Datenbank · " + DbConfig.status()); return; }
            GeyserDb d = new GeyserDb(cfg);
            GeyserDb.Snapshot s;
            try {
                s = d.load();
            } catch (Exception e) {
                String m = msg(e);
                if (m.contains("ORA-00942") || m.contains("ORA-00904") || m.contains("PLS-00201") || m.contains("ORA-06550"))
                    status("Verbunden, aber GEY_ ist nicht eingerichtet (db/DbSetup ausführen)");
                else status("Ohne Datenbank · " + m);
                d.close();
                return;
            }
            try {
                session = d.sessionBegin(host, user, java, res);
            } catch (Exception e) {
                status("Stammdaten geladen, Sitzung nicht begonnen · " + msg(e));
            }
            db = d;
            onLoad.accept(s);
            status("Datenbank: " + cfg.user + " · " + s.api + " · Sitzung " + session + " · " + s.minerals.length + " Mineralien, "
                    + s.facts + " Kennzahlen, " + s.sources + " Belege");
        });
    }

    /** Ausbruch im Hintergrund schreiben. */
    public void logEruption(String code, int day, double hour, double start, double dur, double h, double pred, double interval, boolean manual) {
        GeyserDb d = db;
        if (d == null || session <= 0) return;
        long ses = session;
        ex.submit(() -> {
            try { d.logEruption(ses, code, day, hour, start, dur, h, pred, interval, manual); }
            catch (Exception e) { status("Ausbruch nicht geschrieben · " + msg(e)); }
        });
    }

    /** Zustand im Hintergrund speichern; done bekommt null oder die Fehlermeldung. */
    public void saveState(State s, Consumer<String> done) {
        GeyserDb d = db;
        if (d == null) { done.accept("keine Datenbank"); return; }
        ex.submit(() -> {
            try { d.saveState(s); done.accept(null); } catch (Exception e) { done.accept(msg(e)); }
        });
    }

    public void states(Consumer<List<State>> ok, Consumer<String> err) {
        GeyserDb d = db;
        if (d == null) { err.accept("keine Datenbank"); return; }
        ex.submit(() -> {
            try { ok.accept(d.states()); } catch (Exception e) { err.accept(msg(e)); }
        });
    }

    public void deleteState(String name, Consumer<String> done) {
        GeyserDb d = db;
        if (d == null) { done.accept("keine Datenbank"); return; }
        ex.submit(() -> {
            try { d.deleteState(name); done.accept(null); } catch (Exception e) { done.accept(msg(e)); }
        });
    }

    /** Protokoll aller Sitzungen: Ausbrüche (neueste 500) und Güte je Geysir. */
    public void protocol(Consumer<Object[]> ok, Consumer<String> err) {
        GeyserDb d = db;
        if (d == null) { err.accept("keine Datenbank"); return; }
        ex.submit(() -> {
            try { ok.accept(new Object[]{d.eruptions(500), d.prediction()}); } catch (Exception e) { err.accept(msg(e)); }
        });
    }

    /**
     * Beim Beenden: Zustand „Beim Beenden“ und Ende der Sitzung schreiben, höchstens wait ms warten
     * (die App soll nie am Beenden hängen).
     */
    public void shutdown(State last, long frames, double fps, String res, long waitMs) {
        GeyserDb d = db;
        if (d == null) return;
        long ses = session;
        Future<?> f = ex.submit(() -> {
            try { if (last != null) d.saveState(last); } catch (Exception ignored) { }
            try { if (ses > 0) d.sessionEnd(ses, frames, fps, res); } catch (Exception ignored) { }
            d.close();
        });
        try { f.get(waitMs, TimeUnit.MILLISECONDS); } catch (Exception ignored) { }
    }

    static String msg(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().trim();
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }
}
