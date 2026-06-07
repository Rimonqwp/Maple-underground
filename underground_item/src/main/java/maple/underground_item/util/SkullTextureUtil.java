package maple.underground_item.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

public final class SkullTextureUtil {

    private static final String TEXTURE_PREFIX = "http://textures.minecraft.net/texture/";

    private SkullTextureUtil() {
    }

    public static void applySkullTextureToMeta(SkullMeta meta, String textureId, String ctx) {
        if (meta == null || textureId == null || textureId.isBlank()) {
            return;
        }
        try {
            var profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            profile.getTextures().setSkin(new URL(TEXTURE_PREFIX + textureId.trim()));
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException e) {
            Bukkit.getLogger().warning("[" + ctx + "] Invalid skull texture id: " + textureId);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[" + ctx + "] Failed to apply skull texture: " + t.getMessage());
        }
    }
}
