package me.kaketuz.icedisks;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import com.projectkorra.projectkorra.airbending.AirSwipe;
import com.projectkorra.projectkorra.airbending.combo.AirSweep;
import com.projectkorra.projectkorra.attribute.markers.DayNightFactor;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.earthbending.EarthBlast;
import com.projectkorra.projectkorra.firebending.FireBlast;
import com.projectkorra.projectkorra.firebending.FireShield;
import com.projectkorra.projectkorra.firebending.WallOfFire;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.waterbending.Torrent;
import com.projectkorra.projectkorra.waterbending.WaterManipulation;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class IceDisksProjectile extends IceAbility implements AddonAbility {


    @DayNightFactor
    private double damage;
    @DayNightFactor
    private double range;
    @DayNightFactor
    private double collision_radius;
    @DayNightFactor
    private double knockback;
    @DayNightFactor
    private double speed;
    @DayNightFactor
    private double angular;
    @DayNightFactor(invert = true)
    private long cooldown;

    private boolean can_water_manipulation_dodge;
    private boolean can_torrent_dodge;
    private boolean can_fire_blast_dodge;
    private boolean can_fire_shield_dodge;
    private boolean can_wall_of_fire_dodge;
    private boolean can_earth_blast_dodge;
    private boolean can_air_swipe_dodge;
    private boolean can_air_sweep_dodge;
    private boolean can_redirect;

    private IceDisks disks;
    private BlockDisplay disk;
    private Location origin, location;
    private Vector direction;
    private boolean is_out_of_range;
    private boolean can_bounce;

    public IceDisksProjectile(Player player, IceDisks disks) {
        super(player);
        this.disks = disks;
        if (!disks.isFormed()) return;
        damage = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Disk.Damage");
        range = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Disk.Range");
        collision_radius = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Disk.CollisionRadius");
        knockback = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Disk.Knockback");
        speed = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Disk.Speed");
        angular = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Disk.AngularRotation");
        cooldown = ConfigManager.getConfig().getLong("KaketuZ_Abilities.IceDisks.Disk.Cooldown");
        can_water_manipulation_dodge = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.Dodging.WaterManipulation");
        can_torrent_dodge = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.Dodging.Torrent");
        can_fire_blast_dodge = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.Dodging.FireBlast");
        can_fire_shield_dodge = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.Dodging.FireShield");
        can_wall_of_fire_dodge = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.Dodging.WallOfFire");
        can_earth_blast_dodge = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.Dodging.EarthBlast");
        can_air_swipe_dodge = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.Dodging.AirSwipe");
        can_air_sweep_dodge = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.Dodging.AirSweep");
        can_redirect = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.RedirectAllowed");
        can_bounce = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.CanBounce");

        Location spawnLoc = disks.getBlock().getLocation().add(0, disks.getBlock().getTransformation().getScale().y, 0);
        disk = spawnLoc.getWorld().spawn(spawnLoc, BlockDisplay.class, bd -> {
           bd.setBlock(disks.getBlock().getBlock());
           bd.setTransformation(new Transformation(
                   new Vector3f(-0.5f, 0, -0.5f),
                   disks.getBlock().getTransformation().getLeftRotation(),
                   new Vector3f(1, (float) 1 / disks.getDisksAmount(), 1),
                   new Quaternionf()
           ));
        });

        origin = spawnLoc.clone();
        location = origin.clone();
        direction = player.getLocation().getDirection().multiply(speed);
        bPlayer.addCooldown(this);
        start();
    }

    @Override
    public void progress() {
        if (!disk.isValid()) remove();

        if (System.currentTimeMillis() < getStartTime() + 500) {
            Vector rand = disks.getRandom().setY(0).normalize();
            location.getWorld().spawnParticle(Particle.SNOWFLAKE, location.clone().add(rand), 0, direction.getX(), direction.getY(), direction.getZ(), speed / 10);
        }

        location = location.add(direction);

        if (!disk.getVelocity().isZero()) direction.add(disk.getVelocity().multiply(disk.getVelocity().length() / 2));


        if (!is_out_of_range) {
            if (origin.distance(location) > range) is_out_of_range = true;
            if (can_redirect) {
                direction = direction.add(player.getLocation().getDirection().multiply(angular)).normalize().multiply(speed);
            }
        }
        else {
            direction = direction.subtract(new Vector(0, 0.08, 0));
        }

        disk.setTeleportDuration(2);
        disk.teleport(location.setDirection(direction));

        RayTraceResult result = location.getWorld().rayTrace(location, direction, direction.length(), FluidCollisionMode.NEVER, true, collision_radius, e -> e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId()));
        Optional.ofNullable(result)
                .ifPresent(res -> {
                    Optional.ofNullable(res.getHitBlock())
                            .ifPresent(b -> {

                                BlockFace face = res.getHitBlockFace();
                                if (is_out_of_range && face != BlockFace.UP && face != BlockFace.DOWN && can_bounce) {
                                    assert face != null;
                                    location.getWorld().spawnParticle(Particle.SNOWFLAKE, location, 5, 0.5, 0.5, 0.5, 0.1);
                                    direction = Vector.fromJOML(direction.toVector3d().reflect(face.getDirection().toVector3d()));
                                    direction.multiply(direction.length() / 2);
                                }
                                else {
                                    Location hit_position = res.getHitPosition().toLocation(location.getWorld());
                                    for (int i = 0; i < 15; i++) {
                                        location.getWorld().spawnParticle(Particle.ITEM, hit_position, 0, ThreadLocalRandom.current().nextDouble(-1, 1), Math.random() / 2, ThreadLocalRandom.current().nextDouble(-1, 1), 0.25, new ItemStack(disk.getBlock().getMaterial()));
                                    }
                                    location.getWorld().spawnParticle(Particle.SNOWFLAKE, hit_position, 15, 0.5, 0.5, 0.5, 0.1);
                                    location.getWorld().playSound(hit_position, Sound.BLOCK_GLASS_BREAK, 1, 1.25f);
                                    remove();
                                }
                            });
                    Optional.ofNullable(res.getHitEntity())
                            .map(e -> (LivingEntity)e)
                            .ifPresent(le -> {
                                for (int i = -180; i < 180; i+=10) {
                                    Vector orthogonal = GeneralMethods.getOrthogonalVector(direction, i, 0.01).normalize();
                                    location.getWorld().spawnParticle(Particle.ITEM, location, 0, orthogonal.getX(), orthogonal.getY(), orthogonal.getZ(), Math.random() / 2, new ItemStack(disk.getBlock().getMaterial()));
                                }
                                location.getWorld().spawnParticle(Particle.SNOWFLAKE, location, 15, 0.5, 0.5, 0.5, 0.1);
                                location.getWorld().playSound(location, Sound.BLOCK_GLASS_BREAK, 1, 1.25f);
                                DamageHandler.damageEntity(le, player, damage, disks);
                                le.setFreezeTicks(20);
                                le.setNoDamageTicks(0);
                                GeneralMethods.setVelocity(disks, le, direction.clone().multiply(knockback));
                                remove();
                            });
                });

        if (RegionProtection.isRegionProtected(player, location, disks)) {
            remove();
        }

        handleCollisions();
    }

    private void handleCollisions() {
        if (can_water_manipulation_dodge) {
            getAbilities(WaterManipulation.class).stream()
                    .filter(isb -> isb.getLocation() != null && isb.getLocation().getWorld().equals(player.getWorld()))
                    .filter(WaterManipulation::isProgressing)
                    .filter(w -> w.getLocation().distance(disk.getLocation()) < 1)
                    .forEach(w -> {
                        w.getLocation().getWorld().spawnParticle(Particle.FALLING_WATER, w.getLocation(), 30, 0.5, 0.5, 0.5, 0);
                        w.getLocation().getWorld().spawnParticle(Particle.SNOWFLAKE, location, 15, 0.5, 0.5, 0.5, 0);
                        w.getLocation().getWorld().spawnParticle(Particle.BLOCK, location, 15, 0.5, 0.5, 0.5, 0, disk.getBlock());
                        w.getLocation().getWorld().playSound(location, Sound.BLOCK_GLASS_BREAK, 1, 1.25f);
                        w.getLocation().getWorld().playSound(location, Sound.ENTITY_AXOLOTL_SPLASH, 1, 1f);
                        w.remove();
                        remove();
                    });
        }
        if (can_torrent_dodge) {
            getAbilities(Torrent.class).stream()
                    .filter(isb -> !isb.getBlocks().isEmpty() && isb.getBlocks().getFirst().getLocation().getWorld().equals(player.getWorld()))
                    .filter(Torrent::isLaunch)
                    .filter(w -> w.getBlocks().stream().anyMatch(b -> b.getLocation().distance(disk.getLocation()) < 1))
                    .forEach(w -> {
                        w.getLocation().getWorld().playSound(location, Sound.BLOCK_GLASS_BREAK, 1, 1.25f);
                        w.getLocation().getWorld().spawnParticle(Particle.SNOWFLAKE, location, 15, 0.5, 0.5, 0.5, 0);
                        w.getLocation().getWorld().spawnParticle(Particle.BLOCK, location, 15, 0.5, 0.5, 0.5, 0, disk.getBlock());
                        remove();
                    });
        }
        if (can_fire_blast_dodge) {
            getAbilities(FireBlast.class).stream()
                    .filter(isb -> isb.getLocation() != null && isb.getLocation().getWorld().equals(player.getWorld()))
                    .filter(w -> w.getLocation().distance(disk.getLocation()) < 1)
                    .forEach(w -> {
                        w.getLocation().getWorld().playSound(location, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1, 1);
                        w.getLocation().getWorld().spawnParticle(Particle.FALLING_WATER, location, 30, 0.5, 0.5, 0.5, 0);
                        w.getLocation().getWorld().spawnParticle(Particle.CLOUD, location, 15, 0.5, 0.5, 0.5, 0);
                        w.remove();
                        remove();
                    });
        }
        if (can_fire_shield_dodge) {
            getAbilities(FireShield.class).stream()
                    .filter(isb -> isb.getLocation() != null && isb.getLocation().getWorld().equals(player.getWorld()))
                    .filter(w -> w.getLocation().distance(disk.getLocation()) < 1)
                    .forEach(w -> {
                        w.getLocation().getWorld().playSound(location, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1, 1);
                        w.getLocation().getWorld().spawnParticle(Particle.FALLING_WATER, location, 30, 0.5, 0.5, 0.5, 0);
                        w.getLocation().getWorld().spawnParticle(Particle.CLOUD, location, 15, 0.5, 0.5, 0.5, 0);
                        remove();
                    });
        }
        if (can_wall_of_fire_dodge) {
            getAbilities(WallOfFire.class).stream()
                    .filter(isb -> !isb.getBlocks().isEmpty() && isb.getBlocks().getFirst().getWorld().equals(player.getWorld()))
                    .filter(w -> w.getBlocks().stream().anyMatch(b -> b.getLocation().distance(disk.getLocation()) < 1))
                    .forEach(w ->{
                        w.getLocation().getWorld().playSound(location, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1, 1);
                        w.getLocation().getWorld().spawnParticle(Particle.FALLING_WATER, location, 30, 0.5, 0.5, 0.5, 0);
                        w.getLocation().getWorld().spawnParticle(Particle.CLOUD, location, 15, 0.5, 0.5, 0.5, 0);
                        remove();
                    });
        }
        if (can_earth_blast_dodge) {
            getAbilities(EarthBlast.class).stream()
                    .filter(isb -> isb.getLocation() != null && isb.getLocation().getWorld().equals(player.getWorld()))
                    .filter(EarthBlast::isProgressing)
                    .filter(w -> w.getLocation().distance(disk.getLocation()) < 1)
                    .forEach(w -> {
                        w.getLocation().getWorld().playSound(location, Sound.BLOCK_GLASS_BREAK, 1, 1.25f);
                        w.getLocation().getWorld().spawnParticle(Particle.DUST, location, 15, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(w.getSourceBlock().getBlockData().getMapColor(), 3));
                        w.getLocation().getWorld().spawnParticle(Particle.BLOCK, location, 15, 0.5, 0.5, 0.5, 0, disk.getBlock());
                        w.remove();
                        remove();
                    });
        }
        if (can_air_swipe_dodge) {
            getAbilities(AirSwipe.class).stream()
                    .filter(isb -> !isb.getLocations().isEmpty() && isb.getLocations().getFirst().getWorld().equals(player.getWorld()))
                    .filter(w -> w.getLocations().stream().anyMatch(l -> l.distance(disk.getLocation()) < 1))
                    .forEach(w -> {
                        w.getLocation().getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1, 1.25f);
                        w.getLocation().getWorld().spawnParticle(Particle.SNOWFLAKE, location, 15, 0.5, 0.5, 0.5, 0);
                        new SplitDisk(player, this, direction.clone().add(direction.getCrossProduct(new Vector(0, 1, 0))));
                        new SplitDisk(player, this, direction.clone().add(direction.getCrossProduct(new Vector(0, 1, 0)).multiply(-1)));
                        remove();
                    });
        }
        if (can_air_sweep_dodge) {
            getAbilities(AirSweep.class).stream()
                    .filter(isb -> !isb.getLocations().isEmpty() && isb.getLocations().getFirst().getWorld().equals(player.getWorld()))
                    .filter(w -> w.getLocations().stream().anyMatch(l -> l.distance(disk.getLocation()) < 1))
                    .forEach(w -> {
                        w.getLocation().getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1, 1.25f);
                        w.getLocation().getWorld().spawnParticle(Particle.SNOWFLAKE, location, 15, 0.5, 0.5, 0.5, 0);
                        new SplitDisk(player, this, direction.clone().add(direction.getCrossProduct(new Vector(0, 1, 0))));
                        new SplitDisk(player, this, direction.clone().add(direction.getCrossProduct(new Vector(0, 1, 0)).multiply(-1)));
                        remove();
                    });
        }
    }

    public IceDisks getDisks() {
        return disks;
    }

    public BlockDisplay getDisk() {
        return disk;
    }

    @Override
    public void remove() {
        super.remove();
        disk.remove();
    }

    @Override
    public boolean isSneakAbility() {
        return false;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public boolean isHiddenAbility() {
        return true;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return "IceDisksProjectile";
    }

    @Override
    public Location getLocation() {
        return null;
    }


    @Override
    public void load() {

    }

    @Override
    public void stop() {

    }

    @Override
    public String getAuthor() {
        return "";
    }

    @Override
    public String getVersion() {
        return "";
    }

    public void setOutOfRange(boolean is_out_of_range) {
        this.is_out_of_range = is_out_of_range;
    }

    public void setDirection(Vector direction) {
        this.direction = direction;
    }

    public Vector getDirection() {
        return direction;
    }

    public boolean isOutOfRange() {
        return is_out_of_range;
    }

    public double getSpeed() {
        return speed;
    }
}
