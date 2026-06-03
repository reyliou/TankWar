package com.tankgame.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioSystem;

public class SoundManager {
    private static final float SAMPLE_RATE = 22050f;

    public void shoot() {
        playTone(560, 55, 0.22);
    }

    public void hit() {
        playTone(180, 80, 0.28);
    }

    public void pickup() {
        new Thread(() -> {
            tone(660, 55, 0.22);
            tone(880, 65, 0.20);
        }, "sound-pickup").start();
    }

    public void boom() {
        playTone(95, 140, 0.35);
    }

    public void roundResult(boolean won) {
        new Thread(() -> {
            if (won) {
                tone(520, 90, 0.22);
                tone(700, 90, 0.22);
                tone(920, 130, 0.24);
            } else {
                tone(300, 110, 0.20);
                tone(220, 150, 0.22);
            }
        }, "sound-result").start();
    }

    private void playTone(double hz, int millis, double volume) {
        new Thread(() -> tone(hz, millis, volume), "sound-tone").start();
    }

    private void tone(double hz, int millis, double volume) {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
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
        } catch (Exception ignored) {
            // Audio is optional for classroom demos; missing devices should not stop the game.
        }
    }
}
