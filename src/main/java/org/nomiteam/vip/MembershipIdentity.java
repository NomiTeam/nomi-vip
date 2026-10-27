package org.nomiteam.vip;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.model.user.UserManager;

/** LP 的已知账号映射；不推算离线 UUID、不访问外部名称服务。 */
final class MembershipIdentity {
    private final UserManager users;

    MembershipIdentity(UserManager users) {
        this.users = users;
    }

    CompletableFuture<UUID> resolve(String name) {
        try {
            return users.lookupUniqueId(name).thenCompose(id -> {
                if (id == null) {
                    return CompletableFuture.failedFuture(new IllegalArgumentException("LuckPerms 未知账号名称"));
                }
                return users.lookupUsername(id).thenApply(knownName -> {
                    // LP 名称不区分大小写；反向核对防止旧名称/不一致映射。
                    if (knownName == null || !knownName.equalsIgnoreCase(name)) {
                        throw new IllegalArgumentException("LuckPerms 名称映射不一致，未执行会员变更");
                    }
                    for (var loaded : users.getLoadedUsers()) {
                        if (name.equalsIgnoreCase(loaded.getUsername()) && !id.equals(loaded.getUniqueId())) {
                            throw new IllegalArgumentException("LuckPerms 名称映射存在歧义，未执行会员变更");
                        }
                    }
                    return id;
                });
            });
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
