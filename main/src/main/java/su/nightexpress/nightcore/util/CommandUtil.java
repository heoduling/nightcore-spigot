package su.nightexpress.nightcore.util;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.FormattedCommandAlias;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.help.GenericCommandHelpTopic;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import su.nightexpress.nightcore.command.api.NightPluginCommand;
import su.nightexpress.nightcore.command.experimental.CommandContext;
import su.nightexpress.nightcore.command.experimental.argument.ParsedArguments;
import su.nightexpress.nightcore.command.impl.WrappedCommand;
import su.nightexpress.nightcore.util.bridge.Software;

import java.util.*;

public class CommandUtil {

    private static final Map<String, FormattedCommandAlias> HELP_TEXTS = new HashMap<>();

    @NonNull
    private static SimpleCommandMap getCommandMap() {
        return Software.instance().getCommandMap();
    }

    @Deprecated
    public static void register(@NonNull Plugin plugin, @NonNull NightPluginCommand command) {
        WrappedCommand wrappedCommand = new WrappedCommand(plugin, command);
        register(wrappedCommand, plugin.getName());
        if (wrappedCommand.isRegistered()) {
            command.setBackend(wrappedCommand);
        }
    }

    public static boolean register(@NonNull Plugin plugin, @NonNull WrappedCommand wrappedCommand) {
        return register(wrappedCommand, plugin.getName());
    }

    public static boolean register(@NonNull Command command, @NonNull String fallbackPrefix) {
        boolean registered = getCommandMap().register(fallbackPrefix, command);
        if (command.isRegistered()) restoreHelpCommand(command);
        return registered;
    }

    private static void restoreHelpCommand(@NonNull Command command) {
        String label = command.getLabel();
        FormattedCommandAlias text = HELP_TEXTS.get(label);
        if (text == null) return;

        var topic = Bukkit.getHelpMap().getHelpTopic("/" + label);
        if (topic != null && topic.getClass() == GenericCommandHelpTopic.class
            && Reflex.getFieldValue(topic, "command") == text
            && Reflex.setFieldValue(topic, "command", command)) {
            HELP_TEXTS.remove(label);
        }
    }

    private static void updateHelpText(@NonNull FormattedCommandAlias text, @NonNull Command command) {
        text.setDescription(command.getDescription());
        text.setUsage(command.getUsage());
        text.setPermission(command.getPermission());
        text.setAliases(new ArrayList<>(command.getAliases()));
    }

    private static void unregisterHelpText(@NonNull Command command, @NonNull String label) {
        var topic = Bukkit.getHelpMap().getHelpTopic("/" + label);
        if (topic == null || topic.getClass() != GenericCommandHelpTopic.class) return;

        if (Reflex.getFieldValue(topic, "command") == command) {
            // Preserve the native topic, its text and indexes while releasing the retired command.
            FormattedCommandAlias text = new FormattedCommandAlias(label, new String[0]);
            updateHelpText(text, command);
            if (!Reflex.setFieldValue(topic, "command", text)) return;
            HELP_TEXTS.put(label, text);
        }
    }

    public static void clearHelpTexts() {
        HELP_TEXTS.clear();
    }

    /*public static void syncCommands() {
        // Fix tab completer when registerd on runtime
        Server server = Bukkit.getServer();
        Method method = Reflex.getMethod(server.getClass(), "syncCommands");
        if (method == null) return;
    
        Reflex.invokeMethod(method, server);
    }*/

    public static boolean unregister(@NonNull String name) {
        Command command = getCommand(name).orElse(null);
        if (command == null) return false;

        return unregister(command);
    }

    public static boolean unregister(@NonNull Command command) {
        SimpleCommandMap commandMap = getCommandMap();
        String label = command.getLabel();

        Map<String, Command> knownCommands = Software.instance().getKnownCommands(commandMap);
        if (!command.unregister(commandMap)) return false;
        unregisterHelpText(command, label);

        return knownCommands.values().removeIf(registered -> registered == command);
    }

    @NonNull
    public static Set<String> getAliases(@NonNull String name) {
        return getAliases(name, false);
    }

    @NonNull
    public static Set<String> getAliases(@NonNull String name, boolean inclusive) {
        Command command = getCommand(name).orElse(null);
        if (command == null) return Collections.emptySet();

        Set<String> aliases = new HashSet<>(command.getAliases());
        if (inclusive) aliases.add(command.getName());
        return aliases;
    }

    @NonNull
    public static Optional<Command> getCommand(@NonNull String name) {
        return getCommandMap().getCommands().stream()
            .filter(command -> command.getName().equalsIgnoreCase(name) || command.getLabel().equalsIgnoreCase(
                name) || command.getAliases().contains(name))
            .findFirst();
    }

    @NonNull
    public static String getCommandName(@NonNull String string) {
        String name = string.split(" ")[0].substring(1);

        String[] pluginPrefix = name.split(":");
        if (pluginPrefix.length == 2) {
            name = pluginPrefix[1];
        }

        return name;
    }

    @Nullable
    public static Player getPlayerOrSender(@NonNull CommandContext context, @NonNull ParsedArguments arguments,
                                           @NonNull String name) {
        Player player;
        if (arguments.hasArgument(name)) {
            player = arguments.getPlayerArgument(name);
        }
        else {
            if (context.getExecutor() == null) {
                context.errorPlayerOnly();
                return null;
            }
            player = context.getExecutor();
        }
        return player;
    }
}
