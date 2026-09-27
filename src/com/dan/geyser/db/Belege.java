package com.dan.geyser.db;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alle Belege der App an einer Stelle, mit Code, und die Kennzahlen, die nicht zu einem Mineral
 * gehören. Daraus entstehen <code>GEY_SOURCE</code> und <code>GEY_FACT</code>; die Mineral-Lupe
 * nimmt ihre Quellen ebenfalls von hier.
 */
public final class Belege {
    private Belege() { }

    /** Ein Beleg: Code, Titel, Herausgeber, URL, Art (BEHOERDE, LEXIKON, FACHARTIKEL, DATEN), abgerufen (JJJJ-MM-TT). */
    public static final class Source {
        public final String code, title, publisher, url, kind, accessed;

        Source(String code, String title, String publisher, String url, String kind, String accessed) {
            this.code = code; this.title = title; this.publisher = publisher; this.url = url; this.kind = kind; this.accessed = accessed;
        }
    }

    /** Eine Kennzahl: Thema, Bezug (Code), Text, Wert, Einheit, Beleg. */
    public static final class Fact {
        public final String topic, subject, text, unit, source;
        public final Double value;

        public Fact(String topic, String subject, String text, Double value, String unit, String source) {
            this.topic = topic; this.subject = subject; this.text = text; this.value = value; this.unit = unit; this.source = source;
        }
    }

    public static final Map<String, Source> SOURCES = java.util.Collections.synchronizedMap(new LinkedHashMap<>());

    /** Beleg aus der Datenbank übernehmen (oder ergänzen). */
    public static void put(String code, String title, String pub, String url, String kind, String acc) {
        SOURCES.put(code, new Source(code, title, pub == null ? "" : pub, url, kind, acc));
    }

    private static void s(String code, String title, String pub, String url, String kind, String acc) {
        SOURCES.put(code, new Source(code, title, pub, url, kind, acc));
    }

    static {
        s("NPS_OF", "Old Faithful Geyser", "NPS", "https://home.nps.gov/articles/old-faithful-geyser.htm", "BEHOERDE", "2026-09-26");
        s("NPS_UGB", "Upper Geyser Basin (Old Faithful Virtual Visitor Center)", "NPS", "https://www.nps.gov/features/yell/ofvec/exhibits/treasures/ugb/index.htm", "BEHOERDE", "2026-09-27");
        s("NPS_GPS", "Grand Prismatic Spring", "NPS", "https://www.nps.gov/places/000/grand-prismatic-spring.htm", "BEHOERDE", "2026-09-26");
        s("NPS_THERMO", "Thermophilic Communities", "NPS", "https://www.nps.gov/yell/learn/nature/thermophilic-communities.htm", "BEHOERDE", "2026-09-26");
        s("NPS_FPP", "Bacteria (Fountain Paint Pot tour)", "NPS", "https://www.nps.gov/features/yell/tours/fountainpaint/bacteria.htm", "BEHOERDE", "2026-09-26");
        s("NPS_BISON", "Bison", "NPS", "https://www.nps.gov/yell/learn/nature/bison.htm", "BEHOERDE", "2026-09-27");
        s("NPS_ELK", "Elk", "NPS", "https://www.nps.gov/yell/learn/nature/elk.htm", "BEHOERDE", "2026-09-27");
        s("NPS_SAFETY", "Safety", "NPS", "https://www.nps.gov/yell/planyourvisit/safety.htm", "BEHOERDE", "2026-09-27");
        s("WIKI_OF", "Old Faithful", "Wikipedia", "https://en.wikipedia.org/wiki/Old_Faithful", "LEXIKON", "2026-09-26");
        s("WIKI_GPS", "Grand Prismatic Spring", "Wikipedia", "https://en.wikipedia.org/wiki/Grand_Prismatic_Spring", "LEXIKON", "2026-09-26");
        s("WIKI_CASTLE", "Castle Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Castle_Geyser", "LEXIKON", "2026-09-26");
        s("WIKI_GRAND", "Grand Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Grand_Geyser", "LEXIKON", "2026-09-26");
        s("WIKI_BEEHIVE", "Beehive Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Beehive_Geyser", "LEXIKON", "2026-09-26");
        s("WIKI_RIVERSIDE", "Riverside Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Riverside_Geyser", "LEXIKON", "2026-09-26");
        s("WIKI_MG", "Morning Glory Pool", "Wikipedia", "https://en.wikipedia.org/wiki/Morning_Glory_Pool", "LEXIKON", "2026-09-26");
        s("WIKI_EXCELSIOR", "Excelsior Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Excelsior_Geyser", "LEXIKON", "2026-09-27");
        s("WIKI_RAINBOW", "Rainbow", "Wikipedia", "https://en.wikipedia.org/wiki/Rainbow", "LEXIKON", "2026-09-27");
        s("CR_CLIMATE", "Yellowstone Park WY Average Temperatures by Month", "Current Results (NOAA-Normalwerte 1991–2020)",
                "https://www.currentresults.com/Weather/Wyoming/Places/yellowstone-park-temperatures-by-month-average.php", "DATEN", "2026-09-27");
        s("USGS_SINTER", "Geysers—what exactly are they made of?", "USGS", "https://www.usgs.gov/news/geysers-what-exactly-are-they-made", "BEHOERDE", "2026-09-27");
        s("USGS_CHEM", "The diverse chemistry of Yellowstone's hydrothermal features", "USGS",
                "https://www.usgs.gov/observatories/yvo/news/diverse-chemistry-yellowstones-hydrothermal-features", "BEHOERDE", "2026-09-27");
        s("USGS_TRAV", "Travertine—Yellowstone's hydrothermal timekeeper", "USGS",
                "https://usgs.gov/observatories/yvo/news/travertine-yellowstones-hydrothermal-timekeeper", "BEHOERDE", "2026-09-27");
        s("USGS_ALT", "Alterations go hydrothermal", "USGS", "https://www.usgs.gov/news/alterations-go-hydrothermal-alteration-yellowstone", "BEHOERDE", "2026-09-27");
        s("JVGR_2021", "The structure and volume of large geysers in Yellowstone National Park, USA and the mineralogy and chemistry of their silica sinter deposits",
                "Journal of Volcanology and Geothermal Research", "https://www.sciencedirect.com/science/article/abs/pii/S0377027321002201", "FACHARTIKEL", "2026-09-27");
        s("FRONTIERS_2012", "Microbial iron cycling in acidic geothermal springs of Yellowstone National Park", "Frontiers in Microbiology",
                "https://www.frontiersin.org/journals/microbiology/articles/10.3389/fmicb.2012.00109/full", "FACHARTIKEL", "2026-09-27");
    }

    /** "Titel|URL" für die Anzeige. */
    public static String link(String code) {
        Source s = SOURCES.get(code);
        if (s == null) return code + "|";
        String t = s.publisher.startsWith("Current") ? "Current Results: " + s.title : s.publisher + ": " + s.title;
        if (s.kind.equals("FACHARTIKEL")) t = s.publisher + ": " + (s.title.length() > 70 ? s.title.substring(0, 67) + " …" : s.title);
        return t + "|" + s.url;
    }

    /** Kennzahlen außerhalb der Mineralien (die stehen in {@link com.dan.geyser.atom.Minerals}). */
    public static final Fact[] FACTS = {
            new Fact("GEYSIR", "UPPER", "Auf einer Quadratmeile mindestens 150 Geysire, die dichteste Ansammlung der Welt", 150.0, "Geysire", "NPS_UGB"),
            new Fact("GEYSIR", "OLD_FAITHFUL", "Höhe der Säule 32 bis 56 m", 56.0, "m", "WIKI_OF"),
            new Fact("GEYSIR", "OLD_FAITHFUL", "Ein Ausbruch dauert 1½ bis 5 Minuten", 5.0, "min", "WIKI_OF"),
            new Fact("GEYSIR", "OLD_FAITHFUL", "14 000 bis 32 000 Liter Wasser je Ausbruch", 32000.0, "l", "WIKI_OF"),
            new Fact("GEYSIR", "OLD_FAITHFUL", "Benannt am 18. September 1870 von Henry D. Washburn", 1870.0, "Jahr", "WIKI_OF"),
            new Fact("GEYSIR", "OLD_FAITHFUL", "Vorhersage auf ±10 Minuten aus der Dauer des letzten Ausbruchs (unter oder über 2½ Minuten)", 10.0, "min", "WIKI_OF"),
            new Fact("GEYSIR", "CASTLE", "Der Sinterkegel ist per Radiokarbon um 1022 datiert", 1022.0, "Jahr", "WIKI_CASTLE"),
            new Fact("GEYSIR", "CASTLE", "Rund 20 Minuten Wasser bis 27 m, danach 30 bis 40 Minuten Dampfphase", 27.0, "m", "WIKI_CASTLE"),
            new Fact("GEYSIR", "CASTLE", "Rund 5000 t Sinter, abgelagert mit 470 bis 940 kg im Jahr", 5000.0, "t", "JVGR_2021"),
            new Fact("QUELLE", "GRAND_PRISMATIC", "200 bis 330 ft Durchmesser, mehr als 121 ft tief", 121.0, "ft", "NPS_GPS"),
            new Fact("QUELLE", "EXCELSIOR", "1985 für 46 Stunden wieder aktiv; schüttet 15 100 bis 17 000 l Wasser von 93 °C je Minute in den Firehole", 17000.0, "l/min", "WIKI_EXCELSIOR"),
            new Fact("QUELLE", "MATTEN", "Über 75 °C nur blasse Fäden, darunter orange und gelbe Cyanobakterien, im Winter olivgrün", 75.0, "°C", "NPS_THERMO"),
            new Fact("QUELLE", "MATTEN", "Cyanobakterien bis 73 °C gelbgrün, kühler orange, rost und braun", 73.0, "°C", "NPS_FPP"),
            new Fact("KLIMA", "OLD_FAITHFUL", "Monatsnormalwerte 1991–2020 der Station Old Faithful: Januar 28/1 °F, Juli 76/39 °F", null, null, "CR_CLIMATE"),
            new Fact("LICHT", "REGENBOGEN", "Hauptbogen Rot bei 42°, Violett bei 40,6° um den Gegenpunkt der Sonne", 42.0, "°", "WIKI_RAINBOW"),
            new Fact("LICHT", "REGENBOGEN", "Nebenbogen bei 50 bis 53°, Farben umgekehrt, dazwischen Alexanders dunkles Band", 51.0, "°", "WIKI_RAINBOW"),
            new Fact("TIER", "BISON", "Bullen bis 2000 lb; im Winter an den Geysirbecken wegen Futter und milderem Kleinklima; Brunft Juli und August", 2000.0, "lb", "NPS_BISON"),
            new Fact("TIER", "WAPITI", "Bullen rund 700 lb und fünf Fuß Schulterhöhe; Brunft Anfang September bis Mitte Oktober; im Winter weniger als 2000 im Park", 700.0, "lb", "NPS_ELK"),
            new Fact("TIER", "ABSTAND", "Mindestens 25 Yards (23 m) Abstand zu Bisons und Wapitis", 25.0, "yd", "NPS_SAFETY")};
}
