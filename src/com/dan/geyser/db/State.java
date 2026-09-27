package com.dan.geyser.db;

import com.dan.geyser.effects.DayNightCycle;

/**
 * Ein Zustand der App: Tag, Uhrzeit, Ort, Kamera im Orbit-Maß (Drehpunkt, Gier, Nick, Abstand),
 * Dunst, Wind, Beschriftung und Zugaben. In der Datenbank eine Zeile in GEY_STATE; ohne Datenbank
 * gibt es nur „Beim Beenden“ als Datei.
 */
public final class State {
    public static final String LAST = "Beim Beenden";

    public String name = LAST;
    public int day = 269, site;
    public double hour = 9, haze = 0.12, wind = 0.35;
    /** Drehpunkt x, y, z, Gier und Nick in Grad, Abstand. */
    public double[] pose = new double[6];
    public boolean labels = true, tube, thermo, fauna = true;
    public String host;
    public java.sql.Timestamp saved;

    public String summary() {
        return DayNightCycle.dateLabel(day) + ", " + DayNightCycle.timeLabel(hour) + " · " + (site == 1 ? "Midway" : "Upper Geyser Basin");
    }
}
