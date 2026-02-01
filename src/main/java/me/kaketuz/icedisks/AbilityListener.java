package me.kaketuz.icedisks;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.AbilityEndEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class AbilityListener implements Listener {

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

        if (bPlayer == null || event.isCancelled() || !event.isSneaking()) return;

        if (bPlayer.getBoundAbilityName().equals("IceDisks")) new IceDisks(player);
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);

        if (bPlayer == null) return;

        if (bPlayer.getBoundAbilityName().equals("IceDisks") && CoreAbility.hasAbility(player, IceDisks.class)) {
            IceDisks disks = CoreAbility.getAbility(player, IceDisks.class);
            if (!bPlayer.isOnCooldown("IceDisksProjectile") && disks.isFormed()) {
                disks.shoot();
            }
        }

        if (player.isSneaking()) {
            boolean can_player_dodge = ConfigManager.getConfig().getBoolean("KaketuZ_Abilities.IceDisks.Disk.Dodging.Player");
            double range = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Disk.Dodging.PlayerDodgeRange");
            double collision_radius = ConfigManager.getConfig().getDouble("KaketuZ_Abilities.IceDisks.Disk.Dodging.PlayerDodgeCollisionRadius");

            if (!can_player_dodge) return;

            RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getLocation().getDirection(), range, collision_radius, e -> e instanceof BlockDisplay);
            Optional.ofNullable(result)
                    .map(RayTraceResult::getHitEntity)
                    .map(e -> (BlockDisplay) e)
                    .flatMap(e -> CoreAbility.getAbilities(IceDisksProjectile.class).stream()
                            .filter(idp -> idp.getDisk().equals(e) && !idp.isOutOfRange())
                            .findAny())
                    .ifPresent(idp -> {
                        idp.getDisk().getWorld().playSound(idp.getDisk().getLocation(), Sound.ENTITY_BREEZE_DEFLECT, 1, 0.75f);
                        idp.getDisk().getWorld().playSound(idp.getDisk().getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1, 0.75f);
                        idp.getDisk().getWorld().playSound(idp.getDisk().getLocation(), Sound.ITEM_OMINOUS_BOTTLE_DISPOSE, 1, 1);
                        idp.setOutOfRange(true);
                        idp.setDirection(idp.getDirection().getCrossProduct(new Vector(0, 1, 0)).multiply(ThreadLocalRandom.current().nextDouble(-(idp.getSpeed() * 2), idp.getSpeed() * 2)).add(new Vector(0, 1, 0)).normalize().multiply(idp.getSpeed()));
                    });
        }
    }

}
