package com.rexcantor64.triton.packetinterceptor;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.injector.GamePhase;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.*;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.rexcantor64.triton.SpigotMLP;
import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.api.wrappers.EntityType;
import com.rexcantor64.triton.components.api.chat.BaseComponent;
import com.rexcantor64.triton.components.api.chat.TextComponent;
import com.rexcantor64.triton.components.chat.ComponentSerializer;
import com.rexcantor64.triton.config.MainConfig;
import com.rexcantor64.triton.language.item.LanguageItem;
import com.rexcantor64.triton.language.item.LanguageSign;
import com.rexcantor64.triton.player.LanguagePlayer;
import com.rexcantor64.triton.player.SpigotLanguagePlayer;
import com.rexcantor64.triton.scoreboard.WrappedObjective;
import com.rexcantor64.triton.scoreboard.WrappedTeam;
import com.rexcantor64.triton.utils.NMSUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@SuppressWarnings("deprecation")
public class ProtocolLibListener implements PacketListener, PacketInterceptor {

    private final int mcVersion;
    private final int mcVersionR;

    private Triton main;

    private HashMap<World, HashMap<Integer, Entity>> entities = new HashMap<>();

    public ProtocolLibListener(SpigotMLP main) {
        this.main = main;
        String a = Bukkit.getServer().getClass().getPackage().getName();
        String[] s = a.substring(a.lastIndexOf('.') + 1).split("_");
        mcVersion = Integer.parseInt(s[1]);
        mcVersionR = Integer.parseInt(s[2].substring(1));
    }

    private void handleChat(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        boolean ab = isActionbar(packet.getPacket());
        if (ab && main.getConf().isActionbars()) {
            WrappedChatComponent msg = packet.getPacket().getChatComponents().readSafely(0);
            if (msg != null) {
                msg.setJson(ComponentSerializer.toString(main.getLanguageParser().parseSimpleBaseComponent(languagePlayer, ComponentSerializer.parse(msg.getJson()), main.getConf().getActionbarSyntax())));
                packet.getPacket().getChatComponents().writeSafely(0, msg);
                return;
            }
            packet.getPacket().getModifier().writeSafely(1, toLegacy(main.getLanguageParser().parseChat(languagePlayer, main.getConf().getChatSyntax(), fromLegacy((net.md_5.bungee.api.chat.BaseComponent[]) packet.getPacket().getModifier().readSafely(1)))));
        } else if (!ab && main.getConf().isChat()) {
            WrappedChatComponent msg = packet.getPacket().getChatComponents().readSafely(0);
            if (msg != null) {
                msg.setJson(ComponentSerializer.toString(main.getLanguageParser().parseChat(languagePlayer, main.getConf().getChatSyntax(), ComponentSerializer.parse(msg.getJson()))));
                packet.getPacket().getChatComponents().writeSafely(0, msg);
                return;
            }
            packet.getPacket().getModifier().writeSafely(1, toLegacy(main.getLanguageParser().parseChat(languagePlayer, main.getConf().getChatSyntax(), fromLegacy((net.md_5.bungee.api.chat.BaseComponent[]) packet.getPacket().getModifier().readSafely(1)))));
        }
    }

    private void handleTitle(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        WrappedChatComponent msg = packet.getPacket().getChatComponents().readSafely(0);
        if (msg == null) return;
        msg.setJson(ComponentSerializer.toString(main.getLanguageParser().parseTitle(languagePlayer, ComponentSerializer.parse(msg.getJson()), main.getConf().getTitleSyntax())));
        packet.getPacket().getChatComponents().writeSafely(0, msg);
    }

    private void handlePlayerListHeaderFooter(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        WrappedChatComponent header = packet.getPacket().getChatComponents().readSafely(0);
        String headerJson = header.getJson();
        header.setJson(ComponentSerializer.toString(main.getLanguageParser().parseSimpleBaseComponent(languagePlayer, ComponentSerializer.parse(header.getJson()), main.getConf().getTabSyntax())));
        packet.getPacket().getChatComponents().writeSafely(0, header);
        WrappedChatComponent footer = packet.getPacket().getChatComponents().readSafely(1);
        String footerJson = footer.getJson();
        footer.setJson(ComponentSerializer.toString(main.getLanguageParser().parseSimpleBaseComponent(languagePlayer, ComponentSerializer.parse(footer.getJson()), main.getConf().getTabSyntax())));
        packet.getPacket().getChatComponents().writeSafely(1, footer);
        languagePlayer.setLastTabHeader(headerJson);
        languagePlayer.setLastTabFooter(footerJson);
    }

    private void handleOpenWindow(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        WrappedChatComponent msg = packet.getPacket().getChatComponents().readSafely(0);
        msg.setJson(ComponentSerializer.toString(main.getLanguageParser().parseChat(languagePlayer, main.getConf().getGuiSyntax(), ComponentSerializer.parse(msg.getJson()))));
        packet.getPacket().getChatComponents().writeSafely(0, msg);
    }

    private void handleEntityMetadata(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        Entity e = packet.getPacket().getEntityModifier(packet).readSafely(0);
        if (e == null || (!main.getConf().isHologramsAll() && !main.getConf().getHolograms().contains(EntityType.fromBukkit(e.getType()))))
            return;
        if (e.getType() == org.bukkit.entity.EntityType.PLAYER) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getUniqueId().equals(e.getUniqueId()))
                    return;
        }
        addEntity(packet.getPlayer().getWorld(), packet.getPacket().getIntegers().readSafely(0), e);
        List<WrappedWatchableObject> dw = packet.getPacket().getWatchableCollectionModifier().readSafely(0);
        List<WrappedWatchableObject> dwn = new ArrayList<>();
        for (WrappedWatchableObject obj : dw)
            if (obj.getIndex() == 2)
                if (getMCVersion() < 9)
                    dwn.add(new WrappedWatchableObject(obj.getIndex(), main.getLanguageParser().replaceLanguages((String) obj.getValue(), languagePlayer, main.getConf().getHologramSyntax())));
                else if (getMCVersion() < 13)
                    dwn.add(new WrappedWatchableObject(obj.getWatcherObject(), main.getLanguageParser().replaceLanguages((String) obj.getValue(), languagePlayer, main.getConf().getHologramSyntax())));
                else {
                    Optional optional = (Optional) obj.getValue();
                    if (optional.isPresent()) {
                        dwn.add(new WrappedWatchableObject(obj.getWatcherObject(), Optional.of(WrappedChatComponent.fromJson(ComponentSerializer.toString(main.getLanguageParser().parseChat(languagePlayer, main.getConf().getHologramSyntax(), ComponentSerializer.parse(WrappedChatComponent.fromHandle(optional.get()).getJson())))).getHandle())));
                    } else dwn.add(obj);
                }
            else
                dwn.add(obj);
        packet.getPacket().getWatchableCollectionModifier().writeSafely(0, dwn);
    }

    private void handlePlayerInfo(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        EnumWrappers.PlayerInfoAction infoAction = packet.getPacket().getPlayerInfoAction().readSafely(0);
        if (infoAction != EnumWrappers.PlayerInfoAction.ADD_PLAYER && infoAction != EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME)
            return;
        List<PlayerInfoData> dataList = packet.getPacket().getPlayerInfoDataLists().readSafely(0);
        List<PlayerInfoData> dataListNew = new ArrayList<>();
        for (PlayerInfoData data : dataList) {
            WrappedGameProfile oldGP = data.getProfile();
            WrappedGameProfile newGP = oldGP.withName(translate(languagePlayer, oldGP.getName(), 16, main.getConf().getHologramSyntax()));
            newGP.getProperties().putAll(oldGP.getProperties());
            WrappedChatComponent msg = data.getDisplayName();
            if (msg != null)
                msg.setJson(ComponentSerializer.toString(main.getLanguageParser().parseSimpleBaseComponent(languagePlayer, ComponentSerializer.parse(msg.getJson()), main.getConf().getActionbarSyntax())));
            dataListNew.add(new PlayerInfoData(newGP, data.getLatency(), data.getGameMode(), msg));
        }
        packet.getPacket().getPlayerInfoDataLists().writeSafely(0, dataListNew);
    }

    private void handleScoreboardObjective(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        int mode = packet.getPacket().getIntegers().readSafely(0);
        String name = packet.getPacket().getStrings().readSafely(0);
        if (!main.getConf().isScoreboardsAdvanced()) {
            if (mode == 1) return;
            if (getMCVersion() < 13)
                packet.getPacket().getStrings().writeSafely(1, translate(languagePlayer, packet.getPacket().getStrings().readSafely(1), 32, main.getConf().getScoreboardSyntax()));
            else
                packet.getPacket().getChatComponents().writeSafely(0, WrappedChatComponent.fromJson(ComponentSerializer.toString(main.getLanguageParser().parseChat(languagePlayer, main.getConf().getScoreboardSyntax(), ComponentSerializer.parse(packet.getPacket().getChatComponents().readSafely(0).getJson())))));
        }
        if (mode == 1) {
            languagePlayer.getScoreboard().removeObjective(name);
            return;
        }
        WrappedObjective objective = null;
        if (mode == 0) {
            objective = languagePlayer.getScoreboard().createObjective(name);
        } else if (mode == 2) {
            objective = languagePlayer.getScoreboard().getObjective(name);
        }
        if (objective == null)
            return;
        if (getMCVersion() < 13) {
            objective.setTitle(packet.getPacket().getStrings().readSafely(1));
            languagePlayer.getScoreboard().getBridge().updateObjectiveTitle(translate(languagePlayer, objective.getTitle(), 32, main.getConf().getScoreboardSyntax()));
        } else {
            objective.setTitleComp(ComponentSerializer.parse(packet.getPacket().getChatComponents().readSafely(0).getJson()));
            languagePlayer.getScoreboard().getBridge().updateObjectiveTitle(ComponentSerializer.toString(main.getLanguageParser().parseChat(languagePlayer, main.getConf().getScoreboardSyntax(), objective.getTitleComp())));
        }
    }

    private void handleScoreboardScore(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        StructureModifier<String> strings = packet.getPacket().getStrings();
        if (!main.getConf().isScoreboardsAdvanced()) {
            strings.writeSafely(0, translate(languagePlayer, strings.readSafely(0), 40, main.getConf().getScoreboardSyntax()));
            return;
        }
        WrappedObjective objective = languagePlayer.getScoreboard().getObjective(strings.readSafely(1));
        if (objective == null) return;
        objective.setScore(strings.readSafely(0), packet.getPacket().getScoreboardActions().readSafely(0) == EnumWrappers.ScoreboardAction.CHANGE ? packet.getPacket().getIntegers().readSafely(0) : null);
        languagePlayer.getScoreboard().rerender(false);
    }

    @SuppressWarnings("unchecked")
    private void handleScoreboardTeam(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        StructureModifier<String> strings = packet.getPacket().getStrings();
        StructureModifier<WrappedChatComponent> components = packet.getPacket().getChatComponents();
        if (!main.getConf().isScoreboardsAdvanced()) {
            if (getMCVersion() < 13) {
                strings.writeSafely(2, translate(languagePlayer, strings.readSafely(2), 16, main.getConf().getScoreboardSyntax()));
                strings.writeSafely(3, translate(languagePlayer, strings.readSafely(3), 16, main.getConf().getScoreboardSyntax()));
            } else {
                components.writeSafely(1, WrappedChatComponent.fromJson(ComponentSerializer.toString(main.getLanguageParser().parseChat(languagePlayer, main.getConf().getScoreboardSyntax(), ComponentSerializer.parse(components.readSafely(1).getJson())))));
                components.writeSafely(2, WrappedChatComponent.fromJson(ComponentSerializer.toString(main.getLanguageParser().parseChat(languagePlayer, main.getConf().getScoreboardSyntax(), ComponentSerializer.parse(components.readSafely(2).getJson())))));
            }
            return;
        }
        StructureModifier<Integer> integers = packet.getPacket().getIntegers();
        String name = strings.readSafely(0);
        int mode = integers.readSafely(getMCVersion() < 13 ? 1 : 0);
        if (mode == 1) {
            languagePlayer.getScoreboard().removeTeam(name);
            return;
        }
        WrappedTeam team;
        if (mode == 0) team = languagePlayer.getScoreboard().createTeam(name);
        else team = languagePlayer.getScoreboard().getTeam(name);
        if (team == null)
            return;
        if (mode == 0 || mode == 2) {
            if (getMCVersion() < 13) {
                team.setPrefix(strings.readSafely(2));
                team.setSuffix(strings.readSafely(3));
            } else {
                team.setPrefixComp(ComponentSerializer.parse(components.readSafely(1).getJson()));
                team.setSuffixComp(ComponentSerializer.parse(components.readSafely(2).getJson()));
            }
        }
        if (mode == 0 || mode == 3)
            team.addEntry((Collection<String>) packet.getPacket().getSpecificModifier(Collection.class).readSafely(0));
        if (mode == 4)
            team.removeEntry((Collection<String>) packet.getPacket().getSpecificModifier(Collection.class).readSafely(0));
        languagePlayer.getScoreboard().rerender(false);
    }

    private void handleScoreboardDisplayObjective(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (!main.getConf().isScoreboardsAdvanced()) return;
        int position = packet.getPacket().getIntegers().readSafely(0);
        String name = packet.getPacket().getStrings().readSafely(0);
        if (position != 1 && position < 3) return;
        WrappedObjective obj = languagePlayer.getScoreboard().getObjective(name);
        if (obj == null) return;
        packet.setCancelled(true);
        languagePlayer.getScoreboard().setSidebarObjective(obj);
        languagePlayer.getScoreboard().rerender(false);
    }

    private void handleKickDisconnect(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        WrappedChatComponent msg = packet.getPacket().getChatComponents().readSafely(0);
        msg.setJson(ComponentSerializer.toString(main.getLanguageParser().parseSimpleBaseComponent(languagePlayer, ComponentSerializer.parse(msg.getJson()), main.getConf().getKickSyntax())));
        packet.getPacket().getChatComponents().writeSafely(0, msg);
    }

    private void handleUpdateSign(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        BlockPosition pos = packet.getPacket().getBlockPositionModifier().readSafely(0);
        String[] lines = main.getLanguageManager().getSign(languagePlayer, new LanguageSign.SignLocation(packet.getPlayer().getWorld().getName(), pos.getX(), pos.getY(), pos.getZ()));
        if (lines == null) return;
        WrappedChatComponent[] comps = new WrappedChatComponent[4];
        for (int i = 0; i < 4; i++)
            comps[i] = WrappedChatComponent.fromJson(ComponentSerializer.toString(TextComponent.fromLegacyText(lines[i])));
        packet.getPacket().getModifier().withType(MinecraftReflection.getIChatBaseComponentArrayClass(), BukkitConverters.getArrayConverter(MinecraftReflection.getIChatBaseComponentClass(), BukkitConverters.getWrappedChatComponentConverter())).writeSafely(0, Arrays.asList(comps));
    }

    private void handleTileEntityData(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (packet.getPacket().getIntegers().readSafely(0) == 9) {
            NbtCompound nbt = NbtFactory.asCompound(packet.getPacket().getNbtModifier().readSafely(0));
            LanguageSign.SignLocation l = new LanguageSign.SignLocation(packet.getPlayer().getWorld().getName(), nbt.getInteger("x"), nbt.getInteger("y"), nbt.getInteger("z"));
            String[] sign = main.getLanguageManager().getSign(languagePlayer, l);
            if (sign != null)
                for (int i = 0; i < 4; i++)
                    nbt.put("Text" + (i + 1), ComponentSerializer.toString(TextComponent.fromLegacyText(sign[i])));
        }
    }

    private void handleMapChunk(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        List<NbtBase<?>> entities = packet.getPacket().getListNbtModifier().readSafely(0);
        for (NbtBase<?> entity : entities) {
            NbtCompound nbt = NbtFactory.asCompound(entity);
            if (nbt.getString("id").equals(getMCVersion() <= 10 ? "Sign" : "minecraft:sign")) {
                LanguageSign.SignLocation l = new LanguageSign.SignLocation(packet.getPlayer().getWorld().getName(), nbt.getInteger("x"), nbt.getInteger("y"), nbt.getInteger("z"));
                String[] sign = main.getLanguageManager().getSign(languagePlayer, l);
                if (sign != null)
                    for (int i = 0; i < 4; i++)
                        nbt.put("Text" + (i + 1), ComponentSerializer.toString(TextComponent.fromLegacyText(sign[i])));
            }
        }
    }

    private void handleWindowItems(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (NMSUtils.getNMSClass("ContainerPlayer") == NMSUtils.getDeclaredField(NMSUtils.getHandle(packet.getPlayer()), "activeContainer").getClass() && !main.getConf().isInventoryItems())
            return;

        List<ItemStack> items = getMCVersion() <= 10 ? Arrays.asList(packet.getPacket().getItemArrayModifier().readSafely(0)) : packet.getPacket().getItemListModifier().readSafely(0);
        for (ItemStack item : items) {
            if (item == null) continue;
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName())
                    meta.setDisplayName(main.getLanguageParser().replaceLanguages(meta.getDisplayName(), languagePlayer, main.getConf().getItemsSyntax()));
                if (meta.hasLore()) {
                    List<String> newLore = new ArrayList<>();
                    for (String lore : meta.getLore())
                        newLore.add(main.getLanguageParser().replaceLanguages(lore, languagePlayer, main.getConf().getItemsSyntax()));
                    meta.setLore(newLore);
                }
                item.setItemMeta(meta);
            }
        }
        if (getMCVersion() <= 10)
            packet.getPacket().getItemArrayModifier().writeSafely(0, items.toArray(new ItemStack[0]));
        else
            packet.getPacket().getItemListModifier().writeSafely(0, items);
    }

    private void handleSetSlot(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        if (NMSUtils.getNMSClass("ContainerPlayer") == NMSUtils.getDeclaredField(NMSUtils.getHandle(packet.getPlayer()), "activeContainer").getClass() && !main.getConf().isInventoryItems())
            return;

        ItemStack item = packet.getPacket().getItemModifier().readSafely(0);
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName())
                meta.setDisplayName(main.getLanguageParser().replaceLanguages(meta.getDisplayName(), languagePlayer, main.getConf().getItemsSyntax()));
            if (meta.hasLore()) {
                List<String> newLore = new ArrayList<>();
                for (String lore : meta.getLore())
                    newLore.add(main.getLanguageParser().replaceLanguages(lore, languagePlayer, main.getConf().getItemsSyntax()));
                meta.setLore(newLore);
            }
            item.setItemMeta(meta);
        }
        packet.getPacket().getItemModifier().writeSafely(0, item);
    }

    private void handleBoss(PacketEvent packet, SpigotLanguagePlayer languagePlayer) {
        UUID uuid = packet.getPacket().getUUIDs().readSafely(0);
        Action action = packet.getPacket().getEnumModifier(Action.class, 1).readSafely(0);
        if (action == Action.REMOVE) {
            languagePlayer.removeBossbar(uuid);
            return;
        }
        if (action != Action.ADD && action != Action.UPDATE_NAME) return;
        WrappedChatComponent bossbar = packet.getPacket().getChatComponents().readSafely(0);
        languagePlayer.setBossbar(uuid, bossbar.getJson());
        bossbar.setJson(ComponentSerializer.toString(main.getLanguageParser().parseTitle(languagePlayer, ComponentSerializer.parse(bossbar.getJson()), main.getConf().getBossbarSyntax())));
        packet.getPacket().getChatComponents().writeSafely(0, bossbar);
    }

    @Override
    public void onPacketSending(PacketEvent packet) {
        if (!packet.isServerPacket()) return;
        SpigotLanguagePlayer languagePlayer;
        try {
            languagePlayer = (SpigotLanguagePlayer) Triton.get().getPlayerManager().get(packet.getPlayer().getUniqueId());
        } catch (Exception ignore) {
            Triton.get().logDebugWarning("Failed to translate packet because UUID of the player is unknown (because the player hasn't joined yet).");
            return;
        }
        if (languagePlayer == null) {
            Triton.get().logDebugWarning("Language Player is null on packet sending");
            return;
        }
        if (packet.getPacketType() == PacketType.Play.Server.CHAT) {
            handleChat(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.TITLE && main.getConf().isTitles()) {
            handleTitle(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER && main.getConf().isTab()) {
            handlePlayerListHeaderFooter(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.OPEN_WINDOW && main.getConf().isGuis()) {
            handleOpenWindow(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.ENTITY_METADATA && (main.getConf().isHologramsAll() || main.getConf().getHolograms().size() != 0)) {
            handleEntityMetadata(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.PLAYER_INFO && (main.getConf().isHologramsAll() || main.getConf().getHolograms().contains(EntityType.PLAYER))) {
            handlePlayerInfo(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.SCOREBOARD_OBJECTIVE && main.getConf().isScoreboards()) {
            handleScoreboardObjective(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.SCOREBOARD_SCORE && main.getConf().isScoreboards()) {
            handleScoreboardScore(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.SCOREBOARD_TEAM && main.getConf().isScoreboards()) {
            handleScoreboardTeam(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE && main.getConf().isScoreboards()) {
            handleScoreboardDisplayObjective(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.KICK_DISCONNECT && main.getConf().isKick()) {
            handleKickDisconnect(packet, languagePlayer);
        } else if (existsSignUpdatePacket() && packet.getPacketType() == PacketType.Play.Server.UPDATE_SIGN && main.getConf().isSigns()) {
            handleUpdateSign(packet, languagePlayer);
        } else if (!existsSignUpdatePacket() && packet.getPacketType() == PacketType.Play.Server.TILE_ENTITY_DATA && main.getConf().isSigns()) {
            handleTileEntityData(packet, languagePlayer);
        } else if (!existsSignUpdatePacket() && packet.getPacketType() == PacketType.Play.Server.MAP_CHUNK && main.getConf().isSigns()) {
            handleMapChunk(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS && main.getConf().isItems()) {
            handleWindowItems(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.SET_SLOT && main.getConf().isItems()) {
            handleSetSlot(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Play.Server.BOSS && main.getConf().isBossbars()) {
            handleBoss(packet, languagePlayer);
        } else if (packet.getPacketType() == PacketType.Login.Server.DISCONNECT && main.getConf().isKick()) {
            handleKickDisconnect(packet, languagePlayer);
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent packetEvent) {

    }

    @Override
    public void refreshSigns(SpigotLanguagePlayer player) {
        out:
        for (LanguageItem item : main.getLanguageManager().getAllItems(LanguageItem.LanguageItemType.SIGN)) {
            LanguageSign sign = (LanguageSign) item;
            for (LanguageSign.SignLocation location : sign.getLocations())
                if (player.toBukkit().getWorld().getName().equals(location.getWorld())) {
                    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.UPDATE_SIGN);
                    String[] lines = sign.getLines(player.getLang().getName());
                    if (lines == null) lines = sign.getLines(main.getLanguageManager().getMainLanguage().getName());
                    if (lines == null) continue out;
                    packet.getBlockPositionModifier().writeSafely(0, new BlockPosition(location.getX(), location.getY(), location.getZ()));
                    if (existsSignUpdatePacket()) {
                        WrappedChatComponent[] comps = new WrappedChatComponent[4];
                        for (int i = 0; i < 4; i++)
                            comps[i] = WrappedChatComponent.fromJson(ComponentSerializer.toString(TextComponent.fromLegacyText(lines[i])));
                        packet.getModifier().withType(MinecraftReflection.getIChatBaseComponentArrayClass(), BukkitConverters.getArrayConverter(MinecraftReflection.getIChatBaseComponentClass(), BukkitConverters.getWrappedChatComponentConverter())).writeSafely(0, Arrays.asList(comps));
                    } else {
                        packet.getIntegers().writeSafely(0, 9);
                        NbtCompound compound = NbtFactory.ofCompound(null);
                        compound.put("x", location.getX());
                        compound.put("y", location.getY());
                        compound.put("z", location.getZ());
                        compound.put("id", getMCVersion() <= 10 ? "Sign" : "minecraft:sign");
                        for (int i = 0; i < 4; i++)
                            compound.put("Text" + (i + 1), ComponentSerializer.toString(TextComponent.fromLegacyText(lines[i])));
                        packet.getNbtModifier().writeSafely(0, compound);
                    }
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(player.toBukkit(), packet, false);
                    } catch (InvocationTargetException e) {
                        main.logError("Failed to send sign update packet: %1", e.getMessage());
                    }
                }
        }
    }

    @Override
    public void refreshEntities(SpigotLanguagePlayer player) {
        if (entities.containsKey(player.toBukkit().getWorld()))
            entityLoop:for (Map.Entry<Integer, Entity> entry : entities.get(player.toBukkit().getWorld()).entrySet()) {
                if (entry.getValue().getType() == org.bukkit.entity.EntityType.PLAYER) {
                    Player p = (Player) entry.getValue();
                    for (Player op : Bukkit.getOnlinePlayers())
                        if (op.getUniqueId().equals(p.getUniqueId())) continue entityLoop;
                    List<PlayerInfoData> dataList = new ArrayList<>();
                    dataList.add(new PlayerInfoData(WrappedGameProfile.fromPlayer(p), 50, EnumWrappers.NativeGameMode.fromBukkit(p.getGameMode()), WrappedChatComponent.fromText(p.getPlayerListName())));
                    PacketContainer packetRemove = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);
                    packetRemove.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
                    packetRemove.getPlayerInfoDataLists().writeSafely(0, dataList);

                    PacketContainer packetAdd = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);
                    packetRemove.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
                    packetRemove.getPlayerInfoDataLists().writeSafely(0, dataList);

                    PacketContainer packetDestroy = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                    packetDestroy.getIntegerArrays().writeSafely(0, new int[]{p.getEntityId()});

                    PacketContainer packetSpawn = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
                    packetSpawn.getIntegers().writeSafely(0, p.getEntityId());
                    packetSpawn.getUUIDs().writeSafely(0, p.getUniqueId());
                    packetSpawn.getDoubles().writeSafely(0, p.getLocation().getX()).writeSafely(1, p.getLocation().getY()).writeSafely(2, p.getLocation().getZ());
                    packetSpawn.getBytes().writeSafely(0, (byte) (int) (p.getLocation().getYaw() * 256.0F / 360.0F)).writeSafely(1, (byte) (int) (p.getLocation().getPitch() * 256.0F / 360.0F));
                    packetSpawn.getDataWatcherModifier().writeSafely(0, WrappedDataWatcher.getEntityWatcher(p));

                    PacketContainer packetRotation = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                    packetRotation.getIntegers().writeSafely(0, p.getEntityId());
                    packetRotation.getBytes().writeSafely(0, (byte) p.getLocation().getYaw());

                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(player.toBukkit(), packetRemove, true);
                        ProtocolLibrary.getProtocolManager().sendServerPacket(player.toBukkit(), packetAdd, false);
                        ProtocolLibrary.getProtocolManager().sendServerPacket(player.toBukkit(), packetDestroy, true);
                        ProtocolLibrary.getProtocolManager().sendServerPacket(player.toBukkit(), packetSpawn, true);
                        ProtocolLibrary.getProtocolManager().sendServerPacket(player.toBukkit(), packetRotation, true);
                    } catch (InvocationTargetException e) {
                        main.logError("Failed to send player entity update packet: %1", e.getMessage());
                    }
                    continue;
                }
                if (entry.getValue().getCustomName() == null) continue;
                PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
                packet.getIntegers().writeSafely(0, entry.getKey());
                WrappedDataWatcher dw = WrappedDataWatcher.getEntityWatcher(entry.getValue());
                packet.getWatchableCollectionModifier().writeSafely(0, dw.getWatchableObjects());
                try {
                    ProtocolLibrary.getProtocolManager().sendServerPacket(player.toBukkit(), packet, true);
                } catch (InvocationTargetException e) {
                    main.logError("Failed to send entity update packet: %1", e.getMessage());
                }
            }
    }

    @Override
    public void refreshTabHeaderFooter(SpigotLanguagePlayer player, String header, String footer) {
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER);
        packet.getChatComponents().writeSafely(0, WrappedChatComponent.fromJson(header));
        packet.getChatComponents().writeSafely(1, WrappedChatComponent.fromJson(footer));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player.toBukkit(), packet, true);
        } catch (InvocationTargetException e) {
            main.logError("Failed to send tab update packet: %1", e.getMessage());
        }
    }

    @Override
    public void refreshBossbar(SpigotLanguagePlayer player, UUID uuid, String json) {
        if (getMCVersion() <= 8) return;
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BOSS);
        packet.getUUIDs().writeSafely(0, uuid);
        packet.getEnumModifier(Action.class, 1).writeSafely(0, Action.UPDATE_NAME);
        packet.getChatComponents().writeSafely(0, WrappedChatComponent.fromJson(json));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player.toBukkit(), packet, true);
        } catch (InvocationTargetException e) {
            main.logError("Failed to send bossbar update packet: %1", e.getMessage());
        }
    }

    @Override
    public void refreshScoreboard(SpigotLanguagePlayer player) {
        player.getScoreboard().rerender(true);
    }

    @Override
    public void resetSign(Player p, LanguageSign.SignLocation location) {
        World world = Bukkit.getWorld(location.getWorld());
        if (world == null) return;
        Block block = world.getBlockAt(location.getX(), location.getY(), location.getZ());
        if (block.getType() != Material.SIGN && block.getType() != Material.SIGN_POST && block.getType() != Material.WALL_SIGN)
            return;
        Sign sign = (Sign) block.getState();
        String[] lines = sign.getLines();
        if (existsSignUpdatePacket()) {
            PacketContainer container = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.UPDATE_SIGN, true);
            container.getBlockPositionModifier().writeSafely(0, new BlockPosition(location.getX(), location.getY(), location.getZ()));
            container.getChatComponentArrays().writeSafely(0, new WrappedChatComponent[]{WrappedChatComponent.fromText(lines[0]), WrappedChatComponent.fromText(lines[1]), WrappedChatComponent.fromText(lines[2]), WrappedChatComponent.fromText(lines[3])});
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(p, container, false);
            } catch (Exception e) {
                main.logError("Failed refresh sign: %1", e.getMessage());
            }
        } else {
            PacketContainer container = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.TILE_ENTITY_DATA, true);
            container.getBlockPositionModifier().writeSafely(0, new BlockPosition(location.getX(), location.getY(), location.getZ()));
            container.getIntegers().writeSafely(0, 9); // Action (9): Update sign text
            NbtCompound nbt = NbtFactory.asCompound(container.getNbtModifier().readSafely(0));
            for (int i = 0; i < 4; i++)
                nbt.put("Text" + (i + 1), ComponentSerializer.toString(TextComponent.fromLegacyText(lines[i])));
            nbt.put("name", "null").put("x", block.getX()).put("y", block.getY()).put("z", block.getZ()).put("id", getMCVersion() <= 10 ? "Sign" : "minecraft:sign");
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(p, container, false);
            } catch (Exception e) {
                main.logError("Failed refresh sign: %1", e.getMessage());
            }
        }
    }

    private void addEntity(World world, int id, Entity entity) {
        if (!entities.containsKey(world))
            entities.put(world, new HashMap<>());
        entities.get(world).put(id, entity);
    }

    @Override
    public ListeningWhitelist getSendingWhitelist() {
        Collection<PacketType> types = new ArrayList<>();
        types.add(PacketType.Play.Server.CHAT);
        types.add(PacketType.Play.Server.TITLE);
        types.add(PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER);
        types.add(PacketType.Play.Server.OPEN_WINDOW);
        types.add(PacketType.Play.Server.ENTITY_METADATA);
        types.add(PacketType.Play.Server.PLAYER_INFO);
        types.add(PacketType.Play.Server.SCOREBOARD_OBJECTIVE);
        types.add(PacketType.Play.Server.SCOREBOARD_SCORE);
        types.add(PacketType.Play.Server.SCOREBOARD_TEAM);
        types.add(PacketType.Play.Server.SCOREBOARD_DISPLAY_OBJECTIVE);
        types.add(PacketType.Play.Server.KICK_DISCONNECT);
        if (existsSignUpdatePacket()) types.add(PacketType.Play.Server.UPDATE_SIGN);
        else {
            types.add(PacketType.Play.Server.MAP_CHUNK);
            types.add(PacketType.Play.Server.TILE_ENTITY_DATA);
        }
        types.add(PacketType.Play.Server.WINDOW_ITEMS);
        types.add(PacketType.Play.Server.SET_SLOT);
        types.add(PacketType.Login.Server.DISCONNECT);
        if (getMCVersion() >= 9) types.add(PacketType.Play.Server.BOSS);
        return ListeningWhitelist.newBuilder().gamePhase(GamePhase.PLAYING).types(types).highest().build();
    }

    @Override
    public ListeningWhitelist getReceivingWhitelist() {
        return ListeningWhitelist.EMPTY_WHITELIST;
    }

    @Override
    public Plugin getPlugin() {
        return main.getLoader().asSpigot();
    }

    private BaseComponent[] fromLegacy(net.md_5.bungee.api.chat.BaseComponent... components) {
        return ComponentSerializer.parse(net.md_5.bungee.chat.ComponentSerializer.toString(components));
    }

    private net.md_5.bungee.api.chat.BaseComponent[] toLegacy(BaseComponent... components) {
        return net.md_5.bungee.chat.ComponentSerializer.parse(ComponentSerializer.toString(components));
    }

    private boolean isActionbar(PacketContainer container) {
        if (getMCVersion() >= 12)
            return container.getChatTypes().readSafely(0) == EnumWrappers.ChatType.GAME_INFO;
        else
            return container.getBytes().readSafely(0) == 2;
    }

    private int getMCVersion() {
        return mcVersion;
    }

    private int getMCVersionR() {
        return mcVersionR;
    }

    private boolean existsSignUpdatePacket() {
        return getMCVersion() == 8 || (getMCVersion() == 9 && getMCVersionR() == 1);
    }

    private String translate(LanguagePlayer lp, String s, int max, MainConfig.FeatureSyntax syntax) {
        String r = main.getLanguageParser().replaceLanguages(s, lp, syntax);
        if (r.length() > max) return r.substring(0, max);
        return r;
    }

    public enum Action {
        ADD, REMOVE, UPDATE_PCT, UPDATE_NAME, UPDATE_STYLE, UPDATE_PROPERTIES
    }

    public enum EnumScoreboardHealthDisplay {
        HEARTS, INTEGER
    }

}
