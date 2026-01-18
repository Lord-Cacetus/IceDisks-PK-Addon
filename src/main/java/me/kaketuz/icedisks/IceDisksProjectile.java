package icedisks;

import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class IceDisksProjectile extends IceAbility {

    public IceDisksProjectile(Player player) {
        super(player);
    }

    @Override
    public void progress() {

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
        return 0;
    }

    @Override
    public String getName() {
        return "IceDisksProjectile";
    }

    @Override
    public Location getLocation() {
        return null;
    }


}
