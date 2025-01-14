package com.jab125.util.tradehelper;

import net.minecraft.village.TradeOffers;

import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * Remapped by Jab125
 */
public enum TradeRarity
{
    COMMON("common", (trades, random) -> trades.size(), (trades, random) -> trades.size(), true),
    UNCOMMON("uncommon", (trades, random) -> 3, (trades, random) -> random.nextInt(5) + 1, true),
    RARE("rare", (trades, random) -> 1, (trades, random) -> random.nextInt(3) + 1, true),
    EPIC("epic", (trades, random) -> 0, (trades, random) -> random.nextInt(2), true),
    LEGENDARY("legendary", (trades, random) -> 0, (trades, random) -> random.nextInt(25) == 0 ? 1 : 0, true);

    private final String key;
    private final BiFunction<List<TradeOffers.Factory>, Random, Integer> minimum;
    private final BiFunction<List<TradeOffers.Factory>, Random, Integer> maximum;
    private final boolean shuffle;

    TradeRarity(String key, BiFunction<List<TradeOffers.Factory>, Random, Integer> minimum, BiFunction<List<TradeOffers.Factory>, Random, Integer> maximum, boolean shuffle)
    {
        this.key = key;
        this.minimum = minimum;
        this.maximum = maximum;
        this.shuffle = shuffle;
    }

    public String getKey()
    {
        return this.key;
    }

    public BiFunction<List<TradeOffers.Factory>, Random, Integer> getMinimum()
    {
        return this.minimum;
    }

    public BiFunction<List<TradeOffers.Factory>, Random, Integer> getMaximum()
    {
        return this.maximum;
    }

    public boolean shouldShuffle()
    {
        return this.shuffle;
    }}
