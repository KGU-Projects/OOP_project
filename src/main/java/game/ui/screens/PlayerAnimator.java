package game.ui.screens;

import game.model.GameState;
import game.model.Player;
import game.ui.ImageLoader;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 플레이어의 대기(1), 달리기(2), 점프(1) 애니메이션 상태를 관리한다.
 */
final class PlayerAnimator {

    enum Frame {
        IDLE("player-idle.png", "IDLE"),
        RUN_1("player-run-1.png", "RUN 1"),
        RUN_2("player-run-2.png", "RUN 2"),
        JUMP("player-jump.png", "JUMP");

        final String fileName;
        final String placeholderTag;

        Frame(String fileName, String placeholderTag) {
            this.fileName = fileName;
            this.placeholderTag = placeholderTag;
        }
    }

    private static final double RUN_FRAME_INTERVAL = 0.12;
    private static final double MOVE_EPSILON = 1e-4;
    private static final int GROUND_CONFIRM_TICKS = 2;

    private final Map<Frame, BufferedImage> textures = new EnumMap<>(Frame.class);
    private final Map<Frame, Boolean> fallbackFlags = new EnumMap<>(Frame.class);

    private GameState trackedState;
    private boolean initialized = false;
    private double lastX = 0.0;
    private double lastY = 0.0;

    private boolean airborne = false;
    private int groundedTicks = 0;
    private double runTimer = 0.0;
    private boolean runToggle = false;
    private Frame currentFrame = Frame.IDLE;

    PlayerAnimator(ImageLoader loader, BufferedImage fallbackTexture) {
        for (Frame frame : Frame.values()) {
            Optional<BufferedImage> loaded = (loader != null) ? loader.get(frame.fileName) : Optional.empty();
            if (loaded.isPresent()) {
                textures.put(frame, loaded.get());
                fallbackFlags.put(frame, false);
            } else {
                textures.put(frame, fallbackTexture);
                fallbackFlags.put(frame, true);
            }
        }
    }

    void reset() {
        initialized = false;
        airborne = false;
        groundedTicks = 0;
        runTimer = 0.0;
        runToggle = false;
        currentFrame = Frame.IDLE;
    }

    void update(GameState state, double dt) {
        if (state == null) {
            return;
        }
        if (state != trackedState) {
            trackedState = state;
            reset();
        }

        Player player = state.getPlayer();
        double x = player.getX();
        double y = player.getY();

        if (!initialized) {
            lastX = x;
            lastY = y;
            initialized = true;
            currentFrame = Frame.IDLE;
            return;
        }

        double dx = x - lastX;
        double dy = y - lastY;
        lastX = x;
        lastY = y;

        if (Math.abs(dy) > MOVE_EPSILON) {
            airborne = true;
            groundedTicks = 0;
        } else if (airborne) {
            groundedTicks++;
            if (groundedTicks >= GROUND_CONFIRM_TICKS) {
                airborne = false;
            }
        }

        if (airborne) {
            runTimer = 0.0;
            runToggle = false;
            currentFrame = Frame.JUMP;
            return;
        }

        if (Math.abs(dx) > MOVE_EPSILON) {
            runTimer += dt;
            if (runTimer >= RUN_FRAME_INTERVAL) {
                runTimer -= RUN_FRAME_INTERVAL;
                runToggle = !runToggle;
            }
            currentFrame = runToggle ? Frame.RUN_2 : Frame.RUN_1;
        } else {
            runTimer = 0.0;
            runToggle = false;
            currentFrame = Frame.IDLE;
        }
    }

    Frame getCurrentFrame() {
        return currentFrame;
    }

    BufferedImage getTexture(Frame frame) {
        return textures.get(frame);
    }

    boolean isFallback(Frame frame) {
        return Boolean.TRUE.equals(fallbackFlags.get(frame));
    }
}