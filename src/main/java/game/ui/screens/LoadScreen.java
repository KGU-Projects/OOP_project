package game.ui.screens;

import game.common.ScreenType;
import game.model.SaveData;
import game.model.User;
import game.service.GameService;
import game.service.UserService;
import game.ui.BaseScreen;
import game.ui.ScreenManager;
import game.ui.components.GameButton;
import game.ui.components.SaveButton;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class LoadScreen extends BaseScreen {

    private static final int LIST_WIDTH = 700;
    private static final int MAX_LIST_HEIGHT = 460;
    private static final int SLOT_HEIGHT = 82;
    private static final int SLOT_GAP = 14;
    private static final int LIST_PADDING = 16;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserService userService;
    private final GameService gameService;

    private final JPanel centerWrapper;
    private final JPanel slotListPanel;
    private final JScrollPane scrollPane;
    private final JLabel statusLabel = new JLabel(" ");
    private final GameButton loadActionButton;

    private SaveButton selectedButton;
    private int selectedSlot = -1;
    private boolean selectedSlotHasData = false;
    private ScreenType returnScreen = ScreenType.START;

    public LoadScreen(ScreenManager screenManager, UserService userService) {
        this(screenManager, userService, null);
    }

    public LoadScreen(ScreenManager screenManager, UserService userService, GameService gameService) {
        super(screenManager, new BorderLayout(0, 25));
        this.userService = userService;
        this.gameService = gameService;
        this.centerWrapper = new JPanel(new GridBagLayout());
        this.slotListPanel = new JPanel();
        this.scrollPane = new JScrollPane(slotListPanel);
        this.loadActionButton = new GameButton("불러오기", this::handleLoadSelected);
        initUI();
    }

    public void setReturnScreen(ScreenType returnScreen) {
        this.returnScreen = (returnScreen != null) ? returnScreen : ScreenType.START;
    }

    private void initUI() {
        // 제목
        panel.add(createTitleLabel("불러오기"), BorderLayout.NORTH);

        // 세이브 레이아웃
        slotListPanel.setLayout(new BoxLayout(slotListPanel, BoxLayout.Y_AXIS));
        slotListPanel.setOpaque(false);
        slotListPanel.setBorder(new EmptyBorder(LIST_PADDING, LIST_PADDING, LIST_PADDING, LIST_PADDING));

        // 스크롤바
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // 중앙 래퍼
        centerWrapper.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        centerWrapper.add(scrollPane, gbc);

        panel.add(centerWrapper, BorderLayout.CENTER);

        // 하단 상태 문구 + 선택 버튼 패널
        JPanel southContainer = new JPanel(new BorderLayout());
        southContainer.setOpaque(false);

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        statusLabel.setForeground(new Color(220, 180, 180));
        southContainer.add(statusLabel, BorderLayout.WEST);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(new GameButton("뒤로가기", this::handleBack));
        loadActionButton.setEnabled(false);
        bottomPanel.add(loadActionButton);

        southContainer.add(bottomPanel, BorderLayout.EAST);
        panel.add(southContainer, BorderLayout.SOUTH);
        panel.setBorder(new EmptyBorder(40, 50, 40, 50));

        reloadSlotsFromService();
    }

    public void reloadSlotsFromService() {
        if (gameService == null) {
            updateSaveSlots(List.of());
            return;
        }
        User user = gameService.getCurrentUser();
        updateSaveSlots(gameService.getAllSaveSlots(user.getNickname()));
    }

    public void updateSaveSlots(List<Optional<SaveData>> slots) {
        selectSlot(-1, false, null);
        slotListPanel.removeAll();

        int count = (slots == null) ? 0 : slots.size();

        if (count == 0) {
            JLabel emptyLabel = new JLabel("세이브 슬롯이 없습니다");
            emptyLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
            emptyLabel.setForeground(Color.WHITE);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            slotListPanel.add(emptyLabel);
        } else {
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    slotListPanel.add(Box.createRigidArea(new Dimension(0, SLOT_GAP)));
                }
                final int slotNumber = i + 1;
                Optional<SaveData> dataOpt = slots.get(i);

                String title;
                String detail;
                boolean hasData = dataOpt.isPresent();

                if (hasData) {
                    SaveData data = dataOpt.get();
                    title = "STAGE " + data.getNextStageNo();
                    detail = String.format("HP %d  |  SCORE %d  |  저장: %s",
                            data.getHp(),
                            data.getScore(),
                            data.getSavedAt().format(DATE_FMT));
                } else {
                    title = "빈 슬롯";
                    detail = "저장된 데이터가 없습니다";
                }

                SaveButton slotBtn = new SaveButton(
                        slotNumber,
                        title,
                        detail,
                        LIST_WIDTH - 50,
                        SLOT_HEIGHT);
                slotBtn.addActionListener(e -> selectSlot(slotNumber, hasData, slotBtn));
                slotListPanel.add(slotBtn);
            }
        }

        adjustListDimensions(count);
        slotListPanel.revalidate();
        slotListPanel.repaint();
        centerWrapper.revalidate();
        centerWrapper.repaint();
    }

    private void adjustListDimensions(int count) {
        int contentHeight;
        if (count == 0) {
            contentHeight = 100;
        } else {
            contentHeight = (LIST_PADDING * 2)
                    + (count * SLOT_HEIGHT)
                    + ((count - 1) * SLOT_GAP)
                    + 6;
        }

        int targetHeight = Math.min(MAX_LIST_HEIGHT, contentHeight);
        scrollPane.setPreferredSize(new Dimension(LIST_WIDTH, targetHeight));
        scrollPane.setMinimumSize(new Dimension(LIST_WIDTH, targetHeight));
        scrollPane.setMaximumSize(new Dimension(LIST_WIDTH, MAX_LIST_HEIGHT));
    }

    private void selectSlot(int slotNumber, boolean hasData, SaveButton button) {
        if (selectedButton != null) {
            selectedButton.setSelected(false);
        }
        selectedButton = button;
        selectedSlot = slotNumber;
        selectedSlotHasData = hasData;
        if (selectedButton != null) {
            selectedButton.setSelected(true);
        }
        loadActionButton.setEnabled(selectedSlot >= 1 && selectedSlotHasData);
        statusLabel.setText(" ");
    }

    private void handleLoadSelected() {
        if (gameService == null || selectedSlot < 1 || !selectedSlotHasData) {
            statusLabel.setText("불러올 세이브 슬롯을 선택해주세요.");
            return;
        }
        try {
            User user = gameService.getCurrentUser();
            gameService.continueGame(user, selectedSlot);
            returnScreen = ScreenType.START;
            screenManager.show(ScreenType.GAME);
        } catch (RuntimeException ex) {
            statusLabel.setText("불러오기 실패: " + ex.getMessage());
        }
    }

    private void handleBack() {
        ScreenType target = returnScreen;
        returnScreen = ScreenType.START;
        screenManager.show(target);
    }

    public String getSelectedSaveName() {
        return (selectedButton != null) ? selectedButton.getSaveName() : null;
    }

    @Override
    public void onEnter() {
        statusLabel.setText(" ");
        reloadSlotsFromService();
        scrollPane.getVerticalScrollBar().setValue(0);
    }
}