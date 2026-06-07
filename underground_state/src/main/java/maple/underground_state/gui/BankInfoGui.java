package maple.underground_state.gui;

import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import maple.underground_state.UndergroundState;
import maple.underground_state.bank.BankAction;
import maple.underground_state.bank.BankLogEntry;
import maple.underground_state.bank.BankManager;
import maple.underground_state.bank.BankOperationResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BankInfoGui {

    public static final String PAGE_ID = "bank";
    public static final String DEPOSIT_PAGE_ID = "bank_deposit";
    public static final String WITHDRAW_PAGE_ID = "bank_withdraw";
    public static final String LOG_PAGE_ID = "bank_log";

    private static final int MENU_SIZE = 54;
    private static final int SUMMARY_SLOT = 4;
    private static final int DEPOSIT_SLOT = 20;
    private static final int WITHDRAW_SLOT = 22;
    private static final int LOG_SLOT = 24;
    private static final int ALL_SLOT = 20;
    private static final int HALF_SLOT = 22;
    private static final int CUSTOM_SLOT = 24;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_PAGE_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int LOG_PAGE_SIZE = 21;
    private static final int[] LOG_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final String BANK_HEAD_TEXTURE =
            "http://textures.minecraft.net/texture/84b32710cea560854e3fac192f20a633f729afa02de4b075fefb680b301658e0";
    private static final Component TITLE = Component.text("银行", NamedTextColor.WHITE);
    private static final Component DEPOSIT_TITLE = Component.text("银行 - 存入", NamedTextColor.WHITE);
    private static final Component WITHDRAW_TITLE = Component.text("银行 - 取出", NamedTextColor.WHITE);
    private static final Component LOG_TITLE = Component.text("银行 - 日志", NamedTextColor.WHITE);
    private static final TextColor NAME_RGB = TextColor.color(0x63E6A6);
    private static final TextColor ACTION_RGB = TextColor.color(0xFFE36E);
    private static final TextColor SYMBOL_RGB = TextColor.color(0xE0A83A);
    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final Map<UUID, PendingInput> PENDING_INPUTS = new ConcurrentHashMap<>();

    private BankInfoGui() {
    }

    public static void open(@NotNull Player player) {
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, TITLE);
        holder.setInventory(inventory);
        fillInventory(inventory);

        inventory.setItem(SUMMARY_SLOT, createBankSummary(player));
        inventory.setItem(DEPOSIT_SLOT, InfoGuiItems.createNamedItem(
                Material.CHEST,
                nameText("存入银行"),
                List.of(
                        bodyText("把身上的硬币转入银行余额。"),
                        bodyText("银行余额会独立保存，可随时取出。"),
                        Component.empty(),
                        actionText("点击选择存入金额！")
                )
        ));
        inventory.setItem(WITHDRAW_SLOT, InfoGuiItems.createNamedItem(
                Material.EMERALD,
                nameText("从银行中取出"),
                List.of(
                        bodyText("把银行余额取回到身上。"),
                        bodyText("取款成功后会写入银行日志。"),
                        Component.empty(),
                        actionText("点击选择取出金额！")
                )
        ));
        inventory.setItem(LOG_SLOT, InfoGuiItems.createNamedItem(
                Material.WRITABLE_BOOK,
                nameText("银行日志"),
                List.of(
                        bodyText("查看最近的存款与取款记录。"),
                        bodyText("每一笔都会记录时间、金额和操作后余额。"),
                        Component.empty(),
                        actionText("点击查看日志！")
                )
        ));
        inventory.setItem(BACK_SLOT, backItem("返回主页"));
        inventory.setItem(CLOSE_SLOT, closeItem());

        player.openInventory(inventory);
    }

    public static void openDeposit(@NotNull Player player) {
        openAction(player, BankAction.DEPOSIT);
    }

    public static void openWithdraw(@NotNull Player player) {
        openAction(player, BankAction.WITHDRAW);
    }

    public static void openLog(@NotNull Player player, int page) {
        BankManager bankManager = bankManager();
        long bankBalance = bankManager == null ? 0L : bankManager.bankBalance(player.getUniqueId());
        int totalLogs = bankManager == null ? 0 : bankManager.countLogs(player.getUniqueId());
        int totalPages = Math.max(1, (totalLogs + LOG_PAGE_SIZE - 1) / LOG_PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), LOG_PAGE_ID, safePage);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, LOG_TITLE);
        holder.setInventory(inventory);
        fillInventory(inventory);

        inventory.setItem(SUMMARY_SLOT, InfoGuiItems.createNamedItem(
                Material.WRITABLE_BOOK,
                nameText("银行日志"),
                List.of(
                        bodyText("当前银行余额: " + formatNumber(bankBalance) + " 硬币"),
                        bodyText("记录数量: " + totalLogs + " 笔"),
                        bodyText("当前页: " + (safePage + 1) + " / " + totalPages),
                        Component.empty(),
                        bodyText("日志用于回看你的存款与取款流向。")
                )
        ));

        List<BankLogEntry> entries = bankManager == null
                ? List.of()
                : bankManager.loadLogs(player.getUniqueId(), safePage * LOG_PAGE_SIZE, LOG_PAGE_SIZE);
        if (entries.isEmpty()) {
            inventory.setItem(22, InfoGuiItems.createNamedItem(
                    Material.PAPER,
                    nameText("暂无银行日志"),
                    List.of(bodyText("完成一次存款或取款后，这里会显示记录。"))
            ));
        } else {
            for (int index = 0; index < entries.size() && index < LOG_SLOTS.length; index++) {
                inventory.setItem(LOG_SLOTS[index], createLogItem(
                        entries.get(index),
                        safePage * LOG_PAGE_SIZE + index + 1,
                        player.getName()
                ));
            }
        }

        inventory.setItem(BACK_SLOT, backItem("返回银行"));
        if (safePage > 0) {
            inventory.setItem(PREVIOUS_PAGE_SLOT, pageItem("上一页", safePage, totalPages));
        }
        if (safePage + 1 < totalPages) {
            inventory.setItem(NEXT_PAGE_SLOT, pageItem("下一页", safePage + 2, totalPages));
        }
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    public static void handleClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == DEPOSIT_SLOT) {
            openDeposit(player);
            return;
        }
        if (rawSlot == WITHDRAW_SLOT) {
            openWithdraw(player);
            return;
        }
        if (rawSlot == LOG_SLOT) {
            openLog(player, 0);
            return;
        }
        if (rawSlot == BACK_SLOT) {
            MainInfoGui.open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    public static void handleDepositClick(@NotNull Player player, int rawSlot) {
        handleActionClick(player, rawSlot, BankAction.DEPOSIT);
    }

    public static void handleWithdrawClick(@NotNull Player player, int rawSlot) {
        handleActionClick(player, rawSlot, BankAction.WITHDRAW);
    }

    public static void handleLogClick(@NotNull Player player, int rawSlot) {
        int currentPage = currentPage(player, LOG_PAGE_ID);
        if (rawSlot == PREVIOUS_PAGE_SLOT && currentPage > 0) {
            openLog(player, currentPage - 1);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT) {
            openLog(player, currentPage + 1);
            return;
        }
        if (rawSlot == BACK_SLOT) {
            open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    public static boolean handleSignInput(@NotNull Player player, @NotNull List<Component> lines) {
        PendingInput pending = PENDING_INPUTS.remove(player.getUniqueId());
        if (pending == null) {
            return false;
        }

        List<Component> copiedLines = List.copyOf(lines);
        Runnable task = () -> processSignInput(player, pending, copiedLines);
        UndergroundState plugin = UndergroundState.getInstance();
        if (plugin != null && !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            task.run();
        }
        return true;
    }

    private static void processSignInput(@NotNull Player player,
                                         @NotNull PendingInput pending,
                                         @NotNull List<Component> lines) {
        cleanupVirtualSign(player, pending);
        OptionalLong amount = parseAmount(lines);
        if (amount.isEmpty()) {
            player.sendMessage(text("金额判断失败，请重新点击告示牌并输入数字，或使用 1k / 1m / 1b。", NamedTextColor.RED));
            openAction(player, pending.action());
            return;
        }

        BankOperationResult result = pending.action() == BankAction.DEPOSIT
                ? performDeposit(player, amount.getAsLong())
                : performWithdraw(player, amount.getAsLong());
        sendOperationMessage(player, pending.action(), result);
        openAction(player, pending.action());
    }

    private static void openAction(@NotNull Player player, @NotNull BankAction action) {
        boolean deposit = action == BankAction.DEPOSIT;
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), deposit ? DEPOSIT_PAGE_ID : WITHDRAW_PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, deposit ? DEPOSIT_TITLE : WITHDRAW_TITLE);
        holder.setInventory(inventory);
        fillInventory(inventory);

        BankManager bankManager = bankManager();
        long bankBalance = bankManager == null ? 0L : bankManager.bankBalance(player.getUniqueId());
        long walletBalance = bankManager == null ? 0L : bankManager.walletBalance(player);
        long allAmount = deposit ? walletBalance : bankBalance;
        long halfAmount = allAmount / 2L;

        inventory.setItem(SUMMARY_SLOT, createActionSummary(player, action));
        inventory.setItem(ALL_SLOT, InfoGuiItems.createNamedItem(
                deposit ? Material.HOPPER : Material.DROPPER,
                nameText(deposit ? "全部存入" : "全部取出"),
                List.of(
                        bodyText(deposit ? "把身上的全部硬币存入银行。" : "把银行中的全部余额取回身上。"),
                        bodyText("金额会按整数硬币处理。"),
                        Component.empty(),
                        valueLine("目前银行: ", formatNumber(bankBalance) + " 硬币"),
                        valueLine(deposit ? "将存入: " : "将取出: ", formatNumber(allAmount) + " 硬币"),
                        Component.empty(),
                        actionText("点击执行！")
                )
        ));
        inventory.setItem(HALF_SLOT, InfoGuiItems.createNamedItem(
                Material.CHEST_MINECART,
                nameText(deposit ? "存入一半" : "取出一半"),
                List.of(
                        bodyText(deposit ? "把身上一半硬币存入银行。" : "把银行余额的一半取回身上。"),
                        bodyText("不足 1 枚硬币时不会执行。"),
                        Component.empty(),
                        valueLine("目前银行: ", formatNumber(bankBalance) + " 硬币"),
                        valueLine(deposit ? "将存入: " : "将取出: ", formatNumber(halfAmount) + " 硬币"),
                        Component.empty(),
                        actionText("点击执行！")
                )
        ));
        inventory.setItem(CUSTOM_SLOT, InfoGuiItems.createNamedItem(
                Material.OAK_SIGN,
                nameText(deposit ? "输入存入金额" : "输入取出金额"),
                List.of(
                        bodyText("打开告示牌输入自定义金额。"),
                        bodyText("支持纯数字或单位，例如 10000、1k、1m、1b。"),
                        bodyText("中文、英文或其他字符会判断失败。"),
                        Component.empty(),
                        valueLine("目前银行: ", formatNumber(bankBalance) + " 硬币"),
                        valueLine(deposit ? "将存入: " : "将取出: ", "以告示牌输入为准"),
                        Component.empty(),
                        actionText("点击输入！")
                )
        ));
        inventory.setItem(BACK_SLOT, backItem("返回银行"));
        inventory.setItem(CLOSE_SLOT, closeItem());
        player.openInventory(inventory);
    }

    private static void handleActionClick(@NotNull Player player, int rawSlot, @NotNull BankAction action) {
        if (rawSlot == ALL_SLOT) {
            BankOperationResult result = action == BankAction.DEPOSIT ? performDepositAll(player) : performWithdrawAll(player);
            sendOperationMessage(player, action, result);
            openAction(player, action);
            return;
        }
        if (rawSlot == HALF_SLOT) {
            BankOperationResult result = action == BankAction.DEPOSIT ? performDepositHalf(player) : performWithdrawHalf(player);
            sendOperationMessage(player, action, result);
            openAction(player, action);
            return;
        }
        if (rawSlot == CUSTOM_SLOT) {
            openAmountSign(player, action);
            return;
        }
        if (rawSlot == BACK_SLOT) {
            open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private static void openAmountSign(@NotNull Player player, @NotNull BankAction action) {
        Location location = player.getLocation().getBlock().getLocation();
        BlockPosition position = Position.block(location);
        PENDING_INPUTS.put(player.getUniqueId(), new PendingInput(action, location, position));

        player.closeInventory();
        player.sendBlockChange(location, Material.OAK_SIGN.createBlockData());
        player.sendSignChange(location, List.of(
                Component.empty(),
                Component.empty(),
                Component.empty(),
                Component.empty()
        ));

        UndergroundState plugin = UndergroundState.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && PENDING_INPUTS.containsKey(player.getUniqueId())) {
                player.openVirtualSign(position, Side.FRONT);
            }
        }, 1L);
    }

    private static @NotNull ItemStack createBankSummary(@NotNull Player player) {
        BankManager bankManager = bankManager();
        long bankBalance = bankManager == null ? 0L : bankManager.bankBalance(player.getUniqueId());
        long walletBalance = bankManager == null ? 0L : bankManager.walletBalance(player);
        return InfoGuiItems.createCustomHead(
                BANK_HEAD_TEXTURE,
                nameText("银行"),
                List.of(
                        bodyText("查看你的存款、提款与目前的金币管理状态。"),
                        bodyText("银行可以暂存硬币，降低外出时身上资金过多的风险。"),
                        Component.empty(),
                        valueLine("身上资金: ", formatNumber(walletBalance) + " 硬币"),
                        valueLine("银行余额: ", formatNumber(bankBalance) + " 硬币"),
                        Component.empty(),
                        bodyText("存款、取款和自定义金额都会写入日志。"),
                        actionText("点击下方按钮选择操作！")
                )
        );
    }

    private static @NotNull ItemStack createActionSummary(@NotNull Player player, @NotNull BankAction action) {
        BankManager bankManager = bankManager();
        long bankBalance = bankManager == null ? 0L : bankManager.bankBalance(player.getUniqueId());
        long walletBalance = bankManager == null ? 0L : bankManager.walletBalance(player);
        boolean deposit = action == BankAction.DEPOSIT;
        return InfoGuiItems.createNamedItem(
                deposit ? Material.CHEST : Material.EMERALD,
                nameText(deposit ? "存入银行" : "从银行中取出"),
                List.of(
                        bodyText(deposit ? "选择要从身上转入银行的金额。" : "选择要从银行取回身上的金额。"),
                        Component.empty(),
                        valueLine("身上资金: ", formatNumber(walletBalance) + " 硬币"),
                        valueLine("银行余额: ", formatNumber(bankBalance) + " 硬币"),
                        Component.empty(),
                        bodyText("全部、一半或告示牌自定义金额都可以使用。")
                )
        );
    }

    private static @NotNull ItemStack createLogItem(@NotNull BankLogEntry entry,
                                                    int number,
                                                    @NotNull String playerId) {
        boolean deposit = entry.action() == BankAction.DEPOSIT;
        return InfoGuiItems.createNamedItem(
                deposit ? Material.LIME_DYE : Material.RED_DYE,
                nameText("#" + number + " " + entry.action().displayName()),
                List.of(
                        valueLine("玩家ID: ", playerId),
                        valueLine("金额: ", formatNumber(entry.amount()) + " 硬币"),
                        valueLine("操作后余额: ", formatNumber(entry.balanceAfter()) + " 硬币"),
                        valueLine("时间: ", LOG_TIME_FORMAT.format(entry.createdAt()))
                )
        );
    }

    private static @NotNull ItemStack pageItem(@NotNull String label, int targetPage, int totalPages) {
        return InfoGuiItems.createNamedItem(
                Material.ARROW,
                nameText(label),
                List.of(bodyText("点击切换到第 " + targetPage + " 页 / 共 " + totalPages + " 页"))
        );
    }

    private static @NotNull ItemStack backItem(@NotNull String label) {
        return InfoGuiItems.createNamedItem(
                Material.ARROW,
                nameText(label),
                List.of(bodyText("点击返回上一层页面。"))
        );
    }

    private static @NotNull ItemStack closeItem() {
        return InfoGuiItems.createNamedItem(
                Material.BARRIER,
                nameText("关闭页面"),
                List.of(bodyText("点击关闭这个页面。"))
        );
    }

    private static void fillInventory(@NotNull Inventory inventory) {
        ItemStack filler = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private static @NotNull BankOperationResult performDepositAll(@NotNull Player player) {
        BankManager manager = bankManager();
        return manager == null
                ? unavailableResult(player)
                : manager.depositAll(player);
    }

    private static @NotNull BankOperationResult performDepositHalf(@NotNull Player player) {
        BankManager manager = bankManager();
        return manager == null
                ? unavailableResult(player)
                : manager.depositHalf(player);
    }

    private static @NotNull BankOperationResult performDeposit(@NotNull Player player, long amount) {
        BankManager manager = bankManager();
        return manager == null
                ? unavailableResult(player)
                : manager.deposit(player, amount);
    }

    private static @NotNull BankOperationResult performWithdrawAll(@NotNull Player player) {
        BankManager manager = bankManager();
        return manager == null
                ? unavailableResult(player)
                : manager.withdrawAll(player);
    }

    private static @NotNull BankOperationResult performWithdrawHalf(@NotNull Player player) {
        BankManager manager = bankManager();
        return manager == null
                ? unavailableResult(player)
                : manager.withdrawHalf(player);
    }

    private static @NotNull BankOperationResult performWithdraw(@NotNull Player player, long amount) {
        BankManager manager = bankManager();
        return manager == null
                ? unavailableResult(player)
                : manager.withdraw(player, amount);
    }

    private static @NotNull BankOperationResult unavailableResult(@NotNull Player player) {
        return new BankOperationResult(
                BankOperationResult.Status.ECONOMY_UNAVAILABLE,
                0L,
                0L,
                0L
        );
    }

    private static void sendOperationMessage(@NotNull Player player,
                                             @NotNull BankAction action,
                                             @NotNull BankOperationResult result) {
        if (result.success()) {
            player.sendMessage(Component.text("[银行] ", NamedTextColor.GOLD)
                    .append(Component.text(action.displayName() + "成功: ", NamedTextColor.GREEN))
                    .append(Component.text(formatNumber(result.amount()) + " 硬币", NamedTextColor.YELLOW))
                    .append(Component.text("，银行余额 ", NamedTextColor.GRAY))
                    .append(Component.text(formatNumber(result.bankBalance()), NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            return;
        }

        String message = switch (result.status()) {
            case ECONOMY_UNAVAILABLE -> "经济系统暂时不可用，无法判断身上资金。";
            case INVALID_AMOUNT -> "金额必须大于 0。";
            case INSUFFICIENT_WALLET -> "身上的硬币不足，无法存入这笔金额。";
            case INSUFFICIENT_BANK -> "银行余额不足，无法取出这笔金额。";
            case STORAGE_ERROR -> "银行数据保存失败，请稍后再试。";
            case ECONOMY_ERROR -> "经济系统扣款或加款失败，请稍后再试。";
            case SUCCESS -> "操作成功。";
        };
        player.sendMessage(text("[银行] " + message, NamedTextColor.RED));
    }

    private static @NotNull OptionalLong parseAmount(@NotNull List<Component> lines) {
        StringBuilder input = new StringBuilder();
        for (Component line : lines) {
            String text = PLAIN_TEXT.serialize(line).trim();
            if (!text.isBlank()) {
                if (!input.isEmpty()) {
                    input.append(' ');
                }
                input.append(text);
            }
        }
        String raw = input.toString().replace(",", "").replace("_", "").trim();
        if (!raw.matches("(?:[0-9]+|[0-9]+(?:\\.[0-9]+)?[kKmMbB])")) {
            return OptionalLong.empty();
        }
        try {
            BigDecimal amount = new BigDecimal(raw.substring(0, numericEnd(raw)));
            char suffix = raw.charAt(raw.length() - 1);
            if (suffix == 'k' || suffix == 'K') {
                amount = amount.multiply(BigDecimal.valueOf(1_000L));
            } else if (suffix == 'm' || suffix == 'M') {
                amount = amount.multiply(BigDecimal.valueOf(1_000_000L));
            } else if (suffix == 'b' || suffix == 'B') {
                amount = amount.multiply(BigDecimal.valueOf(1_000_000_000L));
            }
            if (amount.compareTo(BigDecimal.ONE) < 0 || amount.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                return OptionalLong.empty();
            }
            long parsed = amount.longValue();
            return parsed > 0L ? OptionalLong.of(parsed) : OptionalLong.empty();
        } catch (NumberFormatException | ArithmeticException ignored) {
            return OptionalLong.empty();
        }
    }

    private static int numericEnd(@NotNull String raw) {
        char last = raw.charAt(raw.length() - 1);
        if (last == 'k' || last == 'K' || last == 'm' || last == 'M' || last == 'b' || last == 'B') {
            return raw.length() - 1;
        }
        return raw.length();
    }

    private static void cleanupVirtualSign(@NotNull Player player, @NotNull PendingInput pending) {
        player.sendBlockChange(pending.location(), pending.location().getBlock().getBlockData());
    }

    private static int currentPage(@NotNull Player player, @NotNull String pageId) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof InfoGuiHolder holder && pageId.equals(holder.pageId())) {
            return holder.page();
        }
        return 0;
    }

    private static BankManager bankManager() {
        UndergroundState plugin = UndergroundState.getInstance();
        return plugin == null ? null : plugin.getBankManager();
    }

    private static @NotNull Component valueLine(@NotNull String label, @NotNull String value) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, ACTION_RGB))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static @NotNull Component nameText(@NotNull String text) {
        return Component.text(text, NAME_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static @NotNull Component actionText(@NotNull String text) {
        return Component.text(text, ACTION_RGB)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD);
    }

    private static @NotNull Component bodyText(@NotNull String text) {
        return text(text, NamedTextColor.GRAY);
    }

    private static @NotNull Component text(@NotNull String text, @NotNull NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static @NotNull String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private record PendingInput(@NotNull BankAction action,
                                @NotNull Location location,
                                @NotNull BlockPosition position) {
    }
}
