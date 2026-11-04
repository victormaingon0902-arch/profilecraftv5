package fr.profilecraft.listener;

import fr.profilecraft.ProfileCraft;
import fr.profilecraft.discord.DiscordManager;
import fr.profilecraft.head.HeadFactory;
import fr.profilecraft.util.Text;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Connexions, morts, drop de têtes et évènements envoyés à Discord. */
public final class PlayerListener implements Listener {
    private final ProfileCraft plugin;
    private final SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public PlayerListener(ProfileCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.store().rememberPlayer(p.getUniqueId(), p.getName(), HeadFactory.textureOf(p));

        DiscordManager d = plugin.discord();
        if (d.ready()) {
            d.sendEvent("join", "➡️ **" + MarkdownSanitizer.escape(p.getName()) + "** a rejoint le serveur");
        }

        FileConfiguration c = plugin.getConfig();
        if (c.getBoolean("discord.enabled", false) && c.getBoolean("discord.remind-on-join", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || !plugin.discord().ready()) return;
                if (plugin.store().getLink(p.getUniqueId()) != null) return;
                Component msg = Text.mm("<aqua>Liez votre compte Discord pour obtenir le rôle <white>Membre<aqua> : "
                                + "<yellow><underlined>/link discord")
                        .clickEvent(ClickEvent.runCommand("/link discord"));
                p.sendMessage(msg);
            }, 100L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.store().rememberPlayer(p.getUniqueId(), p.getName(), HeadFactory.textureOf(p));
        DiscordManager d = plugin.discord();
        if (d.ready()) {
            d.sendEvent("quit", "⬅️ **" + MarkdownSanitizer.escape(p.getName()) + "** a quitté le serveur");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        boolean pvp = killer != null && !killer.equals(victim);
        String texture = HeadFactory.textureOf(victim);
        plugin.store().rememberPlayer(victim.getUniqueId(), victim.getName(), texture);

        if (pvp) plugin.store().recordKill(killer.getUniqueId(), victim.getUniqueId(), victim.getName());

        // ---- drop de la tête (vrai skin de la victime)
        FileConfiguration c = plugin.getConfig();
        if (c.getBoolean("heads.drop.enabled", true)
                && (pvp || !c.getBoolean("heads.drop.only-pvp", true))
                && ThreadLocalRandom.current().nextDouble(100.0) < c.getDouble("heads.drop.chance", 100.0)) {
            String by = pvp ? "Tué par <white>" + killer.getName() : "<white>Mort naturelle";
            ItemStack head = HeadFactory.skull(victim.getUniqueId(), victim.getName(), texture,
                    Text.item("<yellow>Tête de " + victim.getName()),
                    Text.item(List.of("<gray>" + by, "<dark_gray>" + date.format(new Date()))));
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);
        }

        // ---- Discord
        DiscordManager d = plugin.discord();
        if (d.ready() && c.getBoolean("discord.chat.events.death", true)) {
            Component dm = e.deathMessage();
            String text = dm == null ? victim.getName() + " est mort"
                    : PlainTextComponentSerializer.plainText().serialize(dm);
            d.sendEvent("death", "💀 " + text);
        }
    }
}
