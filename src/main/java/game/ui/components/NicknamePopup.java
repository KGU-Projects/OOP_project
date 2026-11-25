package game.ui.components;

import game.model.User;
import game.service.UserService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.util.function.Consumer;

/**
 * 게임을 켰을 때 게임 창 안에 뜨는 닉네임 입력 팝업. 별도 창이 아니라 게임 창의 glass pane에 올려서,
 * 뒤 화면을 어둡게 덮고 올바른 닉네임을 입력하기 전에는 뒤 화면을 누를 수 없게 한다.
 * 처음 보는 닉네임은 확인을 누르는 순간 UserService가 users.txt에 등록하고, 이미 있는 닉네임은 그 사용자로 이어서 한다.
 */
public class NicknamePopup extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Color DIM = new Color(0, 0, 0, 170);
    private static final Color BACKGROUND = new Color(24, 24, 32);
    private static final Color BORDER_COLOR = new Color(100, 100, 120);
    private static final Color ERROR_COLOR = new Color(255, 110, 110);
    private static final int BOX_WIDTH = 440;

    private final transient UserService userService;
    private final transient Consumer<User> onEntered;
    private final JTextField nicknameField = new JTextField(12);
    private final JLabel messageLabel = new JLabel(" ", SwingConstants.CENTER);

    private NicknamePopup(UserService userService, Consumer<User> onEntered) {
        super(new GridBagLayout()); // 자식이 하나면 한가운데에 놓인다
        this.userService = userService;
        this.onEntered = onEntered;
        setOpaque(false);
        // 뒤 화면으로 마우스가 새지 않게 막고, Tab으로도 팝업 밖으로 나가지 못하게 한다
        addMouseListener(new MouseAdapter() {
        });
        setFocusCycleRoot(true);
        add(createBox());
    }

    /**
     * 게임 창 위에 팝업을 띄운다. 닉네임이 확정되면 팝업을 닫고 onEntered에 입장한 사용자를 넘긴다.
     * 창이 뜨기를 기다리지 않고 바로 돌아오므로, 닉네임이 필요한 일은 onEntered 안에서 한다.
     */
    public static void show(JFrame frame, UserService userService, Consumer<User> onEntered) {
        NicknamePopup popup = new NicknamePopup(userService, onEntered);
        frame.setGlassPane(popup);
        popup.setVisible(true);
        popup.nicknameField.requestFocusInWindow();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(DIM);
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }

    private JPanel createBox() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(BACKGROUND);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 2, true),
                new EmptyBorder(28, 36, 24, 36)));

        JLabel titleLabel = new JLabel("닉네임을 입력하세요");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);

        JLabel ruleLabel = new JLabel("1~12자의 한글, 영문, 숫자");
        ruleLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        ruleLabel.setForeground(new Color(180, 180, 195));

        nicknameField.setFont(new Font("SansSerif", Font.PLAIN, 18));
        nicknameField.setHorizontalAlignment(JTextField.CENTER);
        nicknameField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
        nicknameField.setMaximumSize(new Dimension(320, 40));
        nicknameField.addActionListener(e -> submit()); // Enter 키

        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        messageLabel.setForeground(ERROR_COLOR);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttonPanel.setOpaque(false);
        GameButton exitButton = new GameButton("종료", () -> System.exit(0));
        GameButton confirmButton = new GameButton("확인", this::submit);
        exitButton.setPreferredSize(new Dimension(120, 40));
        confirmButton.setPreferredSize(new Dimension(120, 40));
        buttonPanel.add(exitButton);
        buttonPanel.add(confirmButton);

        for (JComponent c : new JComponent[] { titleLabel, ruleLabel, nicknameField, messageLabel, buttonPanel }) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        box.add(titleLabel);
        box.add(Box.createVerticalStrut(8));
        box.add(ruleLabel);
        box.add(Box.createVerticalStrut(18));
        box.add(nicknameField);
        box.add(Box.createVerticalStrut(8));
        box.add(messageLabel);
        box.add(Box.createVerticalStrut(14));
        box.add(buttonPanel);

        // 오류 문구 길이에 따라 상자가 늘었다 줄었다 하지 않게 너비를 고정한다
        box.setPreferredSize(new Dimension(BOX_WIDTH, box.getPreferredSize().height));
        return box;
    }

    /**
     * 이미 있는 닉네임이면 그 사용자를 돌려주고, 처음 보는 닉네임이면 바로 등록(users.txt에 저장)한 뒤 돌려준다.
     * 닉네임이 규칙에 맞지 않으면 IllegalArgumentException.
     */
    private User enter(String nickname) {
        for (User user : userService.findAll()) {
            if (user.getNickname().equals(nickname)) {
                return user;
            }
        }
        return userService.register(nickname); // 규칙 검사도 여기서 한다
    }

    private void submit() {
        String nickname = nicknameField.getText().trim();
        User user;
        try {
            user = enter(nickname);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        } catch (RuntimeException e) {
            // users.txt가 깨졌거나 쓸 수 없는 경우. 팝업은 그대로 두고 이유를 보여 준다.
            showError("사용자 정보를 저장하지 못했습니다: " + e.getMessage());
            return;
        }
        setVisible(false);
        onEntered.accept(user);
    }

    private void showError(String message) {
        messageLabel.setText(message);
        nicknameField.requestFocusInWindow();
        nicknameField.selectAll();
    }
}
