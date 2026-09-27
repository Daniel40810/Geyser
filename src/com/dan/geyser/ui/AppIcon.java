package com.dan.geyser.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol, gemalt statt geladen: eine Geysirsäule mit Dampfkrone über einem Sinterhügel,
 * davor die Farbringe einer heißen Quelle, dahinter ein Stück Regenbogen im Morgenhimmel. Liefert alle
 * üblichen Größen für Taskleiste, Titel und Alt+Tab.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) l.add(paint(s));
        return l;
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(l.get(l.size() - 1));
            }
        } catch (Exception | Error ignored) {
            // Windows nimmt ohnehin die Fenstersymbole
        }
    }

    /** Malt das Symbol in der Kantenlänge s (quadratisch, mit Alpha). */
    public static BufferedImage paint(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(s / 256.0, s / 256.0);
        boolean small = s <= 24;
        RoundRectangle2D tile = new RoundRectangle2D.Double(8, 8, 240, 240, 56, 56);
        // Morgenhimmel
        g.setPaint(new GradientPaint(0, 8, new Color(22, 58, 104), 0, 200, new Color(126, 170, 204)));
        g.fill(tile);
        g.setClip(tile);
        if (!small) {
            // Regenbogen hinter der Säule
            Color[] rb = {new Color(236, 84, 64), new Color(246, 166, 60), new Color(244, 222, 92), new Color(96, 190, 110), new Color(70, 132, 220), new Color(124, 96, 196)};
            g.setStroke(new BasicStroke(5f));
            for (int i = 0; i < rb.length; i++) {
                g.setColor(new Color(rb[i].getRed(), rb[i].getGreen(), rb[i].getBlue(), 120));
                double r = 118 - i * 5;
                g.draw(new Arc2D.Double(150 - r, 196 - r, 2 * r, 2 * r, 30, 120, Arc2D.OPEN));
            }
        }
        // Wald am Horizont
        g.setColor(new Color(24, 48, 38));
        Path2D forest = new Path2D.Double();
        forest.moveTo(8, 186);
        for (int x = 8; x <= 248; x += 12) { forest.lineTo(x + 6, 164 + (x * 7 % 13)); forest.lineTo(x + 12, 186); }
        forest.lineTo(248, 248); forest.lineTo(8, 248); forest.closePath();
        g.fill(forest);
        // Sinterboden
        g.setPaint(new GradientPaint(0, 180, new Color(200, 198, 186), 0, 248, new Color(232, 228, 214)));
        g.fill(new Ellipse2D.Double(-40, 176, 336, 120));
        // Quelle vorn links: Farbringe
        Color[] ring = {new Color(150, 64, 30), new Color(222, 118, 36), new Color(236, 196, 60), new Color(120, 206, 206), new Color(22, 110, 170)};
        for (int i = 0; i < ring.length; i++) {
            double w = 120 - i * 20, h = 34 - i * 5.6;
            g.setColor(ring[i]);
            g.fill(new Ellipse2D.Double(70 - w / 2, 222 - h / 2, w, h));
        }
        // Säule: unten schmal, oben breit, mit weichem Rand
        Path2D col = new Path2D.Double();
        col.moveTo(163, 196);
        col.curveTo(158, 150, 146, 96, 138, 58);
        col.lineTo(186, 58);
        col.curveTo(178, 96, 172, 150, 175, 196);
        col.closePath();
        g.setPaint(new GradientPaint(0, 58, new Color(255, 255, 255, 235), 0, 196, new Color(236, 242, 246, 250)));
        g.fill(col);
        // Dampfkrone
        double[][] puffs = small ? new double[][]{{162, 54, 40}} : new double[][]{{140, 60, 30}, {164, 44, 38}, {190, 58, 28}, {176, 30, 26}, {150, 36, 24}, {205, 44, 20}};
        for (double[] p : puffs) {
            g.setPaint(new RadialGradientPaint((float) p[0], (float) p[1], (float) p[2],
                    new float[]{0f, 0.7f, 1f}, new Color[]{new Color(255, 255, 255, 250), new Color(240, 244, 248, 200), new Color(240, 244, 248, 0)}));
            g.fill(new Ellipse2D.Double(p[0] - p[2], p[1] - p[2], 2 * p[2], 2 * p[2]));
        }
        // Sinterkegel am Fuß
        g.setColor(new Color(214, 210, 196));
        g.fill(new Ellipse2D.Double(140, 186, 58, 20));
        g.setClip(null);
        if (!small) {
            g.setColor(new Color(226, 190, 72));
            g.setStroke(new BasicStroke(5f));
            g.draw(new RoundRectangle2D.Double(10.5, 10.5, 235, 235, 53, 53));
        }
        g.dispose();
        return img;
    }
}
