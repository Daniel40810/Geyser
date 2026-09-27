package com.dan.geyser.atom;

import java.awt.Color;

/**
 * Die sechs Ablagerungen der Mineral-Lupe. Alle Kennzahlen sind nachgeschlagen (Quellen je
 * Ablagerung). Gegenüber dem Plan steht statt Gips Alunit: Für Gips fand sich in den Quellen zu
 * Yellowstone kein Beleg, Alunit nennt der USGS für die saure Umwandlung ausdrücklich.
 * <p>
 * In den Becken der Szene liegt nur Kieselsinter; Travertin, Schwefel, Eisen(III)-oxid, Skorodit
 * und Alunit kommen anderswo im Park vor (Mammoth, Norris, saure Dampfzonen) und stehen zum
 * Vergleich in der Lupe.
 */
public final class Minerals {
    private Minerals() { }

    /** Wasser im Opal: rund 10 Gewichtsprozent → n ≈ 0,37 H₂O je SiO₂. */
    static final double N_OPAL = 0.1 / 0.9 * 60.08 / 18.015;

    /** Die sechs Ablagerungen; mit Datenbank ersetzt durch die Zeilen aus GEY_MINERAL (gleiche Reihenfolge). */
    public static volatile Mineral[] ALL = {
            new Mineral("SINTER", "Kieselsinter (Geyserit)", "SiO₂ · n H₂O", "Opal-A, amorph", true,
                    "Hier im Becken: an jedem Kegel, jedem Beckenrand und auf den Terrassen", new Color(214, 210, 196),
                    new int[]{14, 8, 1}, new double[]{1, 2 + N_OPAL, 2 * N_OPAL}, new int[]{33, 51},
                    "Heißes Grundwasser löst Kieselsäure aus dem Rhyolith. Kühlt es an der Oberfläche ab, fällt sie aus: Sinter, "
                            + "als perlige Krusten Geyserit genannt. Frisch ist er nicht kristallin (Opal-A) und zu rund einem Zehntel Wasser. "
                            + "Mit der Zeit reift er über Opal-CT zu Quarz und verliert das Wasser fast ganz. Die bunten Bakterienmatten "
                            + "wachsen mit ein und können bis zur Hälfte des Volumens ausmachen.",
                    new String[]{
                            "USGS_SINTER|Frischer Sinter: Opal-A, rund 10 % des Gewichts Wasser",
                            "USGS_SINTER|Reifung: Opal-A → Opal-CT → Quarz, das Wasser geht auf fast 0 %",
                            "USGS_SINTER|Mikroben: bis 50 % des Sintervolumens",
                            "JVGR_2021|Castle Geyser: rund 5000 t Sinter, 470 bis 940 kg im Jahr",
                            "JVGR_2021|Nur rund 2 % der Kieselsäure, die Castle ausschüttet, bleibt als Sinter liegen",
                            "JVGR_2021|Junger Sinter ist reicher an Arsen und Antimon als alter",
                            "USGS_CHEM|Das Wasser im Becken: neutral-chloridisch, mit Carbonat, Chlorid, Kieselsäure und Arsen"}),
            new Mineral("TRAVERTIN", "Travertin", "CaCO₃", "Calcit und Aragonit", false,
                    "Nicht in diesen Becken: Mammoth Hot Springs im Norden des Parks", new Color(232, 222, 200),
                    new int[]{20, 6, 8}, new double[]{1, 1, 3}, new int[0],
                    "Bei Mammoth fließt das heiße Wasser durch Kalkstein und nimmt Calcium und Carbonat auf. Tritt es aus, entweicht "
                            + "Kohlendioxid wie aus einer geöffneten Flasche, und Calciumcarbonat fällt aus. So wachsen die Terrassen, "
                            + "viel schneller als Sinter.",
                    new String[]{
                            "USGS_TRAV|Calcit und Aragonit, beide CaCO₃",
                            "USGS_TRAV|Wächst um rund 3 mm am Tag, schneller als Korallen (1 mm)",
                            "USGS_TRAV|Auslöser: CO₂ entweicht, wenn Druck und Temperatur fallen",
                            "USGS_CHEM|Das Wasser bei Mammoth: reich an Sulfat und Hydrogencarbonat, Calcium, Magnesium, arm an Kieselsäure"}),
            new Mineral("SCHWEFEL", "Schwefel", "S₈", "gediegen, elementar", false,
                    "Nicht in diesen Becken: saure Quellen etwa im Norris Geyser Basin", new Color(236, 206, 60),
                    new int[]{16}, new double[]{8}, new int[0],
                    "In sauren Quellen oxidiert Schwefelwasserstoff. Nahe am Quellmund setzt sich gelber elementarer Schwefel ab, "
                            + "weiter abwärts folgen die rostfarbenen Eisenoxide.",
                    new String[]{
                            "FRONTIERS_2012|Saure Quellen in Norris: pH 2,4 bis 3,6, 53 bis 84 °C",
                            "FRONTIERS_2012|Schwefel liegt jeweils am nächsten zum Quellmund",
                            "USGS_CHEM|Saure Sulfatwässer: pH unter 4, viel Sulfat aus oxidiertem H₂S, wenig Chlorid"}),
            new Mineral("EISENOXID", "Eisen(III)-oxid", "FeO(OH)", "amorph, vereinfacht als Oxyhydroxid", false,
                    "Nicht in diesen Becken: Matten in sauren Quellen, etwa im Norris Geyser Basin", new Color(176, 82, 38),
                    new int[]{26, 8, 1}, new double[]{1, 2, 1}, new int[]{33},
                    "Mikroben oxidieren gelöstes Eisen(II). Das Eisen(III) fällt als weiche, rostrote Matte aus und bindet Arsen. "
                            + "Die Formel ist vereinfacht: In den Matten ist das Oxid meist amorph.",
                    new String[]{
                            "FRONTIERS_2012|Weiche Matten, 1 bis 3 cm dick, teils amorph, teils kristallin",
                            "FRONTIERS_2012|Gebundenes Arsen: 0,6 bis 0,7 mol je mol Eisen",
                            "FRONTIERS_2012|Gelöstes Eisen(II): rund 30 bis 240 µmol/l"}),
            new Mineral("SKORODIT", "Skorodit", "FeAsO₄ · 2 H₂O", "Eisenarsenat", false,
                    "Nicht in diesen Becken: Porcelain Basin und Gap Spring in Norris", new Color(120, 160, 138),
                    new int[]{26, 33, 8, 1}, new double[]{1, 1, 6, 4}, new int[0],
                    "Wo besonders viel Arsen im Wasser ist, bildet sich statt des amorphen Oxids Skorodit, ein Eisenarsenat. "
                            + "In Norris ist Arsen im Wasser ein bis zwei Größenordnungen höher als in anderen Quellen.",
                    new String[]{
                            "FRONTIERS_2012|An Porcelain Basin und Gap Spring die vorherrschende Phase",
                            "FRONTIERS_2012|Arsen in Norris: ein bis zwei Zehnerpotenzen über anderen Quellen"}),
            new Mineral("ALUNIT", "Alunit", "KAl₃(SO₄)₂(OH)₆", "Sulfat aus saurer Umwandlung", false,
                    "Nicht in diesen Becken: saure Dampfzonen und Schlammtöpfe", new Color(222, 214, 226),
                    new int[]{19, 13, 16, 8, 1}, new double[]{1, 3, 2, 14, 6}, new int[0],
                    "Saurer Dampf zersetzt das Gestein: Aus dem Rhyolith werden Opal, Kaolinit, Alunit und Pyrit. "
                            + "Das bleicht und färbt die Hänge, etwa im Grand Canyon of the Yellowstone.",
                    new String[]{
                            "USGS_ALT|Saure Umwandlung erzeugt Opal, Kaolinit, Alunit, Pyrit und Smektit",
                            "USGS_ALT|Zwei Wasserarten im Park: kieselsäurereich alkalisch-chloridisch und sauer-sulfatisch"})};
}
