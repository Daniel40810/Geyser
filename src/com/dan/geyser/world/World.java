package com.dan.geyser.world;

import com.dan.geyser.core.Scene;

/** Was der Bau liefert: die Szene fürs Bild und die Geysire für die Simulation. */
public final class World {
    public final Scene scene;
    public final Geysers geysers;
    /** Drehpunkte für die Ortswahl: Upper Geyser Basin und Midway (x, z). */
    public static final double[] UPPER = {-520, -700}, MIDWAY = {-760, -7180};
    /**
     * Die Stege als Folgen von Deckpunkten (x, Höhe der Bretter, z) alle 2,5 m, in der Reihenfolge des
     * Baus: 0 Halbrund um Old Faithful, 1 zur Brücke, 2 Runde über Geyser Hill, 3 Hauptweg bis Morning
     * Glory, 4 Midway. Für den Rundgang der Regie. Dazu die Plattform über Grand Prismatic (x, y, z).
     */
    public static final java.util.List<double[][]> ROUTES = new java.util.ArrayList<>();
    public static double[] OVERLOOK = {-900, 0, -6880};

    World(Scene scene, Geysers geysers) {
        this.scene = scene;
        this.geysers = geysers;
    }
}
