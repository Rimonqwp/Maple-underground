package maple.underground_caves.dungeon;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.party.PartyManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * start_room NPC: 優先使用 Citizens 顯示完整玩家皮膚；無 Citizens 時使用 Villager 戴玩家頭盔。
 * 面向房間內（背對門口），左鍵/右鍵開啟空白箱子 GUI。
 */
@SuppressWarnings("ALL")
public final class StartRoomNpc implements Listener {
    private static final UUID SKIN_UUID = UUID.fromString("bf5395a4-14b8-4f33-99d1-b475b07b2867");
    /** Citizens 用：Mojang 玩家名稱，用於抓取皮膚。請改為與 SKIN_UUID 對應的 Minecraft 玩家名，才能顯示正確皮膚。 */
    private static final String SKIN_NAME = "Steve";
    private static final int LOCAL_X = 4;
    private static final int LOCAL_Y = 3;
    private static final int LOCAL_Z = 15;
    private static final String GUI_TITLE = "職業選擇";
    private static final int CHEST_SIZE = 54;
    /** NPC 頭顱皮膚 (slot 45) */
    private static final String NPC_SKULL_TEXTURE = "8db4c861843b6c078781267ef335305f44bcd701df352ec4b1b1a556fc69a5d8";
    private static final int[] PLAYER_HEAD_SLOTS = { 2, 3, 4, 5, 6 };
    private static final int[] READY_STATUS_SLOTS = { 11, 12, 13, 14, 15 };
    /** 各人數對應的 slot 索引 (1人[4], 2人[3,5], 3人[3,4,5], 4人[2,3,5,6], 5人[2,3,4,5,6]) */
    private static final int[][] SLOT_INDICES_BY_COUNT = {
            { 2 },           // 1人
            { 1, 3 },        // 2人
            { 1, 2, 3 },     // 3人
            { 0, 1, 3, 4 },  // 4人: 2,3,5,6
            { 0, 1, 2, 3, 4 } // 5人
    };

    private final Test_dungeons plugin;
    private final org.bukkit.NamespacedKey npcKey;
    private final Map<String, UUID> npcIdsByRoomKey = new HashMap<>();
    private final Map<String, Integer> npcCitizenIdsByRoomKey = new HashMap<>();
    private final Set<UUID> npcIds = new HashSet<>();
    private CitizensStartRoomBridge citizensBridge;

    public StartRoomNpc(Test_dungeons plugin) {
        this.plugin = plugin;
        this.npcKey = new org.bukkit.NamespacedKey(plugin, "start_room_npc");
    }

    public void init() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            try {
                citizensBridge = new CitizensStartRoomBridge(this::openNpcGui);
                Bukkit.getPluginManager().registerEvents(citizensBridge, plugin);
            } catch (Throwable ignored) {
                citizensBridge = null;
            }
        }
    }

    public void registerStartRoomNpc(World world, Location roomOrigin, StructureTemplate.Rotation rotation,
                              int sizeX, int sizeZ, List<BlockFace> desiredFaces) {
        if (world == null || roomOrigin == null || rotation == null) return;

        String roomKey = world.getName() + ":" + roomOrigin.getBlockX() + ":" + roomOrigin.getBlockY() + ":" + roomOrigin.getBlockZ();

        Integer oldCitizenId = npcCitizenIdsByRoomKey.remove(roomKey);
        if (oldCitizenId != null && citizensBridge != null) {
            citizensBridge.removeNpc(oldCitizenId);
        }
        UUID oldId = npcIdsByRoomKey.remove(roomKey);
        if (oldId != null) {
            npcIds.remove(oldId);
            Entity e = world.getEntity(oldId);
            if (e != null) e.remove();
        }

        int[] rotated = rotateLocalXZ(LOCAL_X, LOCAL_Z, rotation, sizeX, sizeZ);
        int wx = roomOrigin.getBlockX() + rotated[0];
        int wy = roomOrigin.getBlockY() + LOCAL_Y;
        int wz = roomOrigin.getBlockZ() + rotated[1];

        BlockFace doorFace = (desiredFaces != null && !desiredFaces.isEmpty()) ? desiredFaces.get(0) : BlockFace.SOUTH;
        float yaw = yawFacingAwayFromDoor(doorFace);

        Location spawnAt = new Location(world, wx + 0.5, wy, wz + 0.5, yaw, 0f);

        if (citizensBridge != null) {
            Integer npcId = citizensBridge.createNpc(world, spawnAt, SKIN_NAME);
            if (npcId != null) {
                npcCitizenIdsByRoomKey.put(roomKey, npcId);
                return;
            }
        }

        Villager v = world.spawn(spawnAt, Villager.class, villager -> {
            villager.setAI(false);
            villager.setGravity(false);
            villager.setSilent(true);
            villager.setCollidable(true);
            villager.setInvulnerable(true);
            villager.setRemoveWhenFarAway(false);
            villager.setCustomNameVisible(false);
            villager.setAdult();
            villager.setProfession(Villager.Profession.NONE);
            villager.setVillagerType(Villager.Type.PLAINS);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(SKIN_UUID));
                head.setItemMeta(skull);
            }
            villager.getEquipment().setHelmet(head);

            villager.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
        });

        npcIdsByRoomKey.put(roomKey, v.getUniqueId());
        npcIds.add(v.getUniqueId());
    }

    public boolean isStartRoomNpc(Entity entity) {
        if (entity == null) return false;
        if (citizensBridge != null && citizensBridge.isOurNpcEntity(entity)) return true;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Byte b = pdc.get(npcKey, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    public boolean handleInteract(Player player, Entity clicked) {
        if (player == null || clicked == null) return false;
        if (!isStartRoomNpc(clicked)) return false;
        openNpcGui(player);
        return true;
    }

    public boolean handleDamage(Entity victim, Entity damager) {
        if (victim == null || damager == null) return false;
        if (!isStartRoomNpc(victim)) return false;
        if (!(damager instanceof Player player)) {
            if (damager instanceof org.bukkit.entity.Projectile proj) {
                Object shooter = proj.getShooter();
                if (shooter instanceof Player p) {
                    openNpcGui(p);
                    return true;
                }
            }
            return false;
        }
        openNpcGui(player);
        return true;
    }

    void openNpcGui(Player player) {
        if (player == null) return;
        // Citizens 的 NPC 點擊走 NPCRightClickEvent，不會經過一般實體互動的額外過濾；幽靈也要在這裡擋
        DungeonDeathListener death = plugin.getDungeonDeathListener();
        if (death != null && death.isGhost(player.getUniqueId())) {
            player.sendMessage(Component.text("幽靈狀態無法使用職業 NPC。", NamedTextColor.GRAY));
            return;
        }
        StartRoomClassHolder holder = new StartRoomClassHolder();
        Inventory inv = Bukkit.createInventory(holder, CHEST_SIZE, Component.text(GUI_TITLE, NamedTextColor.GRAY));
        holder.setInventory(inv);
        fillClassGui(inv, player);
        player.openInventory(inv);
    }

    void fillClassGui(Inventory inv, Player player) {
        if (inv == null || inv.getSize() < CHEST_SIZE) return;

        PartyManager pm = plugin.getPartyManager();
        // 從 MySQL 讀取常用職業，若玩家尚未選職業則預選
        var remote = plugin.getPlayerDungeonSettingsRemote();
        if (remote != null && pm != null && pm.getRole(player) == null) {
            String preferred = remote.getPreferredClass(player.getUniqueId());
            if (preferred != null) pm.setRole(player, preferred);
        }

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) glassMeta.displayName(Component.text(" "));
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < CHEST_SIZE; i++) {
            inv.setItem(i, glass);
        }

        ItemStack npcHead = createSkullWithTexture(NPC_SKULL_TEXTURE);
        inv.setItem(45, npcHead);

        List<UUID> members = pm != null ? pm.getPartyMembers(player) : List.of(player.getUniqueId());
        int n = Math.min(5, Math.max(1, members.size()));
        int[] indices = SLOT_INDICES_BY_COUNT[n - 1];

        for (int i = 0; i < n; i++) {
            int slotIdx = indices[i];
            int headSlot = PLAYER_HEAD_SLOTS[slotIdx];
            int readySlot = READY_STATUS_SLOTS[slotIdx];
            UUID memberId = members.get(i);
            Player member = Bukkit.getPlayer(memberId);
            ItemStack head = createPlayerHead(member != null ? member : Bukkit.getOfflinePlayer(memberId));
            inv.setItem(headSlot, head);
            inv.setItem(readySlot, readyStatusGlass(pm != null && pm.isEffectiveReady(memberId)));
        }

        // 右下角長期準備狀態：從 MySQL 讀取（PartyManager 已接 dungeonSettingsRemote 時會讀 MySQL）
        inv.setItem(53, autoReadyGlass(pm != null && pm.isAutoReady(player.getUniqueId())));

        var panelMgr = plugin.getDungeonPanelManager();
        if (panelMgr != null) {
            for (int i = 0; i < 5; i++) {
                inv.setItem(29 + i, panelMgr.createClassArrowForDisplay(i, player));
            }
        } else {
            inv.setItem(29, createTippedArrow(PotionType.REGENERATION, "治療師"));
            inv.setItem(30, createTippedArrow(PotionType.SWIFTNESS, "法師"));
            inv.setItem(31, createTippedArrow(PotionType.HARMING, "戰士"));
            inv.setItem(32, createTippedArrow(PotionType.STRENGTH, "弓箭手"));
            inv.setItem(33, createTippedArrow(PotionType.POISON, "坦克"));
        }

        inv.setItem(38, statusGlass(pm, player, PartyManager.ROLE_HEALER));
        inv.setItem(39, statusGlass(pm, player, PartyManager.ROLE_MAGE));
        inv.setItem(40, statusGlass(pm, player, PartyManager.ROLE_WARRIOR));
        inv.setItem(41, statusGlass(pm, player, PartyManager.ROLE_ARCHER));
        inv.setItem(42, statusGlass(pm, player, PartyManager.ROLE_TANK));
    }

    private static ItemStack createPlayerHead(org.bukkit.OfflinePlayer offline) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(offline);
            meta.displayName(Component.text(offline.getName() != null ? offline.getName() : "?", NamedTextColor.WHITE));
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack readyStatusGlass(boolean ready) {
        Material m = ready ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(m, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.displayName(Component.text(ready ? "已準備" : "未準備", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack autoReadyGlass(boolean enabled) {
        Material m = enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(m, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.displayName(Component.text(enabled ? "自動準備: 開啟" : "自動準備: 關閉", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createSkullWithTexture(String textureId) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            DungeonSecrets.applySkullTextureToMeta(meta, textureId, "StartRoomNpc.skull");
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack createTippedArrow(PotionType type, String name) {
        ItemStack arrow = new ItemStack(Material.TIPPED_ARROW, 1);
        if (arrow.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta) {
            meta.setBasePotionData(new PotionData(type));
            meta.displayName(Component.text(name, NamedTextColor.WHITE));
            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    private static ItemStack statusGlass(PartyManager pm, Player viewer, String role) {
        if (pm == null || viewer == null) return new ItemStack(Material.RED_STAINED_GLASS_PANE, 1);
        boolean taken = pm.isRoleTakenByParty(viewer, role);
        boolean mine = role.equals(pm.getRole(viewer));
        Material m = mine ? Material.LIME_STAINED_GLASS_PANE : (taken ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        ItemStack item = new ItemStack(m, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) meta.displayName(Component.text(mine ? "已選" : (taken ? "已有人選" : "未選"), NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    public boolean handleClassGuiClick(Player player, int slot) {
        if (player == null) return false;
        DungeonDeathListener death = plugin.getDungeonDeathListener();
        if (death != null && death.isGhost(player.getUniqueId())) {
            return true;
        }
        PartyManager pm = plugin.getPartyManager();

        // 右下角：切換長期準備，直接寫入 MySQL（PartyManager.setAutoReady 會寫入）
        if (slot == 53) {
            if (pm != null) {
                UUID id = player.getUniqueId();
                pm.setAutoReady(id, !pm.isAutoReady(id));
                fillClassGui(player.getOpenInventory().getTopInventory(), player);
            }
            return true;
        }

        for (int j = 0; j < READY_STATUS_SLOTS.length; j++) {
            if (READY_STATUS_SLOTS[j] != slot) continue;
            if (pm == null) return true;
            List<UUID> members = pm.getPartyMembers(player);
            int n = Math.min(5, Math.max(1, members.size()));
            int[] indices = SLOT_INDICES_BY_COUNT[n - 1];
            int memberIdx = -1;
            for (int i = 0; i < indices.length; i++) {
                if (indices[i] == j) { memberIdx = i; break; }
            }
            if (memberIdx < 0 || memberIdx >= members.size()) return true;
            UUID memberId = members.get(memberIdx);
            if (!player.getUniqueId().equals(memberId)) return true;
            boolean currentlyReady = pm.isEffectiveReady(memberId);
            if (currentlyReady) {
                if (pm.isAutoReady(memberId)) pm.setExplicitlyCancelled(memberId, true);
                else pm.setReady(player, false);
            } else {
                pm.setExplicitlyCancelled(memberId, false);
                if (!pm.isAutoReady(memberId)) pm.setReady(player, true);
            }
            fillClassGui(player.getOpenInventory().getTopInventory(), player);
            return true;
        }

        Integer roleSlot = switch (slot) {
            case 29, 38 -> 0;
            case 30, 39 -> 1;
            case 31, 40 -> 2;
            case 32, 41 -> 3;
            case 33, 42 -> 4;
            default -> null;
        };
        if (roleSlot == null) return true;
        if (pm == null) return true;
        String role = switch (roleSlot) {
            case 0 -> PartyManager.ROLE_HEALER;
            case 1 -> PartyManager.ROLE_MAGE;
            case 2 -> PartyManager.ROLE_WARRIOR;
            case 3 -> PartyManager.ROLE_ARCHER;
            case 4 -> PartyManager.ROLE_TANK;
            default -> null;
        };
        if (role != null) {
            pm.setRole(player, role);
            var remote = plugin.getPlayerDungeonSettingsRemote();
            if (remote != null) remote.setPreferredClass(player.getUniqueId(), role);
            var scoreboard = plugin.getDungeonScoreboard();
            if (scoreboard != null && player.getWorld() != null)
                scoreboard.updateClassForPlayer(player.getWorld().getName(), player.getUniqueId(), roleSlot);
            fillClassGui(player.getOpenInventory().getTopInventory(), player);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (event == null) return;
        if (handleInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcInteractAt(PlayerInteractAtEntityEvent event) {
        if (event == null) return;
        if (handleInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpcDamage(EntityDamageByEntityEvent event) {
        if (event == null) return;
        if (handleDamage(event.getEntity(), event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClassGuiClick(InventoryClickEvent event) {
        if (event == null) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof StartRoomClassHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;
        handleClassGuiClick(player, rawSlot);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClassGuiDrag(InventoryDragEvent event) {
        if (event == null) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof StartRoomClassHolder)) return;
        event.setCancelled(true);
    }

    public void onDisable() {
        if (citizensBridge != null) {
            citizensBridge.clearAll();
        }
        for (Map.Entry<String, UUID> e : npcIdsByRoomKey.entrySet()) {
            if (e.getValue() == null) continue;
            String[] parts = e.getKey().split(":");
            if (parts.length < 4) continue;
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) continue;
            Entity ent = w.getEntity(e.getValue());
            if (ent != null) ent.remove();
        }
        npcIdsByRoomKey.clear();
        npcCitizenIdsByRoomKey.clear();
        npcIds.clear();
    }

    private static float yawFacingAwayFromDoor(BlockFace doorFace) {
        float doorYaw = blockFaceToYaw(doorFace);
        float awayYaw = (doorYaw + 180f) % 360f;
        if (awayYaw < 0) awayYaw += 360f;
        return awayYaw;
    }

    private static float blockFaceToYaw(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0f;
            case SOUTH_SOUTH_WEST -> 22.5f;
            case SOUTH_WEST -> 45f;
            case WEST_SOUTH_WEST -> 67.5f;
            case WEST -> 90f;
            case WEST_NORTH_WEST -> 112.5f;
            case NORTH_WEST -> 135f;
            case NORTH_NORTH_WEST -> 157.5f;
            case NORTH -> 180f;
            case NORTH_NORTH_EAST -> 202.5f;
            case NORTH_EAST -> 225f;
            case EAST_NORTH_EAST -> 247.5f;
            case EAST -> 270f;
            case EAST_SOUTH_EAST -> 292.5f;
            case SOUTH_EAST -> 315f;
            case SOUTH_SOUTH_EAST -> 337.5f;
            default -> 0f;
        };
    }

    private static int[] rotateLocalXZ(int x, int z, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case R0 -> new int[]{x, z};
            case R90 -> new int[]{z, sizeX - 1 - x};
            case R180 -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
            case R270 -> new int[]{sizeZ - 1 - z, x};
        };
    }
}
