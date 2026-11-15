package game.ui.screens;

import game.common.ScreenType;
import game.service.UserService;
import game.ui.Screen;
import game.ui.ScreenManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
//import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;

public class LoadScreen implements Screen {

    private static final int LIST_WIDTH = 700;
    private static final int MAX_LIST_HEIGHT = 460;
    private static final int SLOT_HEIGHT = 82;
    private static final int SLOT_GAP = 14;
    private static final int LIST_PADDING = 16;

    // 임시값, 파일에서 읽어올 것
    // private static final int PLACEHOLDER_SAVE_COUNT = 99;
    // private static final String PLACEHOLDER_MAP_NAME = "인천시 던전구 마왕로";
    private static final List<String> PLACEHOLDER_SAVE_FILES = List.of(
            "인천시 던전구 마왕로",
            "2천시",
            "3",
            "4",
            "5");

    private final JPanel panel;
    private final ScreenManager screenManager;
    private final UserService userService;

    private final JPanel centerWrapper;
    private final JPanel slotListPanel;
    private final JScrollPane scrollPane;

    private final List<JButton> slotButtons = new ArrayList<>();
    private final List<String> currentSaveNames = new ArrayList<>();
    private int selectedSlot = -1;

    public LoadScreen(ScreenManager screenManager, UserService userService) {
        this.screenManager = screenManager;
        this.userService = userService;
        this.panel = new JPanel(new BorderLayout(0, 25));
        this.centerWrapper = new JPanel(new GridBagLayout());
        this.slotListPanel = new JPanel();
        this.scrollPane = new JScrollPane(slotListPanel);
        initUI();
    }

    private void initUI() {
        panel.setOpaque(true);
        panel.setBackground(Color.BLACK);
        panel.setBorder(new EmptyBorder(40, 50, 40, 50));

        // 제목
        JLabel titleLabel = new JLabel("불러오기");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 40));
        panel.add(titleLabel, BorderLayout.NORTH);

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

        // 중앙 레퍼
        centerWrapper.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        centerWrapper.add(scrollPane, gbc);

        panel.add(centerWrapper, BorderLayout.CENTER);

        updateSaveList(PLACEHOLDER_SAVE_FILES);

        // 선택버튼 패널
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        bottomPanel.setOpaque(false);

        JButton backBtn = createActionButton("뒤로가기");
        JButton loadBtn = createActionButton("불러오기");

        backBtn.addActionListener(e -> screenManager.show(ScreenType.START));
        // loadBtn.addActionListener(e -> handleLoadPlaceholder());

        bottomPanel.add(backBtn);
        bottomPanel.add(loadBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);
    }

    // 값 받아서 갯수만큼 세이브 목록 설정
    public void updateSaveList(List<String> saveFileNames) {
        selectedSlot = -1;
        slotButtons.clear();
        slotListPanel.removeAll();
        currentSaveNames.clear();

        if (saveFileNames != null) {
            currentSaveNames.addAll(saveFileNames);
        }

        int count = currentSaveNames.size();

        if (count == 0) {
            JLabel emptyLabel = new JLabel("세이브 없음");
            emptyLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            slotListPanel.add(emptyLabel);
        } else {
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    slotListPanel.add(Box.createRigidArea(new Dimension(0, SLOT_GAP)));
                }
                final int slotIndex = i;
                String fileName = currentSaveNames.get(i);
                JButton slotBtn = createSlotButton(
                        (i + 1) + "번: " + fileName,
                        "어쩌구저쩌구");
                slotBtn.addActionListener(e -> selectSlot(slotIndex));
                slotButtons.add(slotBtn);
                slotListPanel.add(slotBtn);
            }
        }

        adjustListDimensions(count);
        slotListPanel.revalidate();
        slotListPanel.repaint();
        centerWrapper.revalidate();
        centerWrapper.repaint();
    }

    // 세이브갯수에 맞게 높이조정
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

    // 현제 선택한 세이브 반환
    public int getSelectedSlotIndex() {
        return selectedSlot;
    }

    // 세이브 버튼 생성
    private JButton createSlotButton(String saveNameString, String saveDetailString) {
        String html = String.format(
                "<html><div style='padding: 2px;'>"
                        + "<span style='font-size: 16px; font-weight: bold;'>%s</span><br>"
                        + "<span style='font-size: 12px;'>%s</span>"
                        + "</div></html>",
                saveNameString, saveDetailString);

        JButton btn = new JButton(html);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 15));
        btn.setFocusPainted(false);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setPreferredSize(new Dimension(LIST_WIDTH - 50, SLOT_HEIGHT));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, SLOT_HEIGHT));
        updateSlotSelectionState(btn, false);
        return btn;
    }

    // 세이브 선택
    private void selectSlot(int index) {
        selectedSlot = index;
        for (int i = 0; i < slotButtons.size(); i++) {
            updateSlotSelectionState(slotButtons.get(i), i == selectedSlot);
        }
    }

    // 세이브 선택시 인덴트 넣어 피드백
    private void updateSlotSelectionState(JButton btn, boolean selected) {
        if (selected) {
            btn.setBorder(BorderFactory.createLoweredBevelBorder());
        } else {
            btn.setBorder(UIManager.getBorder("Button.border"));
        }
    }

    // 선택버튼 생성
    private JButton createActionButton(String text) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(210, 50));
        btn.setFont(new Font("SansSerif", Font.BOLD, 16));
        btn.setFocusPainted(false);
        return btn;
    }

    @Override
    public JPanel getPanel() {
        return panel;
    }

    // 진입시 불러오기화면 초기화
    @Override
    public void onEnter() {
        selectedSlot = -1;
        for (JButton btn : slotButtons) {
            updateSlotSelectionState(btn, false);
        }
        scrollPane.getVerticalScrollBar().setValue(0);
    }

    @Override
    public void onExit() {
    }
}