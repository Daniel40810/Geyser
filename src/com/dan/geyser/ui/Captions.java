package com.dan.geyser.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/** Tafeln des Drehbuchs, für das Fenster und den Film ohne Fenster (ohne Bezug auf Swing-Komponenten). */
public final class Captions {
    private Captions() { }

    static final Color INK = new Color(232, 234, 228), SULFUR = new Color(226, 190, 72);

    /**
     * Tafel des Drehbuchs (Überschrift, Text, Quelle, Deckkraft in c) links unten, bottom Pixel über
     * dem unteren Rand; auch für den Film ohne Fenster ({@link com.dan.geyser.tools.FilmRender}).
     */
    public static void draw(Graphics2D g, Object[] c, int W, int H, int bottom) {
        String head = (String) c[0], text = (String) c[1], src = (String) c[2];
        float a = (float) Math.max(0, Math.min(1, (Double) c[3]));
        if (a <= 0.01) return;
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
        int x = 24, y = H - bh - bottom;
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

}
