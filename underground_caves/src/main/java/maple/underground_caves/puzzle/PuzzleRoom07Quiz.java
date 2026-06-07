package maple.underground_caves.puzzle;

import maple.underground_caves.Test_dungeons;
import maple.underground_caves.dungeon.DungeonSecrets;
import maple.underground_caves.dungeon.StructureTemplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class PuzzleRoom07Quiz {
    private static final int QUESTIONS_PER_ROOM = 3;
    private static final int OPTIONS_TOTAL = 3;

    private static final LocalPos PARTICLE_FROM = new LocalPos(15, 39, 16);
    private static final LocalPos[] OPTION_BLOCKS = new LocalPos[]{
            new LocalPos(20, 24, 6),
            new LocalPos(15, 24, 9),
            new LocalPos(10, 24, 6),
    };

    private final Test_dungeons plugin;
    private final Map<String, RoomState> roomsByKey = new HashMap<>();
    private final List<Question> questionBank = new ArrayList<>();
    private long configLoadedAt;

    public PuzzleRoom07Quiz(Test_dungeons plugin) {
        this.plugin = plugin;
    }

    public void onDisable() {
        for (RoomState room : List.copyOf(roomsByKey.values())) {
            if (room == null) continue;
            cleanupRoom(room);
        }
        roomsByKey.clear();
    }

    public void registerPuzzleRoom07(World world, Location roomOrigin, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        if (world == null || roomOrigin == null || rotation == null) return;
        Location base = roomOrigin.getBlock().getLocation();
        String roomKey = world.getName() + ":" + base.getBlockX() + ":" + base.getBlockY() + ":" + base.getBlockZ();

        RoomState old = roomsByKey.remove(roomKey);
        if (old != null) cleanupRoom(old);

        maybeLoadConfig();
        RoomState room = new RoomState(roomKey, world.getName(), rotation, base.getBlockX(), base.getBlockY(), base.getBlockZ(), sizeX, sizeZ);
        room.particleFrom = toWorldLocation(base, PARTICLE_FROM, rotation, sizeX, sizeZ).add(0.5, 0.5, 0.5);
        for (int i = 0; i < OPTIONS_TOTAL; i++) {
            room.optionBlocks[i] = toWorldBlock(base, OPTION_BLOCKS[i], rotation, sizeX, sizeZ);
            room.optionHoloSpots[i] = new Location(world, room.optionBlocks[i].x + 0.5, room.optionBlocks[i].y + 1.2, room.optionBlocks[i].z + 0.5);
        }

        roomsByKey.put(roomKey, room);
        startPresenceScan(room);
    }

    public boolean handleInteract(PlayerInteractEvent event) {
        if (event == null) return false;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return false;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return false;
        RoomState room = findRoomByBlock(clicked.getWorld(), clicked.getX(), clicked.getY(), clicked.getZ());
        if (room == null) return false;

        int option = resolveOptionIndex(room, clicked);
        if (option < 0) return false;

        event.setCancelled(true);
        if (!room.started || room.failed || room.completed) return true;
        if (!room.revealComplete) {
            sendToNearby(room, Component.text("请先等待 3 个选项全部显示完毕", NamedTextColor.YELLOW));
            return true;
        }

        Question q = room.currentQuestion;
        if (q == null) {
            sendToAllInWorld(room, Component.text("题目加载失败", NamedTextColor.RED));
            room.failed = true;
            return true;
        }

        boolean correct = option == q.answerIndex;
        String correctAns = optionLabel(q.answerIndex) + ") " + q.options[q.answerIndex];
        if (!correct) {
            room.failed = true;
            sendToAllInWorld(room, Component.text("回答错误：失败", NamedTextColor.RED));
            sendToAllInWorld(room, Component.text("正确答案：" + correctAns, NamedTextColor.GRAY));
            room.currentQuestion = null;
            cleanupHolograms(room);
            stopRevealTasks(room);
            return true;
        }

        sendToAllInWorld(room, Component.text("回答正确 ✓ 答案：" + correctAns, NamedTextColor.GREEN));
        room.questionIndex++;
        room.currentQuestion = null;
        if (room.questionIndex >= QUESTIONS_PER_ROOM) {
            room.completed = true;
            World w = clicked.getWorld();
            markPuzzleCompleted(w, room);
            sendToAllInWorld(room, Component.text("三题全部答对（测试通过）", NamedTextColor.GOLD));
            cleanupHolograms(room);
            stopRevealTasks(room);
            return true;
        }

        room.revealComplete = false;
        cleanupHolograms(room);
        stopRevealTasks(room);
        startQuestion(room, 20L);
        return true;
    }

    private void startPresenceScan(RoomState room) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (room == null) return;
        if (room.presenceTask != null) {
            room.presenceTask.cancel();
            room.presenceTask = null;
        }
        room.presenceTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (room.started || room.failed || room.completed) return;
                World world = Bukkit.getWorld(room.worldName);
                if (world == null) return;
                for (Player p : world.getPlayers()) {
                    if (p == null) continue;
                    Location loc = p.getLocation();
                    if (loc == null) continue;
                    if (!insideRoom(room, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) continue;
                    startRoom(room);
                    break;
                }
            }
        };
        room.presenceTask.runTaskTimer(plugin, 5L, 10L);
    }

    private void startRoom(RoomState room) {
        if (room == null) return;
        if (room.started) return;
        room.started = true;
        room.failed = false;
        room.completed = false;
        room.questionIndex = 0;
        room.revealComplete = false;
        room.currentQuestion = null;
        room.questions.clear();

        List<Question> selected = pickQuestions(QUESTIONS_PER_ROOM);
        if (selected.size() < QUESTIONS_PER_ROOM) {
            room.failed = true;
            sendToAllInWorld(room, Component.text("题库不足，至少需要 3 道题", NamedTextColor.RED));
            return;
        }
        room.questions.addAll(selected);

        sendToAllInWorld(room, Component.text("进入问答房间", NamedTextColor.AQUA));
        sendToAllInWorld(room, Component.text("请等待提示后再作答", NamedTextColor.AQUA));
        startQuestion(room, 25L);
    }

    private void startQuestion(RoomState room, long delayTicks) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (room == null) return;
        if (room.questionTask != null) {
            room.questionTask.cancel();
            room.questionTask = null;
        }

        int idx = room.questionIndex;
        room.questionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (room.failed || room.completed) return;
                Question q = room.questions.size() > idx ? room.questions.get(idx) : null;
                if (q == null) {
                    room.failed = true;
                    sendToAllInWorld(room, Component.text("题目加载失败", NamedTextColor.RED));
                    return;
                }
                room.revealComplete = false;
                room.currentQuestion = null;
                cleanupHolograms(room);
                stopRevealTasks(room);

                Question shown = shuffledQuestion(q, room.rng);
                room.currentQuestion = shown;
                sendToAllInWorld(room, Component.text("第 " + (idx + 1) + " 题", NamedTextColor.YELLOW));
                sendToAllInWorld(room, Component.text(shown.question, NamedTextColor.WHITE));
                sendToAllInWorld(room, Component.text("A) " + shown.options[0], NamedTextColor.AQUA));
                sendToAllInWorld(room, Component.text("B) " + shown.options[1], NamedTextColor.AQUA));
                sendToAllInWorld(room, Component.text("C) " + shown.options[2], NamedTextColor.AQUA));

                startRevealSequence(room, shown);
            }
        };
        room.questionTask.runTaskLater(plugin, Math.max(1L, delayTicks));
    }

    private Question shuffledQuestion(Question base, Random rng) {
        if (base == null) return null;
        if (rng == null) rng = new Random(System.nanoTime());
        int[] perm = new int[]{0, 1, 2};
        for (int i = perm.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = perm[i];
            perm[i] = perm[j];
            perm[j] = tmp;
        }
        String[] opts = new String[OPTIONS_TOTAL];
        int newAnswer = -1;
        for (int i = 0; i < OPTIONS_TOTAL; i++) {
            int oldIdx = perm[i];
            opts[i] = base.options[oldIdx];
            if (oldIdx == base.answerIndex) newAnswer = i;
        }
        return new Question(base.id, base.question, opts, newAnswer);
    }

    private void startRevealSequence(RoomState room, Question q) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (room == null || q == null) return;
        stopRevealTasks(room);
        long step = 30L;

        room.revealTask = new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (room.failed || room.completed) {
                    stopRevealTasks(room);
                    return;
                }
                if (i >= OPTIONS_TOTAL) return;
                showOption(room, q, i);
                i++;
                if (i >= OPTIONS_TOTAL) {
                    room.revealComplete = true;
                    sendToAllInWorld(room, Component.text("现在可以作答（点击 A/B/C 的方块或按钮）", NamedTextColor.GREEN));
                    cancel();
                    room.revealTask = null;
                }
            }
        };
        room.revealTask.runTaskTimer(plugin, 5L, step);
    }

    private void showOption(RoomState room, Question q, int optionIndex) {
        if (room == null || q == null) return;
        if (optionIndex < 0 || optionIndex >= OPTIONS_TOTAL) return;
        Location to = room.optionHoloSpots[optionIndex];
        if (to == null) return;
        ensureOptionHologram(room, q, optionIndex);

        stopBeamTask(room);
        startBeamTask(room, room.particleFrom, to.clone().subtract(0, 0.7, 0), optionIndex);
    }

    private void ensureOptionHologram(RoomState room, Question q, int optionIndex) {
        if (room == null || q == null) return;
        if (optionIndex < 0 || optionIndex >= OPTIONS_TOTAL) return;
        World w = Bukkit.getWorld(room.worldName);
        if (w == null) return;
        Location at = room.optionHoloSpots[optionIndex];
        if (at == null) return;

        UUID existing = room.optionHolograms[optionIndex];
        if (existing != null) {
            Entity e = w.getEntity(existing);
            if (e instanceof ArmorStand as && !as.isDead()) {
                as.teleport(at);
                as.customName(Component.text(optionLabel(optionIndex) + ") " + q.options[optionIndex], NamedTextColor.AQUA));
                as.setCustomNameVisible(true);
                ensureHoloFacingTask(room);
                return;
            }
            room.optionHolograms[optionIndex] = null;
        }

        ArmorStand as = w.spawn(at, ArmorStand.class, a -> {
            a.setVisible(false);
            a.setMarker(true);
            a.setGravity(false);
            a.setSilent(true);
            a.setCollidable(false);
            a.setInvulnerable(true);
            a.setSmall(true);
            a.customName(Component.text(optionLabel(optionIndex) + ") " + q.options[optionIndex], NamedTextColor.AQUA));
            a.setCustomNameVisible(true);
        });
        room.optionHolograms[optionIndex] = as.getUniqueId();
        ensureHoloVisibilityTask(room);
        Set<UUID> hiddenFrom = room.holoHiddenFrom.computeIfAbsent(as.getUniqueId(), k -> new HashSet<>());
        for (Player p : w.getPlayers()) {
            if (p == null || !p.isOnline()) continue;
            if (!insideRoom(room, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ())) {
                try {
                    p.hideEntity(plugin, as);
                    hiddenFrom.add(p.getUniqueId());
                } catch (Throwable ignored) {}
            }
        }
        ensureHoloFacingTask(room);
    }

    /** 只有進到房間內的玩家才能看到答案盔甲架，每個玩家單獨計算；有遮擋或不在房內則對該玩家隱藏 */
    private void ensureHoloVisibilityTask(RoomState room) {
        if (plugin == null || !plugin.isEnabled() || room == null) return;
        if (room.holoVisibilityTask != null) return;

        room.holoVisibilityTask = new BukkitRunnable() {
            @Override
            public void run() {
                World world = Bukkit.getWorld(room.worldName);
                if (world == null) return;
                List<Entity> stands = new ArrayList<>();
                for (int i = 0; i < OPTIONS_TOTAL; i++) {
                    UUID id = room.optionHolograms[i];
                    if (id == null) continue;
                    Entity e = world.getEntity(id);
                    if (e instanceof ArmorStand) stands.add(e);
                }
                if (room.revealStand != null && room.revealStand.isValid() && !room.revealStand.isDead())
                    stands.add(room.revealStand);

                for (Entity stand : stands) {
                    UUID standId = stand.getUniqueId();
                    Set<UUID> hiddenFrom = room.holoHiddenFrom.computeIfAbsent(standId, k -> new HashSet<>());
                    for (Player p : world.getPlayers()) {
                        if (p == null || !p.isOnline() || !Objects.equals(p.getWorld().getName(), room.worldName)) continue;
                        UUID pid = p.getUniqueId();
                        boolean inRoom = insideRoom(room, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
                        boolean currentlyHidden = hiddenFrom.contains(pid);
                        if (inRoom && currentlyHidden) {
                            try {
                                p.showEntity(plugin, stand);
                                hiddenFrom.remove(pid);
                            } catch (Throwable ignored) {}
                        } else if (!inRoom && !currentlyHidden) {
                            try {
                                p.hideEntity(plugin, stand);
                                hiddenFrom.add(pid);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        };
        room.holoVisibilityTask.runTaskTimer(plugin, 1L, 1L);
    }

    private void ensureHoloFacingTask(RoomState room) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (room == null) return;
        if (room.holoFacingTask != null) return;

        room.holoFacingTask = new BukkitRunnable() {
            @Override
            public void run() {
                World world = Bukkit.getWorld(room.worldName);
                if (world == null) return;
                Player target = nearestPlayer(world, room);
                if (target == null) return;
                Location eye = target.getEyeLocation();
                for (int i = 0; i < OPTIONS_TOTAL; i++) {
                    UUID id = room.optionHolograms[i];
                    if (id == null) continue;
                    Entity e = world.getEntity(id);
                    if (!(e instanceof ArmorStand as)) continue;
                    Location here = as.getLocation();
                    Vector dir = eye.toVector().subtract(here.toVector());
                    float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                    as.setRotation(yaw, 0.0f);
                }
            }
        };
        room.holoFacingTask.runTaskTimer(plugin, 0L, 2L);
    }

    private void startBeamTask(RoomState room, Location from, Location to, int optionIndex) {
        if (plugin == null || !plugin.isEnabled()) return;
        if (room == null || from == null || to == null) return;
        Color c = switch (optionIndex) {
            case 0 -> Color.fromRGB(255, 255, 255);
            case 1 -> Color.fromRGB(255, 216, 0);
            case 2 -> Color.fromRGB(0, 150, 255);
            default -> Color.fromRGB(0, 255, 255);
        };
        int haloR = Math.min(255, (c.getRed() + 160) / 2);
        int haloG = Math.min(255, (c.getGreen() + 80) / 2);
        int haloB = Math.min(255, (c.getBlue() + 220) / 2);
        Particle.DustOptions dustCore = new Particle.DustOptions(c, 1.55f);
        Particle.DustOptions dustHalo = new Particle.DustOptions(Color.fromRGB(haloR, haloG, haloB), 1.1f);
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len <= 0.01) return;
        Vector norm = dir.clone().multiply(1.0 / len);
        int ticksTotal = (int) Math.max(8, Math.min(20, Math.ceil(len / 1.1)));
        double speed = len / ticksTotal;
        Random rng = new Random(System.nanoTime());
        room.beamTask = new BukkitRunnable() {
            int ticks = 0;
            double progress = 0.0;

            @Override
            public void run() {
                ticks++;
                World w = from.getWorld();
                if (w == null) {
                    cancel();
                    room.beamTask = null;
                    return;
                }
                if (ticks == 1) {
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, from.getX(), from.getY(), from.getZ(), 18, 0.20, 0.20, 0.20, 0.0);
                    w.spawnParticle(Particle.WITCH, from.getX(), from.getY(), from.getZ(), 18, 0.22, 0.22, 0.22, 0.0);
                    w.spawnParticle(Particle.ENCHANT, from.getX(), from.getY(), from.getZ(), 26, 0.28, 0.28, 0.28, 0.0);
                }

                progress = Math.min(len, progress + speed);
                Vector cur = from.toVector().add(norm.clone().multiply(progress));

                w.spawnParticle(Particle.DUST, cur.getX(), cur.getY(), cur.getZ(), 2, 0.02, 0.02, 0.02, 0.0, dustCore);
                w.spawnParticle(Particle.ENCHANT, cur.getX(), cur.getY(), cur.getZ(), 3, 0.14, 0.14, 0.14, 0.0);
                w.spawnParticle(Particle.PORTAL, cur.getX(), cur.getY(), cur.getZ(), 1, 0.06, 0.06, 0.06, 0.01);
                if (rng.nextDouble() < 0.35) {
                    w.spawnParticle(Particle.DRAGON_BREATH, cur.getX(), cur.getY(), cur.getZ(), 1, 0.08, 0.08, 0.08, 0.01);
                }
                if ((ticks & 1) == 0) w.spawnParticle(Particle.END_ROD, cur.getX(), cur.getY(), cur.getZ(), 1, 0.06, 0.06, 0.06, 0.01);

                Vector baseUp = Math.abs(norm.getY()) > 0.85 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
                Vector right = norm.clone().crossProduct(baseUp);
                if (right.lengthSquared() < 1e-6) right = norm.clone().crossProduct(new Vector(0, 0, 1));
                right.normalize();
                Vector up = right.clone().crossProduct(norm).normalize();

                double angle = ticks * 0.95;
                double radius = 0.16 + 0.05 * Math.sin(ticks * 0.7);
                int ring = 6;
                for (int j = 0; j < ring; j++) {
                    double a = angle + j * (Math.PI * 2.0 / ring);
                    Vector off = right.clone().multiply(Math.cos(a) * radius).add(up.clone().multiply(Math.sin(a) * radius));
                    Vector p = cur.clone().add(off);
                    w.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0.0, dustHalo);
                }

                int streakPoints = 3 + rng.nextInt(3);
                for (int i = 0; i < streakPoints; i++) {
                    double back = 0.18 * i;
                    Vector p = cur.clone().subtract(norm.clone().multiply(back));
                    double ox = (rng.nextDouble() - 0.5) * 0.08;
                    double oy = (rng.nextDouble() - 0.5) * 0.08;
                    double oz = (rng.nextDouble() - 0.5) * 0.08;
                    w.spawnParticle(Particle.DUST, p.getX() + ox, p.getY() + oy, p.getZ() + oz, 1, 0, 0, 0, 0.0, dustHalo);
                }

                if (ticks == Math.max(2, ticksTotal / 2)) {
                    w.spawnParticle(Particle.FIREWORK, cur.getX(), cur.getY(), cur.getZ(), 14, 0.20, 0.20, 0.20, 0.08);
                    w.spawnParticle(Particle.DUST, cur.getX(), cur.getY(), cur.getZ(), 10, 0.12, 0.12, 0.12, 0.0, dustHalo);
                }

                if (progress >= len - 1e-3 || ticks >= ticksTotal + 3) {
                    w.spawnParticle(Particle.DRAGON_BREATH, to.getX(), to.getY(), to.getZ(), 18, 0.22, 0.18, 0.22, 0.02);
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, to.getX(), to.getY(), to.getZ(), 16, 0.20, 0.20, 0.20, 0.0);
                    w.spawnParticle(Particle.FIREWORK, to.getX(), to.getY(), to.getZ(), 16, 0.22, 0.22, 0.22, 0.08);
                    w.spawnParticle(Particle.END_ROD, to.getX(), to.getY(), to.getZ(), 8, 0.16, 0.16, 0.16, 0.02);
                    w.spawnParticle(Particle.DUST, to.getX(), to.getY(), to.getZ(), 10, 0.14, 0.14, 0.14, 0.0, dustCore);
                    w.spawnParticle(Particle.DUST, to.getX(), to.getY(), to.getZ(), 14, 0.18, 0.18, 0.18, 0.0, dustHalo);
                    cancel();
                    room.beamTask = null;
                }
            }
        };
        room.beamTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void stopBeamTask(RoomState room) {
        if (room == null) return;
        if (room.beamTask != null) {
            room.beamTask.cancel();
            room.beamTask = null;
        }
    }

    private ArmorStand spawnRevealStand(RoomState room) {
        if (room == null) return null;
        World w = Bukkit.getWorld(room.worldName);
        if (w == null) return null;
        Location at = room.optionHoloSpots[0] != null ? room.optionHoloSpots[0].clone() : new Location(w, room.baseX + 0.5, room.baseY + 0.5, room.baseZ + 0.5);
        return w.spawn(at, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setMarker(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setCollidable(false);
            as.setInvulnerable(true);
            as.setSmall(true);
            as.customName(Component.text("", NamedTextColor.AQUA));
            as.setCustomNameVisible(true);
        });
    }

    private Player nearestPlayer(World world, RoomState room) {
        if (world == null || room == null) return null;
        Player best = null;
        double bestD = Double.MAX_VALUE;
        double cx = room.baseX + room.sizeX / 2.0 + 0.5;
        double cz = room.baseZ + room.sizeZ / 2.0 + 0.5;
        for (Player p : world.getPlayers()) {
            if (p == null) continue;
            Location l = p.getLocation();
            double dx = l.getX() - cx;
            double dz = l.getZ() - cz;
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private void cleanupRoom(RoomState room) {
        if (room == null) return;
        if (room.presenceTask != null) {
            room.presenceTask.cancel();
            room.presenceTask = null;
        }
        if (room.questionTask != null) {
            room.questionTask.cancel();
            room.questionTask = null;
        }
        stopRevealTasks(room);
        cleanupHolograms(room);
    }

    private void stopRevealTasks(RoomState room) {
        if (room == null) return;
        if (room.revealTask != null) {
            room.revealTask.cancel();
            room.revealTask = null;
        }
        stopBeamTask(room);
        if (room.revealStand != null) {
            room.holoHiddenFrom.remove(room.revealStand.getUniqueId());
            room.revealStand.remove();
            room.revealStand = null;
        }
    }

    private void cleanupHolograms(RoomState room) {
        if (room == null) return;
        World world = Bukkit.getWorld(room.worldName);
        if (world != null) {
            for (int i = 0; i < OPTIONS_TOTAL; i++) {
                UUID id = room.optionHolograms[i];
                if (id == null) continue;
                room.holoHiddenFrom.remove(id);
                Entity e = world.getEntity(id);
                if (e != null) e.remove();
                room.optionHolograms[i] = null;
            }
        }
        if (room.holoFacingTask != null) {
            room.holoFacingTask.cancel();
            room.holoFacingTask = null;
        }
        if (room.holoVisibilityTask != null) {
            room.holoVisibilityTask.cancel();
            room.holoVisibilityTask = null;
        }
        room.holoHiddenFrom.clear();
    }

    private RoomState findRoomByBlock(World world, int x, int y, int z) {
        if (world == null) return null;
        String wn = world.getName();
        for (RoomState room : List.copyOf(roomsByKey.values())) {
            if (room == null) continue;
            if (!wn.equals(room.worldName)) continue;
            if (!insideRoom(room, x, y, z)) continue;
            return room;
        }
        return null;
    }

    private boolean insideRoom(RoomState room, int x, int y, int z) {
        if (room == null) return false;
        if (x < room.baseX || x >= room.baseX + room.sizeX) return false;
        if (z < room.baseZ || z >= room.baseZ + room.sizeZ) return false;
        int minY = room.baseY - 5;
        int maxY = room.baseY + 80;
        return y >= minY && y <= maxY;
    }

    private int resolveOptionIndex(RoomState room, Block clicked) {
        if (room == null || clicked == null) return -1;
        int cx = clicked.getX();
        int cy = clicked.getY();
        int cz = clicked.getZ();
        for (int i = 0; i < OPTIONS_TOTAL; i++) {
            BlockPos b = room.optionBlocks[i];
            if (b == null) continue;
            int dx = Math.abs(cx - b.x);
            int dy = Math.abs(cy - b.y);
            int dz = Math.abs(cz - b.z);
            if (dx + dy + dz <= 1) return i;
        }
        return -1;
    }

    private boolean isNearRoom(Location loc, RoomState room) {
        if (loc == null || room == null) return false;
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(room.worldName)) return false;
        int cx = room.baseX + room.sizeX / 2;
        int cz = room.baseZ + room.sizeZ / 2;
        double dx = loc.getX() - (cx + 0.5);
        double dz = loc.getZ() - (cz + 0.5);
        return dx * dx + dz * dz <= 35.0 * 35.0;
    }

    private void sendToNearby(RoomState room, Component msg) {
        if (room == null || msg == null) return;
        World w = Bukkit.getWorld(room.worldName);
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (p == null) continue;
            Location loc = p.getLocation();
            if (loc == null) continue;
            if (!insideRoom(room, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) continue;
            p.sendMessage(msg);
        }
    }

    /** 發送給該地牢世界的全部玩家（用於廣播問答題目與答案） */
    private void sendToAllInWorld(RoomState room, Component msg) {
        if (room == null || msg == null) return;
        World w = Bukkit.getWorld(room.worldName);
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private void markPuzzleCompleted(World world, RoomState room) {
        if (plugin == null || world == null || room == null) return;
        DungeonSecrets secrets = plugin.getDungeonSecrets();
        if (secrets == null) return;
        secrets.markPuzzleCompleted(world, new Location(world, room.baseX, room.baseY, room.baseZ));
    }

    private String optionLabel(int i) {
        return switch (i) {
            case 0 -> "A";
            case 1 -> "B";
            case 2 -> "C";
            default -> String.valueOf(i);
        };
    }

    private List<Question> pickQuestions(int count) {
        maybeLoadConfig();
        if (questionBank.isEmpty() || count <= 0) return List.of();
        Random rng = new Random(System.nanoTime());
        Set<Integer> used = new HashSet<>();
        List<Question> out = new ArrayList<>();
        int limit = Math.min(questionBank.size(), count);
        while (out.size() < limit && used.size() < questionBank.size()) {
            int idx = rng.nextInt(questionBank.size());
            if (!used.add(idx)) continue;
            out.add(questionBank.get(idx));
        }
        return out;
    }

    private void maybeLoadConfig() {
        if (plugin == null) return;
        long now = System.currentTimeMillis();
        if (!questionBank.isEmpty() && now - configLoadedAt < 5000) return;
        configLoadedAt = now;
        questionBank.clear();

        File file = configFile();
        if (!file.exists()) {
            try {
                plugin.saveResource("puzzle_room_07.yml", false);
            } catch (Throwable ignored) {
            }
        }
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection qs = cfg.getConfigurationSection("questions");
        if (qs == null) return;
        for (String key : qs.getKeys(false)) {
            ConfigurationSection q = qs.getConfigurationSection(key);
            if (q == null) continue;
            String question = q.getString("question");
            String a = q.getString("options.A");
            String b = q.getString("options.B");
            String c = q.getString("options.C");
            String ans = q.getString("answer");
            if (question == null || a == null || b == null || c == null || ans == null) continue;
            int answerIndex = switch (ans.trim().toUpperCase(Locale.ROOT)) {
                case "A" -> 0;
                case "B" -> 1;
                case "C" -> 2;
                default -> -1;
            };
            if (answerIndex < 0) continue;
            questionBank.add(new Question(key, question, new String[]{a, b, c}, answerIndex));
        }
    }

    private File configFile() {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "puzzle_room_07.yml");
    }

    private static BlockPos toWorldBlock(Location base, LocalPos local, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        int[] rotated = rotateLocalXZ(local.x, local.z, rotation, sizeX, sizeZ);
        int wx = base.getBlockX() + rotated[0];
        int wy = base.getBlockY() + local.y;
        int wz = base.getBlockZ() + rotated[1];
        return new BlockPos(wx, wy, wz);
    }

    private static Location toWorldLocation(Location base, LocalPos local, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        int[] rotated = rotateLocalXZ(local.x, local.z, rotation, sizeX, sizeZ);
        return new Location(base.getWorld(), base.getBlockX() + rotated[0], base.getBlockY() + local.y, base.getBlockZ() + rotated[1]);
    }

    private static int[] rotateLocalXZ(int x, int z, StructureTemplate.Rotation rotation, int sizeX, int sizeZ) {
        return switch (rotation) {
            case R0 -> new int[]{x, z};
            case R90 -> new int[]{z, sizeX - 1 - x};
            case R180 -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
            case R270 -> new int[]{sizeZ - 1 - z, x};
        };
    }

    private record LocalPos(int x, int y, int z) {
    }

    private record BlockPos(int x, int y, int z) {
    }

    private record Question(String id, String question, String[] options, int answerIndex) {
    }

    private static final class RoomState {
        private final String roomKey;
        private final String worldName;
        private final StructureTemplate.Rotation rotation;
        private final int baseX;
        private final int baseY;
        private final int baseZ;
        private final int sizeX;
        private final int sizeZ;

        private boolean started;
        private boolean revealComplete;
        private boolean failed;
        private boolean completed;
        private int questionIndex;
        private Question currentQuestion;

        private Location particleFrom;
        private final BlockPos[] optionBlocks = new BlockPos[OPTIONS_TOTAL];
        private final Location[] optionHoloSpots = new Location[OPTIONS_TOTAL];

        private final List<Question> questions = new ArrayList<>();
        private final Random rng = new Random(System.nanoTime());

        private BukkitRunnable presenceTask;
        private BukkitRunnable questionTask;
        private BukkitRunnable revealTask;
        private BukkitRunnable beamTask;
        private BukkitRunnable holoFacingTask;
        private BukkitRunnable holoVisibilityTask;
        /** 答案盔甲架 UUID -> 對其 hideEntity 的玩家 UUID 集合，只有進房的玩家才看得到，每個玩家單獨計算 */
        private final Map<UUID, Set<UUID>> holoHiddenFrom = new HashMap<>();
        private ArmorStand revealStand;
        private final UUID[] optionHolograms = new UUID[OPTIONS_TOTAL];

        private RoomState(String roomKey, String worldName, StructureTemplate.Rotation rotation, int baseX, int baseY, int baseZ, int sizeX, int sizeZ) {
            this.roomKey = roomKey;
            this.worldName = worldName;
            this.rotation = rotation;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
        }
    }
}
