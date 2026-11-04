package fr.profilecraft.command;

import fr.profilecraft.ProfileCraft;
import fr.profilecraft.gui.HeadMenu;
import fr.profilecraft.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /head : menu des têtes des joueurs tués. */
public final class HeadCommand implements CommandExecutor {
    private final ProfileCraft plugin;

    public HeadCommand(ProfileCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            Text.send(s, "<red>Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("profilecraft.head")) {
            Text.send(p, "<red>Permission manquante.");
            return true;
        }
        new HeadMenu(plugin, p, 0).open();
        return true;
    }
}
