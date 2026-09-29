package com.dan.geyser.world;

import com.dan.geyser.core.Scene;

/** Was der Bau liefert: die Szene fürs Bild und die Geysire für die Simulation. */
public final class World {
    public final Scene scene;
    public final Geysers geysers;
    /** Kiefern und Espen: Jahreszeit des Laubs und fallende Blätter. */
    public final Grove grove;
    /** Der Fluss: Strömung, Wellen, Schaum, Treibgut. */
    public final Firehole firehole;
    /** Gras, Blumen, Steine und Erde um die Kamera. */
    public final Sward sward;
    /** Straßen und Wege mit Verkehr. */
    public final Roadways roadways;
    /** Drehpunkte für die Ortswahl: Upper Geyser Basin, Midway und Lower Geyser Basin (x, z). */
    public static final double[] UPPER = {-520, -700}, MIDWAY = {-760, -7180}, LOWER = {1742, -10013};
    /** Namen der Orte. */
    public static final String[] BASINS = {"Upper Geyser Basin", "Midway Geyser Basin", "Lower Geyser Basin"};

    /** Mitte des Ortes s (0 Upper, 1 Midway, 2 Lower). */
    public static double[] center(int s) { return s == 1 ? MIDWAY : s == 2 ? LOWER : UPPER; }
    /**
     * Die Stege als Folgen von Deckpunkten (x, Höhe der Bretter, z) alle 2,5 m, in der Reihenfolge des
     * Baus: 0 Halbrund um Old Faithful, 1 zur Brücke, 2 Runde über Geyser Hill, 3 Hauptweg bis Morning
     * Glory, 4 Midway, 5 Runde am Fountain Paint Pot. Für den Rundgang der Regie. Dazu die Plattform
     * über Grand Prismatic (x, y, z).
     */
    public static final java.util.List<double[][]> ROUTES = new java.util.ArrayList<>();
    public static double[] OVERLOOK = {-900, 0, -6880};

    World(Scene scene, Geysers geysers, Grove grove, Firehole firehole, Sward sward, Roadways roadways) {
        this.roadways = roadways;
        this.scene = scene;
        this.geysers = geysers;
        this.grove = grove;
        this.firehole = firehole;
        this.sward = sward;
    }
}
