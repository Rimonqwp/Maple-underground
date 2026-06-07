package maple.underground_caves.gear;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.EnumSet;
import java.util.Set;

/**
 * 解析字串中的 & 傳統色碼與 &#RRGGBB / #RRGGBB RGB 色碼，組合成 Component。
 */
public final class LegacyRgbParser {

    /** 使用 & 作為色碼前綴（配置檔常用）；§ 用 legacySection() */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final TextColor DEFAULT_COLOR = NamedTextColor.GRAY;

    private LegacyRgbParser() {}

    /**
     * 將含 & 與 &#RRGGBB（或 #RRGGBB）的字串轉為 Component。
     * 支援：&0-9a-f &l 等傳統碼；&#RRGGBB 或 #RRGGBB（6 位 hex，大小寫皆可）為 RGB。
     * &l 等裝飾會套用到後續 RGB 段落（例：&l&#79A4FF永恆的面具 會正確顯示粗體+漸層）。
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        input = input.trim();
        Component result = Component.empty();
        StringBuilder segment = new StringBuilder();
        TextColor currentRgb = null;
        Set<TextDecoration> currentDecorations = EnumSet.noneOf(TextDecoration.class);
        int i = 0;
        while (i < input.length()) {
            if (input.charAt(i) == '&' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '#') {
                    if (i + 8 <= input.length()) {
                        String hex = input.substring(i + 2, i + 8).toUpperCase();
                        if (isHex6(hex)) {
                            result = flushRgbSegment(result, segment, currentRgb, currentDecorations);
                            segment.setLength(0);
                            currentRgb = TextColor.color(Integer.parseInt(hex, 16));
                            i += 8;
                            continue;
                        }
                    }
                } else if (isLegacyCode(next)) {
                    result = flushRgbSegment(result, segment, currentRgb, currentDecorations);
                    segment.setLength(0);
                    StringBuilder legacyCodes = new StringBuilder();
                    int j = i;
                    while (j < input.length() && input.charAt(j) == '&' && j + 1 < input.length()) {
                        char n = input.charAt(j + 1);
                        if (n == '#') {
                            if (j + 8 <= input.length() && isHex6(input.substring(j + 2, j + 8).toUpperCase())) {
                                break;
                            }
                        } else if (!isLegacyCode(n)) {
                            break;
                        }
                        legacyCodes.append(input.charAt(j)).append(input.charAt(j + 1));
                        updateDecorationsFromLegacy(legacyCodes.toString(), currentDecorations);
                        j += 2;
                    }
                    StringBuilder run = new StringBuilder();
                    while (j < input.length() && input.charAt(j) != '&' && input.charAt(j) != '#') {
                        run.append(input.charAt(j));
                        j++;
                    }
                    if (legacyCodes.length() > 0) {
                        Component part = LEGACY.deserialize(legacyCodes.toString() + run.toString()).colorIfAbsent(DEFAULT_COLOR);
                        if (!currentDecorations.contains(TextDecoration.ITALIC)) part = part.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
                        result = result.append(part);
                    }
                    i = j;
                    continue;
                }
            }
            if (input.charAt(i) == '#' && i + 7 <= input.length()) {
                String hex = input.substring(i + 1, i + 7).toUpperCase();
                if (isHex6(hex)) {
                    result = flushRgbSegment(result, segment, currentRgb, currentDecorations);
                    segment.setLength(0);
                    currentRgb = TextColor.color(Integer.parseInt(hex, 16));
                    i += 7;
                    continue;
                }
            }
            segment.append(input.charAt(i));
            i++;
        }
        result = flushRgbSegment(result, segment, currentRgb, currentDecorations);
        return result;
    }

    private static void updateDecorationsFromLegacy(String legacy, Set<TextDecoration> out) {
        for (int k = 0; k + 1 < legacy.length(); k += 2) {
            if (legacy.charAt(k) != '&') continue;
            char c = legacy.charAt(k + 1);
            if (c == 'r' || c == 'R') {
                out.clear();
                continue;
            }
            if (c == 'l' || c == 'L') out.add(TextDecoration.BOLD);
            else if (c == 'o' || c == 'O') out.add(TextDecoration.ITALIC);
            else if (c == 'n' || c == 'N') out.add(TextDecoration.UNDERLINED);
            else if (c == 'm' || c == 'M') out.add(TextDecoration.STRIKETHROUGH);
            else if (c == 'k' || c == 'K') out.add(TextDecoration.OBFUSCATED);
        }
    }

    private static Component flushRgbSegment(Component result, StringBuilder segment, TextColor rgb, Set<TextDecoration> decorations) {
        if (segment.length() == 0) return result;
        String text = segment.toString();
        Component part;
        if (rgb != null) {
            part = Component.text(text).color(rgb);
        } else {
            part = Component.text(text).color(DEFAULT_COLOR);
        }
        for (TextDecoration d : decorations) {
            part = part.decorate(d);
        }
        if (!decorations.contains(TextDecoration.ITALIC)) part = part.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        result = result.append(part);
        segment.setLength(0);
        return result;
    }

    private static boolean isHex6(String s) {
        if (s == null || s.length() != 6) return false;
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) continue;
            return false;
        }
        return true;
    }

    private static boolean isLegacyCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || c == 'l' || c == 'L' || c == 'o' || c == 'O' || c == 'n' || c == 'N' || c == 'm' || c == 'M' || c == 'k' || c == 'K' || c == 'r' || c == 'R';
    }
}
