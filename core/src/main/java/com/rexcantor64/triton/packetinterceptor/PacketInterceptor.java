package com.rexcantor64.triton.packetinterceptor;

import com.rexcantor64.triton.language.item.SignLocation;
import com.rexcantor64.triton.player.SpigotLanguagePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface PacketInterceptor {

    void refreshSigns(SpigotLanguagePlayer player);

    void refreshEntities(SpigotLanguagePlayer player);

    void refreshTabHeaderFooter(SpigotLanguagePlayer player, String header, String footer);

    void refreshBossbar(SpigotLanguagePlayer player, UUID uuid, String text);

    void resetSign(Player p, SignLocation location);

}
