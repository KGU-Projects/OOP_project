package io.github.OOP_project.ui;

import java.util.Map;

public class ScreenManager {

    CardLayout layout;
    Map<ScreenType, Screen> screens;

    public void register(ScreenType type, Screen screen) {

        if (type == null || screen == null) {
            throw new IllegalArgumentException("ERROR: ScreenType or Screen returned null");
        }

        screens.put(type, screen);
        container.add(screen.getPanel(), type.name());
    }

    public void show(ScreenType type) {

    }

}
