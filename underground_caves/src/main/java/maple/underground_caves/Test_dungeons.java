package maple.underground_caves;

import java.io.File;
import java.io.IOException;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import maple.underground_caves.commands.ItemHandInfoCommand;
import maple.underground_caves.dungeon.BossRoomManager;
import maple.underground_caves.dungeon.ChestLootManager;
import maple.underground_caves.dungeon.DungeonBlessingManager;
import maple.underground_caves.dungeon.DungeonCommand;
import maple.underground_caves.dungeon.DungeonDeathListener;
import maple.underground_caves.dungeon.DungeonMonsters;
import maple.underground_caves.dungeon.DungeonRoomRewardDB;
import maple.underground_caves.dungeon.DungeonScoreboard;
import maple.underground_caves.dungeon.DungeonSecrets;
import maple.underground_caves.dungeon.DungeonTeamScoreCommand;
import maple.underground_caves.dungeon.DungeonTemplateCommand;
import maple.underground_caves.dungeon.ExitRoomManager;
import maple.underground_caves.dungeon.PlayerDungeonSettingsRemote;
import maple.underground_caves.dungeon.ProfessionExperienceRemote;
import maple.underground_caves.dungeon.RoomRewardManager;
import maple.underground_caves.dungeon.StartRoomNpc;
import maple.underground_caves.dungeon.StartRoomReadyManager;
import maple.underground_caves.dungeon.StructureTemplate;
import maple.underground_caves.dungeon.DungeonBlessingManager.BlessingStat;
import maple.underground_caves.dungeon.classskill.ArcherClassSkillListener;
import maple.underground_caves.dungeon.classskill.DungeonClassSkillSharedState;
import maple.underground_caves.dungeon.classskill.HealerClassSkillListener;
import maple.underground_caves.dungeon.classskill.MageClassSkillListener;
import maple.underground_caves.dungeon.classskill.TankClassSkillListener;
import maple.underground_caves.dungeon.classskill.WarriorClassSkillListener;
import maple.underground_state.dungeon.panel.DungeonClassSkills;
import maple.underground_state.dungeon.panel.DungeonLevelXP;
import maple.underground_state.dungeon.panel.DungeonPanelDataProvider;
import maple.underground_state.dungeon.panel.DungeonPanelManager;
import maple.underground_caves.enchantments.EnchantBookCommand;
import maple.underground_caves.enchantments.EnchantmentListener;
import maple.underground_caves.enchantments.EnchantmentRegistry;
import maple.underground_caves.gear.AdaptiveSetManager;
import maple.underground_caves.gear.ArcherSetDropListener;
import maple.underground_caves.gear.ArcherSetManager;
import maple.underground_caves.gear.ArrowDamageBonusListener;
import maple.underground_caves.gear.ArrowDistanceHelmetManager;
import maple.underground_caves.gear.ArrowDistanceListener;
import maple.underground_caves.gear.ArrowKnockbackResistanceListener;
import maple.underground_caves.gear.BloodSetManager;
import maple.underground_caves.gear.BowNoConsumeArrowListener;
import maple.underground_caves.gear.CaveSpecialHelmetManager;
import maple.underground_caves.gear.CavernExplorerDamageListener;
import maple.underground_caves.gear.CavernExplorerSetManager;
import maple.underground_caves.gear.DefeatTankDropListener;
import maple.underground_caves.gear.GearGiveCommand;
import maple.underground_caves.gear.GearManager;
import maple.underground_caves.gear.GearStatsApplyTask;
import maple.underground_caves.gear.GearTabCompleter;
import maple.underground_caves.gear.ImmortalSkullListener;
import maple.underground_caves.gear.ImmortalSkullManager;
import maple.underground_caves.gear.QualityUpgradeItemManager;
import maple.underground_caves.gear.SecondWindHelmetManager;
import maple.underground_caves.gear.SecondWindListener;
import maple.underground_caves.gear.SieveSetDropListener;
import maple.underground_caves.gear.SieveSetManager;
import maple.underground_caves.gear.SkeletonMasterSetDropListener;
import maple.underground_caves.gear.SkeletonMasterSetManager;
import maple.underground_caves.gear.SkeletonSoldierSetDropListener;
import maple.underground_caves.gear.SkeletonSoldierSetManager;
import maple.underground_caves.gear.StatsDoubleHelmetManager;
import maple.underground_caves.gear.SublimationAnvilPrepareListener;
import maple.underground_caves.gear.SublimationGearManager;
import maple.underground_caves.gear.SublimationWorkbenchManager;
import maple.underground_caves.gear.UndergroundCaveHelmetManager;
import maple.underground_caves.gear.UnifiedSkeletonGearDropListener;
import maple.underground_caves.party.PartyCommand;
import maple.underground_caves.party.PartyManager;
import maple.underground_caves.party.PlayerAutoReadyDB;
import maple.underground_caves.puzzle.PuzzleRoom01Couplet;
import maple.underground_caves.puzzle.PuzzleRoom03TicTacToe;
import maple.underground_caves.puzzle.PuzzleRoom04LiarNPC;
import maple.underground_caves.puzzle.PuzzleRoom05IcePath;
import maple.underground_caves.puzzle.PuzzleRoom06Silverfish;
import maple.underground_caves.puzzle.PuzzleRoom07Quiz;
import maple.underground_caves.puzzle.PuzzleRoom08BlazeShoot;
import maple.underground_caves.stats.DungeonPlayerStatsExtension;
import maple.underground_caves.weapons.WeaponListener;
import maple.underground_caves.weapons.WeaponManager;
import maple.underground_caves.weapons.WeaponReloadCommand;
import maple.underground_caves.weapons.WeaponSpecialEffectsListener;
import maple.underground_caves.weapons.WeaponTabCompleter;
import maple.underground_caves.weapons.skill.FreezeSkillRunner;
import maple.underground_caves.weapons.skill.ShowtimeFireworkListener;
import maple.underground_caves.weapons.skill.ShowtimeSkillRunner;
import maple.underground_state.UndergroundState;
import maple.underground_state.stats.PlayerStatsData;
import maple.underground_state.stats.PlayerStatsManager;
import maple.underground_state.stats.PlayerStatsRuntime;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.swofty.dungeons.DungeonRoomType;
import net.swofty.dungeons.SkyBlockDungeon;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class Test_dungeons extends JavaPlugin {
   private static Test_dungeons instance;
   private static final String TRAP_CONFIG_FILE_NAME = "traps.yml";
   private volatile DungeonSnapshot lastDungeon;
   private final Map<String, String> dungeonWorldFloor = new ConcurrentHashMap();
   private final Map<String, Long> dungeonRunStartedMsByWorld = new ConcurrentHashMap();
   private final Map<String, Location> dungeonStartRoomByWorld = new ConcurrentHashMap();
   private final Map<UUID, Double> wishShieldExtraByPlayer = new ConcurrentHashMap();
   private final List<DoorDebug> debugDoors = new ArrayList();
   private final PuzzleRoom06Silverfish puzzleRoom06 = new PuzzleRoom06Silverfish(this);
   private final PuzzleRoom01Couplet puzzleRoom01 = new PuzzleRoom01Couplet(this);
   private final PuzzleRoom05IcePath puzzleRoom05 = new PuzzleRoom05IcePath(this);
   private final PuzzleRoom03TicTacToe puzzleRoom03 = new PuzzleRoom03TicTacToe(this);
   private final PuzzleRoom04LiarNPC puzzleRoom04 = new PuzzleRoom04LiarNPC(this);
   private final PuzzleRoom07Quiz puzzleRoom07 = new PuzzleRoom07Quiz(this);
   private final PuzzleRoom08BlazeShoot puzzleRoom08 = new PuzzleRoom08BlazeShoot(this);
   private StartRoomNpc startRoomNpc;
   private StartRoomReadyManager startRoomReadyManager;
   private DungeonPanelManager dungeonPanelManager;
   private DungeonSecrets dungeonSecrets;
   private DungeonMonsters dungeonMonsters;
   private ChestLootManager chestLootManager;
   private DungeonScoreboard dungeonScoreboard;
   private DungeonBlessingManager dungeonBlessingManager;
   private DungeonRoomRewardDB dungeonRoomRewardDB;
   private ProfessionExperienceRemote professionExperienceRemote;
   private PlayerDungeonSettingsRemote playerDungeonSettingsRemote;
   private RoomRewardManager roomRewardManager;
   private BossRoomManager bossRoomManager;
   private ExitRoomManager exitRoomManager;
   private PlayerAutoReadyDB playerAutoReadyDB;
   private PartyManager partyManager;
   private DungeonDeathListener dungeonDeathListener;
   private EnchantmentRegistry enchantmentRegistry;
   private EnchantmentListener enchantmentListener;
   private PlayerStatsManager playerStatsManager;
   private PlayerStatsRuntime playerStatsRuntime;
   private maple.underground_item.UndergroundItem undergroundItem;
   private DungeonPlayerStatsExtension dungeonPlayerStatsExtension;
   private WeaponManager weaponManager;
   private FreezeSkillRunner freezeSkillRunner;
   private ShowtimeSkillRunner showtimeSkillRunner;
   private GearManager gearManager;
   private GearStatsApplyTask gearStatsApplyTask;
   private ImmortalSkullManager immortalSkullManager;
   private SieveSetManager sieveSetManager;
   private ArcherSetManager archerSetManager;
   private SkeletonSoldierSetManager skeletonSoldierSetManager;
   private SkeletonMasterSetManager skeletonMasterSetManager;
   private UndergroundCaveHelmetManager undergroundCaveHelmetManager;
   private StatsDoubleHelmetManager statsDoubleHelmetManager;
   private CaveSpecialHelmetManager caveSpecialHelmetManager;
   private SecondWindHelmetManager secondWindHelmetManager;
   private ArrowDistanceHelmetManager arrowDistanceHelmetManager;
   private CavernExplorerSetManager cavernExplorerSetManager;
   private AdaptiveSetManager adaptiveSetManager;
   private BloodSetManager bloodSetManager;
   private SublimationGearManager sublimationGearManager;
   private final Map<String, Map<Long, Trap>> trapsByWorld = new HashMap();
   private BukkitRunnable trapTask;
   private final Set<String> unlimitedDoorWorlds = Collections.synchronizedSet(new HashSet());
   public static final double VIRTUAL_HEALTH_SECOND_ROW_THRESHOLD = (double)1000.0F;
   public static final double VIRTUAL_HEALTH_FULL_TWO_ROWS = (double)2000.0F;
   private static final double VIRTUAL_HEALTH_POINTS_ONE_ROW = (double)20.0F;
   private static final double VIRTUAL_HEALTH_POINTS_TWO_ROWS = (double)40.0F;

   public record DungeonSnapshot(SkyBlockDungeon dungeon, int width, int height, String worldName, int originX, int originY, int originZ, int roomScale, int anchorDoorY, String floor) {
   }

   private record DoorDebug(String worldName, int baseX, int baseY, int baseZ, BlockFace facing) {
   }

   private record TrapEditorHolder(String worldName, long emitterKey) implements org.bukkit.inventory.InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   private enum TrapMode {
      LAVA,
      ARROW
   }

   private static final class Trap {
      private final long emitterKey;
      private final int bx;
      private final int by;
      private final int bz;
      private final int dx;
      private final int dz;
      private final int floorY;
      private final int autoDist;
      private TrapMode mode = TrapMode.LAVA;
      private boolean lavaAuto = true;
      private int lavaDist = 0;
      private int arrowIntervalSeconds = 2;
      private int arrowCooldownSeconds = 0;

      private Trap(long emitterKey, int bx, int by, int bz, int dx, int dz, int floorY, int autoDist) {
         this.emitterKey = emitterKey;
         this.bx = bx;
         this.by = by;
         this.bz = bz;
         this.dx = dx;
         this.dz = dz;
         this.floorY = floorY;
         this.autoDist = autoDist;
      }

      private boolean contains(int x, int z) {
         int dist = this.getActiveLavaDist();
         if (dist <= 0) {
            return false;
         } else if (this.dx != 0) {
            if (z != this.bz) {
               return false;
            } else {
               int a = this.bx + this.dx;
               int b = this.bx + this.dx * dist;
               int min = Math.min(a, b);
               int max = Math.max(a, b);
               return x >= min && x <= max;
            }
         } else if (x != this.bx) {
            return false;
         } else {
            int a = this.bz + this.dz;
            int b = this.bz + this.dz * dist;
            int min = Math.min(a, b);
            int max = Math.max(a, b);
            return z >= min && z <= max;
         }
      }

      private int getActiveLavaDist() {
         int cap = Math.max(1, Math.min(64, this.autoDist));
         if (this.lavaAuto) {
            return cap;
         } else {
            int d = this.lavaDist > 0 ? this.lavaDist : cap;
            return Math.max(1, Math.min(cap, d));
         }
      }
   }

   public static Test_dungeons getInstance() {
      return instance;
   }

   public void setLastDungeon(SkyBlockDungeon dungeon, int width, int height, Location origin, int roomScale, int anchorDoorY) {
      this.setLastDungeon(dungeon, width, height, origin, roomScale, anchorDoorY, "F1");
   }

   public void setLastDungeon(SkyBlockDungeon dungeon, int width, int height, Location origin, int roomScale, int anchorDoorY, String floor) {
      if (dungeon == null) {
         this.lastDungeon = null;
         this.trapsByWorld.clear();
      } else {
         String worldName = origin != null && origin.getWorld() != null ? origin.getWorld().getName() : null;
         int ox = origin != null ? origin.getBlockX() : 0;
         int oy = origin != null ? origin.getBlockY() : 0;
         int oz = origin != null ? origin.getBlockZ() : 0;
         String f = floor != null && !floor.isBlank() ? floor : "F1";
         this.lastDungeon = new DungeonSnapshot(dungeon, width, height, worldName, ox, oy, oz, roomScale, anchorDoorY, f);
         if (worldName != null) {
            this.dungeonWorldFloor.put(worldName, f);
            this.trapsByWorld.remove(worldName);
            if (this.dungeonScoreboard != null) {
               this.dungeonScoreboard.prepareRun(worldName);
               this.dungeonScoreboard.initRunFromSnapshot(this.lastDungeon);
            }
         }

      }
   }

   public String getFloorForDungeonWorld(String worldName) {
      if (worldName == null) {
         return "F1";
      } else {
         String f = (String)this.dungeonWorldFloor.get(worldName);
         if (f != null && !f.isBlank()) {
            return f;
         } else {
            DungeonSnapshot snap = this.getLastDungeon();
            if (snap != null && worldName.equals(snap.worldName())) {
               return snap.floor() != null ? snap.floor() : "F1";
            } else {
               return "F1";
            }
         }
      }
   }

   public boolean isDungeonWorld(String worldName) {
      if (worldName == null) {
         return false;
      } else if (this.dungeonWorldFloor.containsKey(worldName)) {
         return true;
      } else {
         DungeonSnapshot snap = this.getLastDungeon();
         return snap != null && worldName.equals(snap.worldName());
      }
   }

   public Location getMainWorldSpawnLocation() {
      for(World w : Bukkit.getWorlds()) {
         if (w != null && !this.isDungeonWorld(w.getName())) {
            return w.getSpawnLocation();
         }
      }

      List<World> worlds = Bukkit.getWorlds();
      return worlds.isEmpty() ? null : ((World)worlds.get(0)).getSpawnLocation();
   }

   public void registerLavaTrapEmitter(Block emitter, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
      this.registerLavaTrapEmitter(emitter, minX, maxX, minY, maxY, minZ, maxZ, (String)null, 0, 0, 0);
   }

   public void registerLavaTrapEmitter(Block emitter, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, String templateName, int localX0, int localY0, int localZ0) {
      if (emitter != null) {
         World world = emitter.getWorld();
         if (world != null) {
            if (emitter.getType() == Material.DISPENSER || emitter.getType() == Material.DROPPER) {
               BlockData data = emitter.getBlockData();
               if (data instanceof Directional) {
                  Directional directional = (Directional)data;
                  BlockFace face = directional.getFacing();
                  if (face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST) {
                     int bx = emitter.getX();
                     int by = emitter.getY();
                     int bz = emitter.getZ();
                     if (bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ) {
                        int dx = face == BlockFace.EAST ? 1 : (face == BlockFace.WEST ? -1 : 0);
                        int dz = face == BlockFace.SOUTH ? 1 : (face == BlockFace.NORTH ? -1 : 0);
                        int dist = 0;

                        for(int i = 1; i <= 64; ++i) {
                           int x = bx + dx * i;
                           int z = bz + dz * i;
                           if (x < minX || x > maxX || by < minY || by > maxY || z < minZ || z > maxZ) {
                              dist = i;
                              break;
                           }

                           Block b = world.getBlockAt(x, by, z);
                           if (!isPassableBlock(b)) {
                              dist = i;
                              break;
                           }
                        }

                        if (dist > 1) {
                           int floorY = by - 1;
                           if (floorY >= world.getMinHeight() && floorY <= world.getMaxHeight()) {
                              long emitterKey = packBlock(bx, by, bz);
                              Trap trap = new Trap(emitterKey, bx, by, bz, dx, dz, floorY, dist - 1);
                              if (templateName != null && !templateName.isBlank()) {
                                 String t = templateName.trim().toLowerCase(Locale.ROOT);
                                 this.applyTemplateTrapSettings(t, localX0, localY0, localZ0, trap);
                              }

                              this.loadTrapSettingsFromBlock(emitter, trap);
                              if (templateName != null && !templateName.isBlank()) {
                                 String t = templateName.trim().toLowerCase(Locale.ROOT);
                                 this.tagTrapEmitterTemplateMeta(emitter, t, localX0, localY0, localZ0);
                              }

                              ((Map)this.trapsByWorld.computeIfAbsent(world.getName(), (k) -> new HashMap())).put(emitterKey, trap);
                              this.ensureTrapTask();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void openTrapEditorGUI(Player player, Block emitter) {
      if (player != null && emitter != null) {
         World world = emitter.getWorld();
         if (world != null) {
            if (emitter.getType() == Material.DISPENSER || emitter.getType() == Material.DROPPER) {
               Trap trap = this.getOrCreateTrap(world, emitter);
               if (trap != null) {
                  Inventory inv = Bukkit.createInventory(new TrapEditorHolder(world.getName(), trap.emitterKey), 27, Component.text("陷阱设置", NamedTextColor.GOLD));
                  this.fillTrapEditorInventory(inv, trap);
                  player.openInventory(inv);
               }
            }
         }
      }
   }

   private Trap getOrCreateTrap(World world, Block emitter) {
      if (world != null && emitter != null) {
         if (emitter.getType() != Material.DISPENSER && emitter.getType() != Material.DROPPER) {
            return null;
         } else {
            BlockData data = emitter.getBlockData();
            if (!(data instanceof Directional)) {
               return null;
            } else {
               Directional directional = (Directional)data;
               BlockFace face = directional.getFacing();
               if (face != BlockFace.NORTH && face != BlockFace.SOUTH && face != BlockFace.EAST && face != BlockFace.WEST) {
                  return null;
               } else {
                  int bx = emitter.getX();
                  int by = emitter.getY();
                  int bz = emitter.getZ();
                  int floorY = by - 1;
                  int dx = face == BlockFace.EAST ? 1 : (face == BlockFace.WEST ? -1 : 0);
                  int dz = face == BlockFace.SOUTH ? 1 : (face == BlockFace.NORTH ? -1 : 0);
                  int dist = 0;

                  for(int i = 1; i <= 64; ++i) {
                     int x = bx + dx * i;
                     int z = bz + dz * i;
                     Block b = world.getBlockAt(x, by, z);
                     if (!isPassableBlock(b)) {
                        dist = i;
                        break;
                     }
                  }

                  if (dist == 0) {
                     dist = 65;
                  }

                  if (dist <= 1) {
                     return null;
                  } else {
                     long emitterKey = packBlock(bx, by, bz);
                     Trap trap = (Trap)((Map)this.trapsByWorld.computeIfAbsent(world.getName(), (k) -> new HashMap())).get(emitterKey);
                     if (trap == null) {
                        trap = new Trap(emitterKey, bx, by, bz, dx, dz, floorY, dist - 1);
                        this.loadTrapSettingsFromStorage(emitter, trap);
                        ((Map)this.trapsByWorld.computeIfAbsent(world.getName(), (k) -> new HashMap())).put(emitterKey, trap);
                        this.ensureTrapTask();
                     }

                     return trap;
                  }
               }
            }
         }
      } else {
         return null;
      }
   }

   private boolean isKnownTrapEmitter(Block emitter) {
      if (emitter == null) {
         return false;
      } else {
         World world = emitter.getWorld();
         if (world == null) {
            return false;
         } else if (emitter.getType() != Material.DISPENSER && emitter.getType() != Material.DROPPER) {
            return false;
         } else {
            long emitterKey = packBlock(emitter.getX(), emitter.getY(), emitter.getZ());
            Map<Long, Trap> traps = (Map)this.trapsByWorld.get(world.getName());
            if (traps != null && traps.containsKey(emitterKey)) {
               return true;
            } else {
               BlockState var7 = emitter.getState();
               if (!(var7 instanceof TileState)) {
                  return false;
               } else {
                  TileState state = (TileState)var7;
                  NamespacedKey modeKey = new NamespacedKey(this, "trap_mode");
                  String v = (String)state.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
                  return v != null && !v.isBlank();
               }
            }
         }
      }
   }

   private void fillTrapEditorInventory(Inventory inv, Trap trap) {
      if (inv != null && trap != null) {
         inv.clear();
         Material var10003 = trap.mode == maple.underground_caves.Test_dungeons.TrapMode.LAVA ? Material.LAVA_BUCKET : Material.ARROW;
         String var10005 = trap.mode == maple.underground_caves.Test_dungeons.TrapMode.LAVA ? "熔岩" : "弓箭";
         inv.setItem(10, this.guiItem(var10003, "切换模式", List.of("当前: " + var10005)));
         int cap = Math.max(1, Math.min(64, trap.autoDist));
         String lavaMode = trap.lavaAuto ? "自动" : "手动";
         String lavaVal = trap.lavaAuto ? "自动(最大 " + cap + ")" : Math.max(1, Math.min(cap, trap.lavaDist)) + " / " + cap;
         inv.setItem(13, this.guiItem(Material.SLIME_BALL, "熔岩长度: " + lavaVal, List.of("模式: " + lavaMode, "Shift点击切换自动/手动", "左键+1 右键-1")));
         int interval = Math.max(1, Math.min(60, trap.arrowIntervalSeconds));
         inv.setItem(16, this.guiItem(Material.CLOCK, "弓箭间隔: " + interval + "s", List.of("Shift点击+5/-5", "左键+1 右键-1")));
      }
   }

   private ItemStack guiItem(Material material, String name, List<String> lore) {
      ItemStack item = new ItemStack(material != null ? material : Material.PAPER, 1);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.displayName(Component.text(name != null ? name : "", NamedTextColor.YELLOW));
         if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList();

            for(String s : lore) {
               if (s != null) {
                  lines.add(Component.text(s, NamedTextColor.GRAY));
               }
            }

            meta.lore(lines);
         }

         item.setItemMeta(meta);
      }

      return item;
   }

   private void ensureTrapTask() {
      if (this.trapTask == null) {
         this.trapTask = new BukkitRunnable() {
            public void run() {
               Test_dungeons.this.tickTraps();
            }
         };
         this.trapTask.runTaskTimer(this, 20L, 20L);
      }
   }

   private void tickTraps() {
      if (this.isEnabled() && !this.trapsByWorld.isEmpty()) {
         for(String worldName : List.copyOf(this.trapsByWorld.keySet())) {
            Map<Long, Trap> traps = (Map)this.trapsByWorld.get(worldName);
            if (traps != null && !traps.isEmpty()) {
               World world = Bukkit.getWorld(worldName);
               if (world != null) {
                  boolean hasPlayers = false;

                  for(Player p : world.getPlayers()) {
                     if (p != null && p.isOnline()) {
                        hasPlayers = true;
                        break;
                     }
                  }

                  for(Trap trap : traps.values()) {
                     if (trap != null) {
                        if (trap.mode == maple.underground_caves.Test_dungeons.TrapMode.LAVA) {
                           int dist = trap.getActiveLavaDist();
                           int count = 0;

                           for(int i = 1; i <= dist; ++i) {
                              int x = trap.bx + trap.dx * i;
                              int z = trap.bz + trap.dz * i;
                              double px = (double)x + (double)0.5F;
                              double py = (double)trap.floorY + 1.02;
                              double pz = (double)z + (double)0.5F;
                              world.spawnParticle(Particle.DRIPPING_LAVA, px, py, pz, 2, (double)0.25F, 0.02, (double)0.25F, (double)0.0F);
                              ++count;
                              if (count >= 96) {
                                 break;
                              }
                           }
                        } else if (trap.mode == maple.underground_caves.Test_dungeons.TrapMode.ARROW && hasPlayers) {
                           int interval = Math.max(1, Math.min(60, trap.arrowIntervalSeconds));
                           if (trap.arrowCooldownSeconds > 0) {
                              --trap.arrowCooldownSeconds;
                           } else {
                              this.shootTrapArrow(world, trap);
                              trap.arrowCooldownSeconds = interval - 1;
                           }
                        }
                     }
                  }

                  for(Player p : world.getPlayers()) {
                     if (p != null && p.isOnline() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                        Location loc = p.getLocation();
                        int px = loc.getBlockX();
                        int pz = loc.getBlockZ();
                        int floorY = loc.getBlockY() - 1;

                        for(Trap trap : traps.values()) {
                           if (trap != null && trap.mode == maple.underground_caves.Test_dungeons.TrapMode.LAVA && trap.floorY == floorY && trap.contains(px, pz)) {
                              applyLavaTrapDamage(p);
                              break;
                           }
                        }
                     }
                  }
               }
            } else {
               this.trapsByWorld.remove(worldName);
            }
         }

      }
   }

   private void shootTrapArrow(World world, Trap trap) {
      if (world != null && trap != null) {
         Vector dir = new Vector((double)trap.dx, 0.02, (double)trap.dz);
         if (!(dir.lengthSquared() < 1.0E-6)) {
            dir.normalize();
            Location spawn = new Location(world, (double)trap.bx + (double)0.5F + (double)trap.dx * 0.55, (double)trap.by + 0.45, (double)trap.bz + (double)0.5F + (double)trap.dz * 0.55);
            AbstractArrow arrow = world.spawnArrow(spawn, dir, 2.2F, 0.0F);
            NamespacedKey key = new NamespacedKey(this, "trap_arrow");
            arrow.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte)1);
            arrow.setPickupStatus(PickupStatus.DISALLOWED);
            arrow.setShooter((ProjectileSource)null);
            arrow.setCritical(false);
            world.playSound(spawn, Sound.BLOCK_DISPENSER_DISPENSE, 0.9F, 1.1F);
         }
      }
   }

   private boolean isTrapArrow(AbstractArrow arrow) {
      if (arrow == null) {
         return false;
      } else {
         NamespacedKey key = new NamespacedKey(this, "trap_arrow");
         Byte v = (Byte)arrow.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
         return v != null && v == 1;
      }
   }

   private static void applyLavaTrapDamage(Player player) {
      if (player != null) {
         double max = (double)20.0F;
         AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
         if (inst != null) {
            try {
               max = inst.getValue();
            } catch (Throwable var10) {
            }
         }

         if (max <= (double)0.0F) {
            max = (double)20.0F;
         }

         double dmg = max / (double)6.0F;
         if (!(dmg <= (double)0.0F)) {
            double health = player.getHealth();
            double next = Math.max((double)0.0F, health - dmg);
            player.setHealth(next);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0F, 1.0F);
         }
      }
   }

   private static boolean isPassableBlock(Block block) {
      if (block == null) {
         return false;
      } else {
         Material type = block.getType();
         return type.isAir() ? true : block.isPassable();
      }
   }

   private static long packBlock(int x, int y, int z) {
      return (long)(x & 67108863) << 38 | (long)(z & 67108863) << 12 | (long)(y & 4095);
   }

   private static int unpackBlockX(long key) {
      int x = (int)(key >> 38);
      if (x >= 33554432) {
         x -= 67108864;
      }

      return x;
   }

   private static int unpackBlockZ(long key) {
      int z = (int)(key >> 12 & 67108863L);
      if (z >= 33554432) {
         z -= 67108864;
      }

      return z;
   }

   private static int unpackBlockY(long key) {
      int y = (int)(key & 4095L);
      if (y >= 2048) {
         y -= 4096;
      }

      return y;
   }

   private void loadTrapSettingsFromBlock(Block emitter, Trap trap) {
      if (emitter != null && trap != null) {
         BlockState var4 = emitter.getState();
         if (var4 instanceof TileState) {
            TileState state = (TileState)var4;
            NamespacedKey var12 = new NamespacedKey(this, "trap_mode");
            NamespacedKey lavaAutoKey = new NamespacedKey(this, "trap_lava_auto");
            NamespacedKey lavaDistKey = new NamespacedKey(this, "trap_lava_dist");
            NamespacedKey arrowIntervalKey = new NamespacedKey(this, "trap_arrow_interval");
            String mode = (String)state.getPersistentDataContainer().get(var12, PersistentDataType.STRING);
            if (mode != null) {
               if (mode.equalsIgnoreCase("ARROW")) {
                  trap.mode = maple.underground_caves.Test_dungeons.TrapMode.ARROW;
               }

               if (mode.equalsIgnoreCase("LAVA")) {
                  trap.mode = maple.underground_caves.Test_dungeons.TrapMode.LAVA;
               }
            }

            Byte auto = (Byte)state.getPersistentDataContainer().get(lavaAutoKey, PersistentDataType.BYTE);
            if (auto != null) {
               trap.lavaAuto = auto != 0;
            }

            Integer d = (Integer)state.getPersistentDataContainer().get(lavaDistKey, PersistentDataType.INTEGER);
            if (d != null) {
               trap.lavaDist = d;
            }

            Integer interval = (Integer)state.getPersistentDataContainer().get(arrowIntervalKey, PersistentDataType.INTEGER);
            if (interval != null) {
               trap.arrowIntervalSeconds = interval;
            }

         }
      }
   }

   private void loadTrapSettingsFromStorage(Block emitter, Trap trap) {
      if (emitter != null && trap != null) {
         BlockState var4 = emitter.getState();
         if (var4 instanceof TileState) {
            TileState state = (TileState)var4;
            NamespacedKey tplKey = new NamespacedKey(this, "trap_tpl");
            NamespacedKey lxKey = new NamespacedKey(this, "trap_lx");
            NamespacedKey lyKey = new NamespacedKey(this, "trap_ly");
            NamespacedKey lzKey = new NamespacedKey(this, "trap_lz");
            String tpl = (String)state.getPersistentDataContainer().get(tplKey, PersistentDataType.STRING);
            Integer lx = (Integer)state.getPersistentDataContainer().get(lxKey, PersistentDataType.INTEGER);
            Integer ly = (Integer)state.getPersistentDataContainer().get(lyKey, PersistentDataType.INTEGER);
            Integer lz = (Integer)state.getPersistentDataContainer().get(lzKey, PersistentDataType.INTEGER);
            if (tpl != null && !tpl.isBlank() && lx != null && ly != null && lz != null) {
               this.applyTemplateTrapSettings(tpl.trim().toLowerCase(Locale.ROOT), lx, ly, lz, trap);
            }
         }

         this.loadTrapSettingsFromBlock(emitter, trap);
      }
   }

   private void saveTrapSettingsToBlock(Block emitter, Trap trap) {
      if (emitter != null && trap != null) {
         BlockState var4 = emitter.getState();
         if (var4 instanceof TileState) {
            TileState state = (TileState)var4;
            NamespacedKey var16 = new NamespacedKey(this, "trap_mode");
            NamespacedKey lavaAutoKey = new NamespacedKey(this, "trap_lava_auto");
            NamespacedKey lavaDistKey = new NamespacedKey(this, "trap_lava_dist");
            NamespacedKey arrowIntervalKey = new NamespacedKey(this, "trap_arrow_interval");
            state.getPersistentDataContainer().set(var16, PersistentDataType.STRING, trap.mode.name());
            state.getPersistentDataContainer().set(lavaAutoKey, PersistentDataType.BYTE, Byte.valueOf((byte)(trap.lavaAuto ? 1 : 0)));
            state.getPersistentDataContainer().set(lavaDistKey, PersistentDataType.INTEGER, trap.lavaDist);
            state.getPersistentDataContainer().set(arrowIntervalKey, PersistentDataType.INTEGER, trap.arrowIntervalSeconds);
            state.update(true, false);
            NamespacedKey tplKey = new NamespacedKey(this, "trap_tpl");
            NamespacedKey lxKey = new NamespacedKey(this, "trap_lx");
            NamespacedKey lyKey = new NamespacedKey(this, "trap_ly");
            NamespacedKey lzKey = new NamespacedKey(this, "trap_lz");
            String tpl = (String)state.getPersistentDataContainer().get(tplKey, PersistentDataType.STRING);
            Integer lx = (Integer)state.getPersistentDataContainer().get(lxKey, PersistentDataType.INTEGER);
            Integer ly = (Integer)state.getPersistentDataContainer().get(lyKey, PersistentDataType.INTEGER);
            Integer lz = (Integer)state.getPersistentDataContainer().get(lzKey, PersistentDataType.INTEGER);
            if (tpl != null && !tpl.isBlank() && lx != null && ly != null && lz != null) {
               this.saveTemplateTrapSettings(tpl.trim().toLowerCase(Locale.ROOT), lx, ly, lz, trap);
            }

         }
      }
   }

   private void tagTrapEmitterTemplateMeta(Block emitter, String templateName, int localX0, int localY0, int localZ0) {
      if (emitter != null && templateName != null && !templateName.isBlank()) {
         BlockState var7 = emitter.getState();
         if (var7 instanceof TileState) {
            TileState state = (TileState)var7;
            NamespacedKey var11 = new NamespacedKey(this, "trap_tpl");
            NamespacedKey lxKey = new NamespacedKey(this, "trap_lx");
            NamespacedKey lyKey = new NamespacedKey(this, "trap_ly");
            NamespacedKey lzKey = new NamespacedKey(this, "trap_lz");
            state.getPersistentDataContainer().set(var11, PersistentDataType.STRING, templateName.trim().toLowerCase(Locale.ROOT));
            state.getPersistentDataContainer().set(lxKey, PersistentDataType.INTEGER, localX0);
            state.getPersistentDataContainer().set(lyKey, PersistentDataType.INTEGER, localY0);
            state.getPersistentDataContainer().set(lzKey, PersistentDataType.INTEGER, localZ0);
            state.update(true, false);
         }
      }
   }

   private File trapConfigFile() {
      File dir = this.getDataFolder();
      if (!dir.exists()) {
         dir.mkdirs();
      }

      return new File(dir, "traps.yml");
   }

   private static String localTrapKey(int x0, int y0, int z0) {
      return x0 + "," + y0 + "," + z0;
   }

   private void applyTemplateTrapSettings(String templateName, int localX0, int localY0, int localZ0, Trap trap) {
      if (templateName != null && !templateName.isBlank() && trap != null) {
         File file = this.trapConfigFile();
         if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String var10000 = templateName.trim().toLowerCase(Locale.ROOT);
            String base = "templates." + var10000 + ".traps." + localTrapKey(localX0, localY0, localZ0);
            ConfigurationSection sec = cfg.getConfigurationSection(base);
            if (sec != null) {
               String mode = sec.getString("mode");
               if (mode != null) {
                  if (mode.equalsIgnoreCase("ARROW")) {
                     trap.mode = maple.underground_caves.Test_dungeons.TrapMode.ARROW;
                  }

                  if (mode.equalsIgnoreCase("LAVA")) {
                     trap.mode = maple.underground_caves.Test_dungeons.TrapMode.LAVA;
                  }
               }

               trap.lavaAuto = sec.getBoolean("lavaAuto", trap.lavaAuto);
               trap.lavaDist = sec.getInt("lavaDist", trap.lavaDist);
               trap.arrowIntervalSeconds = sec.getInt("arrowInterval", trap.arrowIntervalSeconds);
               trap.arrowCooldownSeconds = 0;
            }
         }
      }
   }

   private void saveTemplateTrapSettings(String templateName, int localX0, int localY0, int localZ0, Trap trap) {
      if (templateName != null && !templateName.isBlank() && trap != null) {
         File file = this.trapConfigFile();
         YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
         cfg.set("version", 1);
         String var10000 = templateName.trim().toLowerCase(Locale.ROOT);
         String base = "templates." + var10000 + ".traps." + localTrapKey(localX0, localY0, localZ0) + ".";
         cfg.set(base + "mode", trap.mode.name());
         cfg.set(base + "lavaAuto", trap.lavaAuto);
         cfg.set(base + "lavaDist", trap.lavaDist);
         cfg.set(base + "arrowInterval", trap.arrowIntervalSeconds);

         try {
            cfg.save(file);
         } catch (IOException e) {
            if (this.getLogger() != null) {
               this.getLogger().warning("Failed to save traps.yml: " + e.getMessage());
            }
         }

      }
   }

   public DungeonSnapshot getLastDungeon() {
      return this.lastDungeon;
   }

   public DungeonSecrets getDungeonSecrets() {
      return this.dungeonSecrets;
   }

   public DungeonMonsters getDungeonMonsters() {
      return this.dungeonMonsters;
   }

   public ChestLootManager getChestLootManager() {
      return this.chestLootManager;
   }

   public EnchantmentRegistry getEnchantmentRegistry() {
      return this.enchantmentRegistry;
   }

   public PlayerStatsManager getPlayerStatsManager() {
      return this.playerStatsManager;
   }

   public PlayerStatsRuntime getPlayerStatsRuntime() {
      return this.playerStatsRuntime;
   }

   public WeaponManager getWeaponManager() {
      return this.weaponManager;
   }

   public GearManager getGearManager() {
      return this.gearManager;
   }

   public SieveSetManager getSieveSetManager() {
      return this.sieveSetManager;
   }

   public ArcherSetManager getArcherSetManager() {
      return this.archerSetManager;
   }

   public SkeletonSoldierSetManager getSkeletonSoldierSetManager() {
      return this.skeletonSoldierSetManager;
   }

   public SkeletonMasterSetManager getSkeletonMasterSetManager() {
      return this.skeletonMasterSetManager;
   }

   public UndergroundCaveHelmetManager getUndergroundCaveHelmetManager() {
      return this.undergroundCaveHelmetManager;
   }

   public StatsDoubleHelmetManager getStatsDoubleHelmetManager() {
      return this.statsDoubleHelmetManager;
   }

   public CaveSpecialHelmetManager getCaveSpecialHelmetManager() {
      return this.caveSpecialHelmetManager;
   }

   public ImmortalSkullManager getImmortalSkullManager() {
      return this.immortalSkullManager;
   }

   public SecondWindHelmetManager getSecondWindHelmetManager() {
      return this.secondWindHelmetManager;
   }

   public ArrowDistanceHelmetManager getArrowDistanceHelmetManager() {
      return this.arrowDistanceHelmetManager;
   }

   public CavernExplorerSetManager getCavernExplorerSetManager() {
      return this.cavernExplorerSetManager;
   }

   public AdaptiveSetManager getAdaptiveSetManager() {
      return this.adaptiveSetManager;
   }

   public BloodSetManager getBloodSetManager() {
      return this.bloodSetManager;
   }

   public SublimationGearManager getSublimationGearManager() {
      return this.sublimationGearManager;
   }

   public StartRoomReadyManager getStartRoomReadyManager() {
      return this.startRoomReadyManager;
   }

   public PartyManager getPartyManager() {
      return this.partyManager;
   }

   public void handleDungeonDeath(Player player) {
      if (this.dungeonDeathListener != null) {
         this.dungeonDeathListener.handleDungeonDeath(player);
      }

   }

   public DungeonDeathListener getDungeonDeathListener() {
      return this.dungeonDeathListener;
   }

   public boolean isPlayerDungeonGhost(Player player) {
      return player != null && this.dungeonDeathListener != null ? this.dungeonDeathListener.isDungeonGhost(player.getUniqueId()) : false;
   }

   public double getCooldownMultiplier(Player player) {
      if (player == null) {
         return (double)1.0F;
      } else if (!this.isDungeonWorld(player.getWorld().getName())) {
         return (double)1.0F;
      } else if (!this.isDungeonRunStarted(player.getWorld().getName())) {
         return (double)1.0F;
      } else if (this.isPlayerDungeonGhost(player)) {
         return (double)1.0F;
      } else if (this.getDungeonClassIndex(player) != 1) {
         return (double)1.0F;
      } else {
         int lvl = Math.max(1, this.getDungeonClassLevel(player));
         double pct = DungeonClassSkills.mageTimeAccelPercent(lvl);
         return Math.max(0.1, (double)1.0F - pct / (double)100.0F);
      }
   }

   public DungeonScoreboard getDungeonScoreboard() {
      return this.dungeonScoreboard;
   }

   public DungeonBlessingManager getDungeonBlessingManager() {
      return this.dungeonBlessingManager;
   }

   public int getDungeonClassIndex(Player player) {
      if (player == null) {
         return -1;
      } else {
         return this.dungeonScoreboard == null ? -1 : this.dungeonScoreboard.getClassIndexForPlayer(player.getUniqueId(), player.getWorld().getName());
      }
   }

   public int getDungeonClassLevel(Player player) {
      if (player != null && this.dungeonPanelManager != null) {
         int classIndex = this.getDungeonClassIndex(player);
         if (classIndex < 0) {
            return 0;
         } else {
            long classXp = this.dungeonPanelManager.getClassXP(player, classIndex);
            return DungeonLevelXP.getLevelFromCumulativeXP(classXp);
         }
      } else {
         return 0;
      }
   }

   public DungeonPanelManager getDungeonPanelManager() {
      return this.dungeonPanelManager;
   }

   public double getEffectiveMaxHealth(Player player) {
      return this.playerStatsRuntime != null ? this.playerStatsRuntime.getEffectiveMaxHealth(player) : (double)20.0F;
   }

   public double getEffectiveDefense(Player player) {
      return this.playerStatsRuntime != null ? this.playerStatsRuntime.getEffectiveDefense(player) : (double)0.0F;
   }

   public double applyDungeonBlessing(Player player, DungeonBlessingManager.BlessingStat stat, double base) {
      if (player != null && stat != null && this.dungeonBlessingManager != null) {
         World w = player.getWorld();
         if (w != null && this.isDungeonWorld(w.getName())) {
            double floorMultiplier = computeBlessingFloorMultiplier(this.getFloorForDungeonWorld(w.getName()));
            return this.dungeonBlessingManager.apply(w.getName(), stat, base, floorMultiplier);
         } else {
            return base;
         }
      } else {
         return base;
      }
   }

   private static double computeBlessingFloorMultiplier(String floor) {
      if (floor != null && !floor.isBlank()) {
         String f = floor.trim().toUpperCase(Locale.ROOT);
         if (!f.startsWith("F")) {
            return (double)1.0F;
         } else {
            try {
               int n = Integer.parseInt(f.substring(1));
               return n >= 4 ? 1.2 : (double)1.0F;
            } catch (Exception var3) {
               return (double)1.0F;
            }
         }
      } else {
         return (double)1.0F;
      }
   }

   public double getEffectiveVitalityForSelfHeal(Player player) {
      return this.playerStatsRuntime != null ? this.playerStatsRuntime.getEffectiveVitalityForSelfHeal(player) : (double)100.0F;
   }

   public double getEffectiveOutgoingMending(Player healer) {
      return this.playerStatsRuntime != null ? this.playerStatsRuntime.getEffectiveOutgoingMending(healer) : (double)100.0F;
   }

   public double getEffectiveVitalityForHealing(Player player) {
      return this.getEffectiveVitalityForSelfHeal(player);
   }

   public void applySatiatedRegenToVirtualHealth(Player player, double vanillaHealAmount) {
      if (this.playerStatsRuntime != null) {
         this.playerStatsRuntime.applySatiatedRegenToVirtualHealth(player, vanillaHealAmount);
      }
   }

   public void syncVirtualHealthBar(Player player) {
      if (this.playerStatsRuntime != null) {
         this.playerStatsRuntime.syncVirtualHealthBar(player);
      }
   }

   public void setDungeonStartRoomLocation(String worldName, Location loc) {
      if (worldName != null && !worldName.isBlank() && loc != null) {
         this.dungeonStartRoomByWorld.put(worldName, loc.clone());
      }
   }

   public Location getDungeonStartRoomLocation(String worldName) {
      if (worldName == null) {
         return null;
      } else {
         Location l = (Location)this.dungeonStartRoomByWorld.get(worldName);
         return l != null ? l.clone() : null;
      }
   }

   public ItemStack createDungeonMapItemForCurrentWorld(World world) {
      if (world == null) {
         return null;
      } else {
         DungeonSnapshot snap = this.getLastDungeon();
         return snap != null && world.getName().equals(snap.worldName()) ? this.createDungeonMapItem(world, snap) : null;
      }
   }

   public void markDungeonRunStarted(String worldName) {
      if (worldName != null && !worldName.isBlank()) {
         this.dungeonRunStartedMsByWorld.put(worldName, System.currentTimeMillis());
      }
   }

   public boolean isDungeonRunStarted(String worldName) {
      return worldName != null && !worldName.isBlank() ? this.dungeonRunStartedMsByWorld.containsKey(worldName) : false;
   }

   public void clearDungeonRunStarted(String worldName) {
      if (worldName != null && !worldName.isBlank()) {
         this.dungeonRunStartedMsByWorld.remove(worldName);
      }
   }

   public double getWishShieldExtra(UUID playerId) {
      if (playerId == null) {
         return (double)0.0F;
      } else {
         Double v = (Double)this.wishShieldExtraByPlayer.get(playerId);
         return v != null ? Math.max((double)0.0F, v) : (double)0.0F;
      }
   }

   public void setWishShieldExtra(UUID playerId, double extra) {
      if (playerId != null) {
         double v = Math.max((double)0.0F, extra);
         if (v <= (double)0.0F) {
            this.wishShieldExtraByPlayer.remove(playerId);
         } else {
            this.wishShieldExtraByPlayer.put(playerId, v);
         }

      }
   }

   public void consumeWishShieldIfNeeded(Player player, double newCurrentHealth) {
      if (player != null) {
         UUID id = player.getUniqueId();
         if (this.wishShieldExtraByPlayer.containsKey(id)) {
            double baseMax = this.getEffectiveMaxHealth(player);
            if (newCurrentHealth <= baseMax + 1.0E-6) {
               this.wishShieldExtraByPlayer.remove(id);
            }

         }
      }
   }

   public static double computeVirtualHealthBarMax(double maxHealth) {
      return PlayerStatsRuntime.computeVirtualHealthBarMax(maxHealth);
   }

   public RoomRewardManager getRoomRewardManager() {
      return this.roomRewardManager;
   }

   public DungeonRoomRewardDB getDungeonRoomRewardDB() {
      return this.dungeonRoomRewardDB;
   }

   public PlayerDungeonSettingsRemote getPlayerDungeonSettingsRemote() {
      return this.playerDungeonSettingsRemote;
   }

   public ProfessionExperienceRemote getProfessionExperienceRemote() {
      return this.professionExperienceRemote;
   }

   public void syncPartyProfessionExperienceFromRemote(String worldName, List<UUID> partyMemberIds) {
      if (worldName != null && !worldName.isBlank() && partyMemberIds != null && !partyMemberIds.isEmpty() && this.professionExperienceRemote != null) {
         List<UUID> members = List.copyOf(partyMemberIds);
         Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            for(UUID memberId : members) {
               this.professionExperienceRemote.refreshByPlayer(memberId);
            }
         });
      }
   }

   public BossRoomManager getBossRoomManager() {
      return this.bossRoomManager;
   }

   public ExitRoomManager getExitRoomManager() {
      return this.exitRoomManager;
   }

   public boolean isUnlimitedDoorEnabled(String worldName) {
      return worldName != null && this.unlimitedDoorWorlds.contains(worldName);
   }

   public static boolean isAnyCellOnMainPath(SkyBlockDungeon dungeon, Set<Integer> packedCells) {
      if (dungeon != null && packedCells != null && !packedCells.isEmpty()) {
         for(Integer packed : packedCells) {
            int cellX = packed >> 16;
            int cellY = packed & '\uffff';
            SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(cellX, cellY);
            if (r != null && r.isCritical()) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static List<int[]> getCriticalPath(SkyBlockDungeon dungeon) {
      if (dungeon == null) {
         return List.of();
      } else {
         int[] entrance = null;
         int[] exit = null;
         int maxX = 0;
         int maxY = 0;

         for(SkyBlockDungeon.DungeonDoor d : dungeon.getAllDoors()) {
            maxX = Math.max(maxX, Math.max(d.x1(), d.x2()));
            maxY = Math.max(maxY, Math.max(d.y1(), d.y2()));
         }

         int width = maxX + 2;
         int height = maxY + 2;

         for(int y = 0; y < height; ++y) {
            for(int x = 0; x < width; ++x) {
               SkyBlockDungeon.DungeonRoom r = dungeon.getRoom(x, y);
               if (r != null && r.isCritical()) {
                  if (r.getRoomType() == DungeonRoomType.ENTRANCE) {
                     entrance = new int[]{x, y};
                  }

                  if (r.getRoomType() == DungeonRoomType.EXIT) {
                     exit = new int[]{x, y};
                  }
               }
            }
         }

         if (entrance != null && exit != null) {
            int[][] dirs = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            int n = width * height;
            int[] parent = new int[n];
            Arrays.fill(parent, -1);
            boolean[] visited = new boolean[n];
            ArrayDeque<int[]> q = new ArrayDeque();
            q.add(entrance);
            visited[entrance[0] + entrance[1] * width] = true;

            while(!q.isEmpty()) {
               int[] cur = (int[])q.poll();
               if (cur[0] == exit[0] && cur[1] == exit[1]) {
                  break;
               }

               for(int[] d : dirs) {
                  int nx = cur[0] + d[0];
                  int ny = cur[1] + d[1];
                  if (nx >= 0 && ny >= 0 && nx < width && ny < height) {
                     SkyBlockDungeon.DungeonRoom nr = dungeon.getRoom(nx, ny);
                     if (nr != null && nr.isCritical() && dungeon.isConnected(cur[0], cur[1], nx, ny)) {
                        int ni = nx + ny * width;
                        if (!visited[ni]) {
                           visited[ni] = true;
                           parent[ni] = cur[0] + cur[1] * width;
                           q.add(new int[]{nx, ny});
                        }
                     }
                  }
               }
            }

            int ti = exit[0] + exit[1] * width;
            if (!visited[ti]) {
               return List.of();
            } else {
               List<int[]> path = new ArrayList();

               for(int cur = ti; cur != -1; cur = parent[cur]) {
                  path.add(new int[]{cur % width, cur / width});
               }

               Collections.reverse(path);
               return path;
            }
         } else {
            return List.of();
         }
      }
   }

   public PuzzleRoom08BlazeShoot getPuzzleRoom08() {
      return this.puzzleRoom08;
   }

   public boolean giveDungeonMap(Player player) {
      if (player == null) {
         return false;
      } else {
         DungeonSnapshot snapshot = this.getLastDungeon();
         if (snapshot == null) {
            return false;
         } else {
            ItemStack map = this.createDungeonMapItem(player.getWorld(), snapshot);
            if (map == null) {
               return false;
            } else {
               player.getInventory().addItem(new ItemStack[]{map});
               return true;
            }
         }
      }
   }

   public void registerDebugDoor(Location base, BlockFace facing) {
      if (base != null && facing != null) {
         World world = base.getWorld();
         if (world != null) {
            if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH || facing == BlockFace.EAST || facing == BlockFace.WEST) {
               this.debugDoors.add(new DoorDebug(world.getName(), base.getBlockX(), base.getBlockY(), base.getBlockZ(), facing));
            }
         }
      }
   }

   public void registerPuzzleRoom06(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
      this.puzzleRoom06.registerPuzzleRoom06(world, roomOrigin, rotation, sizeX, sizeZ);
   }

   public void registerPuzzleRoom01(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
      this.puzzleRoom01.registerPuzzleRoom01(world, roomOrigin, rotation, sizeX, sizeZ);
   }

   public void registerPuzzleRoom05(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
      this.puzzleRoom05.registerPuzzleRoom05(world, roomOrigin, rotation, sizeX, sizeZ);
   }

   public void registerPuzzleRoom03(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
      this.puzzleRoom03.registerPuzzleRoom03(world, roomOrigin, rotation, sizeX, sizeZ);
   }

   public void registerPuzzleRoom04(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
      this.puzzleRoom04.registerPuzzleRoom04(world, roomOrigin, rotation, sizeX, sizeZ);
   }

   public void registerPuzzleRoom07(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
      this.puzzleRoom07.registerPuzzleRoom07(world, roomOrigin, rotation, sizeX, sizeZ);
   }

   public void registerPuzzleRoom08(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
      this.puzzleRoom08.registerPuzzleRoom08(world, roomOrigin, rotation, sizeX, sizeZ);
   }

   public void registerStartRoomNpc(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ, List<BlockFace> desiredFaces) {
      if (this.startRoomNpc != null) {
         this.startRoomNpc.registerStartRoomNpc(world, roomOrigin, rotation, sizeX, sizeZ, desiredFaces);
      }

   }

   public void reloadPuzzleRoom05Config() {
      this.puzzleRoom05.reloadConfig();
   }

   private boolean tryOpenDebugDoor(World world, Location loc) {
      if (world != null && loc != null) {
         int bx = loc.getBlockX();
         int by = loc.getBlockY();
         int bz = loc.getBlockZ();
         String worldName = world.getName();

         for(DoorDebug door : List.copyOf(this.debugDoors)) {
            if (worldName.equals(door.worldName()) && this.isDebugDoorInterior(door, bx, by, bz)) {
               this.openDebugDoor(world, door);
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isDebugDoorInterior(DoorDebug door, int bx, int by, int bz) {
      int baseX = door.baseX();
      int baseY = door.baseY();
      int baseZ = door.baseZ();
      BlockFace facing = door.facing();
      if (facing != BlockFace.EAST && facing != BlockFace.WEST) {
         if (bz >= baseZ - 1 && bz <= baseZ + 1) {
            if (bx >= baseX - 2 && bx <= baseX + 2) {
               return by >= baseY - 1 && by <= baseY + 4;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else if (bx >= baseX - 1 && bx <= baseX + 1) {
         if (bz >= baseZ - 2 && bz <= baseZ + 2) {
            return by >= baseY - 1 && by <= baseY + 4;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void openDebugDoor(World world, DoorDebug door) {
      DungeonSecrets secrets = this.getDungeonSecrets();
      if (secrets != null) {
         int baseX = door.baseX();
         int baseY = door.baseY();
         int baseZ = door.baseZ();
         BlockFace facing = door.facing();
         if (facing != BlockFace.EAST && facing != BlockFace.WEST) {
            for(int z = baseZ - 1; z <= baseZ + 1; ++z) {
               for(int x = baseX - 2; x <= baseX + 2; ++x) {
                  for(int y = baseY - 1; y <= baseY + 4; ++y) {
                     secrets.triggerCoalBlockFall(world, x, y, z);
                  }
               }
            }

         } else {
            for(int x = baseX - 1; x <= baseX + 1; ++x) {
               for(int z = baseZ - 2; z <= baseZ + 2; ++z) {
                  for(int y = baseY - 1; y <= baseY + 4; ++y) {
                     secrets.triggerCoalBlockFall(world, x, y, z);
                  }
               }
            }

         }
      }
   }

   private ItemStack createDungeonMapItem(World world, DungeonSnapshot snapshot) {
      if (world != null && snapshot != null) {
         MapView view = Bukkit.createMap(world);

         for(MapRenderer r : List.copyOf(view.getRenderers())) {
            view.removeRenderer(r);
         }

         this.configureDungeonMapView(view, snapshot);
         view.setTrackingPosition(false);
         view.setUnlimitedTracking(false);
         view.setLocked(false);
         view.addRenderer(new DungeonLayoutRenderer(snapshot));
         view.addRenderer(new DungeonCompletionRenderer(snapshot));
         view.addRenderer(new DungeonPointerRenderer(snapshot));
         ItemStack map = new ItemStack(Material.FILLED_MAP, 1);
         ItemMeta var6 = map.getItemMeta();
         if (var6 instanceof MapMeta) {
            MapMeta meta = (MapMeta)var6;
            meta.setMapView(view);
            map.setItemMeta(meta);
         }

         return map;
      } else {
         return null;
      }
   }

   private void configureDungeonMapView(MapView view, DungeonSnapshot snapshot) {
      if (view == null || snapshot == null) {
         return;
      }

      int roomScale = Math.max(1, snapshot.roomScale());
      int widthBlocks = Math.max(1, snapshot.width()) * roomScale;
      int heightBlocks = Math.max(1, snapshot.height()) * roomScale;
      int maxSpan = Math.max(widthBlocks, heightBlocks) + roomScale * 2;
      int centerX = snapshot.originX() + widthBlocks / 2;
      int centerZ = snapshot.originZ() + heightBlocks / 2;

      view.setCenterX(centerX);
      view.setCenterZ(centerZ);
      view.setScale(this.chooseScaleForSpan(maxSpan));
   }

   private MapView.Scale chooseScaleForSpan(int maxSpan) {
      if (maxSpan <= 128) {
         return MapView.Scale.CLOSEST;
      } else if (maxSpan <= 256) {
         return MapView.Scale.CLOSE;
      } else if (maxSpan <= 512) {
         return MapView.Scale.NORMAL;
      } else if (maxSpan <= 1024) {
         return MapView.Scale.FAR;
      } else {
         return MapView.Scale.FARTHEST;
      }
   }

   private static int parseExpType(String s) {
      if (s != null && !s.isBlank()) {
         switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "cave":
            case "underground_caves":
            case "地底洞窟":
               return 0;
            case "healer":
            case "治療師":
               return 1;
            case "mage":
            case "法師":
               return 2;
            case "warrior":
            case "戰士":
               return 3;
            case "archer":
            case "弓箭手":
               return 4;
            case "tank":
            case "坦克":
               return 5;
            default:
               return -1;
         }
      } else {
         return -1;
      }
   }

   private static String expTypeName(int expIndex) {
      String var10000;
      switch (expIndex) {
         case 0 -> var10000 = "地底洞窟";
         case 1 -> var10000 = "治療師";
         case 2 -> var10000 = "法師";
         case 3 -> var10000 = "戰士";
         case 4 -> var10000 = "弓箭手";
         case 5 -> var10000 = "坦克";
         default -> var10000 = "未知";
      }

      return var10000;
   }

   private static List<String> filterStartsWith(String prefix, List<String> options) {
      if (options != null && !options.isEmpty()) {
         if (prefix == null) {
            prefix = "";
         }

         String lower = prefix.toLowerCase(Locale.ROOT);
         return options.stream().filter((s) -> s != null && s.toLowerCase(Locale.ROOT).startsWith(lower)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
      } else {
         return Collections.emptyList();
      }
   }

   private void saveDefaultConfigIfMissing(String resourcePath) {
      File out = new File(this.getDataFolder(), resourcePath);
      if (!out.exists()) {
         this.saveResource(resourcePath, false);
      }

   }

   public void onEnable() {
      instance = this;
      this.startRoomNpc = new StartRoomNpc(this);
      this.startRoomNpc.init();
      this.puzzleRoom05.onEnable();
      this.dungeonSecrets = new DungeonSecrets(this);
      this.dungeonBlessingManager = new DungeonBlessingManager();
      this.dungeonRoomRewardDB = new DungeonRoomRewardDB(this.getDataFolder(), this.getLogger());
      this.professionExperienceRemote = new ProfessionExperienceRemote(this.getDataFolder(), this.getLogger());
      if (!(new File(this.getDataFolder(), "database.yml")).exists()) {
         this.saveResource("database.yml", false);
      }

      this.playerDungeonSettingsRemote = new PlayerDungeonSettingsRemote(this.getDataFolder(), this.getLogger());
      Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
         this.professionExperienceRemote.ensureRemoteTable();
         this.playerDungeonSettingsRemote.ensureTable();
      });
      this.roomRewardManager = new RoomRewardManager(this, this.dungeonRoomRewardDB);
      this.chestLootManager = new ChestLootManager(this);
      this.dungeonMonsters = new DungeonMonsters(this);
      this.dungeonMonsters.setRoomRewardManager(this.roomRewardManager);
      this.playerAutoReadyDB = new PlayerAutoReadyDB(this.getLogger());
      this.partyManager = new PartyManager();
      this.partyManager.setDungeonSettingsRemote(this.playerDungeonSettingsRemote);
      this.partyManager.setPlayerAutoReadyDB(this.playerAutoReadyDB);
      this.dungeonDeathListener = new DungeonDeathListener(this);
      Bukkit.getPluginManager().registerEvents(this.dungeonDeathListener, this);
      this.dungeonScoreboard = new DungeonScoreboard(this, this.partyManager);
      this.dungeonScoreboard.start();
      this.startRoomReadyManager = new StartRoomReadyManager(this, this.partyManager, this.dungeonScoreboard);
      DungeonCommand dungeonCommand = new DungeonCommand(this.dungeonSecrets, this.dungeonMonsters, this.partyManager);
      DungeonTemplateCommand dungeonTemplateCommand = new DungeonTemplateCommand(this.dungeonSecrets, this.dungeonMonsters);
      DungeonMapCommand dungeonMapCommand = new DungeonMapCommand(this);
      PluginCommand genDungeon = this.getCommand("gen_dungeon");
      if (genDungeon != null) {
         genDungeon.setExecutor(dungeonCommand);
         genDungeon.setTabCompleter(dungeonCommand);
      } else {
         this.getLogger().warning("Command not found in plugin.yml: gen_dungeon");
      }

      PluginCommand genRoom = this.getCommand("gen_room");
      if (genRoom != null) {
         genRoom.setExecutor(dungeonCommand);
         genRoom.setTabCompleter(dungeonCommand);
      } else {
         this.getLogger().warning("Command not found in plugin.yml: gen_room");
      }

      PluginCommand dungeonTemplate = this.getCommand("dungeon_template");
      if (dungeonTemplate != null) {
         dungeonTemplate.setExecutor(dungeonTemplateCommand);
         dungeonTemplate.setTabCompleter(dungeonTemplateCommand);
      } else {
         this.getLogger().warning("Command not found in plugin.yml: dungeon_template");
      }

      PluginCommand dungeonMap = this.getCommand("dungeon_map");
      if (dungeonMap != null) {
         dungeonMap.setExecutor(dungeonMapCommand);
      } else {
         this.getLogger().warning("Command not found in plugin.yml: dungeon_map");
      }

      PluginCommand unlimitedDoorCmd = this.getCommand("dungeon_unlimited_door");
      if (unlimitedDoorCmd != null) {
         unlimitedDoorCmd.setExecutor(new UnlimitedDoorCommand(this));
      }

      PluginCommand exitRoomCompleteCmd = this.getCommand("dungeon_exit_room_complete");
      if (exitRoomCompleteCmd != null) {
         exitRoomCompleteCmd.setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) {
               sender.sendMessage(Component.text("僅玩家可使用此指令。", NamedTextColor.RED));
               return true;
            } else if (args.length < 2) {
               sender.sendMessage(Component.text("用法: /dungeon_exit_room_complete <cellX> <cellY>", NamedTextColor.GRAY));
               return true;
            } else {
               int cellX;
               int cellY;
               try {
                  cellX = Integer.parseInt(args[0]);
                  cellY = Integer.parseInt(args[1]);
               } catch (NumberFormatException var9) {
                  sender.sendMessage(Component.text("請輸入有效的 cellX、cellY 數字。", NamedTextColor.RED));
                  return true;
               }

               if (this.exitRoomManager != null && this.exitRoomManager.forceCompleteExitRoom(p.getWorld(), cellX, cellY)) {
                  sender.sendMessage(Component.text("已將 exit 房 (" + cellX + "," + cellY + ") 設為通關並觸發完成流程。", NamedTextColor.GREEN));
               } else {
                  sender.sendMessage(Component.text("該房不是已註冊的 exit 房，或世界無效。", NamedTextColor.RED));
               }

               return true;
            }
         });
      }

      List<String> lastBossChestIds = List.of("wood", "gold", "diamond_left", "diamond_right", "emerald_left", "emerald_right");
      TabCompleter lastBossTabCompleter = (sender, command, alias, args) -> {
         String name = command != null ? command.getName() : null;
         if (name == null) {
            return List.of();
         } else if (!name.equalsIgnoreCase("dungeon_last_boss_chests_dump") && !name.equalsIgnoreCase("dungeon_go_last_boss")) {
            if (name.equalsIgnoreCase("dungeon_last_boss_chests_set")) {
               String prefix = args.length > 0 ? args[args.length - 1] : "";
               if (args.length == 1) {
                  return filterPrefixList(lastBossChestIds, prefix);
               } else {
                  if (sender instanceof Player) {
                     Player p = (Player)sender;
                     if (args.length >= 2 && args.length <= 4) {
                        DungeonSnapshot snapshot = this.getLastDungeon();
                        if (snapshot != null && p.getWorld().getName().equals(snapshot.worldName())) {
                           BossRoomManager bm = this.getBossRoomManager();
                           if (bm != null) {
                              Location origin = bm.getLastBossRoomOrigin(p.getWorld().getName(), snapshot.floor());
                              if (origin != null) {
                                 double lx = p.getLocation().getX() - origin.getX();
                                 double ly = p.getLocation().getY() - origin.getY();
                                 double lz = p.getLocation().getZ() - origin.getZ();
                                 String cur = args.length == 2 ? String.format("%.2f", lx) : (args.length == 3 ? String.format("%.2f", ly) : String.format("%.2f", lz));
                                 return filterPrefixList(List.of(cur), prefix);
                              }
                           }
                        }
                     }
                  }

                  if (args.length == 5) {
                     return filterPrefixList(List.of("0", "1", "2", "3"), prefix);
                  } else {
                     return args.length == 6 ? filterPrefixList(List.of("small", "normal", "0.5", "1.0", "1.5"), prefix) : List.of();
                  }
               }
            } else {
               return List.of();
            }
         } else {
            return List.of();
         }
      };
      PluginCommand lastBossChestsDumpCmd = this.getCommand("dungeon_last_boss_chests_dump");
      if (lastBossChestsDumpCmd != null) {
         lastBossChestsDumpCmd.setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player p) {
               if (this.dungeonMonsters != null) {
                  this.dungeonMonsters.dumpLastBossChestStands(p);
               }

               return true;
            } else {
               sender.sendMessage(Component.text("僅玩家可使用此指令。", NamedTextColor.RED));
               return true;
            }
         });
         lastBossChestsDumpCmd.setTabCompleter(lastBossTabCompleter);
      }

      PluginCommand lastBossChestsSetCmd = this.getCommand("dungeon_last_boss_chests_set");
      if (lastBossChestsSetCmd != null) {
         lastBossChestsSetCmd.setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player p) {
               if (this.dungeonMonsters == null) {
                  return true;
               } else if (args.length < 5) {
                  sender.sendMessage(Component.text("用法: /dungeon_last_boss_chests_set <id> <localX> <localY> <localZ> <yaw4> [small|normal|scale數字]", NamedTextColor.GRAY));
                  sender.sendMessage(Component.text("id: wood, gold, diamond_left, diamond_right, emerald_left, emerald_right", NamedTextColor.DARK_GRAY));
                  sender.sendMessage(Component.text("大小: small / normal，或高版本可填數字如 0.5、1.0、1.5（使用 GENERIC_SCALE）", NamedTextColor.DARK_GRAY));
                  return true;
               } else {
                  double lx;
                  double ly;
                  double lz;
                  int yaw4;
                  try {
                     lx = Double.parseDouble(args[1]);
                     ly = Double.parseDouble(args[2]);
                     lz = Double.parseDouble(args[3]);
                     yaw4 = Integer.parseInt(args[4]);
                  } catch (NumberFormatException var17) {
                     sender.sendMessage(Component.text("請輸入有效的數字（localX/Y/Z 可小數）。", NamedTextColor.RED));
                     return true;
                  }

                  boolean small = true;
                  Double scale = null;
                  if (args.length > 5) {
                     String sizeArg = args[5];
                     if ("normal".equalsIgnoreCase(sizeArg)) {
                        small = false;
                     } else if ("small".equalsIgnoreCase(sizeArg)) {
                        small = true;
                     } else {
                        try {
                           scale = Double.parseDouble(sizeArg);
                           if (scale <= (double)0.0F) {
                              scale = null;
                           } else {
                              small = false;
                           }
                        } catch (NumberFormatException var18) {
                           small = !"normal".equalsIgnoreCase(sizeArg);
                        }
                     }
                  }

                  boolean ok = this.dungeonMonsters.setLastBossChestStand(p.getWorld(), args[0], lx, ly, lz, yaw4, small, scale);
                  String sizeDesc = scale != null ? "scale=" + scale : "small=" + small;
                  if (ok) {
                     sender.sendMessage(Component.text("已更新箱子 " + args[0] + " 位置=(" + lx + "," + ly + "," + lz + ") yaw4=" + yaw4 + " " + sizeDesc, NamedTextColor.GREEN));
                  } else {
                     sender.sendMessage(Component.text("找不到該 id 的箱子盔甲架，請確認在 last_boss 房內且第二隻 BOSS 已死過。", NamedTextColor.RED));
                  }

                  return true;
               }
            } else {
               sender.sendMessage(Component.text("僅玩家可使用此指令。", NamedTextColor.RED));
               return true;
            }
         });
         lastBossChestsSetCmd.setTabCompleter(lastBossTabCompleter);
      }

      PluginCommand goLastBossCmd = this.getCommand("dungeon_go_last_boss");
      if (goLastBossCmd != null) {
         goLastBossCmd.setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player p) {
               DungeonSnapshot snapshot = this.getLastDungeon();
               if (snapshot != null && p.getWorld().getName().equals(snapshot.worldName())) {
                  BossRoomManager bm = this.getBossRoomManager();
                  if (bm == null) {
                     sender.sendMessage(Component.text("無法取得 BOSS 房資訊。", NamedTextColor.RED));
                     return true;
                  } else {
                     Location dest = bm.getLastBossTeleportTarget(snapshot.worldName(), snapshot.floor());
                     if (dest == null) {
                        sender.sendMessage(Component.text("找不到 last_boss_room 傳送點。", NamedTextColor.RED));
                        return true;
                     } else {
                        p.teleport(dest);
                        sender.sendMessage(Component.text("已傳送至 last_boss_room。", NamedTextColor.GREEN));
                        return true;
                     }
                  }
               } else {
                  sender.sendMessage(Component.text("請先在地牢世界內使用，或尚未生成地牢。", NamedTextColor.RED));
                  return true;
               }
            } else {
               sender.sendMessage(Component.text("僅玩家可使用此指令。", NamedTextColor.RED));
               return true;
            }
         });
         goLastBossCmd.setTabCompleter(lastBossTabCompleter);
      }

      UndergroundState panelOwner = UndergroundState.getInstance();
      this.dungeonPanelManager = panelOwner != null ? panelOwner.getDungeonPanelManager() : null;

      PluginCommand dungeonTeamScoreCmd = this.getCommand("dungeon_team_score");
      if (dungeonTeamScoreCmd != null) {
         DungeonTeamScoreCommand teamScoreExecutor = new DungeonTeamScoreCommand(this);
         dungeonTeamScoreCmd.setExecutor(teamScoreExecutor);
         dungeonTeamScoreCmd.setTabCompleter(teamScoreExecutor);
      }

      PluginCommand dungeonCmd = this.getCommand("dungeon");
      if (dungeonCmd != null) {
         dungeonCmd.setExecutor((sender, cmd, label, args) -> {
            if (args.length >= 4 && "addxp".equalsIgnoreCase(args[0])) {
               if (!sender.hasPermission("test_dungeons.admin")) {
                  sender.sendMessage(Component.text("你沒有權限使用此指令。", NamedTextColor.RED));
                  return true;
               } else {
                  Player targetPlayer = Bukkit.getPlayer(args[1]);
                  UUID targetUuid = targetPlayer != null ? targetPlayer.getUniqueId() : null;
                  if (targetUuid == null) {
                     try {
                        targetUuid = UUID.fromString(args[1]);
                     } catch (IllegalArgumentException var12) {
                        sender.sendMessage(Component.text("請指定在線玩家或有效的 UUID：/dungeon addxp <玩家|UUID> <類型> <數值>", NamedTextColor.RED));
                        return true;
                     }
                  }

                  int expIndex = parseExpType(args[2]);
                  if (expIndex < 0) {
                     sender.sendMessage(Component.text("類型請填：cave|healer|mage|warrior|archer|tank 或 地底洞窟|治療師|法師|戰士|弓箭手|坦克", NamedTextColor.RED));
                     return true;
                  } else {
                     long amount;
                     try {
                        amount = Long.parseLong(args[3]);
                     } catch (NumberFormatException var11) {
                        sender.sendMessage(Component.text("請輸入有效數值。", NamedTextColor.RED));
                        return true;
                     }

                     if (amount <= 0L) {
                        sender.sendMessage(Component.text("數值必須大於 0。", NamedTextColor.RED));
                        return true;
                     } else if (this.professionExperienceRemote == null) {
                        sender.sendMessage(Component.text("遠端經驗服務未就緒。", NamedTextColor.RED));
                        return true;
                     } else {
                        this.professionExperienceRemote.addExperience(targetUuid, expIndex, amount);
                        String typeName = expTypeName(expIndex);
                        sender.sendMessage(((TextComponent)((TextComponent)Component.text("已向 MySQL 增加 ", NamedTextColor.GREEN).append(Component.text(DungeonClassSkills.formatNumber(amount), NamedTextColor.YELLOW))).append(Component.text(" " + typeName + " 經驗，目標：", NamedTextColor.GREEN))).append(Component.text(args[1], NamedTextColor.WHITE)));
                        return true;
                     }
                  }
               }
            } else if (args.length >= 3 && "mob".equalsIgnoreCase(args[0]) && "summon".equalsIgnoreCase(args[1])) {
               if (!sender.hasPermission("test_dungeons.admin")) {
                  sender.sendMessage(Component.text("你沒有權限使用此指令。", NamedTextColor.RED));
                  return true;
               } else if (sender instanceof Player) {
                  Player p = (Player)sender;
                  if (this.dungeonMonsters == null) {
                     sender.sendMessage(Component.text("怪物系統未初始化。", NamedTextColor.RED));
                     return true;
                  } else {
                     String typeInput = args[2];
                     String variantInput = null;
                     boolean target = true;
                     if (args.length >= 4) {
                        String a4 = args[3];
                        if (!"target".equalsIgnoreCase(a4) && !"star".equalsIgnoreCase(a4)) {
                           if (!"normal".equalsIgnoreCase(a4) && !"notarget".equalsIgnoreCase(a4)) {
                              variantInput = a4;
                           } else {
                              target = false;
                           }
                        } else {
                           target = true;
                        }
                     }

                     if (args.length >= 5) {
                        String a5 = args[4];
                        if (!"target".equalsIgnoreCase(a5) && !"star".equalsIgnoreCase(a5)) {
                           if ("normal".equalsIgnoreCase(a5) || "notarget".equalsIgnoreCase(a5)) {
                              target = false;
                           }
                        } else {
                           target = true;
                        }
                     }

                     DungeonMonsters.ManualSummonResult result = this.dungeonMonsters.summonMobForPlayer(p, typeInput, variantInput, target);
                     sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                     return true;
                  }
               } else {
                  sender.sendMessage(Component.text("僅玩家可使用召喚。", NamedTextColor.RED));
                  return true;
               }
            } else if (args.length >= 2 && "miniboss".equalsIgnoreCase(args[0]) && "summon".equalsIgnoreCase(args[1])) {
               if (!sender.hasPermission("test_dungeons.admin")) {
                  sender.sendMessage(Component.text("你沒有權限使用此指令。", NamedTextColor.RED));
                  return true;
               } else if (sender instanceof Player) {
                  Player p = (Player)sender;
                  if (this.dungeonMonsters == null) {
                     sender.sendMessage(Component.text("怪物系統未初始化。", NamedTextColor.RED));
                     return true;
                  }

                  String variantInput = null;
                  boolean target = true;
                  if (args.length >= 3) {
                     String a3 = args[2];
                     if ("target".equalsIgnoreCase(a3) || "star".equalsIgnoreCase(a3)) {
                        target = true;
                     } else if ("normal".equalsIgnoreCase(a3) || "notarget".equalsIgnoreCase(a3)) {
                        target = false;
                     } else {
                        variantInput = a3;
                     }
                  }

                  if (args.length >= 4) {
                     String a4 = args[3];
                     if ("target".equalsIgnoreCase(a4) || "star".equalsIgnoreCase(a4)) {
                        target = true;
                     } else if ("normal".equalsIgnoreCase(a4) || "notarget".equalsIgnoreCase(a4)) {
                        target = false;
                     }
                  }

                  DungeonMonsters.ManualSummonResult result = this.dungeonMonsters.summonMobForPlayer(p, "MINIBOSS", variantInput, target);
                  sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                  return true;
               } else {
                  sender.sendMessage(Component.text("僅玩家可使用召喚。", NamedTextColor.RED));
                  return true;
               }
            } else if (args.length >= 3 && "boss".equalsIgnoreCase(args[0]) && "summon".equalsIgnoreCase(args[1])) {
               if (!sender.hasPermission("test_dungeons.admin")) {
                  sender.sendMessage(Component.text("你沒有權限使用此指令。", NamedTextColor.RED));
                  return true;
               } else if (sender instanceof Player) {
                  Player p = (Player)sender;
                  if (this.dungeonMonsters == null) {
                     sender.sendMessage(Component.text("怪物系統未初始化。", NamedTextColor.RED));
                     return true;
                  }

                  String inputTag = args[2] != null ? args[2].trim() : "";
                  String tag;
                  if ("1".equalsIgnoreCase(inputTag) || "f1_1".equalsIgnoreCase(inputTag) || "F1_last_boss_1".equalsIgnoreCase(inputTag)) {
                     tag = "F1_last_boss_1";
                  } else if ("2".equalsIgnoreCase(inputTag) || "f1_2".equalsIgnoreCase(inputTag) || "F1_last_boss_2".equalsIgnoreCase(inputTag)) {
                     tag = "F1_last_boss_2";
                  } else {
                     sender.sendMessage(Component.text("BOSS 代號請填：1 或 2", NamedTextColor.RED));
                     return true;
                  }

                  boolean evasive = "F1_last_boss_1".equalsIgnoreCase(tag);
                  if (args.length >= 4) {
                     String mode = args[3];
                     if ("evasive".equalsIgnoreCase(mode) || "run".equalsIgnoreCase(mode)) evasive = true;
                     if ("aggressive".equalsIgnoreCase(mode) || "attack".equalsIgnoreCase(mode)) evasive = false;
                  }

                  org.bukkit.entity.LivingEntity spawned = this.dungeonMonsters.spawnLastBossBossAt(p.getWorld(), p.getLocation(), tag, evasive);
                  if (spawned == null) {
                     sender.sendMessage(Component.text("召喚失敗：請確認 monsters.yml 的 last_boss_f1 設定存在。", NamedTextColor.RED));
                     return true;
                  }
                  sender.sendMessage(Component.text("已召喚 " + tag + "（" + (evasive ? "逃避型" : "主動攻擊型") + "）", NamedTextColor.GREEN));
                  return true;
               } else {
                  sender.sendMessage(Component.text("僅玩家可使用召喚。", NamedTextColor.RED));
                  return true;
               }
            } else if (sender instanceof Player) {
               Player p = (Player)sender;
               if (args.length >= 4 && "give".equalsIgnoreCase(args[0]) && "xp".equalsIgnoreCase(args[1]) && "dungeon".equalsIgnoreCase(args[2])) {
                  long amount;
                  try {
                     amount = Long.parseLong(args[3]);
                  } catch (NumberFormatException var13) {
                     sender.sendMessage(Component.text("請輸入有效數值：/dungeon give xp dungeon <數值>", NamedTextColor.RED));
                     return true;
                  }

                  if (amount <= 0L) {
                     sender.sendMessage(Component.text("數值必須大於 0。", NamedTextColor.RED));
                     return true;
                  } else {
                     long newTotal = this.dungeonPanelManager.addCaveXP(p, amount);
                     sender.sendMessage(((TextComponent)((TextComponent)Component.text("已增加 ", NamedTextColor.GREEN).append(Component.text(DungeonClassSkills.formatNumber(amount), NamedTextColor.YELLOW))).append(Component.text(" 地底洞窟經驗，當前累積：", NamedTextColor.GREEN))).append(Component.text(DungeonClassSkills.formatNumber(newTotal), NamedTextColor.YELLOW)));
                     return true;
                  }
               } else {
                  sender.sendMessage(Component.text("用法：/dungeon give xp dungeon <數值> | /dungeon addxp <玩家|UUID> <類型> <數值> | /dungeon mob summon <type> [variant] [target|normal] | /dungeon miniboss summon [variant] [target|normal] | /dungeon boss summon <1|2> [evasive|aggressive]", NamedTextColor.GRAY));
                  return true;
               }
            } else {
               sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
               return true;
            }
         });
         dungeonCmd.setTabCompleter((sender, command, alias, args) -> {
            if (args != null && args.length != 0) {
               if (args.length == 1) {
                  return filterStartsWith(args[0], Arrays.asList("give", "addxp", "mob", "miniboss", "boss"));
               } else if ("give".equalsIgnoreCase(args[0])) {
                  if (args.length == 2) {
                     return filterStartsWith(args[1], Collections.singletonList("xp"));
                  } else {
                     return args.length == 3 ? filterStartsWith(args[2], Collections.singletonList("dungeon")) : Collections.emptyList();
                  }
               } else {
                  if ("addxp".equalsIgnoreCase(args[0])) {
                     if (!sender.hasPermission("test_dungeons.admin")) {
                        return Collections.emptyList();
                     }

                     if (args.length == 2) {
                        String partial = args[1].toLowerCase(Locale.ROOT);
                        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter((n) -> n.toLowerCase(Locale.ROOT).startsWith(partial)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
                     }

                     if (args.length == 3) {
                        List<String> types = Arrays.asList("cave", "healer", "mage", "warrior", "archer", "tank", "地底洞窟", "治療師", "法師", "戰士", "弓箭手", "坦克");
                        return filterStartsWith(args[2], types);
                     }
                  }

                  if ("mob".equalsIgnoreCase(args[0])) {
                     if (!sender.hasPermission("test_dungeons.admin")) {
                        return Collections.emptyList();
                     }

                     if (args.length == 2) {
                        return filterStartsWith(args[1], Collections.singletonList("summon"));
                     }

                     if (!"summon".equalsIgnoreCase(args[1])) {
                        return Collections.emptyList();
                     }

                     if (args.length == 3) {
                        List<String> types = this.dungeonMonsters != null ? this.dungeonMonsters.listMonsterTypesForSummon() : Collections.emptyList();
                        return filterStartsWith(args[2], types);
                     }

                     if (args.length == 4) {
                        LinkedHashSet<String> options = new LinkedHashSet();
                        if (this.dungeonMonsters != null) {
                           options.addAll(this.dungeonMonsters.listMonsterVariantsForSummon(args[2]));
                        }

                        options.add("target");
                        options.add("normal");
                        return filterStartsWith(args[3], new ArrayList(options));
                     }

                     if (args.length == 5) {
                        return filterStartsWith(args[4], Arrays.asList("target", "normal"));
                     }
                  }

                  if ("miniboss".equalsIgnoreCase(args[0])) {
                     if (!sender.hasPermission("test_dungeons.admin")) {
                        return Collections.emptyList();
                     }

                     if (args.length == 2) {
                        return filterStartsWith(args[1], Collections.singletonList("summon"));
                     }

                     if (!"summon".equalsIgnoreCase(args[1])) {
                        return Collections.emptyList();
                     }

                     if (args.length == 3) {
                        LinkedHashSet<String> options = new LinkedHashSet();
                        if (this.dungeonMonsters != null) {
                           options.addAll(this.dungeonMonsters.listMonsterVariantsForSummon("MINIBOSS"));
                        }
                        options.add("target");
                        options.add("normal");
                        return filterStartsWith(args[2], new ArrayList(options));
                     }

                     if (args.length == 4) {
                        return filterStartsWith(args[3], Arrays.asList("target", "normal"));
                     }
                  }

                  if ("boss".equalsIgnoreCase(args[0])) {
                     if (!sender.hasPermission("test_dungeons.admin")) {
                        return Collections.emptyList();
                     }

                     if (args.length == 2) {
                        return filterStartsWith(args[1], Collections.singletonList("summon"));
                     }

                     if (!"summon".equalsIgnoreCase(args[1])) {
                        return Collections.emptyList();
                     }

                     if (args.length == 3) {
                        return filterStartsWith(args[2], Arrays.asList("1", "2", "F1_last_boss_1", "F1_last_boss_2"));
                     }

                     if (args.length == 4) {
                        return filterStartsWith(args[3], Arrays.asList("evasive", "aggressive"));
                     }
                  }

                  return Collections.emptyList();
               }
            } else {
               return Arrays.asList("give", "addxp", "mob", "miniboss", "boss");
            }
         });
      }

      this.bossRoomManager = new BossRoomManager(this, this.dungeonSecrets);
      Bukkit.getPluginManager().registerEvents(this.bossRoomManager, this);
      this.exitRoomManager = new ExitRoomManager(this);
      Bukkit.getPluginManager().registerEvents(this.exitRoomManager, this);
      PluginCommand pCommand = this.getCommand("p");
      if (pCommand != null) {
         PartyCommand partyCommand = new PartyCommand(this.partyManager);
         pCommand.setExecutor(partyCommand);
         pCommand.setTabCompleter(partyCommand);
      }

      Bukkit.getPluginManager().registerEvents(new DungeonWorldListener(), this);
      Bukkit.getPluginManager().registerEvents(this.dungeonSecrets, this);
      Bukkit.getPluginManager().registerEvents(this.dungeonMonsters, this);
      Bukkit.getPluginManager().registerEvents(this.roomRewardManager, this);
      Bukkit.getPluginManager().registerEvents(this.startRoomReadyManager, this);
      this.enchantmentRegistry = new EnchantmentRegistry(this);
      this.enchantmentListener = new EnchantmentListener(this, this.enchantmentRegistry);
      Bukkit.getPluginManager().registerEvents(this.enchantmentListener, this);
      PluginCommand enchantBookCmd = this.getCommand("enchant_book");
      if (enchantBookCmd != null) {
         EnchantBookCommand enchantBookExecutor = new EnchantBookCommand(this, this.enchantmentRegistry);
         enchantBookCmd.setExecutor(enchantBookExecutor);
         enchantBookCmd.setTabCompleter(enchantBookExecutor);
      }

      UndergroundState undergroundState = UndergroundState.getInstance();
      if (undergroundState != null && undergroundState.getPlayerStatsManager() != null) {
         this.dungeonPanelManager = undergroundState.getDungeonPanelManager();
         undergroundState.setDungeonPanelDataProvider(new DungeonPanelDataProvider() {
            private long[] safe(long[] values) {
               long[] out = new long[DungeonPanelDataProvider.EXPERIENCE_COUNT];
               if (values != null) {
                  System.arraycopy(values, 0, out, 0, Math.min(values.length, out.length));
               }
               return out;
            }

            public long[] fetchExperience(UUID playerId) {
               return Test_dungeons.this.professionExperienceRemote != null
                  ? safe(Test_dungeons.this.professionExperienceRemote.fetchByPlayer(playerId))
                  : new long[DungeonPanelDataProvider.EXPERIENCE_COUNT];
            }

            public long[] refreshExperience(UUID playerId) {
               return Test_dungeons.this.professionExperienceRemote != null
                  ? safe(Test_dungeons.this.professionExperienceRemote.refreshByPlayer(playerId))
                  : new long[DungeonPanelDataProvider.EXPERIENCE_COUNT];
            }

            public void addExperience(UUID playerId, int expIndex, long amount) {
               if (Test_dungeons.this.professionExperienceRemote != null) {
                  Test_dungeons.this.professionExperienceRemote.addExperience(playerId, expIndex, amount);
               }
            }

            public boolean isSoloClassSelection(Player player) {
               if (player == null || Test_dungeons.this.partyManager == null) return false;
               List<UUID> members = Test_dungeons.this.partyManager.getPartyMembers(player);
               if (members == null || members.isEmpty()) return true;
               int withRole = 0;
               for (UUID id : members) {
                  Player member = Bukkit.getPlayer(id);
                  if (member != null && Test_dungeons.this.partyManager.getRole(member) != null) {
                     withRole++;
                  }
               }
               return withRole == 1;
            }
         });
         this.playerStatsManager = undergroundState.getPlayerStatsManager();
         this.playerStatsRuntime = undergroundState.getPlayerStatsRuntime();
         if (this.playerStatsRuntime == null) {
            this.getLogger().severe("underground_state is required but player stats runtime is unavailable.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
         }
         this.dungeonPlayerStatsExtension = new DungeonPlayerStatsExtension(this);
         this.playerStatsRuntime.registerExtension(this, "dungeon", this.dungeonPlayerStatsExtension);
         DungeonClassSkillSharedState classSkillSharedState = new DungeonClassSkillSharedState();
         Bukkit.getPluginManager().registerEvents(new HealerClassSkillListener(this), this);
         Bukkit.getPluginManager().registerEvents(new MageClassSkillListener(this), this);
         Bukkit.getPluginManager().registerEvents(new WarriorClassSkillListener(this, classSkillSharedState), this);
         Bukkit.getPluginManager().registerEvents(new ArcherClassSkillListener(this), this);
         Bukkit.getPluginManager().registerEvents(new TankClassSkillListener(this, classSkillSharedState), this);
         this.undergroundItem = maple.underground_item.UndergroundItem.getInstance();
         if (this.undergroundItem == null || !this.undergroundItem.isEnabled()) {
            this.getLogger().severe("underground_item is required but unavailable.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
         }
         this.undergroundItem.setStatsExtensionSuppressed(true);

         this.undergroundItem.attachDungeonBridge(new maple.underground_item.api.DungeonItemBridge() {
            public boolean isDungeonWorld(String worldName) {
               return Test_dungeons.this.isDungeonWorld(worldName);
            }

            public String getFloorForDungeonWorld(String worldName) {
               return Test_dungeons.this.getFloorForDungeonWorld(worldName);
            }

            public int getDungeonClassIndex(Player player) {
               return Test_dungeons.this.getDungeonClassIndex(player);
            }

            public boolean isDungeonGhost(UUID playerId) {
               return Test_dungeons.this.dungeonDeathListener != null && Test_dungeons.this.dungeonDeathListener.isDungeonGhost(playerId);
            }

            public double getCooldownMultiplier(Player player) {
               return Test_dungeons.this.getCooldownMultiplier(player);
            }

            public double applyHealthBlessing(Player player, double base) {
               return Test_dungeons.this.applyDungeonBlessing(player, BlessingStat.HEALTH, base);
            }

            public double applyDefenseBlessing(Player player, double base) {
               return Test_dungeons.this.applyDungeonBlessing(player, BlessingStat.DEFENSE, base);
            }

            public double applyIntelligenceBlessing(Player player, double base) {
               return Test_dungeons.this.applyDungeonBlessing(player, BlessingStat.INTELLIGENCE, base);
            }

            public double applyDamageBlessing(Player player, double base) {
               return Test_dungeons.this.applyDungeonBlessing(player, BlessingStat.DAMAGE, base);
            }

            public void syncVirtualHealthBar(Player player) {
               Test_dungeons.this.syncVirtualHealthBar(player);
            }
         });
         this.undergroundItem.attachCustomEnchantService(new maple.underground_item.api.CustomEnchantService() {
            public java.util.Collection<maple.underground_item.api.CustomEnchantView> getAll() {
               java.util.List<maple.underground_item.api.CustomEnchantView> out = new java.util.ArrayList();
               for(var enchant : Test_dungeons.this.enchantmentRegistry.getAll()) {
                  out.add(new maple.underground_item.api.CustomEnchantView(enchant.getId(), enchant.getDisplayName(), enchant.getMaxLevel(), enchant.getLoreColor().value()));
               }
               return out;
            }

            public java.util.Map<maple.underground_item.api.CustomEnchantView, Integer> getEnchants(ItemStack item) {
               java.util.Map<maple.underground_item.api.CustomEnchantView, Integer> out = new java.util.LinkedHashMap();
               for(var entry : Test_dungeons.this.enchantmentRegistry.getEnchants(item).entrySet()) {
                  var enchant = entry.getKey();
                  out.put(new maple.underground_item.api.CustomEnchantView(enchant.getId(), enchant.getDisplayName(), enchant.getMaxLevel(), enchant.getLoreColor().value()), (Integer)entry.getValue());
               }
               return out;
            }
         });
         this.gearManager = this.undergroundItem.getGearManager();
         this.immortalSkullManager = this.undergroundItem.getImmortalSkullManager();
         this.sieveSetManager = this.undergroundItem.getSieveSetManager();
         this.archerSetManager = this.undergroundItem.getArcherSetManager();
         this.skeletonSoldierSetManager = this.undergroundItem.getSkeletonSoldierSetManager();
         this.skeletonMasterSetManager = this.undergroundItem.getSkeletonMasterSetManager();
         this.undergroundCaveHelmetManager = this.undergroundItem.getUndergroundCaveHelmetManager();
         this.statsDoubleHelmetManager = this.undergroundItem.getStatsDoubleHelmetManager();
         this.caveSpecialHelmetManager = this.undergroundItem.getCaveSpecialHelmetManager();
         this.secondWindHelmetManager = this.undergroundItem.getSecondWindHelmetManager();
         this.arrowDistanceHelmetManager = this.undergroundItem.getArrowDistanceHelmetManager();
         this.cavernExplorerSetManager = this.undergroundItem.getCavernExplorerSetManager();
         this.adaptiveSetManager = this.undergroundItem.getAdaptiveSetManager();
         this.bloodSetManager = this.undergroundItem.getBloodSetManager();
         this.sublimationGearManager = this.undergroundItem.getSublimationGearManager();
         this.weaponManager = this.undergroundItem.getWeaponManager();
         for(Player player : Bukkit.getOnlinePlayers()) {
            this.playerStatsRuntime.syncVirtualHealthBar(player);
         }
         this.getLogger().info("Test_dungeons plugin enabled!");
      } else {
         this.getLogger().severe("underground_state is required but player stats manager is unavailable.");
         Bukkit.getPluginManager().disablePlugin(this);
      }
   }

   public void onDisable() {
      UndergroundState undergroundState = UndergroundState.getInstance();
      if (undergroundState != null) {
         undergroundState.setDungeonPanelDataProvider(null);
      }

      if (this.playerStatsRuntime != null) {
         this.playerStatsRuntime.unregisterExtensions(this);
      }

      if (this.undergroundItem != null) {
         this.undergroundItem.attachDungeonBridge(null);
         this.undergroundItem.attachCustomEnchantService(null);
         this.undergroundItem.setStatsExtensionSuppressed(false);
         this.undergroundItem = null;
      }

      if (this.dungeonScoreboard != null) {
         this.dungeonScoreboard.stop();
      }

      this.puzzleRoom05.onDisable();
      this.puzzleRoom04.onDisable();
      this.puzzleRoom07.onDisable();
      this.puzzleRoom08.onDisable();
      if (this.startRoomNpc != null) {
         this.startRoomNpc.onDisable();
      }

      if (this.trapTask != null) {
         this.trapTask.cancel();
         this.trapTask = null;
      }

      if (this.gearStatsApplyTask != null) {
         this.gearStatsApplyTask.cancel();
         this.gearStatsApplyTask = null;
      }

      this.trapsByWorld.clear();
      if (this.roomRewardManager != null) {
         this.roomRewardManager.onDisable();
      }

   }

   private static boolean isDungeonWorld(World world) {
      if (world == null) {
         return false;
      } else {
         String name = world.getName();
         return name != null && name.startsWith("dungeon_");
      }
   }

   private static List<String> filterPrefixList(List<String> candidates, String prefix) {
      if (candidates != null && !candidates.isEmpty()) {
         if (prefix == null) {
            prefix = "";
         }

         String lower = prefix.toLowerCase(Locale.ROOT);
         List<String> out = new ArrayList();

         for(String c : candidates) {
            if (c != null && c.toLowerCase(Locale.ROOT).startsWith(lower)) {
               out.add(c);
            }
         }

         Collections.sort(out);
         return out;
      } else {
         return List.of();
      }
   }

   private static boolean isEntranceDoor(DungeonSnapshot snapshot, Location loc) {
      if (snapshot != null && loc != null) {
         SkyBlockDungeon dungeon = snapshot.dungeon();
         if (dungeon == null) {
            return false;
         } else {
            int bx = loc.getBlockX();
            int by = loc.getBlockY();
            int bz = loc.getBlockZ();

            for(SkyBlockDungeon.DungeonDoor d : dungeon.getAllDoors()) {
               if (Math.abs(d.x1() - d.x2()) + Math.abs(d.y1() - d.y2()) == 1 && isDoorInterior(snapshot, d, bx, by, bz)) {
                  SkyBlockDungeon.DungeonRoom r1 = dungeon.getRoom(d.x1(), d.y1());
                  SkyBlockDungeon.DungeonRoom r2 = dungeon.getRoom(d.x2(), d.y2());
                  if (r1 != null && r1.getRoomType() == DungeonRoomType.ENTRANCE) {
                     return true;
                  }

                  if (r2 != null && r2.getRoomType() == DungeonRoomType.ENTRANCE) {
                     return true;
                  }

                  return false;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isDoorTunnelBlock(DungeonSnapshot snapshot, Location loc) {
      return findDoorTunnel(snapshot, loc) != null;
   }

   private static SkyBlockDungeon.DungeonDoor findDoorTunnel(DungeonSnapshot snapshot, Location clicked) {
      if (snapshot == null || clicked == null) {
         return null;
      }

      SkyBlockDungeon dungeon = snapshot.dungeon();
      if (dungeon == null) {
         return null;
      }

      int bx = clicked.getBlockX();
      int by = clicked.getBlockY();
      int bz = clicked.getBlockZ();

      for(SkyBlockDungeon.DungeonDoor d : dungeon.getAllDoors()) {
         if (Math.abs(d.x1() - d.x2()) + Math.abs(d.y1() - d.y2()) == 1 && isDoorInterior(snapshot, d, bx, by, bz)) {
            return d;
         }
      }

      return null;
   }

   private boolean tryOpenDungeonDoor(Player player, DungeonSnapshot snapshot, World world, Location clicked) {
      if (player == null || snapshot == null || world == null || clicked == null) {
         return false;
      }

      SkyBlockDungeon.DungeonDoor door = findDoorTunnel(snapshot, clicked);
      if (door == null) {
         return false;
      }

      SkyBlockDungeon dungeon = snapshot.dungeon();
      if (dungeon == null) {
         return false;
      }

      if (isEntranceDoor(dungeon, door)) {
         player.sendMessage(Component.text("初始房门会在全员准备完成后由系统自动开启。", NamedTextColor.YELLOW));
         return true;
      }

      if (!this.isUnlimitedDoorEnabled(world.getName())) {
         DungeonRoomRewardDB db = this.getDungeonRoomRewardDB();
         if (db == null) {
            player.sendMessage(Component.text("钥匙系统尚未初始化，暂时无法开启这扇门。", NamedTextColor.RED));
            return true;
         }

         UUID leaderId = player.getUniqueId();
         PartyManager pm = this.getPartyManager();
         if (pm != null) {
            Player leader = pm.getLeader(player);
            if (leader != null) {
               leaderId = leader.getUniqueId();
            }
         }

         if (!db.consumeDoorKey(world.getName(), leaderId)) {
            int keys = db.getDoorKeys(world.getName(), leaderId);
            player.sendMessage(Component.text("门钥匙不足，当前拥有 " + keys + " 把。", NamedTextColor.RED));
            return true;
         }

         int left = db.getDoorKeys(world.getName(), leaderId);
         player.sendMessage(Component.text("消耗 1 把门钥匙，剩余 " + left + " 把。", NamedTextColor.GREEN));
      }

      openDoorTunnel(this, snapshot, world, door);
      return true;
   }

   private static void openDoorTunnel(Test_dungeons plugin, DungeonSnapshot snapshot, World world, Location clicked) {
      SkyBlockDungeon.DungeonDoor door = findDoorTunnel(snapshot, clicked);
      if (door != null) {
         openDoorTunnel(plugin, snapshot, world, door);
      }
   }

   private static void openDoorTunnel(Test_dungeons plugin, DungeonSnapshot snapshot, World world, SkyBlockDungeon.DungeonDoor d) {
      if (snapshot == null || world == null || d == null) {
         return;
      }

      fillDoorInteriorWithDrop(snapshot, world, d);
      if (plugin != null && plugin.getBossRoomManager() != null) {
         plugin.getBossRoomManager().onDoorOpened(world, d.x1(), d.y1());
         plugin.getBossRoomManager().onDoorOpened(world, d.x2(), d.y2());
      }

      if (plugin != null && plugin.getExitRoomManager() != null) {
         plugin.getExitRoomManager().onDoorOpened(world, d.x1(), d.y1());
         plugin.getExitRoomManager().onDoorOpened(world, d.x2(), d.y2());
      }

      if (plugin != null && plugin.getDungeonScoreboard() != null) {
         plugin.getDungeonScoreboard().markRoomsDiscovered(world.getName(), d.x1(), d.y1(), d.x2(), d.y2());
      }
   }

   private static boolean isEntranceDoor(SkyBlockDungeon dungeon, SkyBlockDungeon.DungeonDoor door) {
      if (dungeon == null || door == null) {
         return false;
      }

      SkyBlockDungeon.DungeonRoom room1 = dungeon.getRoom(door.x1(), door.y1());
      SkyBlockDungeon.DungeonRoom room2 = dungeon.getRoom(door.x2(), door.y2());
      return room1 != null && room1.getRoomType() == DungeonRoomType.ENTRANCE
         || room2 != null && room2.getRoomType() == DungeonRoomType.ENTRANCE;
   }

   private static boolean isDoorInterior(DungeonSnapshot snapshot, SkyBlockDungeon.DungeonDoor d, int bx, int by, int bz) {
      int baseY = snapshot.originY() + (snapshot.anchorDoorY() >= 0 ? snapshot.anchorDoorY() : 0);
      int roomScale = snapshot.roomScale();
      int originX = snapshot.originX();
      int originZ = snapshot.originZ();
      if (d.x1() != d.x2()) {
         int minX = Math.min(d.x1(), d.x2());
         int gapX = originX + (minX + 1) * roomScale - 1;
         int centerZ = originZ + d.y1() * roomScale + 15;
         if (bx >= gapX - 1 && bx <= gapX + 1) {
            if (bz >= centerZ - 2 && bz <= centerZ + 2) {
               return by >= baseY - 1 && by <= baseY + 4;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         int minY = Math.min(d.y1(), d.y2());
         int gapZ = originZ + (minY + 1) * roomScale - 1;
         int centerX = originX + d.x1() * roomScale + 15;
         if (bz >= gapZ - 1 && bz <= gapZ + 1) {
            if (bx >= centerX - 2 && bx <= centerX + 2) {
               return by >= baseY - 1 && by <= baseY + 4;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private static void fillDoorInteriorWithDrop(DungeonSnapshot snapshot, World world, SkyBlockDungeon.DungeonDoor d) {
      Test_dungeons inst = getInstance();
      DungeonSecrets secrets = inst != null ? inst.getDungeonSecrets() : null;
      if (secrets != null) {
         int baseY = snapshot.originY() + (snapshot.anchorDoorY() >= 0 ? snapshot.anchorDoorY() : 0);
         int roomScale = snapshot.roomScale();
         int originX = snapshot.originX();
         int originZ = snapshot.originZ();
         if (d.x1() != d.x2()) {
            int minX = Math.min(d.x1(), d.x2());
            int gapX = originX + (minX + 1) * roomScale - 1;
            int centerZ = originZ + d.y1() * roomScale + 15;
            int zMin = centerZ - 2;
            int zMax = centerZ + 2;
            int yMin = baseY - 1;
            int yMax = baseY + 4;

            for(int px = gapX - 1; px <= gapX + 1; ++px) {
               for(int z = zMin; z <= zMax; ++z) {
                  for(int y = yMin; y <= yMax; ++y) {
                     secrets.triggerCoalBlockFall(world, px, y, z);
                  }
               }
            }
         } else {
            int minY = Math.min(d.y1(), d.y2());
            int gapZ = originZ + (minY + 1) * roomScale - 1;
            int centerX = originX + d.x1() * roomScale + 15;
            int xMin = centerX - 2;
            int xMax = centerX + 2;
            int yMin = baseY - 1;
            int yMax = baseY + 4;

            for(int pz = gapZ - 1; pz <= gapZ + 1; ++pz) {
               for(int x = xMin; x <= xMax; ++x) {
                  for(int y = yMin; y <= yMax; ++y) {
                     secrets.triggerCoalBlockFall(world, x, y, pz);
                  }
               }
            }
         }

      }
   }

   private final class DungeonLayoutRenderer extends MapRenderer {
      private static final byte BG_COLOR = MapPalette.matchColor(new Color(18, 22, 28));
      private static final byte EMPTY_COLOR = MapPalette.matchColor(new Color(34, 39, 47));
      private static final byte DOOR_COLOR = MapPalette.matchColor(new Color(214, 196, 120));
      private static final byte BORDER_COLOR = MapPalette.matchColor(new Color(12, 14, 18));
      private static final byte ENTRANCE_COLOR = MapPalette.matchColor(new Color(72, 186, 120));
      private static final byte BASE_COLOR = MapPalette.matchColor(new Color(139, 145, 156));
      private static final byte FAIRY_COLOR = MapPalette.matchColor(new Color(243, 148, 255));
      private static final byte PUZZLE_COLOR = MapPalette.matchColor(new Color(141, 94, 255));
      private static final byte BLOOD_COLOR = MapPalette.matchColor(new Color(196, 54, 64));
      private static final byte TRAP_COLOR = MapPalette.matchColor(new Color(255, 149, 62));
      private static final byte MINI_BOSS_COLOR = MapPalette.matchColor(new Color(255, 232, 92));
      private static final byte EXIT_COLOR = MapPalette.matchColor(new Color(196, 54, 64));
      private static final byte CRITICAL_MARK_COLOR = MapPalette.matchColor(new Color(255, 255, 255));

      private final DungeonSnapshot snapshot;
      private boolean rendered;

      private DungeonLayoutRenderer(DungeonSnapshot snapshot) {
         super(false);
         this.snapshot = snapshot;
      }

      public void render(@org.jetbrains.annotations.NotNull MapView map, @org.jetbrains.annotations.NotNull MapCanvas canvas, @org.jetbrains.annotations.NotNull Player player) {
         if (this.rendered || this.snapshot == null || this.snapshot.dungeon() == null) {
            return;
         }

         this.rendered = true;
         this.paintBackground(canvas, BG_COLOR);

         SkyBlockDungeon dungeon = this.snapshot.dungeon();
         int width = Math.max(1, this.snapshot.width());
         int height = Math.max(1, this.snapshot.height());
         int padding = 10;
         int gap = 6;
         int maxCellWidth = (128 - padding * 2 - Math.max(0, width - 1) * gap) / width;
         int maxCellHeight = (128 - padding * 2 - Math.max(0, height - 1) * gap) / height;
         int cellSize = Math.max(8, Math.min(maxCellWidth, maxCellHeight));
         int layoutWidth = width * cellSize + Math.max(0, width - 1) * gap;
         int layoutHeight = height * cellSize + Math.max(0, height - 1) * gap;
         int startX = Math.max(0, (128 - layoutWidth) / 2);
         int startY = Math.max(0, (128 - layoutHeight) / 2);

         this.paintUnusedGrid(canvas, dungeon, width, height, startX, startY, cellSize, gap);
         this.paintDoors(canvas, dungeon, width, height, startX, startY, cellSize, gap);
         this.paintRooms(canvas, dungeon, width, height, startX, startY, cellSize, gap);
      }

      private void paintBackground(MapCanvas canvas, byte color) {
         for(int x = 0; x < 128; ++x) {
            for(int y = 0; y < 128; ++y) {
               canvas.setPixel(x, y, color);
            }
         }
      }

      private void paintUnusedGrid(MapCanvas canvas, SkyBlockDungeon dungeon, int width, int height, int startX, int startY, int cellSize, int gap) {
         for(int gridX = 0; gridX < width; ++gridX) {
            for(int gridY = 0; gridY < height; ++gridY) {
               if (dungeon.getRoom(gridX, gridY) != null) {
                  continue;
               }

               int x = startX + gridX * (cellSize + gap);
               int y = startY + gridY * (cellSize + gap);
               this.fillRect(canvas, x, y, cellSize, cellSize, EMPTY_COLOR);
               this.drawRect(canvas, x, y, cellSize, cellSize, BORDER_COLOR);
            }
         }
      }

      private void paintDoors(MapCanvas canvas, SkyBlockDungeon dungeon, int width, int height, int startX, int startY, int cellSize, int gap) {
         int doorThickness = Math.max(2, gap);

         for(SkyBlockDungeon.DungeonDoor door : dungeon.getAllDoors()) {
            int x1 = door.x1();
            int y1 = door.y1();
            int x2 = door.x2();
            int y2 = door.y2();
            if (x1 < 0 || y1 < 0 || x1 >= width || y1 >= height || x2 < 0 || y2 < 0 || x2 >= width || y2 >= height) {
               continue;
            }
            if (dungeon.getRoom(x1, y1) == null || dungeon.getRoom(x2, y2) == null) {
               continue;
            }
            if (this.isSameRenderedRoom(dungeon, x1, y1, x2, y2)) {
               continue;
            }

            int room1X = startX + x1 * (cellSize + gap);
            int room1Y = startY + y1 * (cellSize + gap);
            int room2X = startX + x2 * (cellSize + gap);
            int room2Y = startY + y2 * (cellSize + gap);

            if (x1 != x2) {
               int leftRoomX = Math.min(room1X, room2X);
               int bridgeX = leftRoomX + cellSize;
               int bridgeY = room1Y + cellSize / 2 - doorThickness / 2;
               this.fillRect(canvas, bridgeX, bridgeY, gap, doorThickness, DOOR_COLOR);
            } else if (y1 != y2) {
               int topRoomY = Math.min(room1Y, room2Y);
               int bridgeX = room1X + cellSize / 2 - doorThickness / 2;
               int bridgeY = topRoomY + cellSize;
               this.fillRect(canvas, bridgeX, bridgeY, doorThickness, gap, DOOR_COLOR);
            }
         }
      }

      private void paintRooms(MapCanvas canvas, SkyBlockDungeon dungeon, int width, int height, int startX, int startY, int cellSize, int gap) {
         for(int gridX = 0; gridX < width; ++gridX) {
            for(int gridY = 0; gridY < height; ++gridY) {
               SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(gridX, gridY);
               if (room == null) {
                  continue;
               }

               int x = startX + gridX * (cellSize + gap);
               int y = startY + gridY * (cellSize + gap);
               byte roomColor = this.colorForRoom(room);
               this.fillRect(canvas, x, y, cellSize, cellSize, roomColor);
               boolean connectRight = this.isSameRenderedRoom(dungeon, gridX, gridY, gridX + 1, gridY);
               boolean connectDown = this.isSameRenderedRoom(dungeon, gridX, gridY, gridX, gridY + 1);
               if (connectRight) {
                  this.fillRect(canvas, x + cellSize, y, gap, cellSize, roomColor);
               }

               if (connectDown) {
                  this.fillRect(canvas, x, y + cellSize, cellSize, gap, roomColor);
               }

               if (connectRight && connectDown && this.isSameRenderedRoom(dungeon, gridX, gridY, gridX + 1, gridY + 1)) {
                  this.fillRect(canvas, x + cellSize, y + cellSize, gap, gap, roomColor);
               }

            }
         }
      }

      private byte colorForRoom(SkyBlockDungeon.DungeonRoom room) {
         if (room == null || room.getRoomType() == null) {
            return BASE_COLOR;
         }

         return switch (room.getRoomType()) {
            case ENTRANCE -> ENTRANCE_COLOR;
            case BASE -> BASE_COLOR;
            case FAIRY -> FAIRY_COLOR;
            case PUZZLE -> PUZZLE_COLOR;
            case BLOOD -> BLOOD_COLOR;
            case TRAP -> TRAP_COLOR;
            case MINI_BOSS -> MINI_BOSS_COLOR;
            case EXIT -> EXIT_COLOR;
         };
      }

      private void drawCriticalMark(MapCanvas canvas, int x, int y, int cellSize) {
         int cx = x + cellSize / 2;
         int cy = y + cellSize / 2;
         int radius = Math.max(1, cellSize / 6);

         for(int dx = -radius; dx <= radius; ++dx) {
            for(int dy = -radius; dy <= radius; ++dy) {
               if (Math.abs(dx) + Math.abs(dy) <= radius) {
                  this.setPixelSafe(canvas, cx + dx, cy + dy, CRITICAL_MARK_COLOR);
               }
            }
         }
      }

      private void fillRect(MapCanvas canvas, int x, int y, int width, int height, byte color) {
         for(int px = x; px < x + width; ++px) {
            for(int py = y; py < y + height; ++py) {
               this.setPixelSafe(canvas, px, py, color);
            }
         }
      }

      private void drawRect(MapCanvas canvas, int x, int y, int width, int height, byte color) {
         for(int px = x; px < x + width; ++px) {
            this.setPixelSafe(canvas, px, y, color);
            this.setPixelSafe(canvas, px, y + height - 1, color);
         }

         for(int py = y; py < y + height; ++py) {
            this.setPixelSafe(canvas, x, py, color);
            this.setPixelSafe(canvas, x + width - 1, py, color);
         }
      }

      private void setPixelSafe(MapCanvas canvas, int x, int y, byte color) {
         if (x >= 0 && x < 128 && y >= 0 && y < 128) {
            canvas.setPixel(x, y, color);
         }
      }

      private boolean isSameRenderedRoom(SkyBlockDungeon dungeon, int x1, int y1, int x2, int y2) {
         if (dungeon == null) {
            return false;
         }
         if (x2 < 0 || y2 < 0) {
            return false;
         }

         SkyBlockDungeon.DungeonRoom room1 = dungeon.getRoom(x1, y1);
         SkyBlockDungeon.DungeonRoom room2 = dungeon.getRoom(x2, y2);
         if (room1 == null || room2 == null) {
            return false;
         }
         if (room1.getStructureId() == null || room2.getStructureId() == null) {
            return false;
         }

         return room1.getStructureId().equals(room2.getStructureId());
      }
   }

   private final class DungeonPointerRenderer extends MapRenderer {
      private final DungeonSnapshot snapshot;

      private DungeonPointerRenderer(DungeonSnapshot snapshot) {
         super(true);
         this.snapshot = snapshot;
      }

      public void render(@org.jetbrains.annotations.NotNull MapView map, @org.jetbrains.annotations.NotNull MapCanvas canvas, @org.jetbrains.annotations.NotNull Player player) {
         MapCursorCollection cursors = new MapCursorCollection();
         if (canvas == null || player == null || this.snapshot == null || this.snapshot.dungeon() == null) {
            canvas.setCursors(cursors);
            return;
         }

         if (player.getWorld() == null || !player.getWorld().getName().equals(this.snapshot.worldName())) {
            canvas.setCursors(cursors);
            return;
         }

         int width = Math.max(1, this.snapshot.width());
         int height = Math.max(1, this.snapshot.height());
         int roomScale = Math.max(1, this.snapshot.roomScale());
         int padding = 10;
         int gap = 6;
         int maxCellWidth = (128 - padding * 2 - Math.max(0, width - 1) * gap) / width;
         int maxCellHeight = (128 - padding * 2 - Math.max(0, height - 1) * gap) / height;
         int cellSize = Math.max(8, Math.min(maxCellWidth, maxCellHeight));
         int layoutWidth = width * cellSize + Math.max(0, width - 1) * gap;
         int layoutHeight = height * cellSize + Math.max(0, height - 1) * gap;
         int startX = Math.max(0, (128 - layoutWidth) / 2);
         int startY = Math.max(0, (128 - layoutHeight) / 2);

         for(Player teammate : this.getVisiblePartyMembers(player)) {
            if (teammate == null || teammate.getUniqueId().equals(player.getUniqueId())) {
               continue;
            }

            this.addPlayerCursor(cursors, teammate, false, width, height, roomScale, startX, startY, cellSize, gap);
         }

         this.addPlayerCursor(cursors, player, true, width, height, roomScale, startX, startY, cellSize, gap);
         canvas.setCursors(cursors);
      }

      private List<Player> getVisiblePartyMembers(Player viewer) {
         LinkedHashSet<UUID> ids = new LinkedHashSet<>();
         PartyManager pm = Test_dungeons.this.partyManager;
         if (pm != null) {
            List<UUID> members = pm.getPartyMembers(viewer);
            if (members != null) {
               ids.addAll(members);
            }
         }

         ids.add(viewer.getUniqueId());
         List<Player> players = new ArrayList<>();

         for(UUID id : ids) {
            if (id == null) {
               continue;
            }

            Player member = Bukkit.getPlayer(id);
            if (member == null || !member.isOnline()) {
               continue;
            }
            if (member.getWorld() == null || !member.getWorld().getName().equals(this.snapshot.worldName())) {
               continue;
            }

            players.add(member);
         }

         return players;
      }

      private void addPlayerCursor(MapCursorCollection cursors, Player target, boolean self, int width, int height, int roomScale, int startX, int startY, int cellSize, int gap) {
         if (target == null || target.getWorld() == null) {
            return;
         }

         double localX = target.getLocation().getX() - (double)this.snapshot.originX();
         double localZ = target.getLocation().getZ() - (double)this.snapshot.originZ();
         double maxX = (double)(width * roomScale);
         double maxZ = (double)(height * roomScale);
         boolean inside = localX >= 0.0 && localZ >= 0.0 && localX < maxX && localZ < maxZ;

         double clampedX = Math.max(0.0, Math.min(maxX - 1.0E-6, localX));
         double clampedZ = Math.max(0.0, Math.min(maxZ - 1.0E-6, localZ));
         int roomX = Math.max(0, Math.min(width - 1, (int)(clampedX / (double)roomScale)));
         int roomZ = Math.max(0, Math.min(height - 1, (int)(clampedZ / (double)roomScale)));
         double fracX = (clampedX - (double)(roomX * roomScale)) / (double)roomScale;
         double fracZ = (clampedZ - (double)(roomZ * roomScale)) / (double)roomScale;
         double pixelX = (double)(startX + roomX * (cellSize + gap)) + fracX * (double)(cellSize - 1);
         double pixelY = (double)(startY + roomZ * (cellSize + gap)) + fracZ * (double)(cellSize - 1);

         byte cursorX = this.toCursorCoord(pixelX);
         byte cursorY = this.toCursorCoord(pixelY);
         byte direction = this.yawToCursorDirection(target.getLocation().getYaw());
         MapCursor.Type type = self ? MapCursor.Type.BANNER_LIME : MapCursor.Type.BANNER_WHITE;
         if (!inside) {
            cursorX = this.clampCursor(cursorX);
            cursorY = this.clampCursor(cursorY);
         }

         cursors.addCursor(new MapCursor(cursorX, cursorY, direction, type, true));
      }

      private byte toCursorCoord(double pixel) {
         int value = (int)Math.round(pixel * 2.0 - 128.0);
         if (value < -128) {
            value = -128;
         } else if (value > 127) {
            value = 127;
         }

         return (byte)value;
      }

      private byte clampCursor(byte value) {
         if (value < -126) {
            return -126;
         } else {
            return value > 126 ? 126 : value;
         }
      }

      private byte yawToCursorDirection(float yaw) {
         int direction = Math.round(yaw / 22.5F);
         direction = Math.floorMod(direction, 16);
         return (byte)direction;
      }
   }

   private final class DungeonMapCommand implements org.bukkit.command.CommandExecutor {
      private DungeonMapCommand(Test_dungeons ignored) {
      }

      public boolean onCommand(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender, @org.jetbrains.annotations.NotNull org.bukkit.command.Command command, @org.jetbrains.annotations.NotNull String label, @org.jetbrains.annotations.NotNull String[] args) {
         if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
         } else {
            DungeonSnapshot snapshot = Test_dungeons.this.getLastDungeon();
            if (snapshot == null) {
               player.sendMessage(Component.text("No dungeon generated yet. Run /gen_dungeon first.", NamedTextColor.RED));
               return true;
            } else {
               player.getInventory().addItem(new ItemStack[]{Test_dungeons.this.createDungeonMapItem(player.getWorld(), snapshot)});
               player.sendMessage(Component.text("Dungeon map generated.", NamedTextColor.GREEN));
               return true;
            }
         }
      }
   }

   private final class DungeonCompletionRenderer extends MapRenderer {
      private static final byte CHECK_COLOR = MapPalette.matchColor(new Color(129, 235, 118));
      private static final byte CHECK_WHITE_COLOR = MapPalette.matchColor(new Color(245, 245, 245));
      private final DungeonSnapshot snapshot;

      private DungeonCompletionRenderer(DungeonSnapshot snapshot) {
         super(true);
         this.snapshot = snapshot;
      }

      public void render(@org.jetbrains.annotations.NotNull MapView map, @org.jetbrains.annotations.NotNull MapCanvas canvas, @org.jetbrains.annotations.NotNull Player player) {
         if (canvas == null || this.snapshot == null || this.snapshot.dungeon() == null) {
            return;
         }

         var scoreboard = Test_dungeons.this.getDungeonScoreboard();
         if (scoreboard == null) {
            return;
         }
         var secrets = Test_dungeons.this.getDungeonSecrets();
         var monsters = Test_dungeons.this.getDungeonMonsters();

         SkyBlockDungeon dungeon = this.snapshot.dungeon();
         int width = Math.max(1, this.snapshot.width());
         int height = Math.max(1, this.snapshot.height());
         int padding = 10;
         int gap = 6;
         int maxCellWidth = (128 - padding * 2 - Math.max(0, width - 1) * gap) / width;
         int maxCellHeight = (128 - padding * 2 - Math.max(0, height - 1) * gap) / height;
         int cellSize = Math.max(8, Math.min(maxCellWidth, maxCellHeight));
         int layoutWidth = width * cellSize + Math.max(0, width - 1) * gap;
         int layoutHeight = height * cellSize + Math.max(0, height - 1) * gap;
         int startX = Math.max(0, (128 - layoutWidth) / 2);
         int startY = Math.max(0, (128 - layoutHeight) / 2);
         Set<String> renderedRooms = new HashSet<>();

         for(int gridX = 0; gridX < width; ++gridX) {
            for(int gridY = 0; gridY < height; ++gridY) {
               SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(gridX, gridY);
               if (room == null || room.getRoomType() == null || room.getRoomType() == DungeonRoomType.ENTRANCE) {
                  continue;
               }

               String roomKey = this.renderedRoomKey(room, gridX, gridY);
               if (!renderedRooms.add(roomKey)) {
                  continue;
               }

               boolean monstersCleared = monsters != null && monsters.isRoomMonstersCleared(this.snapshot.worldName(), gridX, gridY);
               boolean secretsCompleted = secrets != null && secrets.isRoomSecretsCompleted(this.snapshot.worldName(), gridX, gridY);
               if (!monstersCleared) {
                  continue;
               }

               int[] bounds = this.findRenderedRoomBounds(dungeon, width, height, gridX, gridY);
               int pixelMinX = startX + bounds[0] * (cellSize + gap);
               int pixelMinY = startY + bounds[1] * (cellSize + gap);
               int pixelMaxX = startX + bounds[2] * (cellSize + gap) + cellSize - 1;
               int pixelMaxY = startY + bounds[3] * (cellSize + gap) + cellSize - 1;
               int centerX = (pixelMinX + pixelMaxX) / 2;
               int centerY = (pixelMinY + pixelMaxY) / 2;
               int checkSize = Math.max(cellSize, Math.max(1, bounds[2] - bounds[0] + 1) * cellSize / 2);
               this.drawCheckMark(canvas, centerX, centerY, checkSize, secretsCompleted ? CHECK_COLOR : CHECK_WHITE_COLOR);
            }
         }
      }

      private String renderedRoomKey(SkyBlockDungeon.DungeonRoom room, int gridX, int gridY) {
         if (room != null && room.getStructureId() != null) {
            return room.getStructureId().toString();
         }
         return gridX + ":" + gridY;
      }

      private int[] findRenderedRoomBounds(SkyBlockDungeon dungeon, int width, int height, int startGridX, int startGridY) {
         int minX = startGridX;
         int minY = startGridY;
         int maxX = startGridX;
         int maxY = startGridY;
         SkyBlockDungeon.DungeonRoom startRoom = dungeon.getRoom(startGridX, startGridY);
         if (startRoom == null || startRoom.getStructureId() == null) {
            return new int[]{minX, minY, maxX, maxY};
         }

         UUID structureId = startRoom.getStructureId();
         for(int gridX = 0; gridX < width; ++gridX) {
            for(int gridY = 0; gridY < height; ++gridY) {
               SkyBlockDungeon.DungeonRoom room = dungeon.getRoom(gridX, gridY);
               if (room == null || room.getStructureId() == null) {
                  continue;
               }
               if (!structureId.equals(room.getStructureId())) {
                  continue;
               }

               minX = Math.min(minX, gridX);
               minY = Math.min(minY, gridY);
               maxX = Math.max(maxX, gridX);
               maxY = Math.max(maxY, gridY);
            }
         }

         return new int[]{minX, minY, maxX, maxY};
      }

      private void drawCheckMark(MapCanvas canvas, int centerX, int centerY, int size, byte color) {
         int scale = Math.max(1, size / 8);
         this.drawLine(canvas, centerX - scale * 3, centerY, centerX - scale, centerY + scale * 2, scale, color);
         this.drawLine(canvas, centerX - scale, centerY + scale * 2, centerX + scale * 4, centerY - scale * 3, scale, color);
      }

      private void drawLine(MapCanvas canvas, int x1, int y1, int x2, int y2, int thickness, byte color) {
         int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
         if (steps <= 0) {
            this.drawDot(canvas, x1, y1, thickness, color);
            return;
         }

         for(int i = 0; i <= steps; ++i) {
            double t = (double)i / (double)steps;
            int x = (int)Math.round(x1 + (x2 - x1) * t);
            int y = (int)Math.round(y1 + (y2 - y1) * t);
            this.drawDot(canvas, x, y, thickness, color);
         }
      }

      private void drawDot(MapCanvas canvas, int x, int y, int radius, byte color) {
         for(int dx = -radius; dx <= radius; ++dx) {
            for(int dy = -radius; dy <= radius; ++dy) {
               if (Math.abs(dx) + Math.abs(dy) > radius + 1) {
                  continue;
               }
               this.setPixelSafe(canvas, x + dx, y + dy, color);
            }
         }
      }

      private void setPixelSafe(MapCanvas canvas, int x, int y, byte color) {
         if (x >= 0 && x < 128 && y >= 0 && y < 128) {
            canvas.setPixel(x, y, color);
         }
      }
   }

   private final class UnlimitedDoorCommand implements org.bukkit.command.CommandExecutor {
      private UnlimitedDoorCommand(Test_dungeons ignored) {
      }

      public boolean onCommand(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender, @org.jetbrains.annotations.NotNull org.bukkit.command.Command command, @org.jetbrains.annotations.NotNull String label, @org.jetbrains.annotations.NotNull String[] args) {
         if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
         } else if (!player.hasPermission("test_dungeons.admin")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
         } else {
            String worldName = player.getWorld().getName();
            if (args.length < 1) {
               boolean enabled = Test_dungeons.this.isUnlimitedDoorEnabled(worldName);
               player.sendMessage(Component.text("Unlimited doors for " + worldName + ": " + enabled, NamedTextColor.GRAY));
               return true;
            } else {
               String arg = args[0].trim().toLowerCase(Locale.ROOT);
               boolean enable = arg.equals("on") || arg.equals("enable") || arg.equals("true") || arg.equals("1");
               boolean disable = arg.equals("off") || arg.equals("disable") || arg.equals("false") || arg.equals("0");
               if (enable) {
                  Test_dungeons.this.unlimitedDoorWorlds.add(worldName);
                  player.sendMessage(Component.text("Unlimited doors enabled.", NamedTextColor.GREEN));
               } else if (disable) {
                  Test_dungeons.this.unlimitedDoorWorlds.remove(worldName);
                  player.sendMessage(Component.text("Unlimited doors disabled.", NamedTextColor.GRAY));
               } else {
                  player.sendMessage(Component.text("Usage: /dungeon_unlimited_door <on|off>", NamedTextColor.YELLOW));
               }

               return true;
            }
         }
      }
   }

   private final class DungeonWorldListener implements org.bukkit.event.Listener {
      private DungeonWorldListener() {
      }

      @EventHandler(
         priority = EventPriority.HIGHEST,
         ignoreCancelled = true
      )
      public void onDungeonDoorInteract(PlayerInteractEvent event) {
         if (event == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
         }
         if (event.getHand() != EquipmentSlot.HAND) {
            return;
         }

         Block clicked = event.getClickedBlock();
         if (clicked == null || clicked.getType() != Material.COAL_BLOCK) {
            return;
         }

         World world = clicked.getWorld();
         if (world == null || !Test_dungeons.this.isDungeonWorld(world.getName())) {
            return;
         }

         Player player = event.getPlayer();
         Location clickedLoc = clicked.getLocation();
         DungeonSnapshot snapshot = Test_dungeons.this.getLastDungeon();
         if (snapshot != null
            && world.getName().equals(snapshot.worldName())
            && isDoorTunnelBlock(snapshot, clickedLoc)) {
            Test_dungeons.this.tryOpenDungeonDoor(player, snapshot, world, clickedLoc);
            event.setCancelled(true);
            return;
         }

         if (Test_dungeons.this.tryOpenDebugDoor(world, clickedLoc)) {
            event.setCancelled(true);
         }
      }
   }
}
