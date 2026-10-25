package org.nomiteam.vip;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.model.data.TemporaryNodeMergeStrategy;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.node.types.InheritanceNode;

/** 仅负责 LP 临时组变更及保存回执，不处理费用。 */
final class MembershipGrant {
    record Receipt(UUID player, String group, int days, Instant expiry) {}

    private final UserManager users;
    private final NodeBuilderRegistry builders;
    private final GroupManager groups;
    private final Clock clock;

    MembershipGrant(UserManager users, NodeBuilderRegistry builders, GroupManager groups, Clock clock) {
        this.users = users;
        this.builders = builders;
        this.groups = groups;
        this.clock = clock;
    }

    CompletableFuture<Receipt> grant(UUID player, String group, int days) {
        try {
            if (player == null || !Set.of("vip", "svip", "mvip").contains(group)
                    || (days != 30 && days != 90)) {
                throw new IllegalArgumentException("仅允许固定会员组及30/90天");
            }
            if (groups.getGroup(group) == null) {
                throw new IllegalStateException("固定会员组不存在，未执行会员变更");
            }
            // 付款目标是可信 UUID；loadUser 支持离线及缓存未就绪，不能依赖消息连接。
            return users.loadUser(player).thenCompose(user -> {
                if (user == null || !player.equals(user.getUniqueId())) {
                    throw new IllegalStateException("购买者 LP 用户加载失败或身份不符");
                }
                // 与旧 LP 命令一致：先清理过期节点，再累加仍有效的同档期限。
                user.auditTemporaryNodes();
                var node = builders.forInheritance().group(group)
                        .expiry(clock.instant().plus(Duration.ofDays(days))).build();
                // 实际5.5.85的 accumulate 对应此枚举，不是名为 ACCUMULATE 的枚举。
                var mutation = user.data().add(node, TemporaryNodeMergeStrategy.ADD_NEW_DURATION_TO_EXISTING);
                var merged = mutation.getMergedNode();
                if (!mutation.getResult().wasSuccessful()
                        || !(merged instanceof InheritanceNode inheritance)
                        || !group.equals(inheritance.getGroupName()) || !merged.getValue()
                        || !merged.getContexts().isEmpty() || !merged.hasExpiry()
                        || merged.getExpiry().isBefore(node.getExpiry())) {
                    throw new IllegalStateException("本次会员临时组变更未成功确认");
                }
                var receipt = new Receipt(player, group, days, merged.getExpiry());
                // 不吞保存异常；节点事件、旧权限和命令派发返回值均不代替本次保存回执。
                return users.saveUser(user).thenApply(ignored -> receipt);
            });
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
