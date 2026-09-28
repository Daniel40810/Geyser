package com.dan.geyser.world;

import com.dan.geyser.core.Camera;
import com.dan.geyser.core.Terrain;
import com.dan.geyser.core.Thermal;
import com.dan.geyser.effects.GeyserSound;

/**
 * Pegel des Klangs aus dem Ort der Kamera: Säulen, Dampfphase, Vorspiel, Tremor, kochende Quellen,
 * Fluss und Wind, jeweils mit Richtung. Für das Fenster und für den Film ohne Fenster.
 */
public final class SoundScape {
    private SoundScape() { }

    public static void levels(GeyserSound sound, Geysers gs, Terrain terrain, Camera cam, double gClock, double wind) {
        double rx = cam.rx, rz = cam.rz;
        float roar = 0, rpan = 0, hiss = 0, splash = 0;
        double best = 0;
        for (GeyserModel g : gs.list) {
            double dx = g.x - cam.ex, dy = g.y - cam.ey, dz = g.z - cam.ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double att = 1 / (1 + Math.pow(d / 70, 2));
            double pan = (dx * rx + dz * rz) / Math.max(1, Math.hypot(dx, dz));
            if (g.phase == GeyserModel.Phase.ERUPTION) {
                double v = Math.min(1, g.height(gClock) / Math.max(1, g.hMax) + 0.25) * att;
                roar += v;
                if (v > best) { best = v; rpan = (float) pan; }
            } else if (g.phase == GeyserModel.Phase.STEAM) {
                hiss += (float) (g.steamShare() * att);
                if (att > best) { best = att * 0.5; rpan = (float) pan; }
            } else if (g.phase == GeyserModel.Phase.PREPLAY) {
                splash += (float) (att * (g.surgeDrop() > 0 ? 1 : 0.3));
            }
        }
        // Tremor: in der Nähe spürbar, hier hörbar gemacht
        float trem = 0, tpan = 0;
        for (GeyserModel g : gs.list) {
            double dx = g.x - cam.ex, dy = g.y - cam.ey, dz = g.z - cam.ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float v = (float) (g.tremor() * g.tubeDepth / 22 / (1 + Math.pow(d / 45, 2)));
            if (v > trem) { trem = v; tpan = (float) ((dx * rx + dz * rz) / Math.max(1, Math.hypot(dx, dz))); }
        }
        sound.tremor = Math.min(1, trem); sound.tremorPan = tpan;
        float boil = 0, bpan = 0;
        double bd = 1e9;
        for (Thermal.Spring s : terrain.thermal.springs) {
            if (s.t0 < 85) continue;
            double d = Math.max(0, Math.hypot(s.x - cam.ex, s.z - cam.ez) - Math.max(s.ax, s.az));
            if (d < bd) { bd = d; bpan = (float) (((s.x - cam.ex) * rx + (s.z - cam.ez) * rz) / Math.max(1, Math.hypot(s.x - cam.ex, s.z - cam.ez))); }
        }
        double alt = Math.max(0, cam.ey - terrain.sample(cam.ex, cam.ez));
        boil = (float) (1 / (1 + Math.pow((bd + alt) / 18, 2)));
        float rd = terrain.riverDist((float) cam.ex, (float) cam.ez);
        sound.roar = Math.min(1.2f, roar); sound.roarPan = rpan; sound.hiss = Math.min(1, hiss); sound.splash = Math.min(1, splash);
        sound.boil = boil; sound.boilPan = bpan;
        sound.river = (float) (0.5 / (1 + Math.pow((Math.max(0, rd) + alt) / 35, 2)));
        sound.wind = (float) (wind * (0.25 + 0.75 * Math.min(1, alt / 60)));
    }
}
