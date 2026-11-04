package fr.profilecraft.profile;

import fr.profilecraft.ProfileCraft;
import fr.profilecraft.data.DataStore;
import fr.profilecraft.gui.ProfileMenu;
import fr.profilecraft.head.HeadFactory;
import fr.profilecraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Construit les données d'un profil (statistiques vanilla + LuckPerms + Discord) et ouvre le menu. */
public final class ProfileService {
    private final ProfileCraft plugin;
    private PrefixProvider prefixes = PrefixProvider.NONE;

    public ProfileService(ProfileCraft plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                PrefixProvider lp = LuckPermsPrefixProvider.tryCreate();
                if (lp != null) {
                    prefixes = lp;
                    plugin.getLogger().info("LuckPerms détecté : les préfixes de rang seront affichés.");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("LuckPerms présent mais inutilisable : " + t.getMessage());
            }
        }
    }

    public PrefixProvider prefixes() {
        return prefixes;
    }

    /** Ouvre le profil de {@code targetId} pour {@code viewer}. */
    public void open(Player viewer, UUID targetId) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(targetId);
        DataStore.Known known = plugin.store().known(targetId);
        if (!off.isOnline() && !off.hasPlayedBefore() && known == null) {
            Text.send(viewer, "<red>Ce joueur est inconnu.");
            return;
        }
        prefixes.prefix(targetId).whenComplete((prefix, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!viewer.isOnline()) return;
                    ProfileData data = gather(off, err == null && prefix != null ? prefix : "");
                    new ProfileMenu(plugin, viewer, data).open();
                }));
    }

    private ProfileData gather(OfflinePlayer off, String rawPrefix) {
        UUID id = off.getUniqueId();
        Player online = off.getPlayer();
        DataStore store = plugin.store();
        DataStore.Known known = store.known(id);

        String name = off.getName() != null ? off.getName() : (known != null ? known.name() : "?");
        String texture = online != null ? HeadFactory.textureOf(online) : (known != null ? known.texture() : null);
        if (texture == null && known != null) texture = known.texture();

        long ticks = stat(off, Statistic.PLAY_ONE_MINUTE);
        int kills = (int) stat(off, Statistic.PLAYER_KILLS);
        int deaths = (int) stat(off, Statistic.DEATHS);
        int mobKills = (int) stat(off, Statistic.MOB_KILLS);
        int byPlayer = 0;
        try {
            byPlayer = off.getStatistic(Statistic.ENTITY_KILLED_BY, EntityType.PLAYER);
        } catch (IllegalArgumentException ignored) {
            // statistique indisponible
        }

        return new ProfileData(id, name, texture, rawPrefix, ticks / 20, kills, byPlayer, deaths, mobKills,
                off.getFirstPlayed(), off.isOnline(), off.getLastSeen(), store.headCount(id), store.getLink(id));
    }

    private long stat(OfflinePlayer p, Statistic s) {
        try {
            return p.getStatistic(s);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }
}
