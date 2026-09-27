package com.dan.geyser.effects;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Klang des Beckens, ohne Tondateien Probe für Probe gerechnet (Aufbau aus Semiramis), in Stereo:
 * das Tosen einer Säule, das Zischen der Dampfphase, das Schwappen im Vorspiel, Blasen in heißen
 * Quellen, der Firehole, Wind, im Herbst das Röhren der Wapitibullen (Brunft Anfang September bis
 * Mitte Oktober, NPS) und im Sommer das Brüllen der Bisons (Brunft Juli und August, NPS). Die
 * Bildschleife setzt die Pegel und die Richtung nach dem Ort der Kamera.
 */
public final class GeyserSound {
    public static final int RATE = 22050;

    /** Pegel 0..1 und Richtung −1 (links) .. 1 (rechts), von der Bildschleife gesetzt. */
    public volatile float roar, roarPan, hiss, splash, boil, boilPan, river, wind, master = 1.6f;
    /** Ruf eines Wapitibullen oder Bisons auslösen: Pegel und Richtung. */
    private volatile float bugleReq = -1, buglePan, bellowReq = -1, bellowPan;

    private volatile boolean running;
    private SourceDataLine line;

    private int seed = 4711;
    private float lp1, lp2, lp3, lp4, lp5, lp6, hp1, gust, gustT, surge, surgeT;
    private float spEnv, spLp, spT;
    private final double[] bubPh = new double[6], bubF = new double[6], bubEnv = new double[6];
    private float bubT;
    private double bgT = -1, bgPh, bgPh2, bgLevel, bgPan;
    private double blT = -1, blPh, blLevel, blPan;

    public void bugle(float level, float pan) { buglePan = pan; bugleReq = level; }
    public void bellow(float level, float pan) { bellowPan = pan; bellowReq = level; }

    /** Öffnet den Tonausgang und startet den Klang; false, wenn es keinen gibt. */
    public synchronized boolean start() {
        if (running) return true;
        try {
            AudioFormat f = new AudioFormat(RATE, 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(f);
            line.open(f, 4096 * 4);
            line.start();
        } catch (Exception | Error e) {
            line = null;
            return false;
        }
        running = true;
        Thread t = new Thread(this::loop, "Geyser-Klang");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public synchronized void stop() {
        running = false;
        if (line != null) {
            try { line.stop(); line.flush(); line.close(); } catch (Exception ignored) { }
            line = null;
        }
    }

    public boolean running() { return running; }

    private void loop() {
        short[] s = new short[2048];
        byte[] b = new byte[4096];
        while (running) {
            synth(s, 1024);
            for (int i = 0; i < 2048; i++) { b[2 * i] = (byte) s[i]; b[2 * i + 1] = (byte) (s[i] >> 8); }
            SourceDataLine l = line;
            if (l == null) break;
            l.write(b, 0, b.length);
        }
    }

    private float noise() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFF) / 32768f - 1f;
    }

    private float rnd() { return (noise() + 1) * 0.5f; }

    /** Rechnet n Stereoproben nach out (L, R abwechselnd), auch für die Prüfung ohne Tonausgang. */
    public void synth(short[] out, int n) {
        final float dt = 1f / RATE;
        float ro = roar, rp = roarPan, hi = hiss, spl = splash, bo = boil, bp = boilPan, rv = river, wi = wind, ms = master;
        if (bugleReq >= 0) { bgT = 0; bgLevel = bugleReq; bgPan = buglePan; bgPh = 0; bgPh2 = 0; bugleReq = -1; }
        if (bellowReq >= 0) { blT = 0; blLevel = bellowReq; blPan = bellowPan; blPh = 0; bellowReq = -1; }
        for (int i = 0; i < n; i++) {
            float w = noise();
            // Säule: tiefes Grollen und breites Rauschen, in langsamen Stößen
            lp1 += (w - lp1) * 0.035f;
            lp2 += (w - lp2) * 0.16f;
            surgeT -= dt;
            if (surgeT <= 0) { surgeT = 0.15f + 0.5f * rnd(); surge = 0.6f + 0.4f * rnd(); }
            float sg = gust;
            float rEnv = ro * (0.75f + 0.25f * surge);
            float sRoar = (lp1 * 3.2f + lp2 * 1.1f) * rEnv;
            // Dampfphase: helles Zischen
            lp3 += (w - lp3) * 0.45f;
            float sHiss = (w - lp3) * 0.5f * hi;
            // Vorspiel: einzelne Schwapper
            spT -= dt;
            if (spT <= 0 && spl > 0.01f) { spT = 0.25f + 1.2f * rnd(); spEnv = 0.6f + 0.4f * rnd(); }
            spEnv *= 0.99985f - 0.0004f;
            spLp += (w - spLp) * 0.09f;
            float sSplash = spLp * 2.2f * spEnv * spl;
            // Blasen: kurze, nach oben gleitende Töne
            float sBub = 0;
            if (bo > 0.01f) {
                bubT -= dt;
                if (bubT <= 0) {
                    bubT = (0.02f + 0.25f * rnd()) / (0.3f + bo);
                    for (int k = 0; k < bubPh.length; k++) if (bubEnv[k] < 0.01) { bubEnv[k] = 0.5 + 0.5 * rnd(); bubF[k] = 140 + 380 * rnd(); bubPh[k] = 0; break; }
                }
                for (int k = 0; k < bubPh.length; k++) {
                    if (bubEnv[k] < 0.01) continue;
                    bubF[k] *= 1.00012;
                    bubPh[k] += 2 * Math.PI * bubF[k] * dt;
                    sBub += (float) (Math.sin(bubPh[k]) * bubEnv[k]);
                    bubEnv[k] *= 0.9985;
                }
                sBub *= 0.35f * bo;
            }
            // Fluss: weiches Rauschen
            lp4 += (w - lp4) * 0.06f;
            lp5 += (w - lp5) * 0.22f;
            float sRiver = (lp4 * 1.4f + (lp5 - lp4) * 0.5f) * rv;
            // Wind in Böen
            gustT -= dt;
            if (gustT <= 0) { gustT = 1.5f + 3 * rnd(); }
            gust += ((0.4f + 0.6f * (float) Math.abs(Math.sin(gustT * 1.3f))) - gust) * 0.00005f;
            lp6 += (w - lp6) * (0.015f + 0.02f * sg);
            float sWind = lp6 * 3.0f * wi * sg;
            // Wapitibulle: tiefer Ansatz, Anstieg zum hohen Pfeifen, Abfall, dann Grunzer
            float sBug = 0;
            if (bgT >= 0) {
                double t = bgT;
                double f, a;
                if (t < 0.45) { f = 380 * Math.pow(1500 / 380.0, t / 0.45); a = t / 0.45; }
                else if (t < 1.7) { f = 1500 + 70 * Math.sin((t - 0.45) * 2 * Math.PI * 5.5) + 120 * (t - 0.45) / 1.25; a = 1; }
                else if (t < 2.1) { f = 1620 - 1100 * (t - 1.7) / 0.4; a = 1 - (t - 1.7) / 0.4 * 0.7; }
                else { f = 110; a = 0; }
                bgPh += 2 * Math.PI * f * dt;
                double v = Math.sin(bgPh) * a * 0.55 + Math.sin(2 * bgPh) * a * 0.18 + noise() * a * 0.05;
                // Grunzer
                for (int g = 0; g < 3; g++) {
                    double s0 = 2.4 + g * 0.42;
                    if (t > s0 && t < s0 + 0.16) {
                        bgPh2 += 2 * Math.PI * 105 * dt;
                        double e = Math.sin(Math.PI * (t - s0) / 0.16);
                        v += ((bgPh2 / Math.PI) % 2 - 1) * e * 0.45;
                    }
                }
                sBug = (float) (v * bgLevel);
                bgT += dt;
                if (bgT > 3.8) bgT = -1;
            }
            // Bison: tiefes, raues Brüllen
            float sBel = 0;
            if (blT >= 0) {
                double t = blT, f = 78 + 10 * Math.sin(t * 3.1);
                blPh += 2 * Math.PI * f * dt;
                double e = Math.sin(Math.PI * Math.min(1, t / 1.4));
                double saw = (blPh / Math.PI) % 2 - 1;
                sBel = (float) ((saw * 0.6 + noise() * 0.25) * e * blLevel);
                blT += dt;
                if (blT > 1.4) blT = -1;
            }
            // Mischen mit Richtung (gleiche Leistung)
            float c = sRiver + sWind;
            float rl = (float) Math.cos((rp + 1) * Math.PI / 4), rr = (float) Math.sin((rp + 1) * Math.PI / 4);
            float bl = (float) Math.cos((bp + 1) * Math.PI / 4), br = (float) Math.sin((bp + 1) * Math.PI / 4);
            float gl = (float) Math.cos((bgPan + 1) * Math.PI / 4), gr = (float) Math.sin((bgPan + 1) * Math.PI / 4);
            float nl = (float) Math.cos((blPan + 1) * Math.PI / 4), nr = (float) Math.sin((blPan + 1) * Math.PI / 4);
            float g = sRoar + sHiss + sSplash;
            float L = (c * 0.7071f + g * rl + sBub * bl + sBug * gl + sBel * nl) * ms;
            float R = (c * 0.7071f + g * rr + sBub * br + sBug * gr + sBel * nr) * ms;
            out[2 * i] = (short) (Math.tanh(L) * 30000);
            out[2 * i + 1] = (short) (Math.tanh(R) * 30000);
        }
    }
}
