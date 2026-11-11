package io.github.OOP_project.model;

/**
 * 아이템 종류. 이름은 스테이지 파일과 세이브 파일에 그대로 적힌다.
 * 열쇠(KEY)도 아이템이라 인벤토리에 개수로 들어가며, 이전 스테이지에서 얻은 열쇠는 계속 갖고 있다.
 * 사용 효과 use(Player)는 Player 클래스를 만들 때 함께 추가한다. (HEALTH_POTION은 체력 회복, KEY는 사용 불가)
 */
public enum ItemType {
    HEALTH_POTION,
    KEY
}
