package fr.profilecraft.profile;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Implémentation LuckPerms. Cette classe n'est chargée que si LuckPerms est installé. */
public final class LuckPermsPrefixProvider implements PrefixProvider {
    private final LuckPerms lp;

    private LuckPermsPrefixProvider(LuckPerms lp) {
        this.lp = lp;
    }

    /** @return le provider, ou null si l'API LuckPerms n'est pas disponible. */
    public static PrefixProvider tryCreate() {
        LuckPerms api = Bukkit.getServicesManager().load(LuckPerms.class);
        return api == null ? null : new LuckPermsPrefixProvider(api);
    }

    @Override
    public CompletableFuture<String> prefix(UUID id) {
        return lp.getUserManager().loadUser(id).thenApply(user -> {
            String p = user.getCachedData().getMetaData().getPrefix();
            return p == null ? "" : p;
        });
    }

    @Override
    public String prefixNow(Player p) {
        try {
            String s = lp.getPlayerAdapter(Player.class).getMetaData(p).getPrefix();
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }
}
