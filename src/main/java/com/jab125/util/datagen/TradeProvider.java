package com.jab125.util.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jab125.util.tradehelper.TradeRarity;
import com.jab125.util.tradehelper.type.ITradeType;
import net.hat.gt.GobT;
import net.minecraft.data.DataCache;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reworked for fabric by Jab125
 */
public abstract class TradeProvider implements DataProvider
{
    private static final Logger LOGGER = GobT.LOGGER;
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();

    private final DataGenerator generator;
    private Map<EntityType<?>, EnumMap<TradeRarity, List<ITradeType<?>>>> trades = new HashMap<>();
    private Map<EntityType<?>, EnumMap<TradeRarity, List<ITradeType<?>>>> vanillaTrades = new HashMap<>();

    protected TradeProvider(DataGenerator generator)
    {
        this.generator = generator;
    }

    protected abstract void registerTrades();

    protected void registerVanillaTrades() {};

    // Use an access-widener to access this
    protected final void addTrade(EntityType<?> type, TradeRarity rarity, boolean isVanilla, ITradeType<?> trade)
    {
        if (isVanilla) {
            this.vanillaTrades.putIfAbsent(type, new EnumMap<>(TradeRarity.class));
            this.vanillaTrades.get(type).putIfAbsent(rarity, new ArrayList<>());
            this.vanillaTrades.get(type).get(rarity).add(trade);
        } else {
            this.trades.putIfAbsent(type, new EnumMap<>(TradeRarity.class));
            this.trades.get(type).putIfAbsent(rarity, new ArrayList<>());
            this.trades.get(type).get(rarity).add(trade);
        }
    }

    protected final void addTrade(EntityType<?> type, TradeRarity rarity, ITradeType<?> trade)
    {
        addTrade(type, rarity, false, trade);
    }


    @Override
    public void run(DataCache cache)
    {
        this.trades.clear();
        this.registerTrades();
        this.trades.forEach((entityType, tradeRarityListEnumMap) ->
        {
            tradeRarityListEnumMap.forEach((tradeRarity, tradeList) ->
            {
                JsonObject object = new JsonObject();
                object.addProperty("replace", false);
                JsonArray tradeArray = new JsonArray();
                tradeList.forEach(trade -> tradeArray.add(trade.serialize()));
                object.add("trades", tradeArray);
                Identifier id = Objects.requireNonNull(Registry.ENTITY_TYPE.getId(entityType));
                Path path = this.generator.getOutput().resolve("data/" + id.getNamespace() + "/trades/" + id.getPath() + "/" + tradeRarity.getKey() + ".json");
                try
                {
                    String rawJson = GSON.toJson(object);
                    String hash = SHA1.hashUnencodedChars(rawJson).toString();
                    if(!Objects.equals(cache.getOldSha1(path), hash) || !Files.exists(path))
                    {
                        Files.createDirectories(path.getParent());
                        try(BufferedWriter writer = Files.newBufferedWriter(path))
                        {
                            writer.write(rawJson);
                        }
                    }
                    cache.updateSha1(path, hash);
                }
                catch(IOException e)
                {
                    LOGGER.error("Couldn't save trades to {}", path, e);
                }
            });
        });
        this.vanillaTrades.clear();
        this.registerVanillaTrades();
        this.vanillaTrades.forEach((entityType, tradeRarityListEnumMap) ->
        {
            tradeRarityListEnumMap.forEach((tradeRarity, tradeList) ->
            {
                JsonObject object = new JsonObject();
                object.addProperty("replace", true);
                JsonArray tradeArray = new JsonArray();
                tradeList.forEach(trade -> tradeArray.add(trade.serialize()));
                object.add("trades", tradeArray);
                Identifier id = Objects.requireNonNull(Registry.ENTITY_TYPE.getId(entityType));
                Path path = this.generator.getOutput().resolve("resourcepacks/gobtvanillaish/data/" + "gobtvanillaish" + "/trades/" + id.getPath() + "/" + tradeRarity.getKey() + ".json");
                try
                {
                    String rawJson = GSON.toJson(object);
                    String hash = SHA1.hashUnencodedChars(rawJson).toString();
                    if(!Objects.equals(cache.getOldSha1(path), hash) || !Files.exists(path))
                    {
                        Files.createDirectories(path.getParent());
                        try(BufferedWriter writer = Files.newBufferedWriter(path))
                        {
                            writer.write(rawJson);
                        }
                    }
                    cache.updateSha1(path, hash);
                }
                catch(IOException e)
                {
                    LOGGER.error("Couldn't save trades to {}", path, e);
                }
            });
        });
    }

    @Override
    public String getName()
    {
        return "Trades: " + GobT.MODID;
    }
}
