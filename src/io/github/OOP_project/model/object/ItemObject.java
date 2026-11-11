package io.github.OOP_project.model.object;

import io.github.OOP_project.model.ItemType;

/** 주우면 인벤토리에 들어가는 아이템. */
public class ItemObject extends GameObject {

    private final ItemType itemType;
    private final int count;

    public ItemObject(String id, int x, int y, ItemType itemType, int count) {
        super(id, x, y);
        this.itemType = itemType;
        this.count = count;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public int getCount() {
        return count;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.ITEM;
    }

    @Override
    public String describe() {
        return "아이템  " + itemType.name() + " x" + count;
    }
}
