package maple.underground_mayor.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.Permission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Starts an optional Discord bot alongside the plugin.
 * The token is read from config or environment variables and is never hardcoded.
 */
public final class DiscordBotManager extends ListenerAdapter {
    private static final String ENV_TOKEN = "UNDERGROUND_MAYOR_DISCORD_TOKEN";
    private static final String MAYOR_ANNOUNCE_COMMAND = "mayor_announce";
    private static final String STATE_FILE_NAME = "discord-state.yml";
    private static final String ANNOUNCE_SENT_PATH = "commands.mayor_announce.schedule.sent";
    private static final String ANNOUNCE_SENT_AT_PATH = "commands.mayor_announce.schedule.sent_at";

    private final maple.underground_mayor.UndergroundMayor plugin;
    private final File stateFile;
    private final YamlConfiguration stateConfig;
    private JDA jda;
    private BukkitTask mayorAnnounceScheduleTask;

    public DiscordBotManager(maple.underground_mayor.UndergroundMayor plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), STATE_FILE_NAME);
        this.stateConfig = YamlConfiguration.loadConfiguration(stateFile);
    }

    public void start() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("discord.enabled", false)) {
            plugin.getLogger().info("Discord bot is disabled in config.");
            return;
        }

        String token = resolveToken(config);
        if (token == null || token.isBlank()) {
            plugin.getLogger().warning("Discord bot enabled but no token was provided. Set discord.token or UNDERGROUND_MAYOR_DISCORD_TOKEN.");
            return;
        }

        if (jda != null) {
            plugin.getLogger().warning("Discord bot is already running.");
            return;
        }

        try {
            jda = JDABuilder.createLight(token)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(resolveActivity(config))
                    .addEventListeners(this)
                    .build();
            plugin.getLogger().info("Starting Discord bot...");
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().severe("Failed to start Discord bot. The configured token appears to be invalid.");
            plugin.getLogger().severe(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        } catch (Throwable ex) {
            plugin.getLogger().severe("Failed to start Discord bot: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    public void shutdown() {
        if (mayorAnnounceScheduleTask != null) {
            mayorAnnounceScheduleTask.cancel();
            mayorAnnounceScheduleTask = null;
        }

        JDA current = jda;
        jda = null;
        if (current == null) {
            return;
        }

        try {
            current.removeEventListener(this);
        } catch (Throwable ignored) {
        }

        try {
            current.shutdownNow();
            current.awaitShutdown(Duration.ofSeconds(10));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
        }
    }

    public boolean isRunning() {
        return jda != null;
    }

    public JDA getJda() {
        return jda;
    }

    public @Nullable GuildChannel getConfiguredGuildChannel() {
        if (jda == null) {
            return null;
        }

        String channelId = plugin.getConfig().getString("discord.channel_id", "").trim();
        if (channelId.isEmpty()) {
            return null;
        }

        return jda.getGuildChannelById(channelId);
    }

    public @Nullable MessageChannel getConfiguredMessageChannel() {
        GuildChannel channel = getConfiguredGuildChannel();
        if (channel instanceof MessageChannel messageChannel) {
            return messageChannel;
        }
        return null;
    }

    public boolean sendConfiguredChannelMessage(String message) {
        MessageChannel channel = getConfiguredMessageChannel();
        if (channel == null || message == null || message.isBlank()) {
            return false;
        }

        if (channel instanceof GuildMessageChannel guildMessageChannel && !guildMessageChannel.canTalk()) {
            plugin.getLogger().warning("Configured Discord channel exists but the bot cannot talk there: " + guildMessageChannel.getName() + " (" + guildMessageChannel.getId() + ").");
            return false;
        }

        channel.sendMessage(message).queue();
        return true;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        plugin.getLogger().info("Discord bot connected as " + event.getJDA().getSelfUser().getAsTag() + ".");

        String guildId = plugin.getConfig().getString("discord.guild_id", "").trim();
        String channelId = plugin.getConfig().getString("discord.channel_id", "").trim();

        if (!guildId.isEmpty()) {
            Guild guild = event.getJDA().getGuildById(guildId);
            if (guild != null) {
                plugin.getLogger().info("Discord guild configured: " + guild.getName() + " (" + guild.getId() + ").");
                registerGuildCommands(guild);
            } else {
                plugin.getLogger().warning("Configured Discord guild was not found: " + guildId);
            }
        }

        if (!channelId.isEmpty()) {
            GuildChannel channel = event.getJDA().getGuildChannelById(channelId);
            if (channel != null) {
                plugin.getLogger().info("Discord channel configured: " + describeChannel(channel));
                if (channel instanceof GuildMessageChannel guildMessageChannel) {
                    if (!guildMessageChannel.canTalk()) {
                        plugin.getLogger().warning("The configured Discord channel exists, but the bot cannot send messages there.");
                    }
                } else if (!(channel instanceof MessageChannel)) {
                    plugin.getLogger().warning("The configured Discord channel exists, but this channel type does not support direct message sending.");
                }
            } else {
                plugin.getLogger().warning("Configured Discord channel was not found: " + channelId);
            }
        }

        scheduleMayorAnnouncementIfNeeded();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!MAYOR_ANNOUNCE_COMMAND.equals(event.getName())) {
            return;
        }

        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("你沒有權限使用這個指令。").setEphemeral(true).queue();
            return;
        }

        if (!plugin.getConfig().getBoolean("discord.commands.mayor_announce.enabled", true)) {
            event.reply("這個 Discord 指令目前已被停用。").setEphemeral(true).queue();
            return;
        }

        String message = buildMayorAnnouncementMessage();
        if (message.isBlank()) {
            event.reply("公告模板是空的，請先檢查 config.yml。").setEphemeral(true).queue();
            return;
        }

        if (sendConfiguredChannelMessage(message)) {
            event.reply("已將市長公告發送到設定頻道。").setEphemeral(true).queue();
        } else {
            event.reply("無法發送公告到設定頻道，請檢查 channel_id、頻道類型或 Bot 權限。").setEphemeral(true).queue();
        }
    }

    private String describeChannel(Channel channel) {
        String name = channel.getName();
        String type = channel.getType().name();
        return name + " [" + type + "] (" + channel.getId() + ")";
    }

    private String resolveToken(FileConfiguration config) {
        String envToken = System.getenv(ENV_TOKEN);
        if (envToken != null && !envToken.isBlank()) {
            return envToken.trim();
        }

        String token = config.getString("discord.token", "");
        if (token == null) {
            return "";
        }

        token = token.trim();
        if (token.equalsIgnoreCase("PUT_YOUR_DISCORD_BOT_TOKEN_HERE")) {
            return "";
        }
        return token;
    }

    private Activity resolveActivity(FileConfiguration config) {
        String type = config.getString("discord.activity.type", "playing");
        String text = config.getString("discord.activity.text", "Underground Mayor");
        if (text == null || text.isBlank()) {
            return null;
        }

        return switch (type == null ? "playing" : type.toLowerCase()) {
            case "watching" -> Activity.watching(text);
            case "listening" -> Activity.listening(text);
            case "competing" -> Activity.competing(text);
            default -> Activity.playing(text);
        };
    }

    private void registerGuildCommands(Guild guild) {
        if (guild == null) {
            return;
        }

        boolean enabled = plugin.getConfig().getBoolean("discord.commands.mayor_announce.enabled", true);
        if (!enabled) {
            return;
        }

        guild.upsertCommand(
                Commands.slash(MAYOR_ANNOUNCE_COMMAND, "發送市長建設公告到設定頻道")
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        ).queue(
                success -> plugin.getLogger().info("Registered Discord command /" + MAYOR_ANNOUNCE_COMMAND + " for guild " + guild.getId() + "."),
                failure -> plugin.getLogger().warning("Failed to register Discord command /" + MAYOR_ANNOUNCE_COMMAND + ": " + failure.getMessage())
        );
    }

    private String buildMayorAnnouncementMessage() {
        List<String> lines = plugin.getConfig().getStringList("discord.commands.mayor_announce.message_lines");
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines);
    }

    private void scheduleMayorAnnouncementIfNeeded() {
        if (mayorAnnounceScheduleTask != null) {
            mayorAnnounceScheduleTask.cancel();
            mayorAnnounceScheduleTask = null;
        }

        if (!plugin.getConfig().getBoolean("discord.commands.mayor_announce.enabled", true)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("discord.commands.mayor_announce.schedule.enabled", false)) {
            return;
        }
        if (stateConfig.getBoolean(ANNOUNCE_SENT_PATH, false)) {
            plugin.getLogger().info("Scheduled /mayor_announce message was already sent; skipping scheduler.");
            return;
        }

        OffsetDateTime sendAt = parseScheduledAnnouncementTime();
        if (sendAt == null) {
            return;
        }

        mayorAnnounceScheduleTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (stateConfig.getBoolean(ANNOUNCE_SENT_PATH, false)) {
                cancelMayorAnnouncementTask();
                return;
            }

            OffsetDateTime now = OffsetDateTime.now(sendAt.getOffset());
            if (now.isBefore(sendAt)) {
                return;
            }

            String message = buildMayorAnnouncementMessage();
            boolean sent = sendConfiguredChannelMessage(message);
            if (!sent) {
                plugin.getLogger().warning("Scheduled /mayor_announce message reached send time but failed to send. Will retry on next tick window.");
                return;
            }

            stateConfig.set(ANNOUNCE_SENT_PATH, true);
            stateConfig.set(ANNOUNCE_SENT_AT_PATH, OffsetDateTime.now().toString());
            saveStateConfig();
            plugin.getLogger().info("Scheduled /mayor_announce message sent successfully.");
            cancelMayorAnnouncementTask();
        }, 20L, 20L * 30L);
    }

    private void cancelMayorAnnouncementTask() {
        if (mayorAnnounceScheduleTask != null) {
            mayorAnnounceScheduleTask.cancel();
            mayorAnnounceScheduleTask = null;
        }
    }

    private @Nullable OffsetDateTime parseScheduledAnnouncementTime() {
        String raw = plugin.getConfig().getString("discord.commands.mayor_announce.schedule.send_at", "").trim();
        if (raw.isEmpty()) {
            plugin.getLogger().warning("Scheduled /mayor_announce is enabled but send_at is empty.");
            return null;
        }

        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            plugin.getLogger().warning("Invalid schedule time for /mayor_announce: " + raw + ". Expected format like 2026-04-07T07:00:00+08:00");
            return null;
        }
    }

    private void saveStateConfig() {
        try {
            stateConfig.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save Discord state file: " + ex.getMessage());
        }
    }
}
