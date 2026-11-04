package fr.profilecraft.head;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/** Fabrique de têtes avec le VRAI skin du joueur (texture), jamais Steve. */
public final class HeadFactory {
    private HeadFactory() {}

    /** Valeur base64 de la propriété "textures" d'un joueur en ligne (ou null). */
    public static String textureOf(Player p) {
        for (ProfileProperty pr : p.getPlayerProfile().getProperties()) {
            if (pr.getName().equals("textures")) return pr.getValue();
        }
        return null;
    }

    public static ItemStack skull(UUID id, String name, String texture, Component display, List<Component> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        String safe = name == null ? null : (name.length() > 16 ? name.substring(0, 16) : name);
        PlayerProfile profile = Bukkit.createProfile(id, safe);
        if (texture != null && !texture.isBlank()) {
            profile.setProperty(new ProfileProperty("textures", texture));
        }
        m.setPlayerProfile(profile);
        if (display != null) m.displayName(display);
        if (lore != null && !lore.isEmpty()) m.lore(lore);
        it.setItemMeta(m);
        return it;
    }
}
