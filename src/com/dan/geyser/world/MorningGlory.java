package com.dan.geyser.world;

/**
 * Morning Glory Pool über die Jahrzehnte. Von den 1880ern bis in die 1940er war die Quelle heißer
 * und gleichmäßig tiefblau (USGS). Besucher warfen Münzen, Steine, Holz und Abfall hinein; das
 * verstopfte den Schlot, das Wasser kühlte ab, und gelbe und orange Matten, die es vorher zu heiß
 * hatten, wuchsen zur Mitte. Reinigungen 1950 (mit einem ausgelösten Ausbruch), 1975 und 1991
 * holten Tausende Dinge heraus, heute misst die Quelle rund 70 °C.
 * <p>
 * Belegt sind die Farbe bis in die 1940er, die Reinigungen und der heutige Wert. Die Temperaturen
 * dazwischen sind ein Modell: so gewählt, dass die Farben im Bild dem entsprechen, was die Quellen
 * beschreiben.
 */
public final class MorningGlory {
    private MorningGlory() { }

    /** Temperatur am Quellmund heute (°C), wie im Becken gebaut. */
    public static final double NOW_T = 69.8;
    public static final int FIRST = 1883, LAST = 2026;

    /** Stützpunkte Jahr, Temperatur (°C); an den Reinigungen springt sie. */
    static final double[][] CURVE = {
            {1883, 84}, {1925, 83.5}, {1949.9, 76.5}, {1950.1, 80.5}, {1974.9, 72.5}, {1975.1, 75},
            {1990.9, 70.8}, {1991.1, 72.5}, {2026, NOW_T}};

    /** Ereignisse für die Tafel: Jahr, Text. */
    static final Object[][] EVENTS = {
            {1883, "Benannt: „Convolutus“, bald Morning Glory nach der Trichterwinde"},
            {1935, "Münzen, Steine und Abfall sammeln sich im Schlot"},
            {1950, "Reinigung mit Ausbruch: 76 Taschentücher, 86,27 $ in Pennys"},
            {1975, "Reinigung: Kisten voller Münzen und Abfall"},
            {1991, "Reinigung: wieder Tausende Dinge aus der Quelle"},
            {2010, "Gelbe und orange Matten bis weit zur Mitte"}};

    /** Temperatur am Quellmund im Jahr y (Modell zwischen den Stützpunkten). */
    public static double tempAt(double y) {
        if (y <= CURVE[0][0]) return CURVE[0][1];
        for (int i = 1; i < CURVE.length; i++) {
            if (y <= CURVE[i][0]) {
                double u = (y - CURVE[i - 1][0]) / (CURVE[i][0] - CURVE[i - 1][0]);
                return CURVE[i - 1][1] + (CURVE[i][1] - CURVE[i - 1][1]) * u;
            }
        }
        return NOW_T;
    }

    /** Das letzte Ereignis bis zum Jahr y (für die Tafel) oder null. */
    public static String event(double y) {
        String e = null;
        for (Object[] ev : EVENTS) if (((Number) ev[0]).doubleValue() <= y) e = ev[0] + " · " + ev[1];
        return e;
    }

    /** Jahr zum Fortschritt u 0..1 des Zeitraffers. */
    public static double year(double u) { return FIRST + (LAST - FIRST) * Math.max(0, Math.min(1, u)); }
}
