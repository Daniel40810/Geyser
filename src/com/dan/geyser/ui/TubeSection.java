package com.dan.geyser.ui;

import com.dan.geyser.world.GeyserModel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/**
 * Schnitt durch die Röhre eines Geysirs, als Tafel über dem Bild: links Kegel, Röhre und
 * Wassersäule mit der Temperatur als Farbe, Blasen, wo das Wasser siedet; rechts die Kurven über
 * der Tiefe, der Siedepunkt nach dem Druck der Säule darüber (Antoine-Gleichung) und die
 * Wassertemperatur. Der Siedepunkt am Grund und die Grundtemperatur kommen aus dem Röhrenmodell;
 * dazwischen ist der Temperaturverlauf linear genähert.
 */
final class TubeSection {
    static final int W = 380, H = 430;
    private TubeSection() { }

    static void paint(Graphics2D g, GeyserModel m, int x0, int y0, double time) {
        java.awt.Shape oldClip = g.getClip();
        g.setColor(new Color(8, 14, 18, 215));
        g.fillRoundRect(x0, y0, W, H, 12, 12);
        g.setColor(ScenePanel.SULFUR);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("SCHNITT · " + m.name.toUpperCase(java.util.Locale.GERMANY), x0 + 14, y0 + 20);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(ScenePanel.MUTED);
        g.drawString(phaseText(m), x0 + 14, y0 + 36);

        double D = m.tubeDepth, fill = m.fill, drop = m.surgeDrop();
        double dSurf = Math.max(0, D * (1 - fill)) + drop;               // Tiefe des Wasserspiegels unter dem Schlot
        double tSurf = GeyserModel.boilingPoint(GeyserModel.P_ATM);
        double tBot = m.tBottom;
        double bBot = m.boilAtBottom(fill, drop);

        // ------------------------------------------------ Querschnitt
        int sx = x0 + 18, sy = y0 + 70, sw = 150, sh = 300;
        double k = sh / (D + 4);                                           // Pixel je Meter (4 m über dem Boden)
        int ground = sy + (int) (4 * k);
        g.setClip(sx, sy - 20, sw, sh + 30);
        // Himmel und Luft
        g.setColor(new Color(24, 36, 48));
        g.fillRect(sx, sy - 20, sw, ground - sy + 20);
        // Gestein und Sinterdecke
        g.setPaint(new GradientPaint(0, ground, new Color(70, 58, 50), 0, sy + sh, new Color(40, 32, 28)));
        g.fillRect(sx, ground, sw, sh);
        g.setColor(new Color(200, 196, 184));
        g.fillRect(sx, ground, sw, 5);
        int cx = sx + sw / 2;
        int tube = 16;
        boolean cone = m.type == GeyserModel.Type.CONE;
        if (cone) {
            Path2D p = new Path2D.Double();
            int ch = (int) (1.6 * k);
            p.moveTo(cx - 55, ground + 1);
            p.curveTo(cx - 30, ground - ch * 0.4, cx - 18, ground - ch, cx - tube / 2.0 - 2, ground - ch);
            p.lineTo(cx + tube / 2.0 + 2, ground - ch);
            p.curveTo(cx + 18, ground - ch, cx + 30, ground - ch * 0.4, cx + 55, ground + 1);
            p.closePath();
            g.setColor(new Color(186, 180, 166));
            g.fill(p);
        } else {
            g.setColor(new Color(40, 90, 120));
            g.fillRoundRect(cx - 50, ground - 2, 100, 14, 12, 12);
        }
        // Röhre mit Engstellen
        Path2D tp = new Path2D.Double();
        int top = cone ? ground - (int) (1.6 * k) : ground + 6;
        tp.moveTo(cx - tube / 2.0, top);
        for (int i = 0; i <= 20; i++) {
            double d = i / 20.0 * D;
            double wobble = 1 + 0.35 * Math.sin(d * 0.9) - (Math.abs(d - D * 0.35) < 1.2 ? 0.35 : 0);
            tp.lineTo(cx - tube / 2.0 * wobble, ground + d * k);
        }
        for (int i = 20; i >= 0; i--) {
            double d = i / 20.0 * D;
            double wobble = 1 + 0.35 * Math.sin(d * 0.9 + 1.3) - (Math.abs(d - D * 0.35) < 1.2 ? 0.35 : 0);
            tp.lineTo(cx + tube / 2.0 * wobble, ground + d * k);
        }
        tp.closePath();
        g.setColor(new Color(16, 20, 24));
        g.fill(tp);
        // Wasser nach Temperatur
        java.awt.Shape clip2 = g.getClip();
        g.clip(tp);
        for (int py = (int) (ground + dSurf * k); py < ground + D * k + 2; py++) {
            double d = (py - ground) / k;
            double tw = tempAt(d, dSurf, D, tSurf, tBot);
            g.setColor(tempColor(tw));
            g.drawLine(cx - tube, py, cx + tube, py);
        }
        // Blasen, wo das Wasser siedet (oder im Ausbruch überall)
        boolean erupt = m.phase == GeyserModel.Phase.ERUPTION;
        for (int b = 0; b < 40; b++) {
            double seed = b * 12.9898;
            double speed = 20 + 30 * frac(Math.sin(seed) * 43758.5);
            double yy = (frac(time * speed / (D * k) + frac(Math.sin(seed * 1.7) * 9123.1))) ;
            double d = D - yy * (D - dSurf);
            double tw = tempAt(d, dSurf, D, tSurf, tBot);
            double tb = boilAt(d, dSurf);
            if (!erupt && tw < tb - 0.6) continue;
            double bx = cx + (frac(Math.sin(seed * 3.1) * 777.7) - 0.5) * tube * 0.9;
            int r = erupt ? 3 : 2;
            g.setColor(new Color(235, 245, 255, erupt ? 200 : 150));
            g.drawOval((int) bx - r, (int) (ground + d * k) - r, 2 * r, 2 * r);
        }
        g.setClip(clip2);
        // Säule über dem Schlot
        if (erupt || m.phase == GeyserModel.Phase.STEAM) {
            double h = Math.min(4, m.height(time) * 0.08);
            int hh = (int) Math.min(ground - sy + 18, h * k + 30);
            g.setPaint(new GradientPaint(0, top - hh, new Color(230, 236, 240, 0), 0, top, new Color(230, 236, 240, erupt ? 220 : 120)));
            g.fillRect(cx - tube / 2 - 3, top - hh, tube + 6, hh);
        }
        // Tiefenmaß
        g.setClip(oldClip);
        g.setColor(ScenePanel.MUTED);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (int d = 0; d <= (int) D; d += 5) {
            int py = (int) (ground + d * k);
            g.drawLine(sx + sw - 8, py, sx + sw - 2, py);
            g.drawString(d + " m", sx + sw - 38, py + 4);
        }

        // ------------------------------------------------ Kurven über der Tiefe
        int gx = x0 + 186, gw = 176;
        double tLo = 88, tHi = Math.max(140, Math.ceil(bBot / 5) * 5 + 5);
        g.setColor(new Color(255, 255, 255, 30));
        g.drawRect(gx, ground, gw, (int) (D * k));
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (int tt = 90; tt <= tHi; tt += 10) {
            int px = gx + (int) ((tt - tLo) / (tHi - tLo) * gw);
            g.setColor(new Color(255, 255, 255, 22));
            g.drawLine(px, ground, px, (int) (ground + D * k));
            g.setColor(ScenePanel.MUTED);
            g.drawString(tt + "°", px - 8, ground - 4);
        }
        Path2D boilCurve = new Path2D.Double(), waterCurve = new Path2D.Double();
        boolean firstW = true;
        for (int i = 0; i <= 60; i++) {
            double d = i / 60.0 * D;
            double tb = boilAt(d, dSurf);
            double px = gx + (tb - tLo) / (tHi - tLo) * gw, py = ground + d * k;
            if (i == 0) boilCurve.moveTo(px, py); else boilCurve.lineTo(px, py);
            if (d >= dSurf) {
                double tw = tempAt(d, dSurf, D, tSurf, tBot);
                double qx = gx + (tw - tLo) / (tHi - tLo) * gw;
                if (firstW) { waterCurve.moveTo(qx, py); firstW = false; } else waterCurve.lineTo(qx, py);
            }
        }
        g.setClip(gx - 2, ground - 2, gw + 4, (int) (D * k) + 4);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{5, 4}, 0));
        g.setColor(new Color(236, 240, 244));
        g.draw(boilCurve);
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(new Color(244, 150, 60));
        g.draw(waterCurve);
        g.setStroke(new BasicStroke(1));
        g.setClip(oldClip);
        int ly = (int) (ground + D * k) + 16;
        g.setColor(new Color(236, 240, 244));
        g.drawString("- - Siedepunkt", gx, ly);
        g.setColor(new Color(244, 150, 60));
        g.drawString("— Wasser", gx + 92, ly);

        // ------------------------------------------------ Zahlen
        double pBot = GeyserModel.P_ATM + 960 * 9.81 * Math.max(0, D - dSurf) / 1000;
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(ScenePanel.INK);
        int ty = y0 + H - 36;
        double gap = bBot - tBot;
        g.drawString(String.format(java.util.Locale.GERMANY, "Grund %.1f °C · siedet bei %.1f °C · %s %.1f K", tBot, bBot,
                gap >= 0 ? "fehlen" : "überhitzt", Math.abs(gap)), x0 + 14, ty);
        g.drawString(String.format(java.util.Locale.GERMANY, "Röhre %.0f m · Füllung %.0f %% · Druck am Grund %.0f kPa", D, fill * 100, pBot), x0 + 14, ty + 15);
        g.setFont(new Font("SansSerif", Font.ITALIC, 10));
        g.setColor(ScenePanel.MUTED);
        g.drawString("Temperatur zwischen Spiegel und Grund linear genähert", x0 + 14, ty + 28);
    }

    static double boilAt(double d, double dSurf) {
        double head = Math.max(0, d - dSurf);
        return GeyserModel.boilingPoint(GeyserModel.P_ATM + 960 * 9.81 * head / 1000);
    }

    static double tempAt(double d, double dSurf, double D, double tSurf, double tBot) {
        double u = D - dSurf < 0.01 ? 1 : Math.max(0, Math.min(1, (d - dSurf) / (D - dSurf)));
        return tSurf + (tBot - tSurf) * u;
    }

    static Color tempColor(double t) {
        double u = Math.max(0, Math.min(1, (t - 70) / 65));
        int r = (int) (40 + 215 * Math.min(1, u * 1.4)), gg = (int) (110 + 60 * Math.sin(u * Math.PI) - 60 * u), b = (int) (200 - 170 * u);
        return new Color(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, gg)), Math.max(0, Math.min(255, b)));
    }

    static double frac(double v) { return v - Math.floor(v); }

    static String phaseText(GeyserModel m) {
        switch (m.phase) {
            case RECHARGE: return "Füllt sich und heizt auf";
            case PREPLAY: return m.surgeDrop() > 0 ? "Vorspiel: Wasser schwappt über, der Druck fällt" : "Vorspiel: kurz vor dem Sieden";
            case ERUPTION: return "Ausbruch: das Wasser verdampft schlagartig";
            case STEAM: return "Dampfphase: die Röhre ist fast leer";
            default: return "";
        }
    }
}
