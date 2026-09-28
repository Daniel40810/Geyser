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
        s("WIKI_DAISY", "Daisy Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Daisy_Geyser", "LEXIKON", "2026-09-28");
        s("WIKI_SPLENDID", "Splendid Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Splendid_Geyser", "LEXIKON", "2026-09-28");
        s("WIKI_GROTTO", "Grotto Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Grotto_Geyser", "LEXIKON", "2026-09-28");
        s("WIKI_FAN", "Fan and Mortar Geysers", "Wikipedia", "https://en.wikipedia.org/wiki/Fan_and_Mortar_Geysers", "LEXIKON", "2026-09-28");
        s("WIKI_GIANTESS", "Giantess Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Giantess_Geyser", "LEXIKON", "2026-09-28");
        s("WIKI_TURBAN", "Turban Geyser", "Wikipedia", "https://en.wikipedia.org/wiki/Turban_Geyser", "LEXIKON", "2026-09-28");
        s("WIKI_DOUBLET", "Doublet Pool", "Wikipedia", "https://en.wikipedia.org/wiki/Doublet_Pool", "LEXIKON", "2026-09-28");
        s("WIKI_FPP", "Fountain Paint Pot", "Wikipedia", "https://en.wikipedia.org/wiki/Fountain_Paint_Pot", "LEXIKON", "2026-09-28");
        s("USGS_MG", "What's the story, Morning Glory?", "USGS", "https://www.usgs.gov/observatories/yvo/news/whats-story-morning-glory", "BEHOERDE", "2026-09-28");
        s("GEOLOGY_2008", "Climate-induced variations of geyser periodicity in Yellowstone National Park, USA (Hurwitz u. a.)", "Geology",
                "https://www.sciencedaily.com/releases/2008/06/080614080441.htm", "FACHARTIKEL", "2026-09-28");
        s("GRL_2020", "Yellowstone's Old Faithful Geyser shut down by a severe thirteenth century drought (Hurwitz u. a.)", "Geophysical Research Letters",
                "https://agupubs.onlinelibrary.wiley.com/doi/full/10.1029/2020GL089871", "FACHARTIKEL", "2026-09-28");
        s("JGR_1998", "Bubble collapse as the source of tremor at Old Faithful Geyser (Kedar u. a.)", "Journal of Geophysical Research",
                "https://agupubs.onlinelibrary.wiley.com/doi/10.1029/98JB01824", "FACHARTIKEL", "2026-09-28");
        s("GRL_2019", "Imaging the deep subsurface plumbing of Old Faithful Geyser from low-frequency hydrothermal tremor migration (Wu u. a.)",
                "Geophysical Research Letters", "https://agupubs.onlinelibrary.wiley.com/doi/full/10.1029/2018GL081771", "FACHARTIKEL", "2026-09-28");
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
            new Fact("TIER", "ABSTAND", "Mindestens 25 Yards (23 m) Abstand zu Bisons und Wapitis", 25.0, "yd", "NPS_SAFETY"),
            new Fact("GEYSIR", "GRAND", "Vor Grand bricht Turban alle 15 bis 25 Minuten für fünf Minuten aus; Grand beginnt ein bis zwei Minuten nach dem Beginn eines Turban-Ausbruchs", 2.0, "min", "WIKI_GRAND"),
            new Fact("GEYSIR", "GRAND", "Während Grand spielt Turban ununterbrochen, bis 6 m hoch", 6.0, "m", "WIKI_TURBAN"),
            new Fact("GEYSIR", "BEEHIVE", "Beehive's Indicator, 3 m neben Beehive, spritzt 4,6 bis 7,6 m hoch, Sekunden bis 30 Minuten vor Beehive, im Mittel 13,3 Minuten", 13.3, "min", "WIKI_BEEHIVE"),
            new Fact("GEYSIR", "DAISY", "18 bis 23 m schräg, 3 bis 4 Minuten, alle 120 bis über 200 Minuten", 23.0, "m", "WIKI_DAISY"),
            new Fact("GEYSIR", "DAISY", "Splendid (bis 61 m) ist meist still, wenn Daisy aktiv ist; zuletzt am 13. Mai 1998; ein Ausbruch von Splendid verändert Daisys Abstand", 1998.0, "Jahr", "WIKI_SPLENDID"),
            new Fact("GEYSIR", "GROTTO", "Normale Ausbrüche alle 6 bis 7 Stunden für 1 bis 2 Stunden, nach 2 bis 10 davon ein Marathon von 10 bis 26 Stunden", 26.0, "h", "WIKI_GROTTO"),
            new Fact("GEYSIR", "FAN", "Fan bis 125 ft, Mortar bis 80 ft, gemeinsam alle 3 Tage bis Wochen, rund 30 Minuten", 125.0, "ft", "WIKI_FAN"),
            new Fact("GEYSIR", "GIANTESS", "Bis 61 m, keine bis 41 Ausbrüche im Jahr, meist 2 bis 6, jeweils 4 bis 48 Stunden mit Stößen etwa zweimal je Stunde", 61.0, "m", "WIKI_GIANTESS"),
            new Fact("QUELLE", "DOUBLET", "Doublet Pool brach nur viermal aus, zweimal zusammen mit Giantess", 4.0, "Ausbrüche", "WIKI_DOUBLET"),
            new Fact("GEYSIR", "OLD_FAITHFUL", "Abstand 1997 im Mittel 71, 2006 91 Minuten: weniger Niederschlag, weniger Wasser in der Röhre", 91.0, "min", "GEOLOGY_2008"),
            new Fact("GEYSIR", "OLD_FAITHFUL", "In einer schweren Dürre des 13. Jahrhunderts brach Old Faithful jahrzehntelang nicht aus", null, null, "GRL_2020"),
            new Fact("GEYSIR", "OLD_FAITHFUL", "Tremor aus zusammenfallenden Dampfblasen; der tieffrequente Tremor wächst vor jedem Ausbruch und bricht mit dem Beginn ab", null, null, "GRL_2019"),
            new Fact("QUELLE", "MORNING_GLORY", "Von den 1880ern bis in die 1940er tiefblau und heißer; Münzen und Abfall verstopften den Schlot, die Quelle kühlte ab, gelbe und orange Matten wuchsen zur Mitte", null, null, "USGS_MG"),
            new Fact("QUELLE", "FOUNTAIN_PAINT_POT", "Schlammtopf im Lower Geyser Basin auf 7306 ft (2227 m); im Frühsommer ist der Schlamm dünn vom hohen Grundwasser, im Spätsommer dick", 2227.0, "m", "WIKI_FPP"),
            new Fact("QUELLE", "MORNING_GLORY", "1950 herausgeholt: 76 Taschentücher, 86,27 Dollar in Pennys und 8,10 Dollar in anderen Münzen", 76.0, "Taschentücher", "WIKI_MG")};
}
