package com.dan.geyser.ui;

import java.io.File;
import java.util.Locale;
import java.util.Properties;

/**
 * Rätsel „Wann bricht er aus?“: Nach einem Ausbruch von Old Faithful tippt man, wann der nächste
 * beginnt, so wie die Ranger es aus der Dauer des letzten tun. Bricht er aus, zeigt die App, wie weit
 * der Tipp und wie weit die Regel der Ranger danebenlagen. Die Bilanz (Anzahl, mittlere
 * Abweichung, bester Tipp, wie oft besser als die Ranger) steht in einer Datei im Benutzerordner.
 */
final class Guess {
    /** Offener Tipp: Geysir-Uhr des Tipps und der Ranger-Vorhersage (NaN: keiner). */
    volatile double tipAt = Double.NaN, rangerAt = Double.NaN;
    /** Ergebnis des letzten Tipps für die Tafel (null: keins) und bis wann es steht (ms). */
    volatile String[] result;
    volatile long resultUntil;
    int count, beat;
    double sumAbs, best = Double.NaN;

    boolean open() { return !Double.isNaN(tipAt); }

    /**
     * Fragt den Tipp ab (FDialog mit FTextfield) und gibt die Eingabe an done, bei Abbruch nichts.
     * lines: die Zeilen über dem Eingabefeld.
     */
    static void ask(java.awt.Component parent, String[] lines, java.util.function.Consumer<String> done) {
        com.dan.fDialog.FDialog d = new com.dan.fDialog.FDialog(javax.swing.SwingUtilities.getWindowAncestor(parent), "Wann bricht er aus?", true);
        javax.swing.JPanel p = d.getComponentPane();
        p.setLayout(new java.awt.BorderLayout(0, 12));
        p.setBackground(ControlPanel.BG);
        p.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 18, 14, 18));
        javax.swing.JPanel txt = new javax.swing.JPanel();
        txt.setOpaque(false);
        txt.setLayout(new javax.swing.BoxLayout(txt, javax.swing.BoxLayout.Y_AXIS));
        for (int i = 0; i < lines.length; i++) {
            javax.swing.JLabel l = new javax.swing.JLabel(lines[i]);
            l.setForeground(i == 0 ? ControlPanel.ACCENT : i < 3 ? ControlPanel.INK : ControlPanel.MUTED);
            l.setFont(new java.awt.Font("SansSerif", i == 0 ? java.awt.Font.BOLD : java.awt.Font.PLAIN, i == 0 ? 14 : 13));
            l.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 2, 0));
            txt.add(l);
        }
        p.add(txt, java.awt.BorderLayout.NORTH);
        com.dan.ftextfield.FTextfield field = new com.dan.ftextfield.FTextfield();
        field.setLabelText("Uhrzeit (10:45) oder +Minuten (+70)");
        p.add(field, java.awt.BorderLayout.CENTER);
        javax.swing.JPanel btn = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        btn.setOpaque(false);
        com.dan.fbutton.FButton ok = new com.dan.fbutton.FButton("Tippen"), cancel = new com.dan.fbutton.FButton("Abbrechen");
        Runnable accept = () -> { String v = field.getText(); d.dispose(); done.accept(v); };
        ok.addActionListener(e -> accept.run());
        field.addActionListener(e -> accept.run());
        cancel.addActionListener(e -> d.dispose());
        btn.add(ok);
        btn.add(cancel);
        p.add(btn, java.awt.BorderLayout.SOUTH);
        d.setPreferredDialogSize(new java.awt.Dimension(460, 300));
        d.pack();
        d.setLocationRelativeTo(parent);
        javax.swing.SwingUtilities.invokeLater(field::requestFocusInWindow);
        d.setVisible(true);
    }

    static File file() { return new File(new File(System.getProperty("user.home"), ".geyser"), "raetsel.properties"); }

    void load() {
        Properties p = new Properties();
        try (java.io.InputStream in = new java.io.FileInputStream(file())) { p.load(in); } catch (Exception e) { return; }
        try {
            count = Integer.parseInt(p.getProperty("count", "0"));
            beat = Integer.parseInt(p.getProperty("beat", "0"));
            sumAbs = Double.parseDouble(p.getProperty("sumAbs", "0"));
            best = Double.parseDouble(p.getProperty("best", "NaN"));
        } catch (Exception ignored) { }
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("count", String.valueOf(count));
        p.setProperty("beat", String.valueOf(beat));
        p.setProperty("sumAbs", String.valueOf(sumAbs));
        p.setProperty("best", String.valueOf(best));
        try {
            File f = file();
            f.getParentFile().mkdirs();
            try (java.io.OutputStream o = new java.io.FileOutputStream(f)) { p.store(o, "Geyser · Rätsel „Wann bricht er aus?“"); }
        } catch (Exception e) {
            System.err.println("Rätsel nicht gespeichert: " + e);
        }
    }

    /**
     * Uhrzeit aus der Eingabe: „10:45“, „10.45“, „1045“ oder „+75“ (Minuten ab jetzt). Liefert die
     * Minuten ab jetzt (nächstes Vorkommen dieser Uhrzeit) oder NaN.
     */
    static double parse(String s, double hourNow) {
        if (s == null) return Double.NaN;
        s = s.trim().replace(" ", "").replace("Uhr", "");
        try {
            if (s.startsWith("+")) return Double.parseDouble(s.substring(1).replace(',', '.'));
            int h, m;
            if (s.contains(":") || s.contains(".")) {
                String[] q = s.split("[:.]");
                h = Integer.parseInt(q[0]); m = Integer.parseInt(q[1]);
            } else if (s.length() >= 3) {
                h = Integer.parseInt(s.substring(0, s.length() - 2)); m = Integer.parseInt(s.substring(s.length() - 2));
            } else return Double.NaN;
            if (h < 0 || h > 23 || m < 0 || m > 59) return Double.NaN;
            double d = (h + m / 60.0) - hourNow;
            while (d < 0) d += 24;
            return d * 60;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    /** Ausbruch um start (Geysir-Uhr): Tipp auswerten; liefert die Zeilen für die Tafel. */
    String[] score(double start, String tipClock, String rangerClock, String startClock) {
        double dev = (start - tipAt) / 60, rdev = (start - rangerAt) / 60;
        tipAt = Double.NaN; rangerAt = Double.NaN;
        count++;
        sumAbs += Math.abs(dev);
        if (Double.isNaN(best) || Math.abs(dev) < best) best = Math.abs(dev);
        boolean better = Math.abs(dev) < Math.abs(rdev);
        if (better) beat++;
        save();
        String verdict = Math.abs(dev) <= 2 ? "Punktlandung!" : Math.abs(dev) <= 10 ? "So gut wie die Ranger (±10 min)" : "Daneben";
        return new String[]{
                "WANN BRICHT ER AUS? · " + verdict,
                String.format(Locale.GERMANY, "Ausbruch %s · dein Tipp %s: %s", startClock, tipClock, off(dev)),
                String.format(Locale.GERMANY, "Regel der Ranger %s: %s · %s", rangerClock, off(rdev), better ? "du warst näher" : "die Ranger waren näher"),
                String.format(Locale.GERMANY, "Bilanz: %d Tipps, im Mittel %.1f min daneben, bester %.1f min, %d-mal näher als die Ranger", count, sumAbs / count, best, beat)};
    }

    static String off(double min) {
        if (Math.abs(min) < 0.5) return "auf die Minute";
        return String.format(Locale.GERMANY, "%.0f min %s", Math.abs(min), min > 0 ? "zu früh getippt" : "zu spät getippt");
    }
}
