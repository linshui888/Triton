package com.rexcantor64.multilanguageplugin.language;

import com.rexcantor64.multilanguageplugin.banners.Banner;
import org.apache.commons.lang3.StringEscapeUtils;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class Language {

    private String name;
    private List<String> minecraftCode;
    private String rawDisplayName;
    private String displayName;
    private ItemStack stack;
    private String flagCode;

    Language(String name, String flagCode, List<String> minecraftCode, String displayName) {
        this.name = name;
        this.rawDisplayName = displayName;
        this.displayName = ChatColor.translateAlternateColorCodes('&', StringEscapeUtils.unescapeJava(displayName));
        Banner banner = new Banner(flagCode);
        this.stack = banner.toBukkit();
        ItemMeta im = stack.getItemMeta();
        im.setDisplayName(this.displayName);
        stack.setItemMeta(im);
        this.minecraftCode = minecraftCode;
        this.flagCode = flagCode;
    }

    public String getName() {
        return name;
    }

    public List<String> getMinecraftCodes() {
        return minecraftCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ItemStack getStack() {
        return getStack(false);
    }

    public ItemStack getStack(boolean glow) {
        ItemStack is = stack.clone();
        if (glow)
            is.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        return is;
    }

    public String getRawDisplayName() {
        return rawDisplayName;
    }

    public String getFlagCode() {
        return flagCode;
    }
}
