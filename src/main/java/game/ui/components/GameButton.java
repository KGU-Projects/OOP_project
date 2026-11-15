package game.ui.components;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

public class GameButton extends JButton {

    // 텍스트랑 이벤트 받아와 버튼 생성, 추후 이미지로 대체할 것
    public GameButton(String text, Runnable onClick) {
        super(text);
        setPreferredSize(new Dimension(240, 50));
        setFont(new Font("SansSerif", Font.BOLD, 16));
        setFocusPainted(false);
        setOpaque(true);
        setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(100, 100, 120), 1, true),
                new EmptyBorder(5, 15, 5, 15)));
        if (onClick != null) {
            addActionListener(e -> onClick.run());
        }
    }
}