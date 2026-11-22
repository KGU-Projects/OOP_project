package game.model.object;

import java.util.Set;

public enum ObjectType {

    BUTTON('B'),
    DOOR('D'),
    KEY('K'),
    ITEM('I'),
    BOSS('M'),

    MONSTER('M'),
    CHEST('S'),
    MIMIC('S'),
    TRAP('.'),
    EXIT('D'),
    MOVE_PLATFORM('A');

    private static final Set<Character> MAP_SYMBOLS = Set.of('B', 'D', 'K', 'I', 'M', 'S', 'A');

    private final char symbol;

    ObjectType(char symbol) {
        this.symbol = symbol;
    }

    public char getSymbol() {
        return symbol;
    }

    public boolean hasDedicatedMapSymbol() {
        return this != TRAP;
    }

    public static boolean isMapObjectSymbol(char c) {
        return MAP_SYMBOLS.contains(c);
    }
}