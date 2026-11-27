package game.audio;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;

/** UI for choosing background music and adjusting game audio levels. */
public final class AudioSettingsDialog {
    private AudioSettingsDialog() { }

    public static void show(Component parent) {
        AudioManager audio = AudioManager.getInstance();
        Window owner = parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "오디오 설정", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(10, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
        JPanel controls = new JPanel(new GridLayout(0, 1, 6, 8));

        JLabel selectedFile = new JLabel(audio.hasCustomBgm() ? "사용자 음악이 선택되어 있습니다." : "기본 게임 음악 사용 중");
        JButton chooseButton = new JButton("MP3 음악 선택...");
        JButton resetButton = new JButton("기본 음악으로 되돌리기");
        chooseButton.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("게임 음악 선택");
            chooser.setFileFilter(new FileNameExtensionFilter("MP3 오디오 (*.mp3)", "mp3"));
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                Path file = chooser.getSelectedFile().toPath();
                try {
                    audio.setCustomBgm(file);
                    selectedFile.setText("선택됨: " + file.getFileName());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(), "음악을 불러올 수 없습니다", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        resetButton.addActionListener(event -> {
            try {
                audio.clearCustomBgm();
                selectedFile.setText("기본 게임 음악 사용 중");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, "사용자 음악을 제거하지 못했습니다: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            }
        });

        JSlider bgmSlider = volumeSlider(audio.getBgmVolume(), value -> audio.setBgmVolume(value));
        JSlider sfxSlider = volumeSlider(audio.getSfxVolume(), value -> audio.setSfxVolume(value));
        JCheckBox muteBox = new JCheckBox("음소거", audio.isMuted());
        muteBox.addActionListener(event -> audio.setMuted(muteBox.isSelected()));

        controls.add(new JLabel("배경 음악 음량"));
        controls.add(bgmSlider);
        controls.add(new JLabel("효과음 음량"));
        controls.add(sfxSlider);
        controls.add(muteBox);
        controls.add(selectedFile);

        JPanel musicButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        musicButtons.add(chooseButton);
        musicButtons.add(resetButton);
        content.add(controls, BorderLayout.CENTER);
        content.add(musicButtons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static JSlider volumeSlider(float initial, java.util.function.DoubleConsumer onChange) {
        JSlider slider = new JSlider(0, 100, Math.round(initial * 100));
        slider.setMajorTickSpacing(25);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.addChangeListener(event -> onChange.accept(slider.getValue() / 100.0));
        return slider;
    }
}
