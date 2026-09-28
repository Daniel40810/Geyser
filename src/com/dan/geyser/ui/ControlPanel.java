package com.dan.geyser.ui;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;
import com.dan.geyser.camera.Regie;
import com.dan.geyser.camera.Viewpoint;
import com.dan.geyser.effects.DayNightCycle;
import com.dan.geyser.world.Sites;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/** Bedienfeld rechts: Ansicht, Absteckung, Sonne, Luft, Bild. Wächst mit jeder Phase. */
public final class ControlPanel extends JPanel {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156),
            ACCENT = new Color(226, 190, 72);

    private final ScenePanel scene;
    private final JLabel timeLbl = new JLabel(), dayLbl = new JLabel(), hazeLbl = new JLabel();
    private final FSlider time = new FSlider(0, 239, 90), day = new FSlider(1, 365, DayNightCycle.today()), haze = new FSlider(0, 100, 12);
    private boolean fromScene, fromSite, fromWater, fromWeather;

    public ControlPanel(ScenePanel scene) {
        this.scene = scene;
        setBackground(BG);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        head("ANSICHT");
        FButton home = new FButton("Übersicht");
        home.addActionListener(e -> { scene.goOverview(); scene.requestFocusInWindow(); });
        add(row(home));
        FCheckBox orbit = new FCheckBox("Rundflug");
        orbit.addActionListener(e -> { if (scene.controller() != null) scene.controller().autoOrbit = orbit.isSelected(); scene.requestFocusInWindow(); });
        add(row(orbit));
        scene.setOrbitListener(orbit::setSelected);
        FComboBox where = new FComboBox(new Object[]{"Ort · Upper Geyser Basin", "Ort · Midway (Grand Prismatic)"});
        where.addActionListener(e -> {
            if (fromSite) return;
            scene.setSite(where.getSelectedIndex(), true);
            scene.requestFocusInWindow();
        });
        scene.setSiteListener(v -> { fromSite = true; where.setSelectedIndex(v); fromSite = false; });
        add(row(where));
        note("Das Upper Geyser Basin am Firehole River, von Old Faithful im Südosten bis Morning Glory im Nordwesten; 6,5 km flussabwärts Midway mit Grand Prismatic und Excelsior. Doppelklick auf eine Stelle macht sie zum Drehpunkt; M wechselt den Ort.");

        gap();
        head("GEYSIRE");
        FComboBox which = new FComboBox(scene.geyserNames());
        add(row(which));
        FButton now = new FButton("Ausbruch jetzt");
        now.addActionListener(e -> { scene.trigger((String) which.getSelectedItem()); scene.requestFocusInWindow(); });
        add(row(now));
        FButton quiz = new FButton("Rätsel · Wann bricht er aus?");
        quiz.addActionListener(e -> scene.openGuess());
        add(row(quiz));
        FButton proto = new FButton("Eruptionsprotokoll");
        proto.addActionListener(e -> { scene.openProtocol(); });
        add(row(proto));
        FCheckBox fast = new FCheckBox("Warten abkürzen (60-fach)");
        fast.addActionListener(e -> { scene.setFast(fast.isSelected()); scene.requestFocusInWindow(); });
        scene.setFastListener(fast::setSelected);
        add(row(fast));
        note("Jeder Geysir ist ein Röhrenmodell: Das Wasser am Grund siedet erst beim Druck der Säule darüber. Schwappt im Vorspiel Wasser über, sinkt der Druck, das Wasser verdampft schlagartig. Die Tafel oben rechts sagt Old Faithful voraus wie die Ranger: 65 min nach Ausbrüchen unter 2½ min, sonst 91 min. „Warten abkürzen“ läuft bis zum nächsten Ausbruch, X löst den Geysir am Drehpunkt aus.");
        note("Gekoppelt: Grand bricht ein bis zwei Minuten nach einem Ausbruch von Turban aus, Beehive gut 13 Minuten nach seinem Indicator, Mortar mit Fan. Splendid schläft seit 1998; bricht er aus, verschiebt er Daisy. Während Giantess wallt Doublet Pool.");
        JLabel waterLbl = new JLabel("Grundwasser  100 %");
        waterLbl.setForeground(INK);
        waterLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        add(row(waterLbl));
        FSlider water = new FSlider(20, 150, 100);
        water.addChangeListener(e -> {
            waterLbl.setText("Grundwasser  " + water.getValue() + " %");
            if (!fromWater) scene.setWater(water.getValue() / 100.0);
        });
        scene.setWaterListener(v -> { fromWater = true; water.setValue((int) Math.round(v * 100)); fromWater = false; });
        add(row(water));
        note("Wie viel Regen und Schnee der letzten Jahre im Boden steckt. In Dürrejahren werden die Abstände länger: Old Faithful 1997 im Mittel 71, 2006 91 Minuten (Hurwitz u. a. 2008). Unter rund 40 % verstummt er, wie in der Dürre des 13. Jahrhunderts (Hurwitz u. a. 2020). Die Tafel rechnet weiter mit der Regel von heute.");

        gap();
        head("STELLEN");
        Object[] names = new Object[Sites.ALL.length + 1];
        names[0] = "Zu einer Stelle fliegen …";
        for (int i = 0; i < Sites.ALL.length; i++) names[i + 1] = (i + 1) + "  ·  " + Sites.ALL[i].name;
        FComboBox go = new FComboBox(names);
        go.addActionListener(e -> {
            int i = go.getSelectedIndex();
            if (i > 0) { scene.goSite(i - 1); go.setSelectedIndex(0); }
            scene.requestFocusInWindow();
        });
        add(row(go));
        FCheckBox lab = new FCheckBox("Beschriftung");
        lab.setSelected(true);
        lab.addActionListener(e -> { scene.setLabels(lab.isSelected()); scene.requestFocusInWindow(); });
        scene.setLabelListener(lab::setSelected);
        add(row(lab));
        note("Die Tafeln stehen an den Koordinaten (WGS 84) und zeigen Kennzahlen und Zustand: Füllung der Röhre, Temperatur am Grund und Siedepunkt dort. Tasten 1 bis 7 für die ersten sieben Stellen, Daisy, Grotto, Fan und Giantess über die Liste; L für die Beschriftung.");

        gap();
        head("REGIE");
        Object[] vps = new Object[Viewpoint.NAMES.length + 1];
        vps[0] = "Blickpunkt …";
        for (int i = 0; i < Viewpoint.NAMES.length; i++) vps[i + 1] = Viewpoint.NAMES[i];
        FComboBox vp = new FComboBox(vps);
        vp.addActionListener(e -> {
            int i = vp.getSelectedIndex();
            if (i > 0) { scene.goViewpoint(i - 1); vp.setSelectedIndex(0); }
            scene.requestFocusInWindow();
        });
        add(row(vp));
        Object[] fl = new Object[Regie.FLIGHTS.length + 1];
        fl[0] = "Kamerafahrt …";
        for (int i = 0; i < Regie.FLIGHTS.length; i++) fl[i + 1] = Regie.FLIGHTS[i];
        FComboBox fly = new FComboBox(fl);
        fly.addActionListener(e -> {
            int i = fly.getSelectedIndex();
            if (i > 0) { scene.playFlight(i - 1); fly.setSelectedIndex(0); }
            scene.requestFocusInWindow();
        });
        add(row(fly));
        FButton walk = new FButton("Rundgang auf den Stegen");
        walk.addActionListener(e -> { scene.playWalk(); scene.requestFocusInWindow(); });
        add(row(walk));
        FButton script = new FButton("Drehbuch · Ein Tag am Old Faithful");
        script.addActionListener(e -> { scene.playScript(); scene.requestFocusInWindow(); });
        add(row(script));
        note("Das Drehbuch zeigt den eingestellten Tag vom Morgengrauen bis zur Nacht, mit Tafeln und Quellen; die Uhrzeiten rechnet es aus dem Sonnenstand. Jede Maus- oder Tasteneingabe übernimmt die Kamera an Ort und Stelle. Tasten G, F, T, B.");

        gap();
        head("MINERALIEN");
        FButton lupe = new FButton("Mineral-Lupe");
        lupe.addActionListener(e -> { scene.openLupe(); scene.requestFocusInWindow(); });
        add(row(lupe));
        FButton grow = new FButton("Sinter-Zeitraffer · Castle");
        grow.addActionListener(e -> { scene.playSinterLapse(); scene.requestFocusInWindow(); });
        add(row(grow));
        FButton glory = new FButton("Morning Glory · 1883 bis heute");
        glory.addActionListener(e -> { scene.playMorningGlory(); scene.requestFocusInWindow(); });
        add(row(glory));
        note("Die Lupe zeigt, woraus die Ablagerungen bestehen, bis auf die Atome aus dem ATOMMODEL: Kieselsinter hier im Becken, zum Vergleich Travertin, Schwefel, Eisenoxid, Skorodit und Alunit. Strg+Klick in die Szene öffnet sie für die Quelle dort, Taste U für den Drehpunkt. Der Zeitraffer baut Terrasse und Kegel von Castle in 43 s auf und hält dann an (Taste Z). Morning Glory zeigt, wie Münzen und Abfall die Quelle abkühlten und die Matten zur Mitte wuchsen (Taste J).");

        gap();
        head("ZUGABEN");
        FCheckBox xTube = new FCheckBox("Schnitt durch die Röhre"), xThermo = new FCheckBox("Wärmebild"),
                xSound = new FCheckBox("Klang"), xFauna = new FCheckBox("Bisons und Wapitis"), xPeople = new FCheckBox("Besucher auf den Stegen");
        xFauna.setSelected(true);
        xPeople.setSelected(true);
        xPeople.addActionListener(e -> { scene.setVisitors(xPeople.isSelected()); scene.requestFocusInWindow(); });
        xTube.addActionListener(e -> { scene.setTube(xTube.isSelected()); scene.requestFocusInWindow(); });
        xThermo.addActionListener(e -> { scene.setThermo(xThermo.isSelected()); scene.requestFocusInWindow(); });
        xSound.addActionListener(e -> { scene.setSound(xSound.isSelected()); scene.requestFocusInWindow(); });
        xFauna.addActionListener(e -> { scene.setFauna(xFauna.isSelected()); scene.requestFocusInWindow(); });
        for (FCheckBox cb : new FCheckBox[]{xTube, xThermo, xSound, xFauna, xPeople}) add(row(cb));
        scene.setExtrasListener(v -> { xTube.setSelected(v[0]); xThermo.setSelected(v[1]); xSound.setSelected(v[2]); xFauna.setSelected(v[3]); xPeople.setSelected(v[4]); });
        note("Der Schnitt zeigt Röhre, Wassersäule und Siedepunkt über der Tiefe für den Geysir am Drehpunkt (C). Das Wärmebild färbt nach der Temperatur des Modells (I). Der Klang wird gerechnet: Säulen, Quellen, Fluss, Wind, zur Brunft Wapitis und Bisons (O). Die Tiere grasen auf den Wiesen, im Winter stehen die Bisons auf warmem Boden (N). Die Besucher sammeln sich vor der Vorhersage am Halbrund um Old Faithful und gehen nach dem Ausbruch; im Juli am meisten, im Winter wenige, bei Regen weniger (F3, Modell).");

        gap();
        head("SONNE UND MOND");
        add(row(dayLbl));
        day.addChangeListener(e -> sunChanged());
        add(row(day));
        add(row(timeLbl));
        time.addChangeListener(e -> sunChanged());
        add(row(time));
        FComboBox lapse = new FComboBox(new Object[]{"Zeitraffer aus", "Zeitraffer · 1 Std. in 10 s", "Zeitraffer · 1 Std. in 2 s", "Zeitraffer · 1 Tag in 30 s"});
        lapse.addActionListener(e -> {
            double[] v = {0, 0.1, 0.5, 0.8};
            scene.setTimelapse(v[lapse.getSelectedIndex()]);
            scene.requestFocusInWindow();
        });
        add(row(lapse));
        note("Uhrzeit wie im Park: Mountain Time, bis 1. November MDT (UTC−6), danach MST. Sonne und Mond stehen für 2026 am richtigen Ort.");
        scene.setTimeListener(v -> {
            fromScene = true;
            day.setValue((int) v[0]);
            time.setValue((int) Math.floor(v[1] * 10));
            labels((int) v[0], v[1]);
            fromScene = false;
        });

        gap();
        head("LUFT");
        add(row(hazeLbl));
        haze.addChangeListener(e -> {
            hazeLbl.setText("Dunst  " + haze.getValue() + " %");
            scene.setHaze(haze.getValue() / 100.0);
        });
        add(row(haze));
        hazeLbl.setText("Dunst  " + haze.getValue() + " %");
        FCheckBox fxRays = new FCheckBox("Lichtstrahlen und Bodennebel"), fxBloom = new FCheckBox("Überstrahlen");
        for (FCheckBox cb : new FCheckBox[]{fxRays, fxBloom}) {
            cb.setSelected(true);
            cb.addActionListener(e -> { scene.setEffects(fxRays.isSelected(), fxBloom.isSelected()); scene.requestFocusInWindow(); });
            add(row(cb));
        }
        FCheckBox bow = new FCheckBox("Regenbogen in der Gischt"), shade = new FCheckBox("Eigenschatten im Dampf");
        bow.setSelected(true);
        shade.setSelected(true);
        bow.addActionListener(e -> { scene.setRainbow(bow.isSelected()); scene.requestFocusInWindow(); });
        shade.addActionListener(e -> { scene.setSteamShadow(shade.isSelected()); scene.requestFocusInWindow(); });
        add(row(bow));
        add(row(shade));
        JLabel fogLbl = new JLabel("Bodennebel  100 %");
        fogLbl.setForeground(INK);
        fogLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        add(row(fogLbl));
        FSlider fog = new FSlider(0, 200, 100);
        fog.addChangeListener(e -> {
            fogLbl.setText("Bodennebel  " + fog.getValue() + " %");
            scene.setFog(fog.getValue() / 100.0);
        });
        add(row(fog));
        JLabel windLbl = new JLabel("Wind  35 %");
        windLbl.setForeground(INK);
        windLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        add(row(windLbl));
        FSlider wind = new FSlider(0, 100, 35);
        wind.addChangeListener(e -> {
            windLbl.setText("Wind  " + wind.getValue() + " %");
            scene.setWind(wind.getValue() / 100.0);
        });
        add(row(wind));
        scene.setAirListener(v -> { haze.setValue((int) Math.round(v[0] * 100)); wind.setValue((int) Math.round(v[1] * 100)); });
        Object[] wm = new Object[com.dan.geyser.effects.Weather.MODES.length];
        for (int i = 0; i < wm.length; i++) wm[i] = "Wetter · " + com.dan.geyser.effects.Weather.MODES[i];
        FComboBox weather = new FComboBox(wm);
        weather.addActionListener(e -> { if (!fromWeather) scene.setWeather(weather.getSelectedIndex()); scene.requestFocusInWindow(); });
        scene.setWeatherListener(v -> { fromWeather = true; weather.setSelectedIndex(v); fromWeather = false; });
        add(row(weather));
        note("Nach Jahreszeit: im Juli und August an etwa jedem dritten Tag nachmittags ein Gewitter, Juli sonst meist trocken, im Winter Schneefall, im Frühjahr und Herbst Regen. Welcher Tag welches Wetter hat, ist ein Modell. Donner kommt mit rund 3 s je km Verspätung. Taste Y wechselt.");
        note("Die Lufttemperatur folgt den Klimanormalwerten 1991–2020 am Old Faithful: je kälter, desto dichter der Dampf; von November bis in den Mai liegt Schnee, an Bäumen nahe den Quellen Raureif. Der Regenbogen steht 42° vom Gegenpunkt der Sonne, bei Vollmond nachts als blasser Mondregenbogen. Dunst ist im Spätsommer oft Rauch von Waldbränden.");

        gap();
        head("BILD");
        FComboBox q = new FComboBox(new Object[]{"Qualität · Auto", "Schnell · 50 %", "Mittel · 75 %", "Hoch · 100 %"});
        q.setSelectedIndex(0);
        q.addActionListener(e -> {
            double[] s = {0, 0.5, 0.75, 1.0};
            int i = q.getSelectedIndex();
            if (i == 0) scene.setAutoQuality(); else scene.setScale(s[i]);
            scene.requestFocusInWindow();
        });
        add(row(q));
        Object[] sty = new Object[com.dan.geyser.core.Engine3D.STYLES.length];
        for (int i = 0; i < sty.length; i++) sty[i] = "Farbstil · " + com.dan.geyser.core.Engine3D.STYLES[i];
        FComboBox style = new FComboBox(sty);
        style.setSelectedIndex(0);
        style.addActionListener(e -> { com.dan.geyser.core.Engine3D.setStyle(style.getSelectedIndex()); scene.requestFocusInWindow(); });
        add(row(style));
        FButton still = new FButton("Standbild speichern");
        still.addActionListener(e -> { scene.requestStill(); scene.requestFocusInWindow(); });
        add(row(still));
        FButton cine = new FButton("Kinomodus");
        cine.addActionListener(e -> { scene.setCinema(true); scene.requestFocusInWindow(); });
        add(row(cine));
        note("Auto hält 30 Bilder/s: in Bewegung mit kleinerem Bild, im Stillstand behält der Bildrechner das Licht aus dem vorigen Bild und rechnet nur neu, was sich bewegt (Dampf, Wasser, Bäume im Wind). Farbstil: Kodachrome kräftig und warm wie alte Dias, Schwarzweiß mit Gelbfilter wie Ansel Adams' Old Faithful von 1942. Standbilder landen doppelt so groß unter Bilder/Geyser; der Kinomodus rechnet nur das Breitbild-Band.");

        gap();
        head("DATENBANK");
        JLabel dbLbl = new JLabel();
        dbLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        dbLbl.setForeground(MUTED);
        add(row(dbLbl));
        scene.db().setStatusListener(st -> SwingUtilities.invokeLater(() -> dbLbl.setText("<html><body style='width:185px'>" + st + "</body></html>")));
        FButton saveAs = new FButton("Zustand speichern unter …"), load = new FButton("Zustand laden …"), all = new FButton("Protokoll aller Sitzungen");
        saveAs.addActionListener(e -> StateDialogs.saveAs(this, scene));
        load.addActionListener(e -> StateDialogs.load(this, scene));
        all.addActionListener(e -> scene.openProtocolAll());
        add(row(saveAs));
        add(row(load));
        add(row(all));
        note("Mit Datenbank (Schema DEMO, Präfix GEY_) kommen Tafeln, Kennwerte der Geysire, Mineralien und Klima aus den Tabellen; jede Sitzung und jeder Ausbruch wird protokolliert, der Zustand beim Beenden gespeichert. Einrichten: db/DbSetup (Run File). Ohne Datenbank läuft alles wie bisher.");

        gap();
        head("TASTEN");
        note("F1 oder H zeigt alle Tasten. Die wichtigsten: 0 Übersicht · Leertaste Rundflug · 1 bis 7 Stellen · G Blickpunkte · F Fahrten · T Rundgang · B Drehbuch · + − Uhrzeit · Y Wetter · J Morning Glory · F2 Rätsel · F3 Besucher · P Standbild · K Kinomodus");
        add(Box.createVerticalGlue());
        sunChanged();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(272, d.height);
    }

    private void labels(int d, double h) {
        timeLbl.setText("Uhrzeit  " + DayNightCycle.timeLabel(h) + " " + DayNightCycle.zone(d, h));
        dayLbl.setText("Tag  " + DayNightCycle.dateLabel(d) + " " + DayNightCycle.YEAR);
    }

    private void sunChanged() {
        double h = time.getValue() / 10.0;
        int d = day.getValue();
        labels(d, h);
        if (!fromScene) scene.setSunTime(d, h);
    }

    private void head(String s) {
        JLabel l = new JLabel(s);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(ACCENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        add(row(l));
    }

    private void note(String s) {
        JLabel l = new JLabel("<html><body style='width:185px'>" + s + "</body></html>");
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        add(row(l));
    }

    private void gap() { add(Box.createVerticalStrut(14)); }

    private JComponent row(Component c) {
        if (c == timeLbl || c == dayLbl || c == hazeLbl) {
            ((JLabel) c).setForeground(INK);
            ((JLabel) c).setFont(new Font("SansSerif", Font.PLAIN, 13));
        }
        if (c instanceof FCheckBox) ((FCheckBox) c).setTextColor(INK);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 4));
        return p;
    }
}
