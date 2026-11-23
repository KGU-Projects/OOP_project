package game.ui.screens;

import game.common.ScreenType;
import game.model.Cutscene;
import game.model.CutsceneFrame;
import game.repository.CutsceneRepository;
import game.ui.BaseScreen;
import game.ui.ImageLoader;
import game.ui.ScreenManager;
import game.ui.components.GameButton;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Optional;

/** 컷신 재생 화면 (순수 흑백 플레이스홀더 스타일). */
public class CutsceneScreen extends BaseScreen {

    private static final int TYPE_DELAY_MS = 28;
    private static final Color BG = Color.BLACK;
    private static final Color FG = Color.WHITE;

    private final CutsceneRepository cutsceneRepository;
    private final ImageLoader imageLoader;

    private final BackdropPanel backdropPanel;
    private final PortraitPanel portraitPanel;
    private final JPanel dialogueBox;
    private final JLabel speakerLabel = new JLabel(" ");
    private final JLabel progressLabel = new JLabel(" ", SwingConstants.LEFT);
    private final JLabel hintLabel = new JLabel(" ", SwingConstants.RIGHT);
    private final JTextArea dialogueArea = new JTextArea();
    private final Timer typeTimer;

    private Cutscene currentCutscene;
    private int frameIndex;
    private String fullDialogue = "";
    private int charIndex;
    private Runnable onComplete;
    private boolean finished;

    public CutsceneScreen(ScreenManager screenManager, CutsceneRepository cutsceneRepository) {
        this(screenManager, cutsceneRepository, new ImageLoader());
    }

    public CutsceneScreen(ScreenManager screenManager, CutsceneRepository cutsceneRepository, ImageLoader imageLoader) {
        super(screenManager, new BorderLayout());
        this.cutsceneRepository = cutsceneRepository;
        this.imageLoader = (imageLoader != null) ? imageLoader : new ImageLoader();
        this.backdropPanel = new BackdropPanel();
        this.portraitPanel = new PortraitPanel();
        this.dialogueBox = new JPanel(new BorderLayout(20, 0));
        this.typeTimer = new Timer(TYPE_DELAY_MS, e -> onTypeTick());

        initUI();
        setupInputs();
    }

    /**
     * 컷신 ID로 컷신을 조회해 재생한다.
     * 해당 컷신 파일이 없거나 비어 있으면 화면 전환 없이 즉시 onComplete를 실행한다.
     */
    public void play(String cutsceneId, Runnable onComplete) {
        Optional<Cutscene> found = (cutsceneRepository != null)
                ? cutsceneRepository.findById(cutsceneId)
                : Optional.empty();
        if (found.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        play(found.get(), onComplete);
    }

    /** 컷신 객체를 직접 받아 CUTSCENE 화면으로 전환하고 재생을 시작한다. */
    public void play(Cutscene cutscene, Runnable onComplete) {
        if (cutscene == null || cutscene.size() == 0) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        this.currentCutscene = cutscene;
        this.frameIndex = 0;
        this.onComplete = onComplete;
        this.finished = false;

        screenManager.show(ScreenType.CUTSCENE);
        showFrame(0);
    }

    private void initUI() {
        backdropPanel.setLayout(new BorderLayout());
        backdropPanel.setBorder(new EmptyBorder(24, 36, 28, 36));

        // 상단 우측 건너뛰기 버튼
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topBar.setOpaque(false);
        GameButton skipButton = new GameButton("SKIP [ESC]", this::finish);
        skipButton.setPreferredSize(new Dimension(150, 40));
        skipButton.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        skipButton.setBackground(BG);
        skipButton.setForeground(FG);
        skipButton.setBorder(new LineBorder(FG, 2));
        topBar.add(skipButton);
        backdropPanel.add(topBar, BorderLayout.NORTH);

        // 하단 대화창 영역
        JPanel bottomWrapper = new JPanel(new BorderLayout(0, 6));
        bottomWrapper.setOpaque(false);

        JPanel speakerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        speakerRow.setOpaque(false);
        speakerLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 17));
        speakerLabel.setForeground(FG);
        speakerLabel.setOpaque(true);
        speakerLabel.setBackground(BG);
        speakerLabel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(FG, 2),
                new EmptyBorder(6, 16, 6, 16)));
        speakerRow.add(speakerLabel);
        bottomWrapper.add(speakerRow, BorderLayout.NORTH);

        dialogueBox.setOpaque(true);
        dialogueBox.setBackground(BG);
        dialogueBox.setPreferredSize(new Dimension(0, 200));
        dialogueBox.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(FG, 2),
                new EmptyBorder(16, 20, 14, 24)));

        // 좌측 화자 초상화 플레이스홀더 프레임
        portraitPanel.setPreferredSize(new Dimension(164, 164));
        portraitPanel.setVisible(false);
        dialogueBox.add(portraitPanel, BorderLayout.WEST);

        // 우측 대사 + 하단 안내 문구
        JPanel textContent = new JPanel(new BorderLayout(0, 10));
        textContent.setOpaque(false);

        dialogueArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 19));
        dialogueArea.setForeground(FG);
        dialogueArea.setOpaque(false);
        dialogueArea.setEditable(false);
        dialogueArea.setFocusable(false);
        dialogueArea.setLineWrap(true);
        dialogueArea.setWrapStyleWord(false);
        textContent.add(dialogueArea, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        progressLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        progressLabel.setForeground(FG);
        hintLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        hintLabel.setForeground(FG);
        footer.add(progressLabel, BorderLayout.WEST);
        footer.add(hintLabel, BorderLayout.EAST);
        textContent.add(footer, BorderLayout.SOUTH);

        dialogueBox.add(textContent, BorderLayout.CENTER);
        bottomWrapper.add(dialogueBox, BorderLayout.CENTER);
        backdropPanel.add(bottomWrapper, BorderLayout.SOUTH);

        panel.add(backdropPanel, BorderLayout.CENTER);
    }

    private void setupInputs() {
        backdropPanel.setFocusable(true);
        backdropPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                advance();
            }
        });

        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "cutscene_next_space", this::advance);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "cutscene_next_enter", this::advance);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cutscene_skip", this::finish);
    }

    private void bindKey(KeyStroke stroke, String name, Runnable action) {
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        panel.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void showFrame(int index) {
        CutsceneFrame frame = currentCutscene.getFrames().get(index);

        BufferedImage bg = frame.hasBackgroundImage()
                ? imageLoader.get(frame.backgroundImage()).orElse(null)
                : null;
        backdropPanel.setContent(bg, frame.backgroundImage());

        BufferedImage portrait = frame.hasSpeakerImage()
                ? imageLoader.get(frame.speakerImage()).orElse(null)
                : null;
        boolean showPortraitBox = frame.hasSpeakerImage() || frame.hasSpeaker();
        portraitPanel.setContent(portrait, frame.hasSpeakerImage() ? frame.speakerImage() : frame.speaker());
        portraitPanel.setVisible(showPortraitBox);

        if (frame.hasSpeaker()) {
            speakerLabel.setText(frame.speaker());
            speakerLabel.setVisible(true);
        } else {
            speakerLabel.setVisible(false);
        }

        progressLabel.setText("[" + (index + 1) + "/" + currentCutscene.size() + "]");
        fullDialogue = frame.dialogue();
        charIndex = 0;
        dialogueArea.setText("");
        hintLabel.setText("[CLICK / SPACE / ENTER]");

        dialogueBox.revalidate();
        dialogueBox.repaint();
        typeTimer.restart();
        backdropPanel.repaint();
    }

    private void onTypeTick() {
        if (charIndex < fullDialogue.length()) {
            charIndex++;
            dialogueArea.setText(fullDialogue.substring(0, charIndex));
        } else {
            typeTimer.stop();
            hintLabel.setText(frameIndex + 1 < currentCutscene.size() ? "NEXT >>" : "START >>");
        }
    }

    private void advance() {
        if (finished || currentCutscene == null) {
            return;
        }
        if (charIndex < fullDialogue.length()) {
            typeTimer.stop();
            charIndex = fullDialogue.length();
            dialogueArea.setText(fullDialogue);
            hintLabel.setText(frameIndex + 1 < currentCutscene.size() ? "NEXT >>" : "START >>");
            return;
        }

        if (frameIndex + 1 < currentCutscene.size()) {
            frameIndex++;
            showFrame(frameIndex);
        } else {
            finish();
        }
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        typeTimer.stop();
        Runnable callback = onComplete;
        onComplete = null;
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public void onEnter() {
        SwingUtilities.invokeLater(backdropPanel::requestFocusInWindow);
    }

    @Override
    public void onExit() {
        typeTimer.stop();
    }

    /** 배경 이미지가 없으면 순수 흑백 와이어프레임 플레이스홀더를 그린다. */
    private static final class BackdropPanel extends JPanel {

        private static final long serialVersionUID = 1L;
        private BufferedImage image;
        private String label;

        private BackdropPanel() {
            setOpaque(true);
            setBackground(BG);
        }

        private void setContent(BufferedImage image, String label) {
            this.image = image;
            this.label = label;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();

            if (image != null) {
                g2.drawImage(image, 0, 0, w, h, this);
            } else {
                // 흑백 와이어프레임 배경 플레이스홀더
                g2.setColor(BG);
                g2.fillRect(0, 0, w, h);

                g2.setColor(FG);
                int pad = 16;
                g2.drawRect(pad, pad, w - pad * 2 - 1, h - pad * 2 - 1);

                String tag = (label != null && !label.isBlank())
                        ? "[BG: " + label + "]"
                        : "[CUTSCENE]";
                g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                int boxW = fm.stringWidth(tag) + 24;
                int boxH = 34;
                int bx = (w - boxW) / 2;
                int by = (h - 220) / 2 - boxH / 2;

                g2.setColor(BG);
                g2.fillRect(bx, by, boxW, boxH);
                g2.setColor(FG);
                g2.drawRect(bx, by, boxW, boxH);
                g2.drawString(tag, bx + 12, by + 22);
            }
            g2.dispose();
        }
    }

    /** 화자 초상화 이미지가 없으면 흑백 실루엣 플레이스홀더를 그린다. */
    private static final class PortraitPanel extends JPanel {

        private static final long serialVersionUID = 1L;
        private BufferedImage image;
        private String label;

        private PortraitPanel() {
            setOpaque(true);
            setBackground(BG);
            setBorder(new LineBorder(FG, 2));
        }

        private void setContent(BufferedImage image, String label) {
            this.image = image;
            this.label = label;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();

            if (image != null) {
                int pad = 6;
                int availW = Math.max(1, w - pad * 2);
                int availH = Math.max(1, h - pad * 2);
                double scale = Math.min((double) availW / image.getWidth(), (double) availH / image.getHeight());
                int drawW = (int) Math.round(image.getWidth() * scale);
                int drawH = (int) Math.round(image.getHeight() * scale);
                g2.drawImage(image, (w - drawW) / 2, (h - drawH) / 2, drawW, drawH, this);
            } else {
                // 순수 흑백 초상화 실루엣 플레이스홀더
                g2.setColor(FG);
                int headSize = 42;
                g2.drawOval((w - headSize) / 2, 24, headSize, headSize);
                g2.drawRect((w - 76) / 2, 74, 76, 48);

                String tag = (label != null && !label.isBlank()) ? label : "PORTRAIT";
                g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                if (fm.stringWidth(tag) > w - 12) {
                    tag = "PORTRAIT";
                }
                g2.drawString(tag, (w - fm.stringWidth(tag)) / 2, h - 14);
            }
            g2.dispose();
        }
    }
}