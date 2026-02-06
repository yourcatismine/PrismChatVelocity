package h2ph.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import com.mojang.brigadier.tree.RootCommandNode;
import net.kyori.adventure.text.Component;

public class CommandBlockListener {

    private static final Component MESSAGE_COMPONENT = Component.text("This command does not exist.")
            .color(net.kyori.adventure.text.format.NamedTextColor.RED);

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        if (!isBlockedCommand(event.getCommand())) {
            return;
        }

        event.setResult(CommandExecuteEvent.CommandResult.denied());

        // Send in both chat and actionbar. No sound.
        player.sendMessage(MESSAGE_COMPONENT);
        player.sendActionBar(MESSAGE_COMPONENT);
    }

    @Subscribe
    public void onTabComplete(TabCompleteEvent event) {
        event.getSuggestions().removeIf(CommandBlockListener::isBlockedCommand);
    }

    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        RootCommandNode<?> root = event.getRootNode();
        removeRootCommand(root, "server");
        removeRootCommand(root, "velocity:server");
        removeRootCommand(root, "velocity");
        removeRootCommand(root, "geyser");
        removeRootCommand(root, "geyser:geyser");
        removeRootCommand(root, "lp");
        removeRootCommand(root, "lpv");
        removeRootCommand(root, "luckperms");
        removeRootCommand(root, "luckyperms");
        removeRootCommand(root, "prismmotd");
        removeRootCommand(root, "prismvoid");
        removeRootCommand(root, "velocity:callback");
        removeRootCommand(root, "velocity");
        removeRootCommand(root, "btab");
    }

    private static void removeRootCommand(RootCommandNode<?> root, String name) {
        if (root.getChild(name) != null) {
            root.getChildren().removeIf(node -> node.getName().equalsIgnoreCase(name));
        }
    }

    private static boolean isBlockedCommand(String raw) {
        if (raw == null) {
            return false;
        }
        String command = raw.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isEmpty()) {
            return false;
        }
        String base = command.split("\\s+")[0].toLowerCase();
        if (base.endsWith(":")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(":server") || base.equals("server")) {
            return true;
        }
        if (base.endsWith(":geyser") || base.equals("geyser")) {
            return true;
        }
        if (base.endsWith(":velocity") || base.equals("velocity")) {
            return true;
        }
        if (base.endsWith(":lp") || base.equals("lp")) {
            return true;
        }
        if (base.endsWith(":lpv") || base.equals("lpv")) {
            return true;
        }
        if (base.endsWith(":luckperms") || base.equals("luckperms")) {
            return true;
        }
        if (base.endsWith(":luckyperms") || base.equals("luckyperms")) {
            return true;
        }
        return base.endsWith(":prismmotd") || base.equals("prismmotd");
    }
}
