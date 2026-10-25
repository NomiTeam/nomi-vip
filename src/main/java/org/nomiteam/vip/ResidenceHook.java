package org.nomiteam.vip;

import org.bukkit.entity.Player;
import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.containers.ResAdmin;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.bekvon.bukkit.residence.permissions.PermissionManager.ResPerm;

/** Uses current location and current native permissions, never stale ResidenceFlyState metadata. */
final class ResidenceHook {
    record State(boolean claimFlight, boolean noFly, String claim) {
    }

    static State read(Player p) {
        var r = Residence.getInstance();
        if (r == null || !r.isEnabled() || r.isDisabledWorldListener(p.getLocation())) {
            return new State(false, false, "disabled");
        }
        var claim = r.getResidenceManager().getByLoc(p.getLocation());
        var perms = FlagPermissions.getPerms(p.getLocation(), p);
        boolean noFly = Flags.nofly.isGlobalyEnabled() && perms.has(Flags.nofly, false) && !ResAdmin.isResAdmin(p) && !ResPerm.bypass_nofly.hasPermission(p, 10000L, new String[0]) && (claim == null || !claim.isOwner(p));
        boolean fly = claim != null && claim.containsLoc(p.getLocation()) && Flags.fly.isGlobalyEnabled() && claim.getPermissions().playerHas(p, Flags.fly, false);
        return new State(fly && !noFly, noFly, claim == null ? "none" : claim.getName());
    }
}
