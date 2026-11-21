package maple.underground_collect;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

final class CollectionMenuHolder implements InventoryHolder {

    private final UUID owner;
    private final CollectionMenuType type;
    private final String scope;
    private final int page;
    private final int itemIndex;
    private final int tier;
    private final int recipeIndex;
    private final List<String> recipeStack;
    private Inventory inventory;

    CollectionMenuHolder(@NotNull UUID owner, @NotNull CollectionMenuType type, @NotNull String scope) {
        this(owner, type, scope, 0, 0, 1, 0, List.of());
    }

    CollectionMenuHolder(@NotNull UUID owner, @NotNull CollectionMenuType type, @NotNull String scope, int page) {
        this(owner, type, scope, page, 0, 1, 0, List.of());
    }

    CollectionMenuHolder(@NotNull UUID owner,
                         @NotNull CollectionMenuType type,
                         @NotNull String scope,
                         int itemIndex,
                         int tier,
                         int recipeIndex,
                         @NotNull List<String> recipeStack) {
        this(owner, type, scope, 0, itemIndex, tier, recipeIndex, recipeStack);
    }

    private CollectionMenuHolder(@NotNull UUID owner,
                                 @NotNull CollectionMenuType type,
                                 @NotNull String scope,
                                 int page,
                                 int itemIndex,
                                 int tier,
                                 int recipeIndex,
                                 @NotNull List<String> recipeStack) {
        this.owner = owner;
        this.type = type;
        this.scope = scope;
        this.page = Math.max(0, page);
        this.itemIndex = Math.max(0, itemIndex);
        this.tier = Math.max(1, tier);
        this.recipeIndex = Math.max(0, recipeIndex);
        this.recipeStack = List.copyOf(recipeStack);
    }

    @NotNull UUID owner() {
        return owner;
    }

    @NotNull CollectionMenuType type() {
        return type;
    }

    @NotNull String scope() {
        return scope;
    }

    int page() {
        return page;
    }

    int itemIndex() {
        return itemIndex;
    }

    int tier() {
        return tier;
    }

    int recipeIndex() {
        return recipeIndex;
    }

    @NotNull List<String> recipeStack() {
        return recipeStack;
    }

    void setInventory(@NotNull Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("inventory not set");
        }
        return inventory;
    }
}
