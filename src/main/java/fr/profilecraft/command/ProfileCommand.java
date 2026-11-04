package fr.profilecraft.command;

import fr.profilecraft.ProfileCraft;
import fr.profilecraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /profile [joueur] */
public final class ProfileCommand implements CommandExecutor, TabCompleter {
    private final ProfileCraft plugin;

    public ProfileCommand(ProfileCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            Text.send(s, "<red>Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("profilecraft.profile")) {
            Text.send(p, "<red>Permission manquante.");
            return true;
        }
        if (a.length == 0) {
            plugin.profiles().open(p, p.getUniqueId());
            return true;
        }
        UUID id = null;
        Player online = Bukkit.getPlayerExact(a[0]);
        if (online != null) {
            id = online.getUniqueId();
        } else {
            id = plugin.store().findByName(a[0]);
            if (id == null) {
                OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(a[0]);
                if (off != null) id = off.getUniqueId();
            }
        }
        if (id == null) {
            Text.send(p, "<red>Joueur inconnu : " + a[0]);
            return true;
        }
        plugin.profiles().open(p, id);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length != 1) return out;
        String last = a[0].toLowerCase(Locale.ROOT);
        for (String n : plugin.store().knownNames()) {
            if (n.toLowerCase(Locale.ROOT).startsWith(last)) out.add(n);
        }
        return out;
    }
}
