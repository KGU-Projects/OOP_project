package game.ui;

import game.common.ScreenType;

import java.awt.CardLayout;
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

    // 화면등록
    public void register(ScreenType type, Screen screen) {

        if (type == null || screen == null) {
            throw new IllegalArgumentException("ERROR: ScreenType or Screen returned null");
        }

        screens.put(type, screen);
        container.add(screen.getPanel(), type.name());
    }

    // 화면등장
    public void show(ScreenType type) {
        if (type == null || !screens.containsKey(type)) {
            throw new IllegalArgumentException("ScreenType not registered: " + type);
        }

        if (currentType != null) {
            Screen currentScreen = screens.get(currentType);
            if (currentScreen != null) {
                try {
                    currentScreen.onExit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        currentType = type;
        layout.show(container, type.name());

        Screen nextScreen = screens.get(type);
        if (nextScreen != null) {
            try {
                nextScreen.onEnter();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public ScreenType getCurrentType() {
        return currentType;
    }

    public Screen getScreen(ScreenType type) {
        return screens.get(type);
    }

}
