package maple.underground_caves.dungeon;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Citizens 橋接：建立完整玩家皮膚 NPC。僅在 Citizens 可用時載入。
 */
public final class CitizensStartRoomBridge implements Listener {
    private final Set<Integer> ourNpcIds = new HashSet<>();
    private final Consumer<Player> openGuiCallback;

    CitizensStartRoomBridge(Consumer<Player> openGuiCallback) {
        this.openGuiCallback = openGuiCallback;
    }

    Integer createNpc(World world, Location spawnAt, String skinName) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        if (registry == null) return null;
        NPC npc = registry.createNPC(EntityType.PLAYER, "NPC");
        if (npc == null) return null;

        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
        skin.setFetchDefaultSkin(false);
        skin.setSkinName(skinName != null && !skinName.isEmpty() ? skinName : "MHF_Steve");

        npc.spawn(spawnAt);
        npc.setProtected(true);
        ourNpcIds.add(npc.getId());
        return npc.getId();
    }

    void removeNpc(int npcId) {
        ourNpcIds.remove(npcId);
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null) npc.destroy();
    }

    boolean isOurNpc(int npcId) {
        return ourNpcIds.contains(npcId);
    }

    boolean isOurNpcEntity(org.bukkit.entity.Entity entity) {
        if (entity == null) return false;
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
        return npc != null && ourNpcIds.contains(npc.getId());
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        if (ourNpcIds.contains(event.getNPC().getId()) && event.getClicker() != null) {
            openGuiCallback.accept(event.getClicker());
        }
    }

    @EventHandler
    public void onNpcLeftClick(NPCLeftClickEvent event) {
        if (ourNpcIds.contains(event.getNPC().getId()) && event.getClicker() != null) {
            openGuiCallback.accept(event.getClicker());
        }
    }

    void clearAll() {
        for (Integer id : Set.copyOf(ourNpcIds)) {
            NPC npc = CitizensAPI.getNPCRegistry().getById(id);
            if (npc != null) npc.destroy();
        }
        ourNpcIds.clear();
    }
}
