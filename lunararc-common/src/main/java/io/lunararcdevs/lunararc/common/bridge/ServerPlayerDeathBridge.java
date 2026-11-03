package io.lunararcdevs.lunararc.common.bridge;

/** One-shot PlayerDeathEvent experience state carried across the real NMS respawn replacement. */
public interface ServerPlayerDeathBridge {
    record ExperienceState(
            boolean keepLevel,
            int oldLevel,
            int oldTotalExperience,
            float oldProgress,
            int newExp,
            int newTotalExperience,
            int newLevel) {}

    ExperienceState lunararc$getDeathExperienceState();
    void lunararc$setDeathExperienceState(ExperienceState state);

    java.util.List<net.minecraft.world.item.ItemStack> lunararc$getDeathInventoryState();
    void lunararc$setDeathInventoryState(java.util.List<net.minecraft.world.item.ItemStack> state);

    /** Player#dropEquipment hands its Inventory#dropAll here during a PlayerDeathEvent; false lets vanilla drop as usual. */
    boolean lunararc$dropDeathItems(Runnable dropAll);
}
