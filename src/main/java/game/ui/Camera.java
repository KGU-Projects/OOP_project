package game.ui;

import game.model.Player;
import game.model.Stage;

/**
 * 고정 타일 뷰포트(기본 40 x 20) 기준으로 월드 좌표를 화면 픽셀 좌표로 변환하고 스크롤을 관리한다.
 */
public class Camera {

    private final int viewportCols;
    private final int viewportRows;

    private double scrollX;
    private double scrollY;

    private int tileSize = 32;
    private int viewportPixelW;
    private int viewportPixelH;
    private int originX;
    private int originY;

    public Camera(int viewportCols, int viewportRows) {
        this.viewportCols = viewportCols;
        this.viewportRows = viewportRows;
    }

    /** 즉시 플레이어 위치로 카메라를 맞춘다. */
    public void snapTo(Player player, Stage stage) {
        updateScroll(player, stage, 1.0);
    }

    /** 플레이어를 부드럽게 추적한다. */
    public void follow(Player player, Stage stage) {
        updateScroll(player, stage, 0.2);
    }

    private void updateScroll(Player player, Stage stage, double smoothing) {
        double targetX = player.getCenterX() - viewportCols / 2.0;
        double targetY = player.getCenterY() - viewportRows / 2.0;

        double clampedX = clampScroll(targetX, stage.getWidth(), viewportCols);
        double clampedY = clampScroll(targetY, stage.getHeight(), viewportRows);

        scrollX += (clampedX - scrollX) * smoothing;
        scrollY += (clampedY - scrollY) * smoothing;
    }

    private static double clampScroll(double target, int stageTiles, int viewTiles) {
        if (stageTiles <= viewTiles) {
            return (stageTiles - viewTiles) / 2.0;
        }
        return Math.max(0.0, Math.min(stageTiles - viewTiles, target));
    }

    /** 현재 패널 크기에 맞춰 40 x 20 뷰포트의 타일 크기와 중앙 오프셋을 갱신한다. */
    public void updateViewportMetrics(int panelWidth, int panelHeight) {
        this.tileSize = Math.max(1, Math.min(panelWidth / viewportCols, panelHeight / viewportRows));
        this.viewportPixelW = viewportCols * tileSize;
        this.viewportPixelH = viewportRows * tileSize;
        this.originX = (panelWidth - viewportPixelW) / 2;
        this.originY = (panelHeight - viewportPixelH) / 2;
    }

    public int worldToScreenX(double worldTileX) {
        return originX + (int) Math.round((worldTileX - scrollX) * tileSize);
    }

    public int worldToScreenY(double worldTileY) {
        return originY + (int) Math.round((worldTileY - scrollY) * tileSize);
    }

    public int toPixels(double tiles) {
        return (int) Math.round(tiles * tileSize);
    }

    public int getFirstVisibleCol() {
        return Math.max(0, (int) Math.floor(scrollX));
    }

    public int getLastVisibleCol(int stageWidth) {
        return Math.min(stageWidth - 1, (int) Math.ceil(scrollX + viewportCols));
    }

    public int getFirstVisibleRow() {
        return Math.max(0, (int) Math.floor(scrollY));
    }

    public int getLastVisibleRow(int stageHeight) {
        return Math.min(stageHeight - 1, (int) Math.ceil(scrollY + viewportRows));
    }

    public int getTileSize() {
        return tileSize;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getViewportPixelW() {
        return viewportPixelW;
    }

    public int getViewportPixelH() {
        return viewportPixelH;
    }
}