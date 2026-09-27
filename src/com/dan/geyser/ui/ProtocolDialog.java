package com.dan.geyser.ui;

import com.dan.fDialog.FDialog;
import com.dan.fbutton.FButton;
import com.dan.ftable.FTable;
import com.dan.ftable.FTableDensity;
import com.dan.ftable.FTableStyle;
import com.dan.geyser.effects.DayNightCycle;
import com.dan.geyser.world.Geysers;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eruptionsprotokoll dieser Sitzung: jeder beendete Ausbruch mit Beginn (Szenenzeit), Dauer,
 * größter Höhe, Abstand zum vorigen und der Vorhersage davor. Darunter je Geysir, wie gut die
 * Vorhersage traf (bei Old Faithful: Anteil innerhalb von ±10 Minuten wie bei den Rangern). Von Hand
 * ausgelöste Ausbrüche stehen mit drin, zählen aber nicht für die Vorhersage. Ab der Datenbank
 * landet das Protokoll auch dort; bis dahin lebt es nur, solange die App läuft.
 */
final class ProtocolDialog {
    /** Ein Eintrag: der Ausbruch und Tag und Uhrzeit der Szene bei seinem Beginn. */
    static final class Entry {
        final Geysers.Eruption e;
        final int day;
        final double hour;

        Entry(Geysers.Eruption e, int day, double hour) { this.e = e; this.day = day; this.hour = hour; }
    }

    private ProtocolDialog() { }

    /**
     * Protokoll aller Sitzungen aus der Datenbank: Ausbrüche (GEY_ERUPTION_V, neueste zuerst) und die
     * Güte der Vorhersage je Geysir (GEY_PREDICTION_V).
     */
    static void showDb(Component parent, List<Object[]> er, List<Object[]> stats) {
        FDialog d = new FDialog(SwingUtilities.getWindowAncestor(parent), "Eruptionsprotokoll · alle Sitzungen", false);
        JPanel p = d.getComponentPane();
        p.setLayout(new BorderLayout(0, 10));
        p.setBackground(ControlPanel.BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        String[] cols = {"Geysir", "Szene", "Dauer", "Höhe", "Abstand", "Abweichung", "Sitzung"};
        Object[][] rows = new Object[er.size()][];
        java.text.SimpleDateFormat sf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
        for (int i = 0; i < er.size(); i++) {
            Object[] r = er.get(i);
            double dur = num(r[3]), h = num(r[4]), iv = num(r[5]), dev = num(r[7]);
            rows[i] = new Object[]{
                    r[0] + ("J".equals(r[8]) ? " (von Hand)" : ""),
                    DayNightCycle.dateLabel((int) num(r[1])) + "  " + DayNightCycle.timeLabel(num(r[2])),
                    String.format("%d:%02d min", (int) dur / 60, (int) dur % 60),
                    Double.isNaN(h) ? "–" : String.format(java.util.Locale.GERMANY, "%.0f m", h),
                    Double.isNaN(iv) ? "–" : String.format(java.util.Locale.GERMANY, "%.0f min", iv / 60),
                    Double.isNaN(dev) ? "–" : String.format(java.util.Locale.GERMANY, "%+.1f min", dev),
                    r[9] instanceof java.util.Date ? sf.format((java.util.Date) r[9]) : String.valueOf(r[9])};
        }
        FTable t = new FTable();
        t.setStyle(FTableStyle.LAB_DARK);
        t.setDensity(FTableDensity.COMPACT);
        t.setData(cols, rows);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(ControlPanel.BG);
        sp.setVerticalScrollBar(new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL));
        p.add(sp, BorderLayout.CENTER);
        StringBuilder b = new StringBuilder("<html><body style='width:780px'><b>Güte der Vorhersage über alle Sitzungen</b> (GEY_PREDICTION_V)<br>");
        for (Object[] r : stats) {
            if (num(r[1]) <= 0) continue;
            b.append(r[0]).append(": ").append((int) num(r[1])).append(" Ausbrüche");
            if (num(r[2]) > 0) b.append(String.format(java.util.Locale.GERMANY, ", im Mittel %.1f min daneben, %.0f %% innerhalb ±10 min", num(r[3]), num(r[4])));
            if (!Double.isNaN(num(r[5]))) b.append(String.format(java.util.Locale.GERMANY, ", Abstände %.0f bis %.0f min", num(r[5]), num(r[6])));
            b.append("<br>");
        }
        if (er.isEmpty()) b.append("Noch keine Ausbrüche in der Datenbank.");
        b.append("</body></html>");
        JLabel sum = new JLabel(b.toString());
        sum.setForeground(ControlPanel.INK);
        sum.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(sum, BorderLayout.CENTER);
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btn.setOpaque(false);
        FButton close = new FButton("Schließen");
        close.addActionListener(ev -> d.dispose());
        btn.add(close);
        south.add(btn, BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);
        d.setPreferredDialogSize(new Dimension(960, 600));
        d.setResizable(true);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }

    static double num(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : Double.NaN; }

    static void show(Component parent, List<Entry> entries) {
        List<Entry> list;
        synchronized (entries) { list = new ArrayList<>(entries); }
        FDialog d = new FDialog(SwingUtilities.getWindowAncestor(parent), "Eruptionsprotokoll", false);
        JPanel p = d.getComponentPane();
        p.setLayout(new BorderLayout(0, 10));
        p.setBackground(ControlPanel.BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        String[] cols = {"Geysir", "Beginn", "Dauer", "Höhe", "Abstand", "Vorhersage", "Abweichung"};
        Object[][] rows = new Object[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            Entry en = list.get(list.size() - 1 - i);
            Geysers.Eruption e = en.e;
            double dev = Double.isNaN(e.predicted) ? Double.NaN : (e.start - e.predicted) / 60;
            double predHour = en.hour - dev / 60;
            rows[i] = new Object[]{
                    e.geyser + (e.manual ? " (von Hand)" : ""),
                    DayNightCycle.dateLabel(en.day) + "  " + DayNightCycle.timeLabel(en.hour),
                    String.format("%d:%02d min", (int) e.duration / 60, (int) e.duration % 60),
                    String.format(java.util.Locale.GERMANY, "%.0f m", e.maxHeight),
                    Double.isNaN(e.interval) ? "–" : String.format(java.util.Locale.GERMANY, "%.0f min", e.interval / 60),
                    Double.isNaN(e.predicted) ? "–" : DayNightCycle.timeLabel(((predHour % 24) + 24) % 24),
                    Double.isNaN(dev) ? "–" : String.format(java.util.Locale.GERMANY, "%+.1f min", dev)};
        }
        FTable t = new FTable();
        t.setStyle(FTableStyle.LAB_DARK);
        t.setDensity(FTableDensity.COMPACT);
        t.setData(cols, rows);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(ControlPanel.BG);
        com.dan.fscrollbar.FScrollBar sb = new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL);
        sp.setVerticalScrollBar(sb);
        p.add(sp, BorderLayout.CENTER);

        // Güte der Vorhersage je Geysir
        Map<String, double[]> st = new LinkedHashMap<>();
        for (Entry en : list) {
            double[] a = st.computeIfAbsent(en.e.geyser, k -> new double[4]);
            a[0]++;
            if (!Double.isNaN(en.e.predicted)) {
                double dev = Math.abs(en.e.start - en.e.predicted) / 60;
                a[1]++; a[2] += dev;
                if (dev <= 10) a[3]++;
            }
        }
        StringBuilder b = new StringBuilder("<html><body style='width:760px'>");
        if (list.isEmpty()) b.append("Noch kein Ausbruch in dieser Sitzung. „Warten abkürzen“ (V) läuft bis zum nächsten.");
        for (Map.Entry<String, double[]> m : st.entrySet()) {
            double[] a = m.getValue();
            b.append("<b>").append(m.getKey()).append("</b>: ").append((int) a[0]).append(a[0] == 1 ? " Ausbruch" : " Ausbrüche");
            if (a[1] > 0) b.append(String.format(java.util.Locale.GERMANY, ", Vorhersage im Mittel %.1f min daneben, %d von %d innerhalb ±10 min",
                    a[2] / a[1], (int) a[3], (int) a[1]));
            b.append(" &nbsp;·&nbsp; ");
        }
        b.append("<br><span style='color:#8e9a9c'>Nur diese Sitzung; alle Sitzungen stehen mit Datenbank unter DATENBANK · Protokoll aller Sitzungen.</span></body></html>");
        JLabel sum = new JLabel(b.toString());
        sum.setForeground(ControlPanel.INK);
        sum.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(sum, BorderLayout.CENTER);
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btn.setOpaque(false);
        FButton close = new FButton("Schließen");
        close.addActionListener(ev -> d.dispose());
        btn.add(close);
        south.add(btn, BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);
        d.setPreferredDialogSize(new Dimension(900, 560));
        d.setResizable(true);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }
}
