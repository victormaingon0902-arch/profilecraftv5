package fr.profilecraft.command;

import fr.profilecraft.ProfileCraft;
import fr.profilecraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /profilecraft reload | unlink <joueur> | status */
public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final ProfileCraft plugin;

    public AdminCommand(ProfileCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                Text.send(s, "<green>Configuration rechargée, bot Discord redémarré.");
            }
            case "status" -> Text.send(s, "<gray>Discord : "
                    + (plugin.discord().ready() ? "<green>connecté" : "<red>non connecté"));
            case "unlink" -> {
                if (a.length < 2) {
                    Text.send(s, "<red>Usage : /profilecraft unlink <joueur>");
                    return true;
                }
                UUID id = null;
                Player online = Bukkit.getPlayerExact(a[1]);
                if (online != null) id = online.getUniqueId();
                else id = plugin.store().findByName(a[1]);
                if (id == null) {
                    Text.send(s, "<red>Joueur inconnu.");
                } else if (plugin.discord().unlink(id)) {
                    Text.send(s, "<green>Liaison supprimée.");
                } else {
                    Text.send(s, "<gray>Ce joueur n'est pas lié.");
                }
            }
            default -> Text.send(s, "<red>Usage : /profilecraft <reload | unlink <joueur> | status>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String o : List.of("reload", "unlink", "status")) {
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(o);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("unlink")) {
            for (String n : plugin.store().knownNames()) {
                if (n.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(n);
            }
        }
        return out;
    }
}
