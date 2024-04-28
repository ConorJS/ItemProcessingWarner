package com.aeimo.itemprocessingwarner;

import java.awt.Color;
import net.runelite.client.config.*;

@ConfigGroup("itemprocessingwarner")
public interface ItemProcessingWarnerConfig extends Config {
    int DEFAULT_GLOW_BREATHE_PERIOD_MS = 1_000;

    int DEFAULT_PRE_EMPTIVE_DELAY_MS = 1_200;

    int DEFAULT_MAX_GLOW_BREATHE_INTENSITY = 60;

    int TICKS_PER_ACTION = 3;

    Color DEFAULT_GLOW_COLOR = new Color(255, 0, 0);

    Color DEFAULT_WEAK_GLOW_COLOR = new Color(255, 192, 0);

    @ConfigSection(
            name = "Immediate alerts",
            description = "Configuration for immediate alerts",
            position = 98
    )
    String immediateAlertsSection = "immediateAlerts";

    @ConfigSection(
            name = "Pre-emptive alerts",
            description = "Configuration for pre-emptive alerts",
            position = 99
    )
    String preEmptiveAlertsSection = "preEmptiveAlerts";

    @ConfigItem(
            keyName = "glowSpeedMs",
            name = "Glow speed (ms)",
            description = "How long between cycles of min and max brightness of the glow effect",
            section = immediateAlertsSection,
            position = 1
    )
    @Units(Units.MILLISECONDS)
    default int glowSpeedMs() {
        return DEFAULT_GLOW_BREATHE_PERIOD_MS;
    }

    @ConfigItem(
            keyName = "maxBreatheIntensityPercent",
            name = "Peak glow intensity",
            description = "Peak intensity of glow effect (100% is opaque)",
            section = immediateAlertsSection,
            position = 2
    )
    @Units(Units.PERCENT)
    @Range(min = 10, max = 100)
    default int maxBreatheIntensityPercent() {
        return DEFAULT_MAX_GLOW_BREATHE_INTENSITY;
    }

    @Alpha
    @ConfigItem(
            keyName = "glowColor",
            name = "Alert glow color",
            description = "The color of the main glow effect",
            section = immediateAlertsSection,
            position = 3
    )
    default Color glowColor() {
        return DEFAULT_GLOW_COLOR;
    }

    @ConfigItem(
            keyName = "usePreEmptiveAlerts",
            name = "Use pre-emptive alerts",
            description = "If enabled, will give a weaker alert right before a task is complete",
            section = preEmptiveAlertsSection,
            position = 4
    )
    default boolean usePreEmptiveAlerts() {
        return false;
    }

    @ConfigItem(
            keyName = "ticksPerAction",
            name = "Ticks per action",
            description = "How many ticks does it take to process each item, for whatever activity being done?",
            section = preEmptiveAlertsSection,
            position = 5
    )
    @Units(Units.TICKS)
    default int ticksPerAction() {
        return TICKS_PER_ACTION;
    }

    @ConfigItem(
            keyName = "preEmptiveDelayMs",
            name = "Alert advance warning",
            description = "How long before a task is done to start the pre-emptive alert (ms)",
            section = preEmptiveAlertsSection,
            position = 6
    )
    @Units(Units.MILLISECONDS)
    default int preEmptiveDelayMs() {
        return DEFAULT_PRE_EMPTIVE_DELAY_MS;
    }

    @ConfigItem(
            keyName = "weakGlowSpeedMs",
            name = "Glow speed (ms)",
            description = "How long between cycles of min and max brightness of the glow effect for the pre-emptive alert",
            section = preEmptiveAlertsSection,
            position = 7
    )
    @Units(Units.MILLISECONDS)
    default int weakGlowSpeedMs() {
        return DEFAULT_GLOW_BREATHE_PERIOD_MS;
    }

    @ConfigItem(
            keyName = "weakMaxBreatheIntensityPercent",
            name = "Peak glow intensity",
            description = "Peak intensity of the glow effect for the pre-emptive alert (100% is opaque)",
            section = preEmptiveAlertsSection,
            position = 8
    )
    @Units(Units.PERCENT)
    @Range(min = 10, max = 100)
    default int weakMaxBreatheIntensityPercent() {
        return DEFAULT_MAX_GLOW_BREATHE_INTENSITY;
    }

    @Alpha
    @ConfigItem(
            keyName = "weakGlowColor",
            name = "Alert glow color",
            description = "The color of the glow effect used in pre-emptive alerts",
            section = preEmptiveAlertsSection,
            position = 9
    )
    default Color weakGlowColor() {
        return DEFAULT_WEAK_GLOW_COLOR;
    }
}
