package game.ui.components;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

//세이브
public class SaveButton extends JButton {

    private final String saveName;

    public SaveButton(int slotNumber, String saveName, String detail, int width, int height) {
        this.saveName = saveName;

        setText(formatLabel(slotNumber + "번: " + saveName, detail));
        setHorizontalAlignment(SwingConstants.LEFT);
        setFont(new Font("SansSerif", Font.PLAIN, 15));
        setFocusPainted(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setPreferredSize(new Dimension(width, height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        setSelected(false);
    }

    private String formatLabel(String title, String subtitle) {
        return String.format(
                "<html><div style='padding: 2px;'>"
                        + "<span style='font-size: 16px; font-weight: bold;'>%s</span><br>"
                        + "<span style='font-size: 12px;'>%s</span>"
                        + "</div></html>",
                title, subtitle);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (selected) {
            setBorder(BorderFactory.createLoweredBevelBorder());
        } else {
            setBorder(UIManager.getBorder("Button.border"));
        }
    }

    public String getSaveName() {
        return saveName;
    }
}