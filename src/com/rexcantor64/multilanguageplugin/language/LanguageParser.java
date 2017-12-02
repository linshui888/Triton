package com.rexcantor64.multilanguageplugin.language;

import com.google.common.collect.Lists;
import com.rexcantor64.multilanguageplugin.SpigotMLP;
import com.rexcantor64.multilanguageplugin.components.api.ChatColor;
import com.rexcantor64.multilanguageplugin.utils.ComponentUtils;
import com.rexcantor64.multilanguageplugin.components.api.chat.BaseComponent;
import com.rexcantor64.multilanguageplugin.components.api.chat.TextComponent;
import com.rexcantor64.multilanguageplugin.components.chat.ComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageParser {

    private final Pattern pattern = Pattern
            .compile("\\[" + SpigotMLP.get().getConf().getSyntax() + "\\](.+?)\\[/" + SpigotMLP.get().getConf().getSyntax() + "\\](?!\\[)");
    private final Pattern patternArgs = Pattern.compile(
            "(.+?)\\[" + SpigotMLP.get().getConf().getSyntaxArgs() + "\\](.+?)\\[/" + SpigotMLP.get().getConf().getSyntaxArgs() + "\\]");
    private final Pattern patternArgs2 = Pattern.compile(
            "\\[" + SpigotMLP.get().getConf().getSyntaxArgs() + "\\](.+?)\\[/" + SpigotMLP.get().getConf().getSyntaxArgs() + "\\]");
    private final Pattern patternArg = Pattern
            .compile("\\[" + SpigotMLP.get().getConf().getSyntaxArg() + "\\](.+?)\\[/" + SpigotMLP.get().getConf().getSyntaxArg() + "\\]");
    private final int patternSize = SpigotMLP.get().getConf().getSyntax().length() + 2;
    private final int patternArgSize = SpigotMLP.get().getConf().getSyntaxArg().length() + 2;

    private String replaceLanguages(String input, Player p) {
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String a = matcher.group(1);
            Matcher matcherArgs = patternArgs.matcher(a);
            List<String> args = Lists.newArrayList();
            if (matcherArgs.find()) {
                String argsString = matcherArgs.group(2);
                Matcher matcherArg = patternArg.matcher(argsString);
                while (matcherArg.find())
                    args.add(replaceLanguages(matcherArg.group(1), p));
            }
            input = input
                    .replace("[" + SpigotMLP.get().getConf().getSyntax() + "]" + a + "[/" + SpigotMLP.get().getConf().getSyntax() + "]",
                            SpigotMLP.get().getLanguageManager().getText(p,
                                    org.bukkit.ChatColor.stripColor(a.replaceAll("\\[" + SpigotMLP.get().getConf().getSyntaxArgs()
                                            + "\\](.+?)\\[/" + SpigotMLP.get().getConf().getSyntaxArgs() + "\\]", "")),
                                    args.toArray()));
        }
        return input;
    }

    private List<Integer[]> findPlaceholdersIndex(String input) {
        Matcher matcher = pattern.matcher(input);
        List<Integer[]> indexes = new ArrayList<>();
        while (matcher.find())
            indexes.add(new Integer[]{matcher.start(), matcher.end()});
        return indexes;
    }

    private Integer[] findArgsIndex(String input) {
        Matcher matcher = patternArgs2.matcher(input);
        if (matcher.find())
            return new Integer[]{matcher.start(), matcher.end()};
        return null;
    }

    private List<Integer[]> findArgIndex(String input) {
        Matcher matcher = patternArg.matcher(input);
        List<Integer[]> indexes = new ArrayList<>();
        while (matcher.find())
            indexes.add(new Integer[]{matcher.start(), matcher.end()});
        return indexes;
    }

    public BaseComponent[] parseActionbar(Player p, BaseComponent[] text) {
        for (BaseComponent a : text)
            if (a instanceof TextComponent)
                ((TextComponent) a).setText(replaceLanguages(((TextComponent) a).getText(), p));
        return text;
    }

    public BaseComponent[] parseChat(Player p, BaseComponent[] text) {
        if (text == null) return null;
        int offset = 0;
        List<LanguageMessage> messages = LanguageMessage.fromBaseComponentArray(text);
        indexLoop:
        for (Integer[] i : findPlaceholdersIndex(BaseComponent.toPlainText(text))) {
            int index = 0;
            boolean foundStart = false;
            boolean foundEnd = false;
            StringBuilder cache = new StringBuilder();
            BaseComponent beforeCache = new TextComponent("");
            BaseComponent compCache = new TextComponent("");
            BaseComponent afterCache = new TextComponent("");
            for (LanguageMessage message : messages) {
                if (foundEnd) {
                    afterCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText())));
                    continue;
                }
                if (!foundStart) {
                    if (index + message.getText().length() <= i[0] + offset) {
                        beforeCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText())));
                        index += message.getText().length();
                        continue;
                    }
                    foundStart = true;
                    if (index + message.getText().length() >= i[1] + offset) {
                        cache.append(message.getText().substring(i[0] - index + offset, i[1] - index + offset));
                        compCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(i[0] - index + offset, i[1] - index + offset))));
                        beforeCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(0, i[0] - index + offset))));
                        afterCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(i[1] - index + offset))));
                        foundEnd = true;
                        continue;
                    }
                    cache.append(message.getText().substring(i[0] - index + offset));
                    compCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(i[0] - index + offset))));
                    beforeCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(0, i[0] - index + offset))));
                } else {
                    if (message.isTranslatableComponent()) continue indexLoop;
                    if (index + message.getText().length() < i[1] + offset) {
                        cache.append(message.getText());
                        compCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText())));
                        if (index + message.getText().length() + 1 == i[1] + offset) foundEnd = true;
                    } else {
                        cache.append(message.getText().substring(0, i[1] - index + offset));
                        compCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(0, i[1] - index + offset))));
                        afterCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(i[1] - index + offset))));
                        foundEnd = true;
                        continue;
                    }
                }
                index += message.getText().length();
            }
            BaseComponent result = new TextComponent("");
            result.addExtra(beforeCache);
            BaseComponent processed = processLanguageComponent(compCache, p);
            result.addExtra(processed);
            result.addExtra(afterCache);
            text = new BaseComponent[]{result};
            messages = LanguageMessage.fromBaseComponentArray(text);
            offset += (processed.toPlainText().length() - cache.length());
        }

        return text;
    }

    private BaseComponent processLanguageComponent(BaseComponent component, Player p) {
        String plainText = BaseComponent.toPlainText(component);
        Integer[] argsIndex = findArgsIndex(plainText);
        if (argsIndex == null) {
            BaseComponent comp = ComponentUtils.copyFormatting(component.getExtra().get(0), new TextComponent(""));
            comp.setExtra(Arrays.asList(TextComponent.fromLegacyText(replaceLanguages(plainText, p))));
            return comp;
        }
        String messageCode = plainText.substring(patternSize, argsIndex[0]);
        List<BaseComponent> arguments = new ArrayList<>();
        for (Integer[] i : findArgIndex(plainText)) {
            BaseComponent cache = new TextComponent("");
            i[0] = i[0] + patternArgSize;
            i[1] = i[1] - patternArgSize - 1;
            int index = 0;
            boolean foundStart = false;
            List<LanguageMessage> messages = LanguageMessage.fromBaseComponentArray(component);
            for (LanguageMessage message : messages) {
                if (!foundStart) {
                    if (index + message.getText().length() <= i[0]) {
                        index += message.getText().length();
                        continue;
                    }
                    foundStart = true;
                    if (index + message.getText().length() >= i[1]) {
                        cache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message.getText().substring(i[0] - index, i[1] - index))))));
                        break;
                    }
                    cache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message.getText().substring(i[0] - index))))));
                } else {
                    if (index + message.getText().length() < i[1]) {
                        cache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message.getText())))));
                    } else {
                        cache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message.getText().substring(0, i[1] - index))))));
                        break;
                    }
                }
                index += message.getText().length();
            }
            arguments.add(cache);
        }
        return replaceArguments(TextComponent.fromLegacyText(SpigotMLP.get().getLanguageManager().getText(p, messageCode)), arguments);
    }

    private BaseComponent replaceArguments(BaseComponent[] base, List<BaseComponent> args) {
        BaseComponent result = new TextComponent("");
        for (LanguageMessage message : LanguageMessage.fromBaseComponentArray(base)) {
            String msg = message.getText();
            TextComponent current = new TextComponent("");
            for (int i = 0; i < msg.length(); i++) {
                if (msg.charAt(i) == '%') {
                    i++;
                    if (Character.isDigit(msg.charAt(i)) && args.size() >= Character.getNumericValue(msg.charAt(i))) {
                        result.addExtra(ComponentUtils.copyFormatting(message.getComponent(), current));
                        current = new TextComponent("");
                        current.addExtra(args.get(Character.getNumericValue(msg.charAt(i) - 1)));
                        result.addExtra(ComponentUtils.copyFormatting(message.getComponent(), current));
                        current = new TextComponent("");
                        continue;
                    }
                    i--;
                }
                current.setText(current.getText() + msg.charAt(i));
            }
            result.addExtra(ComponentUtils.copyFormatting(message.getComponent(), current));
        }
        return result;
    }

}
