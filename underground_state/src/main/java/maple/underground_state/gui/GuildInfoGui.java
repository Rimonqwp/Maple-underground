package maple.underground_state.gui;

import maple.underground_state.UndergroundState;
import maple.underground_state.bank.CmiEconomyBridge;
import maple.underground_state.guild.GuildDataDB;
import maple.underground_state.guild.GuildDataDB.CreateGuildResult;
import maple.underground_state.guild.GuildDataDB.Guild;
import maple.underground_state.guild.GuildDataDB.GuildBankChangeResult;
import maple.underground_state.guild.GuildDataDB.GuildBankLogEntry;
import maple.underground_state.guild.GuildDataDB.GuildInvite;
import maple.underground_state.guild.GuildDataDB.GuildMember;
import maple.underground_state.guild.GuildDataDB.GuildMemberProfile;
import maple.underground_state.guild.GuildDataDB.GuildRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class GuildInfoGui {

    public static final String PAGE_ID = "guild";
    public static final String SETTINGS_PAGE_ID = "guild_settings";
    public static final String TRANSFER_OWNER_PAGE_ID = "guild_transfer_owner";
    public static final String ROLES_PAGE_ID = "guild_roles";
    public static final String ROLE_EDIT_PAGE_ID = "guild_role_edit";
    public static final String ROLE_PERMISSION_CATEGORY_PAGE_ID = "guild_role_perm_cat";
    public static final String MEMBER_PROFILE_PAGE_ID = "guild_member_profile";
    public static final String MEMBER_ASSIGN_ROLE_PAGE_ID = "guild_member_assign_role";
    public static final String MEMBER_DISCORD_EDIT_PAGE_ID = "guild_member_discord_edit";
    public static final String INVITE_PAGE_ID = "guild_invite";
    public static final String RECEIVED_INVITES_PAGE_ID = "guild_received_invites";
    public static final String BANK_PAGE_ID = "guild_bank";
    public static final String BANK_DEPOSIT_PAGE_ID = "guild_bank_deposit";
    public static final String BANK_WITHDRAW_PAGE_ID = "guild_bank_withdraw";
    public static final String BANK_LOG_PAGE_ID = "guild_bank_log";
    public static final String CREATE_PAGE_ID = "guild_create";
    public static final String ROLE_CREATE_PAGE_ID = "guild_role_create";

    private static final int MENU_SIZE = 54;
    private static final int COMPACT_MENU_SIZE = 45;
    private static final int ANVIL_INPUT_SLOT = 0;
    private static final int ANVIL_RESULT_SLOT = 2;
    private static final int LEADER_SLOT = 0;
    private static final int INFO_SLOT = 1;
    private static final int BANK_SLOT = 2;
    private static final int CREATE_SLOT = 8;
    private static final int INVITES_SLOT = 7;
    private static final int LEAVE_GUILD_SLOT = 6;
    private static final int INVITE_SLOT = 7;
    private static final int SETTINGS_SLOT = 8;
    private static final int DISPLAY_MODE_SLOT = 17;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int FIRST_PAGE_SLOT = 50;
    private static final int PREVIOUS_PAGE_SLOT = 51;
    private static final int NEXT_PAGE_SLOT = 52;
    private static final int LAST_PAGE_SLOT = 53;
    private static final int PAGE_SIZE = 27;
    private static final int ROLE_SETTINGS_SLOT = 22;
    private static final int TRANSFER_OWNER_SLOT = 20;
    private static final int DISBAND_GUILD_SLOT = 24;
    private static final int MEMBER_PROFILE_HEAD_SLOT = 4;
    private static final int MEMBER_KICK_SLOT = 20;
    private static final int MEMBER_DISCORD_EDIT_SLOT = 22;
    private static final int MEMBER_ROLE_ASSIGN_SLOT = 24;
    private static final int FREE_JOIN_SLOT = 13;
    private static final int ROLE_CREATE_SLOT = 8;
    private static final int SET_DEFAULT_ROLE_SLOT = 4;
    private static final int[] CATEGORY_SLOTS = {20, 21, 22, 23, 24};
    private static final int BANK_SUMMARY_SLOT = 4;
    private static final int BANK_DEPOSIT_SLOT = 20;
    private static final int BANK_WITHDRAW_SLOT = 22;
    private static final int BANK_LOG_SLOT = 24;
    private static final int BANK_ALL_SLOT = 20;
    private static final int BANK_HALF_SLOT = 22;
    private static final int BANK_CUSTOM_SLOT = 24;
    private static final int COMPACT_BACK_SLOT = 36;
    private static final int COMPACT_CLOSE_SLOT = 40;
    private static final int COMPACT_PREVIOUS_SLOT = 39;
    private static final int COMPACT_NEXT_SLOT = 41;
    private static final int[] MEMBER_SLOTS = {
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] ROLE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int[] BLACK_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16,
            46, 47, 48
    };

    private static final String LEAVE_GUILD_TEXTURE =
            "http://textures.minecraft.net/texture/a9dbed522e8de1a681dddd37854ee4267efc48b59917f9a9acb420d6fdb9";
    private static final String INVITE_TEXTURE =
            "http://textures.minecraft.net/texture/b056bc1244fcff99344f12aba42ac23fee6ef6e3351d27d273c1572531f";
    private static final String BANK_TEXTURE =
            "http://textures.minecraft.net/texture/84b32710cea560854e3fac192f20a633f729afa02de4b075fefb680b301658e0";
    private static final String FIRST_PAGE_TEXTURE =
            "http://textures.minecraft.net/texture/49b2bee39b6ef47e182d6f1dca9dea842fcd68bda9bacc6a6d66a8dcdf3ec";
    private static final String PREVIOUS_PAGE_TEXTURE =
            "http://textures.minecraft.net/texture/f5347423ee55daa7923668fca8581985ff5389a45435321efad537af23d";
    private static final String NEXT_PAGE_TEXTURE =
            "http://textures.minecraft.net/texture/4ef356ad2aa7b1678aecb88290e5fa5a3427e5e456ff42fb515690c67517b8";
    private static final String LAST_PAGE_TEXTURE =
            "http://textures.minecraft.net/texture/141ff6bc67a481232d2e669e43c4f087f9d2306665b4f829fb86892d13b70ca";

    private static final Component TITLE = Component.text("公会", NamedTextColor.WHITE);
    private static final Component SETTINGS_TITLE = Component.text("公会设定", NamedTextColor.WHITE);
    private static final Component ROLES_TITLE = Component.text("身份组设定", NamedTextColor.WHITE);
    private static final Component ROLE_EDIT_TITLE = Component.text("身份组权限", NamedTextColor.WHITE);
    private static final Component CREATE_TITLE = Component.text("创建公会", NamedTextColor.WHITE);
    private static final Component ROLE_CREATE_TITLE = Component.text("创建身份组", NamedTextColor.WHITE);
    private static final TextColor NAME_RGB = TextColor.color(0x63E6A6);
    private static final TextColor ACTION_RGB = TextColor.color(0xFFE36E);
    private static final NamespacedKey ANVIL_RAW_NAME_KEY =
            new NamespacedKey("underground_state", "guild_anvil_raw_name");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final List<PermissionDef> PERMISSIONS = List.of(
            new PermissionDef("guild.bank.deposit", "银行", "存入公会银行", "允许把自己的金币存入公会银行。", 10),
            new PermissionDef("guild.bank.deposit.limited", "银行", "有限额度存入", "按该公会该身份组的额度限制存入。", 11),
            new PermissionDef("guild.bank.deposit.unlimited", "银行", "无限额度存入", "忽略存入额度限制。", 12),
            new PermissionDef("guild.bank.withdraw", "银行", "取出公会银行", "允许从公会银行取出金币。", 13),
            new PermissionDef("guild.bank.withdraw.limited", "银行", "有限额度取出", "按该公会该身份组的额度限制取出。", 14),
            new PermissionDef("guild.bank.withdraw.unlimited", "银行", "无限额度取出", "忽略取出额度限制。", 15),
            new PermissionDef("guild.bank.view_logs", "银行", "查看银行流水", "查看公会银行存取记录。", 16),
            new PermissionDef("guild.bank.set_limits", "银行", "调整银行额度", "编辑身份组的公会银行额度。", 19),
            new PermissionDef("guild.invite.create", "邀请", "邀请玩家", "邀请没有公会的玩家加入。", 20),
            new PermissionDef("guild.invite.cancel", "邀请", "取消邀请", "取消本公会发出的邀请。", 21),
            new PermissionDef("guild.invite.view", "邀请", "查看邀请", "查看待处理邀请列表。", 22),
            new PermissionDef("guild.member.kick", "成员", "踢出玩家", "踢出低权限公会成员。", 23),
            new PermissionDef("guild.member.blacklist.add", "成员", "加入黑名单", "把玩家加入公会黑名单。", 24),
            new PermissionDef("guild.member.blacklist.remove", "成员", "解除黑名单", "解除玩家黑名单。", 25),
            new PermissionDef("guild.member.blacklist.view", "成员", "查看黑名单", "查看公会黑名单。", 28),
            new PermissionDef("guild.member.role.assign", "成员", "调整成员身份组", "调整低权限成员的身份组。", 29),
            new PermissionDef("guild.role.create", "身份组", "创建身份组", "创建新的身份组。", 30),
            new PermissionDef("guild.role.delete", "身份组", "删除身份组", "删除非系统、非默认身份组。", 31),
            new PermissionDef("guild.role.rename", "身份组", "重命名身份组", "修改身份组名称。", 32),
            new PermissionDef("guild.role.permissions.edit", "身份组", "编辑权限", "编辑身份组权限开关。", 33),
            new PermissionDef("guild.role.default.set", "身份组", "设置默认身份组", "设置新成员加入后的默认身份组。", 34),
            new PermissionDef("guild.info.name.edit", "资料", "编辑公会名字", "修改公会名称。", 37),
            new PermissionDef("guild.info.description.edit", "资料", "编辑公会资料", "修改简介、资料或公告。", 38),
            new PermissionDef("guild.info.website.edit", "资料", "编辑公会网站", "设置或修改公会网站。", 39),
            new PermissionDef("guild.info.settings.edit", "资料", "编辑基础设定", "修改公开、申请等基础设定。", 40)
    );
    private static final Map<UUID, Long> EDITING_ROLE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> EDITING_PERMISSION_CATEGORY = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> VIEWING_MEMBER = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> VIEWING_MEMBER_BACK_PAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> ANVIL_INPUT_NAMES = new ConcurrentHashMap<>();
    private static final Map<UUID, String> ANVIL_PAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingBankInput> PENDING_BANK_INPUTS = new ConcurrentHashMap<>();
    private static volatile CmiEconomyBridge economy;

    private enum RoleCreateResult {
        SUCCESS,
        NO_GUILD,
        NO_PERMISSION,
        STORAGE_ERROR
    }

    private GuildInfoGui() {
    }

    public static void open(@NotNull Player player) {
        open(player, 0);
    }

    public static void open(@NotNull Player player, int page) {
        GuildDataDB db = guildDb();
        Optional<Guild> guild = db == null ? Optional.empty() : db.findGuildByMember(player.getUniqueId());
        int totalItems = db == null
                ? 0
                : guild.map(value -> db.countMembers(value.id())).orElseGet(() -> db.countOtherGuildMembers(player.getUniqueId()));
        int totalPages = Math.max(1, (totalItems + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), PAGE_ID, safePage);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, TITLE);
        holder.setInventory(inventory);
        fillBase(inventory);

        if (guild.isPresent()) {
            renderGuildHome(inventory, guild.get(), safePage, totalPages);
        } else {
            renderNoGuildHome(inventory, player, safePage, totalPages);
        }

        player.openInventory(inventory);
    }

    public static void handleClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == BACK_SLOT) {
            MainInfoGui.open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        GuildDataDB db = guildDb();
        Optional<Guild> guild = db == null ? Optional.empty() : db.findGuildByMember(player.getUniqueId());
        boolean hasGuild = guild.isPresent();

        if (!hasGuild && rawSlot == CREATE_SLOT) {
            openCreateAnvil(player);
            return;
        }
        if (!hasGuild && rawSlot == INVITES_SLOT) {
            openReceivedInvites(player, 0);
            return;
        }
        if (hasGuild && rawSlot == SETTINGS_SLOT) {
            if (!canOpenGuildSettings(player)) {
                player.sendMessage(text("[公会] 你没有权限打开公会设定。", NamedTextColor.RED));
                return;
            }
            openSettings(player);
            return;
        }
        if (hasGuild && rawSlot == INVITE_SLOT) {
            openInvite(player);
            return;
        }
        if (hasGuild && rawSlot == BANK_SLOT) {
            openGuildBank(player);
            return;
        }
        if (hasGuild && rawSlot == LEAVE_GUILD_SLOT) {
            leaveGuild(player, guild.get());
            return;
        }
        if (hasGuild && rawSlot == INVITE_SLOT) {
            player.sendMessage(text("[公会] 邀请玩家功能已预留，下一步可以接入在线玩家选择页。", NamedTextColor.YELLOW));
            return;
        }
        if (hasGuild && rawSlot == BANK_SLOT) {
            player.sendMessage(text("[公会] 公会银行入口已分离，后续会接入独立公会银行页面。", NamedTextColor.YELLOW));
            return;
        }
        int currentPage = currentPage(player, PAGE_ID);
        int totalItems = db == null
                ? 0
                : guild.map(value -> db.countMembers(value.id())).orElseGet(() -> db.countOtherGuildMembers(player.getUniqueId()));
        int totalPages = Math.max(1, (totalItems + PAGE_SIZE - 1) / PAGE_SIZE);

        if (rawSlot == FIRST_PAGE_SLOT && currentPage > 0) {
            open(player, 0);
            return;
        }
        if (rawSlot == PREVIOUS_PAGE_SLOT && currentPage > 0) {
            open(player, currentPage - 1);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT && currentPage + 1 < totalPages) {
            open(player, currentPage + 1);
            return;
        }
        if (rawSlot == LAST_PAGE_SLOT && currentPage + 1 < totalPages) {
            open(player, totalPages - 1);
            return;
        }
        if (hasGuild) {
            int memberIndex = indexOf(MEMBER_SLOTS, rawSlot);
            if (memberIndex >= 0) {
                openMemberProfileFromSlot(player, guild.get(), currentPage, memberIndex);
            }
            return;
        }
        if (!hasGuild) {
            int memberIndex = indexOf(MEMBER_SLOTS, rawSlot);
            if (memberIndex >= 0) {
                joinFreeGuildFromMemberSlot(player, currentPage, memberIndex);
            }
        }
    }

    private static void openMemberProfileFromSlot(@NotNull Player player,
                                                  @NotNull Guild guild,
                                                  int page,
                                                  int memberIndex) {
        GuildDataDB db = guildDb();
        if (db == null) {
            return;
        }
        List<GuildMember> members = db.loadMembers(guild.id(), page * PAGE_SIZE, PAGE_SIZE);
        if (memberIndex < 0 || memberIndex >= members.size()) {
            return;
        }
        openMemberProfile(player, members.get(memberIndex).uuid(), page);
    }

    public static void openMemberProfile(@NotNull Player player, @NotNull UUID targetUuid, int backPage) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        Optional<GuildMember> target = db.loadMember(guild.get().id(), targetUuid);
        if (target.isEmpty()) {
            player.sendMessage(text("[公会] 该玩家已经不在当前公会。", NamedTextColor.RED));
            open(player, backPage);
            return;
        }

        VIEWING_MEMBER.put(player.getUniqueId(), targetUuid);
        VIEWING_MEMBER_BACK_PAGE.put(player.getUniqueId(), Math.max(0, backPage));

        GuildMember member = target.get();
        GuildMemberProfile profile = db.loadMemberProfile(guild.get().id(), targetUuid)
                .orElse(new GuildMemberProfile(null, null));
        String discord = memberDiscordDisplay(member.uuid());
        Optional<GuildRole> memberRole = db.findMemberRole(member.uuid());
        boolean canKick = canManageMember(player, member, "guild.member.kick");
        boolean canAssignRole = canManageMember(player, member, "guild.member.role.assign");

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), MEMBER_PROFILE_PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.text("成员资讯", NamedTextColor.WHITE));
        holder.setInventory(inventory);
        fillPanel(inventory);

        inventory.setItem(MEMBER_PROFILE_HEAD_SLOT, playerHead(
                member.uuid(),
                member.playerName(),
                nameText(member.playerName()),
                List.of(
                        valueLine("公会: ", formatGuildName(member.guildName())),
                        valueLine("身份组: ", memberRole.map(GuildRole::name).orElse(roleName(member.role()))),
                        valueLine("Discord: ", discord),
                        valueLine("资料: ", profile.infoNote() == null ? "未填写" : profile.infoNote()),
                        valueLine("状态: ", onlineStatusText(member.uuid())),
                        valueLine("加入时间: ", TIME_FORMAT.format(member.joinedAt()))
                )
        ));

        if (canKick) {
            inventory.setItem(MEMBER_KICK_SLOT, InfoGuiItems.createNamedItem(
                    Material.RED_DYE,
                    nameText("踢出公会"),
                    List.of(
                            bodyText("将该玩家移出当前公会。"),
                            valueLine("目标: ", member.playerName()),
                            Component.empty(),
                            actionText("点击踢出公会")
                    )
            ));
        }
        if (canAssignRole) {
            inventory.setItem(MEMBER_ROLE_ASSIGN_SLOT, InfoGuiItems.createNamedItem(
                    Material.NAME_TAG,
                    nameText("更换身份组"),
                    List.of(
                            bodyText("为该成员选择新的公会身份组。"),
                            valueLine("当前: ", memberRole.map(GuildRole::name).orElse(roleName(member.role()))),
                            Component.empty(),
                            actionText("点击选择身份组")
                    )
            ));
        }
        inventory.setItem(MEMBER_DISCORD_EDIT_SLOT, InfoGuiItems.createNamedItem(
                Material.WRITABLE_BOOK,
                nameText("Discord"),
                List.of(
                        valueLine("玩家: ", member.playerName()),
                        valueLine("Discord: ", discord)
                )
        ));

        inventory.setItem(BACK_SLOT, backItem("返回公会主页"));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    public static void handleMemberProfileClick(@NotNull Player player, int rawSlot) {
        int backPage = VIEWING_MEMBER_BACK_PAGE.getOrDefault(player.getUniqueId(), 0);
        if (rawSlot == BACK_SLOT) {
            open(player, backPage);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        UUID targetUuid = VIEWING_MEMBER.get(player.getUniqueId());
        if (targetUuid == null) {
            open(player, backPage);
            return;
        }
        if (rawSlot == MEMBER_KICK_SLOT) {
            kickMember(player, targetUuid, backPage);
            return;
        }
        if (rawSlot == MEMBER_ROLE_ASSIGN_SLOT) {
            openMemberRoleAssign(player, 0);
        }
    }

    public static void openMemberRoleAssign(@NotNull Player player, int page) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        UUID targetUuid = VIEWING_MEMBER.get(player.getUniqueId());
        if (guild.isEmpty() || db == null || targetUuid == null) {
            open(player);
            return;
        }
        Optional<GuildMember> target = db.loadMember(guild.get().id(), targetUuid);
        if (target.isEmpty() || !canManageMember(player, target.get(), "guild.member.role.assign")) {
            player.sendMessage(text("[公会] 你没有权限调整该成员的身份组。", NamedTextColor.RED));
            openMemberProfile(player, targetUuid, VIEWING_MEMBER_BACK_PAGE.getOrDefault(player.getUniqueId(), 0));
            return;
        }

        List<GuildRole> roles = assignableRoles(player, guild.get(), target.get());
        int totalPages = Math.max(1, (roles.size() + ROLE_SLOTS.length - 1) / ROLE_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), MEMBER_ASSIGN_ROLE_PAGE_ID, safePage);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.text("更换身份组", NamedTextColor.WHITE));
        holder.setInventory(inventory);
        fillPanel(inventory);

        inventory.setItem(4, playerHead(
                target.get().uuid(),
                target.get().playerName(),
                nameText("更换身份组: " + target.get().playerName()),
                List.of(
                        valueLine("公会: ", formatGuildName(guild.get().name())),
                        bodyText("点击下方身份组即可套用。")
                )
        ));
        if (roles.isEmpty()) {
            inventory.setItem(22, InfoGuiItems.createNamedItem(
                    Material.PAPER,
                    nameText("暂无可选择身份组"),
                    List.of(bodyText("没有低于你权限等级的身份组可分配。"))
            ));
        } else {
            int offset = safePage * ROLE_SLOTS.length;
            for (int index = 0; index < ROLE_SLOTS.length && offset + index < roles.size(); index++) {
                inventory.setItem(ROLE_SLOTS[index], memberAssignableRoleItem(roles.get(offset + index)));
            }
        }

        renderNavigation(inventory, safePage, totalPages);
        inventory.setItem(BACK_SLOT, backItem("返回成员资讯"));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    public static void handleMemberRoleAssignClick(@NotNull Player player, int rawSlot) {
        int page = currentPage(player, MEMBER_ASSIGN_ROLE_PAGE_ID);
        if (rawSlot == BACK_SLOT) {
            UUID targetUuid = VIEWING_MEMBER.get(player.getUniqueId());
            if (targetUuid == null) {
                open(player);
            } else {
                openMemberProfile(player, targetUuid, VIEWING_MEMBER_BACK_PAGE.getOrDefault(player.getUniqueId(), 0));
            }
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        UUID targetUuid = VIEWING_MEMBER.get(player.getUniqueId());
        if (guild.isEmpty() || db == null || targetUuid == null) {
            open(player);
            return;
        }
        handlePagedNavigation(player, rawSlot, page, ROLE_SLOTS.length, () -> openMemberRoleAssign(player, 0), p -> openMemberRoleAssign(player, p));
        int slotIndex = indexOf(ROLE_SLOTS, rawSlot);
        if (slotIndex < 0) {
            return;
        }
        Optional<GuildMember> target = db.loadMember(guild.get().id(), targetUuid);
        if (target.isEmpty()) {
            open(player);
            return;
        }
        List<GuildRole> roles = assignableRoles(player, guild.get(), target.get());
        int roleIndex = page * ROLE_SLOTS.length + slotIndex;
        if (roleIndex < 0 || roleIndex >= roles.size()) {
            return;
        }

        GuildRole role = roles.get(roleIndex);
        if (!db.setMemberRole(guild.get().id(), targetUuid, role)) {
            player.sendMessage(text("[公会] 更换身份组失败，请稍后再试。", NamedTextColor.RED));
            openMemberRoleAssign(player, page);
            return;
        }
        player.sendMessage(text("[公会] 已将 " + target.get().playerName() + " 的身份组更换为 " + role.name() + "。", NamedTextColor.GREEN));
        Player targetPlayer = Bukkit.getPlayer(targetUuid);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(text("[公会] 你的身份组已被更换为 " + role.name() + "。", NamedTextColor.YELLOW));
        }
        openMemberProfile(player, targetUuid, VIEWING_MEMBER_BACK_PAGE.getOrDefault(player.getUniqueId(), 0));
    }

    public static void openInvite(@NotNull Player player) {
        Optional<Guild> guild = currentGuild(player);
        if (guild.isEmpty()) {
            open(player);
            return;
        }
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), INVITE_PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.text("邀请玩家", NamedTextColor.WHITE));
        holder.setInventory(inventory);
        fillPanel(inventory);

        List<Player> targets = onlinePlayersWithoutGuild(player);
        inventory.setItem(4, InfoGuiItems.createCustomHead(
                INVITE_TEXTURE,
                nameText("邀请玩家"),
                List.of(
                        valueLine("公会: ", formatGuildName(guild.get().name())),
                        valueLine("可邀请玩家: ", String.valueOf(targets.size())),
                        bodyText("点击玩家头颅会向对方发起邀请。")
                )
        ));
        if (targets.isEmpty()) {
            inventory.setItem(22, InfoGuiItems.createNamedItem(
                    Material.PAPER,
                    nameText("暂无可邀请玩家"),
                    List.of(bodyText("在线玩家里没有未加入公会的玩家。"))
            ));
        } else {
            for (int index = 0; index < targets.size() && index < ROLE_SLOTS.length; index++) {
                Player target = targets.get(index);
                inventory.setItem(ROLE_SLOTS[index], playerHead(
                        target.getUniqueId(),
                        target.getName(),
                        nameText(target.getName()),
                        List.of(
                                bodyText("对方接受后才会加入当前公会。"),
                                Component.empty(),
                                actionText("点击邀请")
                        )
                ));
            }
        }
        inventory.setItem(BACK_SLOT, backItem("返回公会主页"));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    public static void handleInviteClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == BACK_SLOT) {
            open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        int index = indexOf(ROLE_SLOTS, rawSlot);
        if (index < 0) {
            return;
        }
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        List<Player> targets = onlinePlayersWithoutGuild(player);
        if (index >= targets.size()) {
            return;
        }
        Player target = targets.get(index);
        if (db.createInvite(guild.get(), player, target)) {
            player.sendMessage(text("[公会] 已向 " + target.getName() + " 发起邀请。", NamedTextColor.GREEN));
            target.sendMessage(text("[公会] " + player.getName() + " 邀请你加入 " + stripRgbCodes(guild.get().name()) + "。", NamedTextColor.YELLOW));
            target.sendMessage(text("[公会] 打开公会页面可以接受邀请。", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(text("[公会] 邀请失败，该玩家可能已经加入公会。", NamedTextColor.RED));
        }
        openInvite(player);
    }

    public static void openReceivedInvites(@NotNull Player player, int page) {
        if (currentGuild(player).isPresent()) {
            open(player);
            return;
        }
        GuildDataDB db = guildDb();
        List<GuildInvite> invites = db == null ? List.of() : db.loadInvites(player.getUniqueId());
        int pageSize = ROLE_SLOTS.length;
        int totalPages = Math.max(1, (invites.size() + pageSize - 1) / pageSize);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), RECEIVED_INVITES_PAGE_ID, safePage);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.text("收到的公会邀请", NamedTextColor.WHITE));
        holder.setInventory(inventory);
        fillPanel(inventory);

        inventory.setItem(4, InfoGuiItems.createCustomHead(
                INVITE_TEXTURE,
                nameText("收到的邀请"),
                List.of(
                        valueLine("邀请数量: ", String.valueOf(invites.size())),
                        bodyText("点击下方公会邀请即可接受。"),
                        Component.empty(),
                        actionText("接受后会加入该公会")
                )
        ));

        if (invites.isEmpty()) {
            inventory.setItem(22, InfoGuiItems.createNamedItem(
                    Material.PAPER,
                    nameText("暂无公会邀请"),
                    List.of(bodyText("收到邀请后会在这里显示。"))
            ));
        } else {
            int start = safePage * pageSize;
            int end = Math.min(invites.size(), start + pageSize);
            List<GuildInvite> pageInvites = invites.subList(start, end);
            for (int index = 0; index < pageInvites.size(); index++) {
                GuildInvite invite = pageInvites.get(index);
                inventory.setItem(ROLE_SLOTS[index], InfoGuiItems.createNamedItem(
                        Material.PAPER,
                        nameText(stripRgbCodes(invite.guildName())),
                        List.of(
                                valueLine("公会: ", formatGuildName(invite.guildName())),
                                valueLine("邀请人: ", invite.inviterName()),
                                valueLine("时间: ", TIME_FORMAT.format(invite.createdAt())),
                                Component.empty(),
                                actionText("点击接受邀请")
                        )
                ));
            }
        }

        inventory.setItem(BACK_SLOT, backItem("返回公会主页"));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        renderNavigation(inventory, safePage, totalPages);
        player.openInventory(inventory);
    }

    public static void handleReceivedInvitesClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == BACK_SLOT) {
            open(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        GuildDataDB db = guildDb();
        if (db == null) {
            player.sendMessage(text("[公会] 数据库暂时不可用。", NamedTextColor.RED));
            return;
        }
        if (currentGuild(player).isPresent()) {
            player.sendMessage(text("[公会] 你已经加入了公会，不能再接受其他邀请。", NamedTextColor.RED));
            open(player);
            return;
        }

        List<GuildInvite> invites = db.loadInvites(player.getUniqueId());
        int pageSize = ROLE_SLOTS.length;
        int currentPage = currentPage(player, RECEIVED_INVITES_PAGE_ID);
        int totalPages = Math.max(1, (invites.size() + pageSize - 1) / pageSize);
        if (rawSlot == FIRST_PAGE_SLOT && currentPage > 0) {
            openReceivedInvites(player, 0);
            return;
        }
        if (rawSlot == PREVIOUS_PAGE_SLOT && currentPage > 0) {
            openReceivedInvites(player, currentPage - 1);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT && currentPage + 1 < totalPages) {
            openReceivedInvites(player, currentPage + 1);
            return;
        }
        if (rawSlot == LAST_PAGE_SLOT && currentPage + 1 < totalPages) {
            openReceivedInvites(player, totalPages - 1);
            return;
        }

        int slotIndex = indexOf(ROLE_SLOTS, rawSlot);
        if (slotIndex < 0) {
            return;
        }
        int inviteIndex = currentPage * pageSize + slotIndex;
        if (inviteIndex < 0 || inviteIndex >= invites.size()) {
            return;
        }

        GuildInvite invite = invites.get(inviteIndex);
        Optional<Guild> guild = db.findGuildById(invite.guildId());
        if (guild.isEmpty() || !db.consumeInvite(player.getUniqueId(), invite.guildId())) {
            player.sendMessage(text("[公会] 这个邀请已经失效。", NamedTextColor.RED));
            openReceivedInvites(player, currentPage);
            return;
        }
        if (!db.addMember(guild.get(), player)) {
            player.sendMessage(text("[公会] 加入失败，你可能已经加入了其他公会。", NamedTextColor.RED));
            open(player);
            return;
        }

        syncLuckPermsUserToGuild(player, guild.get());
        player.sendMessage(text("[公会] 已加入 " + stripRgbCodes(guild.get().name()) + "。", NamedTextColor.GREEN));
        Player inviter = Bukkit.getPlayer(invite.inviterUuid());
        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(text("[公会] " + player.getName() + " 已接受你的邀请。", NamedTextColor.GREEN));
        }
        open(player, 0);
    }

    public static void openGuildBank(@NotNull Player player) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), BANK_PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, COMPACT_MENU_SIZE, Component.text("公会银行", NamedTextColor.WHITE));
        holder.setInventory(inventory);
        fillCompactBank(inventory);

        long bankBalance = db.guildBankBalance(guild.get().id());
        long walletBalance = walletBalance(player);
        inventory.setItem(BANK_SUMMARY_SLOT, InfoGuiItems.createCustomHead(
                BANK_TEXTURE,
                nameText("公会银行"),
                List.of(
                        valueLine("公会: ", formatGuildName(guild.get().name())),
                        valueLine("身上资金: ", formatNumber(walletBalance) + " 硬币"),
                        valueLine("公会银行: ", formatNumber(bankBalance) + " 硬币")
                )
        ));
        inventory.setItem(BANK_DEPOSIT_SLOT, InfoGuiItems.createNamedItem(
                Material.CHEST,
                nameText("存入公会银行"),
                List.of(
                        bodyText("把身上的硬币转入公会银行。"),
                        valueLine("身上资金: ", formatNumber(walletBalance) + " 硬币"),
                        Component.empty(),
                        actionText("点击选择存入金额")
                )
        ));
        inventory.setItem(BANK_WITHDRAW_SLOT, InfoGuiItems.createNamedItem(
                Material.EMERALD,
                nameText("从公会银行取出"),
                List.of(
                        bodyText("把公会银行余额取回到身上。"),
                        valueLine("公会银行: ", formatNumber(bankBalance) + " 硬币"),
                        Component.empty(),
                        actionText("点击选择取出金额")
                )
        ));
        inventory.setItem(BANK_LOG_SLOT, InfoGuiItems.createNamedItem(
                Material.WRITABLE_BOOK,
                nameText("公会银行日志"),
                List.of(bodyText("查看最近的公会银行存取记录。"), Component.empty(), actionText("点击查看"))
        ));
        inventory.setItem(COMPACT_BACK_SLOT, backItem("返回公会主页"));
        inventory.setItem(COMPACT_CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    public static void handleGuildBankClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == COMPACT_BACK_SLOT) {
            open(player);
            return;
        }
        if (rawSlot == COMPACT_CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (rawSlot == BANK_LOG_SLOT) {
            openGuildBankLog(player, 0);
            return;
        }
        if (rawSlot == BANK_DEPOSIT_SLOT) {
            openGuildBankAction(player, true);
            return;
        }
        if (rawSlot == BANK_WITHDRAW_SLOT) {
            openGuildBankAction(player, false);
        }
    }

    public static void openGuildBankAction(@NotNull Player player, boolean deposit) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        long bankBalance = db.guildBankBalance(guild.get().id());
        long walletBalance = walletBalance(player);
        long allAmount = deposit ? walletBalance : bankBalance;
        long halfAmount = allAmount / 2L;

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), deposit ? BANK_DEPOSIT_PAGE_ID : BANK_WITHDRAW_PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, COMPACT_MENU_SIZE,
                Component.text(deposit ? "公会银行 - 存入" : "公会银行 - 取出", NamedTextColor.WHITE));
        holder.setInventory(inventory);
        fillCompactBank(inventory);

        inventory.setItem(BANK_SUMMARY_SLOT, InfoGuiItems.createNamedItem(
                deposit ? Material.CHEST : Material.EMERALD,
                nameText(deposit ? "存入公会银行" : "从公会银行取出"),
                List.of(
                        valueLine("身上资金: ", formatNumber(walletBalance) + " 硬币"),
                        valueLine("公会银行: ", formatNumber(bankBalance) + " 硬币")
                )
        ));
        inventory.setItem(BANK_ALL_SLOT, bankActionItem(deposit ? Material.HOPPER : Material.DROPPER,
                deposit ? "全部存入" : "全部取出", allAmount));
        inventory.setItem(BANK_HALF_SLOT, bankActionItem(Material.CHEST_MINECART,
                deposit ? "存入一半" : "取出一半", halfAmount));
        inventory.setItem(BANK_CUSTOM_SLOT, InfoGuiItems.createNamedItem(
                Material.OAK_SIGN,
                nameText(deposit ? "输入存入金额" : "输入取出金额"),
                List.of(
                        bodyText("打开告示牌输入自定义金额。"),
                        bodyText("支持纯数字或 1k / 1m / 1b。"),
                        Component.empty(),
                        actionText("点击输入")
                )
        ));
        inventory.setItem(COMPACT_BACK_SLOT, backItem("返回公会银行"));
        inventory.setItem(COMPACT_CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    public static void handleGuildBankDepositClick(@NotNull Player player, int rawSlot) {
        handleGuildBankActionClick(player, rawSlot, true);
    }

    public static void handleGuildBankWithdrawClick(@NotNull Player player, int rawSlot) {
        handleGuildBankActionClick(player, rawSlot, false);
    }

    private static void handleGuildBankActionClick(@NotNull Player player, int rawSlot, boolean deposit) {
        if (rawSlot == COMPACT_BACK_SLOT) {
            openGuildBank(player);
            return;
        }
        if (rawSlot == COMPACT_CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        long allAmount = deposit ? walletBalance(player) : db.guildBankBalance(guild.get().id());
        if (rawSlot == BANK_ALL_SLOT) {
            if (deposit) {
                performGuildDeposit(player, allAmount);
            } else {
                performGuildWithdraw(player, allAmount);
            }
            openGuildBankAction(player, deposit);
            return;
        }
        if (rawSlot == BANK_HALF_SLOT) {
            if (deposit) {
                performGuildDeposit(player, allAmount / 2L);
            } else {
                performGuildWithdraw(player, allAmount / 2L);
            }
            openGuildBankAction(player, deposit);
            return;
        }
        if (rawSlot == BANK_CUSTOM_SLOT) {
            openGuildBankAmountSign(player, deposit);
        }
    }

    public static void openGuildBankLog(@NotNull Player player, int page) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), BANK_LOG_PAGE_ID, Math.max(0, page));
        Inventory inventory = Bukkit.createInventory(holder, COMPACT_MENU_SIZE, Component.text("公会银行日志", NamedTextColor.WHITE));
        holder.setInventory(inventory);
        fillCompactBank(inventory);

        List<GuildBankLogEntry> logs = db.loadGuildBankLogs(guild.get().id(), Math.max(0, page) * 14, 14);
        inventory.setItem(4, InfoGuiItems.createNamedItem(
                Material.WRITABLE_BOOK,
                nameText("公会银行日志"),
                List.of(valueLine("页面: ", String.valueOf(page + 1)))
        ));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int index = 0; index < logs.size() && index < slots.length; index++) {
            GuildBankLogEntry log = logs.get(index);
            inventory.setItem(slots[index], InfoGuiItems.createNamedItem(
                    "DEPOSIT".equals(log.action()) ? Material.LIME_DYE : Material.RED_DYE,
                    nameText(log.actorName() + " " + bankActionName(log.action())),
                    List.of(
                            valueLine("金额: ", formatNumber(log.amount()) + " 硬币"),
                            valueLine("操作后余额: ", formatNumber(log.balanceAfter()) + " 硬币"),
                            valueLine("时间: ", TIME_FORMAT.format(log.createdAt()))
                    )
            ));
        }
        inventory.setItem(COMPACT_BACK_SLOT, backItem("返回公会银行"));
        if (page > 0) {
            inventory.setItem(COMPACT_PREVIOUS_SLOT, InfoGuiItems.createNamedItem(Material.ARROW, nameText("上一页")));
        }
        if (logs.size() >= 14) {
            inventory.setItem(COMPACT_NEXT_SLOT, InfoGuiItems.createNamedItem(Material.ARROW, nameText("下一页")));
        }
        inventory.setItem(COMPACT_CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    public static void handleGuildBankLogClick(@NotNull Player player, int rawSlot) {
        int page = currentPage(player, BANK_LOG_PAGE_ID);
        if (rawSlot == COMPACT_BACK_SLOT) {
            openGuildBank(player);
        } else if (rawSlot == COMPACT_CLOSE_SLOT) {
            player.closeInventory();
        } else if (rawSlot == COMPACT_PREVIOUS_SLOT && page > 0) {
            openGuildBankLog(player, page - 1);
        } else if (rawSlot == COMPACT_NEXT_SLOT) {
            openGuildBankLog(player, page + 1);
        }
    }

    public static boolean handleSignInput(@NotNull Player player, @NotNull List<Component> lines) {
        PendingBankInput pending = PENDING_BANK_INPUTS.remove(player.getUniqueId());
        if (pending == null) {
            return false;
        }
        cleanupVirtualSign(player, pending);
        OptionalLong amount = parseAmount(lines);
        if (amount.isEmpty()) {
            player.sendMessage(text("[公会银行] 金额格式错误，请输入数字，或 1k / 1m / 1b。", NamedTextColor.RED));
            openGuildBankAction(player, pending.deposit());
            return true;
        }
        if (pending.deposit()) {
            performGuildDeposit(player, amount.getAsLong());
        } else {
            performGuildWithdraw(player, amount.getAsLong());
        }
        openGuildBankAction(player, pending.deposit());
        return true;
    }

    public static void openSettings(@NotNull Player player) {
        Optional<Guild> guild = currentGuild(player);
        if (guild.isEmpty()) {
            open(player);
            return;
        }
        if (!canOpenGuildSettings(player)) {
            player.sendMessage(text("[公会] 你没有权限打开公会设定。", NamedTextColor.RED));
            open(player);
            return;
        }
        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), SETTINGS_PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, SETTINGS_TITLE);
        holder.setInventory(inventory);
        fillPanel(inventory);

        inventory.setItem(4, InfoGuiItems.createNamedItem(
                Material.REDSTONE_TORCH,
                nameText("公会设定"),
                List.of(
                        valueLine("公会: ", formatGuildName(guild.get().name())),
                        valueLine("自由加入: ", guild.get().freeJoinEnabled() ? "开启" : "关闭")
                )
        ));
        inventory.setItem(FREE_JOIN_SLOT, InfoGuiItems.createNamedItem(
                guild.get().freeJoinEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                nameText("玩家自由加入"),
                List.of(
                        bodyText("开启后，无公会玩家可以从公会列表直接加入。"),
                        valueLine("当前状态: ", guild.get().freeJoinEnabled() ? "开启" : "关闭"),
                        Component.empty(),
                        actionText("点击切换")
                )
        ));
        inventory.setItem(ROLE_SETTINGS_SLOT, InfoGuiItems.createNamedItem(
                Material.NAME_TAG,
                nameText("身份组设定"),
                List.of(
                        bodyText("创建身份组，并调整权限开关。"),
                        bodyText("会长固定拥有全部权限，成员默认没有权限。"),
                        Component.empty(),
                        actionText("点击进入身份组设定")
                )
        ));
        inventory.setItem(TRANSFER_OWNER_SLOT, InfoGuiItems.createNamedItem(
                Material.PLAYER_HEAD,
                nameText("转移公会"),
                List.of(
                        bodyText("把会长转移给本公会的其他成员。"),
                        bodyText("原会长会变成公会默认身份组。"),
                        Component.empty(),
                        actionText("点击选择成员")
                )
        ));
        inventory.setItem(DISBAND_GUILD_SLOT, InfoGuiItems.createNamedItem(
                Material.TNT,
                nameText("解散公会"),
                List.of(
                        bodyText("永久删除当前公会和所有公会数据。"),
                        bodyText("只有会长可以执行。"),
                        Component.empty(),
                        actionText("点击解散公会")
                )
        ));
        inventory.setItem(BACK_SLOT, backItem("返回公会主页"));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    public static void handleSettingsClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == FREE_JOIN_SLOT) {
            toggleFreeJoin(player);
            return;
        }
        if (rawSlot == DISBAND_GUILD_SLOT) {
            disbandGuild(player);
            return;
        }
        if (rawSlot == TRANSFER_OWNER_SLOT) {
            openTransferOwner(player, 0);
            return;
        }
        if (rawSlot == ROLE_SETTINGS_SLOT) {
            openRoles(player, 0);
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

    public static void openTransferOwner(@NotNull Player player, int page) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        if (!guild.get().ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage(text("[公会] 只有会长可以转移公会。", NamedTextColor.RED));
            openSettings(player);
            return;
        }

        int totalMembers = db.countTransferCandidates(guild.get().id(), player.getUniqueId());
        int totalPages = Math.max(1, (totalMembers + ROLE_SLOTS.length - 1) / ROLE_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        List<GuildMember> members = db.loadTransferCandidates(guild.get().id(), player.getUniqueId(),
                safePage * ROLE_SLOTS.length, ROLE_SLOTS.length);

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), TRANSFER_OWNER_PAGE_ID, safePage);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.text("转移公会", NamedTextColor.WHITE));
        holder.setInventory(inventory);
        fillPanel(inventory);

        inventory.setItem(4, InfoGuiItems.createNamedItem(
                Material.PLAYER_HEAD,
                nameText("转移公会"),
                List.of(
                        valueLine("公会: ", formatGuildName(guild.get().name())),
                        valueLine("可转移成员: ", String.valueOf(totalMembers)),
                        bodyText("点击成员头颅后会立即转移会长。")
                )
        ));
        if (members.isEmpty()) {
            inventory.setItem(22, InfoGuiItems.createNamedItem(
                    Material.PAPER,
                    nameText("暂无可转移成员"),
                    List.of(bodyText("当前公会没有其他成员可以接任会长。"))
            ));
        } else {
            for (int index = 0; index < members.size() && index < ROLE_SLOTS.length; index++) {
                GuildMember member = members.get(index);
                inventory.setItem(ROLE_SLOTS[index], transferCandidateItem(member));
            }
        }
        renderNavigation(inventory, safePage, totalPages);
        inventory.setItem(BACK_SLOT, backItem("返回公会设定"));
        player.openInventory(inventory);
    }

    public static void handleTransferOwnerClick(@NotNull Player player, int rawSlot) {
        int page = currentPage(player, TRANSFER_OWNER_PAGE_ID);
        if (rawSlot == BACK_SLOT) {
            openSettings(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        if (!guild.get().ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage(text("[公会] 只有会长可以转移公会。", NamedTextColor.RED));
            openSettings(player);
            return;
        }

        handleTransferNavigation(player, rawSlot, page, guild.get());
        int index = indexOf(ROLE_SLOTS, rawSlot);
        if (index < 0) {
            return;
        }

        List<GuildMember> members = db.loadTransferCandidates(guild.get().id(), player.getUniqueId(),
                page * ROLE_SLOTS.length, ROLE_SLOTS.length);
        if (index >= members.size()) {
            return;
        }

        GuildMember target = members.get(index);
        if (!db.transferOwnership(guild.get(), player, target.uuid(), target.playerName())) {
            player.sendMessage(text("[公会] 转移失败，请确认目标仍在公会内。", NamedTextColor.RED));
            openTransferOwner(player, page);
            return;
        }

        player.sendMessage(text("[公会] 已将会长转移给 " + target.playerName() + "。", NamedTextColor.GREEN));
        Player targetPlayer = Bukkit.getPlayer(target.uuid());
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(text("[公会] 你已成为 " + stripRgbCodes(guild.get().name()) + " 的会长。", NamedTextColor.GREEN));
        }
        open(player, 0);
    }

    public static void openRoles(@NotNull Player player, int page) {
        GuildDataDB db = guildDb();
        UndergroundState plugin = UndergroundState.getInstance();
        if (db == null || plugin == null) {
            open(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<Guild> guild = db.findGuildByMember(playerId);
            boolean allowed = guild.isPresent() && hasAnyGuildPermission(db, playerId,
                    "guild.role.create",
                    "guild.role.default.set",
                    "guild.role.permissions.edit");
            List<GuildRole> roles = allowed ? db.loadRoles(guild.get().id()) : List.of();
            runForOnlinePlayer(plugin, playerId, current -> {
                if (guild.isEmpty()) {
                    open(current);
                    return;
                }
                if (!allowed) {
                    current.sendMessage(text("[公会] 你没有权限打开身份组管理。", NamedTextColor.RED));
                    openSettings(current);
                    return;
                }
                renderRoles(current, page, guild.get(), roles);
            });
        });
    }

    private static void renderRoles(@NotNull Player player,
                                    int page,
                                    @NotNull Guild guild,
                                    @NotNull List<GuildRole> roles) {
        int totalPages = Math.max(1, (roles.size() + ROLE_SLOTS.length - 1) / ROLE_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), ROLES_PAGE_ID, safePage);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, ROLES_TITLE);
        holder.setInventory(inventory);
        fillPanel(inventory);

        inventory.setItem(4, InfoGuiItems.createNamedItem(
                Material.NAME_TAG,
                nameText("身份组设定"),
                List.of(
                        valueLine("公会: ", formatGuildName(guild.name())),
                        valueLine("身份组数量: ", String.valueOf(roles.size())),
                        bodyText("点击身份组进入权限编辑。")
                )
        ));
        inventory.setItem(ROLE_CREATE_SLOT, InfoGuiItems.createNamedItem(
                Material.LIME_DYE,
                nameText("创建身份组"),
                List.of(
                        bodyText("打开铁砧输入新的身份组名称。"),
                        Component.empty(),
                        actionText("点击创建")
                )
        ));

        int offset = safePage * ROLE_SLOTS.length;
        for (int index = 0; index < ROLE_SLOTS.length && offset + index < roles.size(); index++) {
            inventory.setItem(ROLE_SLOTS[index], roleItem(roles.get(offset + index)));
        }
        renderNavigation(inventory, safePage, totalPages);
        inventory.setItem(BACK_SLOT, backItem("返回公会设定"));
        player.openInventory(inventory);
    }

    public static void handleRolesClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == ROLE_CREATE_SLOT) {
            openRoleCreateAnvil(player);
            return;
        }
        if (rawSlot == BACK_SLOT) {
            openSettings(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        int page = currentPage(player, ROLES_PAGE_ID);
        if (rawSlot == FIRST_PAGE_SLOT && page > 0) {
            openRoles(player, 0);
            return;
        }
        if (rawSlot == PREVIOUS_PAGE_SLOT && page > 0) {
            openRoles(player, page - 1);
            return;
        }
        if (rawSlot == NEXT_PAGE_SLOT) {
            openRoles(player, page + 1);
            return;
        }
        if (rawSlot == LAST_PAGE_SLOT) {
            openRoles(player, Integer.MAX_VALUE);
            return;
        }
        int slotIndex = indexOf(ROLE_SLOTS, rawSlot);
        if (slotIndex < 0) {
            return;
        }

        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            return;
        }
        List<GuildRole> roles = db.loadRoles(guild.get().id());
        int roleIndex = page * ROLE_SLOTS.length + slotIndex;
        if (roleIndex < 0 || roleIndex >= roles.size()) {
            return;
        }
        if (!canEditRoleEntries(player)) {
            player.sendMessage(text("[公会] 你没有权限进入这个编辑页。", NamedTextColor.RED));
            openRoles(player, page);
            return;
        }
        EDITING_ROLE.put(player.getUniqueId(), roles.get(roleIndex).id());
        openRoleEdit(player);
    }

    public static void openRoleEdit(@NotNull Player player) {
        GuildDataDB db = guildDb();
        Long roleId = EDITING_ROLE.get(player.getUniqueId());
        Optional<GuildRole> role = db == null || roleId == null ? Optional.empty() : db.loadRole(roleId);
        if (role.isEmpty()) {
            openRoles(player, 0);
            return;
        }
        if (!canEditRoleEntries(player)) {
            player.sendMessage(text("[公会] 你没有权限编辑这个身份组。", NamedTextColor.RED));
            openSettings(player);
            return;
        }

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), ROLE_EDIT_PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, ROLE_EDIT_TITLE);
        holder.setInventory(inventory);
        fillPanel(inventory);

        GuildRole editing = role.get();
        boolean ownerRole = "OWNER".equals(editing.systemRole());
        inventory.setItem(4, InfoGuiItems.createNamedItem(
                ownerRole ? Material.NETHER_STAR : Material.NAME_TAG,
                nameText(editing.name()),
                List.of(
                        valueLine("类型: ", roleTypeName(editing)),
                        valueLine("默认身份组: ", editing.defaultRole() ? "是" : "否"),
                        bodyText(ownerRole ? "会长身份组固定拥有全部权限。" : "点击染料开关权限。")
                )
        ));
        if (!ownerRole) {
            inventory.setItem(SET_DEFAULT_ROLE_SLOT, InfoGuiItems.createNamedItem(
                    editing.defaultRole() ? Material.LIME_DYE : Material.GRAY_DYE,
                    nameText("设为默认身份组"),
                    List.of(
                            bodyText("新玩家加入公会后会进入默认身份组。"),
                            valueLine("当前状态: ", editing.defaultRole() ? "已是默认" : "不是默认"),
                            Component.empty(),
                            actionText("点击切换")
                    )
            ));
        }

        List<String> categories = permissionCategories();
        for (int index = 0; index < categories.size() && index < CATEGORY_SLOTS.length; index++) {
            String category = categories.get(index);
            inventory.setItem(CATEGORY_SLOTS[index], permissionCategoryItem(category, ownerRole));
        }
        inventory.setItem(BACK_SLOT, backItem("返回身份组设定"));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    public static void handleRoleEditClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == BACK_SLOT) {
            openRoles(player, 0);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        GuildDataDB db = guildDb();
        Long roleId = EDITING_ROLE.get(player.getUniqueId());
        Optional<GuildRole> role = db == null || roleId == null ? Optional.empty() : db.loadRole(roleId);
        if (role.isEmpty()) {
            openRoles(player, 0);
            return;
        }
        GuildRole editing = role.get();
        if (!canEditRoleEntries(player)) {
            player.sendMessage(text("[公会] 你没有权限编辑这个身份组。", NamedTextColor.RED));
            openSettings(player);
            return;
        }
        if (rawSlot == SET_DEFAULT_ROLE_SLOT && !"OWNER".equals(editing.systemRole())) {
            if (db.setDefaultRole(editing.guildId(), editing.id())) {
                player.sendMessage(text("[公会] 已更新默认身份组。", NamedTextColor.GREEN));
            }
            openRoleEdit(player);
            return;
        }

        int categoryIndex = indexOf(CATEGORY_SLOTS, rawSlot);
        List<String> categories = permissionCategories();
        if (categoryIndex >= 0 && categoryIndex < categories.size()) {
            if (!canEditRolePermissions(player)) {
                player.sendMessage(text("[公会] 你没有权限编辑权限。", NamedTextColor.RED));
                openRoleEdit(player);
                return;
            }
            EDITING_PERMISSION_CATEGORY.put(player.getUniqueId(), categories.get(categoryIndex));
            openRolePermissionCategory(player);
        }
    }

    public static void openRolePermissionCategory(@NotNull Player player) {
        GuildDataDB db = guildDb();
        Long roleId = EDITING_ROLE.get(player.getUniqueId());
        String category = EDITING_PERMISSION_CATEGORY.get(player.getUniqueId());
        Optional<GuildRole> role = db == null || roleId == null ? Optional.empty() : db.loadRole(roleId);
        if (role.isEmpty() || category == null) {
            openRoleEdit(player);
            return;
        }
        if (!canEditRolePermissions(player)) {
            player.sendMessage(text("[公会] 你没有权限编辑权限。", NamedTextColor.RED));
            openRoleEdit(player);
            return;
        }

        InfoGuiHolder holder = new InfoGuiHolder(player.getUniqueId(), ROLE_PERMISSION_CATEGORY_PAGE_ID);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.text(category + "权限", NamedTextColor.WHITE));
        holder.setInventory(inventory);
        fillPanel(inventory);

        GuildRole editing = role.get();
        boolean ownerRole = "OWNER".equals(editing.systemRole());
        inventory.setItem(4, InfoGuiItems.createNamedItem(
                ownerRole ? Material.NETHER_STAR : Material.NAME_TAG,
                nameText(category + "权限"),
                List.of(
                        valueLine("身份组: ", editing.name()),
                        valueLine("类型: ", roleTypeName(editing)),
                        bodyText(ownerRole ? "会长身份组固定拥有全部权限。" : "点击染料开关权限。")
                )
        ));

        Set<String> enabledPermissions = db == null ? Set.of() : db.loadEnabledPermissions(editing);
        List<PermissionDef> permissions = permissionsByCategory(category);
        for (int index = 0; index < permissions.size() && index < ROLE_SLOTS.length; index++) {
            PermissionDef permission = permissions.get(index);
            boolean enabled = ownerRole || enabledPermissions.contains(permission.id()) || enabledPermissions.contains("*");
            inventory.setItem(ROLE_SLOTS[index], permissionItem(permission, enabled, ownerRole));
        }
        inventory.setItem(BACK_SLOT, backItem("返回权限分类"));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        player.openInventory(inventory);
    }

    public static void handleRolePermissionCategoryClick(@NotNull Player player, int rawSlot) {
        if (rawSlot == BACK_SLOT) {
            openRoleEdit(player);
            return;
        }
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        GuildDataDB db = guildDb();
        Long roleId = EDITING_ROLE.get(player.getUniqueId());
        String category = EDITING_PERMISSION_CATEGORY.get(player.getUniqueId());
        Optional<GuildRole> role = db == null || roleId == null ? Optional.empty() : db.loadRole(roleId);
        if (role.isEmpty() || category == null || db == null) {
            openRoleEdit(player);
            return;
        }
        if (!canEditRolePermissions(player)) {
            player.sendMessage(text("[公会] 你没有权限编辑权限。", NamedTextColor.RED));
            openRoleEdit(player);
            return;
        }
        GuildRole editing = role.get();
        int slotIndex = indexOf(ROLE_SLOTS, rawSlot);
        List<PermissionDef> permissions = permissionsByCategory(category);
        if (slotIndex >= 0 && slotIndex < permissions.size()) {
            PermissionDef permission = permissions.get(slotIndex);
            if ("OWNER".equals(editing.systemRole())) {
                player.sendMessage(text("[公会] 会长身份组固定拥有全部权限，不能关闭。", NamedTextColor.RED));
                return;
            }
            boolean nowEnabled = !db.hasPermission(editing, permission.id());
            db.setPermission(editing, permission.id(), nowEnabled);
            openRolePermissionCategory(player);
        }
    }

    public static void openCreateAnvil(@NotNull Player player) {
        openAnvil(player, CREATE_PAGE_ID, CREATE_TITLE, defaultGuildName(player), "输入公会名字", "取出右侧纸张创建公会");
    }

    public static void openRoleCreateAnvil(@NotNull Player player) {
        if (!canCreateRole(player)) {
            player.sendMessage(text("[公会] 你没有权限创建身份组。", NamedTextColor.RED));
            openRoles(player, 0);
            return;
        }
        openAnvil(player, ROLE_CREATE_PAGE_ID, ROLE_CREATE_TITLE, "新身份组", "输入身份组名字", "取出右侧纸张创建身份组");
    }

    public static void openMemberDiscordAnvil(@NotNull Player player) {
        player.sendMessage(text("[公会] Discord 资料暂未接入独立数据库，暂时不能手动编辑。", NamedTextColor.YELLOW));
        openMemberProfile(player, player.getUniqueId(), VIEWING_MEMBER_BACK_PAGE.getOrDefault(player.getUniqueId(), 0));
    }

    private static void openAnvil(@NotNull Player player,
                                  @NotNull String pageId,
                                  @NotNull Component title,
                                  @NotNull String defaultName,
                                  @NotNull String prompt,
                                  @NotNull String confirmText) {
        InventoryView view = player.openAnvil(null, true);
        if (view == null) {
            log("Open real anvil failed page=" + pageId + " player=" + player.getName());
            player.sendMessage(text("[公会] 无法打开铁砧输入界面。", NamedTextColor.RED));
            return;
        }
        ANVIL_PAGES.put(player.getUniqueId(), pageId);
        ANVIL_INPUT_NAMES.remove(player.getUniqueId());
        log("Open anvil page=" + pageId + " player=" + player.getName() + " defaultName=\"" + defaultName + "\"");
        view.getTopInventory().setItem(ANVIL_INPUT_SLOT, InfoGuiItems.createNamedItem(
                Material.PAPER,
                formatGuildName(defaultName),
                List.of(bodyText(prompt), bodyText("支持 RGB 格式: &#1492ed文字"), Component.empty(), actionText(confirmText))
        ));
        if (view instanceof AnvilView anvilView) {
            anvilView.setRepairCost(0);
        }
    }

    public static void handlePrepareAnvil(@NotNull PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)
                || !(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        String pageId = ANVIL_PAGES.get(player.getUniqueId());
        if (!CREATE_PAGE_ID.equals(pageId)
                && !ROLE_CREATE_PAGE_ID.equals(pageId)
                && !MEMBER_DISCORD_EDIT_PAGE_ID.equals(pageId)) {
            return;
        }
        String renameText = normalizeName(anvilView.getRenameText());
        ANVIL_INPUT_NAMES.put(player.getUniqueId(), renameText);
        log("Prepare anvil page=" + pageId + " player=" + player.getName()
                + " renameText=\"" + renameText + "\"");
        anvilView.setRepairCost(0);
        event.setResult(createAnvilResultPaper(pageId, renameText));
    }

    public static boolean isAnvilSession(@NotNull Player player) {
        return ANVIL_PAGES.containsKey(player.getUniqueId());
    }

    public static void handleAnvilSessionClick(@NotNull InventoryClickEvent event, @NotNull Player player) {
        String pageId = ANVIL_PAGES.get(player.getUniqueId());
        if (CREATE_PAGE_ID.equals(pageId)) {
            handleCreateAnvilClick(event, player);
        } else if (ROLE_CREATE_PAGE_ID.equals(pageId)) {
            handleRoleCreateAnvilClick(event, player);
        } else if (MEMBER_DISCORD_EDIT_PAGE_ID.equals(pageId)) {
            handleMemberDiscordAnvilClick(event, player);
        } else {
            event.setCancelled(true);
        }
    }

    public static void clearAnvilSession(@NotNull Player player) {
        ANVIL_PAGES.remove(player.getUniqueId());
        ANVIL_INPUT_NAMES.remove(player.getUniqueId());
    }

    public static void handleCreateAnvilClick(@NotNull InventoryClickEvent event, @NotNull Player player) {
        handleAnvilClick(event, player, CREATE_PAGE_ID);
    }

    public static void handleRoleCreateAnvilClick(@NotNull InventoryClickEvent event, @NotNull Player player) {
        handleAnvilClick(event, player, ROLE_CREATE_PAGE_ID);
    }

    public static void handleMemberDiscordAnvilClick(@NotNull InventoryClickEvent event, @NotNull Player player) {
        handleAnvilClick(event, player, MEMBER_DISCORD_EDIT_PAGE_ID);
    }

    private static void handleAnvilClick(@NotNull InventoryClickEvent event, @NotNull Player player, @NotNull String pageId) {
        event.setCancelled(true);
        if (event.getRawSlot() != ANVIL_RESULT_SLOT || !(event.getView() instanceof AnvilView anvilView)) {
            log("Anvil click ignored page=" + pageId + " player=" + player.getName()
                    + " rawSlot=" + event.getRawSlot()
                    + " isAnvilView=" + (event.getView() instanceof AnvilView));
            return;
        }
        String rawName = ANVIL_INPUT_NAMES.get(player.getUniqueId());
        if (rawName == null || rawName.isBlank()) {
            rawName = rawNameFromResult(event.getCurrentItem(), anvilView.getRenameText());
        }
        log("Anvil confirm page=" + pageId + " player=" + player.getName()
                + " cached=\"" + ANVIL_INPUT_NAMES.get(player.getUniqueId()) + "\""
                + " viewRename=\"" + normalizeName(anvilView.getRenameText()) + "\""
                + " resultName=\"" + rawName + "\""
                + " item=" + formatItem(event.getCurrentItem()));
        String validationError = MEMBER_DISCORD_EDIT_PAGE_ID.equals(pageId)
                ? validateDiscord(rawName)
                : validateName(rawName, pageId);
        if (validationError != null) {
            log("Anvil validation failed page=" + pageId + " player=" + player.getName()
                    + " rawName=\"" + rawName + "\" reason=\"" + validationError + "\"");
            player.sendMessage(text("[公会] " + validationError, NamedTextColor.RED));
            if (CREATE_PAGE_ID.equals(pageId)) {
                openCreateAnvil(player);
            } else if (MEMBER_DISCORD_EDIT_PAGE_ID.equals(pageId)) {
                openMemberDiscordAnvil(player);
            } else {
                openRoleCreateAnvil(player);
            }
            return;
        }
        if (CREATE_PAGE_ID.equals(pageId)) {
            createGuild(player, rawName);
        } else if (MEMBER_DISCORD_EDIT_PAGE_ID.equals(pageId)) {
            saveMemberDiscord(player, rawName);
        } else {
            createRole(player, rawName);
        }
        ANVIL_INPUT_NAMES.remove(player.getUniqueId());
        ANVIL_PAGES.remove(player.getUniqueId());
    }

    private static void createGuild(@NotNull Player player, @NotNull String guildName) {
        GuildDataDB db = guildDb();
        if (db == null) {
            log("Create guild failed before DB: database unavailable player=" + player.getName()
                    + " guildName=\"" + guildName + "\"");
            player.sendMessage(text("[公会] 数据库暂时不可用，无法创建公会。", NamedTextColor.RED));
            return;
        }
        CreateGuildResult result = db.createGuild(player, guildName);
        log("Create guild result player=" + player.getName() + " uuid=" + player.getUniqueId()
                + " guildName=\"" + guildName + "\" result=" + result);
        switch (result) {
            case SUCCESS -> {
                syncLuckPermsGuildGroup(player, guildName);
                player.sendMessage(text("[公会] 创建成功，你已成为公会会长。", NamedTextColor.GREEN));
            }
            case ALREADY_IN_GUILD -> player.sendMessage(text("[公会] 你已经加入了公会。", NamedTextColor.RED));
            case INVALID_NAME -> player.sendMessage(text("[公会] 公会名字无效，请重新输入。", NamedTextColor.RED));
            case STORAGE_ERROR -> player.sendMessage(text("[公会] 数据保存失败，请稍后再试。", NamedTextColor.RED));
        }
        open(player, 0);
    }

    private static void saveMemberDiscord(@NotNull Player player, @NotNull String rawDiscord) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        String discord = "-".equals(rawDiscord.trim()) ? null : rawDiscord.trim();
        if (!db.setMemberDiscord(guild.get().id(), player.getUniqueId(), discord)) {
            player.sendMessage(text("[公会] Discord 保存失败，请稍后再试。", NamedTextColor.RED));
            openMemberProfile(player, player.getUniqueId(), VIEWING_MEMBER_BACK_PAGE.getOrDefault(player.getUniqueId(), 0));
            return;
        }
        player.sendMessage(text("[公会] 已更新你的 Discord 资讯。", NamedTextColor.GREEN));
        openMemberProfile(player, player.getUniqueId(), VIEWING_MEMBER_BACK_PAGE.getOrDefault(player.getUniqueId(), 0));
    }

    private static void syncLuckPermsGuildGroup(@NotNull Player player, @NotNull String guildName) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        Optional<Guild> guild = currentGuild(player);
        if (guild.isEmpty()) {
            log("LuckPerms sync skipped: created guild not found for player=" + player.getName());
            return;
        }

        String groupName = "guild_" + guild.get().id();
        String prefix = guildName + " &r";
        dispatchConsoleCommand("lp creategroup " + groupName);
        dispatchConsoleCommand("lp group " + groupName + " parent add default");
        dispatchConsoleCommand("lp group " + groupName + " setweight 1000");
        dispatchConsoleCommand("lp group " + groupName + " meta setprefix 1000 \"" + quoteCommandArgument(prefix) + "\"");
        dispatchConsoleCommand("lp user " + player.getName() + " parent add " + groupName);
    }

    private static void syncLuckPermsUserToGuild(@NotNull Player player, @NotNull Guild guild) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        dispatchConsoleCommand("lp user " + player.getName() + " parent add guild_" + guild.id());
    }

    private static void syncLuckPermsUserLeaveGuild(@NotNull Player player, @NotNull Guild guild) {
        syncLuckPermsUserLeaveGuild(player.getName(), guild);
    }

    private static void syncLuckPermsUserLeaveGuild(@NotNull String playerName, @NotNull Guild guild) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        dispatchConsoleCommand("lp user " + playerName + " parent remove guild_" + guild.id());
    }

    private static void syncLuckPermsDisbandGuild(@NotNull Guild guild, @NotNull List<GuildMember> members) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        String groupName = "guild_" + guild.id();
        for (GuildMember member : members) {
            dispatchConsoleCommand("lp user " + member.playerName() + " parent remove " + groupName);
        }
        dispatchConsoleCommand("lp deletegroup " + groupName);
    }

    private static void toggleFreeJoin(@NotNull Player player) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        if (!hasGuildPermission(player, "guild.info.settings.edit")) {
            player.sendMessage(text("[公会] 你没有权限修改基础设定。", NamedTextColor.RED));
            openSettings(player);
            return;
        }

        boolean enabled = !guild.get().freeJoinEnabled();
        if (!db.setFreeJoinEnabled(guild.get(), enabled)) {
            player.sendMessage(text("[公会] 更新自由加入设定失败。", NamedTextColor.RED));
            openSettings(player);
            return;
        }
        player.sendMessage(text("[公会] 玩家自由加入已" + (enabled ? "开启" : "关闭") + "。", NamedTextColor.GREEN));
        openSettings(player);
    }

    private static void joinFreeGuildFromMemberSlot(@NotNull Player player, int page, int memberIndex) {
        GuildDataDB db = guildDb();
        if (db == null) {
            player.sendMessage(text("[公会] 数据库暂时不可用。", NamedTextColor.RED));
            return;
        }
        if (currentGuild(player).isPresent()) {
            open(player);
            return;
        }

        List<GuildMember> members = db.loadOtherGuildMembers(player.getUniqueId(), page * PAGE_SIZE, PAGE_SIZE);
        if (memberIndex < 0 || memberIndex >= members.size()) {
            return;
        }
        GuildMember member = members.get(memberIndex);
        Optional<Guild> guild = db.findGuildById(member.guildId());
        if (guild.isEmpty()) {
            player.sendMessage(text("[公会] 这个公会已经不存在。", NamedTextColor.RED));
            open(player, page);
            return;
        }
        if (!guild.get().freeJoinEnabled()) {
            player.sendMessage(text("[公会] " + stripRgbCodes(guild.get().name()) + " 未开启玩家自由加入。", NamedTextColor.RED));
            open(player, page);
            return;
        }
        if (!db.addMember(guild.get(), player)) {
            player.sendMessage(text("[公会] 加入失败，你可能已经加入了其他公会。", NamedTextColor.RED));
            open(player);
            return;
        }

        syncLuckPermsUserToGuild(player, guild.get());
        player.sendMessage(text("[公会] 已加入 " + stripRgbCodes(guild.get().name()) + "。", NamedTextColor.GREEN));
        Player owner = Bukkit.getPlayer(guild.get().ownerUuid());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(text("[公会] " + player.getName() + " 通过自由加入进入了公会。", NamedTextColor.GREEN));
        }
        open(player, 0);
    }

    private static void leaveGuild(@NotNull Player player, @NotNull Guild guild) {
        if (guild.ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage(text("[公会] 会长需要先转让公会，才可以离开。", NamedTextColor.RED));
            return;
        }

        GuildDataDB db = guildDb();
        if (db == null || !db.leaveGuild(guild, player)) {
            player.sendMessage(text("[公会] 离开公会失败，请稍后再试。", NamedTextColor.RED));
            return;
        }

        syncLuckPermsUserLeaveGuild(player, guild);
        player.sendMessage(text("[公会] 你已离开 " + stripRgbCodes(guild.name()) + "。", NamedTextColor.GREEN));
        open(player, 0);
    }

    private static void kickMember(@NotNull Player player, @NotNull UUID targetUuid, int backPage) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        Optional<GuildMember> target = db.loadMember(guild.get().id(), targetUuid);
        if (target.isEmpty()) {
            player.sendMessage(text("[公会] 该玩家已经不在当前公会。", NamedTextColor.RED));
            open(player, backPage);
            return;
        }
        if (!canManageMember(player, target.get(), "guild.member.kick")) {
            player.sendMessage(text("[公会] 你没有权限踢出该成员。", NamedTextColor.RED));
            openMemberProfile(player, targetUuid, backPage);
            return;
        }
        if (!db.kickMember(guild.get(), targetUuid)) {
            player.sendMessage(text("[公会] 踢出成员失败，请稍后再试。", NamedTextColor.RED));
            openMemberProfile(player, targetUuid, backPage);
            return;
        }

        syncLuckPermsUserLeaveGuild(target.get().playerName(), guild.get());
        player.sendMessage(text("[公会] 已将 " + target.get().playerName() + " 踢出公会。", NamedTextColor.GREEN));
        Player targetPlayer = Bukkit.getPlayer(targetUuid);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(text("[公会] 你已被移出 " + stripRgbCodes(guild.get().name()) + "。", NamedTextColor.RED));
        }
        VIEWING_MEMBER.remove(player.getUniqueId());
        open(player, backPage);
    }

    private static void disbandGuild(@NotNull Player player) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            open(player);
            return;
        }
        if (!guild.get().ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage(text("[公会] 只有会长可以解散公会。", NamedTextColor.RED));
            openSettings(player);
            return;
        }

        List<GuildMember> members = db.loadMembers(guild.get().id(), 0, Math.max(1, db.countMembers(guild.get().id())));
        if (!db.disbandGuild(guild.get(), player)) {
            player.sendMessage(text("[公会] 解散公会失败，请稍后再试。", NamedTextColor.RED));
            openSettings(player);
            return;
        }

        syncLuckPermsDisbandGuild(guild.get(), members);
        player.sendMessage(text("[公会] 已解散 " + stripRgbCodes(guild.get().name()) + "。", NamedTextColor.GREEN));
        open(player, 0);
    }

    private static void dispatchConsoleCommand(@NotNull String command) {
        log("Dispatch console command: " + command);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private static @NotNull String quoteCommandArgument(@NotNull String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "");
    }

    private static void performGuildDeposit(@NotNull Player player, long amount) {
        if (amount <= 0L) {
            player.sendMessage(text("[公会银行] 金额必须大于 0。", NamedTextColor.RED));
            return;
        }
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            return;
        }
        CmiEconomyBridge economyBridge = economyBridge();
        OptionalDouble wallet = economyBridge.balance(player);
        if (wallet.isEmpty()) {
            player.sendMessage(text("[公会银行] 经济系统不可用。", NamedTextColor.RED));
            return;
        }
        long walletBalance = Math.max(0L, (long) Math.floor(wallet.getAsDouble()));
        if (walletBalance < amount) {
            player.sendMessage(text("[公会银行] 身上资金不足。", NamedTextColor.RED));
            return;
        }
        if (!economyBridge.withdraw(player, amount)) {
            player.sendMessage(text("[公会银行] 扣款失败。", NamedTextColor.RED));
            return;
        }
        GuildBankChangeResult result = db.changeGuildBank(guild.get().id(), player, amount, "DEPOSIT");
        if (!result.success()) {
            economyBridge.deposit(player, amount);
            player.sendMessage(text("[公会银行] 数据保存失败，已退回资金。", NamedTextColor.RED));
            return;
        }
        player.sendMessage(text("[公会银行] 已存入 " + formatNumber(amount) + " 硬币。", NamedTextColor.GREEN));
    }

    private static void performGuildWithdraw(@NotNull Player player, long amount) {
        if (amount <= 0L) {
            player.sendMessage(text("[公会银行] 金额必须大于 0。", NamedTextColor.RED));
            return;
        }
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            return;
        }
        CmiEconomyBridge economyBridge = economyBridge();
        if (economyBridge.balance(player).isEmpty()) {
            player.sendMessage(text("[公会银行] 经济系统不可用。", NamedTextColor.RED));
            return;
        }
        GuildBankChangeResult result = db.changeGuildBank(guild.get().id(), player, -amount, "WITHDRAW");
        if (!result.success()) {
            player.sendMessage(text(result.insufficientFunds()
                    ? "[公会银行] 公会银行余额不足。"
                    : "[公会银行] 数据保存失败。", NamedTextColor.RED));
            return;
        }
        if (!economyBridge.deposit(player, amount)) {
            db.changeGuildBank(guild.get().id(), player, amount, "DEPOSIT");
            player.sendMessage(text("[公会银行] 发放资金失败，已回滚银行余额。", NamedTextColor.RED));
            return;
        }
        player.sendMessage(text("[公会银行] 已取出 " + formatNumber(amount) + " 硬币。", NamedTextColor.GREEN));
    }

    private static void openGuildBankAmountSign(@NotNull Player player, boolean deposit) {
        Location location = player.getLocation().getBlock().getLocation();
        BlockPosition position = Position.block(location);
        PENDING_BANK_INPUTS.put(player.getUniqueId(), new PendingBankInput(deposit, location, position));

        player.closeInventory();
        player.sendBlockChange(location, Material.OAK_SIGN.createBlockData());
        player.sendSignChange(location, List.of(Component.empty(), Component.empty(), Component.empty(), Component.empty()));

        UndergroundState plugin = UndergroundState.getInstance();
        if (plugin != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && PENDING_BANK_INPUTS.containsKey(player.getUniqueId())) {
                    player.openVirtualSign(position, Side.FRONT);
                }
            }, 1L);
        }
    }

    private static void cleanupVirtualSign(@NotNull Player player, @NotNull PendingBankInput pending) {
        player.sendBlockChange(pending.location(), pending.location().getBlock().getBlockData());
    }

    private static void createRole(@NotNull Player player, @NotNull String roleName) {
        GuildDataDB db = guildDb();
        UndergroundState plugin = UndergroundState.getInstance();
        if (db == null || plugin == null) {
            open(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RoleCreateResult result = createRoleInDatabase(db, playerId, roleName);
            runForOnlinePlayer(plugin, playerId, current -> {
                switch (result) {
                    case SUCCESS -> current.sendMessage(text("[公会] 身份组创建成功。", NamedTextColor.GREEN));
                    case NO_GUILD -> {
                        open(current);
                        return;
                    }
                    case NO_PERMISSION -> current.sendMessage(text("[公会] 你没有权限创建身份组。", NamedTextColor.RED));
                    case STORAGE_ERROR -> current.sendMessage(text("[公会] 身份组创建失败，请稍后再试。", NamedTextColor.RED));
                }
                openRoles(current, 0);
            });
        });
    }

    private static void renderGuildHome(@NotNull Inventory inventory,
                                        @NotNull Guild guild,
                                        int page,
                                        int totalPages) {
        GuildDataDB db = guildDb();
        int totalMembers = db == null ? 0 : db.countMembers(guild.id());
        long guildBankBalance = db == null ? guild.bankBalance() : db.guildBankBalance(guild.id());
        inventory.setItem(LEADER_SLOT, playerHead(
                guild.ownerUuid(),
                guild.ownerName(),
                nameText("公会会长 " + guild.ownerName()),
                List.of(
                        valueLine("公会: ", formatGuildName(guild.name())),
                        bodyText("等级: Lv." + guild.level()),
                        bodyText("成员: " + totalMembers + " 人")
                )
        ));
        inventory.setItem(INFO_SLOT, InfoGuiItems.createNamedItem(
                Material.ITEM_FRAME,
                nameText("工会信息"),
                List.of(
                        valueLine("名称: ", formatGuildName(guild.name())),
                        valueLine("会长: ", guild.ownerName()),
                        valueLine("等级: ", "Lv." + guild.level()),
                        valueLine("成员: ", totalMembers + " 人"),
                        valueLine("创建时间: ", TIME_FORMAT.format(guild.createdAt()))
                )
        ));
        inventory.setItem(BANK_SLOT, InfoGuiItems.createCustomHead(
                BANK_TEXTURE,
                nameText("工会银行"),
                List.of(
                        bodyText("公会银行和个人银行分开保存。"),
                        valueLine("余额: ", formatNumber(guildBankBalance) + " 硬币"),
                        Component.empty(),
                        actionText("点击打开工会银行")
                )
        ));
        inventory.setItem(LEAVE_GUILD_SLOT, InfoGuiItems.createCustomHead(
                LEAVE_GUILD_TEXTURE,
                nameText("离开公会"),
                List.of(
                        bodyText("离开当前公会并移除你的公会身份。"),
                        bodyText("会长需要先转让或解散公会。"),
                        Component.empty(),
                        actionText("点击离开公会")
                )
        ));
        inventory.setItem(INVITE_SLOT, InfoGuiItems.createCustomHead(
                INVITE_TEXTURE,
                nameText("邀请玩家"),
                List.of(bodyText("邀请没有公会的玩家加入。"), Component.empty(), actionText("点击邀请玩家"))
        ));
        inventory.setItem(SETTINGS_SLOT, InfoGuiItems.createNamedItem(
                Material.REDSTONE_TORCH,
                nameText("公会设定"),
                List.of(bodyText("进入身份组和权限设置。"), Component.empty(), actionText("点击打开设定"))
        ));
        inventory.setItem(DISPLAY_MODE_SLOT, InfoGuiItems.createNamedItem(
                Material.HOPPER,
                nameText("玩家显示排版方式"),
                List.of(bodyText("当前按职位与加入时间排序。"), valueLine("页面: ", (page + 1) + " / " + totalPages))
        ));

        List<GuildMember> members = db == null ? List.of() : db.loadMembers(guild.id(), page * PAGE_SIZE, PAGE_SIZE);
        renderMembers(inventory, members, "公会成员");
        renderNavigation(inventory, page, totalPages);
    }

    private static void renderNoGuildHome(@NotNull Inventory inventory,
                                          @NotNull Player player,
                                          int page,
                                          int totalPages) {
        inventory.setItem(LEADER_SLOT, InfoGuiItems.createPlayerHead(player));
        inventory.setItem(INFO_SLOT, InfoGuiItems.createNamedItem(
                Material.ITEM_FRAME,
                nameText("尚未加入公会"),
                List.of(bodyText("下面会显示其他公会的玩家头颅。"), bodyText("你可以先创建自己的公会。"))
        ));
        inventory.setItem(CREATE_SLOT, InfoGuiItems.createCustomHead(
                INVITE_TEXTURE,
                nameText("创建公会"),
                List.of(bodyText("点击后会打开铁砧输入公会名字。"), bodyText("支持 RGB 格式: &#1492ed文字"), Component.empty(), actionText("点击创建公会"))
        ));
        GuildDataDB db = guildDb();
        int inviteCount = db == null ? 0 : db.loadInvites(player.getUniqueId()).size();
        inventory.setItem(INVITES_SLOT, InfoGuiItems.createCustomHead(
                INVITE_TEXTURE,
                nameText("收到的邀请"),
                List.of(
                        valueLine("邀请数量: ", String.valueOf(inviteCount)),
                        bodyText("这里显示其他公会发给你的邀请。"),
                        Component.empty(),
                        actionText("点击查看邀请")
                )
        ));
        inventory.setItem(DISPLAY_MODE_SLOT, InfoGuiItems.createNamedItem(
                Material.HOPPER,
                nameText("玩家显示排版方式"),
                List.of(bodyText("当前显示其他公会成员。"), valueLine("页面: ", (page + 1) + " / " + totalPages))
        ));

        List<GuildMember> members = db == null
                ? List.of()
                : db.loadOtherGuildMembers(player.getUniqueId(), page * PAGE_SIZE, PAGE_SIZE);
        renderOtherGuildMembers(inventory, members);
        renderNavigation(inventory, page, totalPages);
    }

    private static void renderMembers(@NotNull Inventory inventory,
                                      @NotNull List<GuildMember> members,
                                      @NotNull String emptyLabel) {
        if (members.isEmpty()) {
            inventory.setItem(MEMBER_SLOTS[13], InfoGuiItems.createNamedItem(
                    Material.PAPER,
                    nameText(emptyLabel),
                    List.of(bodyText("目前没有可以显示的玩家。"))
            ));
            return;
        }
        for (int index = 0; index < members.size() && index < MEMBER_SLOTS.length; index++) {
            GuildMember member = members.get(index);
            inventory.setItem(MEMBER_SLOTS[index], playerHead(
                    member.uuid(),
                    member.playerName(),
                    nameText(member.playerName()),
                    List.of(
                            valueLine("公会: ", formatGuildName(member.guildName())),
                            valueLine("职位: ", memberRoleName(member)),
                            valueLine("状态: ", onlineStatusText(member.uuid())),
                            valueLine("加入时间: ", TIME_FORMAT.format(member.joinedAt())),
                            Component.empty(),
                            actionText("点击查看更多资讯")
                    )
            ));
        }
    }

    private static void renderOtherGuildMembers(@NotNull Inventory inventory,
                                                @NotNull List<GuildMember> members) {
        if (members.isEmpty()) {
            inventory.setItem(MEMBER_SLOTS[13], InfoGuiItems.createNamedItem(
                    Material.PAPER,
                    nameText("其他公会玩家"),
                    List.of(bodyText("目前没有可以显示的玩家。"))
            ));
            return;
        }
        for (int index = 0; index < members.size() && index < MEMBER_SLOTS.length; index++) {
            GuildMember member = members.get(index);
            boolean freeJoin = member.guildFreeJoinEnabled();
            inventory.setItem(MEMBER_SLOTS[index], playerHead(
                    member.uuid(),
                    member.playerName(),
                    nameText(member.playerName()),
                    List.of(
                            valueLine("公会: ", formatGuildName(member.guildName())),
                            valueLine("职位: ", roleName(member.role())),
                            valueLine("状态: ", onlineStatusText(member.uuid())),
                            valueLine("自由加入: ", freeJoin ? "开启" : "关闭"),
                            valueLine("加入时间: ", TIME_FORMAT.format(member.joinedAt())),
                            Component.empty(),
                            freeJoin ? actionText("点击加入这个公会") : bodyText("该公会需要邀请才能加入。")
                    )
            ));
        }
    }

    private static @NotNull ItemStack transferCandidateItem(@NotNull GuildMember member) {
        return playerHead(
                member.uuid(),
                member.playerName(),
                nameText(member.playerName()),
                List.of(
                        valueLine("公会: ", formatGuildName(member.guildName())),
                        valueLine("职位: ", roleName(member.role())),
                        valueLine("状态: ", onlineStatusText(member.uuid())),
                        valueLine("加入时间: ", TIME_FORMAT.format(member.joinedAt())),
                        Component.empty(),
                        actionText("点击转移会长")
                )
        );
    }

    private static @NotNull ItemStack roleItem(@NotNull GuildRole role) {
        Material material = switch (role.systemRole()) {
            case "OWNER" -> Material.NETHER_STAR;
            case "MEMBER" -> Material.PAPER;
            default -> Material.NAME_TAG;
        };
        return InfoGuiItems.createNamedItem(
                material,
                nameText(role.name()),
                List.of(
                        valueLine("类型: ", roleTypeName(role)),
                        valueLine("默认身份组: ", role.defaultRole() ? "是" : "否"),
                        bodyText("点击编辑权限。")
                )
        );
    }

    private static @NotNull ItemStack memberAssignableRoleItem(@NotNull GuildRole role) {
        Material material = "MEMBER".equals(role.systemRole()) ? Material.PAPER : Material.NAME_TAG;
        return InfoGuiItems.createNamedItem(
                material,
                nameText(role.name()),
                List.of(
                        valueLine("类型: ", roleTypeName(role)),
                        valueLine("默认身份组: ", role.defaultRole() ? "是" : "否"),
                        Component.empty(),
                        actionText("点击更换为这个身份组")
                )
        );
    }

    private static @NotNull ItemStack permissionItem(@NotNull PermissionDef permission,
                                                     boolean enabled,
                                                     boolean locked) {
        Material material = locked ? Material.RED_DYE : enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        String status = locked ? "会长固定开启" : enabled ? "开启" : "关闭";
        return InfoGuiItems.createNamedItem(
                material,
                nameText(permission.name()),
                List.of(
                        valueLine("分类: ", permission.category()),
                        valueLine("状态: ", status),
                        valueLine("权限: ", permission.id()),
                        bodyText(permission.description()),
                        Component.empty(),
                        actionText(locked ? "不能编辑" : "点击切换")
                )
        );
    }

    private static @NotNull ItemStack permissionCategoryItem(@NotNull String category, boolean ownerRole) {
        long count = PERMISSIONS.stream().filter(permission -> permission.category().equals(category)).count();
        Material material = switch (category) {
            case "银行" -> Material.CHEST;
            case "邀请" -> Material.PLAYER_HEAD;
            case "成员" -> Material.ARMOR_STAND;
            case "身份组" -> Material.NAME_TAG;
            case "资料" -> Material.WRITABLE_BOOK;
            default -> Material.BOOK;
        };
        ItemStack item = "邀请".equals(category)
                ? InfoGuiItems.createCustomHead(INVITE_TEXTURE, nameText(category + "权限"), List.of())
                : InfoGuiItems.createNamedItem(material, nameText(category + "权限"), List.of());
        item.editMeta(meta -> {
            meta.lore(List.of(
                    valueLine("权限数量: ", String.valueOf(count)),
                    bodyText(ownerRole ? "会长身份组固定拥有全部权限。" : "点击查看该分类权限。"),
                    Component.empty(),
                    actionText("点击进入")
            ));
        });
        return item;
    }

    private static @NotNull ItemStack createAnvilResultPaper(@NotNull String pageId, @NotNull String rawName) {
        String validationError = MEMBER_DISCORD_EDIT_PAGE_ID.equals(pageId)
                ? validateDiscord(rawName)
                : validateName(rawName, pageId);
        if (validationError != null) {
            return InfoGuiItems.createNamedItem(
                    Material.PAPER,
                    nameText("名字无效"),
                    List.of(bodyText(validationError), bodyText("请继续在铁砧里修改名字。"))
            );
        }
        if (MEMBER_DISCORD_EDIT_PAGE_ID.equals(pageId)) {
            String display = "-".equals(rawName.trim()) ? "清空 Discord" : rawName.trim();
            ItemStack item = InfoGuiItems.createNamedItem(
                    Material.PAPER,
                    nameText(display),
                    List.of(
                            bodyText("确认保存这个 Discord。"),
                            valueLine("Discord: ", display),
                            Component.empty(),
                            actionText("取出纸张确认")
                    )
            );
            item.editMeta(meta -> meta.getPersistentDataContainer().set(ANVIL_RAW_NAME_KEY, PersistentDataType.STRING, rawName));
            return item;
        }
        ItemStack item = InfoGuiItems.createNamedItem(
                Material.PAPER,
                formatGuildName(rawName),
                List.of(
                        bodyText(CREATE_PAGE_ID.equals(pageId) ? "确认创建这个公会。" : "确认创建这个身份组。"),
                        valueLine("原始名字: ", rawName),
                        Component.empty(),
                        actionText("取出纸张确认")
                )
        );
        item.editMeta(meta -> meta.getPersistentDataContainer().set(ANVIL_RAW_NAME_KEY, PersistentDataType.STRING, rawName));
        return item;
    }

    private static void renderNavigation(@NotNull Inventory inventory, int page, int totalPages) {
        ItemStack disabledPageSlot = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        inventory.setItem(BACK_SLOT, backItem("返回"));
        inventory.setItem(CLOSE_SLOT, InfoGuiItems.createCloseItem());
        inventory.setItem(FIRST_PAGE_SLOT, disabledPageSlot.clone());
        inventory.setItem(PREVIOUS_PAGE_SLOT, disabledPageSlot.clone());
        inventory.setItem(NEXT_PAGE_SLOT, disabledPageSlot.clone());
        inventory.setItem(LAST_PAGE_SLOT, disabledPageSlot.clone());
        if (page > 0) {
            inventory.setItem(FIRST_PAGE_SLOT, InfoGuiItems.createCustomHead(FIRST_PAGE_TEXTURE, nameText("第一页"), List.of(bodyText("点击回到第一页"))));
            inventory.setItem(PREVIOUS_PAGE_SLOT, InfoGuiItems.createCustomHead(PREVIOUS_PAGE_TEXTURE, nameText("上一页"), List.of(bodyText("点击切换上一页"))));
        }
        if (page + 1 < totalPages) {
            inventory.setItem(NEXT_PAGE_SLOT, InfoGuiItems.createCustomHead(NEXT_PAGE_TEXTURE, nameText("下一页"), List.of(bodyText("点击切换下一页"))));
            inventory.setItem(LAST_PAGE_SLOT, InfoGuiItems.createCustomHead(LAST_PAGE_TEXTURE, nameText("最后一页"), List.of(bodyText("点击跳到最后一页"))));
        }
    }

    private static void handlePagedNavigation(@NotNull Player player,
                                              int rawSlot,
                                              int page,
                                              int pageSize,
                                              @NotNull Runnable firstPage,
                                              @NotNull PageOpen pageOpen) {
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null) {
            return;
        }
        int total = db.loadRoles(guild.get().id()).size();
        int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        if (rawSlot == FIRST_PAGE_SLOT && page > 0) {
            firstPage.run();
        } else if (rawSlot == PREVIOUS_PAGE_SLOT && page > 0) {
            pageOpen.open(page - 1);
        } else if (rawSlot == NEXT_PAGE_SLOT && page + 1 < totalPages) {
            pageOpen.open(page + 1);
        } else if (rawSlot == LAST_PAGE_SLOT && page + 1 < totalPages) {
            pageOpen.open(totalPages - 1);
        }
    }

    private static void handleTransferNavigation(@NotNull Player player,
                                                 int rawSlot,
                                                 int page,
                                                 @NotNull Guild guild) {
        GuildDataDB db = guildDb();
        if (db == null) {
            return;
        }
        int total = db.countTransferCandidates(guild.id(), player.getUniqueId());
        int totalPages = Math.max(1, (total + ROLE_SLOTS.length - 1) / ROLE_SLOTS.length);
        if (rawSlot == FIRST_PAGE_SLOT && page > 0) {
            openTransferOwner(player, 0);
        } else if (rawSlot == PREVIOUS_PAGE_SLOT && page > 0) {
            openTransferOwner(player, page - 1);
        } else if (rawSlot == NEXT_PAGE_SLOT && page + 1 < totalPages) {
            openTransferOwner(player, page + 1);
        } else if (rawSlot == LAST_PAGE_SLOT && page + 1 < totalPages) {
            openTransferOwner(player, totalPages - 1);
        }
    }

    private static void fillBase(@NotNull Inventory inventory) {
        ItemStack black = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        ItemStack memberBackground = InfoGuiItems.createFiller(Material.GRAY_STAINED_GLASS_PANE, NamedTextColor.GRAY);
        Arrays.stream(BLACK_SLOTS).forEach(slot -> inventory.setItem(slot, black.clone()));
        Arrays.stream(MEMBER_SLOTS).forEach(slot -> inventory.setItem(slot, memberBackground.clone()));
    }

    private static void fillPanel(@NotNull Inventory inventory) {
        ItemStack black = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        ItemStack gray = InfoGuiItems.createFiller(Material.GRAY_STAINED_GLASS_PANE, NamedTextColor.GRAY);
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            inventory.setItem(slot, black.clone());
        }
        for (int slot : new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43}) {
            inventory.setItem(slot, gray.clone());
        }
    }

    private static void fillCompactBank(@NotNull Inventory inventory) {
        ItemStack black = InfoGuiItems.createFiller(Material.BLACK_STAINED_GLASS_PANE, NamedTextColor.BLACK);
        for (int slot = 0; slot < COMPACT_MENU_SIZE; slot++) {
            inventory.setItem(slot, black.clone());
        }
    }

    private static @NotNull ItemStack bankActionItem(@NotNull Material material,
                                                     @NotNull String name,
                                                     long amount) {
        return InfoGuiItems.createNamedItem(
                material,
                nameText(name),
                List.of(
                        valueLine("金额: ", formatNumber(amount) + " 硬币"),
                        Component.empty(),
                        actionText("点击执行")
                )
        );
    }

    private static @NotNull String bankActionName(@NotNull String action) {
        return "DEPOSIT".equals(action) ? "存入" : "取出";
    }

    private static @NotNull List<Player> onlinePlayersWithoutGuild(@NotNull Player viewer) {
        GuildDataDB db = guildDb();
        if (db == null) {
            return List.of();
        }
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(viewer.getUniqueId()))
                .filter(player -> db.findGuildByMember(player.getUniqueId()).isEmpty())
                .map(Player.class::cast)
                .toList();
    }

    private static @NotNull String memberDiscordDisplay(@NotNull UUID uuid) {
        return "暂未绑定";
    }

    private static long walletBalance(@NotNull Player player) {
        OptionalDouble balance = economyBridge().balance(player);
        return balance.isPresent() ? Math.max(0L, (long) Math.floor(balance.getAsDouble())) : 0L;
    }

    private static @NotNull CmiEconomyBridge economyBridge() {
        CmiEconomyBridge current = economy;
        if (current != null) {
            return current;
        }
        UndergroundState plugin = UndergroundState.getInstance();
        current = new CmiEconomyBridge(plugin == null ? java.util.logging.Logger.getAnonymousLogger() : plugin.getLogger());
        economy = current;
        return current;
    }

    private static @NotNull ItemStack backItem(@NotNull String label) {
        return InfoGuiItems.createNamedItem(Material.ARROW, nameText(label), List.of(bodyText("点击返回上一层。")));
    }

    private static @NotNull ItemStack playerHead(@NotNull UUID uuid,
                                                 @NotNull String playerName,
                                                 @NotNull Component name,
                                                 @NotNull List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (!(item.getItemMeta() instanceof SkullMeta meta)) {
            return item;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        meta.setOwningPlayer(offlinePlayer);
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static @NotNull Component onlineStatusText(@NotNull UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        boolean online = player != null && player.isOnline();
        return Component.text(online ? "在线" : "离线", online ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Optional<Guild> currentGuild(@NotNull Player player) {
        GuildDataDB db = guildDb();
        return db == null ? Optional.empty() : db.findGuildByMember(player.getUniqueId());
    }

    private static boolean canManageMember(@NotNull Player player,
                                           @NotNull GuildMember target,
                                           @NotNull String permission) {
        if (player.getUniqueId().equals(target.uuid())) {
            return false;
        }
        Optional<Guild> guild = currentGuild(player);
        GuildDataDB db = guildDb();
        if (guild.isEmpty() || db == null || guild.get().id() != target.guildId()) {
            return false;
        }
        if (guild.get().ownerUuid().equals(target.uuid())) {
            return false;
        }
        if (guild.get().ownerUuid().equals(player.getUniqueId())) {
            return true;
        }
        if (!hasGuildPermission(player, permission)) {
            return false;
        }
        Optional<GuildRole> actorRole = currentMemberRole(player);
        Optional<GuildRole> targetRole = db.findMemberRole(target.uuid());
        return actorRole.isPresent()
                && targetRole.isPresent()
                && actorRole.get().guildId() == target.guildId()
                && actorRole.get().priority() < targetRole.get().priority();
    }

    private static @NotNull List<GuildRole> assignableRoles(@NotNull Player player,
                                                            @NotNull Guild guild,
                                                            @Nullable GuildMember target) {
        GuildDataDB db = guildDb();
        if (db == null || target == null || !canManageMember(player, target, "guild.member.role.assign")) {
            return List.of();
        }
        Optional<GuildRole> actorRole = currentMemberRole(player);
        boolean owner = guild.ownerUuid().equals(player.getUniqueId());
        return db.loadRoles(guild.id()).stream()
                .filter(role -> !"OWNER".equals(role.systemRole()))
                .filter(role -> owner || actorRole.isPresent() && actorRole.get().priority() < role.priority())
                .toList();
    }

    private static boolean canOpenGuildSettings(@NotNull Player player) {
        return hasAnyGuildPermission(player,
                "guild.role.create",
                "guild.role.default.set",
                "guild.role.permissions.edit",
                "guild.info.settings.edit",
                "guild.info.name.edit",
                "guild.info.description.edit",
                "guild.info.website.edit");
    }

    private static boolean canOpenRoleManagement(@NotNull Player player) {
        return hasAnyGuildPermission(player,
                "guild.role.create",
                "guild.role.default.set",
                "guild.role.permissions.edit");
    }

    private static boolean canEditRoleEntries(@NotNull Player player) {
        return hasAnyGuildPermission(player,
                "guild.role.default.set",
                "guild.role.permissions.edit");
    }

    private static boolean canCreateRole(@NotNull Player player) {
        return hasGuildPermission(player, "guild.role.create");
    }

    private static boolean canSetDefaultRole(@NotNull Player player) {
        return hasGuildPermission(player, "guild.role.default.set");
    }

    private static boolean canEditRolePermissions(@NotNull Player player) {
        return hasGuildPermission(player, "guild.role.permissions.edit");
    }

    private static boolean hasAnyGuildPermission(@NotNull Player player, @NotNull String... permissions) {
        GuildDataDB db = guildDb();
        if (db == null) {
            return false;
        }
        Optional<GuildRole> role = currentMemberRole(player);
        if (role.isEmpty()) {
            return false;
        }
        Set<String> enabledPermissions = db.loadEnabledPermissions(role.get());
        for (String permission : permissions) {
            if (enabledPermissions.contains("*") || enabledPermissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyGuildPermission(@NotNull GuildDataDB db,
                                                 @NotNull UUID playerId,
                                                 @NotNull String... permissions) {
        Optional<GuildRole> role = db.findMemberRole(playerId);
        if (role.isEmpty()) {
            return false;
        }
        Set<String> enabledPermissions = db.loadEnabledPermissions(role.get());
        for (String permission : permissions) {
            if (enabledPermissions.contains("*") || enabledPermissions.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGuildPermission(@NotNull Player player, @NotNull String permission) {
        GuildDataDB db = guildDb();
        if (db == null) {
            return false;
        }
        Optional<GuildRole> role = currentMemberRole(player);
        return role.isPresent() && db.hasPermission(role.get(), permission);
    }

    private static @NotNull RoleCreateResult createRoleInDatabase(@NotNull GuildDataDB db,
                                                                  @NotNull UUID playerId,
                                                                  @NotNull String roleName) {
        Optional<Guild> guild = db.findGuildByMember(playerId);
        if (guild.isEmpty()) {
            return RoleCreateResult.NO_GUILD;
        }
        Optional<GuildRole> role = db.findMemberRole(playerId);
        if (role.isEmpty() || !db.hasPermission(role.get(), "guild.role.create")) {
            return RoleCreateResult.NO_PERMISSION;
        }
        return db.createRole(guild.get().id(), roleName)
                ? RoleCreateResult.SUCCESS
                : RoleCreateResult.STORAGE_ERROR;
    }

    private static Optional<GuildRole> currentMemberRole(@NotNull Player player) {
        GuildDataDB db = guildDb();
        return db == null ? Optional.empty() : db.findMemberRole(player.getUniqueId());
    }

    private static void runForOnlinePlayer(@NotNull UndergroundState plugin,
                                           @NotNull UUID playerId,
                                           @NotNull Consumer<Player> task) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                task.accept(player);
            }
        });
    }

    private static int currentPage(@NotNull Player player, @NotNull String pageId) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof InfoGuiHolder holder && pageId.equals(holder.pageId())) {
            return holder.page();
        }
        return 0;
    }

    private static GuildDataDB guildDb() {
        UndergroundState plugin = UndergroundState.getInstance();
        return plugin == null ? null : plugin.getGuildDataDB();
    }

    private static @NotNull String defaultGuildName(@NotNull Player player) {
        return player.getName() + "的公会";
    }

    private static @NotNull String normalizeName(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static @NotNull String rawNameFromResult(@Nullable ItemStack result, @Nullable String fallback) {
        if (result != null && result.hasItemMeta()) {
            ItemMeta meta = result.getItemMeta();
            String stored = meta.getPersistentDataContainer().get(ANVIL_RAW_NAME_KEY, PersistentDataType.STRING);
            if (stored != null && !stored.isBlank()) {
                return normalizeName(stored);
            }
        }
        return normalizeName(fallback);
    }

    private static @NotNull String formatItem(@Nullable ItemStack item) {
        if (item == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(item.getType().name()).append(" x").append(item.getAmount());
        if (item.hasItemMeta()) {
            String stored = item.getItemMeta().getPersistentDataContainer().get(ANVIL_RAW_NAME_KEY, PersistentDataType.STRING);
            if (stored != null) {
                builder.append(" storedRawName=\"").append(stored).append("\"");
            }
        }
        return builder.toString();
    }

    private static String validateName(@NotNull String rawName, @NotNull String pageId) {
        String plain = stripRgbCodes(rawName).trim();
        int maxVisible = CREATE_PAGE_ID.equals(pageId) ? 32 : 24;
        int maxRaw = CREATE_PAGE_ID.equals(pageId) ? 255 : 64;
        if (plain.length() < 2) {
            return "名字至少需要 2 个可见字符。";
        }
        if (plain.length() > maxVisible) {
            return "名字最多 " + maxVisible + " 个可见字符。";
        }
        if (rawName.length() > maxRaw) {
            return "包含颜色代码后的名字最多 " + maxRaw + " 个字符。";
        }
        return null;
    }

    private static String validateDiscord(@NotNull String rawDiscord) {
        String value = rawDiscord.trim();
        if ("-".equals(value)) {
            return null;
        }
        if (value.isEmpty()) {
            return "Discord 不能为空；输入 - 可以清空。";
        }
        if (value.length() > 64) {
            return "Discord 最多 64 个字符。";
        }
        if (value.contains("\r") || value.contains("\n")) {
            return "Discord 不能包含换行。";
        }
        return null;
    }

    private static @NotNull String roleName(@NotNull String role) {
        return switch (role) {
            case "OWNER" -> "会长";
            case "OFFICER" -> "管理员";
            default -> "成员";
        };
    }

    private static @NotNull String memberRoleName(@NotNull GuildMember member) {
        GuildDataDB db = guildDb();
        if (db == null) {
            return roleName(member.role());
        }
        return db.findMemberRole(member.uuid())
                .map(GuildRole::name)
                .orElseGet(() -> roleName(member.role()));
    }

    private static @NotNull String roleTypeName(@NotNull GuildRole role) {
        return switch (role.systemRole()) {
            case "OWNER" -> "会长";
            case "MEMBER" -> "成员";
            default -> "自定义";
        };
    }

    private static @NotNull List<String> permissionCategories() {
        return List.of("银行", "邀请", "成员", "身份组", "资料");
    }

    private static @NotNull List<PermissionDef> permissionsByCategory(@NotNull String category) {
        return PERMISSIONS.stream()
                .filter(permission -> permission.category().equals(category))
                .toList();
    }

    private static int indexOf(int @NotNull [] slots, int rawSlot) {
        for (int index = 0; index < slots.length; index++) {
            if (slots[index] == rawSlot) {
                return index;
            }
        }
        return -1;
    }

    private static @NotNull Component valueLine(@NotNull String label, @NotNull String value) {
        return valueLine(label, Component.text(value, ACTION_RGB));
    }

    private static @NotNull Component valueLine(@NotNull String label, @NotNull Component value) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(value)
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

    private static @NotNull Component formatGuildName(@NotNull String raw) {
        Component result = Component.empty();
        TextColor currentColor = ACTION_RGB;
        int index = 0;
        while (index < raw.length()) {
            if (isRgbCodeAt(raw, index)) {
                TextColor parsed = TextColor.fromHexString(raw.substring(index + 1, index + 8));
                currentColor = parsed == null ? ACTION_RGB : parsed;
                index += 8;
                continue;
            }

            int next = index + 1;
            while (next < raw.length() && !isRgbCodeAt(raw, next)) {
                next++;
            }
            result = result.append(Component.text(raw.substring(index, next), currentColor)
                    .decoration(TextDecoration.ITALIC, false));
            index = next;
        }
        return result.decoration(TextDecoration.ITALIC, false);
    }

    private static @NotNull String stripRgbCodes(@NotNull String raw) {
        StringBuilder builder = new StringBuilder(raw.length());
        int index = 0;
        while (index < raw.length()) {
            if (isRgbCodeAt(raw, index)) {
                index += 8;
                continue;
            }
            builder.append(raw.charAt(index));
            index++;
        }
        return builder.toString();
    }

    private static boolean isRgbCodeAt(@NotNull String raw, int index) {
        if (index < 0 || index + 8 > raw.length() || raw.charAt(index) != '&' || raw.charAt(index + 1) != '#') {
            return false;
        }
        for (int offset = 2; offset < 8; offset++) {
            char c = raw.charAt(index + offset);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static @NotNull String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, value));
    }

    private static @NotNull OptionalLong parseAmount(@NotNull List<Component> lines) {
        StringBuilder input = new StringBuilder();
        for (Component line : lines) {
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(line)
                    .trim();
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
            java.math.BigDecimal amount = new java.math.BigDecimal(raw.substring(0, numericEnd(raw)));
            char suffix = raw.charAt(raw.length() - 1);
            if (suffix == 'k' || suffix == 'K') {
                amount = amount.multiply(java.math.BigDecimal.valueOf(1_000L));
            } else if (suffix == 'm' || suffix == 'M') {
                amount = amount.multiply(java.math.BigDecimal.valueOf(1_000_000L));
            } else if (suffix == 'b' || suffix == 'B') {
                amount = amount.multiply(java.math.BigDecimal.valueOf(1_000_000_000L));
            }
            if (amount.compareTo(java.math.BigDecimal.ONE) < 0
                    || amount.compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
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

    private static void log(@NotNull String message) {
        UndergroundState plugin = UndergroundState.getInstance();
        if (plugin != null) {
            plugin.getLogger().info("[GuildInfoGui] " + message);
        }
    }

    private record PermissionDef(@NotNull String id,
                                 @NotNull String category,
                                 @NotNull String name,
                                 @NotNull String description,
                                 int slot) {
    }

    private record PendingBankInput(boolean deposit,
                                    @NotNull Location location,
                                    @NotNull BlockPosition position) {
    }

    @FunctionalInterface
    private interface PageOpen {
        void open(int page);
    }
}
