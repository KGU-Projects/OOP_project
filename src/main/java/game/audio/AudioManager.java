package game.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/** Centralized WAV playback for background music and one-shot effects. */
public final class AudioManager {
    public enum Track {
        START("bgm-start.wav"), GAME("bgm-game.wav"), QUIZ("bgm-quiz.wav");
        private final String file;
        Track(String file) { this.file = file; }
    }

    public enum Sound {
        BUTTON("sfx-button.wav"), CORRECT("sfx-correct.wav"), WRONG("sfx-wrong.wav"),
        ATTACK("sfx-attack.wav"), HIT("sfx-hit.wav"), VICTORY("sfx-victory.wav"),
        DEFEAT("sfx-defeat.wav"), CHEST("sfx-chest.wav");
        private final String file;
        Sound(String file) { this.file = file; }
    }

    private static final AudioManager INSTANCE = new AudioManager();
    private final Map<Track, Clip> tracks = new EnumMap<>(Track.class);
    private Clip currentBgm;
    private Track currentTrack;
    private int pausedFrame;
    private boolean muted;
    private float bgmVolume = 0.6f;
    private float sfxVolume = 0.8f;

    private AudioManager() { }

    public static AudioManager getInstance() { return INSTANCE; }

    /** Called by ScreenManager so transitions never stack two BGM clips. */
    public synchronized void onScreenChanged(game.common.ScreenType screen) {
        if (screen == null) return;
        switch (screen) {
            case START -> playBgm(Track.START);
            case GAME, CUTSCENE -> playBgm(Track.GAME);
            case QUIZ -> playBgm(Track.QUIZ);
            // LOAD and utility screens keep the music for the screen they came from.
            default -> { }
        }
    }

    public synchronized void playBgm(Track track) {
        if (track == null || (currentTrack == track && currentBgm != null && currentBgm.isRunning())) return;
        stopBgm();
        Clip clip = load(track.file);
        if (clip == null) return;
        tracks.put(track, clip);
        currentTrack = track;
        currentBgm = clip;
        setVolume(clip, bgmVolume);
        if (!muted) clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public synchronized void stopBgm() {
        if (currentBgm != null) {
            currentBgm.stop();
            currentBgm.close();
        }
        currentBgm = null;
        currentTrack = null;
        pausedFrame = 0;
        tracks.clear();
    }

    public synchronized void pauseBgm() {
        if (currentBgm != null && currentBgm.isRunning()) {
            pausedFrame = currentBgm.getFramePosition();
            currentBgm.stop();
        }
    }

    public synchronized void resumeBgm() {
        if (currentBgm != null && !muted && !currentBgm.isRunning()) {
            currentBgm.setFramePosition(Math.min(pausedFrame, Math.max(0, currentBgm.getFrameLength() - 1)));
            currentBgm.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    public void play(Sound sound) {
        if (sound == null || muted) return;
        Clip clip = load(sound.file);
        if (clip == null) return;
        setVolume(clip, sfxVolume);
        clip.addLineListener(event -> {
            if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) clip.close();
        });
        clip.start();
    }

    public synchronized void setMuted(boolean value) {
        muted = value;
        if (muted) pauseBgm();
        else resumeBgm();
    }

    public boolean isMuted() { return muted; }
    public synchronized void setBgmVolume(float value) {
        bgmVolume = clamp(value);
        setVolume(currentBgm, bgmVolume);
    }
    public void setSfxVolume(float value) { sfxVolume = clamp(value); }
    public float getBgmVolume() { return bgmVolume; }
    public float getSfxVolume() { return sfxVolume; }

    public synchronized void dispose() { stopBgm(); }

    private static Clip load(String filename) {
        try {
            AudioAssetGenerator.ensureAssets();
            File file = AudioAssetGenerator.audioDirectory().resolve(filename).toFile();
            try (AudioInputStream input = AudioSystem.getAudioInputStream(file)) {
                Clip clip = AudioSystem.getClip();
                clip.open(input);
                return clip;
            }
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException ignored) {
            // Audio is optional; missing files or unsupported audio devices must not stop gameplay.
            return null;
        }
    }

    private static void setVolume(Clip clip, float volume) {
        if (clip == null) return;
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float db = (float) (20.0 * Math.log10(Math.max(0.0001f, volume)));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
        } catch (IllegalArgumentException ignored) { }
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
