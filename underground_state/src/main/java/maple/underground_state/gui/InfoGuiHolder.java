package maple.underground_state.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class InfoGuiHolder implements InventoryHolder {

    private final UUID owner;
    private final String pageId;
    private final int page;
    private Inventory inventory;

    public InfoGuiHolder(@NotNull UUID owner, @NotNull String pageId) {
        this(owner, pageId, 0);
    }

    public InfoGuiHolder(@NotNull UUID owner, @NotNull String pageId, int page) {
        this.owner = owner;
        this.pageId = pageId;
        this.page = Math.max(0, page);
    }

    public @NotNull UUID owner() {
        return owner;
    }

    public @NotNull String pageId() {
        return pageId;
    }

    public int page() {
        return page;
    }

    public void setInventory(@NotNull Inventory inventory) {
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
