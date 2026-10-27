package org.nomiteam.vip;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import me.lucko.luckperms.common.api.implementation.ApiUserManager;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.plugin.bootstrap.LuckPermsBootstrap;
import me.lucko.luckperms.common.storage.Storage;
import me.lucko.luckperms.common.storage.implementation.StorageImplementation;

/** Actual LP API -> Storage -> async executor; only backend/host scheduler are doubles. */
public final class QrLookupRuntimeTest {
    static Object schedulerTree(Class<?> type, Executor executor) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("async")) {
                        return executor;
                    }
                    return schedulerTree(method.getReturnType(), executor);
                });
    }

    public static void main(String[] args) {
        for (String mode : List.of("known-offline", "unknown", "backend-failure")) {
            List<Runnable> pendingTasks = new ArrayList<>();
            Storage[] storageHolder = {null};
            var plugin = MembershipGrantTest.proxy(LuckPermsPlugin.class, (method, arguments) -> switch (method) {
                case "getStorage" -> storageHolder[0];
                case "getBootstrap" -> schedulerTree(LuckPermsBootstrap.class, pendingTasks::add);
                default -> throw new AssertionError(method);
            });
            var backend = MembershipGrantTest.proxy(StorageImplementation.class, (method, arguments) -> {
                if (mode.equals("backend-failure")) {
                    throw new IllegalStateException("lookup backend offline");
                }
                return switch (method) {
                    case "getPlayerUniqueId" -> {
                        assert arguments[0].equals("KnownBuyer");
                        yield mode.equals("unknown") ? null : QrOfflineTest.ID;
                    }
                    case "getPlayerName" -> {
                        assert arguments[0].equals(QrOfflineTest.ID);
                        yield "KnownBuyer";
                    }
                    default -> throw new AssertionError(method);
                };
            });
            storageHolder[0] = new Storage(plugin, backend);

            // These queries use real LP Storage futures without a loaded User or manager cache.
            var users = new ApiUserManager(plugin, null);
            var uniqueIdLookup = users.lookupUniqueId("KnownBuyer");
            assert !uniqueIdLookup.isDone();
            pendingTasks.removeFirst().run();

            if (mode.equals("backend-failure")) {
                MembershipGrantTest.expectFailure(uniqueIdLookup);
            } else if (mode.equals("unknown")) {
                assert uniqueIdLookup.join() == null;
            } else {
                assert uniqueIdLookup.join().equals(QrOfflineTest.ID);
                var usernameLookup = users.lookupUsername(uniqueIdLookup.join());
                assert !usernameLookup.isDone();
                pendingTasks.removeFirst().run();
                assert usernameLookup.join().equals("KnownBuyer");
            }
        }
        System.out.println("PASS actual LP5.5.85 ApiUserManager/Storage: asynchronous offline forward/reverse lookup "
                + "without loaded cache, unknown null and backend exception");
    }
}
