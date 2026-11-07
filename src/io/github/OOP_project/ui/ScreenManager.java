package io.github.OOP_project.ui;

import java.util.EnumMap;
import java.util.Map;

import javax.swing.JPanel;

public class ScreenManager {

    private final JPanel container;
    private final CardLayout layout;
    private final Map<ScreenType, Screen> screens = new EnumMap<>(ScreenType.class);

    private ScreenType currentType;

    // 컨스트럭터
    public ScreenManager(JPanel container, CardLayout layout) {
        if (container == null || layout == null) {
            throw new IllegalArgumentException("ERROR: Container or CardLayout returned null");
        }
        this.container = container;
        this.layout = layout;
    }

    public void register(ScreenType type, Screen screen) {

        if (type == null || screen == null) {
            throw new IllegalArgumentException("ERROR: ScreenType or Screen returned null");
        }

        screens.put(type, screen);
        container.add(screen.getPanel(), type.name());
    }

    public void show(ScreenType type) {

        if (type == null) {
            throw new IllegalArgumentException("ERROR: illegal ScreenType" + type);
        }

        if (currentType != null) {
            Screen currentScreen = screens.get(currentType);
            if (currentScreen != null) {
                currentScreen.onExit();
            }
        }

        currentType = type;
        layout.show(container, type.name());

        Screen nextScreen = screens.get(type);
        if (nextScreen != null) {
            nextScreen.onEnter();
        }
    }

}
