package com.aeimo.itemprocessingwarner;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;

public class ItemProcessingWarnerOverlay extends Overlay {
    private static final int MAX_BRIGHTNESS_ALPHA_LEVEL = 255;

    @Inject
    private ItemProcessingWarnerPlugin plugin;

    @Inject
    private Client client;

    private boolean isRenderingAlertAnimation = false;

    @Inject
    private ItemProcessingWarnerOverlay(Client client, ItemProcessingWarnerPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(0f);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.isDoAlertStrong()) {
            glowGameWindowRectangle(
                    graphics,
                    plugin.getGlowColor(),
                    plugin.getGlowBreathePeriod(),
                    plugin.getMaxBreatheIntensityPercent(),
                    1.0f);

        } else if (plugin.isDoAlertWeak()) {
            glowGameWindowRectangle(
                    graphics,
                    plugin.getWeakGlowColor(),
                    plugin.getWeakGlowBreathePeriod(),
                    plugin.getWeakMaxBreatheIntensityPercent(),
                    0.5f);

        } else {
            isRenderingAlertAnimation = false;
        }

        return null;
    }

    private void glowGameWindowRectangle(
            Graphics2D graphics,
            Color glowColor,
            int glowPeriod,
            int maxIntensityBreathePercent,
            float intensityModifier) {

        graphics.setColor(new Color(
                glowColor.getRed(),
                glowColor.getGreen(),
                glowColor.getBlue(),
                getBreathingAlpha(glowPeriod, maxIntensityBreathePercent, intensityModifier))
        );
        graphics.fill(getGameWindowRectangle());
    }

    private Rectangle getGameWindowRectangle() {
        Dimension clientCanvasSize = client.getCanvas().getSize();
        Point clientCanvasLocation = client.getCanvas().getLocation();
        // Need to adjust rectangle position slightly to cover whole game window perfectly (x: -5, y: -20)
        Point adjustedLocation = new Point(clientCanvasLocation.x - 5, clientCanvasLocation.y - 20);

        return new Rectangle(adjustedLocation, clientCanvasSize);
    }

    private int getBreathingAlpha(int breathePeriodMillis, int maxIntensityBreathePercent, float intensityModifier) {
        double currentMillisOffset = System.currentTimeMillis() % breathePeriodMillis;
        double fractionCycleComplete = currentMillisOffset / breathePeriodMillis;

        int maxIntensityPc = (int) ((float) maxIntensityBreathePercent * intensityModifier);
        double fractionAlpha = Math.sin(fractionCycleComplete * 2 * Math.PI);
        double fractionAlphaPositive = (fractionAlpha + 1) / 2;

        // This check forces the animation to start near the dimmest point of the wave (gives a fade-in effect)
        if (isRenderingAlertAnimation || fractionAlphaPositive < 0.025) {
            isRenderingAlertAnimation = true;
            return ((int) (fractionAlphaPositive * MAX_BRIGHTNESS_ALPHA_LEVEL * (maxIntensityPc / 100.0)));
        }
        return 0;
    }
}
