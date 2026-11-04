package fr.profilecraft.profile;

import fr.profilecraft.data.DataStore;

import java.util.UUID;

/** Instantané des informations affichées dans le menu de profil. */
public record ProfileData(
        UUID id,
        String name,
        String texture,
        String rawPrefix,
        long playSeconds,
        int kills,
        int deathsByPlayer,
        int deaths,
        int mobKills,
        long firstPlayed,
        boolean online,
        long lastSeen,
        int headsCollected,
        DataStore.Link link) {}
