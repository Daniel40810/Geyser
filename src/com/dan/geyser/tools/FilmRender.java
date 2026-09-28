package com.dan.geyser.tools;

import com.dan.geyser.camera.Director;
import com.dan.geyser.camera.Regie;
import com.dan.geyser.core.Animals;
import com.dan.geyser.core.Camera;
import com.dan.geyser.core.Engine3D;
import com.dan.geyser.core.Thermal;
import com.dan.geyser.effects.Climate;
import com.dan.geyser.effects.DayNightCycle;
import com.dan.geyser.effects.GeyserSound;
import com.dan.geyser.effects.LightingEngine;
import com.dan.geyser.effects.ParticleSystem;
import com.dan.geyser.effects.Weather;
import com.dan.geyser.ui.Captions;
import com.dan.geyser.world.Basin;
import com.dan.geyser.world.Fauna;
import com.dan.geyser.world.GeyserModel;
import com.dan.geyser.world.SoundScape;
import com.dan.geyser.world.Visitors;
import com.dan.geyser.world.World;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Das Drehbuch „Ein Tag am Old Faithful“ als Film, ohne Fenster: Bild für Bild als PNG-Folge mit
 * den Tafeln und Schwarzblenden, dazu die Tonspur als WAV aus dem Klang der App. Die Szene läuft
 * genau wie im Fenster (Geysire, Teilchen, Licht, Tiere, Besucher, Wetter), nur in festen Schritten
 * von 1/fps Sekunden. Zum Schluss steht der ffmpeg-Aufruf da, der daraus ein MP4 macht.
 * <pre>
 *   java … com.dan.geyser.tools.FilmRender ordner [tag] [bilder/s] [breite] [höhe] [farbstil] [von s] [bis s] [wetter]
 * </pre>
 * Beispiel: {@code film 200 30 1920 1080 0} rechnet den 19. Juli in Full HD. Mit von und bis nur
 * einen Ausschnitt (die Szene läuft vom Anfang an mit, gerechnet wird nur das Bild im Ausschnitt).
 */
public final class FilmRender {
    private FilmRender() { }

    public static void main(String[] a) throws Exception {
        if (a.length < 1) {
            System.out.println("Aufruf: FilmRender ordner [tag] [bilder/s] [breite] [höhe] [farbstil] [von s] [bis s] [wetter]");
            return;
        }
        File dir = new File(a[0]);
        int day = a.length > 1 ? Integer.parseInt(a[1]) : DayNightCycle.today();
        int fps = a.length > 2 ? Integer.parseInt(a[2]) : 30;
        int W = a.length > 3 ? Integer.parseInt(a[3]) : 1920, H = a.length > 4 ? Integer.parseInt(a[4]) : 1080;
        int style = a.length > 5 ? Integer.parseInt(a[5]) : 0;
        double from = a.length > 6 ? Double.parseDouble(a[6]) : 0, to = a.length > 7 ? Double.parseDouble(a[7]) : Double.MAX_VALUE;
        int wmode = a.length > 8 ? Integer.parseInt(a[8]) : Weather.CLEAR;
        dir.mkdirs();

        World w = Basin.build();
        Engine3D.setStyle(style);
        Director dr = new Director(w.scene.terrain);
        Director.Program prog = Regie.script(w.scene.terrain, day);
        dr.play(prog);
        double total = prog.duration();
        to = Math.min(to, total);
        System.out.printf(Locale.GERMANY, "%s · %s · %.1f s · %d Bilder/s · %d × %d%n", prog.title, DayNightCycle.dateLabel(day), total, fps, W, H);

        ParticleSystem ps = new ParticleSystem();
        for (GeyserModel g : w.geysers.list) g.startIn(0, g.name.equals("Old Faithful") ? 3600 : (0.2 + 0.8 * Math.random()) * Math.max(3600, g.intLong));
        Engine3D r = new Engine3D(w.scene, 4096);
        r.particles = ps;
        r.wetness = w.geysers.wet;
        r.setSize(W, H);
        Fauna fauna = new Fauna(w.scene.terrain, w.scene.thermal);
        Visitors visitors = new Visitors(w.scene.terrain);
        Animals animals = new Animals();
        Weather weather = new Weather();
        weather.mode = wmode;
        GeyserSound sound = new GeyserSound();
        int spf = GeyserSound.RATE / fps;
        java.io.ByteArrayOutputStream pcm = new java.io.ByteArrayOutputStream();
        short[] buf = new short[2 * spf];

        Camera cam = new Camera();
        DayNightCycle dc = new DayNightCycle();
        double hour = 9, litHour = -99, clock = 0, dt = 1.0 / fps, wind = 0.35;
        int site = -1, frame = 0;
        long t0 = System.nanoTime();
        for (double t = 0; t < to; t += dt) {
            boolean on = dr.update(dt, cam);
            Director.Shot st = dr.takeStarted();
            if (st != null && st.site >= 0 && st.site != site) {
                site = st.site;
                double[] c = World.center(site);
                LightingEngine.centerX = site == 0 ? LightingEngine.FCX : c[0];
                LightingEngine.centerZ = site == 0 ? LightingEngine.FCZ : c[1];
                litHour = -99;
            }
            if (st != null) r.resetExposure();
            String tg = dr.takeTrigger();
            if (tg != null) { GeyserModel g = w.geysers.byName(tg); if (g != null) g.triggerNow(clock); }
            double dh = dr.hour();
            if (!Double.isNaN(dh)) hour = dh;
            // Klima wie in der App
            double tair = Climate.air(day, hour);
            Thermal.setDay(day);
            Thermal.ambient = (float) Math.max(0, tair + 4);
            Thermal.snow = (float) Climate.snow(day);
            Thermal.rime = (float) Math.max(0, Math.min(1, (-2 - tair) / 10));
            w.geysers.steamVis = (float) Climate.steam(tair);
            weather.step(dt, day, hour, tair, cam.ex, cam.ez);
            com.dan.geyser.effects.Sky.overcastNext = weather.overcast;
            boolean render = t >= from;
            if (render && Math.abs(hour - litHour) > 1 / 60.0) {
                dc.set(day, hour);
                r.setSky(dc, Math.min(1, 0.12 + 0.3 * weather.overcast));
                litHour = hour;
            }
            clock += dt;
            float wx = (float) (0.8 * wind * 9), wz = (float) (0.6 * wind * 9);
            weather.emit(ps, w.scene.terrain, cam.ex, cam.ey, cam.ez, (float) dt, wx, wz);
            w.geysers.update(clock, dt, (float) dt, ps, wx, wz, (float) Math.max(0, dc.elevationDeg / 40.0));
            r.plumes = w.geysers.plumes;
            r.wind = wind;
            r.rainWet = weather.rain * 0.8f;
            r.flash = weather.flash;
            r.bolt = weather.bolt;
            animals.clear();
            fauna.update(dt, day, Thermal.snow);
            fauna.fill(animals);
            GeyserModel of = w.geysers.byName("Old Faithful");
            boolean er = of.phase == GeyserModel.Phase.ERUPTION;
            visitors.update(dt, day, hour, weather.rain, er ? Double.NaN : 5, er, false);
            visitors.fill(animals);
            r.animals = animals.n > 0 ? animals : null;
            // Ton: Pegel aus dem Ort der Kamera, 1/fps Sekunden Proben
            SoundScape.levels(sound, w.geysers, w.scene.terrain, cam, clock, wind);
            sound.rain = weather.rain * 0.8f;
            sound.synth(buf, spf);
            for (short v : buf) { pcm.write(v & 255); pcm.write((v >> 8) & 255); }
            if (!render) continue;
            r.day = day; r.hour = hour; r.sidereal = dc.siderealDeg;
            BufferedImage img = r.render(cam, t, dt);
            BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double fd = dr.fade();
            if (fd > 0) { g.setColor(new Color(0, 0, 0, (int) (255 * fd))); g.fillRect(0, 0, W, H); }
            Object[] cap = dr.caption();
            if (cap != null) {
                // Tafeln wie im Fenster, auf eine Bildbreite von 1280 bezogen
                double k = W / 1280.0;
                g.scale(k, k);
                Captions.draw(g, cap, 1280, (int) Math.round(H / k), 48);
            }
            g.dispose();
            ImageIO.write(out, "png", new File(dir, String.format("bild_%05d.png", frame)));
            frame++;
            if (frame % fps == 0) {
                double el = (System.nanoTime() - t0) / 1e9;
                System.out.printf(Locale.GERMANY, "  %5.1f s von %.1f · %d Bilder · %.2f s je Bild%n", t, to, frame, el / frame);
            }
            if (!on) break;
        }
        writeWav(new File(dir, "ton.wav"), pcm.toByteArray(), from);
        System.out.println("Fertig: " + frame + " Bilder und ton.wav in " + dir.getAbsolutePath());
        System.out.println("Film: ffmpeg -framerate " + fps + " -i \"" + new File(dir, "bild_%05d.png").getPath() + "\" -i \""
                + new File(dir, "ton.wav").getPath() + "\" -c:v libx264 -pix_fmt yuv420p -crf 18 -c:a aac -shortest drehbuch.mp4");
    }

    /** 16 Bit Stereo, ab Sekunde from (der Ton davor gehört zum übersprungenen Teil). */
    static void writeWav(File f, byte[] pcm, double from) throws java.io.IOException {
        int skip = (int) Math.round(from * GeyserSound.RATE) * 4;
        skip = Math.max(0, Math.min(pcm.length, skip));
        int n = pcm.length - skip;
        try (java.io.DataOutputStream o = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(f)))) {
            o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + n)); o.writeBytes("WAVEfmt ");
            o.writeInt(Integer.reverseBytes(16)); o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 2));
            o.writeInt(Integer.reverseBytes(GeyserSound.RATE)); o.writeInt(Integer.reverseBytes(GeyserSound.RATE * 4));
            o.writeShort(Short.reverseBytes((short) 4)); o.writeShort(Short.reverseBytes((short) 16));
            o.writeBytes("data"); o.writeInt(Integer.reverseBytes(n));
            o.write(pcm, skip, n);
        }
    }
}
