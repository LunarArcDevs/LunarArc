package io.lunararcdevs.lunararc.common.compat;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;

public final class LunarArcPlayerInfoUpdatePacketCompat {
    private static final Unsafe UNSAFE = findUnsafe();
    private static final Field ACTIONS_FIELD = findField("actions");
    private static final Field ENTRIES_FIELD = findField("entries");

    private LunarArcPlayerInfoUpdatePacketCompat() {
    }

    public static ClientboundPlayerInfoUpdatePacket create(
            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions,
            ClientboundPlayerInfoUpdatePacket.Entry entry) {
        try {
            ClientboundPlayerInfoUpdatePacket packet = (ClientboundPlayerInfoUpdatePacket)
                    UNSAFE.allocateInstance(ClientboundPlayerInfoUpdatePacket.class);
            ACTIONS_FIELD.set(packet, actions);
            ENTRIES_FIELD.set(packet, List.of(entry));
            return packet;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not construct single-entry ClientboundPlayerInfoUpdatePacket", e);
        }
    }

    private static Unsafe findUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Field findField(String name) {
        try {
            Field field = io.lunararcdevs.lunararc.common.mod.LunarArcReflectionBridge.getDeclaredField(
                    ClientboundPlayerInfoUpdatePacket.class, name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
