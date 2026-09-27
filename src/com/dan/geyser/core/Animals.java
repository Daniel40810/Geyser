package com.dan.geyser.core;

/**
 * Tiere für den Bildrechner: Bison und Wapiti als Schattenrisse in einer senkrechten Ebene entlang
 * der Laufrichtung, die sich so weit zur Kamera dreht, dass sie nie ganz zur Linie wird. Die Beine
 * schwingen im Schritt. Wer sie bewegt ({@link com.dan.geyser.world.Fauna}), füllt die Felder vor
 * jedem Bild; der Bildrechner liest sie nur.
 * <p>
 * Die Umrisse sind in Metern gezeichnet (x nach vorn, y nach oben, Boden bei 0).
 */
public final class Animals {
    public static final byte BISON = 0, ELK_COW = 1, ELK_BULL = 2, BISON_CALF = 3;

    public int n;
    public final float[] x = new float[64], y = new float[64], z = new float[64], hx = new float[64], hz = new float[64];
    /** Schrittphase (Bogenmaß), Gangstärke 0 (steht, grast) bis 1 (geht), Kopf gesenkt 0..1, Größe. */
    public final float[] step = new float[64], gait = new float[64], graze = new float[64], scale = new float[64];
    public final byte[] kind = new byte[64];

    /** Körper: Umriss als Punktfolge (x, y) und Farbe (linear, Albedo). */
    static final float[] BISON_BODY = {
            -1.45f, 1.05f, -1.40f, 1.38f, -1.10f, 1.52f, -0.50f, 1.60f, 0.05f, 1.78f, 0.35f, 1.86f, 0.62f, 1.80f,
            0.95f, 1.55f, 1.20f, 1.28f, 1.38f, 1.02f, 1.47f, 0.70f, 1.42f, 0.52f, 1.22f, 0.46f, 1.05f, 0.30f,
            0.82f, 0.40f, 0.62f, 0.55f, 0.20f, 0.66f, -0.55f, 0.72f, -1.05f, 0.80f, -1.35f, 0.88f};
    /** Mähne und Kopf dunkler, über den Körper gelegt. */
    static final float[] BISON_MANE = {0.05f, 1.78f, 0.35f, 1.86f, 0.62f, 1.80f, 0.95f, 1.55f, 1.20f, 1.28f, 1.38f, 1.02f,
            1.47f, 0.70f, 1.42f, 0.52f, 1.22f, 0.46f, 1.05f, 0.30f, 0.82f, 0.40f, 0.62f, 0.55f, 0.25f, 0.70f, -0.05f, 1.10f};
    static final float[] ELK_BODY = {
            -1.08f, 1.30f, -1.00f, 1.43f, -0.55f, 1.47f, 0.00f, 1.45f, 0.40f, 1.52f, 0.62f, 1.50f, 0.80f, 1.30f,
            0.74f, 1.08f, 0.62f, 0.98f, 0.20f, 0.95f, -0.30f, 0.97f, -0.70f, 1.02f, -1.02f, 1.08f};
    /** Hals und Kopf mit Ohr, um den Widerrist drehbar (Grasen). */
    static final float[] ELK_NECK = {0.35f, 1.50f, 0.62f, 1.72f, 0.92f, 2.00f, 0.96f, 2.28f, 1.02f, 2.14f, 1.20f, 2.10f,
            1.55f, 1.88f, 1.50f, 1.78f, 1.22f, 1.82f, 0.98f, 1.64f, 0.80f, 1.36f, 0.62f, 1.16f};
    static final float[] ELK_RUMP = {-1.08f, 1.10f, -1.07f, 1.40f, -0.82f, 1.45f, -0.72f, 1.22f, -0.86f, 1.04f};
    /** Geweih des Bullen: Stange und Enden als Linien (x0, y0, x1, y1), vom Kopf aus. */
    static final float[] ANTLER = {1.12f, 2.15f, 0.70f, 2.85f, 0.70f, 2.85f, 0.25f, 3.20f, 1.05f, 2.30f, 1.40f, 2.55f,
            0.92f, 2.52f, 1.22f, 2.85f, 0.80f, 2.70f, 1.00f, 3.05f, 0.55f, 2.98f, 0.62f, 3.30f};

    /** Beine: Hüft-x, Hüfthöhe, Länge bis zum Huf, Phasenversatz. */
    static final float[][] BISON_LEGS = {{0.75f, 0.70f, 0.70f, 0}, {0.55f, 0.70f, 0.70f, 3.14f}, {-0.95f, 0.85f, 0.85f, 1.57f}, {-1.15f, 0.85f, 0.85f, 4.71f}};
    static final float[][] ELK_LEGS = {{0.70f, 1.05f, 1.05f, 0}, {0.52f, 1.05f, 1.05f, 3.14f}, {-0.75f, 1.05f, 1.05f, 1.57f}, {-0.92f, 1.05f, 1.05f, 4.71f}};

    public void clear() { n = 0; }

    public int add(byte k, double px, double py, double pz, double hdx, double hdz, double sc) {
        if (n == x.length) return -1;
        int i = n++;
        kind[i] = k; x[i] = (float) px; y[i] = (float) py; z[i] = (float) pz;
        double l = Math.hypot(hdx, hdz);
        hx[i] = (float) (hdx / l); hz[i] = (float) (hdz / l);
        scale[i] = (float) sc; step[i] = 0; gait[i] = 0; graze[i] = 0;
        return i;
    }
}
