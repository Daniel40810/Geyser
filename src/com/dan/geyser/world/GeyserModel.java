package com.dan.geyser.world;

/**
 * Ein Geysir als einfaches Röhrenmodell, physikalisch angelehnt:
 * <ol>
 * <li><b>Füllen und Heizen.</b> Die Röhre (Tiefe {@link #tubeDepth}) füllt sich, das Wasser am Grund
 * wird von unten geheizt.</li>
 * <li><b>Siedepunkt nach Druck.</b> Am Grund lastet die Wassersäule auf dem Wasser; es siedet erst bei
 * {@link #boilingPoint(double)} des Luftdrucks auf 2240 m (77 kPa, 92,5 °C) plus dem Druck der Säule.</li>
 * <li><b>Vorspiel.</b> Ist die Röhre voll und das Wasser am Grund fast am Sieden, schwappt Wasser über
 * den Rand. Jedes Überschwappen nimmt ein wenig Säule weg; der Siedepunkt am Grund sinkt.</li>
 * <li><b>Ausbruch.</b> Erreicht das Wasser am Grund den gesenkten Siedepunkt, verdampft es schlagartig
 * und treibt die Säule heraus. Höhe über die Zeit nach einem Verlauf je Art (Kegel, Fontäne mit Stößen).</li>
 * <li><b>Dampfphase und Nachheizen.</b> Wie viel Wasser hinausging, hängt an der Dauer; so viel muss
 * nachfließen und aufgeheizt werden. Die Heizleistung ist so eingestellt, dass die Abstände der
 * beobachteten Regel folgen (bei Old Faithful 65 min nach Ausbrüchen unter 2½ min, sonst 91 min).</li>
 * </ol>
 * Zeiten in Sekunden der Szenenzeit.
 */
public final class GeyserModel {
    public enum Type { CONE, FOUNTAIN }
    public enum Phase { RECHARGE, PREPLAY, ERUPTION, STEAM }

    public final String name;
    public final Type type;
    /** Schlot (x, y, z), Radius des Schlots in Metern, Tiefe der Röhre. */
    public final double x, z;
    public double ventR, tubeDepth;
    public double y;
    /** Größte Höhe der Säule (m). */
    public double hMax;
    /** Dauer der Wasserphase (s): kurz und lang, Anteil kurzer Ausbrüche. */
    public double shortMin, shortMax, longMin, longMax, shortFrac;
    /** Abstand von Beginn zu Beginn (s) nach kurzem und langem Ausbruch, Streuung ±. */
    public double intShort, intLong, intSpread;
    /** Dampfphase (s). */
    public double steamPhase;
    /** Neigung der Säule in Grad und Richtung (Bogenmaß, 0 = Osten, π/2 = Süden). */
    public double tiltDeg;
    public final double tiltDir;
    /** Stöße bei Fontänen: Anzahl von bis. */
    public int burstsMin, burstsMax;
    /** Mindesthöhe, auf der Tropfen landen (etwa der Fluss unter Riverside); NaN = Boden. */
    public double floor = Double.NaN;
    /** Quelle für Bildschirm und Werkbuch. */
    public final String source;

    // ------------------------------------------------------------ Zustand

    public Phase phase = Phase.RECHARGE;
    /** Füllung 0..1 und Temperatur am Grund (°C). */
    public double fill = 1, tBottom = 100;
    /** Zeit in der Phase, geplante Dauer der Wasserphase, Höhenfaktor dieses Ausbruchs. */
    public double tPhase, duration, hFactor = 1;
    /** Beginn des letzten Ausbruchs, Dauer des letzten, vorhergesagter nächster Beginn (Szenenzeit). */
    public double lastStart = Double.NaN, lastDuration = Double.NaN, predicted = Double.NaN;
    /** Heizleistung (K/s) für den aktuellen Zyklus. */
    private double heat = 0.01;
    /** Stöße der Fontäne: Beginn und Ende relativ zum Ausbruch, Höhe je Stoß. */
    private double[] bursts = new double[0];
    /** Aktuelles Überschwappen im Vorspiel: Restzeit und Höhe. */
    private double surgeLeft, surgeH, nextSurge;
    public int eruptions;
    /** true, wenn der laufende Ausbruch von Hand ausgelöst wurde (dann zählt die Vorhersage nicht). */
    public boolean manual;
    private final java.util.Random rnd;

    GeyserModel(String name, Type type, double x, double y, double z, double ventR, double tubeDepth, double hMax,
                double shortMin, double shortMax, double longMin, double longMax, double shortFrac,
                double intShort, double intLong, double intSpread, double steamPhase, double tiltDeg, double tiltDir,
                int burstsMin, int burstsMax, String source, long seed) {
        this.name = name; this.type = type; this.x = x; this.y = y; this.z = z; this.ventR = ventR; this.tubeDepth = tubeDepth;
        this.hMax = hMax; this.shortMin = shortMin; this.shortMax = shortMax; this.longMin = longMin; this.longMax = longMax;
        this.shortFrac = shortFrac; this.intShort = intShort; this.intLong = intLong; this.intSpread = intSpread;
        this.steamPhase = steamPhase; this.tiltDeg = tiltDeg; this.tiltDir = tiltDir; this.burstsMin = burstsMin; this.burstsMax = burstsMax;
        this.source = source;
        this.rnd = new java.util.Random(seed);
    }

    /**
     * Kennwerte aus der Datenbank übernehmen (GEY_GEYSER); Reihenfolge wie die Spalten: Schlotradius,
     * Röhre, Höhe, Dauern kurz min/max, lang min/max, Anteil kurz, Abstände kurz/lang, Streuung,
     * Dampfphase, Neigung, Stöße min/max. Der laufende Zyklus bleibt, der nächste rechnet damit.
     */
    public void setParams(double[] p) {
        ventR = p[0]; tubeDepth = p[1]; hMax = p[2]; shortMin = p[3]; shortMax = p[4]; longMin = p[5]; longMax = p[6];
        shortFrac = p[7]; intShort = p[8]; intLong = p[9]; intSpread = p[10]; steamPhase = p[11]; tiltDeg = p[12];
        burstsMin = (int) p[13]; burstsMax = (int) p[14];
    }

    /** Die Kennwerte in derselben Reihenfolge wie {@link #setParams}. */
    public double[] params() {
        return new double[]{ventR, tubeDepth, hMax, shortMin, shortMax, longMin, longMax, shortFrac, intShort, intLong, intSpread,
                steamPhase, tiltDeg, burstsMin, burstsMax};
    }

    // ------------------------------------------------------------ Physik

    /** Luftdruck auf 2240 m nach der Standardatmosphäre (kPa). */
    public static final double P_ATM = 101.325 * Math.pow(1 - 2.25577e-5 * 2240, 5.25588);
    /** So viel unter dem Siedepunkt am Grund beginnt das Vorspiel (K). */
    static final double PRE = 1.5;
    /** Dichte heißen Wassers (kg/m³). */
    static final double RHO = 960;

    /** Siedepunkt (°C) beim Druck p in kPa, nach der Antoine-Gleichung (zwei Bereiche). */
    public static double boilingPoint(double pKPa) {
        double mmHg = pKPa * 7.50062;
        double lg = Math.log10(mmHg);
        double t = 1730.63 / (8.07131 - lg) - 233.426;
        if (t > 99) t = 1810.94 / (8.14019 - lg) - 244.485;
        return t;
    }

    /** Siedepunkt am Grund der Röhre bei Füllung f (und abgezogener Säule drop in Metern). */
    public double boilAtBottom(double f, double drop) {
        double head = Math.max(0, f * tubeDepth - drop);
        return boilingPoint(P_ATM + RHO * 9.81 * head / 1000);
    }

    /** Beginnt den Zyklus so, dass der nächste Ausbruch nach wait Sekunden etwa beginnt. */
    public void startIn(double now, double wait) {
        phase = Phase.RECHARGE;
        fill = 1;
        // Defizit wie nach einem Ausbruch (18–28 K), Heizleistung so, dass es nach wait Sekunden erreicht ist
        double need = 18 + 10 * rnd.nextDouble();
        heat = need / Math.max(1, wait);
        tBottom = boilAtBottom(1, 0) - PRE - need;
        predicted = now + wait;
        nextSurge = 0;
    }

    /** Löst den Ausbruch jetzt aus (Knopf im Bedienfeld). */
    public void triggerNow(double now) {
        if (phase == Phase.ERUPTION) return;
        fill = 1;
        tBottom = boilAtBottom(1, 0);
        beginEruption(now);
        manual = true;
    }

    /** Ein Zeitschritt dt (Sekunden Szenenzeit). */
    public void step(double now, double dt) {
        tPhase += dt;
        switch (phase) {
            case RECHARGE: {
                fill = Math.min(1, fill + dt / 600.0 * (1.05 - fill));
                tBottom += heat * dt;
                double tb = boilAtBottom(fill, 0);
                if (fill > 0.985 && tBottom > tb - PRE) { phase = Phase.PREPLAY; tPhase = 0; nextSurge = 5 + 20 * rnd.nextDouble(); }
                break;
            }
            case PREPLAY: {
                tBottom += heat * dt;
                double drop = 0;
                if (surgeLeft > 0) {
                    surgeLeft -= dt;
                    drop = 0.25 + surgeH * 0.12;
                } else if (tPhase >= nextSurge) {
                    surgeLeft = 4 + 10 * rnd.nextDouble();
                    surgeH = 1 + 5 * rnd.nextDouble() * rnd.nextDouble();
                    nextSurge = tPhase + surgeLeft + 12 + 40 * rnd.nextDouble();
                }
                if (tBottom >= boilAtBottom(fill, drop)) beginEruption(now);
                break;
            }
            case ERUPTION: {
                fill = Math.max(0.05, fill - dt / Math.max(30, duration) * 0.7);
                if (tPhase >= duration) { phase = Phase.STEAM; tPhase = 0; }
                break;
            }
            case STEAM: {
                if (tPhase >= steamPhase) endCycle(now);
                break;
            }
            default:
        }
    }

    private void beginEruption(double now) {
        manual = false;
        phase = Phase.ERUPTION;
        tPhase = 0;
        surgeLeft = 0;
        boolean shortOne = rnd.nextDouble() < shortFrac;
        duration = shortOne ? lerp(shortMin, shortMax, rnd.nextDouble()) : lerp(longMin, longMax, rnd.nextDouble());
        hFactor = 0.78 + 0.22 * rnd.nextDouble();
        lastStart = now;
        eruptions++;
        if (type == Type.FOUNTAIN) {
            int nb = burstsMin + rnd.nextInt(Math.max(1, burstsMax - burstsMin + 1));
            bursts = new double[3 * nb];
            double gap = duration / nb;
            for (int b = 0; b < nb; b++) {
                double s = b * gap + (b == 0 ? 0 : 0.15 * gap * rnd.nextDouble());
                double e = s + gap * (0.55 + 0.3 * rnd.nextDouble());
                bursts[3 * b] = s; bursts[3 * b + 1] = Math.min(duration, e); bursts[3 * b + 2] = 0.55 + 0.45 * rnd.nextDouble();
            }
        }
        // Vorhersage wie die Ranger: aus der Dauer (erst mit dem Ende bekannt, hier schon geplant)
        predicted = Double.NaN;
    }

    private void endCycle(double now) {
        lastDuration = duration;
        double interval = duration < 150 && intShort > 0 ? intShort : intLong;
        interval += (rnd.nextDouble() * 2 - 1) * intSpread;
        predicted = lastStart + (duration < 150 && intShort > 0 ? intShort : intLong);
        double wait = Math.max(120, lastStart + interval - now);
        // So viel Säule ging hinaus: nachfüllen, dabei kühlt das Wasser am Grund; Heizleistung passt zum Abstand
        phase = Phase.RECHARGE;
        tPhase = 0;
        fill = Math.max(0.2, 0.9 - 0.6 * duration / Math.max(longMax, 1));
        double target = boilAtBottom(1, 0) - PRE;
        double start = target - 18 - 10 * (1 - fill);
        tBottom = start;
        heat = (target - start) / (wait * 0.97);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    // ------------------------------------------------------------ Säule

    /**
     * Höhe der Säule jetzt (m), 0 = still. Kegel: in 12 s auf volle Höhe, gehalten, dann abfallend;
     * Fontäne: Stöße mit Flackern. Im Vorspiel die kleinen Überschwapper, in der Dampfphase ein paar Meter.
     */
    public double height(double t) {
        switch (phase) {
            case PREPLAY:
                return surgeLeft > 0 ? surgeH * (0.6 + 0.4 * Math.sin(t * 3.1) * Math.sin(t * 1.7)) : 0;
            case ERUPTION: {
                double p = tPhase, D = duration, h;
                if (type == Type.CONE) {
                    double rise = smooth(0, 14, p);
                    double fall = 1 - 0.72 * smooth(0.45 * D, D, p);
                    double flick = 0.9 + 0.1 * Math.sin(t * 5.3) * Math.sin(t * 2.1 + 1);
                    h = hMax * hFactor * rise * fall * flick;
                } else {
                    h = 0;
                    for (int b = 0; b < bursts.length; b += 3) {
                        double s = bursts[b], e = bursts[b + 1];
                        if (p < s || p > e) continue;
                        double k = smooth(s, s + 6, p) * (1 - smooth(e - 8, e, p));
                        double jag = 0.55 + 0.45 * Math.abs(Math.sin(t * 2.3 + b) * Math.sin(t * 0.9 + 2 * b));
                        h = Math.max(h, hMax * hFactor * bursts[b + 2] * k * jag);
                    }
                    if (h < 1.5) h = 1.5 + Math.abs(Math.sin(t * 1.3)) * 2;
                }
                return h;
            }
            case STEAM:
                return 4 + 3 * (1 - tPhase / Math.max(1, steamPhase)) * Math.abs(Math.sin(t * 1.1));
            default:
                return 0;
        }
    }

    /** Wie weit die Säule in der Röhre gerade durch Überschwappen abgesenkt ist (m), für den Schnitt. */
    public double surgeDrop() { return phase == Phase.PREPLAY && surgeLeft > 0 ? 0.25 + surgeH * 0.12 : 0; }

    /** Anteil Wasser an der Säule 0..1 (in der Dampfphase fast nur Dampf). */
    public double waterShare() {
        return phase == Phase.STEAM ? 0.08 : 1;
    }

    /** Wie stark dampft die Säule 0..1. */
    public double steamShare() {
        switch (phase) {
            case ERUPTION: return 0.8;
            case STEAM: return Math.max(0.25, 1 - tPhase / Math.max(1, steamPhase));
            case PREPLAY: return 0.25;
            default: return 0.08;
        }
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Kurzer Zustand für Statuszeile und Tafeln. */
    public String state(double now) {
        switch (phase) {
            case ERUPTION: return String.format(java.util.Locale.GERMANY, "Ausbruch · %.0f m · %d:%02d", height(now), (int) tPhase / 60, (int) tPhase % 60);
            case STEAM: return "Dampfphase";
            case PREPLAY: return "Vorspiel · bald";
            default: {
                double tb = boilAtBottom(fill, 0);
                return String.format(java.util.Locale.GERMANY, "Röhre %.0f %% · Grund %.1f °C, siedet bei %.1f °C", fill * 100, tBottom, tb);
            }
        }
    }
}
