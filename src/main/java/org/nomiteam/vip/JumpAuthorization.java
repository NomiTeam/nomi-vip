package org.nomiteam.vip;

import org.bukkit.entity.Player;

/** Read-only boundary for a verified double-jump integration. Never grants sustained flying.
* Invoked only on the player's entity scheduler; implementations must be read-only.
*/
public interface JumpAuthorization {
    boolean mayDoubleJump(Player player);
}
