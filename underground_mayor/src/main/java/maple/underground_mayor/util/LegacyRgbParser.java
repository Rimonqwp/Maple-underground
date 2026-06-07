package maple.underground_mayor.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.EnumSet;
import java.util.Set;

/**
 * Parses legacy ampersand colors together with &#RRGGBB and #RRGGBB RGB tags.
 */
public final class LegacyRgbParser {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final TextColor DEFAULT_COLOR = NamedTextColor.GRAY;

    private LegacyRgbParser() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        Component result = Component.empty();
        StringBuilder segment = new StringBuilder();
        TextColor currentRgb = null;
        Set<TextDecoration> currentDecorations = EnumSet.noneOf(TextDecoration.class);

        for (int i = 0; i < input.length(); ) {
            if (input.charAt(i) == '&' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '#' && i + 8 <= input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    if (isHex6(hex)) {
                        result = flushRgbSegment(result, segment, currentRgb, currentDecorations);
                        currentRgb = TextColor.color(Integer.parseInt(hex, 16));
                        i += 8;
                        continue;
                    }
                } else if (isLegacyCode(next)) {
                    result = flushRgbSegment(result, segment, currentRgb, currentDecorations);
                    StringBuilder codes = new StringBuilder();
                    int j = i;
                    while (j + 1 < input.length() && input.charAt(j) == '&' && isLegacyCode(input.charAt(j + 1))) {
                        codes.append(input, j, j + 2);
                        updateDecorationsFromLegacy(codes.toString(), currentDecorations);
                        j += 2;
                    }
                    StringBuilder run = new StringBuilder();
                    while (j < input.length() && input.charAt(j) != '&' && input.charAt(j) != '#') {
                        run.append(input.charAt(j));
                        j++;
                    }
                    Component part = LEGACY.deserialize(codes + run.toString()).colorIfAbsent(DEFAULT_COLOR);
                    if (!currentDecorations.contains(TextDecoration.ITALIC)) {
                        part = part.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
                    }
                    result = result.append(part);
                    i = j;
                    continue;
                }
            }

            if (input.charAt(i) == '#' && i + 7 <= input.length()) {
                String hex = input.substring(i + 1, i + 7);
                if (isHex6(hex)) {
                    result = flushRgbSegment(result, segment, currentRgb, currentDecorations);
                    currentRgb = TextColor.color(Integer.parseInt(hex, 16));
                    i += 7;
                    continue;
                }
            }

            segment.append(input.charAt(i));
            i++;
        }

        return flushRgbSegment(result, segment, currentRgb, currentDecorations);
    }

    private static Component flushRgbSegment(Component result, StringBuilder segment, TextColor rgb, Set<TextDecoration> decorations) {
        if (segment.isEmpty()) {
            return result;
        }

        Component part = Component.text(segment.toString()).color(rgb != null ? rgb : DEFAULT_COLOR);
        for (TextDecoration decoration : decorations) {
            part = part.decorate(decoration);
        }
        if (!decorations.contains(TextDecoration.ITALIC)) {
            part = part.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }

        segment.setLength(0);
        return result.append(part);
    }

    private static void updateDecorationsFromLegacy(String legacy, Set<TextDecoration> out) {
        for (int i = 0; i + 1 < legacy.length(); i += 2) {
            if (legacy.charAt(i) != '&') {
                continue;
            }
            char code = legacy.charAt(i + 1);
            if (code == 'r' || code == 'R') {
                out.clear();
            } else if (code == 'l' || code == 'L') {
                out.add(TextDecoration.BOLD);
            } else if (code == 'o' || code == 'O') {
                out.add(TextDecoration.ITALIC);
            } else if (code == 'n' || code == 'N') {
                out.add(TextDecoration.UNDERLINED);
            } else if (code == 'm' || code == 'M') {
                out.add(TextDecoration.STRIKETHROUGH);
            } else if (code == 'k' || code == 'K') {
                out.add(TextDecoration.OBFUSCATED);
            }
        }
    }

    private static boolean isHex6(String s) {
        if (s == null || s.length() != 6) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLegacyCode(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F')
                || c == 'l' || c == 'L'
                || c == 'o' || c == 'O'
                || c == 'n' || c == 'N'
                || c == 'm' || c == 'M'
                || c == 'k' || c == 'K'
                || c == 'r' || c == 'R';
    }
}
