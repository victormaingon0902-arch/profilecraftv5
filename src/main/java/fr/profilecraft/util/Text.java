package fr.profilecraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static String prefix = "<dark_gray>[<aqua>Profil<dark_gray>] <gray>";

    private Text() {}

    public static void setPrefix(String p) {
        prefix = p;
    }

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    /** Sans italique (noms / lores d'objets). */
    public static Component item(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> item(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(item(l));
        return out;
    }

    public static void send(CommandSender to, String body) {
        to.sendMessage(mm(prefix + body));
    }

    /** Préfixe LuckPerms ("&7[&cAdmin&7]", "&#ff0000Chef", "§c...") -> Component. */
    public static Component legacy(String s) {
        return LEGACY.deserialize(s.replace('§', '&')).decoration(TextDecoration.ITALIC, false);
    }

    /** Retire les codes couleur legacy ("&c", "§c", "&#rrggbb"). */
    public static String stripLegacy(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)[&§]#[0-9a-f]{6}", "").replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }

    /** 93784 s -> "1j 2h 3min". */
    public static String duration(long seconds) {
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        if (d > 0) return d + "j " + h + "h " + m + "min";
        if (h > 0) return h + "h " + m + "min";
        return m + "min";
    }
}
