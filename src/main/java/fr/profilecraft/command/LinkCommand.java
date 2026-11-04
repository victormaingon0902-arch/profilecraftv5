package fr.profilecraft.command;

import fr.profilecraft.ProfileCraft;
import fr.profilecraft.data.DataStore;
import fr.profilecraft.discord.DiscordManager;
import fr.profilecraft.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /link discord | /link unlink | /link status */
public final class LinkCommand implements CommandExecutor, TabCompleter {
    private final ProfileCraft plugin;

    public LinkCommand(ProfileCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            Text.send(s, "<red>Commande réservée aux joueurs.");
            return true;
        }
        if (!p.hasPermission("profilecraft.link")) {
            Text.send(p, "<red>Permission manquante.");
            return true;
        }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "discord" -> start(p);
            case "unlink" -> {
                if (plugin.discord().unlink(p.getUniqueId())) {
                    Text.send(p, "<yellow>Votre compte Discord a été délié.");
                } else {
                    Text.send(p, "<gray>Votre compte n'est lié à aucun Discord.");
                }
            }
            case "status" -> {
                DataStore.Link l = plugin.store().getLink(p.getUniqueId());
                if (l == null) {
                    Text.send(p, "<gray>Discord : <red>non lié <gray>- tapez <white>/link discord");
                } else {
                    Text.send(p, "<gray>Discord : <green>lié à <white>" + l.discordName());
                }
            }
            default -> Text.send(p, "<gray>Usage : <white>/link discord<gray> | <white>/link unlink<gray> | <white>/link status");
        }
        return true;
    }

    private void start(Player p) {
        DiscordManager d = plugin.discord();
        if (!d.ready()) {
            Text.send(p, "<red>Le lien Discord est indisponible pour le moment.");
            return;
        }
        DataStore.Link existing = plugin.store().getLink(p.getUniqueId());
        if (existing != null) {
            Text.send(p, "<yellow>Vous êtes déjà lié à <white>" + existing.discordName()
                    + "<yellow>. Utilisez <white>/link unlink<yellow> pour délier.");
            return;
        }
        String code = d.createCode(p);
        String url = plugin.getConfig().getString("discord.invite-url", "");
        long minutes = plugin.getConfig().getLong("discord.link-code-minutes", 10);

        p.sendMessage(Text.mm("<aqua><bold>Liaison Discord"));
        Component step1 = Text.mm("<gray>1. Rejoignez le Discord : <aqua><underlined>" + url);
        if (url != null && url.startsWith("http")) step1 = step1.clickEvent(ClickEvent.openUrl(url));
        p.sendMessage(step1);
        p.sendMessage(Text.mm("<gray>2. Dans n'importe quel salon, tapez la commande <white>/link<gray> puis collez ce code :")
                .clickEvent(ClickEvent.copyToClipboard(code)));
        p.sendMessage(Text.mm("   <yellow><bold>" + code + " <dark_gray>(cliquez pour copier)")
                .clickEvent(ClickEvent.copyToClipboard(code)));
        p.sendMessage(Text.mm("<gray>Le code expire dans <white>" + minutes + " minutes<gray>."));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String o : List.of("discord", "unlink", "status")) {
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(o);
            }
        }
        return out;
    }
}
