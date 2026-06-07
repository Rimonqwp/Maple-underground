package net.swofty.dungeons;

import lombok.Getter;

@Getter
public enum DungeonRoomType {
    ENTRANCE(),
    BASE(),
    FAIRY(true, false),
    PUZZLE(true),
    BLOOD(true),
    TRAP(true),
    MINI_BOSS(true),
    EXIT(),
    ;

    private boolean requiresData = false;
    private boolean automaticallyPlace = true;

    DungeonRoomType() {}

    DungeonRoomType(boolean requiresData) {
        this.requiresData = requiresData;
    }

    DungeonRoomType(boolean requiresData, boolean automaticallyPlace) {
        this.requiresData = requiresData;
        this.automaticallyPlace = automaticallyPlace;
    }

    public boolean isRequiresData() {
        return requiresData;
    }

    public boolean isAutomaticallyPlace() {
        return automaticallyPlace;
    }
}
