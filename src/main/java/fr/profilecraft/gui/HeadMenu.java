package fr.profilecraft.gui;

import fr.profilecraft.ProfileCraft;
import fr.profilecraft.data.DataStore;
import fr.profilecraft.head.HeadFactory;
import fr.profilecraft.util.Items;
import fr.profilecraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** /head : les têtes de tous les joueurs que vous avez tués (avec leur vrai skin). */
public final class HeadMenu extends Menu {
    private static final int PER_PAGE = 45;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final int page;

    public HeadMenu(ProfileCraft plugin, Player viewer, int page) {
        super(plugin, viewer, 6, Text.mm("<dark_aqua><bold>Têtes collectées <dark_gray>» <gray>"
                + plugin.store().headCount(viewer.getUniqueId())));
        this.page = page;
        build();
    }

    private void build() {
        DataStore store = plugin.store();
        fill(Items.pane(), 45, 53);

        String mode = plugin.getConfig().getString("heads.menu.claim-mode", "unlimited").toLowerCase(Locale.ROOT);
        boolean viewOnly = mode.equals("view-only");
        boolean perKill = mode.equals("per-kill");

        List<DataStore.HeadView> list = store.headsOf(viewer.getUniqueId());
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PER_PAGE));
        int from = page * PER_PAGE;
        int to = Math.min(list.size(), from + PER_PAGE);

        if (list.isEmpty()) {
            set(22, Items.make(Material.BARRIER, "<red>Aucune tête pour l'instant",
                    "<gray>Tuez un autre joueur pour collectionner sa tête."));
        }

        for (int i = from; i < to; i++) {
            DataStore.HeadView h = list.get(i);
            DataStore.Known known = store.known(h.victim());
            String texture = known != null ? known.texture() : null;

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Tué <white>" + h.kills() + "<gray> fois");
            lore.add("<gray>Dernier kill : <white>" + date(h.last()));
            lore.add("");
            if (viewOnly) {
                lore.add("<dark_gray>Collection (lecture seule)");
            } else if (perKill) {
                int left = Math.max(0, h.kills() - h.claimed());
                lore.add(left > 0 ? "<green>Clic : récupérer la tête <gray>(" + left + " restante(s))"
                        : "<red>Aucune tête restante pour ce joueur");
            } else {
                lore.add("<green>Clic : récupérer la tête");
            }

            ItemStack icon = HeadFactory.skull(h.victim(), h.name(), texture,
                    Text.item("<yellow>" + h.name()), Text.item(lore));
            set(i - from, icon, e -> {
                if (viewOnly) return;
                if (!store.claimHead(viewer.getUniqueId(), h.victim(), perKill)) {
                    Text.send(viewer, "<red>Vous ne pouvez plus récupérer la tête de <white>" + h.name() + "<red>.");
                    return;
                }
                Items.give(viewer, HeadFactory.skull(h.victim(), h.name(), texture,
                        Text.item("<yellow>Tête de " + h.name()),
                        List.of(Text.item("<gray>Collection de <white>" + viewer.getName()))));
                Text.send(viewer, "<green>Vous récupérez la tête de <white>" + h.name() + "<green>.");
                later(() -> new HeadMenu(plugin, viewer, page).open());
            });
        }

        set(49, Items.make(Material.PAPER, "<white>Page " + (page + 1) + "/" + pages,
                "<gray>" + list.size() + " tête(s) collectée(s)"));
        if (page > 0) {
            set(48, Items.make(Material.ARROW, "<yellow>« Page précédente"),
                    e -> later(() -> new HeadMenu(plugin, viewer, page - 1).open()));
        }
        if (page + 1 < pages) {
            set(50, Items.make(Material.ARROW, "<yellow>Page suivante »"),
                    e -> later(() -> new HeadMenu(plugin, viewer, page + 1).open()));
        }
    }

    private static String date(long millis) {
        synchronized (DATE) {
            return DATE.format(new Date(millis));
        }
    }
}
