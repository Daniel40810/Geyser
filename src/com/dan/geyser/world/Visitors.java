package com.dan.geyser.world;

import com.dan.geyser.core.Animals;
import com.dan.geyser.core.Terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Besucher am Old Faithful. Wenn es voll ist, stehen auf dem Halbrund um den Geysir 500 bis über
 * 1000 Menschen, drei, vier Reihen tief am Geländer; am vollsten ist es im Juli, dann im August und
 * Juni (Reiseführer nach den Zahlen des Parks). Sie kommen vor der vorhergesagten Zeit, warten auf
 * den Bänken und am Geländer, und wenn der Ausbruch vorbei ist, gehen die meisten zurück zum
 * Visitor Center. Ein paar wandern über Geyser Hill.
 * <p>
 * Gezeigt werden höchstens {@link #MAX} Figuren; wie viele, folgt Monat, Uhrzeit, Wetter und
 * Vorhersage (Modell). Die Figuren gehen auf den Stegen aus {@link World#ROUTES}.
 */
public final class Visitors {
    public static final int MAX = 240;
    /** Anteil am Höchststand je Monat, Januar bis Dezember (Modell: Hauptsaison Juni bis August, Winter nur mit Schneemobil und Snowcoach). */
    static final double[] MONTH = {0.08, 0.08, 0.05, 0.03, 0.35, 0.8, 1.0, 0.92, 0.6, 0.25, 0.03, 0.06};

    static final int ARRIVE = 0, WAIT = 1, LEAVE = 2, STROLL = 3;

    static final class Person {
        int state;
        /** Lage auf der Route: Index (mit Bruchteil), Ziel, Seitenversatz, Reihe hinter dem Geländer. */
        double s, target, side, row;
        int route;
        double speed, step, gait, scale, dir = 1;
        float[] jacket, pants;
        double x, y, z, hx = 1, hz;
    }

    private final Terrain t;
    private final Random rnd = new Random(1872);
    private final List<Person> people = new ArrayList<>();
    private double spawnAcc;
    /** Wie viele gerade da sein sollten (für die Statuszeile). */
    public int wanted;

    private static final float[][] JACKETS = {
            {0.50f, 0.04f, 0.03f}, {0.03f, 0.10f, 0.40f}, {0.60f, 0.35f, 0.02f}, {0.05f, 0.22f, 0.08f}, {0.45f, 0.45f, 0.42f},
            {0.02f, 0.02f, 0.03f}, {0.35f, 0.02f, 0.25f}, {0.02f, 0.30f, 0.35f}, {0.70f, 0.60f, 0.40f}, {0.15f, 0.10f, 0.06f}};
    private static final float[][] PANTS = {{0.03f, 0.04f, 0.10f}, {0.02f, 0.02f, 0.02f}, {0.20f, 0.16f, 0.10f}, {0.12f, 0.12f, 0.12f}};

    public Visitors(Terrain t) { this.t = t; }

    /** Soll-Anzahl nach Tag, Stunde, Wetter (0 trocken … 1 Gewitter) und Minuten bis zum vorhergesagten Ausbruch. */
    public static int wanted(int day, double hour, double wet, double minutesToEruption, boolean erupting) {
        int mo = Math.min(11, (int) ((day - 1) / 30.5));
        double h = hour < 7 || hour > 21.5 ? 0 : Math.min(1, Math.min((hour - 7) / 3, (21.5 - hour) / 3));
        double g;
        if (erupting) g = 1;
        else if (Double.isNaN(minutesToEruption)) g = 0.35;
        else g = minutesToEruption < 0 ? 1 : 0.3 + 0.7 * Math.max(0, 1 - minutesToEruption / 35);
        return (int) Math.round(MAX * MONTH[mo] * h * g * (1 - 0.6 * wet));
    }

    private static double[][] route(int r) { return r < World.ROUTES.size() ? World.ROUTES.get(r) : null; }

    /** Ein Zeitschritt. predicted: Minuten bis zum vorhergesagten Ausbruch von Old Faithful (NaN: keiner). */
    public void update(double dt, int day, double hour, double wet, double minutesToEruption, boolean erupting, boolean justEnded) {
        double[][] arc = route(0);
        if (arc == null) return;
        int want = wanted(day, hour, wet, minutesToEruption, erupting);
        wanted = want;
        int atArc = 0;
        for (Person p : people) if (p.route == 0 && p.state != LEAVE) atArc++;
        // nach dem Ausbruch: die meisten gehen
        if (justEnded) for (Person p : people) if (p.state == WAIT && rnd.nextDouble() < 0.75) leave(p, arc);
        // zu viele: einzelne gehen
        if (atArc > want + 8) for (Person p : people) if (p.state == WAIT && rnd.nextDouble() < dt * 0.01) { leave(p, arc); atArc--; }
        // zu wenige: neue kommen, in kleinen Gruppen
        spawnAcc += dt * Math.max(0, want - atArc) * 0.08;
        while (spawnAcc >= 1 && people.size() < MAX) {
            spawnAcc -= 1;
            int g = 1 + rnd.nextInt(3);
            for (int k = 0; k < g && people.size() < MAX; k++) people.add(arrive(arc));
        }
        spawnAcc = Math.min(spawnAcc, 3);
        // ein paar auf Geyser Hill
        double[][] hill = route(2);
        int strollers = 0;
        for (Person p : people) if (p.state == STROLL) strollers++;
        if (hill != null && strollers < want / 8 && rnd.nextDouble() < dt * 0.05) {
            Person p = person();
            p.route = 2; p.state = STROLL; p.s = rnd.nextDouble() * (hill.length - 1); p.dir = rnd.nextBoolean() ? 1 : -1;
            p.side = (rnd.nextDouble() - 0.5) * 1.4;
            people.add(p);
        }
        for (int i = people.size() - 1; i >= 0; i--) {
            Person p = people.get(i);
            double[][] rt = route(p.route);
            if (rt == null) { people.remove(i); continue; }
            boolean walking;
            if (p.state == STROLL) {
                p.s += p.dir * p.speed * dt / 2.5;
                walking = true;
                if (p.s <= 0 || p.s >= rt.length - 1 || (strollers > want / 8 + 2 && rnd.nextDouble() < dt * 0.02)) { people.remove(i); continue; }
            } else {
                double d = p.target - p.s;
                walking = Math.abs(d) > 0.05;
                if (walking) p.s += Math.signum(d) * Math.min(Math.abs(d), p.speed * dt / 2.5);
                else if (p.state == ARRIVE) p.state = WAIT;
                else if (p.state == LEAVE) { people.remove(i); continue; }
            }
            p.gait += ((walking ? 1 : 0) - p.gait) * Math.min(1, dt * 3);
            if (walking) p.step += dt * p.speed * 2 * Math.PI / 1.4;
            place(p, rt, walking);
        }
    }

    private Person person() {
        Person p = new Person();
        p.speed = 1.0 + 0.5 * rnd.nextDouble();
        p.scale = 0.9 + 0.15 * rnd.nextDouble();
        if (rnd.nextDouble() < 0.12) p.scale = 0.6 + 0.15 * rnd.nextDouble();   // Kinder
        p.jacket = JACKETS[rnd.nextInt(JACKETS.length)];
        p.pants = PANTS[rnd.nextInt(PANTS.length)];
        p.step = rnd.nextDouble() * 6.28;
        return p;
    }

    /** Kommt an einem Ende des Halbrunds an (dort geht es zum Visitor Center) und sucht sich einen Platz. */
    private Person arrive(double[][] arc) {
        Person p = person();
        p.route = 0;
        p.state = ARRIVE;
        boolean west = rnd.nextDouble() < 0.6;
        p.s = west ? arc.length - 1 : 0;
        // Plätze: über das ganze Halbrund, zur Mitte (Süden, vor dem Visitor Center) dichter
        double u = 0.5 + (rnd.nextDouble() + rnd.nextDouble() - 1) * 0.55;
        p.target = Math.max(1, Math.min(arc.length - 2, u * (arc.length - 1)));
        p.side = (rnd.nextDouble() - 0.5) * 1.6;
        p.row = rnd.nextDouble() < 0.55 ? 0 : 1 + rnd.nextInt(3);
        return p;
    }

    private void leave(Person p, double[][] arc) {
        p.state = LEAVE;
        p.target = p.s > (arc.length - 1) * 0.45 ? arc.length - 1 : 0;
        p.row = 0;
    }

    /** Lage und Blickrichtung: auf dem Steg, wartend in Reihen am Geländer mit Blick zum Geysir. */
    private void place(Person p, double[][] rt, boolean walking) {
        int i = Math.max(0, Math.min(rt.length - 2, (int) Math.floor(p.s)));
        double f = Math.max(0, Math.min(1, p.s - i));
        double[] a = rt[i], b = rt[i + 1];
        double x = a[0] + (b[0] - a[0]) * f, y = a[1] + (b[1] - a[1]) * f, z = a[2] + (b[2] - a[2]) * f;
        double tx = b[0] - a[0], tz = b[2] - a[2], tl = Math.hypot(tx, tz);
        tx /= tl; tz /= tl;
        // quer zum Steg; beim Halbrund zeigt „innen“ zum Geysir
        double nx = -tz, nz = tx;
        if (p.route == 0 && (nx * -x + nz * -z) < 0) { nx = -nx; nz = -nz; }
        double side = p.side;
        boolean waiting = p.state == WAIT && !walking;
        if (waiting) side = 1.0 - 1.1 * p.row;                 // vorn am Geländer, dahinter in Reihen
        x += nx * side; z += nz * side;
        if (waiting && p.row >= 2) y = t.sample(x, z);          // hinter dem Steg auf dem Boden
        p.x = x; p.y = y; p.z = z;
        if (waiting && p.route == 0) { p.hx = -x; p.hz = -z; }  // zum Geysir (am Ursprung)
        else { p.hx = tx * (p.target >= p.s || p.state == STROLL && p.dir > 0 ? 1 : -1); p.hz = tz * (p.target >= p.s || p.state == STROLL && p.dir > 0 ? 1 : -1); }
    }

    /** Hängt die Besucher für das nächste Bild an. */
    public void fill(Animals out) {
        for (Person p : people) {
            int i = out.add(Animals.PERSON, p.x, p.y, p.z, p.hx, p.hz, p.scale);
            if (i < 0) return;
            out.step[i] = (float) p.step; out.gait[i] = (float) p.gait;
            out.cr[i] = p.jacket[0]; out.cg[i] = p.jacket[1]; out.cb[i] = p.jacket[2];
            out.pr[i] = p.pants[0]; out.pg[i] = p.pants[1]; out.pb[i] = p.pants[2];
        }
    }

    public int count() { return people.size(); }
}
