package maple.underground_collect;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

final class CollectionMenuHolder implements InventoryHolder {

    private final UUID owner;
    private final CollectionMenuType type;
    private final String scope;
    private Inventory inventory;

    CollectionMenuHolder(@NotNull UUID owner, @NotNull CollectionMenuType type, @NotNull String scope) {
        this.owner = owner;
        this.type = type;
        this.scope = scope;
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
