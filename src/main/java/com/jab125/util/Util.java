package com.jab125.util;

import com.terraformersmc.modmenu.gui.widget.entries.ModListEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.hat.gt.GobT;

import java.util.Collection;

public class Util {
    public static boolean isModInstalled(String modid) {
        return FabricLoader.getInstance().isModLoaded(modid);
    }
    public static boolean isOverflowInstalled() {
        return isModInstalled("overflow");
    }
    public static boolean isEnchantmentDisplaysInstalled() {
        return isModInstalled("enchantment-displays");
    }
    public static boolean maxEnchantTextConfig() {
        if (isEnchantmentDisplaysInstalled()) {
            return false;
        } else {
            return GobT.config.MAX_ENCHANTMENT_TEXT;
        }
    }
}
