package game.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates original, lightweight WAV placeholder music/effects on first run. */
public final class AudioAssetGenerator {
    private static final float SAMPLE_RATE = 22050f;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private static final Path DEFAULT_DIRECTORY = Path.of(System.getProperty("user.home"), ".code-escape", "audio");
    private static volatile boolean generated;

    private AudioAssetGenerator() { }

    public static Path audioDirectory() { return DEFAULT_DIRECTORY; }

    /** Creates missing assets only, preserving any WAV files supplied by the team. */
    public static synchronized void ensureAssets() throws IOException {
        if (generated && Files.isDirectory(DEFAULT_DIRECTORY)) return;
        Files.createDirectories(DEFAULT_DIRECTORY);
        writeIfMissing("bgm-start.wav", new double[]{523.25, 659.25, 783.99, 659.25, 587.33, 698.46, 880.00, 698.46}, 0.48, 0.16);
        writeIfMissing("bgm-game.wav", new double[]{220.00, 261.63, 329.63, 261.63, 196.00, 246.94, 293.66, 246.94}, 0.55, 0.13);
        writeIfMissing("bgm-quiz.wav", new double[]{293.66, 349.23, 440.00, 392.00, 329.63, 392.00, 493.88, 440.00}, 0.40, 0.15);
        writeIfMissing("sfx-button.wav", new double[]{660, 880}, 0.07, 0.12);
        writeIfMissing("sfx-correct.wav", new double[]{523, 659, 784}, 0.11, 0.18);
        writeIfMissing("sfx-wrong.wav", new double[]{280, 220}, 0.14, 0.18);
        writeIfMissing("sfx-attack.wav", new double[]{260, 440}, 0.10, 0.22);
        writeIfMissing("sfx-hit.wav", new double[]{180, 125}, 0.13, 0.20);
        writeIfMissing("sfx-victory.wav", new double[]{523, 659, 784, 1047}, 0.18, 0.20);
        writeIfMissing("sfx-defeat.wav", new double[]{392, 330, 261, 196}, 0.18, 0.20);
        writeIfMissing("sfx-chest.wav", new double[]{440, 660, 990}, 0.09, 0.16);
        generated = true;
    }

    /** Optional CLI: java game.audio.AudioAssetGenerator [output-directory]. */
    public static void main(String[] args) throws IOException {
        Path output = args.length == 0 ? DEFAULT_DIRECTORY : Path.of(args[0]);
        Files.createDirectories(output);
        generateAll(output);
        System.out.println("Generated WAV audio assets in " + output.toAbsolutePath());
    }

    private static void generateAll(Path output) throws IOException {
        write(output.resolve("bgm-start.wav"), new double[]{523.25, 659.25, 783.99, 659.25, 587.33, 698.46, 880, 698.46}, .48, .16);
        write(output.resolve("bgm-game.wav"), new double[]{220, 261.63, 329.63, 261.63, 196, 246.94, 293.66, 246.94}, .55, .13);
        write(output.resolve("bgm-quiz.wav"), new double[]{293.66, 349.23, 440, 392, 329.63, 392, 493.88, 440}, .40, .15);
        write(output.resolve("sfx-button.wav"), new double[]{660, 880}, .07, .12);
        write(output.resolve("sfx-correct.wav"), new double[]{523, 659, 784}, .11, .18);
        write(output.resolve("sfx-wrong.wav"), new double[]{280, 220}, .14, .18);
        write(output.resolve("sfx-attack.wav"), new double[]{260, 440}, .10, .22);
        write(output.resolve("sfx-hit.wav"), new double[]{180, 125}, .13, .20);
        write(output.resolve("sfx-victory.wav"), new double[]{523, 659, 784, 1047}, .18, .20);
        write(output.resolve("sfx-defeat.wav"), new double[]{392, 330, 261, 196}, .18, .20);
        write(output.resolve("sfx-chest.wav"), new double[]{440, 660, 990}, .09, .16);
    }

    private static void writeIfMissing(String filename, double[] notes, double noteSeconds, double amplitude) throws IOException {
        Path path = DEFAULT_DIRECTORY.resolve(filename);
        if (!Files.exists(path)) write(path, notes, noteSeconds, amplitude);
    }

    private static void write(Path path, double[] notes, double noteSeconds, double amplitude) throws IOException {
        int samplesPerNote = Math.max(1, (int) (SAMPLE_RATE * noteSeconds));
        byte[] pcm = new byte[samplesPerNote * notes.length * 2];
        int offset = 0;
        for (double frequency : notes) {
            for (int i = 0; i < samplesPerNote; i++) {
                double t = i / SAMPLE_RATE;
                double edge = Math.min(1.0, Math.min(i / (SAMPLE_RATE * .015), (samplesPerNote - i) / (SAMPLE_RATE * .035)));
                double sample = Math.sin(2 * Math.PI * frequency * t) * amplitude * Math.max(0, edge);
                short value = (short) (sample * Short.MAX_VALUE);
                pcm[offset++] = (byte) (value & 0xff);
                pcm[offset++] = (byte) ((value >>> 8) & 0xff);
            }
        }
        try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT, pcm.length / 2L)) {
            AudioSystem.write(audio, javax.sound.sampled.AudioFileFormat.Type.WAVE, path.toFile());
        }
    }
}
