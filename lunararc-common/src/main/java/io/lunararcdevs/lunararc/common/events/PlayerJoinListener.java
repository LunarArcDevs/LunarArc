package io.lunararcdevs.lunararc.common.events;

import io.lunararcdevs.lunararc.common.server.LunarArcVersionFetcher;
import io.lunararcdevs.lunararc.common.server.LunarArcVersionInfo;
import io.lunararcdevs.lunararc.i18n.TranslationManager;
import com.mojang.authlib.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class PlayerJoinListener {
    private PlayerJoinListener() {
    }

    public static void checkAndNotify(Player player, GameProfile profile, net.minecraft.server.MinecraftServer server,
                                      Consumer<Runnable> serverExecutor) {
        if (!hasLevelFourOperatorAccess(profile, server)) return;

        CompletableFuture
                .supplyAsync(LunarArcVersionFetcher::fetchLatestRelease)
                .thenAccept(release -> release.ifPresent(latest -> {
                    if (!isCurrentVersion(latest.version())) {
                        serverExecutor.accept(() -> notifyPlayer(player, latest));
                    }
                }));
    }

    private static boolean hasLevelFourOperatorAccess(GameProfile profile, net.minecraft.server.MinecraftServer server) {
        try {
            Object opList = server.getPlayerList().getOps();
            Object entry = opList.getClass().getMethod("get", GameProfile.class).invoke(opList, profile);
            return entry != null && ((Number) entry.getClass().getMethod("getLevel").invoke(entry)).intValue() >= 4;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isCurrentVersion(String latestVersion) {
        return LunarArcVersionFetcher.isSameVersion(LunarArcVersionInfo.lunarArcVersion(), latestVersion);
    }

    private static void notifyPlayer(Player player, LunarArcVersionFetcher.Release release) {
        if (!player.isOnline()) return;
        player.sendMessage(Component.text(TranslationManager.get("ingame.update.available"),
                NamedTextColor.AQUA, TextDecoration.BOLD));
        player.sendMessage(Component.text(TranslationManager.get("ingame.update.latest"), NamedTextColor.WHITE)
                .append(Component.text(release.version(), NamedTextColor.GREEN)));
        player.sendMessage(Component.text(TranslationManager.get("ingame.update.download_link"),
                        NamedTextColor.GOLD, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(release.downloadUrl())));
    }
}
