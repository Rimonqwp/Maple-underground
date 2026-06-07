package maple.underground_caves.enchantments;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 附魔 lore 顯示：普通附魔灰色，終極附魔粉色。
 */
public final class EnchantmentLore {

    private static final String[] ROMAN = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII" };

    public static String levelToRoman(int level) {
        if (level < 1 || level > ROMAN.length) return String.valueOf(level);
        return ROMAN[level - 1];
    }

    /** 單行附魔 lore：例如 "連擊 I" 或 "枯木逢春 III"，顏色依普通/終極決定 */
    public static Component line(CustomEnchantment enchant, int level) {
        String roman = levelToRoman(level);
        String text = enchant.getDisplayName() + " " + roman;
        return Component.text(text, enchant.getLoreColor());
    }

    /** 附魔描述行（效果說明），同一顏色 */
    public static Component descriptionLine(CustomEnchantment enchant, int level) {
        String desc = enchant.getDescription(level);
        return Component.text("  " + desc, enchant.getLoreColor());
    }

    /** 為物品生成附魔 lore 列表（先標題行再描述行） */
    public static List<Component> buildLore(Map<CustomEnchantment, Integer> enchants) {
        List<Component> out = new ArrayList<>();
        if (enchants == null || enchants.isEmpty()) return out;
        for (Map.Entry<CustomEnchantment, Integer> e : enchants.entrySet()) {
            out.add(line(e.getKey(), e.getValue()));
            out.add(descriptionLine(e.getKey(), e.getValue()));
        }
        return out;
    }
}
