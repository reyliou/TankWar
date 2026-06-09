package com.tankgame.client;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class SoundManager {
    private final Map<String, AudioClip> sfx = new HashMap<>();
    private MediaPlayer bgmPlayer;
    private String currentBgmPath;

    public SoundManager() {
        loadSfx("shoot", "assets/sounds/sfx/tank_fire.MP3");
        // Fallback or future assets could be added here
    }

    private void loadSfx(String name, String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                sfx.put(name, new AudioClip(file.toURI().toString()));
            }
        } catch (Exception e) {
            System.err.println("Failed to load SFX: " + path + " - " + e.getMessage());
        }
    }

    public void shoot() {
        playSfx("shoot", 560, 55, 0.22);
    }

    public void hit() {
        playSfx("hit", 180, 80, 0.28);
    }

    public void pickup() {
        if (sfx.containsKey("pickup")) {
            sfx.get("pickup").play();
        } else {
            new Thread(() -> {
                tone(660, 55, 0.22);
                tone(880, 65, 0.20);
            }, "sound-pickup").start();
        }
    }

    public void boom() {
        playSfx("boom", 95, 140, 0.35);
    }

    public void roundResult(boolean won) {
        stopBgm();
        playBgm("assets/sounds/bgm/endgame.mp3", false);
        
        if (!won) {
            new Thread(() -> {
                tone(300, 110, 0.20);
                tone(220, 150, 0.22);
            }, "sound-result").start();
        }
    }

    public void updateBgm(String phase) {
        switch (phase) {
            case "MENU":
                playBgm("assets/sounds/bgm/menu.mp3", true);
                break;
            case "ROOM_WAIT":
                playBgm("assets/sounds/bgm/menu.mp3", true);
                break;
            case "COMBAT":
                playBgm("assets/sounds/bgm/fight.mp3", true);
                break;
            case "ROUND_OVER":
                // Handled in roundResult
                break;
        }
    }

    private void playBgm(String path, boolean loop) {
        if (currentBgmPath != null && currentBgmPath.equals(path)) {
            return;
        }
        stopBgm();
        try {
            File file = new File(path);
            if (!file.exists()) return;
            
            Media media = new Media(file.toURI().toString());
            bgmPlayer = new MediaPlayer(media);
            bgmPlayer.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
            bgmPlayer.setVolume(0.4);
            bgmPlayer.play();
            currentBgmPath = path;
        } catch (Exception e) {
            System.err.println("Failed to play BGM: " + path + " - " + e.getMessage());
        }
    }

    public void stopBgm() {
        if (bgmPlayer != null) {
            bgmPlayer.stop();
            bgmPlayer = null;
            currentBgmPath = null;
        }
    }

    private void playSfx(String name, double fallbackHz, int fallbackMillis, double fallbackVol) {
        AudioClip clip = sfx.get(name);
        if (clip != null) {
            clip.play();
        } else {
            playTone(fallbackHz, fallbackMillis, fallbackVol);
        }
    }

    private void playTone(double hz, int millis, double volume) {
        new Thread(() -> tone(hz, millis, volume), "sound-tone").start();
    }

    private static final float SAMPLE_RATE = 22050f;
    private void tone(double hz, int millis, double volume) {
        try {
            javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            try (javax.sound.sampled.SourceDataLine line = javax.sound.sampled.AudioSystem.getSourceDataLine(format)) {
                line.open(format);
                line.start();
                int samples = (int) (millis * SAMPLE_RATE / 1000.0);
                byte[] buffer = new byte[samples];
                for (int i = 0; i < samples; i++) {
                    double fade = 1.0 - (i / (double) samples);
                    double wave = Math.sin(2.0 * Math.PI * i * hz / SAMPLE_RATE);
                    buffer[i] = (byte) (wave * 127 * volume * fade);
                }
                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        } catch (Exception ignored) {}
    }
}
