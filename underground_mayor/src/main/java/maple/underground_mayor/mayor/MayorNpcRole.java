package maple.underground_mayor.mayor;

import maple.underground_mayor.compat.CitizensCompat;

import java.util.Locale;

public enum MayorNpcRole {
    COLLECTOR(
            "莉迪",
            true,
            "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzVmN2MxNmExNTBmNjIzYjlkMmE3OTFiOTg4ZWEwMDAxNmUyOGI3Zjk2ZDczNjhhYzNjMWIxZDA0Mjk5ODQ1MCJ9fX0=",
            "lidi", "collector", "1"
    ),
    DECORATION_ONE(
            "巴克",
            false,
            "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjE5ZDA3YTNkYzA2ZTg2MmEzZmFhZTNhYTkzMzRmZDYyMmJiMzhlZjJjZDlhMjYwOWEzNWUyMWYwZmI1Y2NmNiJ9fX0=",
            "bake", "buck", "barke", "2"
    ),
    DECORATION_TWO(
            "艾里斯",
            false,
            "e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDNkMmEwMGJlMjcxZmE0OWFlODgxY2UxYmE0NGU1N2ZjMTYxNzRhODkyYzI1ZWY2YTk2MmU5OGYwNmYyMWY5MiJ9fX0=",
            "ailisi", "iris", "3"
    );

    private final String displayName;
    private final boolean collector;
    private final String skinTexture;
    private final String[] aliases;

    MayorNpcRole(String displayName, boolean collector, String skinTexture, String... aliases) {
        this.displayName = displayName;
        this.collector = collector;
        this.skinTexture = skinTexture;
        this.aliases = aliases;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isCollector() {
        return collector;
    }

    public CitizensCompat.SkinData skinData() {
        return new CitizensCompat.SkinData(skinTexture, "");
    }

    public boolean matches(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (displayName.equalsIgnoreCase(input) || name().equalsIgnoreCase(normalized)) {
            return true;
        }

        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static MayorNpcRole fromName(String name) {
        for (MayorNpcRole role : values()) {
            if (role.name().equalsIgnoreCase(name)) {
                return role;
            }
        }
        return null;
    }

    public static MayorNpcRole fromInput(String input) {
        for (MayorNpcRole role : values()) {
            if (role.matches(input)) {
                return role;
            }
        }
        return null;
    }
}
