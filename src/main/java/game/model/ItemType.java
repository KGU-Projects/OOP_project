package game.model;

/**
 * 아이템 종류. 이름은 스테이지 파일과 세이브 파일에 그대로 적힌다.
 * 열쇠(KEY)도 아이템이라 인벤토리에 개수로 들어가며, 이전 스테이지에서 얻은 열쇠는 계속 갖고 있다.
 */
public enum ItemType {
    HEALTH_POTION {
        @Override
        public boolean use(GameState state) {
            if (state.getHp() >= state.getMaxHp()) {
                return false;
            }
            if (!state.consumeItem(this)) {
                return false;
            }
            return state.heal(1);
        }
    },
    KEY {
        @Override
        public boolean use(GameState state) {
            return false; // 열쇠는 직접 소모 불가
        }
    };

    /** 아이템 사용 효과를 적용한다. 사용할 수 없으면 false. */
    public abstract boolean use(GameState state);
}