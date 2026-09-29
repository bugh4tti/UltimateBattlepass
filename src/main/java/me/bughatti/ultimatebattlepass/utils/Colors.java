package me.bughatti.ultimatebattlepass.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Colors {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Colors() {
    }

    /**
     * Convierte los códigos & y los colores hex (&#RRGGBB).
     */
    public static String colorize(String text) {
        if (text == null) {
            return "";
        }

        String cc = String.valueOf(ChatColor.COLOR_CHAR);
        Matcher matcher = HEX.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder(cc).append("x");
            for (char c : matcher.group(1).toCharArray()) {
                replacement.append(cc).append(c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static List<String> colorize(List<String> lines) {
        List<String> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }

    public static String strip(String text) {
        return ChatColor.stripColor(colorize(text));
    }
            }
