package fr.profilecraft.listener;

import fr.profilecraft.ProfileCraft;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Minecraft -> Discord. */
public final class ChatListener implements Listener {
    private final ProfileCraft plugin;

    public ChatListener(ProfileCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.discord().ready()) return;
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (msg.isBlank()) return;
        plugin.discord().sendChat(e.getPlayer(), msg);
    }
}
