package dev.shadowsoffire.apotheosis.adventure.loot;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.mutable.MutableInt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.ListCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.adventure.AdventureModule;
import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;
import dev.shadowsoffire.placebo.codec.PlaceboCodecs;
import dev.shadowsoffire.placebo.json.PSerializer;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import dev.shadowsoffire.placebo.reload.TypeKeyed.TypeKeyedBase;
import dev.shadowsoffire.placebo.reload.WeightedDynamicRegistry.ILuckyWeighted;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class LootRarity extends TypeKeyedBase<LootRarity> implements ILuckyWeighted, Comparable<LootRarity> {

    public static final Codec<LootRarity> LOAD_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        TextColor.CODEC.fieldOf("color").forGetter(LootRarity::getColor),
        ForgeRegistries.ITEMS.getCodec().fieldOf("material").forGetter(LootRarity::getMaterial),
        Codec.INT.fieldOf("ordinal").forGetter(LootRarity::ordinal),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("weight").forGetter(ILuckyWeighted::getWeight),
        Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf("quality", 0F).forGetter(ILuckyWeighted::getQuality),
        new ListCodec<>(LootRule.CODEC).fieldOf("rules").forGetter(LootRarity::getRules))
        .apply(inst, LootRarity::new));

    public static final PSerializer<LootRarity> SERIALIZER = PSerializer.fromCodec("Loot Rarity", LOAD_CODEC);

    public static final Codec<DynamicHolder<LootRarity>> HOLDER_CODEC = ExtraCodecs.lazyInitializedCodec(() -> Codec.STRING.xmap(RarityRegistry::convertId, ResourceLocation::toString).xmap(RarityRegistry.INSTANCE::holder,
        DynamicHolder::getId));

    /**
     * Direct resolution codec. Only for use in other datapack objects which load after the {@link RarityRegistry}.
     */
    public static final Codec<LootRarity> CODEC = ExtraCodecs.lazyInitializedCodec(() -> HOLDER_CODEC.xmap(DynamicHolder::get, RarityRegistry.INSTANCE::holder));

    private final Item material;
    private final TextColor color;
    private final int ordinal;
    private final int weight;
    private final float quality;
    private final List<LootRule> rules;

    private LootRarity(TextColor color, Item material, int ordinal, int weight, float quality, List<LootRule> rules) {
        this.color = color;
        this.material = material;
        this.ordinal = ordinal;
        this.weight = weight;
        this.quality = quality;
        this.rules = rules;
    }

    public Item getMaterial() {
        return this.material;
    }

    public TextColor getColor() {
        return this.color;
    }

    public int ordinal() {
        return this.ordinal;
    }

    @Override
    public int getWeight() {
        return this.weight;
    }

    @Override
    public float getQuality() {
        return this.quality;
    }

    public List<LootRule> getRules() {
        return this.rules;
    }

    public LootRarity next() {
        return RarityRegistry.next(RarityRegistry.INSTANCE.holder(this)).get();
    }

    public LootRarity prev() {
        return RarityRegistry.prev(RarityRegistry.INSTANCE.holder(this)).get();
    }

    /**
     * Checks if this rarity is the same or worse than the passed rarity.
     */
    public boolean isAtMost(LootRarity other) {
        return this.ordinal() <= other.ordinal();
    }

    /**
     * Checks if this rarity is the same or better than the passed rarity.
     */
    public boolean isAtLeast(LootRarity other) {
        return this.ordinal() >= other.ordinal();
    }

    /**
     * Returns the minimum (worst) rarity between a and b.
     */
    public static LootRarity min(LootRarity a, @Nullable LootRarity b) {
        if (b == null) return a;
        return a.ordinal <= b.ordinal ? a : b;
    }

    /**
     * Returns the maximum (best) rarity between a and b.
     */
    public static LootRarity max(LootRarity a, @Nullable LootRarity b) {
        if (b == null) return a;
        return a.ordinal >= b.ordinal ? a : b;
    }

    /**
     * Clamps a loot rarity to within a min/max bound.
     *
     * @param lowerBound The minimum valid rarity
     * @param upperBound The maximum valid rarity
     * @return This, if this is within the bounds, or the min or max if it exceeded that bound.
     */
    public LootRarity clamp(@Nullable LootRarity lowerBound, @Nullable LootRarity upperBound) {
        return LootRarity.max(LootRarity.min(this, upperBound), lowerBound);
    }

    public Component toComponent() {
        return Component.translatable("rarity." + this.getId()).withStyle(Style.EMPTY.withColor(this.color));
    }

    @Override
    public String toString() {
        return "LootRarity{" + this.id + "}";
    }

    @Override
    public int compareTo(LootRarity o) {
        return Integer.compare(this.ordinal, o.ordinal);
    }

    @Override
    public PSerializer<? extends LootRarity> getSerializer() {
        return SERIALIZER;
    }

    public static LootRarity random(RandomSource rand, float luck) {
        return random(rand, luck, null, null);
    }

    public static LootRarity random(RandomSource rand, float luck, @Nullable Clamped item) {
        if (item == null) return random(rand, luck);
        return random(rand, luck, item.getMinRarity(), item.getMaxRarity());
    }

    public static LootRarity random(RandomSource rand, float luck, @Nullable LootRarity min, @Nullable LootRarity max) {
        return RarityRegistry.INSTANCE.getRandomItem(rand, luck).clamp(min, max);
    }

    public static <T> Codec<Map<LootRarity, T>> mapCodec(Codec<T> codec) {
        return Codec.unboundedMap(LootRarity.CODEC, codec);
    }

    public static record LootRule(AffixType type, float chance, @Nullable LootRule backup) {

        public static final Codec<LootRule> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            PlaceboCodecs.enumCodec(AffixType.class).fieldOf("type").forGetter(LootRule::type),
            Codec.FLOAT.fieldOf("chance").forGetter(LootRule::chance),
            ExtraCodecs.lazyInitializedCodec(() -> LootRule.CODEC).optionalFieldOf("backup").forGetter(rule -> Optional.ofNullable(rule.backup())))
            .apply(inst, LootRule::new));

        private static Random jRand = new Random();

        public LootRule(AffixType type, float chance) {
            this(type, chance, Optional.empty());
        }

        public LootRule(AffixType type, float chance, Optional<LootRule> backup) {
            this(type, chance, backup.orElse(null));
        }

        public void execute(ItemStack stack, LootRarity rarity, Set<DynamicHolder<Affix>> currentAffixes, MutableInt sockets, RandomSource rand) {
            if (this.type == AffixType.DURABILITY) return;
            if (rand.nextFloat() <= this.chance) {
                if (this.type == AffixType.SOCKET) {
                    sockets.add(1);
                    return;
                }
                List<DynamicHolder<Affix>> available = AffixHelper.byType(this.type).stream().filter(a -> a.get().canApplyTo(stack, LootCategory.forItem(stack), rarity) && !currentAffixes.contains(a)).collect(Collectors.toList());
                if (available.size() == 0) {
                    if (this.backup != null) this.backup.execute(stack, rarity, currentAffixes, sockets, rand);
                    else AdventureModule.LOGGER.error("Failed to execute LootRule {}/{}/{}/{}!", ForgeRegistries.ITEMS.getKey(stack.getItem()), rarity.getId(), this.type, this.chance);
                    return;
                }
                jRand.setSeed(rand.nextLong());
                Collections.shuffle(available, jRand);
                currentAffixes.add(available.get(0));
            }
        }
    }

    public static interface Clamped {

        @Nullable
        public LootRarity getMinRarity();

        @Nullable
        public LootRarity getMaxRarity();

        default LootRarity clamp(LootRarity rarity) {
            return rarity.clamp(this.getMinRarity(), this.getMaxRarity());
        }

        public static record Simple(DynamicHolder<LootRarity> min, DynamicHolder<LootRarity> max) implements Clamped {

            @Override
            public LootRarity getMinRarity() {
                return this.min.getOptional().orElse(null);
            }

            @Override
            public LootRarity getMaxRarity() {
                return this.max.getOptional().orElse(null);
            }

        }

    }
}
