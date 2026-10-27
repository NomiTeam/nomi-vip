package org.nomiteam.vip;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import me.lucko.luckperms.common.api.implementation.ApiNodeBuilderRegistry;
import me.lucko.luckperms.common.command.utils.ArgumentList;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.storage.Storage;
import me.lucko.luckperms.common.storage.implementation.StorageImplementation;
import net.luckperms.api.model.data.TemporaryNodeMergeStrategy;

/** 实际 LP 内嵌实现的离线回归；存储替身不连接数据库、不保存真实用户。 */
public final class MembershipRuntimeTest {
    private static Object returningTree(Class<?> type, Executor executor) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
            if (method.getName().equals("async")) return executor;
            return returningTree(method.getReturnType(), executor);
        });
    }

    public static void main(String[] args) throws Exception {
        var arguments = new ArgumentList(new ArrayList<>(List.of("accumulate")));
        assert arguments.getTemporaryModifierAndRemove(0).orElseThrow()
                == TemporaryNodeMergeStrategy.ADD_NEW_DURATION_TO_EXISTING;
        assert arguments.isEmpty();
        // 对真实 Storage.future/AsyncInterface.runAsync 注入受控执行器与保存异常。
        for (boolean fail : List.of(false, true)) {
            List<Runnable> pending = new ArrayList<>();
            int[] writes = {0};
            var plugin = (LuckPermsPlugin) returningTree(LuckPermsPlugin.class, pending::add);
            var implementation = MembershipGrantTest.proxy(StorageImplementation.class, (name, values) -> {
                assert name.equals("saveUser") : name;
                writes[0]++;
                if (fail) throw new IllegalStateException("injected backend failure");
                return null;
            });
            var storage = new Storage(plugin, implementation);
            var saved = storage.saveUser(null);
            assert !saved.isDone() && writes[0] == 0;
            pending.getFirst().run();
            assert writes[0] == 1;
            if (fail) MembershipGrantTest.expectFailure(saved);
            else saved.join();
        }
        assert ApiNodeBuilderRegistry.INSTANCE.forInheritance().group("vip").build().getGroupName().equals("vip");
        System.out.println("PASS actual LP5.5.85: accumulate argument mapping; real Storage/AsyncInterface pending, normal completion and backend exception propagation");
    }

}
