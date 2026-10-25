package org.nomiteam.vip;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

/** 只操作当前玩家实体；LP 的有效权限是唯一会员来源，不缓存等级。 */
final class ScaleControl {
    private ScaleControl() {
    }

    private static int tier(Player player) {
        if (player.hasPermission("nuomi.vip.mvip")) {
            return 3;
        }
        if (player.hasPermission("nuomi.vip.svip")) {
            return 2;
        }
        return player.hasPermission("nuomi.vip.vip") ? 1 : 0;
    }
    // 必须由调用方在玩家实体调度上下文执行，菜单不能传入其他玩家。
    static void change(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage("§e用法：/nuomiscale <比例>");
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[0]);
        } catch (NumberFormatException exception) {
            player.sendMessage("§c请输入有效的体型比例。");
            return;
        }
        if (!VipPolicy.scaleAllowed(value, tier(player))) {
            player.sendMessage("§c你的会员等级尚未解锁这个体型。");
            return;
        }
        var attribute = player.getAttribute(Attribute.SCALE);
        if (attribute == null) {
            player.sendMessage("§c当前服务器不支持体型调整。");
            return;
        }
        attribute.setBaseValue(value);
        player.sendMessage("§a体型已调整为正常体型的 " + Math.round(value * 100) + "%。");
    }

    static void enforce(Player player) {
        var attribute = player.getAttribute(Attribute.SCALE);
        if (attribute != null && !VipPolicy.scaleAllowed(attribute.getBaseValue(), tier(player))) {
            attribute.setBaseValue(1);
        }
    }
}
