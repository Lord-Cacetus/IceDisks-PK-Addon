package me.kaketuz.icedisks;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.attribute.markers.DayNightFactor;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.TempBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class IceDisks extends IceAbility implements AddonAbility {

    public static final String current_version = "1.0.1-R";

    private static final EnumSet<Material> WOODEN_BLOCKS = EnumSet.of(
            Material.OAK_WOOD, Material.OAK_LOG,
            Material.SPRUCE_LOG, Material.SPRUCE_WOOD,
            Material.BIRCH_LOG, Material.BIRCH_WOOD,
            Material.ACACIA_LOG, Material.ACACIA_WOOD,
            Material.JUNGLE_LOG, Material.JUNGLE_WOOD,
            Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD,
            Material.MANGROVE_LOG, Material.MANGROVE_WOOD,
            Material.CHERRY_LOG, Material.CHERRY_WOOD,
            Material.PALE_OAK_LOG, Material.PALE_OAK_WOOD
    );

    //source

    @DayNightFactor
    @Attribute(Attribute.SELECT_RANGE)
    private double source_range;
    @DayNightFactor
    @Attribute(Attribute.SELECT_RANGE)
    private double source_radius;
    @DayNightFactor
    private int aoe_sources_amount;

    private boolean allow_source_water;

    @DayNightFactor(invert = true)
    private int aoe_source_interval;
    @DayNightFactor
    private double aoe_source_speed;

    private long sources_revert_time;
    @DayNightFactor
    private double aoe_source_angular_speed;


    //disk block
    @DayNightFactor
    private int disks_amount;
    @DayNightFactor(invert = true)
    private long form_speed;
    @DayNightFactor
    private double disappear_radius;
    @DayNightFactor
    private double action_radius;

    //other
    @DayNightFactor(invert = true)
    private long cooldown;

    private boolean isFormed;
    private boolean canInteract;



    public enum SourceMode {
        SOURCE_FOUND, AOE_VARIANT
    }


    private SourceMode mode;
    private BlockDisplay block;
    private int current_sources;
    private int interval;
    private double vert;


    private final Set<TriPair<Location, Vector, Block>> SOURCE_STREAMS = new HashSet<>();

    private Listener listener;

    private boolean water_flag;
    private int disks_left;




    public IceDisks(Player player) {
        super(player);
        if (!this.bPlayer.canBendIgnoreBinds(this) || hasAbility(player, IceDisks.class) || RegionProtection.isRegionProtected(player, this)) return;

        this.source_range = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Source.Range");
        final boolean aoe_allowed = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Allowed");
        this.source_radius = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Radius");
        this.aoe_sources_amount = ConfigManager.getConfig().getInt("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.SourcesAmount");
        this.aoe_source_interval = ConfigManager.getConfig().getInt("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Interval");
        this.aoe_source_speed = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Speed");
        this.action_radius = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.ActionRadius");
        this.sources_revert_time = ConfigManager.getConfig().getLong("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.SourcesRevertTime");
        this.aoe_source_angular_speed = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.AngularSpeed");
        this.allow_source_water = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Source.AllowWaterSource");
        this.disks_amount = ConfigManager.getConfig().getInt("KaketuZ_Abilities.IceDisks.DisksAmount");
        this.disappear_radius = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.DisappearRadius");
        this.form_speed = ConfigManager.getConfig().getLong("KaketuZ_Abilities.IceDisks.FormSpeed");
        this.cooldown = ConfigManager.getConfig().getLong("KaketuZ_Abilities.IceDisks.Cooldown");


        //IDK why but BlockSource is so buggy

        final AtomicReference<Block> source = new AtomicReference<>(null);

        final RayTraceResult result = player.rayTraceBlocks(this.source_range, FluidCollisionMode.ALWAYS);
        Optional.ofNullable(result)
                .map(RayTraceResult::getHitBlock)
                .filter(this::isSourceValid)
                .ifPresent(source::set);

        if (source.get() == null) {
            if (!aoe_allowed) return;
            final Location target = getTargetedLocation(player, this.source_range, Block::isPassable);
            this.block = target.getWorld().spawn(target.clone().add(0.5, 0, 0.5), BlockDisplay.class, bd -> {
                bd.setBlock(Material.ICE.createBlockData());
                bd.setTransformation(new Transformation(
                        new Vector3f(-0.5f, 0, -0.5f),
                        new Quaternionf(),
                        new Vector3f(1, 0, 1),
                        new Quaternionf()
                ));
            });
            this.mode = SourceMode.AOE_VARIANT;
        }
        else {
            final Location relative = source.get().getRelative(BlockFace.UP, 1).getLocation();
            this.block = source.get().getWorld().spawn(relative.clone().add(0.5, 0, 0.5), BlockDisplay.class, bd -> {
                bd.setBlock(switch(source.get().getType()) {
                    case PACKED_ICE -> Material.PACKED_ICE.createBlockData();
                    case BLUE_ICE -> Material.BLUE_ICE.createBlockData();
                    default -> Material.ICE.createBlockData();
                });
                bd.setTransformation(new Transformation(
                        new Vector3f(-0.5f, 0, -0.5f),
                        new Quaternionf(),
                        new Vector3f(1, 0, 1),
                        new Quaternionf()
                ));
            });
            this.block.getWorld().playSound(this.block.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5f, 0);
            this.block.getWorld().playSound(this.block.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.25f, 0);
            this.mode = SourceMode.SOURCE_FOUND;
        }

        this.disks_left = this.disks_amount;

        start();
    }
    @SuppressWarnings("deprecation") //only 197 line: ChatColor is deprecated in PaperAPI
    @Override
    public void progress() {

        if (this.bPlayer.getBoundAbilityName().equals(getName())) {
            this.player.sendActionBar(Component.text(getName(), TextColor.color(getElement().getColor().getColor().getRGB()), TextDecoration.UNDERLINED));
        }

        if (RegionProtection.isRegionProtected(this.player, this.block.getLocation(), this)) {
            remove();
            return;
        }
        if (this.player.isDead() || !this.player.isOnline() || !this.bPlayer.canBendIgnoreBinds(this)) {
            this.bPlayer.addCooldown(this);
            remove();
            return;
        }
        if (this.block.getLocation().distance(this.player.getLocation()) >= this.disappear_radius) {
            this.bPlayer.addCooldown(this);
            remove();
            return;
        }
        if (!this.block.getWorld().equals(this.player.getWorld())) {
            this.bPlayer.addCooldown(this);
            remove();
            return;
        }

        this.canInteract = this.player.getLocation().distance(this.block.getLocation()) <= this.action_radius;

        if (!this.isFormed) {
            switch (this.mode) {
                case AOE_VARIANT -> {
                    if (!this.player.isSneaking()) {
                        this.player.getWorld().playSound(block.getLocation(), Sound.BLOCK_GLASS_BREAK, 1, 1);
                        this.player.getWorld().spawnParticle(Particle.BLOCK, this.block.getLocation().add(0.5, 0.5, 0.5), this.current_sources * 5, 0.5, (double) 1 / this.current_sources, 0.5, 0, this.block.getBlock());
                        this.bPlayer.addCooldown(this, getCooldown() / 2L);
                        remove();
                        return;
                    }

                    final Location target = getTargetedLocation(this.player, this.source_range, Block::isPassable);

                    if (System.currentTimeMillis() > getStartTime() + this.interval && this.SOURCE_STREAMS.size() < 10) {
                        Block closest = null;
                        double min = Double.MAX_VALUE;
                        Location targetLoc = target.clone();

                        for (final Block b : GeneralMethods.getBlocksAroundPoint(this.player.getLocation(), this.source_radius)) {
                            final Material type = b.getType();
                            final BlockData data = b.getBlockData();
                            final boolean valid = (isWater(b) || isPlant(b) || isSnow(b) || isIce(b) || WOODEN_BLOCKS.contains(type) || (data instanceof Waterlogged w && w.isWaterlogged()) || type == Material.WATER_CAULDRON || type == Material.WET_SPONGE) && type != Material.MANGROVE_ROOTS;

                            if (valid) {
                                final double distSq = b.getLocation().distanceSquared(targetLoc);
                                if (distSq < min) {
                                    min = distSq;
                                    closest = b;
                                }
                            }
                        }
                        if (closest != null) {
                            final Material type = closest.getType();
                            final BlockData data = closest.getBlockData();
                            BlockData newData = Material.AIR.createBlockData();

                            if (type == Material.WET_SPONGE) {
                                closest.setType(Material.SPONGE);
                            } else if (type == Material.WATER_CAULDRON && data instanceof Levelled level) {
                                if (level.getLevel() > 1) {
                                    level.setLevel(level.getLevel() - 1);
                                    closest.setBlockData(level);
                                } else {
                                    closest.setType(Material.CAULDRON);
                                }
                            }
                            else if (closest.getBlockData() instanceof Waterlogged wl) {
                                if (wl.isWaterlogged()) wl.setWaterlogged(false);
                            }
                            if (WOODEN_BLOCKS.contains(type)) {
                                newData = Material.MANGROVE_ROOTS.createBlockData();
                                closest.getWorld().spawnParticle(Particle.FISHING, closest.getLocation(), 30, 0, 0, 0, 0.3);
                                closest.getWorld().playSound(closest.getLocation(), Sound.ENTITY_CREAKING_DEATH, 0.35f, ThreadLocalRandom.current().nextFloat(0, 1));
                                closest.getWorld().playSound(closest.getLocation(), Sound.ENTITY_ZOMBIE_CONVERTED_TO_DROWNED, 0.15f, ThreadLocalRandom.current().nextFloat(0, 1));
                            } else if (isPlant(closest) && closest.getType() != Material.MANGROVE_ROOTS) {
                                if (GeneralMethods.getMCVersion() >= 1215) {
                                    newData = Material.TALL_DRY_GRASS.createBlockData();
                                }
                            } else if (isWater(closest) && data instanceof Levelled level) {
                                if (level.getLevel() < 7) {
                                    level.setLevel(level.getLevel() + 1);
                                    newData = level;
                                }
                            } else if (isIce(closest)) {
                                Levelled water = (Levelled) Material.WATER.createBlockData();
                                water.setLevel(1);
                                newData = water;
                            } else if (isSnow(closest) && data instanceof Levelled level) {
                                if (level.getLevel() > 1) {
                                    level.setLevel(level.getLevel() - 1);
                                    newData = level;
                                }
                            }

                            if (newData.getMaterial() != Material.AIR) new TempBlock(closest, newData, this.sources_revert_time);

                            final Location bLoc = closest.getLocation();
                            final Vector direction = GeneralMethods.getDirection(bLoc, targetLoc).normalize();
                            direction.add(direction.clone().crossProduct(new Vector(0, 1, 0)).multiply(ThreadLocalRandom.current().nextDouble(-1, 1))).multiply(this.aoe_source_speed);

                            this.SOURCE_STREAMS.add(new TriPair<>(bLoc, direction, bLoc.getBlock()));
                        }
                        this.interval += this.aoe_source_interval;
                    }

                    this.block.setTeleportDuration(2);
                    target.setPitch(0);
                    this.block.teleport(target);

                    final Iterator<TriPair<Location, Vector, Block>> iterator = this.SOURCE_STREAMS.iterator();
                    while (iterator.hasNext()) {
                        final TriPair<Location, Vector, Block> pair = iterator.next();
                        Vector desired = target.toVector()
                                .subtract(pair.first.toVector())
                                .normalize()
                                .multiply(this.aoe_source_speed);
                        pair.second.add(desired.clone()
                                .subtract(pair.second)
                                .multiply(this.aoe_source_angular_speed));


                        pair.first.add(pair.second);

                        if (pair.first.distance(target) < 0.5) {
                            this.block.getWorld().playSound(this.block.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 0.25f, 0);
                            pair.first.getWorld().playSound(pair.first, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.2f, 2);
                            pair.first.getWorld().spawnParticle(Particle.SNOWFLAKE, pair.first, 3, 0.5, 0.5, 0.5, 0.05);
                            this.current_sources++;
                            iterator.remove();
                            continue;
                        }

                        for (int i = 0; i < 5; i++) {
                            final Vector rand = getRandom().multiply(0.15);
                            pair.first.getWorld().spawnParticle(Particle.FISHING, pair.first.clone().add(rand), 0, pair.second.getX(), pair.second.getY(), pair.second.getZ(), 0.25);
                        }
                        final Biome biome = pair.first.getBlock().getBiome();
                        pair.first.getWorld().spawnParticle(Particle.DUST, pair.first, 5, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(getWaterColor(biome)), 1));

                        if (getRunningTicks() % 5 == 0) {
                            pair.first.getWorld().playSound(pair.first, Sound.ENTITY_BOAT_PADDLE_WATER, 0.25f, 0);
                        }
                        // I think this is superfluous
                        /*
                        RayTraceResult result = pair.first.getWorld().rayTraceBlocks(pair.first, pair.second, pair.second.length(), FluidCollisionMode.NEVER, true);
                        Optional.ofNullable(result)
                                .map(RayTraceResult::getHitBlock)
                                .filter(GeneralMethods::isSolid)
                                .filter(b -> !b.getType().equals(Material.MANGROVE_ROOTS))
                                .filter(b -> !b.equals(pair.third))
                                .ifPresent(b -> iterator.remove());
                         */

                    }
                    final Transformation transform = block.getTransformation();
                    if (transform.getScale().y < 1) {
                        float y = (float) this.current_sources / this.aoe_sources_amount;
                        transform.getScale().set(new Vector3f(1, Math.min(1, y), 1));
                        block.setTransformation(transform);
                    } else isFormed = true;
                }
                case SOURCE_FOUND -> {
                    final Transformation transform = block.getTransformation();
                    if (transform.getScale().y < 1) {
                        float y = (float) (System.currentTimeMillis() - getStartTime()) / this.form_speed;
                        this.block.setInterpolationDuration(2);
                        this.block.setInterpolationDelay(0);
                        this.block.setTransformation(new Transformation(
                                new Vector3f(-0.5f, 0, -0.5f),
                                new Quaternionf(),
                                new Vector3f(1, Math.min(1, y), 1),
                                new Quaternionf()
                        ));
                    } else this.isFormed = true;
                }
            }
        } else {
            if (this.disks_left <= 0) {
                this.bPlayer.addCooldown(this);
                remove();
            }


            final Location loc = this.block.getLocation();
            final Location centerLoc = loc.clone().add(0, 0.5, 0);
            final Block centerBlock = centerLoc.getBlock();
            final boolean inWater = isWater(centerBlock);

            if (inWater && !this.water_flag) {
                if (this.vert <= -0.5) {
                    bigSplashEffects(centerBlock);
                } else if (this.vert < 0) {
                    smallSplashEffects(centerBlock);
                }
                this.water_flag = true;
            }




            if (inWater) {
                if (Math.abs(vert) > 0.05) {
                    centerLoc.getWorld().spawnParticle(Particle.BUBBLE, centerLoc, 1, 1, 1, 1, 0.02);
                }

                this.vert *= 0.85;

                final Block blockAbove = centerBlock.getRelative(BlockFace.UP);
                if (isWater(blockAbove)) {
                    this.vert = Math.min(this.vert + 0.03, 0.1);
                } else {

                    final double targetY = centerBlock.getY() + 0.6;
                    final double diff = targetY - loc.getY();
                    this.vert += diff * 0.05;

                    if (getRunningTicks() % 20 == 0 && Math.abs(diff) > 0.1) {
                        smallSplashEffects(centerBlock);
                    }
                }

                final Block floor = loc.clone().subtract(0, 0.1, 0).getBlock();
                if (!floor.isPassable() && !isWater(floor)) {
                    this.vert = 0.15;
                }
            } else {
                this.water_flag = false;
                final Block blockBelow = loc.clone().subtract(0, 0.05, 0).getBlock();
                if (blockBelow.isPassable()) {
                    this.vert -= 0.04;
                } else {
                    if (this.vert < 0) {
                        this.vert = 0;

                        final Location add = this.block.getLocation();

                        add.getWorld().playSound(add, Sound.BLOCK_BASALT_BREAK, 0.5f, 0);

                        for (int i = 0; i < 20; i++) {
                            final Vector rand = getRandom().setY(0).normalize().multiply(0.5);
                            add.getWorld().spawnParticle(Particle.ITEM, add.clone().add(rand), 0, rand.getX(), rand.getY() + 0.5, rand.getZ(), 0.4, new ItemStack(i % 2 == 0 ? block.getBlock().getMaterial() : blockBelow.getType()));
                        }

                        for (int i = 0; i < 40; i++) {
                            final Vector rand = getRandom().setY(0).normalize().multiply(0.5);
                            add.getWorld().spawnParticle(Particle.DUST_PLUME, add.clone().add(rand), 0, rand.getX(), rand.getY(), rand.getZ(), 0.1);
                        }

                        loc.setY(blockBelow.getY() + 1.001);
                        this.block.teleport(loc);

                        return;
                    }
                }
            }

            this.vert = Math.max(Math.min(this.vert, 0.4), -1.0); //MAX работает даже в коде какетуса :D

            if (Math.abs(this.vert) > 0.001) {
                this.block.setTeleportDuration(2);
                this.block.teleport(loc.add(0, this.vert, 0));
            }
        }
    }

    private boolean isSourceValid(Block block) {
        if (isWater(block) && !this.allow_source_water) return false;
        return !isPlant(block) && (isWaterbendable(block) || isSnow(block) || isIcebendable(block) || (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) || block.getType() == Material.WATER_CAULDRON || block.getType() == Material.WET_SPONGE);
    }



    private void bigSplashEffects(Block water) {
        water.getWorld().playSound(water.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1, 1);

        for (int i = 0; i < 40; i++) {
            final Vector rand = getRandom().setY(0).normalize().multiply(1.5);
            water.getWorld().spawnParticle(Particle.FISHING, water.getLocation().add(0.5, 0, 0.5).add(rand), 0, 0, 1, 0, 0.4);
            if (i % 2 == 0) {
                water.getWorld().spawnParticle(Particle.SNOWFLAKE, water.getLocation().add(0.5, 0, 0.5).add(rand), 0, 0, 1, 0, 0.4);
            }
        }
        for (int i = 0; i < 40; i++) {
            final Vector rand = getRandom().setY(0).normalize().multiply(1.5);
            water.getWorld().spawnParticle(Particle.SPLASH, water.getLocation().add(0, 1, 0).add(rand), 0, Math.random() / 2, 1, Math.random() / 2, 0.4);
        }

        for (int i = -180; i < 180; i++) {
            final Vector orthogonal = GeneralMethods.getOrthogonalVector(new Vector(0, 1, 0), i, 0.1).normalize();
            Location add = water.getLocation().add(0, 1, 0).add(orthogonal);
            water.getWorld().spawnParticle(Particle.FISHING, add, 0, orthogonal.getX(), orthogonal.getY(), orthogonal.getZ(), 0.25);
        }
    }

    private void smallSplashEffects(Block water) {
        for (int i = 0; i < 5; i++) {
            final Vector rand = getRandom().setY(0).normalize().multiply(1.5);
            water.getWorld().spawnParticle(Particle.SPLASH, water.getLocation().add(0, 1, 0).add(rand), 0, Math.random() / 2, 1, Math.random() / 2, 0.1);
        }
    }

    public void shoot() {
        if (!this.isFormed || !this.canInteract) return;
        --this.disks_left;
        new IceDisksProjectile(this.player, this);
        final Transformation transformation = this.block.getTransformation();
        final float y = transformation.getScale().y;
        transformation.getScale().set(new Vector3f(1, y - (float) 1 / this.disks_amount, 1));
        this.block.setTransformation(transformation);

        this.block.getWorld().playSound(this.block.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.25f, 2);
        this.block.getWorld().playSound(this.block.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.25f, 1.25f);
        this.block.getWorld().playSound(this.block.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.25f, 0);
        for (int i = 0; i < 15; i++) {
            this.block.getWorld().spawnParticle(Particle.SNOWFLAKE, this.block.getLocation().add(0, y, 0), 0, ThreadLocalRandom.current().nextDouble(-1, 1), 0, ThreadLocalRandom.current().nextDouble(-1, 1), 0.2);
        }
    }

    @Override
    public void remove() {
        super.remove();
        this.block.remove();
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return "IceDisks";
    }

    @Override
    public Location getLocation() {
        return null;
    }

    @Override
    public void load() {
        listener = new AbilityListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(listener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Help.Description", "This ability allows the Waterbender to create a cube of ice. This cube can be used to form throwable ice disks that deal damage! The ice block can be created from non-water sources as long as there are water-containing objects nearby!");
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Help.Instruction", "Tap Sneak while looking at ice or water. Once the ice block forms, Left Click where you're facing to fire the disk in that direction. If there's no water nearby but there are a lot of plants, you can create a block by collecting water around it. Hold Sneak, and water will begin to flow from nearby plants, forming an ice block.");
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Source.Range", 10);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Allowed", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Radius", 20);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.SourcesAmount", 10);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Interval", 500);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Speed", 1);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.SourcesRevertTime", 10000);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.AngularSpeed", 0.05);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Source.AllowWaterSource", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.DisksAmount", 10);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.DisappearRadius", 50);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.FormSpeed", 500);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Cooldown", 9000);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Damage", 1);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Range", 20);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.CollisionRadius", 1);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Knockback", 0.25);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Speed", 1);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.AngularRotation", 0.24);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Cooldown", 0);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.CanBounce", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.WaterManipulation", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.Torrent", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.FireBlast", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.FireShield", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.WallOfFire", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.EarthBlast", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.AirSwipe", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.AirSweep", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.Player", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.RedirectAllowed", true);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.PlayerDodgeRange", 4);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Disk.Dodging.PlayerDodgeCollisionRadius", 0.76);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.ActionRadius", 4);
        ConfigManager.defaultConfig.save();

        ProjectKorra.plugin.getComponentLogger().info(Component.text("IceDisks ability by KaketuZ successfully loaded! Thanks for installing!", NamedTextColor.GREEN));
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(listener);
    }

    @Override
    public String getAuthor() {
        final Component author = Component.text("❄ KᴀᴋᴇᴛᴜZ", TextColor.color(0x9CC6FF))
                .hoverEvent(HoverEvent.showText(Component.text(Objects.requireNonNull(ConfigManager.getConfig().getString("KaketuZ_Abilities.IceDisks.Help.HoverAuthor")))
                        .decorate(TextDecoration.UNDERLINED)
                        .color(TextColor.color(0x51FF))))
                .clickEvent(ClickEvent.openUrl("https://projectkorra.com/forum/resources/authors/lord_cacetus_.28072/"));
        return LegacyComponentSerializer.legacySection().serialize(author);
    }

    @Override
    public String getVersion() {
        final Component version = Component.text("IceDisks", TextColor.color(0xB5D8FF), TextDecoration.BOLD)
                .append(Component.text(" v" + current_version, NamedTextColor.GREEN))
                .hoverEvent(HoverEvent.showText(Component.text(Objects.requireNonNull(ConfigManager.getConfig().getString("KaketuZ_Abilities.IceDisks.Help.HoverVersion")))
                        .decorate(TextDecoration.UNDERLINED)
                        .color(TextColor.color(0x51FF))))
                .clickEvent(ClickEvent.openUrl("https://projectkorra.com/forum/resources/authors/lord_cacetus_.28072/")); //TODO: изменить ссылку на будущий github
        return LegacyComponentSerializer.legacySection().serialize(version);
    }

    @Override
    public String getDescription() {
        return ConfigManager.getConfig().getString("KaketuZ_Abilities.IceDisks.Help.Description");
    }

    @Override
    public String getInstructions() {
        return ConfigManager.getConfig().getString("KaketuZ_Abilities.IceDisks.Help.Instruction");
    }

    //Do you really think I'm so crazy that I'm going to write water colors for every biome? Nuh uh, I used Gemini
    private final Map<Biome, Integer> COLOR_MAP = new HashMap<>();
    int DEFAULT_COLOR = 0x3F76E4;

    private void register(int color, Biome... biomes) {
        for (Biome biome : biomes) {
            COLOR_MAP.put(biome, color);
        }
    }

    private int getWaterColor(Biome biome) {
        return COLOR_MAP.getOrDefault(biome, DEFAULT_COLOR);
    }

    {
        register(0x43D5EE, Biome.WARM_OCEAN);
        register(0x45ADF2, Biome.LUKEWARM_OCEAN, Biome.DEEP_LUKEWARM_OCEAN);
        register(0x3D57D6, Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN);
        register(0x3938C9, Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN);
        register(0x617B64, Biome.SWAMP);
        register(0x3A7A6A, Biome.MANGROVE_SWAMP);
        register(0x3938C9, Biome.FROZEN_RIVER);
        register(0x3D57D6, Biome.SNOWY_BEACH);
        register(0x0E4ECF, Biome.MEADOW);
        register(0x5DB7EF, Biome.CHERRY_GROVE);
        register(0x905957,
                Biome.NETHER_WASTES,
                Biome.SOUL_SAND_VALLEY,
                Biome.CRIMSON_FOREST,
                Biome.WARPED_FOREST,
                Biome.BASALT_DELTAS
        );
        register(0x62529E,
                Biome.THE_END,
                Biome.SMALL_END_ISLANDS,
                Biome.END_MIDLANDS,
                Biome.END_HIGHLANDS,
                Biome.END_BARRENS
        );
    }

    private Location getTargetedLocation(@NotNull Player player, double range, Predicate<Block> predicate) {
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();

        Location location = origin.clone();
        Vector vec = direction.normalize().multiply(0.2);

        for(double i = 0.0F; i < range; i += 0.2) {
            location.add(vec);
            Block block = location.getBlock();
            if (!predicate.test(block) || WaterAbility.isBendableWaterTempBlock(block) || EarthAbility.isBendableEarthTempBlock(block)) {
                location.subtract(vec);
                break;
            }
        }

        return location;
    }

    public @NotNull Vector getRandom() {
        double pitch = ThreadLocalRandom.current().nextDouble(-90.0, 90.0);
        double yaw = ThreadLocalRandom.current().nextDouble(-180.0, 180.0);
        return new Vector(-Math.cos(pitch) * Math.sin(yaw), -Math.sin(pitch), Math.cos(pitch) * Math.cos(yaw));
    }

    public BlockDisplay getBlock() {
        return block;
    }

    public boolean isFormed() {
        return isFormed;
    }

    public int getDisksAmount() {
        return disks_amount;
    }
}
