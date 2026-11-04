package fr.profilecraft.listener;

import fr.profilecraft.ProfileCraft;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shift + clic droit sur un joueur = ouvre son profil. */
public final class ProfileListener implements Listener {
    private final ProfileCraft plugin;
    private final Map<UUID, Integer> last = new HashMap<>();

    public ProfileListener(ProfileCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player target)) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || e.getHand() != EquipmentSlot.HAND) return;
        if (!plugin.getConfig().getBoolean("profile.shift-click", true)) return;
        if (target.hasMetadata("NPC")) return;             // PNJ Citizens
        if (!p.hasPermission("profilecraft.profile")) return;

        e.setCancelled(true);
        int now = Bukkit.getCurrentTick();
        Integer prev = last.get(p.getUniqueId());
        if (prev != null && now - prev < 10) return;       // PlayerInteractAt + PlayerInteract dans le même tick
        last.put(p.getUniqueId(), now);

        plugin.profiles().open(p, target.getUniqueId());
    }
}
