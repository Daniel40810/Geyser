package com.dan.geyser.core;

/** Materialnummern der Szene. 0 bleibt frei (Himmel im G-Puffer). */
public final class Mat {
    /** Gelände: die Farbe kommt aus der Bodenkarte (Sinter, Wiese, Wald, Ufer, Fels) und den Quellen. */
    public static final int TERRAIN = 1;
    /** Kieselsinter (Geyserit) als glatte Fläche. */
    public static final int SINTER = 2;
    /** Holz: Pfosten. */
    public static final int WOOD = 3;
    /** Signalfarbe am Kopf der Pfosten. */
    public static final int MARK = 4;
    /** Rhyolith, der vulkanische Fels des Plateaus. */
    public static final int ROCK = 5;
    /** Geyserit am Beckenrand: perlig, feucht glänzend. */
    public static final int RIM = 6;
    /** Rinde der Drehkiefer, Nadeln, tote Stämme (unten weiß: „Bobby Socks“). */
    public static final int BARK = 7, NEEDLES = 8, SNAG = 9;
    /** Bretter der Stege. */
    public static final int BOARD = 10;
    /** Sinterkegel der Geysire: Lagen, Perlen, braune Streifen vom Abfluss. */
    public static final int CONE = 11;
    /** Tiere (Bisons, Wapitis), nur im G-Puffer gesetzt. */
    public static final int ANIMAL = 12;
    /** Wasser: der Fluss und die heißen Quellen (Farbe aus Tiefe und Temperatur). */
    public static final int WATER = 13, RIVER = 14, POOL = 15;
    /** Laub der Espen: Farbe nach Jahreszeit ({@link Materials#leafLut}). */
    public static final int LEAVES = 19;
    /** Rinde der Espe: hell, grünlich weiß, mit dunklen Narben. */
    public static final int WHITEBARK = 20;
    public static final int COUNT = 21;

    public static boolean water(int m) { return m >= WATER && m <= 18; }

    /** Flächen, die nass werden können (Gischt, Abfluss). */
    public static boolean wettable(int m) { return m == TERRAIN || m == SINTER || m == RIM || m == CONE || m == BOARD || m == ROCK; }

    private Mat() { }
}
