package maple.underground_caves.gear;

import maple.underground_caves.weapons.WeaponManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 管理員指令：發放裝備／武器／永恆的面具（同一指令）。
 * /gear give [玩家]                    — 堅壁套裝一整套 (F1)
 * /gear give <玩家>                    — 堅壁套裝給指定玩家
 * /gear give <部位> [樓層] [玩家]      — 堅壁單件，部位=helmet|chest|leggings|boots
 * /gear give skull [玩家]              — 永恆的面具
 * /gear give weapon <武器ID> [玩家]     — 指定武器
 * /gear reload                          — 重載裝備／武器／永恆的面具配置，並刷新所有在線玩家背包內對應物品
 * /immortalskull give [玩家]           — 等同 /gear give skull [玩家]
 */
@SuppressWarnings("ALL")
public final class GearGiveCommand implements CommandExecutor {

    private final GearManager gearManager;
    private final ImmortalSkullManager skullManager;
    private final WeaponManager weaponManager;
    private final SieveSetManager sieveSetManager;
    private final ArcherSetManager archerSetManager;
    private final SkeletonSoldierSetManager skeletonSoldierSetManager;
    private final SkeletonMasterSetManager skeletonMasterSetManager;
    private final UndergroundCaveHelmetManager undergroundCaveHelmetManager;
    private final StatsDoubleHelmetManager statsDoubleHelmetManager;
    private final CaveSpecialHelmetManager caveSpecialHelmetManager;
    private final SecondWindHelmetManager secondWindHelmetManager;
    private final ArrowDistanceHelmetManager arrowDistanceHelmetManager;
    private final CavernExplorerSetManager cavernExplorerSetManager;
    private final AdaptiveSetManager adaptiveSetManager;
    private final BloodSetManager bloodSetManager;
    private final QualityUpgradeItemManager qualityUpgradeItemManager;

    public GearGiveCommand(GearManager gearManager) {
        this(gearManager, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public GearGiveCommand(GearManager gearManager, ImmortalSkullManager skullManager, WeaponManager weaponManager,
                           SieveSetManager sieveSetManager, ArcherSetManager archerSetManager) {
        this(gearManager, skullManager, weaponManager, sieveSetManager, archerSetManager, null, null, null, null, null, null, null, null, null, null, null);
    }

    public GearGiveCommand(GearManager gearManager, ImmortalSkullManager skullManager, WeaponManager weaponManager,
                           SieveSetManager sieveSetManager, ArcherSetManager archerSetManager,
                           SkeletonSoldierSetManager skeletonSoldierSetManager) {
        this(gearManager, skullManager, weaponManager, sieveSetManager, archerSetManager, skeletonSoldierSetManager, null, null, null, null, null, null, null, null, null, null);
    }

    public GearGiveCommand(GearManager gearManager, ImmortalSkullManager skullManager, WeaponManager weaponManager,
                           SieveSetManager sieveSetManager, ArcherSetManager archerSetManager,
                           SkeletonSoldierSetManager skeletonSoldierSetManager,
                           SkeletonMasterSetManager skeletonMasterSetManager) {
        this(gearManager, skullManager, weaponManager, sieveSetManager, archerSetManager, skeletonSoldierSetManager, skeletonMasterSetManager, null, null, null, null, null, null, null, null, null);
    }

    public GearGiveCommand(GearManager gearManager, ImmortalSkullManager skullManager, WeaponManager weaponManager,
                           SieveSetManager sieveSetManager, ArcherSetManager archerSetManager,
                           SkeletonSoldierSetManager skeletonSoldierSetManager,
                           SkeletonMasterSetManager skeletonMasterSetManager,
                           UndergroundCaveHelmetManager undergroundCaveHelmetManager) {
        this(gearManager, skullManager, weaponManager, sieveSetManager, archerSetManager,
                skeletonSoldierSetManager, skeletonMasterSetManager, undergroundCaveHelmetManager, null, null, null, null, null, null, null, null);
    }

    public GearGiveCommand(GearManager gearManager, ImmortalSkullManager skullManager, WeaponManager weaponManager,
                           SieveSetManager sieveSetManager, ArcherSetManager archerSetManager,
                           SkeletonSoldierSetManager skeletonSoldierSetManager,
                           SkeletonMasterSetManager skeletonMasterSetManager,
                           UndergroundCaveHelmetManager undergroundCaveHelmetManager,
                           StatsDoubleHelmetManager statsDoubleHelmetManager) {
        this(gearManager, skullManager, weaponManager, sieveSetManager, archerSetManager,
                skeletonSoldierSetManager, skeletonMasterSetManager, undergroundCaveHelmetManager, statsDoubleHelmetManager, null, null, null, null, null, null, null);
    }

    public GearGiveCommand(GearManager gearManager, ImmortalSkullManager skullManager, WeaponManager weaponManager,
                           SieveSetManager sieveSetManager, ArcherSetManager archerSetManager,
                           SkeletonSoldierSetManager skeletonSoldierSetManager,
                           SkeletonMasterSetManager skeletonMasterSetManager,
                           UndergroundCaveHelmetManager undergroundCaveHelmetManager,
                           StatsDoubleHelmetManager statsDoubleHelmetManager,
                           CaveSpecialHelmetManager caveSpecialHelmetManager) {
        this(gearManager, skullManager, weaponManager, sieveSetManager, archerSetManager,
                skeletonSoldierSetManager, skeletonMasterSetManager, undergroundCaveHelmetManager, statsDoubleHelmetManager, caveSpecialHelmetManager, null, null, null, null, null, null);
    }

    public GearGiveCommand(GearManager gearManager, ImmortalSkullManager skullManager, WeaponManager weaponManager,
                           SieveSetManager sieveSetManager, ArcherSetManager archerSetManager,
                           SkeletonSoldierSetManager skeletonSoldierSetManager,
                           SkeletonMasterSetManager skeletonMasterSetManager,
                           UndergroundCaveHelmetManager undergroundCaveHelmetManager,
                           StatsDoubleHelmetManager statsDoubleHelmetManager,
                           CaveSpecialHelmetManager caveSpecialHelmetManager,
                           SecondWindHelmetManager secondWindHelmetManager,
                           ArrowDistanceHelmetManager arrowDistanceHelmetManager,
                           CavernExplorerSetManager cavernExplorerSetManager,
                           AdaptiveSetManager adaptiveSetManager,
                           BloodSetManager bloodSetManager,
                           QualityUpgradeItemManager qualityUpgradeItemManager) {
        this.gearManager = gearManager;
        this.skullManager = skullManager;
        this.weaponManager = weaponManager;
        this.sieveSetManager = sieveSetManager;
        this.archerSetManager = archerSetManager;
        this.skeletonSoldierSetManager = skeletonSoldierSetManager;
        this.skeletonMasterSetManager = skeletonMasterSetManager;
        this.undergroundCaveHelmetManager = undergroundCaveHelmetManager;
        this.statsDoubleHelmetManager = statsDoubleHelmetManager;
        this.caveSpecialHelmetManager = caveSpecialHelmetManager;
        this.secondWindHelmetManager = secondWindHelmetManager;
        this.arrowDistanceHelmetManager = arrowDistanceHelmetManager;
        this.cavernExplorerSetManager = cavernExplorerSetManager;
        this.adaptiveSetManager = adaptiveSetManager;
        this.bloodSetManager = bloodSetManager;
        this.qualityUpgradeItemManager = qualityUpgradeItemManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            runGlobalReload(sender);
            return true;
        }
        // /immortalskull give [player] → 視為 /gear give skull [player]
        if ("immortalskull".equalsIgnoreCase(label) && args.length >= 1 && "give".equalsIgnoreCase(args[0])) {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "give";
            newArgs[1] = "skull";
            for (int i = 1; i < args.length; i++) newArgs[i + 1] = args[i];
            args = newArgs;
        }
        if (!"give".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return true;
        }
        if (args.length == 1) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage("§c請指定玩家: §f/gear give <玩家>");
                return true;
            }
            giveFullSet(self, self, "F1");
            sender.sendMessage("§a已發放一整套堅壁套裝 (F1) 至你的背包。");
            return true;
        }
        String first = args[1];

        // /gear give skull [玩家]
        if ("skull".equalsIgnoreCase(first)) {
            if (skullManager == null) {
                sender.sendMessage("§c永恆的面具功能未載入。");
                return true;
            }
            Player target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (target.getInventory().firstEmpty() == -1) {
                sender.sendMessage("§c目標背包已滿。");
                return true;
            }
            target.getInventory().addItem(skullManager.createImmortalSkull());
            if (target.equals(sender)) sender.sendMessage("§a已獲得永恆的面具。");
            else {
                sender.sendMessage("§a已發放永恆的面具給 §f" + target.getName() + "§a。");
                target.sendMessage("§a你獲得了永恆的面具。");
            }
            return true;
        }

        // /gear give quality_upgrade [玩家]
        if ("quality_upgrade".equalsIgnoreCase(first)) {
            if (qualityUpgradeItemManager == null) {
                sender.sendMessage("§c品質升級道具功能未載入。");
                return true;
            }
            Player target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (target.getInventory().firstEmpty() == -1) {
                sender.sendMessage("§c目標背包已滿。");
                return true;
            }
            target.getInventory().addItem(qualityUpgradeItemManager.createItem());
            if (target.equals(sender)) sender.sendMessage("§a已獲得品質升級道具。");
            else {
                sender.sendMessage("§a已發放品質升級道具給 §f" + target.getName() + "§a。");
                target.sendMessage("§a你獲得了品質升級道具。");
            }
            return true;
        }

        // /gear give cave_helmet <F1|F2|F3> [玩家]
        if ("cave_helmet".equalsIgnoreCase(first)) {
            if (undergroundCaveHelmetManager == null) {
                sender.sendMessage("§c地下洞穴頭盔功能未載入。");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§e用法: §f/gear give cave_helmet <F1|F2|F3> [玩家]");
                return true;
            }
            if (!maple.underground_caves.gear.UndergroundCaveHelmetManager.isValidFloor(args[2])) {
                sender.sendMessage("§c樓層須為 F1、F2 或 F3。");
                return true;
            }
            String floor = maple.underground_caves.gear.UndergroundCaveHelmetManager.toValidFloor(args[2]);
            Player target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (target.getInventory().firstEmpty() == -1) {
                sender.sendMessage("§c目標背包已滿。");
                return true;
            }
            target.getInventory().addItem(undergroundCaveHelmetManager.createCaveHelmet(floor));
            if (target.equals(sender)) sender.sendMessage("§a已獲得地下洞穴頭盔 §f(" + floor + ")§a。");
            else {
                sender.sendMessage("§a已發放地下洞穴頭盔 §f(" + floor + ") §a給 §f" + target.getName() + "§a。");
                target.sendMessage("§a你獲得了地下洞穴頭盔 §f(" + floor + ")§a。");
            }
            return true;
        }

        // /gear give weapon <武器ID> [玩家]
        if ("weapon".equalsIgnoreCase(first)) {
            if (weaponManager == null) {
                sender.sendMessage("§c武器功能未載入。");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§e用法: §f/gear give weapon <武器ID> [玩家]");
                return true;
            }
            String weaponId = args[2];
            ItemStack weapon = weaponManager.createWeaponItem(weaponId);
            if (weapon == null) {
                sender.sendMessage("§c找不到武器: " + weaponId + " §7(可用: " + String.join(", ", weaponManager.getWeaponIds()) + ")");
                return true;
            }
            Player target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (target.getInventory().firstEmpty() == -1) {
                sender.sendMessage("§c目標背包已滿。");
                return true;
            }
            target.getInventory().addItem(weapon);
            if (target.equals(sender)) sender.sendMessage("§a已獲得武器: §f" + weaponId);
            else sender.sendMessage("§a已發放武器 §f" + weaponId + " §a給 " + target.getName());
            return true;
        }

        // /gear give <黃金/鑽石頭盔ID> [玩家] — 戴上時屬性 x2 的頭盔
        String helmetIdMatch = statsDoubleHelmetManager != null
                ? statsDoubleHelmetManager.getHelmetIds().stream().filter(id -> id.equalsIgnoreCase(first)).findFirst().orElse(null)
                : null;
        if (helmetIdMatch != null) {
            ItemStack helmet = statsDoubleHelmetManager.createHelmet(helmetIdMatch);
            if (helmet == null) {
                sender.sendMessage("§c找不到頭盔: " + helmetIdMatch);
                return true;
            }
            Player target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (target.getInventory().firstEmpty() == -1) {
                sender.sendMessage("§c目標背包已滿。");
                return true;
            }
            target.getInventory().addItem(helmet);
            if (target.equals(sender)) sender.sendMessage("§a已獲得頭盔: §f" + helmetIdMatch + " §7(戴上時屬性 x2)");
            else sender.sendMessage("§a已發放頭盔 §f" + helmetIdMatch + " §a給 §f" + target.getName() + "§a。");
            return true;
        }
        // /gear give <洞窟特殊頭盔ID> [玩家] — 癒合之冠／法術頭盔等
        String caveHelmetIdMatch = caveSpecialHelmetManager != null
                ? caveSpecialHelmetManager.getHelmetIds().stream().filter(id -> id.equalsIgnoreCase(first)).findFirst().orElse(null)
                : null;
        if (caveHelmetIdMatch != null) {
            ItemStack helmet = caveSpecialHelmetManager.createHelmet(caveHelmetIdMatch);
            if (helmet == null) {
                sender.sendMessage("§c找不到頭盔: " + caveHelmetIdMatch);
                return true;
            }
            Player target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (target.getInventory().firstEmpty() == -1) {
                sender.sendMessage("§c目標背包已滿。");
                return true;
            }
            target.getInventory().addItem(helmet);
            if (target.equals(sender)) sender.sendMessage("§a已獲得洞窟頭盔: §f" + caveHelmetIdMatch);
            else sender.sendMessage("§a已發放洞窟頭盔 §f" + caveHelmetIdMatch + " §a給 §f" + target.getName() + "§a。");
            return true;
        }

        // /gear give second_wind_helmet [玩家]
        if ("second_wind_helmet".equalsIgnoreCase(first) && secondWindHelmetManager != null) {
            ItemStack helmet = secondWindHelmetManager.createHelmet();
            Player target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) { sender.sendMessage("§c請指定在線玩家。"); return true; }
            if (target.getInventory().firstEmpty() == -1) { sender.sendMessage("§c目標背包已滿。"); return true; }
            if (helmet != null) target.getInventory().addItem(helmet);
            sender.sendMessage("§a已發放絕境逢生頭盔。");
            return true;
        }
        // /gear give arrow_distance_helmet [玩家]
        if ("arrow_distance_helmet".equalsIgnoreCase(first) && arrowDistanceHelmetManager != null) {
            ItemStack helmet = arrowDistanceHelmetManager.createHelmet();
            Player target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) { sender.sendMessage("§c請指定在線玩家。"); return true; }
            if (target.getInventory().firstEmpty() == -1) { sender.sendMessage("§c目標背包已滿。"); return true; }
            if (helmet != null) target.getInventory().addItem(helmet);
            sender.sendMessage("§a已發放遠程蓄勢頭盔。");
            return true;
        }
        // /gear give adaptive_set [玩家] 或 adaptive_set <slot> [玩家]
        if ("adaptive_set".equalsIgnoreCase(first) && adaptiveSetManager != null) {
            Player target;
            String slot = args.length >= 3 && isSlot(args[2]) ? args[2] : null;
            if (slot != null) target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
            else target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) { sender.sendMessage("§c請指定在線玩家。"); return true; }
            if (slot != null) {
                ItemStack piece = adaptiveSetManager.createPiece(slot);
                if (piece != null) target.getInventory().addItem(piece);
                sender.sendMessage("§a已發放自適應 " + slot + "。");
            } else {
                for (String s : AdaptiveSetManager.getSlots()) { ItemStack piece = adaptiveSetManager.createPiece(s); if (piece != null) target.getInventory().addItem(piece); }
                sender.sendMessage("§a已發放一整套自適應套裝 (F3)。");
            }
            return true;
        }
        // /gear give cavern_explorer_set [玩家] 或 cavern_explorer_set <slot> [玩家]
        if ("cavern_explorer_set".equalsIgnoreCase(first) && cavernExplorerSetManager != null) {
            Player target;
            String slot = args.length >= 3 && isSlot(args[2]) ? args[2] : null;
            if (slot != null) target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
            else target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) { sender.sendMessage("§c請指定在線玩家。"); return true; }
            if (slot != null) {
                ItemStack piece = cavernExplorerSetManager.createPiece(slot);
                if (piece != null) target.getInventory().addItem(piece);
                sender.sendMessage("§a已發放深窟探險者 " + slot + "。");
            } else {
                for (String s : CavernExplorerSetManager.getSlots()) { ItemStack piece = cavernExplorerSetManager.createPiece(s); if (piece != null) target.getInventory().addItem(piece); }
                sender.sendMessage("§a已發放一整套深窟探險者套裝 (F2)。");
            }
            return true;
        }
        // /gear give blood_*_set [玩家] 或 blood_*_set <slot> [玩家]
        if (bloodSetManager != null && first != null && first.endsWith("_set") && first.startsWith("blood_")) {
            String setId = first;
            if (java.util.Arrays.asList(BloodSetManager.getSetIds()).contains(setId)) {
                Player target;
                String slot = args.length >= 3 && isSlot(args[2]) ? args[2] : null;
                if (slot != null) target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                else target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                if (target == null) { sender.sendMessage("§c請指定在線玩家。"); return true; }
                if (slot != null) {
                    ItemStack piece = bloodSetManager.createPiece(setId, slot);
                    if (piece != null) target.getInventory().addItem(piece);
                    sender.sendMessage("§a已發放傳說套裝 " + setId + " " + slot + "。");
                } else {
                    for (String s : BloodSetManager.getSlots()) { ItemStack piece = bloodSetManager.createPiece(setId, s); if (piece != null) target.getInventory().addItem(piece); }
                    sender.sendMessage("§a已發放一整套 " + setId + "。");
                }
                return true;
            }
        }

        // /gear give sieve [樓層] [玩家] — 一整套篩子身體（F1/F2/F3）
        // /gear give sieve <部位> [樓層] [玩家] — 單件篩子
        if ("sieve".equalsIgnoreCase(first)) {
            if (sieveSetManager == null) {
                sender.sendMessage("§c篩子身體套裝功能未載入。");
                return true;
            }
            Player target;
            boolean fullSet = false;
            String slot = null;
            String floor = "F1";
            if (args.length >= 3 && isSlot(args[2])) {
                slot = args[2];
                if (args.length >= 4 && isFloor(args[3])) {
                    floor = args[3];
                    target = args.length >= 5 ? sender.getServer().getPlayer(args[4]) : (sender instanceof Player p ? p : null);
                } else {
                    target = args.length >= 3 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                }
            } else {
                fullSet = true;
                if (args.length >= 3 && isFloor(args[2])) {
                    floor = args[2];
                    target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                } else {
                    target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                }
            }
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (fullSet) {
                for (String s : SieveSetConfig.SLOTS) {
                    ItemStack piece = sieveSetManager.createSievePiece(s, floor);
                    if (piece != null) target.getInventory().addItem(piece);
                }
                if (target.equals(sender)) sender.sendMessage("§a已獲得一整套篩子身體套裝 (" + floor + ")。");
                else sender.sendMessage("§a已發放一整套篩子身體套裝 (" + floor + ") 給 §f" + target.getName() + "§a。");
            } else {
                ItemStack piece = sieveSetManager.createSievePiece(slot, floor);
                if (piece == null) {
                    sender.sendMessage("§c無效部位: " + slot);
                    return true;
                }
                target.getInventory().addItem(piece);
                sender.sendMessage("§a已發放篩子身體 " + slot + " (" + floor + ") 給 " + target.getName());
            }
            return true;
        }

        // /gear give archer [樓層] [玩家] — 一整套骨弓套裝（F1/F2/F3）
        // /gear give archer <部位> [樓層] [玩家] — 單件骨弓
        if ("archer".equalsIgnoreCase(first)) {
            if (archerSetManager == null) {
                sender.sendMessage("§c骨弓套裝功能未載入。");
                return true;
            }
            Player target;
            boolean fullSet = false;
            String slot = null;
            String floor = "F1";
            if (args.length >= 3 && isSlot(args[2])) {
                slot = args[2];
                if (args.length >= 4 && isFloor(args[3])) {
                    floor = args[3];
                    target = args.length >= 5 ? sender.getServer().getPlayer(args[4]) : (sender instanceof Player p ? p : null);
                } else {
                    target = args.length >= 3 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                }
            } else {
                fullSet = true;
                if (args.length >= 3 && isFloor(args[2])) {
                    floor = args[2];
                    target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                } else {
                    target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                }
            }
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (fullSet) {
                for (String s : ArcherSetConfig.SLOTS) {
                    ItemStack piece = archerSetManager.createArcherPiece(s, floor);
                    if (piece != null) target.getInventory().addItem(piece);
                }
                if (target.equals(sender)) sender.sendMessage("§a已獲得一整套骨弓套裝 (" + floor + ")。");
                else sender.sendMessage("§a已發放一整套骨弓套裝 (" + floor + ") 給 §f" + target.getName() + "§a。");
            } else {
                ItemStack piece = archerSetManager.createArcherPiece(slot, floor);
                if (piece == null) {
                    sender.sendMessage("§c無效部位: " + slot);
                    return true;
                }
                target.getInventory().addItem(piece);
                sender.sendMessage("§a已發放骨弓 " + slot + " (" + floor + ") 給 " + target.getName());
            }
            return true;
        }

        // /gear give skeleton_soldier [樓層] [玩家] — 一整套骷髏士兵
        // /gear give skeleton_soldier <部位> [樓層] [玩家] — 單件
        if ("skeleton_soldier".equalsIgnoreCase(first)) {
            if (skeletonSoldierSetManager == null) {
                sender.sendMessage("§c白骨先鋒套裝功能未載入。");
                return true;
            }
            Player target;
            boolean fullSet = false;
            String slot = null;
            String floor = "F1";
            if (args.length >= 3 && isSlot(args[2])) {
                slot = args[2];
                if (args.length >= 4 && isFloor(args[3])) {
                    floor = args[3];
                    target = args.length >= 5 ? sender.getServer().getPlayer(args[4]) : (sender instanceof Player p ? p : null);
                } else {
                    target = args.length >= 3 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                }
            } else {
                fullSet = true;
                if (args.length >= 3 && isFloor(args[2])) {
                    floor = args[2];
                    target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                } else {
                    target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                }
            }
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (fullSet) {
                for (String s : SkeletonSoldierSetConfig.SLOTS) {
                    ItemStack piece = skeletonSoldierSetManager.createSkeletonSoldierPiece(s, floor);
                    if (piece != null) target.getInventory().addItem(piece);
                }
                if (target.equals(sender)) sender.sendMessage("§a已獲得一整套白骨先鋒套裝 (" + floor + ")。");
                else sender.sendMessage("§a已發放一整套白骨先鋒套裝 (" + floor + ") 給 §f" + target.getName() + "§a。");
            } else {
                ItemStack piece = skeletonSoldierSetManager.createSkeletonSoldierPiece(slot, floor);
                if (piece == null) {
                    sender.sendMessage("§c無效部位: " + slot);
                    return true;
                }
                target.getInventory().addItem(piece);
                sender.sendMessage("§a已發放白骨先鋒 " + slot + " (" + floor + ") 給 " + target.getName());
            }
            return true;
        }

        // /gear give skeleton_master [樓層] [玩家] — 一整套冥界
        // /gear give skeleton_master <部位> [樓層] [玩家] — 單件
        if ("skeleton_master".equalsIgnoreCase(first)) {
            if (skeletonMasterSetManager == null) {
                sender.sendMessage("§c冥界套裝功能未載入。");
                return true;
            }
            Player target;
            boolean fullSet = false;
            String slot = null;
            String floor = "F1";
            if (args.length >= 3 && isSlot(args[2])) {
                slot = args[2];
                if (args.length >= 4 && isFloor(args[3])) {
                    floor = args[3];
                    target = args.length >= 5 ? sender.getServer().getPlayer(args[4]) : (sender instanceof Player p ? p : null);
                } else {
                    target = args.length >= 3 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                }
            } else {
                fullSet = true;
                if (args.length >= 3 && isFloor(args[2])) {
                    floor = args[2];
                    target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                } else {
                    target = args.length >= 3 ? sender.getServer().getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                }
            }
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            if (fullSet) {
                for (String s : SkeletonMasterSetConfig.SLOTS) {
                    ItemStack piece = skeletonMasterSetManager.createSkeletonMasterPiece(s, floor);
                    if (piece != null) target.getInventory().addItem(piece);
                }
                if (target.equals(sender)) sender.sendMessage("§a已獲得一整套冥界套裝 (" + floor + ")。");
                else sender.sendMessage("§a已發放一整套冥界套裝 (" + floor + ") 給 §f" + target.getName() + "§a。");
            } else {
                ItemStack piece = skeletonMasterSetManager.createSkeletonMasterPiece(slot, floor);
                if (piece == null) {
                    sender.sendMessage("§c無效部位: " + slot);
                    return true;
                }
                target.getInventory().addItem(piece);
                sender.sendMessage("§a已發放冥界 " + slot + " (" + floor + ") 給 " + target.getName());
            }
            return true;
        }

        // give <部位> [樓層] [玩家]
        if (isSlot(first)) {
            String slot = first;
            String floor = "F1";
            Player target = sender instanceof Player p ? p : null;
            if (args.length >= 3) {
                if (isFloor(args[2])) {
                    floor = args[2];
                    target = args.length >= 4 ? sender.getServer().getPlayer(args[3]) : (sender instanceof Player p ? p : null);
                } else {
                    target = sender.getServer().getPlayer(args[2]);
                }
            }
            if (target == null) {
                sender.sendMessage("§c請指定在線玩家。");
                return true;
            }
            ItemStack piece = gearManager.createDefeatTankPiece(slot, floor);
            if (piece == null) {
                sender.sendMessage("§c無效部位: " + slot);
                return true;
            }
            target.getInventory().addItem(piece);
            sender.sendMessage("§a已發放堅壁 " + slot + " (" + floor + ") 給 " + target.getName());
            return true;
        }

        // give <玩家> — 一整套堅壁
        Player target = sender.getServer().getPlayer(first);
        if (target == null) {
            sender.sendMessage("§c找不到玩家: " + first);
            return true;
        }
        giveFullSet(sender instanceof Player p ? p : target, target, "F1");
        sender.sendMessage("§a已發放一整套堅壁套裝 (F1) 給 " + target.getName());
        return true;
    }

    private void giveFullSet(Player source, Player target, String floor) {
        for (String slot : DefeatTankSetConfig.SLOTS) {
            ItemStack piece = gearManager.createDefeatTankPiece(slot, floor);
            if (piece != null) target.getInventory().addItem(piece);
        }
    }

    private static boolean isSlot(String s) {
        if (s == null) return false;
        switch (s.toLowerCase()) {
            case "helmet": case "chest": case "leggings": case "boots":
                return true;
            default:
                return false;
        }
    }

    private static boolean isFloor(String s) {
        if (s == null) return false;
        return "F1".equalsIgnoreCase(s) || "F2".equalsIgnoreCase(s) || "F3".equalsIgnoreCase(s);
    }

    private void runGlobalReload(CommandSender sender) {
        gearManager.reload();
        if (skullManager != null) skullManager.reload();
        if (weaponManager != null) weaponManager.reload();
        if (sieveSetManager != null) sieveSetManager.reload();
        if (archerSetManager != null) archerSetManager.reload();
        if (skeletonSoldierSetManager != null) skeletonSoldierSetManager.reload();
        if (skeletonMasterSetManager != null) skeletonMasterSetManager.reload();
        if (undergroundCaveHelmetManager != null) undergroundCaveHelmetManager.reload();
        if (statsDoubleHelmetManager != null) statsDoubleHelmetManager.reload();
        if (caveSpecialHelmetManager != null) caveSpecialHelmetManager.reload();
        if (secondWindHelmetManager != null) secondWindHelmetManager.reload();
        if (arrowDistanceHelmetManager != null) arrowDistanceHelmetManager.reload();
        if (cavernExplorerSetManager != null) cavernExplorerSetManager.reload();
        if (adaptiveSetManager != null) adaptiveSetManager.reload();
        if (bloodSetManager != null) bloodSetManager.reload();
        if (qualityUpgradeItemManager != null) qualityUpgradeItemManager.reload();
        int weaponsUpdated = 0;
        int skullsUpdated = 0;
        int qualityUpgradeUpdated = 0;
        for (Player p : sender.getServer().getOnlinePlayers()) {
            if (weaponManager != null) {
                var inv = p.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    if (weaponManager.updateItemFromConfig(inv.getItem(i))) weaponsUpdated++;
                }
                if (weaponManager.updateItemFromConfig(inv.getItemInOffHand())) weaponsUpdated++;
                for (ItemStack armor : inv.getArmorContents()) {
                    if (armor != null && !armor.getType().isAir() && weaponManager.updateItemFromConfig(armor)) weaponsUpdated++;
                }
            }
            gearManager.refreshSetProgressLore(p);
            if (skullManager != null) {
                var inv = p.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    if (skullManager.updateItemFromConfig(inv.getItem(i))) skullsUpdated++;
                }
                for (ItemStack armor : inv.getArmorContents()) {
                    if (armor != null && !armor.getType().isAir() && skullManager.updateItemFromConfig(armor)) skullsUpdated++;
                }
            }
            if (qualityUpgradeItemManager != null) {
                var inv = p.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    if (qualityUpgradeItemManager.updateItemFromConfig(inv.getItem(i))) qualityUpgradeUpdated++;
                }
            }
        }
        sender.sendMessage("§a已重載：堅壁套裝、篩子身體、骨弓套裝、白骨先鋒、冥界、永恆的面具、品質升級道具、地下洞穴頭盔、黃金/鑽石頭盔、武器配置。");
        sender.sendMessage("§7已刷新在線玩家背包內：§f" + weaponsUpdated + " §7件武器、§f" + skullsUpdated + " §7件永恆的面具、§f" + qualityUpgradeUpdated + " §7件品質升級道具；堅壁 lore 已更新。");
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage("§e用法（裝備／武器／永恆的面具）:");
        sender.sendMessage("§f  /gear reload §7— 重載配置並刷新背包內對應物品");
        sender.sendMessage("§f  /gear give §7— 給自己一整套堅壁套裝 (F1)");
        sender.sendMessage("§f  /gear give <玩家> §7— 給指定玩家一整套堅壁 (F1)");
        sender.sendMessage("§f  /gear give <部位> [樓層] [玩家] §7— 堅壁單件 §7(helmet|chest|leggings|boots, F1|F2|F3)");
        sender.sendMessage("§f  /gear give sieve [樓層] [玩家] §7— 一整套篩子身體 §7(F1|F2|F3)");
        sender.sendMessage("§f  /gear give sieve <部位> [樓層] [玩家] §7— 篩子身體單件");
        sender.sendMessage("§f  /gear give archer [樓層] [玩家] §7— 一整套骨弓套裝 §7(每件箭矢傷害+5%)");
        sender.sendMessage("§f  /gear give archer <部位> [樓層] [玩家] §7— 骨弓單件");
        sender.sendMessage("§f  /gear give skeleton_soldier [樓層] [玩家] §7— 一整套白骨先鋒 §7(套裝獎勵+25%)");
        sender.sendMessage("§f  /gear give skeleton_soldier <部位> [樓層] [玩家] §7— 白骨先鋒單件");
        sender.sendMessage("§f  /gear give skeleton_master [樓層] [玩家] §7— 一整套冥界 §7(套裝+25%，胸甲弓不耗箭)");
        sender.sendMessage("§f  /gear give skeleton_master <部位> [樓層] [玩家] §7— 冥界單件");
        sender.sendMessage("§f  /gear give skull [玩家] §7— 永恆的面具");
        sender.sendMessage("§f  /gear give quality_upgrade [玩家] §7— 品質升級道具（頭顱，使用後品質提升一級）");
        sender.sendMessage("§f  /gear give cave_helmet <F1|F2|F3> [玩家] §7— 地下洞穴頭盔 §7(三種獨立材質與屬性)");
        sender.sendMessage("§f  /gear give <頭盔ID> [玩家] §7— 黃金/鑽石頭盔 §7(戴上時屬性 x2，如 golden_villager_helmet)");
        sender.sendMessage("§f  /gear give weapon <武器ID> [玩家] §7— 發放武器");
    }
}
