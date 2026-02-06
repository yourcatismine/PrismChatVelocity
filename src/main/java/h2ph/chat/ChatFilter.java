package h2ph.chat;

import com.velocitypowered.api.proxy.Player;
import h2ph.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatFilter {

    private static final Component TOO_MANY = Component.text("You are sending too many messages at once.")
            .color(NamedTextColor.RED);
    private static final Component REPEAT = Component.text("Please do not repeat the same (or similar) message.")
            .color(NamedTextColor.RED);

    private final ConfigManager configManager;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public ChatFilter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean canSend(Player player, String message) {
        if (player == null) {
            return true;
        }

        double cooldownSeconds = configManager.getDouble("chat.cooldown-seconds", 1.5);
        double spamWindowSeconds = configManager.getDouble("chat.spam-window-seconds", 3.0);
        int spamMaxMessages = configManager.getInt("chat.spam-max-messages", 4);
        int repeatMinLength = configManager.getInt("chat.repeat-min-length", 4);
        double repeatSimilarity = configManager.getDouble("chat.repeat-similarity", 0.9);

        long now = System.currentTimeMillis();
        long cooldownMillis = (long) (cooldownSeconds * 1000.0);
        long windowMillis = (long) (spamWindowSeconds * 1000.0);

        State state = states.computeIfAbsent(player.getUniqueId(), k -> new State());

        synchronized (state) {
            if (cooldownMillis > 0) {
                long elapsed = now - state.lastMessageTime;
                if (state.lastMessageTime > 0 && elapsed < cooldownMillis) {
                    double remaining = (cooldownMillis - elapsed) / 1000.0;
                    String secondsText = formatSeconds(remaining);
                    sendBoth(player, Component.text(String.format("Please wait %s before sending your next message.", secondsText))
                            .color(NamedTextColor.RED));
                    return false;
                }
            }

            if (windowMillis > 0 && spamMaxMessages > 0) {
                prune(state.messageTimes, now, windowMillis);
                state.messageTimes.addLast(now);
                if (state.messageTimes.size() > spamMaxMessages) {
                    sendBoth(player, TOO_MANY);
                    return false;
                }
            }

            String normalized = normalizeMessage(message);
            if (normalized.length() >= repeatMinLength && state.lastMessageNormalized != null) {
                double similarity = similarityRatio(normalized, state.lastMessageNormalized);
                if (similarity >= repeatSimilarity) {
                    sendBoth(player, REPEAT);
                    return false;
                }
            }

            state.lastMessageTime = now;
            state.lastMessageNormalized = normalized;
        }

        return true;
    }

    private static void sendBoth(Player player, Component message) {
        player.sendMessage(message);
        player.sendActionBar(message);
    }

    private static void prune(Deque<Long> deque, long now, long windowMillis) {
        while (!deque.isEmpty()) {
            long ts = deque.peekFirst();
            if (now - ts > windowMillis) {
                deque.removeFirst();
            } else {
                break;
            }
        }
    }

    private static String formatSeconds(double remainingSeconds) {
        double rounded = Math.ceil(remainingSeconds * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.valueOf((int) Math.rint(rounded));
        }
        return String.format("%.1f", rounded);
    }

    private static String normalizeMessage(String input) {
        if (input == null) {
            return "";
        }
        String lower = input.toLowerCase();
        lower = lower.replaceAll("(?i)&[0-9a-fk-orx]", "");
        lower = lower.replaceAll("(?i)§[0-9a-fk-orx]", "");
        lower = lower.replaceAll("(?i)&#[0-9a-f]{6}", "");
        lower = lower.replaceAll("(?i)#[0-9a-f]{6}", "");
        lower = lower.replaceAll("[^a-z0-9\\s]", " ");
        lower = lower.replaceAll("\\s+", " ").trim();
        return lower;
    }

    private static double similarityRatio(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int maxLen = Math.max(a.length(), b.length());
        int dist = levenshtein(a, b);
        return 1.0 - (double) dist / (double) maxLen;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }

    private static class State {
        private long lastMessageTime;
        private String lastMessageNormalized;
        private final Deque<Long> messageTimes = new ArrayDeque<>();
    }
}
