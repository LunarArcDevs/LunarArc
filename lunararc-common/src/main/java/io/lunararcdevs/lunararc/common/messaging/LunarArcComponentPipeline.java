package io.lunararcdevs.lunararc.common.messaging;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.chat.BaseComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;


public final class LunarArcComponentPipeline {
    private LunarArcComponentPipeline() {}

    private static volatile Class<?> paperAdventureBridge;
    private static volatile boolean paperAdventureBridgeMissing;

    /** The io.papermc.paper.adventure.PaperAdventure bridge class, resolved once and cached -
     *  {@link #toAdventure} and {@link #fromAdventure} both need it and neither can assume it exists. */
    private static Class<?> paperAdventureBridge() throws ClassNotFoundException {
        if (paperAdventureBridgeMissing) throw new ClassNotFoundException("io.papermc.paper.adventure.PaperAdventure");
        Class<?> bridge = paperAdventureBridge;
        if (bridge != null) return bridge;
        try {
            bridge = Class.forName("io.papermc.paper.adventure.PaperAdventure", false,
                    LunarArcComponentPipeline.class.getClassLoader());
            paperAdventureBridge = bridge;
            return bridge;
        } catch (ClassNotFoundException notFound) {
            paperAdventureBridgeMissing = true;
            throw notFound;
        }
    }

    public static net.minecraft.network.chat.Component fromLegacy(String message) {
        if (message == null || message.isEmpty()) {
            return net.minecraft.network.chat.Component.empty();
        }


        try {
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer serializer =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                            .character('§')
                            .hexColors()
                            .useUnusualXRepeatedCharacterHexFormat()
                            .build();
            return fromAdventure(linkifyUrls(serializer.deserialize(message)));
        } catch (Throwable ignored) {
            // Do not call CraftChatMessage#fromStringOrNull here: that method routes
            // back through this pipeline and would recurse if Adventure conversion
            // itself failed. Preserve the original text as the terminal fallback.
            return net.minecraft.network.chat.Component.literal(message);
        }
    }


    public static Component legacyToAdventure(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        try {
            Component decoded = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build()
                    .deserialize(message);
            return linkifyUrls(decoded);
        } catch (Throwable ignored) {
            return linkifyUrls(Component.text(message));
        }
    }

    // CraftBukkit 1.21.1 URL recognition. Keep this aligned with the server's
    // normal legacy-message linkification instead of imposing a LunarArc URL policy.
    private static final java.util.regex.Pattern URL_PATTERN = java.util.regex.Pattern.compile(
            "((?:(?:https?):\\/\\/)?(?:[-\\w_\\.]{2,}\\.[a-z]{2,4}.*?(?=[\\.\\?!,;:]?(?:[§ \n]|$))))",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Restores CraftBukkit-style clickable links in legacy text without imposing a
     * LunarArc/domain allow-list. Explicit plugin click events are preserved exactly;
     * plain URLs receive OPEN_URL and the Minecraft client remains authoritative for
     * whether that target can actually be opened.
     */
    private static Component linkifyUrls(Component component) {
        if (component == null) return Component.empty();
        try {
            return component.replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                    .match(URL_PATTERN)
                    .replacement((match, builder) -> {
                        // Never replace a click action supplied by the plugin/component itself.
                        if (builder.build().clickEvent() != null) return builder;

                        String shown = match.group();
                        String target = shown.regionMatches(true, 0, "http://", 0, 7)
                                || shown.regionMatches(true, 0, "https://", 0, 8)
                                ? shown : "http://" + shown;
                        return builder.clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(target));
                    })
                    .build());
        } catch (Throwable ignored) {
            // A malformed piece of plugin text must not lose its original component/style.
            return component;
        }
    }


    public static String toLegacy(Component component) {
        if (component == null) return "";
        try {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build()
                    .serialize(component);
        } catch (Throwable ignored) {
            try {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
            } catch (Throwable ignoredAgain) {
                return String.valueOf(component);
            }
        }
    }


    public static Component toAdventure(net.minecraft.network.chat.Component component) {
        if (component == null) return Component.empty();
        try {
            Class<?> bridge = paperAdventureBridge();
            java.lang.reflect.Method method = bridge.getMethod("asAdventure", net.minecraft.network.chat.Component.class);
            Object converted = method.invoke(null, component);
            if (converted instanceof Component adventure) return adventure;
        } catch (Throwable ignored) {
        }
        try {
            String json = CraftChatMessage.toJSON(component);
            return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(json);
        } catch (Throwable ignored) {
            return Component.text(component.getString());
        }
    }

    public static Component bungeeToAdventure(BaseComponent... components) {
        return toAdventure(fromBungee(components));
    }

    public static net.minecraft.network.chat.Component fromAdventure(Component component) {
        if (component == null) {
            return net.minecraft.network.chat.Component.empty();
        }


        try {
            Class<?> bridge = paperAdventureBridge();
            java.lang.reflect.Method method = bridge.getMethod("asVanilla", Component.class);
            Object converted = method.invoke(null, component);
            if (converted instanceof net.minecraft.network.chat.Component nms) return nms;
        } catch (Throwable ignored) {
        }


        try {
            String json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(component);
            net.minecraft.network.chat.MutableComponent nms = CraftChatMessage.fromJSON(json);
            if (nms != null) return nms;
        } catch (Throwable ignored) {
        }


        try {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
            return net.minecraft.network.chat.Component.literal(plain);
        } catch (Throwable ignored) {
            return net.minecraft.network.chat.Component.literal(String.valueOf(component));
        }
    }

    public static net.minecraft.network.chat.Component fromBungee(BaseComponent... components) {
        if (components == null || components.length == 0) {
            return net.minecraft.network.chat.Component.empty();
        }
        try {
            String json = net.md_5.bungee.chat.ComponentSerializer.toString(components);
            net.minecraft.network.chat.MutableComponent nms = CraftChatMessage.fromJSON(json);
            if (nms != null) return nms;
        } catch (Throwable ignored) {
        }
        try {
            return fromLegacy(BaseComponent.toLegacyText(components));
        } catch (Throwable ignored) {
            return net.minecraft.network.chat.Component.literal("");
        }
    }

    public static void sendSystem(ServerPlayer player, String legacy) {
        sendSystem(player, fromLegacy(legacy));
    }

    public static void sendSystem(ServerPlayer player, Component component) {
        sendSystem(player, fromAdventure(linkifyUrls(component)));
    }

    public static void sendSystem(ServerPlayer player, BaseComponent... components) {
        sendSystem(player, fromBungee(components));
    }

    public static void sendSystem(ServerPlayer player, net.minecraft.network.chat.Component component) {
        if (player == null || player.connection == null || component == null) return;
        io.lunararcdevs.lunararc.common.server.LunarArcCommandLogger.capture(player.getUUID(), component.getString());
        player.connection.send(new ClientboundSystemChatPacket(component, false));
    }

    public static void sendActionBar(ServerPlayer player, String legacy) {
        sendActionBar(player, fromLegacy(legacy));
    }

    public static void sendActionBar(ServerPlayer player, Component component) {
        sendActionBar(player, fromAdventure(component));
    }

    public static void sendActionBar(ServerPlayer player, BaseComponent... components) {
        sendActionBar(player, fromBungee(components));
    }

    public static void sendActionBar(ServerPlayer player, net.minecraft.network.chat.Component component) {
        if (player == null || player.connection == null || component == null) return;
        player.connection.send(new ClientboundSetActionBarTextPacket(component));
    }
}
