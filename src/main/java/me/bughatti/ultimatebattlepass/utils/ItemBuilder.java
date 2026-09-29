package me.bughatti.ultimatebattlepass.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemBuilder {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private Material material = Material.STONE;
    private int amount = 1;
    private String name = null;
    private List<String> lore = new ArrayList<>();
    private String texture = "";

    public static ItemBuilder fromSection(ConfigurationSection section) {
        return fromSection(section, "lore");
    }

    /**
     * Crea el ítem desde una sección del yml. Con loreKey se puede elegir
     * otra lista de lore (por ejemplo "lore-active").
     */
    public static ItemBuilder fromSection(ConfigurationSection section, String loreKey) {
        ItemBuilder builder = new ItemBuilder();
        if (section == null) {
            return builder;
        }

        Material found = Material.matchMaterial(section.getString("material", "STONE"));
        if (found != null && !found.isAir() && found.isItem()) {
            builder.material = found;
        } else {
            builder.material = Material.BARRIER;
        }

        builder.amount = clamp(section.getInt("amount", 1));
        builder.name = section.getString("name");
        builder.lore = new ArrayList<>(section.getStringList(loreKey));
        builder.texture = section.getString("texture", "");
        return builder;
    }

    public ItemBuilder amount(int amount) {
        this.amount = clamp(amount);
        return this;
    }

    /**
     * Reemplaza un texto en el nombre y en todas las líneas del lore.
     */
    public ItemBuilder replace(String key, String value) {
        if (name != null) {
            name = name.replace(key, value);
        }
        lore.replaceAll(line -> line.replace(key, value));
        return this;
    }

    /**
     * Reemplaza una línea del lore (que sea exactamente "key") por varias líneas.
     * Conviene llamarlo antes que replace(...).
     */
    public ItemBuilder replaceLines(String key, List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lore) {
            if (line.trim().equals(key)) {
                result.addAll(lines);
            } else {
                result.add(line);
            }
        }
        lore = result;
        return this;
    }

    public ItemStack build() {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (name != null) {
            meta.setDisplayName(Colors.colorize(name));
        }
        if (!lore.isEmpty()) {
            meta.setLore(Colors.colorize(lore));
        }
        meta.addItemFlags(ItemFlag.values());

        if (material == Material.PLAYER_HEAD && texture != null && !texture.isEmpty()
                && meta instanceof SkullMeta skull) {
            applyTexture(skull);
        }

        item.setItemMeta(meta);
        return item;
    }

    private void applyTexture(SkullMeta meta) {
        String url = resolveUrl(texture);
        if (url == null) {
            return;
        }

        try {
            UUID id = UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createPlayerProfile(id);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(url));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException ignored) {
            // Textura inválida: se deja la cabeza por defecto
        }
    }

    /**
     * Acepta una URL directa (http://textures.minecraft.net/...) o el valor
     * base64 que dan las páginas de cabezas.
     */
    private static String resolveUrl(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("http")) {
            return trimmed;
        }

        try {
            String json = new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
            Matcher matcher = URL_PATTERN.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IllegalArgumentException ignored) {
            // No era base64
        }
        return null;
    }

    private static int clamp(int amount) {
        return Math.max(1, Math.min(64, amount));
    }
  }
