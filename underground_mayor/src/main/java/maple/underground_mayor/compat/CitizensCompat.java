package maple.underground_mayor.compat;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Optional bridge for Citizens-backed NPC features.
 * All methods fail safely when Citizens is not installed.
 */
public final class CitizensCompat {
    private final Set<Integer> trackedNpcIds = new HashSet<>();

    public record SkinData(String texture, String signature) {
        public String textureOrEmpty() {
            return texture == null ? "" : texture;
        }

        public String signatureOrEmpty() {
            return signature == null ? "" : signature;
        }
    }

    public CitizensCompat() {
    }

    public boolean isAvailable() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        return pluginManager.getPlugin("Citizens") != null && CitizensAPI.hasImplementation();
    }

    public @Nullable NPC createPlayerNpc(Location spawnAt, String displayName, @Nullable String skinName) {
        return createPlayerNpc(spawnAt, displayName, skinName, null);
    }

    public @Nullable NPC createPlayerNpc(
            Location spawnAt,
            String displayName,
            @Nullable String skinName,
            @Nullable SkinData skinData
    ) {
        if (!isAvailable() || spawnAt == null || spawnAt.getWorld() == null) {
            return null;
        }

        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        if (registry == null) {
            return null;
        }

        NPC npc = registry.createNPC(EntityType.PLAYER, displayName == null || displayName.isBlank() ? "NPC" : displayName);
        if (npc == null) {
            return null;
        }

        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        if ((skinName != null && !skinName.isBlank()) || skinData != null) {
            skinTrait.setFetchDefaultSkin(false);
        }
        if (skinData != null && !skinData.textureOrEmpty().isBlank()) {
            applySkinDataCompat(skinTrait, displayName == null || displayName.isBlank() ? "NPC" : displayName, skinData);
        } else if (skinName != null && !skinName.isBlank()) {
            skinTrait.setSkinName(skinName);
        }

        npc.spawn(spawnAt);
        npc.setProtected(true);
        trackedNpcIds.add(npc.getId());
        return npc;
    }

    public boolean applySkin(NPC npc, @Nullable String skinName) {
        if (!isAvailable() || npc == null || skinName == null || skinName.isBlank()) {
            return false;
        }

        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setFetchDefaultSkin(false);
        skinTrait.setSkinName(skinName);
        return true;
    }

    public boolean applySkin(NPC npc, @Nullable SkinData skinData) {
        if (!isAvailable() || npc == null || skinData == null || skinData.textureOrEmpty().isBlank()) {
            return false;
        }

        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setFetchDefaultSkin(false);
        return applySkinDataCompat(
                skinTrait,
                npc.getName() == null || npc.getName().isBlank() ? "NPC" : npc.getName(),
                skinData
        );
    }

    public boolean walkTo(NPC npc, Location target) {
        if (!isAvailable() || npc == null || target == null || target.getWorld() == null) {
            return false;
        }

        npc.getNavigator().cancelNavigation();
        npc.getNavigator().setTarget(target);
        return true;
    }

    public boolean walkTo(NPC npc, Entity target) {
        if (!isAvailable() || npc == null || target == null || !target.isValid()) {
            return false;
        }

        npc.getNavigator().cancelNavigation();
        npc.getNavigator().setTarget(target, false);
        return true;
    }

    public boolean stopWalking(NPC npc) {
        if (!isAvailable() || npc == null) {
            return false;
        }

        npc.getNavigator().cancelNavigation();
        return true;
    }

    public boolean destroyNpc(@Nullable NPC npc) {
        if (npc == null) {
            return false;
        }

        trackedNpcIds.remove(npc.getId());
        npc.destroy();
        return true;
    }

    public @Nullable NPC getNpc(int npcId) {
        if (!isAvailable()) {
            return null;
        }

        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        return registry == null ? null : registry.getById(npcId);
    }

    public void shutdown() {
        if (!isAvailable()) {
            trackedNpcIds.clear();
            return;
        }

        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        if (registry != null) {
            for (Integer npcId : Set.copyOf(trackedNpcIds)) {
                NPC npc = registry.getById(npcId);
                if (npc != null) {
                    npc.destroy();
                }
            }
        }

        trackedNpcIds.clear();
    }

    private boolean applySkinDataCompat(SkinTrait skinTrait, String skinName, SkinData skinData) {
        if (skinTrait == null || skinData == null || skinData.textureOrEmpty().isBlank()) {
            return false;
        }

        boolean applied = false;
        try {
            Method setTexture = skinTrait.getClass().getMethod("setTexture", String.class, String.class);
            setTexture.invoke(skinTrait, skinData.textureOrEmpty(), skinData.signatureOrEmpty());
            applied = true;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method setSkinPersistent = skinTrait.getClass().getMethod("setSkinPersistent", String.class, String.class, String.class);
            setSkinPersistent.invoke(skinTrait, skinName, skinData.signatureOrEmpty(), skinData.textureOrEmpty());
            applied = true;
        } catch (ReflectiveOperationException ignored) {
        }

        if (!applied && skinName != null && !skinName.isBlank()) {
            try {
                skinTrait.setSkinName(skinName);
                applied = true;
            } catch (Throwable ignored) {
            }
        }

        return applied;
    }
}
