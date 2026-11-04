package fr.profilecraft.data;

import fr.profilecraft.ProfileCraft;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stockage (plugins/ProfileCraft/data.yml) :
 *  - joueurs connus (pseudo + texture du skin, pour fabriquer leurs têtes même hors-ligne)
 *  - têtes collectées par chaque tueur
 *  - liens Minecraft <-> Discord
 * Toutes les méthodes sont synchronisées (accès depuis le thread principal ET les threads JDA).
 */
public final class DataStore {

    public record Known(String name, String texture) {}

    public record Link(String discordId, String discordName, long linkedAt) {}

    /** Vue immuable d'une tête collectée. */
    public record HeadView(UUID victim, String name, int kills, int claimed, long last) {}

    private static final class HeadEntry {
        String name;
        int kills;
        int claimed;
        long last;
    }

    private final ProfileCraft plugin;
    private final File file;
    private final Object ioLock = new Object();
    private final Map<UUID, Known> players = new HashMap<>();
    private final Map<UUID, Map<UUID, HeadEntry>> heads = new HashMap<>();
    private final Map<UUID, Link> links = new HashMap<>();
    private final Map<String, UUID> byDiscord = new HashMap<>();
    private volatile boolean dirty;

    public DataStore(ProfileCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    // ------------------------------------------------------------------ joueurs connus

    /** Mémorise pseudo + texture (si texture == null, l'ancienne est conservée). */
    public synchronized void rememberPlayer(UUID id, String name, String texture) {
        Known old = players.get(id);
        String tex = texture != null ? texture : (old != null ? old.texture() : null);
        players.put(id, new Known(name, tex));
        dirty = true;
    }

    public synchronized Known known(UUID id) {
        return players.get(id);
    }

    public synchronized UUID findByName(String name) {
        for (Map.Entry<UUID, Known> e : players.entrySet()) {
            if (e.getValue().name().equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    public synchronized List<String> knownNames() {
        List<String> out = new ArrayList<>();
        for (Known k : players.values()) out.add(k.name());
        return out;
    }

    // ------------------------------------------------------------------ têtes

    public synchronized void recordKill(UUID killer, UUID victim, String victimName) {
        HeadEntry e = heads.computeIfAbsent(killer, k -> new HashMap<>()).computeIfAbsent(victim, k -> new HeadEntry());
        e.name = victimName;
        e.kills++;
        e.last = System.currentTimeMillis();
        dirty = true;
    }

    /** Têtes collectées, la plus récente d'abord. */
    public synchronized List<HeadView> headsOf(UUID killer) {
        Map<UUID, HeadEntry> m = heads.get(killer);
        List<HeadView> out = new ArrayList<>();
        if (m == null) return out;
        for (Map.Entry<UUID, HeadEntry> en : m.entrySet()) {
            HeadEntry h = en.getValue();
            out.add(new HeadView(en.getKey(), h.name, h.kills, h.claimed, h.last));
        }
        out.sort(Comparator.comparingLong(HeadView::last).reversed());
        return out;
    }

    public synchronized int headCount(UUID killer) {
        Map<UUID, HeadEntry> m = heads.get(killer);
        return m == null ? 0 : m.size();
    }

    /**
     * Tente de récupérer une tête.
     * @param perKill true = limité au nombre de kills ; false = illimité
     */
    public synchronized boolean claimHead(UUID killer, UUID victim, boolean perKill) {
        Map<UUID, HeadEntry> m = heads.get(killer);
        HeadEntry e = m == null ? null : m.get(victim);
        if (e == null) return false;
        if (perKill && e.claimed >= e.kills) return false;
        e.claimed++;
        dirty = true;
        return true;
    }

    // ------------------------------------------------------------------ liens Discord

    public synchronized Link getLink(UUID id) {
        return links.get(id);
    }

    public synchronized UUID getByDiscord(String discordId) {
        return byDiscord.get(discordId);
    }

    public synchronized void putLink(UUID id, Link link) {
        Link old = links.put(id, link);
        if (old != null) byDiscord.remove(old.discordId());
        byDiscord.put(link.discordId(), id);
        dirty = true;
    }

    public synchronized Link removeLink(UUID id) {
        Link old = links.remove(id);
        if (old != null) {
            byDiscord.remove(old.discordId());
            dirty = true;
        }
        return old;
    }

    public synchronized void updateDiscordName(UUID id, String discordName) {
        Link l = links.get(id);
        if (l != null && !l.discordName().equals(discordName)) {
            links.put(id, new Link(l.discordId(), discordName, l.linkedAt()));
            dirty = true;
        }
    }

    // ------------------------------------------------------------------ persistance

    private synchronized void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                try {
                    ConfigurationSection s = ps.getConfigurationSection(k);
                    if (s != null) players.put(UUID.fromString(k), new Known(s.getString("name", "?"), s.getString("texture")));
                } catch (IllegalArgumentException ignored) {
                    // clé invalide
                }
            }
        }
        ConfigurationSection hs = y.getConfigurationSection("heads");
        if (hs != null) {
            for (String killerKey : hs.getKeys(false)) {
                try {
                    UUID killer = UUID.fromString(killerKey);
                    ConfigurationSection ks = hs.getConfigurationSection(killerKey);
                    if (ks == null) continue;
                    Map<UUID, HeadEntry> map = new HashMap<>();
                    for (String victimKey : ks.getKeys(false)) {
                        ConfigurationSection v = ks.getConfigurationSection(victimKey);
                        if (v == null) continue;
                        HeadEntry e = new HeadEntry();
                        e.name = v.getString("name", "?");
                        e.kills = v.getInt("kills");
                        e.claimed = v.getInt("claimed");
                        e.last = v.getLong("last");
                        map.put(UUID.fromString(victimKey), e);
                    }
                    heads.put(killer, map);
                } catch (IllegalArgumentException ignored) {
                    // clé invalide
                }
            }
        }
        ConfigurationSection ls = y.getConfigurationSection("links");
        if (ls != null) {
            for (String k : ls.getKeys(false)) {
                try {
                    ConfigurationSection s = ls.getConfigurationSection(k);
                    if (s == null) continue;
                    UUID id = UUID.fromString(k);
                    Link l = new Link(s.getString("discord-id", ""), s.getString("discord-name", "?"), s.getLong("at"));
                    links.put(id, l);
                    byDiscord.put(l.discordId(), id);
                } catch (IllegalArgumentException ignored) {
                    // clé invalide
                }
            }
        }
    }

    private synchronized String snapshot() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Known> e : players.entrySet()) {
            y.set("players." + e.getKey() + ".name", e.getValue().name());
            y.set("players." + e.getKey() + ".texture", e.getValue().texture());
        }
        for (Map.Entry<UUID, Map<UUID, HeadEntry>> k : heads.entrySet()) {
            for (Map.Entry<UUID, HeadEntry> v : k.getValue().entrySet()) {
                String base = "heads." + k.getKey() + "." + v.getKey();
                y.set(base + ".name", v.getValue().name);
                y.set(base + ".kills", v.getValue().kills);
                y.set(base + ".claimed", v.getValue().claimed);
                y.set(base + ".last", v.getValue().last);
            }
        }
        for (Map.Entry<UUID, Link> e : links.entrySet()) {
            String base = "links." + e.getKey();
            y.set(base + ".discord-id", e.getValue().discordId());
            y.set(base + ".discord-name", e.getValue().discordName());
            y.set(base + ".at", e.getValue().linkedAt());
        }
        return y.saveToString();
    }

    private void write(String data) {
        synchronized (ioLock) {
            try {
                Files.createDirectories(file.getParentFile().toPath());
                Path tmp = new File(file.getParentFile(), "data.yml.tmp").toPath();
                Files.writeString(tmp, data, StandardCharsets.UTF_8);
                Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de sauvegarder data.yml : " + e.getMessage());
            }
        }
    }

    /** Sauvegarde asynchrone si des données ont changé (peut être appelée de n'importe quel thread). */
    public void saveAsync() {
        if (!dirty) return;
        dirty = false;
        String data = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    public void saveNow() {
        dirty = false;
        write(snapshot());
    }
}
