package com.aeimo.itemprocessingwarner;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(name = "Item Processing Warner", description = "Alerts and Advance Warnings 0for Item Processing Tasks (e.g. Crafting)", tags = {"item", "items", "processing", "completion", "warn", "warning", "alert", "skilling", "cooking", "crafting", "smithing", "smelting", "fletching", "prayer", "herblore"})
public class ItemProcessingWarnerPlugin extends Plugin {
    // @formatter:off
    //<editor-fold desc=attributes>
    //== attributes ===================================================================================================================

    @Inject
    private ItemProcessingWarnerConfig config;
    @Inject
    private ItemProcessingWarnerOverlay overlay;
    @Inject
    private Client client;
    @Inject
    private OverlayManager overlayManager;

    @Provides
    ItemProcessingWarnerConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(ItemProcessingWarnerConfig.class);
    }

    private PreviousAndCurrent<LocalPoint> playerLocationMemory;

    // Inventory info
    private final Map<Integer, PreviousAndCurrentInt> itemCountMemory = new HashMap<>();

    private boolean strongAlert;

    private boolean weakAlert;
    private Integer lastItemIncrease;
    private Integer lastItemDecrease;
    private int ticksSinceLastItemChange = 0;

    private LocalDateTime processingCompletedTime;

    private int[] inventoryAtTimeOfProcessingComplete;

    private LocalDateTime predictionThresholdBreachedTime;

    //</editor-fold>
    // @formatter:on

    //<editor-fold desc=subscriptions>
    //== subscriptions ===============================================================================================================

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        clearState();
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        Widget bankContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
        if (bankContainer != null && !bankContainer.isSelfHidden()) {
            // Do nothing and avoid tracking any events if the bank is open, this can trigger false positives.
            inventoryAtTimeOfProcessingComplete = null;
            predictionThresholdBreachedTime = null;
            processingCompletedTime = null;
            return;
        }

        updateCountsOfItems();
        updatePlayerLocation();

        if (lastItemDecrease != null && countOfItem(lastItemDecrease) == 0) {
            int[] currentInventory = getInventoryArray();
            if (!Arrays.equals(currentInventory, inventoryAtTimeOfProcessingComplete)) {
                processingCompletedTime = LocalDateTime.now();
                inventoryAtTimeOfProcessingComplete = currentInventory;
            }
        } else {
            inventoryAtTimeOfProcessingComplete = null;
            predictionThresholdBreachedTime = null;
            processingCompletedTime = null;
        }

        strongAlert = shouldDoAlertStrong();
        weakAlert = shouldDoAlertWeak();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked click) {
        onUserInteractionEvent();
    }

    @Subscribe
    public void onMenuOpened(MenuOpened click) {
        onUserInteractionEvent();
    }

    private void onUserInteractionEvent() {
        strongAlert = false;
        weakAlert = false;
        lastItemIncrease = null;
        lastItemDecrease = null;
    }
    //</editor-fold>

    //<editor-fold desc=core>
    //== core ========================================================================================================================

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        updatePlayerLocation();
    }

    private void clearState() {
        itemCountMemory.clear();

        playerLocationMemory = null;
        lastItemDecrease = null;
        lastItemIncrease = null;
        ticksSinceLastItemChange = 0;
    }

    protected boolean shouldDoAlertWeak() {
        if (!config.usePreEmptiveAlerts()) {
            return false;
        }

        if (userInteractingWithClient()) {
            return false;
        }

        if (predictionThresholdBreachedTime != null && client.getMouseLastPressedMillis() >= timeSinceEpoch(predictionThresholdBreachedTime)) {
            return false;
        }

        if (lastItemDecrease != null && meetsThresholdWithRemainderDelayOrExceeds(getItemCount(lastItemDecrease))) {
            predictionThresholdBreachedTime = LocalDateTime.now();
            return true;
        }

        // If we were already alerting, keep alerting. Otherwise, we have no reason to start an alert.
        return weakAlert;
    }

    private boolean meetsThresholdWithRemainderDelayOrExceeds(int subject) {
        int thresholdTicks = secondsToTicksRoundNearest(config.preEmptiveDelayMs() / 1000f);

        // Unsuccessful actions pad ticksSinceLastItemChange, get around this by figuring out when the last action (successful
        // or otherwise) must have occurred.
        int ticksSinceLastAction = ticksSinceLastItemChange % config.ticksPerAction();

        int estimatedTicksLeft = ((config.ticksPerAction() * subject) - ticksSinceLastAction);
        return thresholdTicks >= estimatedTicksLeft;
    }

    private boolean shouldDoAlertStrong() {
        if (userInteractingWithClient()) {
            return false;
        }

        if (processingCompletedTime == null) {
            // The client hasn't seen the user finish processing a set of items this session.
            return false;
        }

        // Only alert if the user hasn't clicked since the last set of items finished processing.
        return client.getMouseLastPressedMillis() < timeSinceEpoch(processingCompletedTime);
    }

    private static long timeSinceEpoch(LocalDateTime localDateTime) {
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime zonedDateTime = localDateTime.atZone(zoneId);
        Instant instant = zonedDateTime.toInstant();
        return instant.toEpochMilli();
    }

    private void updateCountsOfItems() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return;
        }

        Arrays.stream(inventory.getItems())
                .filter(Objects::nonNull)
                .map(Item::getId)
                .distinct()
                // Empty inventory slot
                .filter(i -> i != -1)
                .forEach(this::updateCountOfItem);

        Integer maybeItemIncreased = determineLastItemChange(this::anyItemsIncreased);
        Integer maybeItemDecreased = determineLastItemChange(this::anyItemsDecreased);
        if (maybeItemIncreased == null && maybeItemDecreased == null) {
            ticksSinceLastItemChange++;
        } else {
            ticksSinceLastItemChange = 0;
        }
        lastItemIncrease = orDefault(maybeItemIncreased, lastItemIncrease);
        lastItemDecrease = orDefault(maybeItemDecreased, lastItemDecrease);
    }

    private <T> T orDefault(T maybe, T defaultValue) {
        return maybe == null ? defaultValue : maybe;
    }

    private Integer determineLastItemChange(IntPredicate handler) {
        List<Integer> changedCountItems = itemCountMemory.keySet().stream()
                .filter(handler::test)
                .collect(Collectors.toList());
        if (!changedCountItems.isEmpty()) {
            if (changedCountItems.size() > 1) {
                log.error("Multiple tracked items changed in the same way: {}", changedCountItems);
            } else {
                return changedCountItems.get(0);
            }
        }
        return null;
    }

    private void updatePlayerLocation() {
        LocalPoint playerLocation = client.getLocalDestinationLocation();
        if (playerLocationMemory == null) {
            playerLocationMemory = new PreviousAndCurrent<>(playerLocation);
        } else {
            playerLocationMemory.newData(playerLocation);
        }
    }
    //</editor-fold>

    //<editor-fold desc=helpers (alerts)>
    //== helpers (alerts) ===========================================================================================================================

    private boolean userInteractingWithClient() {
        // `client.getKeyboardIdleTicks() < 10` used to be included here
        return client.getGameState() != GameState.LOGGED_IN
                || client.getLocalPlayer() == null
                // If user has clicked in the last second then they're not idle so don't send idle notification
                || System.currentTimeMillis() - client.getMouseLastPressedMillis() < 1000
                || client.getKeyboardIdleTicks() < 10;
    }

    private int getItemCount(int itemId) {
        return itemCountMemory.get(itemId).current;
    }

    private static int secondsToTicksRoundNearest(float seconds) {
        return (int) Math.round(seconds / 0.6);
    }

    public int getGlowBreathePeriod() {
        return config.glowSpeedMs();
    }

    public int getMaxBreatheIntensityPercent() {
        return config.maxBreatheIntensityPercent();
    }

    public int getWeakGlowBreathePeriod() {
        return config.weakGlowSpeedMs();
    }

    public int getWeakMaxBreatheIntensityPercent() {
        return config.weakMaxBreatheIntensityPercent();
    }

    public Color getGlowColor() {
        return config.glowColor();
    }

    public Color getWeakGlowColor() {
        return config.weakGlowColor();
    }

    public boolean isDoAlertWeak() {
        return weakAlert;
    }

    public boolean isDoAlertStrong() {
        return strongAlert;
    }
    //</editor-fold>

    //<editor-fold desc=helpers (item management)>
    //== helpers (item management) ==================================================================================================================

    private int[] getInventoryArray() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);

        int[] inventoryArray = new int[28];

        if (inventory != null && inventory.size() == 28) {
            for (int i = 0; i < 28; i++) {
                inventoryArray[i] = inventory.getItems()[i].getId();
            }
        }

        return inventoryArray;
    }

    private int countOfItem(int itemId) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return 0;
        }

        return (int) Arrays.stream(inventory.getItems())
                .filter(Objects::nonNull)
                // Empty inventory slot
                .filter(i -> i.getId() == itemId)
                .count();
    }

    private void updateCountOfItem(int itemId) {
        int count = countOfItem(itemId);
        if (itemCountMemory.containsKey(itemId)) {
            itemCountMemory.get(itemId).newData(count);
        } else {
            itemCountMemory.put(itemId, new PreviousAndCurrentInt(count));
        }
    }

    private boolean anyItemsIncreased(int... itemIds) {
        return Arrays.stream(itemIds)
                .mapToObj(itemCountMemory::get)
                .anyMatch(PreviousAndCurrentInt::increased);
    }

    private boolean anyItemsDecreased(int... itemIds) {
        return Arrays.stream(itemIds)
                .mapToObj(itemCountMemory::get)
                .anyMatch(PreviousAndCurrentInt::decreased);
    }
    //</editor-fold>

    //<editor-fold desc=types>
    //== types ======================================================================================================================================

    static class PreviousAndCurrent<T> {
        T previous;

        T current;

        PreviousAndCurrent(T initialValue) {
            current = initialValue;
        }

        void newData(T data) {
            previous = current;
            current = data;
        }
    }

    static class PreviousAndCurrentInt extends PreviousAndCurrent<Integer> {
        PreviousAndCurrentInt(Integer initialValue) {
            super(initialValue);
        }

        boolean increased() {
            return current != null && previous != null && current > previous;
        }

        boolean decreased() {
            return current != null && previous != null && previous > current;
        }
    }
    //</editor-fold>
}
