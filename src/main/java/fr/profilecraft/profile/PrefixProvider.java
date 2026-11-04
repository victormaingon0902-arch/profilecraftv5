package fr.profilecraft.profile;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Fournit le préfixe de rang (LuckPerms si présent, sinon vide). */
public interface PrefixProvider {

    /** Préfixe brut (codes legacy "&c...") d'un joueur, en ligne ou non. Jamais null. */
    CompletableFuture<String> prefix(UUID id);

    /** Préfixe brut d'un joueur en ligne, lu dans le cache (sans I/O). Jamais null. */
    String prefixNow(Player p);

    PrefixProvider NONE = new PrefixProvider() {
        @Override
        public CompletableFuture<String> prefix(UUID id) {
            return CompletableFuture.completedFuture("");
        }

        @Override
        public String prefixNow(Player p) {
            return "";
        }
    };
}
