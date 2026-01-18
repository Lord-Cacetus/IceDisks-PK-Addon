package icedisks;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.BlockSource;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class IceDisks extends IceAbility implements AddonAbility {

    public static final String current_version = "1.0.0-D";

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

    //commands
    private String command_help_author_hover;
    private String command_help_version_hover;

    private String command_help_description;
    private String command_help_instruction;

    //source
    private double source_range;
    private double source_radius;
    private int aoe_sources_amount;
    private boolean allow_source_water;
    private boolean aoe_allowed;
    private int aoe_source_interval;
    private double aoe_source_speed;
    private long sources_revert_time;
    private double aoe_source_angular_speed;

    //disk block
    private int disks_amount;
    private long form_speed;
    private double disappear_radius;

    //other
    private long cooldown;

    private boolean isFormed;


    public enum SourceMode {
        SOURCE_FOUND, AOE_VARIANT
    }


    private SourceMode mode;
    private BlockDisplay block;
    private int current_sources;
    private int interval;
    private double verticalVelocity;


    private final Set<Pair<Location, Vector>> SOURCE_STREAMS = new HashSet<>();

    private Listener listener;

    private boolean water_flag;




    public IceDisks(Player player) {
        super(player);
        command_help_author_hover = ConfigManager.getConfig().getString("KaketuZ_Abilities.IceDisks.Help.HoverAuthor");
        command_help_version_hover = ConfigManager.getConfig().getString("KaketuZ_Abilities.IceDisks.Help.HoverVersion");
        command_help_description = ConfigManager.getConfig().getString("KaketuZ_Abilities.IceDisks.Help.Description");
        command_help_instruction = ConfigManager.getConfig().getString("KaketuZ_Abilities.IceDisks.Help.Instruction");
        source_range = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Source.Range");
        aoe_allowed = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Allowed");
        source_radius = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Radius");
        aoe_sources_amount = ConfigManager.getConfig().getInt("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.SourcesAmount");
        aoe_source_interval = ConfigManager.getConfig().getInt("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Interval");
        aoe_source_speed = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.Speed");
        sources_revert_time = ConfigManager.getConfig().getLong("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.SourcesRevertTime");
        aoe_source_angular_speed = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Source.AOE_Variant.AngularSpeed");
        allow_source_water = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Source.AllowWaterSource");
        disks_amount = ConfigManager.getConfig().getInt("KaketuZ_Abilities.IceDisks.DisksAmount");
        disappear_radius = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.DisappearRadius");
        form_speed = ConfigManager.getConfig().getLong("KaketuZ_Abilities.IceDisks.FormSpeed");
        cooldown = ConfigManager.getConfig().getLong("KaketuZ_Abilities.IceDisks.Cooldown");

        if (!bPlayer.canBendIgnoreBinds(this) || hasAbility(player, IceDisks.class)) return;

        final Block source = BlockSource.getWaterSourceBlock(player, source_range, allow_source_water, true, false);

        if (source == null) {
            if (!aoe_allowed) return;
            Location target = getTargetedLocation(player, source_range, Block::isPassable);
            block = target.getWorld().spawn(target, BlockDisplay.class, bd -> {
                bd.setBlock(Material.ICE.createBlockData());
                bd.setTransformation(new Transformation(
                        new Vector3f(),
                        new Quaternionf(),
                        new Vector3f(1, 0, 1),
                        new Quaternionf()
                ));
            });
            mode = SourceMode.AOE_VARIANT;
        }
        else {
            Location relative = source.getRelative(BlockFace.UP, 1).getLocation();
            block = source.getWorld().spawn(relative, BlockDisplay.class, bd -> {
                bd.setBlock(switch(source.getType()) {
                    case PACKED_ICE -> Material.PACKED_ICE.createBlockData();
                    case BLUE_ICE -> Material.BLUE_ICE.createBlockData();
                    default -> Material.ICE.createBlockData();
                });
                bd.setTransformation(new Transformation(
                        new Vector3f(),
                        new Quaternionf(),
                        new Vector3f(1, 0, 1),
                        new Quaternionf()
                ));
            });
            mode = SourceMode.SOURCE_FOUND;
        }

        start();
    }

    @Override
    public void progress() {
        if (RegionProtection.isRegionProtected(player, block.getLocation(), this)) {
            remove();
            return;
        }
        if (player.isDead() || !player.isOnline() || !bPlayer.canBendIgnoreBinds(this)) {
            bPlayer.addCooldown(this);
            remove();
            return;
        }
        if (block.getLocation().distance(player.getLocation()) >= disappear_radius) {
            bPlayer.addCooldown(this);
            remove();
            return;
        }
        if (!block.getWorld().equals(player.getWorld())) {
            bPlayer.addCooldown(this);
            remove();
            return;
        }

        if (!isFormed) {
            switch (mode) {
                case AOE_VARIANT -> {
                    if (!player.isSneaking()) {
                        bPlayer.addCooldown(this, getCooldown() / 2L);
                        remove();
                        return;
                    }

                    Location target = getTargetedLocation(player, source_range, Block::isPassable);

                    if (System.currentTimeMillis() > getStartTime() + interval && SOURCE_STREAMS.size() < 10) {
                        Block closestBlock = null;
                        double minDistanceSq = Double.MAX_VALUE;
                        Location targetLoc = target.clone();

                        for (Block b : GeneralMethods.getBlocksAroundPoint(player.getLocation(), source_radius)) {
                            Material type = b.getType();
                            BlockData data = b.getBlockData();
                            boolean isValid = (isWater(b) || isPlant(b) || isSnow(b) || isIce(b) ||
                                    WOODEN_BLOCKS.contains(type) || data instanceof Waterlogged ||
                                    type == Material.WATER_CAULDRON || type == Material.WET_SPONGE) && type != Material.MANGROVE_ROOTS;

                            if (isValid) {
                                double distSq = b.getLocation().distanceSquared(targetLoc);
                                if (distSq < minDistanceSq) {
                                    minDistanceSq = distSq;
                                    closestBlock = b;
                                }
                            }
                        }
                        if (closestBlock != null) {
                            Material type = closestBlock.getType();
                            BlockData data = closestBlock.getBlockData();
                            BlockData newData = Material.AIR.createBlockData();

                            if (type == Material.WET_SPONGE) {
                                closestBlock.setType(Material.SPONGE);
                            } else if (type == Material.WATER_CAULDRON && data instanceof Levelled level) {
                                if (level.getLevel() > 1) {
                                    level.setLevel(level.getLevel() - 1);
                                    closestBlock.setBlockData(level);
                                } else {
                                    closestBlock.setType(Material.CAULDRON);
                                }
                            }
                            if (WOODEN_BLOCKS.contains(type)) {
                                newData = Material.MANGROVE_ROOTS.createBlockData();
                                closestBlock.getWorld().playSound(closestBlock.getLocation(), Sound.ENTITY_CREAKING_DEATH, 0.5f, 0);
                                closestBlock.getWorld().playSound(closestBlock.getLocation(), Sound.ENTITY_ZOMBIE_CONVERTED_TO_DROWNED, 0.25f, 1);
                            } else if (isPlant(closestBlock) && closestBlock.getType() != Material.MANGROVE_ROOTS) {
                                if (GeneralMethods.getMCVersion() >= 1215) {
                                    newData = Material.TALL_DRY_GRASS.createBlockData();
                                }
                            } else if (isWater(closestBlock) && data instanceof Levelled level) {
                                if (level.getLevel() < 7) {
                                    level.setLevel(level.getLevel() + 1);
                                    newData = level;
                                }
                            } else if (isIce(closestBlock)) {
                                Levelled water = (Levelled) Material.WATER.createBlockData();
                                water.setLevel(1);
                                newData = water;
                            } else if (isSnow(closestBlock) && data instanceof Levelled level) {
                                if (level.getLevel() > 1) {
                                    level.setLevel(level.getLevel() - 1);
                                    newData = level;
                                }
                            }

                            new TempBlock(closestBlock, newData, sources_revert_time);

                            Location bLoc = closestBlock.getLocation();
                            Vector direction = GeneralMethods.getDirection(bLoc, targetLoc).normalize().multiply(aoe_source_speed);

                            SOURCE_STREAMS.add(new Pair<>(bLoc, direction));
                        }
                        interval += aoe_source_interval;
                    }

                    block.setTeleportDuration(2);
                    target.setPitch(0);
                    block.teleport(target);

                    final Iterator<Pair<Location, Vector>> iterator = SOURCE_STREAMS.iterator();
                    while (iterator.hasNext()) {
                        Pair<Location, Vector> pair = iterator.next();
                        Vector desired = target.toVector()
                                .subtract(pair.left.toVector())
                                .normalize()
                                .multiply(aoe_source_speed);
                        pair.right.add(desired.clone()
                                .subtract(pair.right)
                                .multiply(aoe_source_angular_speed));


                        pair.left.add(pair.right);

                        if (pair.left.distance(target) < 0.5) {
                            pair.left.getWorld().playSound(pair.left, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.2f, 2);
                            pair.left.getWorld().spawnParticle(Particle.SNOWFLAKE, pair.left, 3, 0.5, 0.5, 0.5, 0.05);
                            current_sources++;
                            iterator.remove();
                            continue;
                        }

                        for (int i = 0; i < 5; i++) {
                            Vector rand = getRandom().multiply(0.25);
                            pair.left.getWorld().spawnParticle(Particle.FISHING, pair.left.clone().add(rand), 0, pair.right.getX(), pair.right.getY(), pair.right.getZ(), 0.25);
                        }
                        Biome biome = pair.left.getBlock().getBiome();
                        pair.left.getWorld().spawnParticle(Particle.DUST, pair.left, 5, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.fromRGB(getWaterColor(biome)), 1));

                        if (getRunningTicks() % 10 == 0) {
                            playWaterbendingSound(pair.left);
                        }
                        RayTraceResult result = pair.left.getWorld().rayTraceBlocks(pair.left, pair.right, pair.right.length(), FluidCollisionMode.NEVER, true);
                        Optional.ofNullable(result)
                                .map(RayTraceResult::getHitBlock)
                                .filter(GeneralMethods::isSolid)
                                .filter(b -> !b.getType().equals(Material.MANGROVE_ROOTS))
                                .ifPresent(b -> iterator.remove());

                    }
                    Transformation transform = block.getTransformation();
                    if (transform.getScale().y < 1) {
                        float y = (float) current_sources / aoe_sources_amount;
                        transform.getScale().set(new Vector3f(1, Math.min(1, y), 1));
                        block.setTransformation(transform);
                    } else isFormed = true;
                }
                case SOURCE_FOUND -> {
                    Transformation transform = block.getTransformation();
                    if (transform.getScale().y < 1) {
                        float y = (float) (System.currentTimeMillis() - getStartTime()) / form_speed;
                        block.setInterpolationDuration(2);
                        block.setInterpolationDelay(0);
                        block.setTransformation(new Transformation(
                                new Vector3f(),
                                new Quaternionf(),
                                new Vector3f(1, Math.min(1, y), 1),
                                new Quaternionf()
                        ));
                    } else isFormed = true;
                }
            }
        } else {
            Location loc = block.getLocation();
            Location centerLoc = loc.clone().add(0.5, 0.5, 0.5);
            Block centerBlock = centerLoc.getBlock();
            boolean inWater = isWater(centerBlock);

            if (inWater && !water_flag) {
                if (verticalVelocity <= -0.5) {
                    bigSplashEffects(centerBlock);
                } else if (verticalVelocity < 0) {
                    smallSplashEffects(centerBlock);
                }
                water_flag = true;
            }

            if (inWater) {
                if (Math.abs(verticalVelocity) > 0.05) {
                    centerLoc.getWorld().spawnParticle(Particle.BUBBLE, centerLoc, 3, 0.2, 0.2, 0.2, 0.02);
                }

                verticalVelocity *= 0.85;

                Block blockAbove = centerBlock.getRelative(BlockFace.UP);
                if (isWater(blockAbove)) {
                    verticalVelocity = Math.min(verticalVelocity + 0.03, 0.1);
                } else {

                    double targetY = centerBlock.getY() + 0.6;
                    double diff = targetY - loc.getY();
                    verticalVelocity += diff * 0.05;

                    if (getRunningTicks() % 20 == 0 && Math.abs(diff) > 0.1) {
                        smallSplashEffects(centerBlock);
                    }
                }

                Block floor = loc.clone().subtract(0, 0.1, 0).getBlock();
                if (!floor.isPassable() && !isWater(floor)) {
                    verticalVelocity = 0.15;
                }
            } else {
                water_flag = false;
                Block blockBelow = loc.clone().subtract(0, 0.05, 0).getBlock();
                if (blockBelow.isPassable()) {
                    verticalVelocity -= 0.04;
                } else {
                    if (verticalVelocity < 0) {
                        verticalVelocity = 0;

                        Location add = block.getLocation().add(0.5, 0.5, 0.5);

                        add.getWorld().playSound(add, Sound.BLOCK_BASALT_BREAK, 0.5f, 0);

                        for (int i = 0; i < 20; i++) {
                            Vector rand = getRandom().setY(0).normalize().multiply(0.5);
                            add.getWorld().spawnParticle(Particle.ITEM, add.clone().add(rand), 0, rand.getX(), rand.getY() + 0.5, rand.getZ(), 0.4, new ItemStack(i % 2 == 0 ? block.getBlock().getMaterial() : blockBelow.getType()));
                        }

                        for (int i = 0; i < 40; i++) {
                            Vector rand = getRandom().setY(0).normalize().multiply(0.5);
                            add.getWorld().spawnParticle(Particle.DUST_PLUME, add.clone().add(rand), 0, rand.getX(), rand.getY(), rand.getZ(), 0.1);
                        }

                        loc.setY(blockBelow.getY() + 1.01);
                        block.teleport(loc);

                        return;
                    }
                }
            }

            verticalVelocity = Math.max(Math.min(verticalVelocity, 0.4), -1.0);

            if (Math.abs(verticalVelocity) > 0.001) {
                block.setTeleportDuration(2);
                block.teleport(loc.add(0, verticalVelocity, 0));
            }
        }
    }

    private void bigSplashEffects(Block water) {
        water.getWorld().playSound(water.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1, 1);

        for (int i = 0; i < 40; i++) {
            Vector rand = getRandom().setY(0).normalize().multiply(1.5);
            water.getWorld().spawnParticle(Particle.FISHING, water.getLocation().add(rand), 0, 0, 1, 0, 0.4);
            if (i % 2 == 0) {
                water.getWorld().spawnParticle(Particle.SNOWFLAKE, water.getLocation().add(rand), 0, 0, 1, 0, 0.4);
            }
        }
        for (int i = 0; i < 40; i++) {
            Vector rand = getRandom().setY(0).normalize().multiply(1.5);
            water.getWorld().spawnParticle(Particle.SPLASH, water.getLocation().add(0, 1, 0).add(rand), 0, Math.random() / 2, 1, Math.random() / 2, 0.4);
        }

        for (int i = -180; i < 180; i++) {
            Vector orthogonal = GeneralMethods.getOrthogonalVector(new Vector(0, 1, 0), i, 0.1).normalize();
            Location add = water.getLocation().add(0, 1, 0).add(orthogonal);
            water.getWorld().spawnParticle(Particle.FISHING, add, 0, orthogonal.getX(), orthogonal.getY(), orthogonal.getZ(), 0.25);
        }
    }

    private void smallSplashEffects(Block water) {
        for (int i = 0; i < 5; i++) {
            Vector rand = getRandom().setY(0).normalize().multiply(1.5);
            water.getWorld().spawnParticle(Particle.SPLASH, water.getLocation().add(0, 1, 0).add(rand), 0, Math.random() / 2, 1, Math.random() / 2, 0.1);
        }
    }

    @Override
    public void remove() {
        super.remove();
        block.remove();
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
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Help.HoverAuthor", "Click to see more of my work on ProjectKorra!");
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Help.HoverVersion", "Click to see source code on Github!");
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
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.FormSpeed", 1500);
        ConfigManager.getConfig().addDefault("KaketuZ_Abilities.IceDisks.Cooldown", 9000);
        ConfigManager.defaultConfig.save();

        ProjectKorra.plugin.getComponentLogger().info(Component.text("IceDisks ability by KaketuZ successfully loaded! Thanks for installing!", NamedTextColor.GREEN));
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(listener);
    }

    @Override
    public String getAuthor() {
        final Component author = Component.text("❄️ KᴀᴋᴇᴛᴜZ", TextColor.color(0x9CC6FF))
                .hoverEvent(HoverEvent.showText(Component.text(command_help_author_hover)
                        .decorate(TextDecoration.UNDERLINED)
                        .color(TextColor.color(0x51FF))))
                .clickEvent(ClickEvent.openUrl("https://projectkorra.com/forum/resources/authors/lord_cacetus_.28072/"));
        return LegacyComponentSerializer.legacySection().serialize(author);
    }

    @Override
    public String getVersion() {
        final Component version = Component.text("IceDisks", TextColor.color(0xB5D8FF), TextDecoration.BOLD)
                .append(Component.text(" v" + current_version, NamedTextColor.GREEN))
                .hoverEvent(HoverEvent.showText(Component.text(command_help_version_hover)
                        .decorate(TextDecoration.UNDERLINED)
                        .color(TextColor.color(0x51FF))))
                .clickEvent(ClickEvent.openUrl("https://projectkorra.com/forum/resources/authors/lord_cacetus_.28072/")); //TODO: изменить ссылку на будущий github
        return LegacyComponentSerializer.legacySection().serialize(version);
    }

    @Override
    public String getDescription() {
        return command_help_description;
    }

    @Override
    public String getInstructions() {
        return command_help_instruction;
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

        for(double i = (double)0.0F; i < range; i += 0.2) {
            location.add(vec);
            Block block = location.getBlock();
            if (!predicate.test(block) || WaterAbility.isBendableWaterTempBlock(block) || EarthAbility.isBendableEarthTempBlock(block)) {
                location.subtract(vec);
                break;
            }
        }

        return location;
    }

    private @NotNull Vector getRandom() {
        double pitch = ThreadLocalRandom.current().nextDouble(-90.0, 90.0);
        double yaw = ThreadLocalRandom.current().nextDouble(-180.0, 180.0);
        return new Vector(-Math.cos(pitch) * Math.sin(yaw), -Math.sin(pitch), Math.cos(pitch) * Math.cos(yaw));
    }
}
