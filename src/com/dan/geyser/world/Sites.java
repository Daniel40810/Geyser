package com.dan.geyser.world;

/**
 * Die beschrifteten Stellen mit ihren Koordinaten (WGS 84, wie SRID 8307), Höhen und Kennzahlen.
 * Quellen: die Artikel des National Park Service und der Wikipedia zu jedem Geysir (abgerufen am
 * 26.09.2026, die Stellen ab Daisy am 28.09.2026). Umrechnung auf die Szene: Meter östlich und südlich von Old Faithful, mit den Gradlängen
 * auf 44,46° N.
 */
public final class Sites {
    public static final double LAT0 = 44.46047, LON0 = -110.82814;
    /** Meter je Grad Länge und Breite auf 44,46° N (Ellipsoid WGS 84). */
    public static final double M_LON = 79570.8, M_LAT = 111122.4;

    public enum Kind { GEYSER, SPRING }

    public static final class Site {
        public final String name, line;
        public final double lat, lon;
        public final Kind kind;
        /** Größte Höhe der Säule in Metern (0 bei Quellen); Höhe über dem Meer (NaN: unbekannt). */
        public final double height, elevation;
        /** Becken: 0 Upper Geyser Basin, 1 Midway. */
        public int basin;

        Site(String name, double lat, double lon, Kind kind, double height, double elevation, String line) {
            this.name = name; this.lat = lat; this.lon = lon; this.kind = kind; this.height = height; this.elevation = elevation;
            this.line = line;
        }

        public double x() { return (lon - LON0) * M_LON; }
        public double z() { return -(lat - LAT0) * M_LAT; }
        /** Höhe über y = 0 (Old Faithful, 2240 m). */
        public double level() { return elevation - 2240; }
    }

    /** Die Stellen; die ersten sieben haben die Tasten 1 bis 7, die weiteren gibt es im Bedienfeld. */
    public static final Site[] ALL = {
            new Site("Old Faithful", 44.46047, -110.82814, Kind.GEYSER, 55, 2240, "Geysir · 30–55 m · alle 65 oder 91 min"),
            new Site("Beehive Geyser", 44.4629887, -110.8299335, Kind.GEYSER, 61, 2244, "Geysir auf Geyser Hill · bis 61 m · alle 8–24 h"),
            new Site("Castle Geyser", 44.463445, -110.83666, Kind.GEYSER, 27, 2235, "Geysir · 27 m · alle 15–17 h · Kegel rund 1000 Jahre alt"),
            new Site("Grand Geyser", 44.4665996, -110.8382669, Kind.GEYSER, 61, 2234, "Fontänengeysir · bis 61 m · alle 6–7 h"),
            new Site("Riverside Geyser", 44.4735439, -110.8404890, Kind.GEYSER, 23, Double.NaN, "Geysir · 23 m schräg über den Fluss · alle 5–7 h"),
            new Site("Morning Glory Pool", 44.4750325, -110.8435128, Kind.SPRING, 0, 2225, "Quelle · 69,8 °C · 7 m tief"),
            new Site("Grand Prismatic Spring", 44.525, -110.83806, Kind.SPRING, 0, 2216, "Quelle in Midway · 110 m breit · 50 m tief · 70 °C"),
            new Site("Daisy Geyser", 44.4699327, -110.8449336, Kind.GEYSER, 23, Double.NaN, "Geysir · 18–23 m schräg · alle 2 bis gut 3 Std. · Splendid daneben schläft seit 1998"),
            new Site("Grotto Geyser", 44.47181, -110.84178, Kind.GEYSER, 3, Double.NaN, "Geysir im Sintergewölbe · 3 m · 1–2 Std., Marathons 10–26 Std."),
            new Site("Fan Geyser", 44.47444, -110.8425, Kind.GEYSER, 38, Double.NaN, "Fan und Mortar am Fluss · 38 und 24 m · alle 3 Tage bis Wochen"),
            new Site("Giantess Geyser", 44.4635358, -110.828924, Kind.GEYSER, 61, Double.NaN, "Geysir auf Geyser Hill · bis 61 m · 2- bis 6-mal im Jahr, 4–48 Std.")};

    static { ALL[6].basin = 1; }

    /** Codes der Stellen in der Datenbank (GEY_SITE.code), in der Reihenfolge von {@link #ALL}. */
    public static final String[] CODES = {"OLD_FAITHFUL", "BEEHIVE", "CASTLE", "GRAND", "RIVERSIDE", "MORNING_GLORY", "GRAND_PRISMATIC",
            "DAISY", "GROTTO", "FAN", "GIANTESS"};

    public static int index(String name) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].name.equals(name)) return i;
        return -1;
    }

    /** Excelsior Geyser Crater (Midway): 44,526321° N, 110,8368778° W, 2212 m, 93 °C. */
    public static final double EXC_LAT = 44.526321, EXC_LON = -110.8368778, EXC_ELEV = 2212;

    public static double x(double lon) { return (lon - LON0) * M_LON; }
    public static double z(double lat) { return -(lat - LAT0) * M_LAT; }

    private Sites() { }
}
