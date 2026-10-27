package org.nomiteam.vip;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeBuilderRegistry;
import org.bukkit.command.ConsoleCommandSender;

public final class QrOfflineTest {
    static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static void main(String[] args) throws Exception {
        var builders = (NodeBuilderRegistry) Class.forName(
                "me.lucko.luckperms.common.api.implementation.ApiNodeBuilderRegistry")
                .getField("INSTANCE").get(null);
        for (String group : List.of("vip", "svip", "mvip")) {
            for (String days : List.of("30", "90")) {
                for (String mode : List.of("offline", "online", "reconnect", "save-failure")) {
                    var grantFixture = new MembershipGrantTest.Fixture(builders);
                    var uniqueIdLookup = new CompletableFuture<UUID>();
                    var usernameLookup = new CompletableFuture<String>();
                    List<String> consoleMessages = new ArrayList<>();
                    List<Throwable> errors = new ArrayList<>();
                    var users = MembershipGrantTest.proxy(UserManager.class, (method, arguments) -> switch (method) {
                        case "lookupUniqueId" -> {
                            assert arguments[0].equals("KnownBuyer");
                            yield uniqueIdLookup;
                        }
                        case "lookupUsername" -> {
                            assert arguments[0].equals(ID);
                            yield usernameLookup;
                        }
                        case "getLoadedUsers" -> Set.of();
                        default -> throw new AssertionError(method);
                    });
                    var console = MembershipGrantTest.proxy(ConsoleCommandSender.class, (method, arguments) -> {
                        if (method.equals("sendMessage")) {
                            consoleMessages.add((String) arguments[0]);
                        }
                        return null;
                    });
                    var viewer = new MembershipFeedbackTest.Viewer();
                    viewer.current = true;
                    var audience = new MembershipFeedback.Audience() {
                        public UUID id() {
                            return ID;
                        }

                        public boolean current() {
                            return viewer.current;
                        }

                        public void dispatch(Runnable task) {
                            viewer.dispatch(task);
                        }

                        public void message(String text) {
                            viewer.message(text);
                        }
                    };
                    var uuidCommand = new MembershipCommand(grantFixture.service(),
                            target -> mode.equals("offline") ? null : audience, errors::add, Clock.systemUTC());
                    List<Runnable> dispatchedTasks = new ArrayList<>();
                    var nameCommand = new MembershipNameCommand(new MembershipIdentity(users)::resolve,
                            uuidCommand, dispatchedTasks::add, errors::add);
                    nameCommand.onCommand(console, null, "nomivipgrantname",
                            new String[]{"KnownBuyer", group, days});

                    // Both identity queries must complete before dispatch can start the UUID grant.
                    assert grantFixture.adds == 0 && grantFixture.saves == 0 && dispatchedTasks.isEmpty();
                    uniqueIdLookup.complete(ID);
                    assert grantFixture.adds == 0 && dispatchedTasks.isEmpty();
                    usernameLookup.complete("knownbuyer");
                    assert grantFixture.adds == 0 && dispatchedTasks.size() == 1;
                    dispatchedTasks.removeFirst().run();
                    assert grantFixture.adds == 1 && grantFixture.saves == 1 && viewer.tasks.isEmpty();
                    assert grantFixture.requested.getKey().equals("group." + group);

                    // An unavailable/stale audience must not cancel saving; failed saves cannot report success.
                    if (mode.equals("reconnect")) {
                        viewer.current = false;
                    }
                    if (mode.equals("save-failure")) {
                        grantFixture.saved.completeExceptionally(new IllegalStateException("storage failed"));
                    } else {
                        grantFixture.saved.complete(null);
                    }
                    viewer.flush();
                    if (mode.equals("online")) {
                        assert viewer.messages.size() == 1 && viewer.messages.getFirst().contains("成功，");
                    }
                    if (mode.equals("offline") || mode.equals("reconnect")) {
                        assert viewer.messages.isEmpty();
                    }
                    if (mode.equals("save-failure")) {
                        assert viewer.messages.stream().noneMatch(text -> text.contains("§a")) && errors.size() == 1;
                    }
                    assert consoleMessages.isEmpty();
                }
            }
        }

        for (String mode : List.of("unknown", "lookup-failure", "reverse-failure", "reverse-null",
                "mismatch", "ambiguous", "sync-failure")) {
            var conflictingUser = MembershipGrantTest.proxy(User.class, (method, arguments) -> switch (method) {
                case "getUsername" -> "KnownBuyer";
                case "getUniqueId" -> UUID.randomUUID();
                default -> throw new AssertionError(method);
            });
            var users = MembershipGrantTest.proxy(UserManager.class, (method, arguments) -> switch (method) {
                case "lookupUniqueId" -> {
                    if (mode.equals("sync-failure")) {
                        throw new IllegalStateException("lookup failed");
                    }
                    yield mode.equals("lookup-failure")
                            ? CompletableFuture.failedFuture(new IllegalStateException("backend"))
                            : CompletableFuture.completedFuture(mode.equals("unknown") ? null : ID);
                }
                case "lookupUsername" -> mode.equals("reverse-failure")
                        ? CompletableFuture.failedFuture(new IllegalStateException("backend"))
                        : CompletableFuture.completedFuture(mode.equals("reverse-null") ? null
                                : mode.equals("mismatch") ? "Different" : "KnownBuyer");
                case "getLoadedUsers" -> mode.equals("ambiguous") ? Set.of(conflictingUser) : Set.of();
                default -> throw new AssertionError(method);
            });
            int[] grantCalls = {0};
            List<String> rejectionMessages = new ArrayList<>();
            List<Throwable> errors = new ArrayList<>();
            var console = MembershipGrantTest.proxy(ConsoleCommandSender.class, (method, arguments) -> {
                if (method.equals("sendMessage")) {
                    rejectionMessages.add((String) arguments[0]);
                }
                return null;
            });
            var nameCommand = new MembershipNameCommand(new MembershipIdentity(users)::resolve,
                    (sender, command, label, commandArguments) -> {
                        grantCalls[0]++;
                        return true;
                    }, Runnable::run, errors::add);
            nameCommand.onCommand(console, null, "nomivipgrantname", new String[]{"KnownBuyer", "vip", "30"});

            // Invalid identity must produce an explicit rejection before any grant is invoked.
            assert grantCalls[0] == 0 && rejectionMessages.size() == 1
                    && rejectionMessages.getFirst().contains("拒绝") && errors.size() == 1 : mode;
        }
        System.out.println("PASS QR six plans: offline known LP name -> pending lookup/reverse -> dispatch -> UUID "
                + "load/grant/save; online payer-only success, reconnect suppressed, save failure no success");
        System.out.println("PASS QR identities: unknown, failed forward/reverse lookup, missing/mismatched reverse, "
                + "conflicting loaded UUID, synchronous lookup failure explicitly rejected");
    }
}
