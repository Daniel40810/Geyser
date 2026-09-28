package com.dan.geyser.tools;

import com.dan.geyser.camera.Viewpoint;
import com.dan.geyser.core.Animals;
import com.dan.geyser.core.Camera;
import com.dan.geyser.core.Engine3D;
import com.dan.geyser.core.Thermal;
import com.dan.geyser.effects.Climate;
import com.dan.geyser.effects.DayNightCycle;
import com.dan.geyser.effects.LightingEngine;
import com.dan.geyser.effects.ParticleSystem;
import com.dan.geyser.world.Basin;
import com.dan.geyser.world.Fauna;
import com.dan.geyser.world.GeyserModel;
import com.dan.geyser.world.World;

import javax.imageio.ImageIO;
import java.io.File;

/**
 * Standbilder ohne Fenster, zum Beispiel für die Bilder im README. Jeder Auftrag ist ein Argument:
 * <pre>
 *   datei|Blickpunkt|Tag im Jahr|Uhrzeit|Geysir oder -|Sekunden nach dem Auslösen|Farbstil|Breite|Höhe|Wetter|Jahr
 * </pre>
 * Wetter ist die Nummer aus {@link com.dan.geyser.effects.Weather#MODES} (fehlt es: klar), Jahr das
 * Jahr für Morning Glory Pool (fehlt es: heute).
 * Blickpunkt ist die Nummer oder der Name aus {@link Viewpoint#NAMES}, oder frei
 * {@code ex,ey,ez,tx,ty,tz} (y über Gelände) mit dem Ort als Nachsatz {@code @1} für Midway, {@code @2} für das Lower Geyser Basin. Beispiel:
 * {@code docs/bilder/readme/old_faithful.png|1|200|8.3|Old Faithful|40|0|1600|900}. Oder eine Einstellung
 * des Drehbuchs: {@code drehbuch:3:12} (Einstellung 3, Sekunde 12) mit Uhrzeit {@code -} und Geysir
 * {@code auto} nimmt Uhrzeit, Ort und Ausbruch aus dem Drehbuch.
 * Das Gelände wird einmal gebaut; die Geysire laufen je Auftrag bis zum gewünschten Augenblick.
 */
public final class StillRender {
    private StillRender() { }

    public static void main(String[] args) throws Exception {
        World w = Basin.build();
        Viewpoint[] vps = Viewpoint.all(w.scene.terrain);
        for (String job : args) {
            String[] a = job.split("\\|");
            String file = a[0];
            int day = Integer.parseInt(a[2]);
            double hour = "-".equals(a[3]) ? Double.NaN : Double.parseDouble(a[3]);
            String gey = a.length > 4 ? a[4] : "-";
            double secs = a.length > 5 ? Double.parseDouble(a[5]) : 0;
            int style = a.length > 6 ? Integer.parseInt(a[6]) : 0;
            int W = a.length > 7 ? Integer.parseInt(a[7]) : 1600, H = a.length > 8 ? Integer.parseInt(a[8]) : 900;
            com.dan.geyser.effects.Weather wx = new com.dan.geyser.effects.Weather();
            wx.mode = a.length > 9 ? Integer.parseInt(a[9]) : com.dan.geyser.effects.Weather.CLEAR;
            // Blickpunkt
            double[] pose;
            int site = 0;
            String v = a[1];
            if (v.startsWith("drehbuch:")) {
                // Einstellung N des Drehbuchs zur Sekunde u: Kamera, Uhrzeit, Ort und Ausbruch wie in der App
                String[] q = v.split(":");
                com.dan.geyser.camera.Director.Shot sh = com.dan.geyser.camera.Regie.script(w.scene.terrain, day).shots.get(Integer.parseInt(q[1]) - 1);
                double u = Double.parseDouble(q[2]);
                pose = new double[6];
                sh.path.sample(u, pose);
                if (Double.isNaN(hour) && !Double.isNaN(sh.h0)) hour = sh.h0 + (sh.h1 - sh.h0) * u / sh.path.duration();
                if (sh.site >= 0) site = sh.site;
                if ("auto".equals(gey)) { gey = sh.trigger == null ? "-" : sh.trigger; secs = Math.max(0, u - sh.triggerAt); }
            } else if (v.contains(",")) {
                String[] q = v.split("@");
                if (q.length > 1) site = Integer.parseInt(q[1]);
                String[] c = q[0].split(",");
                pose = new double[6];
                for (int i = 0; i < 6; i++) pose[i] = Double.parseDouble(c[i]);
                pose[1] += w.scene.terrain.sample(pose[0], pose[2]);
                pose[4] += w.scene.terrain.sample(pose[3], pose[5]);
            } else {
                Viewpoint vp = null;
                try { vp = vps[Integer.parseInt(v)]; } catch (NumberFormatException e) { for (Viewpoint x : vps) if (x.name.equals(v)) vp = x; }
                if (vp == null) throw new IllegalArgumentException("Blickpunkt unbekannt: " + v);
                pose = vp.pose;
                site = vp.site;
            }
            double[] cc = World.center(site);
            LightingEngine.centerX = site == 0 ? LightingEngine.FCX : cc[0];
            LightingEngine.centerZ = site == 0 ? LightingEngine.FCZ : cc[1];
            // Klima wie in der App
            double tair = Climate.air(day, hour);
            Thermal.setDay(day);
            Thermal.ambient = (float) Math.max(0, tair + 4);
            Thermal.snow = (float) Climate.snow(day);
            Thermal.rime = (float) Math.max(0, Math.min(1, (-2 - tair) / 10));
            w.geysers.steamVis = (float) Climate.steam(tair);
            float[] wt = wx.target(day, hour, tair);
            wx.overcast = wt[0]; wx.rain = wt[1]; wx.snow = wt[2];
            com.dan.geyser.effects.Sky.overcastNext = wx.overcast;
            Thermal.Spring mgp = w.scene.thermal.byName("Morning Glory Pool");
            mgp.t0 = a.length > 10 ? com.dan.geyser.world.MorningGlory.tempAt(Double.parseDouble(a[10])) : com.dan.geyser.world.MorningGlory.NOW_T;
            w.scene.thermal.changed(mgp);
            Engine3D.setStyle(style);
            DayNightCycle dc = new DayNightCycle();
            dc.set(day, hour);
            // Geysire: alle ruhig, einer bricht aus; Dampf der Quellen eine Weile anlaufen lassen
            ParticleSystem ps = new ParticleSystem();
            for (GeyserModel g : w.geysers.list) g.startIn(0, 99999);
            double clock = 0, dt = 1 / 30.0;
            float sunK = (float) Math.max(0, dc.elevationDeg / 40.0);
            for (int k = 0; k < 600; k++) { clock += dt; w.geysers.update(clock, dt, (float) dt, ps, 2.5f, 1.9f, sunK); }
            if (!"-".equals(gey)) {
                GeyserModel g = w.geysers.byName(gey);
                if (g == null) throw new IllegalArgumentException("Geysir unbekannt: " + gey);
                g.triggerNow(clock);
                for (int k = 0; k < secs * 30; k++) { clock += dt; w.geysers.update(clock, dt, (float) dt, ps, 2.5f, 1.9f, sunK); }
            }
            // Regen und Schnee um die Kamera einschwingen lassen
            if (wx.rain > 0 || wx.snow > 0) {
                for (int k = 0; k < 150; k++) {
                    wx.emit(ps, w.scene.terrain, pose[0], pose[1], pose[2], (float) dt, 2.5f, 1.9f);
                    ps.step((float) dt, 2.5f, 1.9f, w.scene.terrain, w.geysers.wet);
                }
            }
            Engine3D r = new Engine3D(w.scene, 4096);
            r.particles = ps;
            r.wetness = w.geysers.wet;
            r.plumes = w.geysers.plumes;
            r.wind = 0.35;
            Fauna fa = new Fauna(w.scene.terrain, w.scene.thermal);
            for (int k = 0; k < 300; k++) fa.update(0.1, day, Thermal.snow);
            Animals an = new Animals();
            fa.fill(an);
            // Besucher: kurz vor dem vorhergesagten Ausbruch versammelt (beim Ausbruch von Old Faithful)
            com.dan.geyser.world.Visitors vis = new com.dan.geyser.world.Visitors(w.scene.terrain);
            boolean ofOn = "Old Faithful".equals(gey);
            for (int k = 0; k < 4000; k++) vis.update(0.25, day, hour, Math.max(wx.rain, wx.snow * 0.5), ofOn ? 2 : 40, false, false);
            vis.fill(an);
            r.animals = an;
            // Laub nach der Jahreszeit; im Herbst eine Minute Blätterfall um die Kamera
            w.grove.setSeason(day, Thermal.snow);
            for (int k = 0; k < 1800; k++) w.grove.update(dt, 0.35, 0.8, 0.6, pose[0], pose[2], w.scene.terrain);
            r.leaves = w.grove.quads;
            r.setSky(dc, Math.min(1, 0.12 + 0.3 * wx.overcast));
            r.rainWet = wx.rain * 0.8f;
            r.day = day; r.hour = hour; r.sidereal = dc.siderealDeg;
            r.setSize(W, H);
            Camera cam = new Camera();
            cam.ex = pose[0]; cam.ey = pose[1]; cam.ez = pose[2];
            cam.lookAt(pose[3], pose[4], pose[5]);
            r.resetExposure();
            for (int k = 0; k < 3; k++) r.render(cam, clock, 0);
            java.awt.image.BufferedImage img = r.render(cam, clock, 0);
            File f = new File(file);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
            System.out.println(file + "  " + W + " × " + H + "  " + DayNightCycle.dateLabel(day) + " " + DayNightCycle.timeLabel(hour));
        }
    }
}
