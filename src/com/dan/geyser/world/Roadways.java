package com.dan.geyser.world;

import com.dan.geyser.core.Animals;
import com.dan.geyser.core.Terrain;
import com.dan.road.Ground;
import com.dan.road.Mover;
import com.dan.road.Network;
import com.dan.road.Roads;
import com.dan.road.Way;
import com.dan.road.WayType;

/**
 * Straßen und Wege mit dem Wege-Paket ({@link com.dan.road}):
 * <ul>
 *   <li>die Grand Loop Road: von Süden am Old Faithful vorbei, am Westrand des Upper Geyser Basin nach
 *       Norden, über den Firehole, am Ostufer nach Midway und weiter westlich am Fountain Paint Pot
 *       vorbei nach Norden, mit Autos, Wohnmobilen, Bussen, Motorrädern und Radfahrern;</li>
 *   <li>der Fairy Falls Trail südlich von Midway: eine alte Straße, heute ein Kiesweg für Wanderer und
 *       Radfahrer, über eine Brücke auf das Westufer;</li>
 *   <li>der Pfad vom Fairy Falls Trail hinauf zur Aussichtsplattform über Grand Prismatic;</li>
 *   <li>der Pfad von Geyser Hill hinauf zum Observation Point.</li>
 * </ul>
 * Die Linien sind nach Karten genähert. Das Gelände wird unter den Wegen geebnet, daneben Damm und
 * Einschnitt; aus der Ferne malt die Bodenfarbe die Fahrbahn. Im Lower Geyser Basin quert ab und zu
 * eine kleine Bisonherde die Straße und bleibt eine Weile darauf stehen: Die Autos stauen sich, das
 * erste schaltet die Warnblinker ein.
 */
public final class Roadways implements Terrain.Shaper {
    public final Network net = new Network();
    public Roads roads;
    public final Way loop, fairy, overlook, observation;
    private final Terrain t;

    /** Grand Loop Road (x, z), von Süden nach Norden. */
    static final double[] LOOP = {
            1150, 1900, 700, 950, 330, 520, -60, 300, -420, 130, -760, -120, -1060, -430, -1250, -800, -1400, -1050,
            -1450, -1300, -1490, -1550, -1520, -1850, -1560, -2500, -1600, -3250, -1470, -4200, -1400, -4900, -1150, -5700, -950, -6300,
            -640, -6800, -470, -7250, -380, -7700, -300, -8300, -150, -8900, 150, -9250, 600, -9600, 1100, -9850, 1430, -9960,
            1560, -10400, 1150, -10750, 650, -11250, 200, -11800, 90, -12450};
    /** Fairy Falls Trail: vom Parkplatz an der Straße über den Fluss nach Nordwesten. */
    static final double[] FAIRY = {-930, -6360, -1060, -6420, -1230, -6480, -1300, -6650, -1170, -6900, -1040, -7150, -1000, -7450, -1150, -7750, -1350, -7950};
    /** Pfad zur Plattform über Grand Prismatic, am Hang in Kehren. */
    static final double[] OVERLOOK_PATH = {-1165, -6935, -1085, -6925, -1025, -6965, -975, -6905, -925, -6890};
    /** Pfad von Geyser Hill zum Observation Point, in Kehren den Hang hinauf. */
    static final double[] OBSERVATION = {40, -205, 95, -300, 55, -390, 140, -455, 80, -540, 160, -600, 120, -660};

    /** Terrasse am Fuß des Talrands westlich der Daisy-Gruppe (x, z, Höhe): dort läuft die Straße. */
    static final double[] BENCH = {-1250, -800, 1, -1330, -930, 2, -1400, -1060, 2, -1440, -1200, 1, -1465, -1350, -1, -1490, -1550, -5, -1515, -1750, -9};

    Roadways(Terrain t) {
        this.t = t;
        for (int k = 0; k < BENCH.length; k += 3) t.pad(BENCH[k], BENCH[k + 1], 45, BENCH[k + 2], 110);
        loop = net.add(new Way(WayType.road(), false, LOOP));
        WayType tr = WayType.track();
        tr.name = "Fairy Falls Trail";
        fairy = net.add(new Way(tr, false, FAIRY));
        WayType p = WayType.path();
        overlook = net.add(new Way(p, false, OVERLOOK_PATH));
        observation = net.add(new Way(p.copy(), false, OBSERVATION));
        loop.type.name = "Grand Loop Road";
        loop.type.speedLimit = 20;       // 45 mph
        loop.type.age = 0.6f;
        // Profil nach der Formel des Geländes (vor dem Bau der Gitter)
        double[] o = new double[6];
        net.build(new Ground() {
            @Override public float height(double x, double z) { return (float) t.exact(x, z); }
            @Override public float water(double x, double z) {
                t.nearest(x, z, o);
                return o[0] < o[2] + 1 ? (float) o[1] : Float.NaN;
            }
        });
        t.shaper = this;
    }

    /** Nach dem Bau des Geländes: Verkehr und Geometrie auf dem sichtbaren Boden. */
    void attach() {
        roads = new Roads(net, (x, z) -> t.sample(x, z), 1872);
        roads.radius = 1500;
        // die Nacht der Szene ist dunkler belichtet als die Vorschau: stärkere Scheinwerfer
        roads.headlight = 1500;
        roads.glowScale = 0.05f;
        roads.traffic.flow[loop.index] = 160;
        roads.traffic.slowFlow[loop.index] = 6;
        roads.traffic.flow[fairy.index] = 0;
        roads.traffic.slowFlow[fairy.index] = 30;
        roads.traffic.slowFlow[overlook.index] = 40;
        roads.traffic.slowFlow[observation.index] = 20;
        roads.traffic.rates();
        roads.traffic.populate();
        herdSpot();
    }

    // ------------------------------------------------------------ Gelände

    @Override public double shape(double x, double z, double h) { return net.shape(x, z, h); }

    private static final ThreadLocal<Network.Hit> HIT = ThreadLocal.withInitial(Network.Hit::new);

    @Override public void paint(float x, float z, float foot, float[] rgb) {
        Network.Hit h = HIT.get();
        if (!net.locate(x, z, h) || h.cover <= 0.001f) return;
        WayType ty = h.way.type;
        float[] c = ty.paving == WayType.Paving.ASPHALT ? ty.worn : ty.surface;
        float k = h.cover * (ty.kind == WayType.Kind.PATH ? 0.7f : 0.95f);
        float snow = com.dan.geyser.core.Thermal.snow;
        // geräumt: im Winter bleibt die Straße dunkel, Wege liegen unter Schnee
        if (snow > 0.01f && ty.paving != WayType.Paving.ASPHALT) k *= 1 - Math.min(1, snow * 2);
        float r = c[0] * 0.9f, g = c[1] * 0.9f, b = c[2] * 0.9f;
        if (snow > 0.01f && ty.paving == WayType.Paving.ASPHALT) { r += 0.2f * snow; g += 0.2f * snow; b += 0.22f * snow; }
        rgb[0] += (r - rgb[0]) * k; rgb[1] += (g - rgb[1]) * k; rgb[2] += (b - rgb[2]) * k;
    }

    /** Wie weit (x, z) befestigt ist (für Gras und Bäume). */
    public float cover(double x, double z) { return net.cover(x, z); }

    /** Abstand von (x, z) zum Rand des nächsten Weges (m), höchstens 30; negativ darauf. */
    public float clearance(double x, double z) {
        Network.Hit h = HIT.get();
        if (!net.locate(x, z, h)) return 30;
        return (float) (Math.abs(h.u) - h.way.type.half());
    }

    // ------------------------------------------------------------ Bisonherde an der Straße

    private double herdS;
    private final int herdN = 8;
    private final double[] bx = new double[herdN], bz = new double[herdN], bhx = new double[herdN], bhz = new double[herdN];
    private final double[] tx = new double[herdN], tz = new double[herdN], step = new double[herdN], gait = new double[herdN];
    private final java.util.Random rnd = new java.util.Random(88);
    /** 0 grasen neben der Straße, 1 hinüber auf die Straße, 2 auf der Straße, 3 weiter auf die andere Seite. */
    private int phase;
    private double timer = 120, side = 1;
    private final float[] F = new float[6];
    private final float[] obs = new float[3 * 64];

    /** Die Stelle im Lower Geyser Basin, an der die Herde die Straße quert: offener Talboden westlich des Fountain Paint Pot. */
    private void herdSpot() {
        Network.Hit h = new Network.Hit();
        net.locate(1100, -9870, h);
        herdS = h.way == loop ? h.s : loop.length * 0.8;
        for (int i = 0; i < herdN; i++) {
            target(i, 30 + rnd.nextDouble() * 25);
            bx[i] = tx[i]; bz[i] = tz[i];
            double a = rnd.nextDouble() * 6.28;
            bhx[i] = Math.cos(a); bhz[i] = Math.sin(a);
        }
    }

    /** Ziel des Tieres i: seitlich der Straße im Abstand u (Vorzeichen: Seite), längs verstreut. */
    private void target(int i, double u) {
        loop.at(herdS + (i - herdN / 2.0) * 7 + (rnd.nextDouble() - 0.5) * 6, side * u, F);
        tx[i] = F[0] + (rnd.nextDouble() - 0.5) * 3; tz[i] = F[2] + (rnd.nextDouble() - 0.5) * 3;
    }

    private void herd(double dt) {
        timer -= dt;
        if (timer <= 0) {
            phase = (phase + 1) % 4;
            switch (phase) {
                case 0 -> { timer = 240 + rnd.nextDouble() * 420; for (int i = 0; i < herdN; i++) target(i, 28 + rnd.nextDouble() * 25); }
                case 1 -> { timer = 50; side = -side; for (int i = 0; i < herdN; i++) target(i, -side * (rnd.nextDouble() - 0.5) * 6); }
                case 2 -> { timer = 90 + rnd.nextDouble() * 120; }
                case 3 -> { timer = 70; for (int i = 0; i < herdN; i++) target(i, 26 + rnd.nextDouble() * 20); }
            }
        }
        // auf der Straße treten sie ab und zu ein paar Schritte
        if (phase == 2 && rnd.nextDouble() < dt * 0.05) { int i = rnd.nextInt(herdN); target(i, -side * (rnd.nextDouble() - 0.5) * 7); }
        if (phase == 0 && rnd.nextDouble() < dt * 0.03) { int i = rnd.nextInt(herdN); target(i, 28 + rnd.nextDouble() * 25); }
        for (int i = 0; i < herdN; i++) {
            double dx = tx[i] - bx[i], dz = tz[i] - bz[i], d = Math.hypot(dx, dz);
            boolean walk = d > 0.8;
            if (walk) {
                double k = Math.min(1, dt * 1.2);
                bhx[i] += (dx / d - bhx[i]) * k; bhz[i] += (dz / d - bhz[i]) * k;
                double l = Math.hypot(bhx[i], bhz[i]);
                bhx[i] /= l; bhz[i] /= l;
                double v = 0.6 * Math.max(0, bhx[i] * dx / d + bhz[i] * dz / d);
                bx[i] += bhx[i] * v * dt; bz[i] += bhz[i] * v * dt;
                step[i] += dt * v * 2 * Math.PI / 1.5;
            }
            gait[i] += ((walk ? 1 : 0) - gait[i]) * Math.min(1, dt * 2);
        }
    }

    /** Die Herde steht sofort auf der Straße (für Bilder und Blickpunkte). */
    public void herdOnRoad() {
        phase = 2;
        timer = 150;
        for (int i = 0; i < herdN; i++) {
            target(i, (rnd.nextDouble() - 0.5) * 6);
            bx[i] = tx[i]; bz[i] = tz[i];
        }
    }

    /** Die Herde an der Straße ins Bild. */
    public void fillAnimals(Animals out) {
        for (int i = 0; i < herdN; i++) {
            int k = out.add(Animals.BISON, bx[i], t.sample(bx[i], bz[i]), bz[i], bhx[i], bhz[i], 0.9 + 0.03 * (i % 5));
            if (k < 0) return;
            out.step[k] = (float) step[i]; out.gait[k] = (float) gait[i]; out.graze[k] = (float) (1 - gait[i]);
        }
    }

    /** Wo die Herde gerade ist (x, z), für Blickpunkte. */
    public double[] herdCenter() {
        double x = 0, z = 0;
        for (int i = 0; i < herdN; i++) { x += bx[i]; z += bz[i]; }
        return new double[]{x / herdN, z / herdN};
    }

    // ------------------------------------------------------------ Schritt

    /**
     * Ein Zeitschritt: Wetter (Regen 0..1, Schnee 0..1, Dunkelheit 0..1), Tag im Jahr (Schneestangen
     * im Winterhalbjahr), Tiere der Szene (Bisons und Wapitis auf der Fahrbahn halten den Verkehr an),
     * Zeitraffer und Kamera.
     */
    public void update(float time, double dt, int day, float rain, float snow, float dark, float wind, double wx, double wz,
                       Animals animals, double speed, double camX, double camY, double camZ) {
        Roads r = roads;
        if (r == null) return;
        herd(dt * speed);
        var w = r.weather;
        w.rain = rain; w.snow = snow; w.dark = dark;
        w.windSpeed = wind * 9;
        w.windDirection = (float) Math.toDegrees(Math.atan2(wz, wx));
        w.timeScale = (float) speed;
        w.poles = day > 285 || day < 140;
        // Hindernisse: Tiere auf oder an der Fahrbahn
        int n = 0;
        for (int i = 0; i < herdN && n < 64; i++) if (clearance(bx[i], bz[i]) < 1.5f) { obs[3 * n] = (float) bx[i]; obs[3 * n + 1] = (float) bz[i]; obs[3 * n + 2] = 1.8f; n++; }
        if (animals != null)
            for (int i = 0; i < animals.n && n < 64; i++) {
                if (animals.kind[i] == Animals.PERSON) continue;
                if (clearance(animals.x[i], animals.z[i]) < 1.5f) { obs[3 * n] = animals.x[i]; obs[3 * n + 1] = animals.z[i]; obs[3 * n + 2] = 1.8f; n++; }
            }
        r.traffic.obstacles(obs, n);
        // hoch über dem Boden: nur der Verkehr, keine feinen Teile
        r.update(time, (float) (dt * Math.min(speed, 4)), camX, camY, camZ);
    }

    /** Dunkelheit 0..1 aus der Sonnenhöhe (Grad): ab etwa 3° über dem Horizont bis 7° darunter. */
    public static float dark(double elevDeg) {
        double u = Math.max(0, Math.min(1, (elevDeg + 7) / 10));
        return (float) (1 - u * u * (3 - 2 * u));
    }

    /** Das Fahrzeug, das der Kamera am nächsten ist (für Blicke), oder null. */
    public Mover nearest(double x, double z) {
        Mover best = null;
        double bd = Double.MAX_VALUE;
        for (Mover m : roads.traffic.movers) {
            if (m.kind.walks() || m.kind.pedals()) continue;
            double d = Math.hypot(m.x - x, m.z - z);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }
}
