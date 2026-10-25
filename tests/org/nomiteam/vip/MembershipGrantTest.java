package org.nomiteam.vip;

import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import net.luckperms.api.model.data.*;
import net.luckperms.api.model.user.*;
import net.luckperms.api.model.group.*;
import net.luckperms.api.node.*;
import net.luckperms.api.node.types.InheritanceNode;

public final class MembershipGrantTest {
    public static void main(String[] args) throws Exception {
        var builders = (NodeBuilderRegistry) Class.forName(
                "me.lucko.luckperms.common.api.implementation.ApiNodeBuilderRegistry")
                .getField("INSTANCE").get(null);
        run(builders);
    }
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, method, args) -> call.apply(method.getName(), args));
    }

    static final class Fixture {
        UUID actual = SELF;
        int adds;
        int saves;
        boolean changed = true;
        boolean throwAdd;
        boolean throwSave;
        boolean missingGroup;
        boolean missingUser;
        Node merged;
        Node requested;
        TemporaryNodeMergeStrategy strategy;
        final CompletableFuture<Void> saved = new CompletableFuture<>();
        final NodeMap data = proxy(NodeMap.class, (name, args) -> {
            if (!name.equals("add")) throw new AssertionError(name);
            adds++;
            if (throwAdd) throw new IllegalStateException("mutation failure");
            requested = (Node) args[0];
            strategy = (TemporaryNodeMergeStrategy) args[1];
            return new DataMutateResult.WithMergedNode() {
                public DataMutateResult getResult() {
                    return changed ? DataMutateResult.SUCCESS : DataMutateResult.FAIL_ALREADY_HAS;
                }
                public Node getMergedNode() { return merged == null ? requested : merged; }
            };
        });
        final User user = proxy(User.class, (name, args) -> switch (name) {
            case "getUniqueId" -> actual;
            case "data" -> data;
            case "auditTemporaryNodes" -> null;
            default -> throw new AssertionError(name);
        });
        final UserManager users = proxy(UserManager.class, (name, args) -> switch (name) {
            case "getUser" -> {
                assert args[0].equals(SELF);
                yield missingUser ? null : user;
            }
            case "loadUser" -> {
                assert args[0].equals(SELF);
                yield CompletableFuture.completedFuture(missingUser ? null : user);
            }
            case "saveUser" -> {
                assert args[0] == user;
                saves++;
                if (throwSave) throw new IllegalStateException("sync save failure");
                yield saved;
            }
            default -> throw new AssertionError(name);
        });
        final NodeBuilderRegistry builders;
        final GroupManager groups = proxy(GroupManager.class, (name, args) -> {
            if (!name.equals("getGroup")) throw new AssertionError(name);
            return missingGroup ? null : proxy(Group.class, (method, values) -> args[0]);
        });
        Fixture(NodeBuilderRegistry builders) { this.builders = builders; }
        MembershipGrant service() { return new MembershipGrant(users, builders, groups, Clock.fixed(NOW, ZoneOffset.UTC)); }
    }

    // 真正的 LP builder 由独立运行器传入，不用自造节点来验证期限与上下文。
    public static void run(NodeBuilderRegistry builders) {
        Fixture f = new Fixture(builders);
        var result = f.service().grant(SELF, "vip", 30);
        assert f.adds == 1 : "缺失本次 LP 临时组变更";
        assert f.saves == 1 : "缺失 LP 保存";
        assert !result.isDone() : "不能把变更/排队当保存成功";
        assert f.strategy == TemporaryNodeMergeStrategy.ADD_NEW_DURATION_TO_EXISTING;
        assert f.requested.getExpiry().equals(NOW.plus(Duration.ofDays(30)));
        assert f.requested.getContexts().isEmpty();
        f.saved.complete(null);
        var receipt = result.join();
        assert receipt.player().equals(SELF) && receipt.group().equals("vip");
        assert receipt.expiry().equals(f.requested.getExpiry());

        Fixture renewal = new Fixture(builders);
        renewal.merged = builders.forInheritance().group("svip")
                .expiry(NOW.plus(Duration.ofDays(117))).build();
        var renewed = renewal.service().grant(SELF, "svip", 90);
        renewal.saved.complete(null);
        assert renewed.join().expiry().equals(renewal.merged.getExpiry()) : "回执必须使用 LP 累加后的期限";
        assert renewal.strategy == TemporaryNodeMergeStrategy.ADD_NEW_DURATION_TO_EXISTING;

        Fixture failed = new Fixture(builders);
        var failure = failed.service().grant(SELF, "mvip", 30);
        failed.saved.completeExceptionally(new IllegalStateException("storage offline"));
        expectFailure(failure);
        for (String mode : List.of("unchanged", "wrong-user", "add-throw", "save-throw", "bad-merged", "missing-group", "missing-user")) {
            Fixture bad = new Fixture(builders);
            switch (mode) {
                case "unchanged" -> bad.changed = false;
                case "wrong-user" -> bad.actual = UUID.randomUUID();
                case "add-throw" -> bad.throwAdd = true;
                case "save-throw" -> bad.throwSave = true;
                case "missing-group" -> bad.missingGroup = true;
                case "missing-user" -> bad.missingUser = true;
                case "bad-merged" -> bad.merged = builders.forInheritance().group("mvip")
                        .expiry(NOW.plusSeconds(10)).build();
            }
            expectFailure(bad.service().grant(SELF, "vip", 30));
            assert bad.saves == (mode.equals("save-throw") ? 1 : 0) : mode;
            if (Set.of("wrong-user", "missing-group", "missing-user").contains(mode)) assert bad.adds == 0;
        }
        Fixture invalid = new Fixture(builders);
        expectFailure(invalid.service().grant(SELF, "admin", 30));
        expectFailure(invalid.service().grant(SELF, "vip", 31));
        assert invalid.adds == 0 && invalid.saves == 0;
        System.out.println("PASS membership mutation/save: pending, success, renewal receipt, unchanged, wrong UUID, exceptions, strict terms");
    }

    static void expectFailure(CompletableFuture<?> future) {
        try { future.join(); throw new AssertionError("应失败而非成功"); }
        catch (CompletionException expected) { /* 真实异步失败不产生成功回执。 */ }
    }
}
