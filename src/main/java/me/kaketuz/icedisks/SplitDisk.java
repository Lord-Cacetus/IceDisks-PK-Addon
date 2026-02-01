package me.kaketuz.icedisks;

import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

public class SplitDisk extends IceAbility implements AddonAbility {

    private BlockDisplay split;
    private Vector direction;

    public SplitDisk(Player player, IceDisksProjectile projectile, Vector direction) {
        super(player);
        this.direction = direction;
        split = projectile.getDisk().getWorld().spawn(projectile.getDisk().getLocation().add(0.5, 0.5, 0.5), BlockDisplay.class, bd -> {
            bd.setBlock(projectile.getDisk().getBlock());
            bd.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.5f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(1, projectile.getDisk().getTransformation().getScale().y, 0.5f),
                    new Quaternionf()
            ));
        });
        start();
    }

    @Override
    public void remove() {
        super.remove();
        split.remove();
    }

    @Override
    public void progress() {
        if (!split.isValid()) remove();
        split.setTeleportDuration(2);
        split.teleport(split.getLocation().add(direction).setDirection(direction));

        direction = direction.subtract(new Vector(0, 0.06, 0));

        RayTraceResult result = split.getWorld().rayTraceBlocks(split.getLocation(), direction, direction.length(), FluidCollisionMode.NEVER, true);
        Optional.ofNullable(result)
                .map(RayTraceResult::getHitBlock)
                .ifPresent(b -> {
                    BlockFace face = result.getHitBlockFace();
                    if (face == BlockFace.UP) {
                        remove();
                    }
                    else {
                        assert face != null;
                        direction = Vector.fromJOML(direction.toVector3d().reflect(face.getDirection().toVector3d()));
                    }
                });
    }

    @Override
    public boolean isSneakAbility() {
        return false;
    }

    @Override
    public boolean isHarmlessAbility() {
        return true;
    }

    @Override
    public boolean isHiddenAbility() {
        return true;
    }

    @Override
    public long getCooldown() {
        return 0;
    }

    @Override
    public String getName() {
        return "SplitDisk";
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
}
