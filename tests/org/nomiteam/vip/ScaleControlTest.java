package org.nomiteam.vip;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.Player;

/** 验证真实权限查询边界；属性注册表和实体调度仍需服务端验收。 */
public final class ScaleControlTest {
    public static void main(String[] args) throws Exception {
        Set<String> permissions = new HashSet<>();
        int[] messages = {0};
        Player player = (Player) Proxy.newProxyInstance(
        Player.class.getClassLoader(), new Class<?>[] {Player.class},
        (proxy, method, arguments) -> switch (method.getName()) {
            case "hasPermission" -> permissions.contains(arguments[0]);
            case "sendMessage" -> {
                messages[0]++;
                yield null;
            }
            default -> throw new AssertionError("Unexpected player access: " + method.getName());
        });
        var tier = ScaleControl.class.getDeclaredMethod("tier", Player.class);
        tier.setAccessible(true);
        String[] nodes = {"nuomi.vip.vip", "nuomi.vip.svip", "nuomi.vip.mvip"};
        for (int mask = 0; mask < 8; mask++) {
            permissions.clear();
            int expected = 0;
            for (int bit = 0; bit < nodes.length; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    permissions.add(nodes[bit]);
                    expected = bit + 1;
                }
            }
            PolicyChecks.check(tier.invoke(null, player).equals(expected), "effective tier mask " + mask);
        }
        // 撤销后立即重新查询；单独 scale 节点不能冒充会员等级。
        permissions.clear();
        permissions.add("nuomi.vip.scale.40");
        PolicyChecks.check(tier.invoke(null, player).equals(0), "no cached or descriptive tier");
        for (String[] input : new String[][] {
            {
            }, {"1.4"}, {"NaN"}, {"Infinity"}, {"not-a-number"}, {"1", "OtherPlayer"}
        }) {
            int previous = messages[0];
            ScaleControl.change(player, input);
            PolicyChecks.check(messages[0] == previous + 1, "rejected before attribute mutation");
        }
        System.out.println("PASS scale control: live LP nodes, all combinations, revoke, invalid/self-only inputs");
    }
}
