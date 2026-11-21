package maple.underground_state.commands;

import maple.underground_state.gui.BookshelfInfoGui;
import maple.underground_state.gui.AxeInfoGui;
import maple.underground_state.gui.AlchemyInfoGui;
import maple.underground_state.gui.BackpackInfoGui;
import maple.underground_state.gui.BankInfoGui;
import maple.underground_state.gui.CollectionInfoGui;
import maple.underground_state.gui.CopperSwordMenuGui;
import maple.underground_state.gui.CraftingInfoGui;
import maple.underground_state.gui.EnchantingInfoGui;
import maple.underground_state.gui.ExplorationInfoGui;
import maple.underground_state.gui.FishingRodInfoGui;
import maple.underground_state.gui.GuildInfoGui;
import maple.underground_state.gui.HoeInfoGui;
import maple.underground_state.gui.InfoGuiHolder;
import maple.underground_state.gui.MainInfoGui;
import maple.underground_state.gui.PickaxeInfoGui;
import maple.underground_state.gui.SocialInfoGui;
import maple.underground_state.gui.SwordInfoGui;
import maple.underground_state.gui.TamingInfoGui;
import maple.underground_state.gui.WardrobeInfoGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class InfoMenuCommand implements CommandExecutor, Listener {

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用这个指令。", NamedTextColor.RED));
            return true;
        }
        MainInfoGui.open(player);
        return true;
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player anvilPlayer && GuildInfoGui.isAnvilSession(anvilPlayer)) {
            GuildInfoGui.handleAnvilSessionClick(event, anvilPlayer);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InfoGuiHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (!holder.owner().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (WardrobeInfoGui.PAGE_ID.equals(holder.pageId())) {
            WardrobeInfoGui.handleInventoryClick(event);
            return;
        }
        if (BackpackInfoGui.STORAGE_PAGE_ID.equals(holder.pageId())) {
            BackpackInfoGui.handleStorageClick(event);
            return;
        }
        if (GuildInfoGui.CREATE_PAGE_ID.equals(holder.pageId())) {
            GuildInfoGui.handleCreateAnvilClick(event, player);
            return;
        }
        if (GuildInfoGui.ROLE_CREATE_PAGE_ID.equals(holder.pageId())) {
            GuildInfoGui.handleRoleCreateAnvilClick(event, player);
            return;
        }

         event.setCancelled(true);
         int rawSlot = event.getRawSlot();
         if (BackpackInfoGui.PAGE_ID.equals(holder.pageId())
                 && event.getClickedInventory() != null
                 && event.getClickedInventory().equals(player.getInventory())) {
             BackpackInfoGui.handleOverviewBottomClick(event);
             return;
         }
         if (rawSlot < 0 || rawSlot >= top.getSize()) {
             return;
         }

        switch (holder.pageId()) {
            case MainInfoGui.PAGE_ID -> MainInfoGui.handleClick(player, rawSlot);
            case BankInfoGui.PAGE_ID -> BankInfoGui.handleClick(player, rawSlot);
            case BankInfoGui.DEPOSIT_PAGE_ID -> BankInfoGui.handleDepositClick(player, rawSlot);
            case BankInfoGui.WITHDRAW_PAGE_ID -> BankInfoGui.handleWithdrawClick(player, rawSlot);
            case BankInfoGui.LOG_PAGE_ID -> BankInfoGui.handleLogClick(player, rawSlot);
            case GuildInfoGui.PAGE_ID -> GuildInfoGui.handleClick(player, rawSlot);
            case GuildInfoGui.SETTINGS_PAGE_ID -> GuildInfoGui.handleSettingsClick(player, rawSlot);
            case GuildInfoGui.TRANSFER_OWNER_PAGE_ID -> GuildInfoGui.handleTransferOwnerClick(player, rawSlot);
            case GuildInfoGui.ROLES_PAGE_ID -> GuildInfoGui.handleRolesClick(player, rawSlot);
            case GuildInfoGui.ROLE_EDIT_PAGE_ID -> GuildInfoGui.handleRoleEditClick(player, rawSlot);
            case GuildInfoGui.ROLE_PERMISSION_CATEGORY_PAGE_ID -> GuildInfoGui.handleRolePermissionCategoryClick(player, rawSlot);
            case GuildInfoGui.MEMBER_PROFILE_PAGE_ID -> GuildInfoGui.handleMemberProfileClick(player, rawSlot);
            case GuildInfoGui.MEMBER_ASSIGN_ROLE_PAGE_ID -> GuildInfoGui.handleMemberRoleAssignClick(player, rawSlot);
            case GuildInfoGui.INVITE_PAGE_ID -> GuildInfoGui.handleInviteClick(player, rawSlot);
            case GuildInfoGui.RECEIVED_INVITES_PAGE_ID -> GuildInfoGui.handleReceivedInvitesClick(player, rawSlot);
            case GuildInfoGui.BANK_PAGE_ID -> GuildInfoGui.handleGuildBankClick(player, rawSlot);
            case GuildInfoGui.BANK_DEPOSIT_PAGE_ID -> GuildInfoGui.handleGuildBankDepositClick(player, rawSlot);
            case GuildInfoGui.BANK_WITHDRAW_PAGE_ID -> GuildInfoGui.handleGuildBankWithdrawClick(player, rawSlot);
            case GuildInfoGui.BANK_LOG_PAGE_ID -> GuildInfoGui.handleGuildBankLogClick(player, rawSlot);
            case GuildInfoGui.CREATE_PAGE_ID -> GuildInfoGui.handleCreateAnvilClick(event, player);
            case GuildInfoGui.ROLE_CREATE_PAGE_ID -> GuildInfoGui.handleRoleCreateAnvilClick(event, player);
            case GuildInfoGui.MEMBER_DISCORD_EDIT_PAGE_ID -> GuildInfoGui.handleMemberDiscordAnvilClick(event, player);
            case BackpackInfoGui.PAGE_ID -> BackpackInfoGui.handleOverviewClick(event);
            case CopperSwordMenuGui.PAGE_ID -> CopperSwordMenuGui.handleClick(player, rawSlot);
            case BookshelfInfoGui.PAGE_ID -> BookshelfInfoGui.handleClick(player, rawSlot);
            case CollectionInfoGui.HOE_PAGE_ID -> CollectionInfoGui.handleClick(player, holder.pageId(), rawSlot, event.isRightClick());
            case CollectionInfoGui.PICKAXE_PAGE_ID -> CollectionInfoGui.handleClick(player, holder.pageId(), rawSlot, event.isRightClick());
            case CollectionInfoGui.SWORD_PAGE_ID -> CollectionInfoGui.handleClick(player, holder.pageId(), rawSlot, event.isRightClick());
            case CollectionInfoGui.AXE_PAGE_ID -> CollectionInfoGui.handleClick(player, holder.pageId(), rawSlot, event.isRightClick());
            case CollectionInfoGui.FISHING_ROD_PAGE_ID -> CollectionInfoGui.handleClick(player, holder.pageId(), rawSlot, event.isRightClick());
            case HoeInfoGui.PAGE_ID -> HoeInfoGui.handleClick(player, rawSlot);
            case PickaxeInfoGui.PAGE_ID -> PickaxeInfoGui.handleClick(player, rawSlot);
            case SwordInfoGui.PAGE_ID -> SwordInfoGui.handleClick(player, rawSlot);
            case SocialInfoGui.PAGE_ID -> SocialInfoGui.handleClick(player, rawSlot);
            case AxeInfoGui.PAGE_ID -> AxeInfoGui.handleClick(player, rawSlot);
            case FishingRodInfoGui.PAGE_ID -> FishingRodInfoGui.handleClick(player, rawSlot);
            case EnchantingInfoGui.PAGE_ID -> EnchantingInfoGui.handleClick(player, rawSlot);
            case AlchemyInfoGui.PAGE_ID -> AlchemyInfoGui.handleClick(player, rawSlot);
            case ExplorationInfoGui.PAGE_ID -> ExplorationInfoGui.handleClick(player, rawSlot);
            case CraftingInfoGui.PAGE_ID -> CraftingInfoGui.handleClick(player, rawSlot);
            case TamingInfoGui.PAGE_ID -> TamingInfoGui.handleClick(player, rawSlot);
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player anvilPlayer && GuildInfoGui.isAnvilSession(anvilPlayer)) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InfoGuiHolder holder)) {
            return;
        }
        if (GuildInfoGui.CREATE_PAGE_ID.equals(holder.pageId())
                || GuildInfoGui.ROLE_CREATE_PAGE_ID.equals(holder.pageId())) {
            event.setCancelled(true);
            return;
        }
        if (BackpackInfoGui.STORAGE_PAGE_ID.equals(holder.pageId())) {
            BackpackInfoGui.handleStorageDrag(event);
            return;
        }
        if (BackpackInfoGui.PAGE_ID.equals(holder.pageId())) {
            event.setCancelled(true);
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player anvilPlayer && GuildInfoGui.isAnvilSession(anvilPlayer)) {
            GuildInfoGui.clearAnvilSession(anvilPlayer);
            return;
        }

        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof InfoGuiHolder holder)) {
            return;
        }
        if (BackpackInfoGui.STORAGE_PAGE_ID.equals(holder.pageId())) {
            BackpackInfoGui.handleStorageClose(event);
        }
    }

    @EventHandler
    public void onPrepareAnvil(@NotNull PrepareAnvilEvent event) {
        GuildInfoGui.handlePrepareAnvil(event);
    }

    @EventHandler
    public void onUncheckedSignChange(@NotNull UncheckedSignChangeEvent event) {
        if (GuildInfoGui.handleSignInput(event.getPlayer(), event.lines())) {
            event.setCancelled(true);
            return;
        }
        if (BankInfoGui.handleSignInput(event.getPlayer(), event.lines())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSignChange(@NotNull SignChangeEvent event) {
        if (GuildInfoGui.handleSignInput(event.getPlayer(), event.lines())) {
            event.setCancelled(true);
            return;
        }
        if (BankInfoGui.handleSignInput(event.getPlayer(), event.lines())) {
            event.setCancelled(true);
        }
    }
}
