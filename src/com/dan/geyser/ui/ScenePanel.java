package com.dan.geyser.ui;

import com.dan.geyser.effects.Climate;

import com.dan.geyser.camera.CameraController;
import com.dan.geyser.camera.Director;
import com.dan.geyser.camera.Regie;
import com.dan.geyser.camera.Viewpoint;
import com.dan.geyser.core.Camera;
import com.dan.geyser.core.Engine3D;
import com.dan.geyser.core.Scene;
import com.dan.geyser.core.Terrain;
import com.dan.geyser.core.Thermal;
import com.dan.geyser.effects.DayNightCycle;
import com.dan.geyser.effects.LightingEngine;
import com.dan.geyser.effects.ParticleSystem;
import com.dan.geyser.world.Basin;
import com.dan.geyser.world.GeyserModel;
import com.dan.geyser.world.Geysers;
import com.dan.geyser.world.World;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Zeigt die Szene, nimmt Maus und Tastatur entgegen und treibt die Bildschleife. Gerechnet wird auf
 * einem eigenen Thread mit Delta-Time; die Oberfläche zeigt immer das zuletzt fertige Bild. Höchstens
 * 60 Bilder je Sekunde. Aufbau nach Semiramis. Dazu die Uhr der Geysire: Sie läuft mit der Szenenzeit
 * mit (auch im Zeitraffer), springt aber nicht mit, wenn man die Uhrzeit von Hand verschiebt. „Warten
 * abkürzen“ lässt Szene und Geysire sechzigmal so schnell laufen, bis ein Ausbruch beginnt.
 */
public final class ScenePanel extends JPanel {
    /** Farben der Oberfläche: Sinterweiß, Dampfgrau, Schwefelgelb, Quellblau. */
    static final Color INK = new Color(232, 234, 228), MUTED = new Color(178, 186, 184), SULFUR = new Color(226, 190, 72),
            POOL = new Color(98, 182, 226);

    private volatile Engine3D engine;
    private volatile CameraController ctl;
    private volatile Scene scene;
    private volatile Geysers geysers;
    private final ParticleSystem ps = new ParticleSystem();
    /** Uhr der Geysire in Sekunden; schneller Vorlauf bis zum nächsten Ausbruch; Ort 0 = Upper, 1 = Midway. */
    private volatile double gClock;
    private volatile boolean fast;
    private volatile int site;
    private volatile String triggerReq;
    private volatile boolean triggerNearest;
    private final Camera cam = new Camera();
    private final DayNightCycle cycle = new DayNightCycle();

    private volatile BufferedImage shown;
    private volatile String loading = "Das Becken wird geformt …";
    private volatile int step = 1;
    private javax.swing.Timer pulse;
    static final String[] STEPS = {"Gelände", "Sonne und Schatten"};
    private volatile int day = DayNightCycle.today();
    private volatile double hour = 9.0;
    private volatile boolean sunDirty = true;
    private volatile double haze = 0.12, timelapse = 0;
    private volatile int[] pickRequest;
    private volatile double fps;
    private volatile boolean running = true;
    private volatile boolean labels = true;

    private Consumer<String> status = s -> { };
    private Consumer<double[]> timeListener = v -> { };
    private Runnable onReady = () -> { };
    private Consumer<Boolean> orbitListener = b -> { };
    private int lastX, lastY;

    public ScenePanel() {
        setBackground(new Color(10, 16, 20));
        setFocusable(true);
        setDoubleBuffered(true);
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                takeOver();
                lastX = e.getX(); lastY = e.getY();
            }

            @Override public void mouseMoved(MouseEvent e) { mouseSeen(); }

            @Override public void mouseDragged(MouseEvent e) {
                mouseSeen();
                CameraController c = ctl;
                if (c == null) return;
                boolean pan = SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e) || e.isShiftDown();
                c.drag(e.getX() - lastX, e.getY() - lastY, pan);
                lastX = e.getX(); lastY = e.getY();
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown()) { lupeRequest = new int[]{e.getX(), e.getY()}; return; }
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) pickRequest = new int[]{e.getX(), e.getY()};
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                CameraController c = ctl;
                takeOver();
                if (c != null) c.wheel(e.getPreciseWheelRotation());
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { key(e, true); }
            @Override public void keyReleased(KeyEvent e) { key(e, false); }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                CameraController c = ctl;
                if (c != null) c.releaseKeys();
            }
        });
    }

    private void key(KeyEvent e, boolean down) {
        CameraController c = ctl;
        if (c == null) return;
        int k = -1;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W: case KeyEvent.VK_UP: k = CameraController.K_W; break;
            case KeyEvent.VK_S: case KeyEvent.VK_DOWN: k = CameraController.K_S; break;
            case KeyEvent.VK_A: case KeyEvent.VK_LEFT: k = CameraController.K_A; break;
            case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: k = CameraController.K_D; break;
            case KeyEvent.VK_Q: case KeyEvent.VK_PAGE_DOWN: k = CameraController.K_Q; break;
            case KeyEvent.VK_E: case KeyEvent.VK_PAGE_UP: k = CameraController.K_E; break;
            case KeyEvent.VK_SHIFT: k = CameraController.K_SHIFT; break;
            default:
        }
        if (k >= 0) { if (down && k != CameraController.K_SHIFT) takeOver(); c.setKey(k, down); return; }
        if (!down) return;
        int kc = e.getKeyCode();
        if (kc >= KeyEvent.VK_1 && kc <= KeyEvent.VK_7) { goSite(kc - KeyEvent.VK_1); return; }
        if (kc >= KeyEvent.VK_NUMPAD1 && kc <= KeyEvent.VK_NUMPAD7) { goSite(kc - KeyEvent.VK_NUMPAD1); return; }
        switch (kc) {
            case KeyEvent.VK_ESCAPE:
                Director dr = director;
                if (help) help = false; else if (dr != null && dr.active()) takeOver(); else if (cinema) setCinema(false);
                return;
            case KeyEvent.VK_R: case KeyEvent.VK_0: case KeyEvent.VK_NUMPAD0: goOverview(); return;
            case KeyEvent.VK_F: playFlight(nextFlight); return;
            case KeyEvent.VK_T: playWalk(); return;
            case KeyEvent.VK_B: playScript(); return;
            case KeyEvent.VK_G: goViewpoint((nextVp + 1) % Viewpoint.NAMES.length); return;
            case KeyEvent.VK_U: openLupe(); return;
            case KeyEvent.VK_C: setTube(!tubeOn); return;
            case KeyEvent.VK_I: setThermo(!thermoOn); return;
            case KeyEvent.VK_O: setSound(!soundOn); return;
            case KeyEvent.VK_N: setFauna(!faunaOn); return;
            case KeyEvent.VK_F3: setVisitors(!visitorsOn); return;
            case KeyEvent.VK_Z: playSinterLapse(); return;
            case KeyEvent.VK_J: playMorningGlory(); return;
            case KeyEvent.VK_Y: setWeather((weather.mode + 1) % com.dan.geyser.effects.Weather.MODES.length); return;
            case KeyEvent.VK_SPACE: {
                takeOver();
                boolean on = !c.autoOrbit;
                c.autoOrbit = on;
                SwingUtilities.invokeLater(() -> orbitListener.accept(on));
                return;
            }
            case KeyEvent.VK_PLUS: case KeyEvent.VK_ADD: case KeyEvent.VK_EQUALS: shiftHour(0.5); return;
            case KeyEvent.VK_MINUS: case KeyEvent.VK_SUBTRACT: shiftHour(-0.5); return;
            case KeyEvent.VK_L: labels = !labels; SwingUtilities.invokeLater(() -> labelListener.accept(labels)); return;
            case KeyEvent.VK_X: triggerNearest = true; return;
            case KeyEvent.VK_V: setFast(!fast); return;
            case KeyEvent.VK_M: setSite(site == 0 ? 1 : 0, true); return;
            case KeyEvent.VK_P: requestStill(); return;
            case KeyEvent.VK_K: case KeyEvent.VK_F11: setCinema(!cinema); return;
            case KeyEvent.VK_F1: case KeyEvent.VK_H: help = !help; return;
            default:
        }
    }

    // ------------------------------------------------------------ Absteckung

    private Consumer<Boolean> labelListener = b -> { };
    public void setLabelListener(Consumer<Boolean> l) { labelListener = l; }
    public void setLabels(boolean on) { labels = on; }

    /** Fliegt zur abgesteckten Stelle i (Reihenfolge wie in {@link com.dan.geyser.world.Sites#ALL}). */
    public void goSite(int i) {
        Scene sc = scene;
        CameraController c = ctl;
        if (sc == null || c == null || i < 0 || i >= sc.markers.size()) return;
        Scene.Marker m = sc.markers.get(i);
        takeOver();
        cmds.add(() -> {
            if (m.site != site) setSite(m.site, false);
            double d = i == 6 ? 420 : (i == 5 ? 60 : 150);
            c.flyTo(m.x, m.y, m.z, Double.NaN, i == 6 ? 30 : 17, d);
        });
        showToast(m.name + "  ·  " + m.line, 3500);
    }

    // ------------------------------------------------------------ Regie

    private volatile Director director;
    private volatile boolean takeOverReq;
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> cmds = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private int nextFlight, nextVp;

    /** Jede Eingabe beendet ein laufendes Programm; die Kamerasteuerung übernimmt an Ort und Stelle. */
    private void takeOver() { Director d = director; if (d != null && d.active()) takeOverReq = true; }

    /** Übergang zum Blickpunkt i (Liste in {@link Viewpoint#NAMES}). */
    public void goViewpoint(int i) {
        nextVp = i;
        cmds.add(() -> { Scene sc = scene; director.goTo(Viewpoint.all(sc.terrain)[i], cam); });
    }

    public void playFlight(int i) {
        nextFlight = (i + 1) % Regie.FLIGHTS.length;
        cmds.add(() -> director.play(Regie.flight(scene.terrain, i)));
    }

    public void playWalk() { cmds.add(() -> director.play(Regie.walk(scene.terrain))); }

    /** Drehbuch „Ein Tag am Old Faithful“ zum eingestellten Tag; Zeitraffer und Vorlauf ruhen. */
    public void playScript() {
        cmds.add(() -> {
            timelapse = 0;
            if (fast) setFast(false);
            director.play(Regie.script(scene.terrain, day));
        });
    }

    public void stopDirector() { takeOver(); }

    // ------------------------------------------------------------ Eruptionsprotokoll und Zustand

    private final java.util.List<ProtocolDialog.Entry> protocol = new java.util.ArrayList<>();

    /** Protokoll dieser Sitzung zeigen. */
    public void openProtocol() { ProtocolDialog.show(this, protocol); }

    private void logEruption(Geysers.Eruption e) {
        double h = hour - (gClock - e.start) / 3600.0;
        int d = day;
        while (h < 0) { h += 24; d = d > 1 ? d - 1 : 365; }
        synchronized (protocol) {
            protocol.add(new ProtocolDialog.Entry(e, d, h));
            if (protocol.size() > 2000) protocol.remove(0);
        }
        int si = com.dan.geyser.world.Sites.index(e.geyser);
        // eine ältere Datenbank kennt die neuen Geysire noch nicht (DbSetup neu)
        java.util.Set<String> known = dbGeysers;
        if (si >= 0 && (known == null || known.contains(com.dan.geyser.world.Sites.CODES[si]))) db.logEruption(com.dan.geyser.world.Sites.CODES[si], d, h, e.start, e.duration, e.maxHeight, e.predicted, e.interval, e.manual);
    }

    // ------------------------------------------------------------ Datenbank

    private final com.dan.geyser.db.DbService db = new com.dan.geyser.db.DbService();
    /** Geysire, deren Kennwerte die Datenbank geliefert hat (null: noch keine Datenbank). */
    private volatile java.util.Set<String> dbGeysers;
    private volatile long totalFrames;
    private volatile double totalTime;
    private Consumer<double[]> airListener = v -> { };

    public com.dan.geyser.db.DbService db() { return db; }
    public void setAirListener(Consumer<double[]> l) { airListener = l; }

    /** Protokoll aller Sitzungen aus der Datenbank. */
    public void openProtocolAll() {
        db.protocol(r -> SwingUtilities.invokeLater(() -> ProtocolDialog.showDb(this, (java.util.List<Object[]>) r[0], (java.util.List<Object[]>) r[1])),
                err -> SwingUtilities.invokeLater(() -> showToast("Protokoll aus der Datenbank nicht lesbar: " + err, 5000)));
    }

    /** Stammdaten aus der Datenbank übernehmen (auf dem Rechenfaden): Tafeln, Kennwerte, Mineralien, Klima. */
    private void applySnapshot(com.dan.geyser.db.GeyserDb.Snapshot s) {
        Scene sc = scene;
        for (Scene.Marker m : sc.markers) {
            int i = com.dan.geyser.world.Sites.index(m.name);
            String t = i < 0 ? null : s.tafel.get(com.dan.geyser.world.Sites.CODES[i]);
            if (t != null) m.line = t;
        }
        int applied = 0;
        dbGeysers = new java.util.HashSet<>(s.params.keySet());
        for (GeyserModel g : geysers.list) {
            int i = com.dan.geyser.world.Sites.index(g.name);
            double[] p = i < 0 ? null : s.params.get(com.dan.geyser.world.Sites.CODES[i]);
            if (p != null) { g.setParams(p); applied++; }
        }
        if (s.minerals != null && s.minerals.length > 0) com.dan.geyser.atom.Minerals.ALL = s.minerals;
        com.dan.geyser.effects.Climate.set(s.hiF, s.loF);
        showToast("Datenbank: Tafeln, " + applied + " Geysire, " + s.minerals.length + " Mineralien und Klima aus GEY_ übernommen", 4000);
    }

    /** Der jetzige Zustand zum Speichern. */
    public com.dan.geyser.db.State captureState() {
        com.dan.geyser.db.State st = new com.dan.geyser.db.State();
        CameraController c = ctl;
        if (c != null) st.pose = c.pose();
        st.day = day; st.hour = hour; st.site = site; st.haze = haze; st.wind = wind;
        st.labels = labels; st.tube = tubeOn; st.thermo = thermoOn; st.fauna = faunaOn;
        st.host = hostName();
        return st;
    }

    /** Einen gespeicherten Zustand setzen (Kamera gleitet hin). */
    public void applyState(com.dan.geyser.db.State st) {
        takeOver();
        cmds.add(() -> {
            setSunTime(st.day, st.hour);
            SwingUtilities.invokeLater(() -> timeListener.accept(new double[]{st.day, st.hour}));
            if (st.site != site) applySite(st.site);
            CameraController c = ctl;
            if (c != null) c.setPose(st.pose, false);
            labels = st.labels;
            SwingUtilities.invokeLater(() -> labelListener.accept(st.labels));
            tubeOn = st.tube; thermoOn = st.thermo; faunaOn = st.fauna;
            extrasChanged();
            setHaze(st.haze);
            wind = st.wind;
            SwingUtilities.invokeLater(() -> airListener.accept(new double[]{st.haze, st.wind}));
            showToast("Zustand „" + st.name + "“: " + st.summary(), 3500);
        });
    }

    static String hostName() {
        String h = System.getenv("COMPUTERNAME");
        if (h == null) h = System.getenv("HOSTNAME");
        if (h == null) try { h = java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception e) { h = "?"; }
        return h;
    }

    private String resolution() {
        java.awt.Dimension d = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        return d.width + "x" + d.height;
    }

    static java.io.File stateFile() {
        return new java.io.File(new java.io.File(System.getProperty("user.home"), ".geyser"), "zustand.properties");
    }

    /** Zustand beim Beenden in eine Datei im Benutzerordner: Tag, Uhrzeit, Ort, Kamera, Beschriftung, Zugaben. */
    public void saveState() {
        CameraController c = ctl;
        if (c == null) return;
        db.shutdown(captureState(), totalFrames, totalTime > 0 ? totalFrames / totalTime : 0, resolution(), 3000);
        java.util.Properties p = new java.util.Properties();
        double[] pose = c.pose();
        p.setProperty("day", String.valueOf(day));
        p.setProperty("hour", String.valueOf(hour));
        p.setProperty("site", String.valueOf(site));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) b.append(i > 0 ? "," : "").append(pose[i]);
        p.setProperty("pose", b.toString());
        p.setProperty("labels", String.valueOf(labels));
        p.setProperty("tube", String.valueOf(tubeOn));
        p.setProperty("thermo", String.valueOf(thermoOn));
        p.setProperty("fauna", String.valueOf(faunaOn));
        p.setProperty("visitors", String.valueOf(visitorsOn));
        p.setProperty("haze", String.valueOf(haze));
        p.setProperty("wind", String.valueOf(wind));
        p.setProperty("water", String.valueOf(water));
        p.setProperty("weather", String.valueOf(weather.mode));
        try {
            java.io.File f = stateFile();
            f.getParentFile().mkdirs();
            try (java.io.OutputStream o = new java.io.FileOutputStream(f)) { p.store(o, "Geyser · Zustand beim Beenden"); }
        } catch (Exception e) {
            System.err.println("Zustand nicht gespeichert: " + e);
        }
    }

    /** Liest den Zustand vom letzten Beenden, falls vorhanden, und setzt ihn (auf dem Rechenfaden, vor dem ersten Bild). */
    private void restoreState(CameraController c) {
        java.io.File f = stateFile();
        if (!f.isFile()) return;
        java.util.Properties p = new java.util.Properties();
        try (java.io.InputStream in = new java.io.FileInputStream(f)) { p.load(in); } catch (Exception e) { return; }
        try {
            int d = Integer.parseInt(p.getProperty("day", String.valueOf(day)));
            double h = Double.parseDouble(p.getProperty("hour", String.valueOf(hour)));
            setSunTime(d, h);
            SwingUtilities.invokeLater(() -> timeListener.accept(new double[]{d, h}));
            int st = Integer.parseInt(p.getProperty("site", "0"));
            if (st != site) applySite(st);
            String[] ps = p.getProperty("pose", "").split(",");
            if (ps.length == 6) {
                double[] pose = new double[6];
                for (int i = 0; i < 6; i++) pose[i] = Double.parseDouble(ps[i]);
                c.setPose(pose, false);
            }
            labels = Boolean.parseBoolean(p.getProperty("labels", "true"));
            boolean lb = labels;
            SwingUtilities.invokeLater(() -> labelListener.accept(lb));
            tubeOn = Boolean.parseBoolean(p.getProperty("tube", "false"));
            thermoOn = Boolean.parseBoolean(p.getProperty("thermo", "false"));
            faunaOn = Boolean.parseBoolean(p.getProperty("fauna", "true"));
            visitorsOn = Boolean.parseBoolean(p.getProperty("visitors", "true"));
            extrasChanged();
            int wm = Integer.parseInt(p.getProperty("weather", "0"));
            weather.mode = Math.max(0, Math.min(com.dan.geyser.effects.Weather.MODES.length - 1, wm));
            SwingUtilities.invokeLater(() -> weatherListener.accept(weather.mode));
            double wt = Double.parseDouble(p.getProperty("water", "1"));
            water = wt;
            geysers.setWater(wt);
            SwingUtilities.invokeLater(() -> waterListener.accept(wt));
            showToast("Zustand vom letzten Beenden: " + DayNightCycle.dateLabel(d) + ", " + DayNightCycle.timeLabel(h), 3500);
        } catch (Exception e) {
            System.err.println("Zustand nicht gelesen: " + e);
        }
    }

    // ------------------------------------------------------------ Zugaben

    private volatile boolean tubeOn, thermoOn, soundOn, faunaOn = true;
    private volatile com.dan.geyser.world.Fauna fauna;
    private volatile com.dan.geyser.world.Visitors visitors;
    private volatile boolean visitorsOn = true;
    private boolean ofWasErupting;

    /** Besucher auf den Stegen an oder aus. */
    public void setVisitors(boolean on) {
        visitorsOn = on;
        if (on) showToast("Besucher: im Juli bis über 1000 am Halbrund um Old Faithful; gezeigt höchstens " + com.dan.geyser.world.Visitors.MAX, 3500);
        extrasChanged();
    }
    private final com.dan.geyser.core.Animals animals = new com.dan.geyser.core.Animals();
    private final com.dan.geyser.effects.GeyserSound sound = new com.dan.geyser.effects.GeyserSound();
    private volatile GeyserModel tubeGeyser;
    /** Seismogramm der letzten 20 s (20 Werte je Sekunde) am Geysir des Schnitts. */
    private final float[] seis = new float[400];
    private volatile int seisHead;
    private double seisAcc;
    private double bugleIn = 20, bellowIn = 15;
    private final java.util.Random zrnd = new java.util.Random();
    private Consumer<boolean[]> extrasListener = b -> { };

    public void setExtrasListener(Consumer<boolean[]> l) { extrasListener = l; }
    private void extrasChanged() {
        boolean[] v = {tubeOn, thermoOn, soundOn, faunaOn, visitorsOn};
        SwingUtilities.invokeLater(() -> extrasListener.accept(v));
    }

    /** Schnitt durch die Röhre des Geysirs am Drehpunkt (oder des Ausbruchs in der Nähe). */
    public void setTube(boolean on) { tubeOn = on; extrasChanged(); }

    /** Wärmebild statt Farben. */
    public void setThermo(boolean on) {
        thermoOn = on;
        Engine3D r = engine;
        if (r != null) r.thermo = on;
        if (on) showToast("Wärmebild: Temperaturen aus dem Modell der App, nicht gemessen", 3500);
        extrasChanged();
    }

    /** Klang an oder aus; ohne Tonausgang bleibt er aus. */
    public void setSound(boolean on) {
        if (on && !sound.start()) { showToast("Kein Tonausgang gefunden", 3000); on = false; }
        if (!on) sound.stop();
        soundOn = on;
        extrasChanged();
    }

    /** Bisons und Wapitis. */
    public void setFauna(boolean on) {
        faunaOn = on;
        if (on) showToast("Bisons und Wapitis · im Park mindestens 23 m Abstand halten (NPS)", 3500);
        extrasChanged();
    }

    /** Pegel des Klangs aus dem Ort der Kamera: Säulen, Quellen, Fluss, Wind, Tiere zur Brunftzeit. */
    private void listen(Geysers gs, double dt) {
        double rx = cam.rx, rz = cam.rz;
        float roar = 0, rpan = 0, hiss = 0, splash = 0;
        double best = 0;
        for (GeyserModel g : gs.list) {
            double dx = g.x - cam.ex, dy = g.y - cam.ey, dz = g.z - cam.ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double att = 1 / (1 + Math.pow(d / 70, 2));
            double pan = (dx * rx + dz * rz) / Math.max(1, Math.hypot(dx, dz));
            if (g.phase == GeyserModel.Phase.ERUPTION) {
                double v = Math.min(1, g.height(gClock) / Math.max(1, g.hMax) + 0.25) * att;
                roar += v;
                if (v > best) { best = v; rpan = (float) pan; }
            } else if (g.phase == GeyserModel.Phase.STEAM) {
                hiss += (float) (g.steamShare() * att);
                if (att > best) { best = att * 0.5; rpan = (float) pan; }
            } else if (g.phase == GeyserModel.Phase.PREPLAY) {
                splash += (float) (att * (g.surgeDrop() > 0 ? 1 : 0.3));
            }
        }
        // Tremor: in der Nähe spürbar, hier hörbar gemacht
        float trem = 0, tpan = 0;
        for (GeyserModel g : gs.list) {
            double dx = g.x - cam.ex, dy = g.y - cam.ey, dz = g.z - cam.ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float v = (float) (g.tremor() * g.tubeDepth / 22 / (1 + Math.pow(d / 45, 2)));
            if (v > trem) { trem = v; tpan = (float) ((dx * rx + dz * rz) / Math.max(1, Math.hypot(dx, dz))); }
        }
        sound.tremor = Math.min(1, trem); sound.tremorPan = tpan;
        float boil = 0, bpan = 0;
        double bd = 1e9;
        for (Thermal.Spring s : scene.terrain.thermal.springs) {
            if (s.t0 < 85) continue;
            double d = Math.max(0, Math.hypot(s.x - cam.ex, s.z - cam.ez) - Math.max(s.ax, s.az));
            if (d < bd) { bd = d; bpan = (float) (((s.x - cam.ex) * rx + (s.z - cam.ez) * rz) / Math.max(1, Math.hypot(s.x - cam.ex, s.z - cam.ez))); }
        }
        double alt = Math.max(0, cam.ey - scene.terrain.sample(cam.ex, cam.ez));
        boil = (float) (1 / (1 + Math.pow((bd + alt) / 18, 2)));
        float rd = scene.terrain.riverDist((float) cam.ex, (float) cam.ez);
        sound.roar = Math.min(1.2f, roar); sound.roarPan = rpan; sound.hiss = Math.min(1, hiss); sound.splash = Math.min(1, splash);
        sound.boil = boil; sound.boilPan = bpan;
        sound.river = (float) (0.5 / (1 + Math.pow((Math.max(0, rd) + alt) / 35, 2)));
        sound.wind = (float) (wind * (0.25 + 0.75 * Math.min(1, alt / 60)));
        // Tiere zur Brunft: Wapitis Anfang September bis Mitte Oktober, Bisons Juli und August (NPS)
        com.dan.geyser.world.Fauna f = fauna;
        if (f != null && faunaOn) {
            bugleIn -= dt;
            bellowIn -= dt;
            if (bugleIn <= 0) {
                bugleIn = 25 + 45 * zrnd.nextDouble();
                double d = f.nearestElk(cam.ex, cam.ez);
                if (day >= 244 && day <= 288 && f.hasBull() && d < 900) {
                    double[] h = f.home(1);
                    float pan = h == null ? 0 : (float) (((h[0] - cam.ex) * rx + (h[1] - cam.ez) * rz) / Math.max(1, Math.hypot(h[0] - cam.ex, h[1] - cam.ez)));
                    sound.bugle((float) (0.9 / (1 + Math.pow(d / 160, 2))), pan);
                }
            }
            if (bellowIn <= 0) {
                bellowIn = 15 + 30 * zrnd.nextDouble();
                double d = f.nearestBison(cam.ex, cam.ez);
                if (day >= 182 && day <= 243 && d < 600) {
                    double[] h = f.home(0);
                    float pan = h == null ? 0 : (float) (((h[0] - cam.ex) * rx + (h[1] - cam.ez) * rz) / Math.max(1, Math.hypot(h[0] - cam.ex, h[1] - cam.ez)));
                    sound.bellow((float) (0.9 / (1 + Math.pow(d / 90, 2))), pan);
                }
            }
        }
    }

    // ------------------------------------------------------------ Mineral-Lupe und Sinter-Zeitraffer

    private volatile int[] lupeRequest;
    private volatile boolean lupeAtTarget;
    private volatile com.dan.geyser.atom.SinterGrowth sinter;
    private volatile boolean sinterOn;
    private volatile double sinterYearsBP = -1;
    private double sinterShadowAt;

    /** Lupe für die Quelle am Drehpunkt (Taste U, Knopf im Bedienfeld). */
    public void openLupe() { lupeAtTarget = true; }

    /** Zeitraffer am Kegel von Castle Geyser (Taste Z, Knopf in der Lupe). */
    public void playSinterLapse() {
        cmds.add(() -> {
            Scene sc = scene;
            if (sinter == null) {
                com.dan.geyser.world.Sites.Site ca = com.dan.geyser.world.Sites.ALL[2];
                sinter = new com.dan.geyser.atom.SinterGrowth(sc.mesh, ca.x(), ca.z(), 19.5, -5, 1.0);
            }
            if (site != 0) applySite(0);
            timelapse = 0;
            if (fast) setFast(false);
            com.dan.geyser.world.Sites.Site ca = com.dan.geyser.world.Sites.ALL[2];
            double cx = ca.x(), cz = ca.z();
            com.dan.geyser.camera.CameraPath p = new com.dan.geyser.camera.CameraPath();
            for (int k = 0; k <= 12; k++) {
                double a = Math.toRadians(225 + k * 15), r = 34 - k * 1.3;
                double ex = cx + r * Math.sin(a), ez = cz + r * Math.cos(a);
                p.add(k * 3.6, ex, sc.terrain.sample(ex, ez) + 8 - k * 0.35, ez, cx, k < 8 ? -3.2 : -2.6, cz);
            }
            double[] last = p.key(p.size() - 1);
            p.add(48.2, last[1], last[2], last[3], last[4], last[5], last[6]);   // 5 s stehen bleiben
            Director.Program pr = new Director.Program("Sinter-Zeitraffer");
            pr.add(new Director.Shot(p, 15.5, 15.7, "Castle Geyser wächst",
                    "Rund 5000 t Sinter, 470 bis 940 kg im Jahr: erst die Terrasse, dann seit etwa 1022 der Kegel. Wie lange die Terrasse brauchte, ist aus Masse und Rate gerechnet, nicht gemessen; die letzten 1000 Jahre laufen langsamer.",
                    "J. Volcanol. Geotherm. Res. 2021; Wikipedia: Castle Geyser").site(0).fades(true, false));
            director.play(pr);
            sinterOn = true;
        });
    }

    // ------------------------------------------------------------ Morning Glory über die Jahrzehnte

    private volatile boolean gloryOn;
    private volatile double gloryYear = -1;
    private double gloryAppliedT = Double.NaN;

    /** Zeitraffer an Morning Glory Pool von 1883 bis heute (Taste J). */
    public void playMorningGlory() {
        cmds.add(() -> {
            Scene sc = scene;
            if (site != 0) applySite(0);
            timelapse = 0;
            if (fast) setFast(false);
            com.dan.geyser.world.Sites.Site mg = com.dan.geyser.world.Sites.ALL[5];
            double cx = mg.x(), cz = mg.z(), cy = -15;
            com.dan.geyser.camera.CameraPath p = new com.dan.geyser.camera.CameraPath();
            for (int k = 0; k <= 10; k++) {
                double a = Math.toRadians(150 + k * 14), r = 17 - k * 0.5;
                double ex = cx + r * Math.sin(a), ez = cz + r * Math.cos(a);
                p.add(k * 4.2, ex, sc.terrain.sample(ex, ez) + 5.5 - k * 0.15, ez, cx, cy - 1.5, cz);
            }
            double[] last = p.key(p.size() - 1);
            p.add(47, last[1], last[2], last[3], last[4], last[5], last[6]);   // 5 s stehen bleiben
            Director.Program pr = new Director.Program("Morning Glory im Zeitraffer");
            pr.add(new Director.Shot(p, 12.0, 12.2, "Morning Glory Pool, 1883 bis heute",
                    "Bis in die 1940er heiß und tiefblau. Münzen und Abfall verstopften den Schlot, die Quelle kühlte ab, und gelbe und orange Matten wuchsen zur Mitte. Temperaturen zwischen den Eckpunkten genähert.",
                    "USGS: What's the story, Morning Glory?; Wikipedia: Morning Glory Pool").site(0).fades(true, false));
            director.play(pr);
            gloryOn = true;
        });
    }

    /** Temperatur von Morning Glory zum Fortschritt u (u ≥ 1: heute) setzen; die Kacheln des Temperaturfelds neu backen. */
    private void applyGlory(double u, boolean show) {
        Thermal th = scene.terrain.thermal;
        Thermal.Spring mg = th.byName("Morning Glory Pool");
        if (mg == null) return;
        double y = com.dan.geyser.world.MorningGlory.year(u);
        double t = u >= 1 ? com.dan.geyser.world.MorningGlory.NOW_T : com.dan.geyser.world.MorningGlory.tempAt(y);
        gloryYear = show ? y : -1;
        if (Double.isNaN(gloryAppliedT) || Math.abs(t - gloryAppliedT) > 0.12 || (u >= 1 && t != gloryAppliedT)) {
            mg.t0 = t;
            th.changed(mg);
            gloryAppliedT = t;
        }
    }

    /** Jahr, Temperatur und Ereignis während des Zeitraffers an Morning Glory, oben in der Mitte. */
    private void gloryHud(Graphics2D g) {
        double y = gloryYear;
        if (y < 0) return;
        int W = getWidth();
        String yr = String.valueOf((int) Math.floor(y));
        String sub = String.format(java.util.Locale.GERMANY, "Quellmund %.1f °C  ·  %s", com.dan.geyser.world.MorningGlory.tempAt(y),
                com.dan.geyser.world.MorningGlory.tempAt(y) > 78 ? "tiefblau" : com.dan.geyser.world.MorningGlory.tempAt(y) > 73 ? "Matten wachsen vom Rand" : "gelb und orange bis zur Mitte");
        String ev = com.dan.geyser.world.MorningGlory.event(y);
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        int w1 = g.getFontMetrics().stringWidth(yr);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        int w2 = Math.max(g.getFontMetrics().stringWidth(sub), ev == null ? 0 : g.getFontMetrics().stringWidth(ev));
        int bw = Math.max(w1, w2) + 40, x = (W - bw) / 2, top = cinema ? Math.max(20, (int) ((getHeight() - W / 2.39) / 2) + 14) : 96;
        g.setColor(new Color(10, 16, 20, 180));
        g.fillRoundRect(x, top, bw, ev == null ? 78 : 96, 12, 12);
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString(yr, x + (bw - w1) / 2, top + 38);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(SULFUR);
        g.drawString(sub, x + (bw - g.getFontMetrics().stringWidth(sub)) / 2, top + 58);
        if (ev != null) {
            g.setColor(MUTED);
            g.drawString(ev, x + (bw - g.getFontMetrics().stringWidth(ev)) / 2, top + 78);
        }
        g.setColor(new Color(255, 255, 255, 40));
        int by = top + (ev == null ? 66 : 86);
        g.fillRect(x + 20, by, bw - 40, 3);
        g.setColor(POOL);
        g.fillRect(x + 20, by, (int) ((bw - 40) * (y - com.dan.geyser.world.MorningGlory.FIRST) / (com.dan.geyser.world.MorningGlory.LAST - com.dan.geyser.world.MorningGlory.FIRST)), 3);
    }

    /** Stand des Zeitraffers aus dem Fortschritt u 0..1; u ≥ 1 stellt den heutigen Kegel wieder her. */
    private void applySinter(double u, boolean show, Geysers gs) {
        com.dan.geyser.atom.SinterGrowth sg = sinter;
        if (sg == null) return;
        double[] sh = com.dan.geyser.atom.SinterGrowth.shares(Math.min(1, u));
        double top = sg.set(sh[0], sh[1]);
        sinterYearsBP = show ? sh[2] : -1;
        GeyserModel g = gs.byName("Castle Geyser");
        if (g != null) g.y = -5 + Math.max(0.2, top);
        for (Thermal.Spring s : scene.terrain.thermal.springs) if (s.name.equals("Castle Geyser")) s.y = -5 + Math.max(0.2, top);
    }

    private static String sinterYear(double bp) {
        long y = Math.round(com.dan.geyser.atom.SinterGrowth.NOW - bp);
        if (bp < 0.5) return "heute";
        return y > 0 ? String.format(java.util.Locale.GERMANY, "um %,d n. Chr.", y) : String.format(java.util.Locale.GERMANY, "um %,d v. Chr.", 1 - y);
    }

    private String lupeContext(double[] p) {
        if (p == null) return "Upper Geyser Basin";
        Thermal.Spring best = null;
        double bd = 90;
        for (Thermal.Spring s : scene.terrain.thermal.springs) {
            double d = Math.hypot(s.x - p[0], s.z - p[2]) - Math.max(s.ax, s.az);
            if (d < bd) { bd = d; best = s; }
        }
        if (best == null) return site == 1 ? "Midway Geyser Basin" : "Upper Geyser Basin";
        return String.format(java.util.Locale.GERMANY, "am Rand von %s · %.0f °C", best.name, best.t0);
    }

    private void showLupe(String ctx) {
        SwingUtilities.invokeLater(() -> com.dan.geyser.atom.MineralLupe.show(SwingUtilities.getWindowAncestor(this), ctx,
                com.dan.geyser.atom.Minerals.ALL[0], this::playSinterLapse));
    }

    public void setRainbow(boolean on) { rainbow = on; Engine3D r = engine; if (r != null) r.rainbow = on; }
    public void setSteamShadow(boolean on) { steamShadow = on; Engine3D r = engine; if (r != null) r.steamShadow = on; }
    private volatile boolean rainbow = true, steamShadow = true;

    // ------------------------------------------------------------ Geysire und Ort

    private Consumer<Integer> siteListener = v -> { };
    private Consumer<Boolean> fastListener = v -> { };
    public void setSiteListener(Consumer<Integer> l) { siteListener = l; }
    public void setFastListener(Consumer<Boolean> l) { fastListener = l; }

    /**
     * Ort wählen: Upper Geyser Basin (0) oder Midway mit Grand Prismatic (1). Die feine Schattenkarte
     * wandert mit; mit fly gleitet die Kamera zur Übersicht des Ortes.
     */
    public void setSite(int s, boolean fly) {
        if (fly) takeOver();
        applySite(s);
        double[] c = s == 0 ? World.UPPER : World.MIDWAY;
        CameraController ctl0 = ctl;
        if (fly && ctl0 != null) {
            cmds.add(() -> {
                if (s == 0) ctl0.goOverview();
                else { Scene sc = scene; ctl0.flyTo(c[0], sc == null ? -24 : sc.terrain.sample(c[0], c[1]), c[1], 215, 22, 900); }
            });
        }
        showToast(s == 0 ? "Upper Geyser Basin" : "Midway Geyser Basin · Grand Prismatic Spring und Excelsior", 3000);
    }

    /** Ort umstellen ohne Kamerafahrt und Hinweis: feine Schattenkarte, Bedienfeld. */
    private void applySite(int s) {
        if (s == site) return;
        site = s;
        double[] c = s == 0 ? World.UPPER : World.MIDWAY;
        LightingEngine.centerX = s == 0 ? LightingEngine.FCX : c[0];
        LightingEngine.centerZ = s == 0 ? LightingEngine.FCZ : c[1];
        sunDirty = true;
        SwingUtilities.invokeLater(() -> siteListener.accept(s));
    }

    public int site() { return site; }

    /** Warten abkürzen: Szene und Geysire laufen sechzigmal so schnell, bis ein Ausbruch beginnt. */
    public void setFast(boolean on) {
        fast = on;
        if (on) showToast("Warten abkürzen: 60-fach bis zum nächsten Ausbruch", 2500);
        SwingUtilities.invokeLater(() -> fastListener.accept(on));
    }

    /** Ausbruch eines Geysirs jetzt auslösen (Name wie im Modell). */
    public void trigger(String name) { triggerReq = name; }

    private volatile double wind = 0.35;
    private volatile double water = 1;
    private final com.dan.geyser.effects.Weather weather = new com.dan.geyser.effects.Weather();
    private double rainWet, litOvercast;
    private Consumer<Integer> weatherListener = v -> { };
    public void setWeatherListener(Consumer<Integer> l) { weatherListener = l; }

    /** Wetter: 0 nach Jahreszeit, dann klar, bewölkt, Regen, Gewitter, Schneefall (siehe {@link com.dan.geyser.effects.Weather}). */
    public void setWeather(int mode) {
        weather.mode = mode;
        int m = mode;
        SwingUtilities.invokeLater(() -> weatherListener.accept(m));
        showToast("Wetter: " + com.dan.geyser.effects.Weather.MODES[mode] + (mode == 0 ? " (Modell: Sommergewitter, Winterschnee)" : ""), 2500);
    }

    public int weatherMode() { return weather.mode; }
    private Consumer<Double> waterListener = v -> { };
    public void setWaterListener(Consumer<Double> l) { waterListener = l; }

    /**
     * Grundwasser 0,2..1,5 (1 = heute), siehe {@link GeyserModel#water}. Die Vorhersagetafel rechnet
     * weiter mit der Regel der Ranger von heute; wie weit sie danebenliegt, zeigt das Protokoll.
     */
    public void setWater(double w) {
        double old = water;
        water = w;
        Geysers gs = geysers;
        if (gs != null) gs.setWater(w);
        if (Math.abs(old - w) > 1e-9) {
            GeyserModel of = gs == null ? null : gs.byName("Old Faithful");
            if (of != null && of.silenced()) showToast("Grundwasser " + Math.round(w * 100) + " %: Old Faithful verstummt, wie in der Dürre des 13. Jahrhunderts", 4500);
            else showToast(String.format(java.util.Locale.GERMANY, "Grundwasser %d %%: Abstände etwa × %.2f", Math.round(w * 100), GeyserModel.intervalScale(w)), 3000);
        }
    }

    public double water() { return water; }
    /** Windstärke 0..1 (1 ≈ 9 m/s): treibt Dampf und Gischt und bewegt die Kronen. */
    public void setWind(double w) { wind = w; }
    private volatile double airTemp = 8;

    /**
     * Lufttemperatur aus den Klimanormalwerten 1991–2020 am Old Faithful (Tages- und Jahresgang,
     * siehe {@link Climate}); daraus Dampfsichtbarkeit, Schneedecke und Raureif.
     */
    private void climate() {
        Thermal.setDay(day);
        double t = Climate.air(day, hour);
        airTemp = t;
        Thermal.ambient = (float) Math.max(0, t + 4);
        Thermal.snow = (float) Climate.snow(day);
        Thermal.rime = (float) Math.max(0, Math.min(1, (-2 - t) / 10));
        Geysers gs = geysers;
        if (gs != null) gs.steamVis = (float) Climate.steam(t);
    }

    /** Zustände an den Tafeln der Geysire; bei Old Faithful die Vorhersage wie im Visitor Center. */
    private void updateMarkers() {
        Scene sc = scene;
        Geysers gs = geysers;
        if (sc == null || gs == null) return;
        for (Scene.Marker m : sc.markers) {
            GeyserModel g = gs.byName(m.name);
            if (g == null) continue;
            String st = g.state(gClock);
            if (g.phase == GeyserModel.Phase.RECHARGE && !Double.isNaN(g.predicted) && !g.silenced()) st = "nächster etwa " + clockAt(g.predicted) + "  ·  " + st;
            if (g.phase == GeyserModel.Phase.RECHARGE && g.tremor() > 0.45) st += "  ·  der Boden zittert";
            m.live = st;
        }
    }

    /** Uhrzeit der Szene zu einem Zeitpunkt der Geysir-Uhr; ab einem Tag voraus mit dem Datum. */
    private String clockAt(double t) {
        double ahead = (t - gClock) / 86400.0;
        if (ahead > 1) {
            int d = day + (int) Math.floor((hour / 24.0) + ahead);
            while (d > 365) d -= 365;
            return ahead > 60 ? "in " + Math.round(ahead / 7) + " Wochen" : "am " + DayNightCycle.dateLabel(d);
        }
        double h = hour + (t - gClock) / 3600.0;
        h = ((h % 24) + 24) % 24;
        return DayNightCycle.timeLabel(h);
    }

    public String[] geyserNames() {
        return new String[]{"Old Faithful", "Beehive Geyser", "Castle Geyser", "Grand Geyser", "Riverside Geyser", "Daisy Geyser",
                "Grotto Geyser", "Fan Geyser", "Giantess Geyser", "Splendid Geyser"};
    }

    public void goOverview() { goViewpoint(0); }

    private void shiftHour(double dh) {
        double h = ((hour + dh) % 24 + 24) % 24;
        setSunTime(day, h);
        int d = day;
        SwingUtilities.invokeLater(() -> timeListener.accept(new double[]{d, h}));
    }

    // ------------------------------------------------------------ Schnittstelle zum Bedienfeld

    public void setStatusListener(Consumer<String> s) { status = s; }
    public void setTimeListener(Consumer<double[]> l) { timeListener = l; }
    public void setOnReady(Runnable r) { onReady = r; }
    public void setOrbitListener(Consumer<Boolean> l) { orbitListener = l; }
    public CameraController controller() { return ctl; }
    public int day() { return day; }
    public double hour() { return hour; }
    public void setSunTime(int day, double hour) { this.day = day; this.hour = hour; sunDirty = true; litHour = hour; }
    public void setHaze(double h) { haze = h; sunDirty = true; }
    public void setTimelapse(double hoursPerSecond) { timelapse = hoursPerSecond; }
    public void setEffects(boolean rays, boolean bloom) { fx = new boolean[]{rays, bloom}; applyFx(); }
    public void setFog(double f) { fog = f; applyFx(); }
    private volatile boolean[] fx = {true, true};
    private volatile double fog = 1;
    private void applyFx() {
        Engine3D r = engine;
        if (r == null) return;
        r.rays = fx[0]; r.bloom = fx[1];
        r.fogScale = fog;
    }
    public void setScale(double s) { scale = s; autoQuality = false; }
    public void setAutoQuality() { autoQuality = true; }
    public double fps() { return fps; }
    public double renderMs() { return renderMs; }
    public boolean ready() { return ctl != null; }

    /** Startet den Aufbau und die Bildschleife auf einem eigenen Thread. */
    public void start() {
        Thread t = new Thread(this::loop, "Geyser-Render");
        t.setDaemon(true);
        t.start();
    }

    public void stop() { running = false; }

    private void loop() {
        Engine3D r;
        CameraController c;
        try {
            long t0 = System.nanoTime();
            World world = Basin.build();
            Scene sc = world.scene;
            geysers = world.geysers;
            // Old Faithful beginnt gleich mit dem Vorspiel, die anderen irgendwo in ihrem Abstand
            java.util.Random rr = new java.util.Random();
            for (GeyserModel g : geysers.list) {
                double wait = g.name.equals("Old Faithful") ? 50 : (0.1 + 0.9 * rr.nextDouble()) * (g.intLong > 0 ? g.intLong : 3600);
                g.startIn(0, wait);
            }
            buildMs = (System.nanoTime() - t0) / 1e6;
            step = 2;
            loading = "Die Sonne steht über dem Plateau …";
            repaint();
            r = new Engine3D(sc, 4096);
            r.wetness = geysers.wet;
            r.particles = ps;
            cycle.set(day, hour);
            long s0 = System.nanoTime();
            r.setSky(cycle, haze);
            shadowMs = (System.nanoTime() - s0) / 1e6;
            sunDirty = false;
            c = new CameraController(sc.terrain);
            director = new Director(sc.terrain);
            fauna = new com.dan.geyser.world.Fauna(sc.terrain, sc.thermal);
            visitors = new com.dan.geyser.world.Visitors(sc.terrain);
            geysers.onEruption = this::logEruption;
            restoreState(c);
            db.start(hostName(), System.getProperty("user.name"), System.getProperty("java.version"), resolution(),
                    snap -> cmds.add(() -> applySnapshot(snap)));
            r.rainbow = rainbow; r.steamShadow = steamShadow;
            scene = sc;
            engine = r; ctl = c;
            applyFx();
            loading = null;
            SwingUtilities.invokeLater(onReady);
        } catch (Throwable ex) {
            loading = "Fehler beim Aufbau: " + ex;
            repaint();
            ex.printStackTrace();
            return;
        }
        Thread lw = new Thread(this::lightLoop, "Geyser-Licht");
        lw.setDaemon(true);
        lw.setPriority(Thread.NORM_PRIORITY - 1);
        lw.start();
        long last = System.nanoTime();
        double t = 0, fpsAcc = 0;
        int frames = 0;
        long lastStatus = 0;
        while (running) {
            long now = System.nanoTime();
            double realDt = Math.min(0.5, (now - last) / 1e9);
            double dt = Math.min(0.1, realDt);
            last = now;
            t += dt;
            double tl = timelapse;
            boolean ff = fast;
            // Die Uhr läuft in Echtzeit, beim Warten abkürzen 60-fach, dazu der Zeitraffer
            double advH = realDt / 3600.0 * (ff ? 60 : 1) + dt * tl;
            DayNightCycle dc0 = new DayNightCycle();
            dc0.set(day, hour);
            dc0.advance(advH);
            day = dc0.day(); hour = dc0.hour();
            if (tl > 0 || Math.abs(hour - litHour) > 1 / 60.0) { sunDirty = true; litHour = hour; }
            int minute = (int) (hour * 60);
            if (minute != lastMinute) {
                lastMinute = minute;
                double[] v = {day, hour};
                SwingUtilities.invokeLater(() -> timeListener.accept(v));
            }
            // Geysire: Uhr mit der Szene, Teilchen in Echtzeit
            double simDt = realDt * (ff ? 60 : 1) + dt * tl * 3600;
            gClock += simDt;
            Geysers gs = geysers;
            String trq = triggerReq;
            if (trq != null) {
                triggerReq = null;
                GeyserModel g = gs.byName(trq);
                if (g != null) { g.triggerNow(gClock); showToast(g.name + ": Ausbruch ausgelöst", 2500); }
            }
            if (triggerNearest) {
                triggerNearest = false;
                double[] pz = c.pose();
                GeyserModel best = null;
                double bd = Double.MAX_VALUE;
                for (GeyserModel g : gs.list) {
                    double d = Math.hypot(g.x - pz[0], g.z - pz[2]);
                    if (d < bd) { bd = d; best = g; }
                }
                if (best != null && bd < 1500) { best.triggerNow(gClock); showToast(best.name + ": Ausbruch ausgelöst", 2500); }
            }
            GeyserModel was = gs.erupting();
            climate();
            float wind = (float) this.wind;
            // Wetter: Bewölkung ins Licht, Regen und Schnee um die Kamera, Blitz und Donner
            if (weather.step(dt, day, hour, airTemp, cam.ex, cam.ez)) {
                weather.makeBolt(scene.terrain);
                double bd = Math.hypot(weather.boltX - cam.ex, weather.boltZ - cam.ez);
                float bp = (float) (((weather.boltX - cam.ex) * cam.rx + (weather.boltZ - cam.ez) * cam.rz) / Math.max(1, bd));
                if (soundOn) sound.thunder((float) Math.min(1, 1.6 / (1 + bd / 900)), (float) (bd / 343), bp);
            }
            com.dan.geyser.effects.Sky.overcastNext = weather.overcast;
            if (Math.abs(weather.overcast - litOvercast) > 0.03) { litOvercast = weather.overcast; sunDirty = true; }
            weather.emit(ps, scene.terrain, cam.ex, cam.ey, cam.ez, (float) dt, (float) (0.8 * wind * 9), (float) (0.6 * wind * 9));
            rainWet = Math.max(0, Math.min(1, rainWet + (weather.rain > 0.15 ? dt / 90 * weather.rain : -dt / 900)));
            r.rainWet = (float) (0.8 * rainWet);
            r.flash = weather.flash;
            r.bolt = weather.bolt;
            if (soundOn) sound.rain = (float) (weather.rain * 0.8 / (1 + Math.max(0, cam.ey - scene.terrain.sample(cam.ex, cam.ez)) / 80));
            gs.update(gClock, simDt, (float) dt, ps, (float) (0.8 * wind * 9), (float) (0.6 * wind * 9), (float) Math.max(0, cycle.elevationDeg / 40.0));
            r.plumes = gs.plumes;
            r.wind = wind;
            com.dan.geyser.world.Fauna fa = fauna;
            animals.clear();
            if (faunaOn && fa != null) {
                fa.update(dt, day, Thermal.snow);
                fa.fill(animals);
            }
            com.dan.geyser.world.Visitors vi = visitors;
            if (vi != null) {
                GeyserModel of = gs.byName("Old Faithful");
                boolean er = of != null && of.phase == GeyserModel.Phase.ERUPTION;
                boolean ended = ofWasErupting && !er;
                ofWasErupting = er;
                double mins = of == null || Double.isNaN(of.predicted) || er || of.silenced() ? Double.NaN : (of.predicted - gClock) / 60;
                if (visitorsOn) {
                    vi.update(dt * (ff ? 4 : 1), day, hour, Math.max(weather.rain, weather.snow * 0.5), mins, er || (of != null && of.phase == GeyserModel.Phase.PREPLAY), ended);
                    vi.fill(animals);
                }
            }
            r.animals = animals.n > 0 ? animals : null;
            r.thermo = thermoOn;
            GeyserModel now2 = gs.erupting();
            if (now2 != null && now2 != was && ff) {
                fast = false;
                SwingUtilities.invokeLater(() -> fastListener.accept(false));
                showToast(now2.name + " bricht aus", 3500);
            }
            int pw = Math.max(1, getWidth()), ph = Math.max(1, getHeight());
            int[] pr = pickRequest;
            if (pr != null && r.width() > 0) {
                pickRequest = null;
                c.focus(r.pick((int) (pr[0] * r.width() / (double) pw), bandY(pr[1], r.height(), pw, ph)));
            }
            int[] lr = lupeRequest;
            if (lr != null && r.width() > 0) {
                lupeRequest = null;
                showLupe(lupeContext(r.pick((int) (lr[0] * r.width() / (double) pw), bandY(lr[1], r.height(), pw, ph))));
            }
            if (lupeAtTarget) { lupeAtTarget = false; showLupe(lupeContext(c.pose())); }
            // Regie: Übernahme, Befehle, laufendes Programm; sonst die Kamerasteuerung
            Director dr = director;
            if (takeOverReq) {
                takeOverReq = false;
                if (dr.active()) {
                    dr.stop();
                    dr.clearCaption();
                    c.adopt(cam, dr.targetDistance());
                }
            }
            for (Runnable cmd; (cmd = cmds.poll()) != null; ) {
                if (c.autoOrbit) { c.autoOrbit = false; SwingUtilities.invokeLater(() -> orbitListener.accept(false)); }
                dr.clearCaption();
                cmd.run();
            }
            if (dr.active()) {
                boolean on = dr.update(dt, cam);
                Director.Shot st = dr.takeStarted();
                if (st != null && st.site >= 0 && st.site != site) applySite(st.site);
                String tg = dr.takeTrigger();
                if (tg != null) { GeyserModel g = gs.byName(tg); if (g != null) g.triggerNow(gClock); }
                double dh = dr.hour();
                if (!Double.isNaN(dh)) {
                    hour = dh;
                    if (Math.abs(hour - litHour) > 1 / 60.0) { sunDirty = true; litHour = hour; }
                }
                if (!on) c.adopt(cam, dr.targetDistance());
            } else {
                c.update(dt, cam);
            }
            if (sinterOn) {
                boolean run = dr.active() && "Sinter-Zeitraffer".equals(dr.title());
                applySinter(run ? dr.elapsedSeconds() / Math.max(1, dr.totalSeconds() - 5) : 1, run, gs);
                if (!run) sinterOn = false;
                if (!run || t - sinterShadowAt > 0.5) { sinterShadowAt = t; sunDirty = true; }
            }
            if (gloryOn) {
                boolean run = dr.active() && "Morning Glory im Zeitraffer".equals(dr.title());
                applyGlory(run ? dr.elapsedSeconds() / Math.max(1, dr.totalSeconds() - 5) : 1, run);
                if (!run) gloryOn = false;
            }
            if (soundOn) listen(gs, dt);
            if (tubeOn) {
                double[] pz0 = c.pose();
                GeyserModel tb = null;
                double tbd = 1e9;
                for (GeyserModel g0 : gs.list) {
                    double d = Math.hypot(g0.x - pz0[0], g0.z - pz0[2]) * (g0.phase == GeyserModel.Phase.ERUPTION ? 0.4 : 1);
                    if (d < tbd) { tbd = d; tb = g0; }
                }
                tubeGeyser = tb;
                // Seismogramm: zwanzigmal je Sekunde ein Ausschlag nach dem Tremor, dazu einzelne Blasenschläge
                seisAcc += dt;
                while (seisAcc >= 0.05 && tb != null) {
                    seisAcc -= 0.05;
                    double tr = tb.tremor();
                    float v = (float) (zrnd.nextGaussian() * 0.25 * tr + (zrnd.nextDouble() < 0.02 + 0.1 * tr ? (zrnd.nextDouble() - 0.5) * 1.6 * tr : 0));
                    seis[seisHead] = v;
                    seisHead = (seisHead + 1) % seis.length;
                }
            }
            // Qualität: fest oder automatisch. Auto hält 30 Bilder/s: in Bewegung und im Stillstand je
            // ein eigener Maßstab; im Stillstand übernimmt der Bildrechner das Licht aus dem letzten
            // Bild und schafft darum meist die volle Auflösung.
            cam.update();
            boolean moved = Math.abs(cam.ex - lastCam[0]) + Math.abs(cam.ey - lastCam[1]) + Math.abs(cam.ez - lastCam[2])
                    + Math.abs(cam.fx - lastCam[3]) + Math.abs(cam.fy - lastCam[4]) + Math.abs(cam.fz - lastCam[5]) > 1e-4;
            lastCam[0] = cam.ex; lastCam[1] = cam.ey; lastCam[2] = cam.ez; lastCam[3] = cam.fx; lastCam[4] = cam.fy; lastCam[5] = cam.fz;
            stillFor = moved ? 0 : stillFor + realDt;
            double sc = scale;
            if (autoQuality) {
                boolean still = stillFor > 0.7;
                // ein Bild direkt nach einem Wechsel (Maßstab, Licht, Stillstand) ist kalt und zählt nicht
                boolean cold = still && r.cacheHit < 0.5;
                if (lastWasAuto && still == lastWasSharp && !cold) {
                    if (still) {
                        if (loopMs > 1000 / 30.5) autoStill = Math.max(0.6, autoStill * 0.96);
                        else if (loopMs < 1000 / 40.0) autoStill = Math.min(1.0, autoStill * 1.03);
                    } else {
                        if (loopMs > 1000 / 31.0) autoScale = Math.max(0.42, autoScale * 0.96);
                        else if (loopMs < 1000 / 46.0) autoScale = Math.min(1.0, autoScale * 1.03);
                    }
                }
                sc = Math.round((still ? autoStill : autoScale) * 25) / 25.0;
                lastWasSharp = still;
            }
            lastWasAuto = autoQuality;
            // Kinomodus: nur das Band zwischen den Balken rechnen (spart ein Viertel und mehr)
            int bandH = cinema ? Math.min(ph, (int) Math.round(pw / 2.39)) : ph;
            r.cropY = bandH / (double) ph;
            int w = Math.max(64, (int) (pw * sc)), h = Math.max(48, (int) (bandH * sc));
            r.setSize(w, h);
            r.day = day; r.hour = hour; r.sidereal = cycle.siderealDeg;
            long r0 = System.nanoTime();
            shown = r.render(cam, t, dt);
            renderMs = (System.nanoTime() - r0) / 1e6;
            overlay = computeOverlay(r, scene);
            if (stillRequest) { stillRequest = false; saveStill(r); r.setSize(w, h); }
            repaint();
            frames++;
            totalFrames++;
            totalTime += realDt;
            fpsAcc += realDt;
            if (fpsAcc > 0.5) { fps = frames / fpsAcc; frames = 0; fpsAcc = 0; }
            if (now - lastStatus > 250_000_000L) {
                lastStatus = now;
                Runtime rt = Runtime.getRuntime();
                long mb = (rt.totalMemory() - rt.freeMemory()) >> 20;
                String zone = DayNightCycle.zone(day, hour);
                String sky = cycle.elevationDeg > -4
                        ? String.format(java.util.Locale.GERMANY, "Sonne %s %s, Höhe %.0f°, Richtung %.0f°", DayNightCycle.timeLabel(hour), zone, cycle.elevationDeg, cycle.azimuthDeg)
                        : String.format(java.util.Locale.GERMANY, "%s %s, Mond %.0f° hoch, %s", DayNightCycle.timeLabel(hour), zone, cycle.moonElevationDeg, cycle.moonLabel());
                double camH = Terrain.DATUM + cam.ey;
                String s = String.format(java.util.Locale.GERMANY, "%s%s  ·  %s  ·  Kamera %,.0f m ü. M.  ·  %,d Dreiecke, %,d im Bild  ·  %,d Teilchen  ·  %d × %d%s  ·  %.0f Bilder/s, %.0f ms je Bild%s  ·  Schatten %.0f ms  ·  Aufbau %.1f s  ·  %d MB",
                        dr.active() ? dr.title() : (c.autoOrbit ? "Rundflug" : "Orbit"), fast ? " · 60-fach" : "", sky, camH, scene.triangles(), r.drawnTris, r.drawnParticles,
                        w, h, autoQuality ? " (Auto)" : "", fps, renderMs, r.cacheHit > 0.05 ? String.format(java.util.Locale.GERMANY, ", Licht zu %.0f %% behalten", r.cacheHit * 100) : "", shadowMs, buildMs / 1000, mb);
                updateMarkers();
                SwingUtilities.invokeLater(() -> status.accept(s));
            }
            long spent = System.nanoTime() - now;
            loopMs = spent / 1e6;
            long wait = Math.max(1, (16_000_000L - spent) / 1_000_000L);
            try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
        }
    }

    private volatile double buildMs, shadowMs;
    private double litHour = -1;
    private int lastMinute = -1;
    private volatile Object[] overlay;

    /**
     * Beschriftung der Absteckung, im Takt des Bildrechners berechnet (dort stimmen Kamera und
     * Tiefenpuffer): Lage des Pfostenkopfs im Bild und ob er verdeckt ist.
     */
    private Object[] computeOverlay(Engine3D r, Scene sc) {
        int W = r.width(), H = r.height();
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        java.util.List<Scene.Marker> ms = new java.util.ArrayList<>();
        Director dr = director;
        if (labels && (dr == null || !dr.clean())) {
            for (Scene.Marker m : sc.markers) {
                double[] p = r.project(m.x, m.y, m.z);
                if (p == null || p[0] < 0 || p[1] < 0 || p[0] >= W || p[1] >= H || p[2] > 9000) continue;
                boolean seen = r.depthAt((int) p[0], (int) p[1]) >= p[2] - Math.max(2, p[2] * 0.004);
                pts.add(new float[]{(float) p[0], (float) p[1], seen ? 1f : 0.5f, (float) p[2]});
                ms.add(m);
            }
        }
        return new Object[]{W, H, pts, ms};
    }

    @SuppressWarnings("unchecked")
    private void drawOverlay(Graphics2D g) {
        Object[] o = overlay;
        if (o == null) return;
        int W = (Integer) o[0], H = (Integer) o[1];
        double kx = getWidth() / (double) W, ky = getHeight() / (double) H;
        java.util.List<float[]> pts = (java.util.List<float[]>) o[2];
        java.util.List<Scene.Marker> ms = (java.util.List<Scene.Marker>) o[3];
        Font fn = new Font("SansSerif", Font.BOLD, 12), fl = new Font("SansSerif", Font.PLAIN, 11);
        // Nahe Stellen zuerst; spätere Tafeln weichen nach oben aus, wenn sie eine frühere überdecken
        Integer[] order = new Integer[pts.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (u, v) -> Float.compare(pts.get(u)[3], pts.get(v)[3]));
        java.util.List<java.awt.Rectangle> placed = new java.util.ArrayList<>();
        for (int oi : order) {
            float[] p = pts.get(oi);
            Scene.Marker m = ms.get(oi);
            int x = (int) (p[0] * kx), y = (int) (p[1] * ky);
            int a = (int) (255 * p[2]);
            boolean near = p[3] < 2500;
            String live = m.live;
            g.setFont(fn);
            int tw = g.getFontMetrics().stringWidth(m.name);
            int lw = 0, vw = 0;
            if (near) { g.setFont(fl); lw = g.getFontMetrics().stringWidth(m.line); if (live != null) vw = g.getFontMetrics().stringWidth(live); }
            int bw = Math.max(tw, Math.max(lw, vw)) + 18, bh = near ? (live != null ? 51 : 36) : 20;
            int bx = x + 10, by = y - 26 - bh;
            java.awt.Rectangle box = new java.awt.Rectangle(bx, by, bw, bh);
            for (int guard = 0; guard < 12; guard++) {
                java.awt.Rectangle hit = null;
                for (java.awt.Rectangle q : placed) if (q.intersects(box)) { hit = q; break; }
                if (hit == null) break;
                box.y = hit.y - bh - 4;
            }
            placed.add(box);
            by = box.y;
            g.setColor(new Color(236, 110, 40, a));
            g.fillOval(x - 3, y - 3, 6, 6);
            g.setColor(new Color(236, 230, 214, (int) (200 * p[2])));
            g.drawLine(x, y, bx, by + bh);
            g.setColor(new Color(10, 16, 20, (int) (175 * p[2])));
            g.fillRoundRect(bx, by, bw, bh, 8, 8);
            g.setColor(new Color(SULFUR.getRed(), SULFUR.getGreen(), SULFUR.getBlue(), a));
            g.fillRect(bx, by + 5, 2, bh - 10);
            g.setFont(fn);
            g.setColor(new Color(INK.getRed(), INK.getGreen(), INK.getBlue(), a));
            g.drawString(m.name, bx + 10, by + 15);
            if (near) {
                g.setFont(fl);
                g.setColor(new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), a));
                g.drawString(m.line, bx + 10, by + 30);
                if (live != null) {
                    boolean hot = live.startsWith("Ausbruch") || live.startsWith("Vorspiel");
                    Color lc = hot ? SULFUR : POOL;
                    g.setColor(new Color(lc.getRed(), lc.getGreen(), lc.getBlue(), a));
                    g.drawString(live, bx + 10, by + 45);
                }
            }
        }
    }

    /** Rechnet Himmel und Schattenkarten neu, sobald sich Sonne oder Dunst ändern; im Hintergrund. */
    private void lightLoop() {
        DayNightCycle dc = new DayNightCycle();
        while (running) {
            Engine3D r = engine;
            if (r == null || !sunDirty) { sleep(8); continue; }
            LightingEngine spare = r.takeSpare();
            if (spare == null) { sleep(4); continue; }
            sunDirty = false;
            dc.set(day, hour);
            long t0 = System.nanoTime();
            spare.compute(r.mesh(), dc.dir, dc.moonDir, dc.moonLit, Math.min(1, haze + 0.3 * weather.overcast));
            shadowMs = (System.nanoTime() - t0) / 1e6;
            cycle.set(dc.day(), dc.hour());
            r.offer(spare);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ------------------------------------------------------------ Qualität, Standbild, Kinomodus

    private final double[] lastCam = new double[6];
    private double stillFor, autoScale = 0.8, autoStill = 1.0, renderMs, loopMs;
    private boolean lastWasAuto, lastWasSharp;
    private volatile double scale = 1;
    private volatile boolean autoQuality = true, stillRequest, cinema, help;
    private volatile String toast;
    private volatile long toastUntil;
    private Consumer<Boolean> cinemaListener = b -> { };

    public void setCinemaListener(Consumer<Boolean> l) { cinemaListener = l; }

    /** Kinomodus: Bedienfeld und Statuszeile verschwinden, Breitbild-Balken, keine Hinweise. */
    public void setCinema(boolean on) {
        if (on && !cinema) {
            cinemaSince = System.currentTimeMillis();
            CameraController c = ctl;
            Director dr = director;
            if (c != null && !c.autoOrbit && (dr == null || !dr.active())) {
                c.autoOrbit = true;
                SwingUtilities.invokeLater(() -> orbitListener.accept(true));
            }
        }
        cinema = on;
        mouseSeen();
        SwingUtilities.invokeLater(() -> cinemaListener.accept(on));
    }

    private volatile long cinemaSince, lastMouse = System.currentTimeMillis();
    private boolean cursorHidden;
    private static final java.awt.Cursor BLANK = java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new java.awt.Point(0, 0), "leer");

    private void mouseSeen() {
        lastMouse = System.currentTimeMillis();
        if (cursorHidden) { cursorHidden = false; setCursor(java.awt.Cursor.getDefaultCursor()); }
    }

    private void cinemaCursor() {
        boolean hide = cinema && System.currentTimeMillis() - lastMouse > 2000;
        if (hide != cursorHidden) { cursorHidden = hide; setCursor(hide ? BLANK : java.awt.Cursor.getDefaultCursor()); }
    }

    /** Titel beim Eintritt in den Kinomodus: blendet über sechs Sekunden ein und wieder aus. */
    private void cinemaTitle(Graphics2D g) {
        double t = (System.currentTimeMillis() - cinemaSince) / 1000.0;
        Director dr = director;
        if (t > 6 || (dr != null && dr.caption() != null)) return;
        float a = (float) Math.max(0, Math.min(1, Math.min(t / 1.2, (6 - t) / 1.5)));
        if (a <= 0.01f) return;
        int W = getWidth(), H = getHeight();
        int bar = (int) Math.max(0, (H - W / 2.39) / 2);
        java.awt.Composite old = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, a));
        String t1 = "GEYSER", t2 = "Upper Geyser Basin · Yellowstone · " + DayNightCycle.dateLabel(day) + " " + DayNightCycle.YEAR;
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        int y = H - bar - 70;
        g.setColor(new Color(0, 0, 0, 90));
        g.drawString(t1, 42, y + 2);
        g.setColor(INK);
        g.drawString(t1, 40, y);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(SULFUR);
        g.drawString(t2, 42, y + 28);
        g.setComposite(old);
    }

    public void requestStill() { stillRequest = true; showToast("Standbild wird gerechnet …", 30000); }

    private void showToast(String s, long ms) { toast = s; toastUntil = System.currentTimeMillis() + ms; }

    /** Standbild doppelt so groß (höchstens 3840 Pixel breit) unter Bilder/Geyser. */
    /** Mausposition (y im Fenster) auf eine Zeile des Bildes, im Kinomodus im Band zwischen den Balken. */
    private int bandY(int my, int rh, int pw, int ph) {
        int band = cinema ? Math.min(ph, (int) Math.round(pw / 2.39)) : ph;
        int top = (ph - band) / 2;
        return (int) Math.max(0, Math.min(rh - 1, (my - top) * rh / (double) band));
    }

    private void saveStill(Engine3D r) {
        try {
            int pw = Math.max(1, getWidth()), ph = Math.max(1, getHeight());
            double k = Math.min(2.0, 3840.0 / pw);
            int w = (int) (pw * k), h = (int) (ph * k);
            double crop = r.cropY;
            r.cropY = 1;
            r.setSize(w, h);
            r.resetExposure();
            r.render(cam, 0, 0);
            BufferedImage img = r.render(cam, 0, 0);
            r.resetExposure();
            r.cropY = crop;
            if (cinema) {
                int ch = Math.min(h, (int) Math.round(w / 2.39));
                BufferedImage c = new BufferedImage(w, ch, BufferedImage.TYPE_INT_RGB);
                Graphics2D cg = c.createGraphics();
                cg.drawImage(img, 0, -(h - ch) / 2, null);
                cg.dispose();
                img = c;
                h = ch;
            }
            java.io.File dir = new java.io.File(System.getProperty("user.home"), "Pictures");
            dir = dir.isDirectory() ? new java.io.File(dir, "Geyser") : new java.io.File("standbilder");
            dir.mkdirs();
            String name = "Geyser_" + java.time.LocalDateTime.now().withNano(0).toString().replace(':', '-').replace('T', '_') + ".png";
            java.io.File f = new java.io.File(dir, name);
            writePng(img, f, stillInfo(w, h));
            showToast("Standbild gespeichert: " + f.getAbsolutePath() + "  (" + w + " × " + h + ")", 6000);
        } catch (Exception e) {
            showToast("Standbild nicht gespeichert: " + e.getMessage(), 6000);
        }
    }

    private java.util.Map<String, String> stillInfo(int w, int h) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("Title", "Geyser · Upper Geyser Basin, Yellowstone");
        m.put("Description", DayNightCycle.dateLabel(day) + " " + DayNightCycle.YEAR + ", " + DayNightCycle.timeLabel(hour) + " "
                + DayNightCycle.zone(day, hour));
        m.put("Comment", String.format(java.util.Locale.ROOT, "Kamera %.1f %.1f %.1f, Blick %.3f %.3f %.3f, %d x %d",
                cam.ex, cam.ey, cam.ez, cam.fx, cam.fy, cam.fz, w, h));
        m.put("Software", "Geyser (Java " + System.getProperty("java.version") + ", eigener Software-Renderer)");
        m.put("Creation Time", java.time.LocalDateTime.now().withNano(0).toString());
        return m;
    }

    /** PNG mit Textfeldern (iTXt); fällt auf ein schlichtes PNG zurück, wenn das nicht geht. Aus Semiramis. */
    static void writePng(BufferedImage img, java.io.File f, java.util.Map<String, String> text) throws java.io.IOException {
        try {
            javax.imageio.ImageWriter wr = javax.imageio.ImageIO.getImageWritersByFormatName("png").next();
            javax.imageio.ImageWriteParam prm = wr.getDefaultWriteParam();
            javax.imageio.metadata.IIOMetadata md = wr.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(img), prm);
            javax.imageio.metadata.IIOMetadataNode root = new javax.imageio.metadata.IIOMetadataNode("javax_imageio_png_1.0");
            javax.imageio.metadata.IIOMetadataNode it = new javax.imageio.metadata.IIOMetadataNode("iTXt");
            for (java.util.Map.Entry<String, String> e : text.entrySet()) {
                javax.imageio.metadata.IIOMetadataNode n = new javax.imageio.metadata.IIOMetadataNode("iTXtEntry");
                n.setAttribute("keyword", e.getKey());
                n.setAttribute("compressionFlag", "FALSE");
                n.setAttribute("compressionMethod", "0");
                n.setAttribute("languageTag", "de");
                n.setAttribute("translatedKeyword", e.getKey());
                n.setAttribute("text", e.getValue());
                it.appendChild(n);
            }
            root.appendChild(it);
            md.mergeTree("javax_imageio_png_1.0", root);
            try (javax.imageio.stream.ImageOutputStream out = javax.imageio.ImageIO.createImageOutputStream(f)) {
                wr.setOutput(out);
                wr.write(new javax.imageio.IIOImage(img, null, md));
            } finally {
                wr.dispose();
            }
        } catch (RuntimeException | javax.imageio.metadata.IIOInvalidTreeException ex) {
            javax.imageio.ImageIO.write(img, "png", f);
        }
    }

    // ------------------------------------------------------------ Zeichnen

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        cinemaCursor();
        Graphics2D g = (Graphics2D) g0.create();
        int W = getWidth(), H = getHeight();
        BufferedImage img = shown;
        String l = loading;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (img != null && l == null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // das Band nur, wenn das Bild schon im Breitformat gerechnet ist (erstes Bild nach dem Umschalten)
            int band = cinema && img.getWidth() > 1.9 * img.getHeight() ? Math.min(H, (int) Math.round(W / 2.39)) : H;
            g.drawImage(img, 0, (H - band) / 2, W, band, null);
            Director dr = director;
            double fd = dr == null ? 0 : dr.fade();
            if (fd > 0) {
                g.setColor(new Color(0, 0, 0, (int) (255 * fd)));
                g.fillRect(0, 0, W, H);
            }
            if (!cinema) drawOverlay(g);
            if (cinema) {
                int bar = (int) Math.max(0, (H - W / 2.39) / 2);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, W, bar);
                g.fillRect(0, H - bar, W, bar);
                cinemaTitle(g);
            } else {
                hud(g);
            }
            caption(g);
            sinterHud(g);
            gloryHud(g);
            if (help) helpHud(g);
            if (toast != null && System.currentTimeMillis() < toastUntil) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 13));
                int tw = g.getFontMetrics().stringWidth(toast);
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRoundRect((W - tw) / 2 - 16, H / 2 - 20, tw + 32, 36, 10, 10);
                g.setColor(INK);
                g.drawString(toast, (W - tw) / 2, H / 2 + 3);
            }
        } else {
            startScreen(g, W, H, l);
        }
        g.dispose();
    }

    /** Startbildschirm: Titel, Schritt, Balken; dahinter eine stilisierte Dampfsäule. */
    private void startScreen(Graphics2D g, int W, int H, String l) {
        g.setPaint(new GradientPaint(0, 0, new Color(14, 34, 52), 0, H, new Color(6, 10, 14)));
        g.fillRect(0, 0, W, H);
        long ms = System.currentTimeMillis();
        // Dampf: ein paar weiche Kreise, die langsam steigen
        for (int i = 0; i < 14; i++) {
            double ph = ((ms / 1000.0) * 0.08 + i / 14.0) % 1;
            int r = (int) (40 + 160 * ph);
            int cx = W / 2 + (int) (Math.sin(i * 1.7 + ph * 3) * 30 * ph + ph * 60);
            int cy = (int) (H * 0.58 - ph * H * 0.45);
            g.setColor(new Color(220, 228, 232, (int) (28 * (1 - ph))));
            g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        }
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        String title = "GEYSER";
        g.drawString(title, (W - g.getFontMetrics().stringWidth(title)) / 2, H / 2 - 14);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.setColor(SULFUR);
        String sub = "Upper Geyser Basin · Yellowstone";
        g.drawString(sub, (W - g.getFontMetrics().stringWidth(sub)) / 2, H / 2 + 12);
        g.setColor(MUTED);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        String s = l == null ? "" : l;
        g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, H / 2 + 40);
        if (l != null && !l.startsWith("Fehler")) {
            int n = STEPS.length, cw = 190, bx = (W - n * cw) / 2, by = H / 2 + 64, cur = step;
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            for (int i = 0; i < n; i++) {
                int x = bx + i * cw;
                boolean done = i + 1 < cur, now = i + 1 == cur;
                g.setColor(new Color(255, 255, 255, 40));
                g.fillRoundRect(x + 6, by, cw - 12, 5, 5, 5);
                if (done || now) {
                    g.setColor(done ? POOL : new Color(98, 182, 226, 150));
                    int fw = done ? cw - 12 : (int) ((cw - 12) * (0.35 + 0.3 * Math.sin(ms / 300.0)));
                    g.fillRoundRect(x + 6, by, fw, 5, 5, 5);
                }
                String t = (i + 1) + "  " + STEPS[i];
                g.setColor(done ? INK : now ? POOL : new Color(120, 128, 132));
                g.drawString(t, x + (cw - g.getFontMetrics().stringWidth(t)) / 2, by + 24);
            }
            javax.swing.Timer tm = pulse;
            if (tm == null) {
                pulse = tm = new javax.swing.Timer(60, e -> { if (loading == null) ((javax.swing.Timer) e.getSource()).stop(); repaint(); });
                tm.start();
            }
        }
    }

    private void hud(Graphics2D g) {
        String place = "UPPER GEYSER BASIN  ·  YELLOWSTONE";
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        int w = g.getFontMetrics().stringWidth(place);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(14, 14, w + 28, 38, 10, 10);
        g.setColor(INK);
        g.drawString(place, 28, 40);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        String sub = DayNightCycle.dateLabel(day) + " " + DayNightCycle.YEAR + "  ·  " + DayNightCycle.timeLabel(hour) + " "
                + DayNightCycle.zone(day, hour) + "  ·  " + weather.label + String.format(java.util.Locale.GERMANY, ", %.0f °C", airTemp)
                + "  ·  44,46° N  110,83° W  ·  2240 m";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(14, 58, sw + 24, 24, 8, 8);
        g.setColor(SULFUR);
        g.drawString(sub, 26, 75);
        predictionBoard(g);
        GeyserModel tg = tubeGeyser;
        if (tubeOn && tg != null) {
            TubeSection.paint(g, tg, getWidth() - TubeSection.W - 16, 100, gClock);
            TubeSection.seismo(g, seis, seisHead, tg.tremor(), getWidth() - TubeSection.W - 16, 100 + TubeSection.H + 8);
        }
        if (thermoOn) thermoLegend(g);
        Director dr = director;
        if (dr != null && dr.active()) { timeline(g, dr); return; }
        String hint = "Ziehen: drehen · Rechts ziehen: verschieben · Rad: Zoom · Doppelklick: Drehpunkt · 1–7: Stellen · X: Ausbruch · V: Warten abkürzen · F1: Tasten";
        int hw = g.getFontMetrics().stringWidth(hint);
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(14, getHeight() - 36, hw + 20, 24, 8, 8);
        g.setColor(INK);
        g.drawString(hint, 24, getHeight() - 19);
    }

    /**
     * Tafel oben rechts, wie im Visitor Center: nächster Ausbruch von Old Faithful nach der Regel der
     * Ranger (±10 min), oder was gerade ausbricht.
     */
    private void predictionBoard(Graphics2D g) {
        Geysers gs = geysers;
        if (gs == null) return;
        GeyserModel of = gs.byName("Old Faithful");
        // der Ausbruch, der der Kamera am nächsten ist; in Midway keine Vorhersage für Old Faithful
        GeyserModel er = null;
        double bd = 3000;
        for (GeyserModel g0 : gs.list) {
            if (g0.phase != GeyserModel.Phase.ERUPTION) continue;
            double d = Math.hypot(g0.x - cam.ex, g0.z - cam.ez);
            if (d < bd) { bd = d; er = g0; }
        }
        if (site == 1 && er == null) return;
        String head, big, small;
        if (er != null) {
            head = er.name.toUpperCase(java.util.Locale.ROOT);
            big = String.format(java.util.Locale.GERMANY, "%.0f m", er.height(gClock));
            small = String.format("Ausbruch seit %d:%02d", (int) er.tPhase / 60, (int) er.tPhase % 60);
        } else if (of != null && of.phase == GeyserModel.Phase.PREPLAY) {
            head = "OLD FAITHFUL"; big = "gleich"; small = "Vorspiel: Wasser schwappt über";
        } else if (of != null && !Double.isNaN(of.predicted)) {
            head = "OLD FAITHFUL · NÄCHSTER AUSBRUCH";
            big = clockAt(of.predicted) + " " + DayNightCycle.zone(day, hour);
            small = Double.isNaN(of.lastDuration) ? "± 10 min" : String.format(java.util.Locale.GERMANY, "± 10 min · letzter Ausbruch %d:%02d min", (int) of.lastDuration / 60, (int) of.lastDuration % 60);
        } else return;
        int W = getWidth();
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        int w1 = g.getFontMetrics().stringWidth(head);
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        int w2 = g.getFontMetrics().stringWidth(big);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int w3 = g.getFontMetrics().stringWidth(small);
        int bw = Math.max(w1, Math.max(w2, w3)) + 28, bh = 76;
        int x = W - bw - 16, y = 14;
        g.setColor(new Color(58, 40, 26, 205));
        g.fillRoundRect(x, y, bw, bh, 8, 8);
        g.setColor(new Color(244, 234, 216, 90));
        g.drawRoundRect(x, y, bw, bh, 8, 8);
        g.setColor(new Color(244, 234, 216));
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString(head, x + 14, y + 20);
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.setColor(er != null ? SULFUR : new Color(250, 244, 232));
        g.drawString(big, x + 14, y + 49);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(220, 206, 184));
        g.drawString(small, x + 14, y + 66);
    }

    /** Farbskala des Wärmebilds unten rechts. */
    private void thermoLegend(Graphics2D g) {
        int w = 300, h = 12, x = getWidth() - w - 24, y = getHeight() - 58;
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(x - 10, y - 22, w + 20, 54, 10, 10);
        for (int i = 0; i < w; i++) {
            float t = Engine3D.THERMO_LO + (Engine3D.THERMO_HI - Engine3D.THERMO_LO) * i / (float) (w - 1);
            g.setColor(new Color(Engine3D.ironbow(t)));
            g.drawLine(x + i, y, x + i, y + h);
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(INK);
        g.drawString("WÄRMEBILD · Temperatur aus dem Modell", x, y - 8);
        for (int t = -20; t <= 100; t += 20) {
            int px = x + (int) ((t - Engine3D.THERMO_LO) / (Engine3D.THERMO_HI - Engine3D.THERMO_LO) * (w - 1));
            g.drawLine(px, y + h, px, y + h + 3);
            g.drawString(t + "°", px - 8, y + h + 14);
        }
    }

    /** Jahr und Sintermasse während des Zeitraffers, oben in der Mitte. */
    private void sinterHud(Graphics2D g) {
        double bp = sinterYearsBP;
        if (bp < 0) return;
        int W = getWidth();
        String yr = sinterYear(bp);
        double t = com.dan.geyser.atom.SinterGrowth.massAt(bp);
        String sub = String.format(java.util.Locale.GERMANY, "Sinter %,.0f t von rund 5000 t  ·  %s", t,
                bp > com.dan.geyser.atom.SinterGrowth.NOW - com.dan.geyser.atom.SinterGrowth.CONE_YEAR ? "die Terrasse wächst" : "der Kegel wächst");
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        int w1 = g.getFontMetrics().stringWidth(yr);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        int w2 = g.getFontMetrics().stringWidth(sub);
        int bw = Math.max(w1, w2) + 40, x = (W - bw) / 2, y = cinema ? Math.max(20, (int) ((getHeight() - W / 2.39) / 2) + 14) : 96;
        g.setColor(new Color(10, 16, 20, 180));
        g.fillRoundRect(x, y, bw, 78, 12, 12);
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.drawString(yr, x + (bw - w1) / 2, y + 38);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(SULFUR);
        g.drawString(sub, x + (bw - w2) / 2, y + 58);
        g.setColor(new Color(255, 255, 255, 40));
        g.fillRect(x + 20, y + 66, bw - 40, 3);
        g.setColor(SULFUR);
        g.fillRect(x + 20, y + 66, (int) ((bw - 40) * t / 5000), 3);
    }

    /** Zeitleiste des laufenden Programms unten im Bild, mit den Schnitten als Marken. */
    private void timeline(Graphics2D g, Director dr) {
        int W = getWidth(), H = getHeight();
        String title = dr.title();
        if (title == null) return;
        int bw = Math.min(560, W - 40), x = 20, y = H - 44;
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(x - 6, y - 22, bw + 12, 40, 10, 10);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(INK);
        double tot = dr.totalSeconds(), el = Math.min(tot, dr.elapsedSeconds());
        String right = String.format("%d:%02d / %d:%02d  ·  Esc oder Maus übernimmt", (int) el / 60, (int) el % 60, (int) tot / 60, (int) tot % 60);
        g.drawString(title, x + 4, y - 6);
        g.setColor(MUTED);
        g.drawString(right, x + bw - g.getFontMetrics().stringWidth(right) - 4, y - 6);
        g.setColor(new Color(255, 255, 255, 50));
        g.fillRoundRect(x + 4, y + 2, bw - 8, 5, 5, 5);
        g.setColor(SULFUR);
        g.fillRoundRect(x + 4, y + 2, (int) ((bw - 8) * dr.progress()), 5, 5, 5);
        g.setColor(new Color(12, 14, 20));
        for (double m : dr.marks()) g.fillRect(x + 4 + (int) ((bw - 8) * m), y + 1, 2, 7);
    }

    /** Tafel mit Überschrift, Text und Quelle, weich ein- und ausgeblendet. */
    private void caption(Graphics2D g) {
        Director dr = director;
        Object[] c = dr == null ? null : dr.caption();
        if (c == null) return;
        String head = (String) c[0], text = (String) c[1], src = (String) c[2];
        float a = (float) Math.max(0, Math.min(1, (Double) c[3]));
        if (a <= 0.01) return;
        int W = getWidth(), H = getHeight();
        int bw = Math.min(480, W - 40);
        Font fh = new Font("SansSerif", Font.BOLD, 22), ft = new Font("SansSerif", Font.PLAIN, 14), fs = new Font("SansSerif", Font.ITALIC, 12);
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text != null) {
            java.awt.FontMetrics fm = g.getFontMetrics(ft);
            StringBuilder line = new StringBuilder();
            for (String w : text.split(" ")) {
                if (line.length() > 0 && fm.stringWidth(line + " " + w) > bw - 40) { lines.add(line.toString()); line.setLength(0); }
                if (line.length() > 0) line.append(' ');
                line.append(w);
            }
            if (line.length() > 0) lines.add(line.toString());
        }
        int bh = 24 + (head != null ? 30 : 0) + lines.size() * 20 + (src != null ? 24 : 0) + 8;
        int x = 24, y = H - bh - (cinema ? Math.max(20, (int) ((H - W / 2.39) / 2) + 16) : 72);
        java.awt.Composite old = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, a));
        g.setColor(new Color(10, 16, 20, 180));
        g.fillRoundRect(x, y, bw, bh, 12, 12);
        g.setColor(SULFUR);
        g.fillRect(x, y + 14, 3, bh - 28);
        int cy = y + 20;
        if (head != null) {
            g.setFont(fh);
            g.setColor(INK);
            cy += 18;
            g.drawString(head, x + 20, cy);
            cy += 12;
        }
        g.setFont(ft);
        g.setColor(new Color(222, 226, 220));
        for (String l : lines) { cy += 20; g.drawString(l, x + 20, cy); }
        if (src != null) {
            g.setFont(fs);
            g.setColor(SULFUR);
            cy += 24;
            g.drawString("Quelle: " + src, x + 20, cy);
        }
        g.setComposite(old);
    }

    private void helpHud(Graphics2D g) {
        String[][] rows = {
                {"KAMERA", null},
                {"Maus ziehen", "drehen"}, {"rechts ziehen", "verschieben"}, {"Mausrad", "Zoom"},
                {"Doppelklick", "neuer Drehpunkt"}, {"W A S D, Pfeile", "Drehpunkt bewegen"}, {"Q E", "tiefer, höher"},
                {"Umschalt", "schneller"}, {"Leertaste", "Rundflug"}, {"0 oder R", "Übersicht"},
                {"REGIE", null},
                {"G", "nächster Blickpunkt"}, {"F", "nächste Kamerafahrt"}, {"T", "Rundgang"}, {"B", "Drehbuch"},
                {"Esc, Maus", "Kamera übernehmen"},
                {"MINERALIEN", null},
                {"U, Strg+Klick", "Mineral-Lupe"}, {"Z", "Sinter-Zeitraffer an Castle"},
                {"ZUGABEN", null},
                {"C", "Schnitt durch die Röhre"}, {"I", "Wärmebild"}, {"O", "Klang"}, {"N", "Bisons und Wapitis"},
                {"STELLEN", null},
                {"1 bis 6", "Old Faithful bis Morning Glory"}, {"7", "Grand Prismatic (Midway)"}, {"M", "Upper Basin oder Midway"},
                {"L", "Beschriftung"},
                {"GEYSIRE", null},
                {"X", "nächsten Geysir auslösen"}, {"V", "Warten abkürzen (60-fach)"},
                {"SONNE", null},
                {"+ und −", "½ Stunde vor, zurück"},
                {"BILD", null},
                {"P", "Standbild speichern"}, {"K oder F11", "Kinomodus"}, {"F1 oder H", "diese Übersicht"}, {"Esc", "schließen"}};
        int W = getWidth(), H = getHeight();
        int perCol = (rows.length + 1) / 2;
        int bw = 600, bh = 56 + perCol * 20 + 30;
        int x = (W - bw) / 2, y = Math.max(20, (H - bh) / 2);
        g.setColor(new Color(8, 14, 18, 215));
        g.fillRoundRect(x, y, bw, bh, 14, 14);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(INK);
        g.drawString("Tasten", x + 24, y + 34);
        for (int i = 0; i < rows.length; i++) {
            int cx = x + 24 + (i / perCol) * (bw / 2), cy = y + 62 + (i % perCol) * 20;
            if (rows[i][1] == null) {
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                g.setColor(SULFUR);
                g.drawString(rows[i][0], cx, cy);
            } else {
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.setColor(INK);
                g.drawString(rows[i][0], cx, cy);
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(MUTED);
                g.drawString(rows[i][1], cx + 118, cy);
            }
        }
        g.setFont(new Font("SansSerif", Font.ITALIC, 11));
        g.setColor(MUTED);
        g.drawString("Farbstil und Qualität stehen im Bedienfeld unter BILD, Datenbank und Zustände unter DATENBANK.", x + 24, y + bh - 16);
    }
}
