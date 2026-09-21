package io.lunararcdevs.lunararc.common.compat;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;

/**
 * Real Paper adds a constructor overload to this packet taking a pre-built {@code Entry} directly,
 * as a server-side performance patch - plugins compiled against Paper's API, such as TAB's
 * paper_1_20_5 platform module, call it to send a fully custom Entry (e.g. a spoofed display name)
 * without needing a real ServerPlayer. Plain vanilla NMS, which this platform runs on, only has
 * constructors that build the Entry themselves from a ServerPlayer - there is no vanilla constructor
 * this can delegate to, so the fields are set directly via reflection instead, mirroring what Paper's
 * own added constructor does internally. See
 * {@code LunarArcIntegratedPatcher.patchPlayerInfoUpdateSingleEntryConstructor} for the plugin-load-time
 * redirect that routes calls here instead of the nonexistent Paper-only constructor.
 */
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
            Field field = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
