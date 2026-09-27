package com.dan.geyser.atom;

import com.dan.atommodel.view.FAtomLook;
import com.dan.atommodel.view.FAtomView;
import com.dan.fDialog.FDialog;
import com.dan.fbutton.FButton;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

/**
 * Die Mineral-Lupe: links die sechs Ablagerungen, in der Mitte Formel, Zusammensetzung nach
 * Masse, die beteiligten Elemente und der belegte Text, rechts das gewählte Element als animiertes
 * Atom aus dem ATOMMODEL ({@link FAtomView}) mit seinen Daten. Die Elementdaten kommen aus der
 * Datenbank (öffentliche View <code>am_element</code>) oder, ohne sie, aus der eingebauten Tabelle;
 * unten steht, welche Quelle gilt. Beim Kieselsinter startet ein Knopf den Zeitraffer am Kegel von
 * Castle Geyser in der Szene.
 */
public final class MineralLupe {
    static final Color BG = new Color(14, 20, 24), PANEL = new Color(20, 28, 34), INK = new Color(226, 230, 226),
            MUTED = new Color(142, 154, 156), SULFUR = new Color(226, 190, 72), POOL = new Color(98, 182, 226), LINE = new Color(44, 56, 64);

    private static MineralLupe open;

    private final FDialog dialog;
    private final String context;
    private volatile Map<Integer, Element> el = Elements.BUILTIN;
    private volatile String source = "eingebaut · Datenbank wird gefragt …";
    private Mineral cur;
    private int curEl;
    private final FAtomView atom = new FAtomView();
    private final Runnable timelapse;
    private final JComponent list, head, bar, chips, atomInfo, foot;
    private final JEditorPane text = new JEditorPane();
    private final FButton lapse = new FButton("Zeitraffer am Castle Geyser");

    /**
     * Öffnet die Lupe (oder holt die offene nach vorn) für den Ort context, mit dem Mineral
     * preselect; timelapse startet den Zeitraffer in der Szene.
     */
    public static void show(Window owner, String context, Mineral preselect, Runnable timelapse) {
        if (open != null && open.dialog.isDisplayable()) {
            open.select(preselect == null ? open.cur : preselect);
            open.dialog.toFront();
            return;
        }
        open = new MineralLupe(owner, context, preselect == null ? Minerals.ALL[0] : preselect, timelapse);
    }

    private MineralLupe(Window owner, String context, Mineral first, Runnable timelapse) {
        this.context = context;
        this.timelapse = timelapse;
        dialog = new FDialog(owner, "Mineral-Lupe", false);
        JPanel p = dialog.getComponentPane();
        p.setLayout(new BorderLayout(14, 0));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        list = new ListView();
        list.setPreferredSize(new Dimension(250, 560));
        p.add(list, BorderLayout.WEST);

        JPanel mid = new JPanel(new BorderLayout(0, 8));
        mid.setOpaque(false);
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
        head = new Head();
        bar = new Bar();
        chips = new Chips();
        for (JComponent c : new JComponent[]{head, bar, chips}) { c.setAlignmentX(0); top.add(c); }
        mid.add(top, BorderLayout.NORTH);
        text.setContentType("text/html");
        text.setEditable(false);
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 6));
        text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        text.setFont(new Font("SansSerif", Font.PLAIN, 13));
        text.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED || e.getURL() == null) return;
            try { java.awt.Desktop.getDesktop().browse(e.getURL().toURI()); } catch (Exception ex) { /* kein Browser */ }
        });
        JScrollPane sp = new JScrollPane(text);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
        com.dan.fscrollbar.FScrollBar sb = new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL);
        sp.setVerticalScrollBar(sb);
        mid.add(sp, BorderLayout.CENTER);
        p.add(mid, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        atom.setPreferredSize(new Dimension(360, 360));
        atom.setLook(FAtomLook.DEEP_SPACE);
        atom.setAutoRotateSpeed(0.25);
        right.add(atom, BorderLayout.NORTH);
        atomInfo = new AtomInfo();
        right.add(atomInfo, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        foot = new Foot();
        south.add(foot, BorderLayout.CENTER);
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btn.setOpaque(false);
        lapse.addActionListener(e -> { if (this.timelapse != null) this.timelapse.run(); });
        FButton close = new FButton("Schließen");
        close.addActionListener(e -> dialog.dispose());
        btn.add(lapse);
        btn.add(close);
        south.add(btn, BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);

        dialog.setPreferredDialogSize(new Dimension(1180, 720));
        dialog.setResizable(true);
        select(first);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);

        Thread t = new Thread(() -> {
            Elements.Result r = Elements.load();
            SwingUtilities.invokeLater(() -> {
                el = r.map;
                source = r.source;
                select(cur, curEl);
                dialog.repaint();
            });
        }, "Geyser-Elemente");
        t.setDaemon(true);
        t.start();
    }

    private void select(Mineral m) { select(m, m == cur ? curEl : 0); }

    private void select(Mineral m, int elIndex) {
        cur = m;
        curEl = Math.max(0, Math.min(m.z.length - 1, elIndex));
        Element e = el.get(m.z[curEl]);
        atom.setAtom(e.z, e.neutrons, e.shells);
        lapse.setVisible(m.local && timelapse != null);
        text.setText(html(m));
        text.setCaretPosition(0);
        for (JComponent c : new JComponent[]{list, head, bar, chips, atomInfo, foot}) c.repaint();
    }

    private String html(Mineral m) {
        StringBuilder b = new StringBuilder("<html><body style='color:#e2e6e2;font-family:SansSerif;font-size:12pt;margin:6px 2px'>");
        b.append("<p style='margin-top:4px'>").append(m.text).append("</p>");
        if (m.traces.length > 0) {
            b.append("<p style='color:#8e9a9c'>Spuren: ");
            for (int i = 0; i < m.traces.length; i++) {
                Element e = el.get(m.traces[i]);
                b.append(i > 0 ? ", " : "").append(e.name).append(" (").append(e.symbol).append(')');
            }
            b.append("</p>");
        }
        b.append("<p style='color:#e2be48;font-size:10pt;margin-bottom:2px'><b>KENNZAHLEN</b></p><ul style='margin-top:0;margin-left:16px'>");
        for (String f : m.facts) b.append("<li style='margin-bottom:3px'>").append(f).append("</li>");
        b.append("</ul><p style='color:#e2be48;font-size:10pt;margin-bottom:2px'><b>QUELLEN</b></p><p style='margin-top:0;font-size:10pt'>");
        for (String s : m.sources) {
            String[] q = s.split("\\|");
            b.append("<a style='color:#62b6e2' href='").append(q[1]).append("'>").append(q[0]).append("</a><br>");
        }
        b.append("</p></body></html>");
        return b.toString();
    }

    private static Graphics2D g2(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    private static String pct(double v) {
        return String.format(java.util.Locale.GERMANY, v >= 0.1 ? "%.1f %%" : "%.2f %%", v * 100);
    }

    // ------------------------------------------------------------ Liste der Ablagerungen

    private final class ListView extends JComponent {
        static final int ROW = 78;

        ListView() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int i = (e.getY() - 34) / ROW;
                    if (i >= 0 && i < Minerals.ALL.length) select(Minerals.ALL[i], 0);
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = g2(g0);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.setColor(SULFUR);
            g.drawString("ABLAGERUNGEN", 4, 16);
            g.setColor(MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(context, 4, 30);
            for (int i = 0; i < Minerals.ALL.length; i++) {
                Mineral m = Minerals.ALL[i];
                int y = 38 + i * ROW;
                boolean on = m == cur;
                g.setColor(on ? new Color(30, 42, 50) : PANEL);
                g.fillRoundRect(0, y, getWidth() - 2, ROW - 8, 12, 12);
                if (on) { g.setColor(SULFUR); g.fillRoundRect(0, y + 10, 3, ROW - 28, 3, 3); }
                // Farbfeld wie ein Mineralstück
                g.setPaint(new GradientPaint(14, y + 12, m.swatch.brighter(), 44, y + 42, m.swatch.darker()));
                g.fillOval(14, y + 14, 30, 30);
                g.setColor(new Color(255, 255, 255, 70));
                g.fillOval(20, y + 18, 10, 7);
                g.setColor(INK);
                g.setFont(new Font("SansSerif", Font.BOLD, 13));
                g.drawString(m.name, 56, y + 22);
                g.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g.setColor(new Color(200, 210, 206));
                g.drawString(m.formula, 56, y + 41);
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(m.local ? SULFUR : MUTED);
                g.drawString(m.local ? "hier im Becken" : "zum Vergleich · " + shortPlace(m), 56, y + 58);
            }
        }

        private String shortPlace(Mineral m) {
            if (m.place.contains("Mammoth")) return "Mammoth";
            if (m.place.contains("Norris")) return "Norris";
            return "saure Zonen";
        }
    }

    // ------------------------------------------------------------ Kopf mit Formel

    private final class Head extends JComponent {
        Head() { setPreferredSize(new Dimension(480, 108)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 108)); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = g2(g0);
            Mineral m = cur;
            g.setColor(MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(m.kind.toUpperCase(java.util.Locale.GERMANY), 2, 16);
            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 26));
            g.drawString(m.name, 0, 48);
            g.setFont(new Font("Serif", Font.PLAIN, 30));
            g.setColor(new Color(236, 226, 196));
            g.drawString(m.formula, 0, 84);
            FontMetrics fm = g.getFontMetrics();
            int fx = fm.stringWidth(m.formula) + 18;
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(MUTED);
            double fmass = m.formulaMass(el);
            g.drawString(String.format(java.util.Locale.GERMANY, "Formelmasse %.1f u", fmass), fx, 80);
            g.setColor(m.local ? SULFUR : POOL);
            g.drawString(m.place, 2, 102);
        }
    }

    // ------------------------------------------------------------ Massenanteile

    private final class Bar extends JComponent {
        Bar() { setPreferredSize(new Dimension(480, 40)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 40)); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = g2(g0);
            Mineral m = cur;
            int w = getWidth() - 4, x = 0, h = 18, y = 8;
            g.setColor(PANEL);
            g.fillRoundRect(0, y, w, h, 9, 9);
            java.awt.Shape clip = new java.awt.geom.RoundRectangle2D.Float(0, y, w, h, 9, 9);
            g.setClip(clip);
            double acc = 0;
            for (int i = 0; i < m.z.length; i++) {
                double s = m.massShare(i, el);
                int x0 = (int) Math.round(acc * w), x1 = (int) Math.round((acc + s) * w);
                Color c = el.get(m.z[i]).color;
                g.setPaint(new GradientPaint(0, y, c.brighter(), 0, y + h, c.darker()));
                g.fillRect(x0, y, x1 - x0, h);
                if (i == curEl) { g.setColor(new Color(255, 255, 255, 90)); g.fillRect(x0, y, x1 - x0, h); }
                if (x1 - x0 > 60) {
                    g.setFont(new Font("SansSerif", Font.BOLD, 11));
                    g.setColor(lum(c) > 0.55 ? new Color(20, 24, 28) : Color.WHITE);
                    g.drawString(el.get(m.z[i]).symbol + "  " + pct(s), x0 + 6, y + 13);
                }
                acc += s;
            }
            g.setClip(null);
            g.setColor(MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.drawString("Anteil an der Masse", 2, y + h + 12);
        }
    }

    static double lum(Color c) { return (0.3 * c.getRed() + 0.59 * c.getGreen() + 0.11 * c.getBlue()) / 255.0; }

    // ------------------------------------------------------------ Elemente zum Anklicken

    private final class Chips extends JComponent {
        static final int W = 112, H = 64;

        Chips() {
            setPreferredSize(new Dimension(480, H + 16));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, H + 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int i = e.getX() / (W + 8);
                    if (i >= 0 && i < cur.z.length && e.getY() > 6 && e.getY() < 6 + H) select(cur, i);
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = g2(g0);
            Mineral m = cur;
            for (int i = 0; i < m.z.length; i++) {
                Element e = el.get(m.z[i]);
                int x = i * (W + 8), y = 6;
                boolean on = i == curEl;
                g.setColor(on ? new Color(30, 42, 50) : PANEL);
                g.fillRoundRect(x, y, W, H, 12, 12);
                g.setColor(on ? SULFUR : LINE);
                g.setStroke(new BasicStroke(on ? 1.6f : 1f));
                g.drawRoundRect(x, y, W, H, 12, 12);
                g.setPaint(new GradientPaint(x + 10, y + 10, e.color.brighter(), x + 44, y + 44, e.color.darker()));
                g.fillOval(x + 10, y + 12, 38, 38);
                g.setColor(lum(e.color) > 0.55 ? new Color(20, 24, 28) : Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 15));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(e.symbol, x + 29 - fm.stringWidth(e.symbol) / 2, y + 36);
                g.setColor(INK);
                g.setFont(new Font("SansSerif", Font.PLAIN, 11));
                double c = m.count[i];
                String cnt = Math.abs(c - Math.rint(c)) < 1e-6 ? String.valueOf((int) Math.rint(c)) : String.format(java.util.Locale.GERMANY, "%.2f", c);
                g.drawString("× " + cnt, x + 56, y + 26);
                g.setColor(MUTED);
                g.drawString(pct(m.massShare(i, el)), x + 56, y + 44);
            }
        }
    }

    // ------------------------------------------------------------ Daten zum Atom

    private final class AtomInfo extends JComponent {
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = g2(g0);
            Element e = el.get(cur.z[curEl]);
            int y = 18;
            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString(e.name + "  " + e.symbol, 4, y + 6);
            y += 30;
            String[][] rows = {
                    {"Ordnungszahl", String.valueOf(e.z)},
                    {"Atommasse", String.format(java.util.Locale.GERMANY, "%.3f u", e.mass)},
                    {"Kern", e.z + " Protonen, " + e.neutrons + " Neutronen"},
                    {"Schalen", e.shellText()},
                    {"Konfiguration", e.config == null ? "–" : e.config},
                    {"Elektronegativität", e.en == null ? "–" : String.format(java.util.Locale.GERMANY, "%.2f", e.en)},
                    {"Gruppe", e.category == null ? "–" : e.category}};
            for (String[] r : rows) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(MUTED);
                g.drawString(r[0], 4, y);
                g.setColor(INK);
                g.drawString(r[1], 136, y);
                y += 20;
            }
            g.setFont(new Font("SansSerif", Font.ITALIC, 11));
            g.setColor(e.fromDb ? POOL : MUTED);
            g.drawString(e.fromDb ? "aus ATOMMODEL (am_element)" : "eingebaute Werte", 4, y + 6);
            g.setColor(MUTED);
            g.drawString("Atom ziehen: drehen · Rad: Zoom · Doppelklick: Aufbau", 4, y + 24);
        }
    }

    private final class Foot extends JComponent {
        Foot() { setPreferredSize(new Dimension(400, 30)); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = g2(g0);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(MUTED);
            g.drawString("Elementdaten: " + source, 2, 18);
        }
    }
}
