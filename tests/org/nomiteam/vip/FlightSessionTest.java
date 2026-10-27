package org.nomiteam.vip;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Exercises the real session against a player boundary; the host supplies its entity thread. */
public final class FlightSessionTest {
    private static final class PlayerState {
        boolean permission;
        boolean allowFlight;
        boolean flying;
        boolean entityThread;
        GameMode mode = GameMode.SURVIVAL;
        int flagWrites;
        final List<String> messages = new ArrayList<>();
        final Player player = MembershipGrantTest.proxy(Player.class, (method, arguments) -> {
            assert entityThread : "Player access must remain in entity context: " + method;
            return switch (method) {
                case "getGameMode" -> mode;
                case "hasPermission" -> {
                    assert arguments[0].equals("nuomi.vip.fly.global");
                    yield permission;
                }
                case "getAllowFlight" -> allowFlight;
                case "isFlying" -> flying;
                case "setAllowFlight" -> {
                    allowFlight = (boolean) arguments[0];
                    flagWrites++;
                    yield null;
                }
                case "setFlying" -> {
                    flying = (boolean) arguments[0];
                    flagWrites++;
                    yield null;
                }
                case "setFallDistance" -> null;
                case "sendMessage" -> {
                    messages.add((String) arguments[0]);
                    yield null;
                }
                default -> throw new AssertionError("Unexpected API: " + method);
            };
        });

        void onEntity(Runnable action) {
            entityThread = true;
            try {
                action.run();
            } finally {
                entityThread = false;
            }
        }
    }

    public static void main(String[] args) {
        for (boolean allowed : List.of(false, true)) {
            for (boolean flying : List.of(false, true)) {
                var state = new PlayerState();
                state.allowFlight = allowed;
                state.flying = flying;
                var session = new FlightSession(state.player);
                state.onEntity(session::enforce);
                assert state.flagWrites == 0 : "Inactive native/restored flags must remain untouched";
                state.onEntity(session::toggle);
                assert state.flagWrites == 0 : "Denied command must not clear native flight";
                state.permission = true;
                state.onEntity(session::enforce);
                assert state.flagWrites == 0 : "Permission alone must not opt in";
            }
        }

        var member = new PlayerState();
        member.permission = true;
        var session = new FlightSession(member.player);
        member.onEntity(session::toggle);
        assert member.allowFlight && member.flying;
        int enabledWrites = member.flagWrites;
        member.onEntity(session::enforce);
        assert member.flagWrites == enabledWrites : "Active enforcement must not fight other plugins";
        member.permission = false;
        member.onEntity(session::enforce);
        assert !member.allowFlight && !member.flying;
        assert session.protectFall() && !session.protectFall() : "One fall grace after stopping owned flight";
        member.permission = true;
        member.onEntity(session::enforce);
        assert !member.allowFlight && !member.flying : "Re-grant must require another toggle";
        member.onEntity(session::toggle);
        member.onEntity(session::toggle);
        assert !member.allowFlight && !member.flying : "Explicit off removes owned flight";

        for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
            var nativePlayer = new PlayerState();
            nativePlayer.mode = mode;
            nativePlayer.allowFlight = true;
            nativePlayer.flying = true;
            var nativeSession = new FlightSession(nativePlayer.player);
            nativePlayer.onEntity(nativeSession::toggle);
            nativePlayer.onEntity(nativeSession::enforce);
            assert nativePlayer.flagWrites == 0 : "Native game modes must not be changed";

            var transitioningPlayer = new PlayerState();
            transitioningPlayer.permission = true;
            var transitioningSession = new FlightSession(transitioningPlayer.player);
            transitioningPlayer.onEntity(transitioningSession::toggle);
            transitioningPlayer.mode = mode;
            transitioningPlayer.permission = false;
            int beforeModeChange = transitioningPlayer.flagWrites;
            transitioningPlayer.onEntity(transitioningSession::enforce);
            transitioningPlayer.onEntity(transitioningSession::stop);
            assert transitioningPlayer.flagWrites == beforeModeChange : "Native mode takes over flight";
        }

        for (boolean alreadyFlying : List.of(false, true)) {
            var nativeFlight = new PlayerState();
            nativeFlight.permission = true;
            nativeFlight.allowFlight = true;
            nativeFlight.flying = alreadyFlying;
            var nativeSession = new FlightSession(nativeFlight.player);
            nativeFlight.onEntity(nativeSession::toggle);
            nativeFlight.permission = false;
            nativeFlight.onEntity(nativeSession::enforce);
            assert nativeFlight.allowFlight : "Pre-existing mayfly belongs to its native source";
            assert nativeFlight.flying == alreadyFlying : "Only flight started by this toggle is stopped";
        }

        var reconnecting = new PlayerState();
        reconnecting.permission = true;
        var oldSession = new FlightSession(reconnecting.player);
        reconnecting.onEntity(oldSession::toggle);
        reconnecting.onEntity(oldSession::stop);
        var newSession = new FlightSession(reconnecting.player);
        reconnecting.onEntity(newSession::enforce);
        assert !reconnecting.allowFlight && !reconnecting.flying : "Quit clears owned state; join is inactive";
        System.out.println("PASS minimal flight: opt-in, live revoke/off, no auto-resume, native modes/flags preserved, "
                + "entity-only player access, reconnect and one-use fall grace");
    }
}
