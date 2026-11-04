package fr.profilecraft.discord;

import fr.profilecraft.ProfileCraft;
import fr.profilecraft.data.DataStore;
import fr.profilecraft.util.Text;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.awt.Color;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Bot Discord (JDA) :
 *  - /link <code> et /unlink (commandes slash) pour lier un compte Minecraft à un compte Discord
 *  - attribue / retire le rôle "Membre"
 *  - pont de chat Minecraft <-> Discord
 * Les méthodes appelées par les évènements JDA s'exécutent sur des threads JDA :
 * tout accès à l'API Bukkit passe par le scheduler.
 */
public final class DiscordManager extends ListenerAdapter {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private record Pending(UUID uuid, String name, long expires) {}

    private final ProfileCraft plugin;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private volatile JDA jda;
    private volatile boolean ready;

    public DiscordManager(ProfileCraft plugin) {
        this.plugin = plugin;
    }

    public boolean ready() {
        return ready && jda != null;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ================================================================== démarrage / arrêt

    public void start() {
        FileConfiguration c = cfg();
        if (!c.getBoolean("discord.enabled", false)) {
            plugin.getLogger().info("Intégration Discord désactivée (discord.enabled: false).");
            return;
        }
        String token = c.getString("discord.token", "");
        if (token == null || token.isBlank()) token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            plugin.getLogger().warning("Discord activé mais aucun token (discord.token ou variable DISCORD_TOKEN).");
            return;
        }
        final String botToken = token.trim();
        final String activity = c.getString("discord.activity", "");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JDABuilder builder = JDABuilder.createLight(botToken, EnumSet.of(
                                GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.MESSAGE_CONTENT,
                                GatewayIntent.GUILD_MEMBERS))
                        .addEventListeners(this);
                if (activity != null && !activity.isBlank()) builder.setActivity(Activity.playing(activity));
                jda = builder.build();
                plugin.getLogger().info("Connexion à Discord en cours...");
            } catch (Exception ex) {
                plugin.getLogger().severe("Impossible de démarrer le bot Discord : " + ex.getMessage()
                        + " (token invalide ?)");
            }
        });
    }

    /** Arrêt propre (peut être appelé depuis onDisable). */
    public void shutdown() {
        JDA j = jda;
        if (j == null) return;
        if (ready && cfg().getBoolean("discord.chat.events.server-start-stop", true)) {
            TextChannel ch = channel();
            if (ch != null) {
                try {
                    ch.sendMessage("🔴 Serveur arrêté").timeout(3, TimeUnit.SECONDS).complete();
                } catch (Exception ignored) {
                    // pas grave si le message de fin n'est pas envoyé
                }
            }
        }
        ready = false;
        jda = null;
        pending.clear();
        try {
            j.shutdown();
            if (!j.awaitShutdown(5, TimeUnit.SECONDS)) j.shutdownNow();
        } catch (InterruptedException ex) {
            j.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void restart() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            shutdown();
            Bukkit.getScheduler().runTask(plugin, this::start);
        });
    }

    // ================================================================== accès

    private Guild guild() {
        JDA j = jda;
        String id = cfg().getString("discord.guild-id", "");
        if (j == null || id == null || id.isBlank()) return null;
        return j.getGuildById(id.trim());
    }

    private TextChannel channel() {
        JDA j = jda;
        String id = cfg().getString("discord.chat.channel-id", "");
        if (j == null || id == null || id.isBlank()) return null;
        return j.getTextChannelById(id.trim());
    }

    // ================================================================== codes de liaison

    /** Génère un code de liaison pour ce joueur (remplace l'éventuel code précédent). */
    public String createCode(Player p) {
        pending.values().removeIf(x -> x.uuid().equals(p.getUniqueId()));
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            code = sb.toString();
        } while (pending.containsKey(code));
        long ttl = cfg().getLong("discord.link-code-minutes", 10) * 60_000L;
        pending.put(code, new Pending(p.getUniqueId(), p.getName(), System.currentTimeMillis() + ttl));
        return code;
    }

    /** Supprime la liaison d'un joueur (et retire le rôle). @return true si un lien existait. */
    public boolean unlink(UUID player) {
        DataStore.Link l = plugin.store().removeLink(player);
        if (l == null) return false;
        plugin.store().saveAsync();
        removeRole(l.discordId());
        return true;
    }

    // ================================================================== rôles

    private void giveRole(String discordId) {
        Guild g = guild();
        String roleId = cfg().getString("discord.member-role-id", "");
        if (g == null || roleId == null || roleId.isBlank()) return;
        Role role = g.getRoleById(roleId.trim());
        if (role == null) {
            plugin.getLogger().warning("Rôle Membre introuvable (discord.member-role-id).");
            return;
        }
        g.addRoleToMember(UserSnowflake.fromId(discordId), role).queue(null, err ->
                plugin.getLogger().warning("Impossible de donner le rôle Membre : " + err.getMessage()
                        + " (le rôle du bot doit être AU-DESSUS de « Membre » et avoir « Gérer les rôles »)"));
    }

    private void removeRole(String discordId) {
        Guild g = guild();
        String roleId = cfg().getString("discord.member-role-id", "");
        if (g == null || roleId == null || roleId.isBlank()) return;
        Role role = g.getRoleById(roleId.trim());
        if (role == null) return;
        g.removeRoleFromMember(UserSnowflake.fromId(discordId), role).queue(null, err ->
                plugin.getLogger().warning("Impossible de retirer le rôle Membre : " + err.getMessage()));
    }

    private void syncNickname(String discordId, String mcName) {
        Guild g = guild();
        if (g == null || !cfg().getBoolean("discord.sync-nickname", false)) return;
        g.retrieveMemberById(discordId).queue(
                (Member m) -> g.modifyNickname(m, mcName).queue(null, err -> { }),
                err -> { });
    }

    // ================================================================== évènements JDA

    @Override
    public void onReady(ReadyEvent event) {
        ready = true;
        plugin.getLogger().info("Bot Discord connecté : " + event.getJDA().getSelfUser().getName());

        Guild g = guild();
        if (g == null) {
            plugin.getLogger().warning("Le bot n'est pas présent sur le serveur discord.guild-id : "
                    + "invitez-le avec l'URL d'invitation (voir README).");
            return;
        }
        g.updateCommands().addCommands(
                Commands.slash("link", "Lier votre compte Minecraft à Discord")
                        .addOption(OptionType.STRING, "code", "Code obtenu avec /link discord en jeu", true),
                Commands.slash("unlink", "Délier votre compte Minecraft"),
                Commands.slash("annonce", "Afficher une annonce sur l'écran des joueurs")
                        .addOption(OptionType.STRING, "message", "Texte de l'annonce", true)
        ).queue(
                ok -> plugin.getLogger().info("Commandes slash /link, /unlink et /annonce enregistrées."),
                err -> plugin.getLogger().warning("Enregistrement des commandes slash impossible : " + err.getMessage()));

        if (cfg().getBoolean("discord.chat.events.server-start-stop", true)) send("🟢 Serveur démarré");
    }

    @Override
    public void onShutdown(ShutdownEvent event) {
        ready = false;
        if (event.getCloseCode() != null) {
            plugin.getLogger().warning("Connexion Discord fermée : " + event.getCloseCode()
                    + " (si « DISALLOWED_INTENTS » : activez Message Content Intent et Server Members Intent "
                    + "dans le Developer Portal).");
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        Guild g = e.getGuild();
        String expected = cfg().getString("discord.guild-id", "");
        if (g == null || expected == null || !g.getId().equals(expected.trim())) return;

        switch (e.getName()) {
            case "link" -> handleLink(e);
            case "unlink" -> handleUnlink(e);
            case "annonce" -> handleAnnouncement(e);
            default -> { }
        }
    }

    private void handleAnnouncement(SlashCommandInteractionEvent e) {
        Member member = e.getMember();
        String adminRole = cfg().getString("discord.admin-role-id", "");
        boolean authorized = member != null && member.hasPermission(Permission.ADMINISTRATOR);
        if (!authorized && member != null && adminRole != null && !adminRole.isBlank())
            authorized = member.getRoles().stream().anyMatch(role -> role.getId().equals(adminRole.trim()));
        if (!authorized) {
            e.reply("⛔ Cette commande est réservée aux administrateurs.").setEphemeral(true).queue();
            return;
        }
        String requiredChannel = cfg().getString("discord.admin-channel-id", "");
        if (requiredChannel != null && !requiredChannel.isBlank() && !e.getChannel().getId().equals(requiredChannel.trim())) {
            e.reply("⛔ Utilisez cette commande dans le salon administration.").setEphemeral(true).queue();
            return;
        }
        String message = e.getOption("message", option -> option.getAsString());
        if (message == null || message.isBlank()) {
            e.reply("Le message de l'annonce ne peut pas être vide.").setEphemeral(true).queue();
            return;
        }
        if (message.length() > 250) message = message.substring(0, 247) + "...";
        final String announcement = message;
        EmbedBuilder embed = new EmbedBuilder().setTitle("📢 ANNONCE SERVEUR")
                .setDescription(announcement).setColor(new Color(220, 35, 35));
        e.getChannel().sendMessageEmbeds(embed.build()).queue();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component title = Component.text("ANNONCE", NamedTextColor.RED);
            Component subtitle = Component.text(announcement, NamedTextColor.RED);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(Title.title(title, subtitle));
                player.sendMessage(Component.text("[ANNONCE] " + announcement, NamedTextColor.RED));
            }
        });
        e.reply("✅ Annonce envoyée aux joueurs.").setEphemeral(true).queue();
    }

    private void handleLink(SlashCommandInteractionEvent e) {
        String raw = e.getOption("code", opt -> opt.getAsString());
        String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        Pending p = pending.get(code);
        if (p == null || p.expires() < System.currentTimeMillis()) {
            pending.remove(code);
            e.reply("❌ Code invalide ou expiré. Tapez **/link discord** en jeu pour en obtenir un nouveau.")
                    .setEphemeral(true).queue();
            return;
        }
        DataStore store = plugin.store();
        String discordId = e.getUser().getId();
        if (store.getByDiscord(discordId) != null) {
            e.reply("❌ Ce compte Discord est déjà lié à un compte Minecraft. Utilisez **/unlink** d'abord.")
                    .setEphemeral(true).queue();
            return;
        }
        if (store.getLink(p.uuid()) != null) {
            pending.remove(code);
            e.reply("❌ Ce compte Minecraft est déjà lié à un Discord.").setEphemeral(true).queue();
            return;
        }
        pending.remove(code);
        store.putLink(p.uuid(), new DataStore.Link(discordId, e.getUser().getName(), System.currentTimeMillis()));
        store.saveAsync();

        giveRole(discordId);
        syncNickname(discordId, p.name());
        e.reply("✅ Compte lié à **" + MarkdownSanitizer.escape(p.name()) + "** !"
                + (cfg().getString("discord.member-role-id", "").isBlank() ? "" : " Le rôle Membre vous a été attribué."))
                .setEphemeral(true).queue();

        final UUID uuid = p.uuid();
        final String mcName = p.name();
        final String discordName = e.getUser().getName();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                Text.send(online, "<green>Votre compte Discord <white>" + discordName + "<green> est maintenant lié !");
            }
            List<String> commands = cfg().getStringList("discord.link-commands");
            for (String cmd : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", mcName));
            }
        });
    }

    private void handleUnlink(SlashCommandInteractionEvent e) {
        UUID uuid = plugin.store().getByDiscord(e.getUser().getId());
        if (uuid == null) {
            e.reply("ℹ️ Votre compte Discord n'est lié à aucun compte Minecraft.").setEphemeral(true).queue();
            return;
        }
        unlink(uuid);
        e.reply("✅ Compte délié.").setEphemeral(true).queue();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) Text.send(online, "<yellow>Votre compte Discord a été délié depuis Discord.");
        });
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent e) {
        Guild g = guild();
        if (g == null || !e.getGuild().getId().equals(g.getId())) return;
        if (plugin.store().getByDiscord(e.getUser().getId()) != null) giveRole(e.getUser().getId());
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent e) {
        Guild g = guild();
        if (g == null || !e.getGuild().getId().equals(g.getId())) return;
        UUID uuid = plugin.store().getByDiscord(e.getUser().getId());
        if (uuid != null) {
            plugin.store().removeLink(uuid);
            plugin.store().saveAsync();
        }
    }

    /** Discord -> Minecraft. */
    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (!e.isFromGuild() || e.getAuthor().isBot() || e.isWebhookMessage()) return;
        if (!cfg().getBoolean("discord.chat.discord-to-minecraft", true)) return;
        String channelId = cfg().getString("discord.chat.channel-id", "");
        if (channelId == null || channelId.isBlank() || !e.getChannel().getId().equals(channelId.trim())) return;

        String text = e.getMessage().getContentDisplay().replace('\n', ' ').trim();
        int attachments = e.getMessage().getAttachments().size();
        if (attachments > 0) text = (text + " [" + attachments + " pièce(s) jointe(s)]").trim();
        if (text.isEmpty()) return;
        if (text.length() > 256) text = text.substring(0, 253) + "...";

        String name = e.getMember() != null ? e.getMember().getEffectiveName() : e.getAuthor().getName();
        if (cfg().getBoolean("discord.chat.use-minecraft-name-if-linked", true)) {
            UUID uuid = plugin.store().getByDiscord(e.getAuthor().getId());
            if (uuid != null) {
                DataStore.Known k = plugin.store().known(uuid);
                if (k != null) name = k.name();
                plugin.store().updateDiscordName(uuid, e.getAuthor().getName());
            }
        }

        String format = cfg().getString("discord.chat.discord-format",
                "<blue>[Discord]</blue> <gray><name></gray> <dark_gray>»</dark_gray> <white><message>");
        Component line = MiniMessage.miniMessage().deserialize(format,
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("message", text));
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(line));
    }

    // ================================================================== Minecraft -> Discord

    /** Envoie un texte brut dans le salon du pont (sans mentions possibles). */
    public void send(String text) {
        TextChannel ch = channel();
        if (!ready() || ch == null) return;
        if (text.length() > 1900) text = text.substring(0, 1900) + "...";
        ch.sendMessage(text).setAllowedMentions(EnumSet.noneOf(Message.MentionType.class)).queue();
    }

    /** Message de chat d'un joueur Minecraft. */
    public void sendChat(Player p, String message) {
        if (!cfg().getBoolean("discord.chat.minecraft-to-discord", true)) return;
        String prefix = Text.stripLegacy(plugin.profiles().prefixes().prefixNow(p)).trim();
        String format = cfg().getString("discord.chat.mc-format", "{prefix}**{name}** » {message}");
        String out = format
                .replace("{prefix}", prefix.isEmpty() ? "" : MarkdownSanitizer.escape(prefix) + " ")
                .replace("{name}", MarkdownSanitizer.escape(p.getName()))
                .replace("{message}", message.replace('\n', ' '));
        send(out);
    }

    /** Message d'évènement (connexion, déconnexion, mort...). */
    public void sendEvent(String configKey, String text) {
        if (!cfg().getBoolean("discord.chat.events." + configKey, true)) return;
        send(text);
    }
}
