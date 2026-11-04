package fr.profilecraft.gui;

import fr.profilecraft.ProfileCraft;
import fr.profilecraft.data.DataStore;
import fr.profilecraft.head.HeadFactory;
import fr.profilecraft.profile.ProfileData;
import fr.profilecraft.util.Items;
import fr.profilecraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Menu de profil d'un joueur. */
public final class ProfileMenu extends Menu {
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final ProfileData d;

    public ProfileMenu(ProfileCraft plugin, Player viewer, ProfileData d) {
        super(plugin, viewer, 4, Text.mm("<dark_aqua><bold>Profil <dark_gray>» <white>" + d.name()));
        this.d = d;
        build();
    }

    private void build() {
        fill(Items.pane(), 0, 35);

        boolean hasPrefix = !Text.stripLegacy(d.rawPrefix()).isBlank();
        Component prefixComponent = hasPrefix ? Text.legacy(d.rawPrefix().trim()) : Component.empty();

        // ---- tête + statut
        Component title = hasPrefix
                ? prefixComponent.append(Component.text(" ")).append(Text.item("<white><bold>" + d.name()))
                : Text.item("<white><bold>" + d.name());
        String status = d.online()
                ? "<green>● En ligne"
                : "<gray>● Dernière connexion : <white>" + date(d.lastSeen());
        ItemStack head = HeadFactory.skull(d.id(), d.name(), d.texture(), title,
                Text.item(List.of(status)));
        set(4, head);

        // ---- rang (préfixe LuckPerms uniquement)
        ItemStack rank = Items.make(Material.NAME_TAG, "<yellow>Rang");
        ItemMeta rm = rank.getItemMeta();
        rm.lore(List.of(hasPrefix ? prefixComponent : Text.item("<gray>Aucun")));
        rank.setItemMeta(rm);
        set(10, rank);

        set(12, Items.make(Material.CLOCK, "<yellow>Temps de jeu",
                "<white>" + Text.duration(d.playSeconds())));
        set(14, Items.make(Material.DIAMOND_SWORD, "<yellow>Joueurs tués",
                "<white>" + d.kills()));
        set(16, Items.make(Material.SKELETON_SKULL, "<yellow>Morts par un joueur",
                "<white>" + d.deathsByPlayer()));

        String ratio = String.format(Locale.US, "%.2f",
                d.deathsByPlayer() == 0 ? (double) d.kills() : d.kills() / (double) d.deathsByPlayer());
        set(20, Items.make(Material.COMPASS, "<yellow>Ratio PvP", "<white>" + ratio,
                "<gray>(joueurs tués / morts par un joueur)"));
        set(22, Items.make(Material.BONE, "<yellow>Morts totales", "<white>" + d.deaths()));
        set(24, Items.make(Material.ROTTEN_FLESH, "<yellow>Monstres tués", "<white>" + d.mobKills()));

        set(29, Items.make(Material.ZOMBIE_HEAD, "<yellow>Têtes collectées", "<white>" + d.headsCollected()));

        // ---- Discord
        if (plugin.getConfig().getBoolean("profile.show-discord", true)) {
            DataStore.Link l = d.link();
            if (l != null) {
                set(31, Items.make(Material.BLUE_BANNER, "<#5865F2>Discord", "<white>" + l.discordName(),
                        "<green>Compte lié"));
            } else {
                set(31, Items.make(Material.GRAY_BANNER, "<#5865F2>Discord", "<gray>Non lié"));
            }
        }

        set(33, Items.make(Material.WRITABLE_BOOK, "<yellow>Première connexion",
                "<white>" + (d.firstPlayed() > 0 ? date(d.firstPlayed()) : "?")));
    }

    private static String date(long millis) {
        if (millis <= 0) return "?";
        synchronized (DATE) {
            return DATE.format(new Date(millis));
        }
    }
}
