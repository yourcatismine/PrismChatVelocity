package h2ph.util;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

public final class ChatFormatUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private ChatFormatUtil() {
    }

    public static String getDisplayNameLegacy(Player player) {
        String prefix = getLuckPermsPrefix(player);
        if (prefix == null || prefix.trim().isEmpty()) {
            return player.getUsername();
        }

        String normalized = normalizeLegacyColors(prefix);
        if (normalized == null || normalized.trim().isEmpty()) {
            return player.getUsername();
        }

        String spacer = normalized.endsWith(" ") ? "" : " ";
        return normalized + spacer + player.getUsername();
    }

    public static Component deserializeLegacy(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    private static String getLuckPermsPrefix(Player player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
            if (user == null) {
                return null;
            }
            return user.getCachedData().getMetaData().getPrefix();
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeLegacyColors(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.replace('§', '&');
        // Convert hex colors like #RRGGBB to &#RRGGBB
        normalized = normalized.replaceAll("(?i)(?<!&)#([0-9a-f]{6})", "&#$1");
        return normalized;
    }
}
