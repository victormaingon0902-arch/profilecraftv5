package fr.profilecraft;

import fr.profilecraft.command.AdminCommand;
import fr.profilecraft.command.HeadCommand;
import fr.profilecraft.command.LinkCommand;
import fr.profilecraft.command.ProfileCommand;
import fr.profilecraft.data.DataStore;
import fr.profilecraft.discord.DiscordManager;
import fr.profilecraft.gui.MenuListener;
import fr.profilecraft.listener.ChatListener;
import fr.profilecraft.listener.PlayerListener;
import fr.profilecraft.listener.ProfileListener;
import fr.profilecraft.profile.ProfileService;
import fr.profilecraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ProfileCraft : profils (shift + clic droit), têtes des joueurs tués (/head),
 * liaison Discord (/link discord) et pont de chat Minecraft <-> Discord.
 */
public final class ProfileCraft extends JavaPlugin {
    private DataStore store;
    private ProfileService profiles;
    private DiscordManager discord;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        Text.setPrefix(getConfig().getString("prefix", "<dark_gray>[<aqua>Profil<dark_gray>] <gray>"));

        store = new DataStore(this);
        profiles = new ProfileService(this);
        discord = new DiscordManager(this);

        Bukkit.getPluginManager().registerEvents(new ProfileListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);

        register("profile", new ProfileCommand(this));
        register("head", new HeadCommand(this));
        register("link", new LinkCommand(this));
        register("profilecraft", new AdminCommand(this));

        // joueurs déjà connectés (rechargement à chaud)
        Bukkit.getOnlinePlayers().forEach(p -> store.rememberPlayer(p.getUniqueId(), p.getName(),
                fr.profilecraft.head.HeadFactory.textureOf(p)));

        // sauvegarde automatique toutes les 2 minutes
        Bukkit.getScheduler().runTaskTimer(this, store::saveAsync, 2400L, 2400L);

        discord.start();
        getLogger().info("ProfileCraft activé.");
    }

    @Override
    public void onDisable() {
        if (discord != null) discord.shutdown();
        if (store != null) store.saveNow();
    }

    public void reloadAll() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        Text.setPrefix(getConfig().getString("prefix", "<dark_gray>[<aqua>Profil<dark_gray>] <gray>"));
        discord.restart();
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().warning("Commande absente de plugin.yml : " + name);
            return;
        }
        c.setExecutor(executor);
        if (executor instanceof TabCompleter tc) c.setTabCompleter(tc);
    }

    public DataStore store() { return store; }
    public ProfileService profiles() { return profiles; }
    public DiscordManager discord() { return discord; }
}
