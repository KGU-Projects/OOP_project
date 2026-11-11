package main.java.game.ui;

import javax.swing.JPanel;

public interface Screen {

    // getPanel 시 JPanel 반환
    JPanel getPanel();

    void onEnter();

    void onExit();
}