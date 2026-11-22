package game.model;

import game.common.GameConstants;
import game.model.object.GameObject;

import java.util.Optional;

/**
 * 스테이지 위를 이동하는 플레이어 엔티티.
 * 타일 단위 좌표(0.8 x 1.6)와 물리·충돌 판정을 담당한다.
 */
public class Player {

    private static final double MOVE_SPEED = 7.0;
    private static final double GRAVITY = 32.0;
    private static final double JUMP_VELOCITY = -13.5;
    private static final double MAX_FALL_SPEED = 18.0;
    private static final double EPS = 1e-4;

    private double x;
    private double y;
    private double velX;
    private double velY;
    private boolean onGround;
    private boolean facingRight = true;

    private boolean leftPressed;
    private boolean rightPressed;
    private boolean jumpPressed;

    public Player(double startX, double startY) {
        spawnAt(startX, startY);
    }

    public final void spawnAt(double startX, double startY) {
        this.x = startX;
        this.y = startY;
        this.velX = 0.0;
        this.velY = 0.0;
        this.onGround = false;
        this.facingRight = true;
        clearInputs();
    }

    public void clearInputs() {
        leftPressed = false;
        rightPressed = false;
        jumpPressed = false;
    }

    public void setLeftPressed(boolean pressed) {
        this.leftPressed = pressed;
    }

    public void setRightPressed(boolean pressed) {
        this.rightPressed = pressed;
    }

    public void setJumpPressed(boolean pressed) {
        this.jumpPressed = pressed;
    }

    /** 한 프레임(dt초) 동안 이동·충돌·오브젝트 접촉을 처리하고 안내 문구가 있으면 반환한다. */
    public Optional<String> update(double dt, GameState state) {
        velX = 0.0;
        if (leftPressed && !rightPressed) {
            velX = -MOVE_SPEED;
            facingRight = false;
        } else if (rightPressed && !leftPressed) {
            velX = MOVE_SPEED;
            facingRight = true;
        }

        if (jumpPressed && onGround) {
            velY = JUMP_VELOCITY;
            onGround = false;
        }
        velY = Math.min(MAX_FALL_SPEED, velY + GRAVITY * dt);

        moveHorizontal(velX * dt, state);
        moveVertical(velY * dt, state);
        return checkTouchObjects(state);
    }

    private void moveHorizontal(double dx, GameState state) {
        if (dx == 0.0) {
            return;
        }
        double nextX = x + dx;
        double w = GameConstants.PLAYER_WIDTH;
        double h = GameConstants.PLAYER_HEIGHT;

        int topTile = (int) Math.floor(y + EPS);
        int bottomTile = (int) Math.floor(y + h - EPS);

        if (dx > 0.0) {
            int rightTile = (int) Math.floor(nextX + w - EPS);
            for (int ty = topTile; ty <= bottomTile; ty++) {
                if (isBlocked(state, rightTile, ty)) {
                    nextX = rightTile - w;
                    break;
                }
            }
        } else {
            int leftTile = (int) Math.floor(nextX + EPS);
            for (int ty = topTile; ty <= bottomTile; ty++) {
                if (isBlocked(state, leftTile, ty)) {
                    nextX = leftTile + 1.0;
                    break;
                }
            }
        }
        x = nextX;
    }

    private void moveVertical(double dy, GameState state) {
        double nextY = y + dy;
        double w = GameConstants.PLAYER_WIDTH;
        double h = GameConstants.PLAYER_HEIGHT;
        onGround = false;

        int leftTile = (int) Math.floor(x + EPS);
        int rightTile = (int) Math.floor(x + w - EPS);

        if (dy > 0.0) {
            int bottomTile = (int) Math.floor(nextY + h - EPS);
            for (int tx = leftTile; tx <= rightTile; tx++) {
                if (isBlocked(state, tx, bottomTile)) {
                    nextY = bottomTile - h;
                    velY = 0.0;
                    onGround = true;
                    break;
                }
            }
        } else if (dy < 0.0) {
            int topTile = (int) Math.floor(nextY + EPS);
            for (int tx = leftTile; tx <= rightTile; tx++) {
                if (isBlocked(state, tx, topTile)) {
                    nextY = topTile + 1.0;
                    velY = 0.0;
                    break;
                }
            }
        }
        y = nextY;
    }

    private boolean isBlocked(GameState state, int tx, int ty) {
        Stage stage = state.getStage();
        if (stage.isSolidTile(tx, ty)) {
            return true;
        }
        for (GameObject obj : stage.getObjects()) {
            if (obj.isSolid(state) && obj.occupiesTile(tx, ty)) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> checkTouchObjects(GameState state) {
        for (GameObject obj : state.getStage().getObjects()) {
            if (state.isObjectCollected(obj.getId()) || !overlaps(obj, 0.0)) {
                continue;
            }
            Optional<String> message = obj.onTouch(state);
            if (message.isPresent()) {
                return message;
            }
        }
        return Optional.empty();
    }

    public boolean overlaps(GameObject obj, double margin) {
        double px1 = x - margin;
        double py1 = y - margin;
        double px2 = x + GameConstants.PLAYER_WIDTH + margin;
        double py2 = y + GameConstants.PLAYER_HEIGHT + margin;

        double ox1 = obj.getX();
        double oy1 = obj.getY();
        double ox2 = obj.getX() + obj.getWidth();
        double oy2 = obj.getY() + obj.getHeight();

        return px1 < ox2 && px2 > ox1 && py1 < oy2 && py2 > oy1;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getCenterX() {
        return x + GameConstants.PLAYER_WIDTH / 2.0;
    }

    public double getCenterY() {
        return y + GameConstants.PLAYER_HEIGHT / 2.0;
    }

    public boolean isFacingRight() {
        return facingRight;
    }
}