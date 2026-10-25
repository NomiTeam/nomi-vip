package org.nomiteam.vip;

import java.util.*;

public final class PolicyChecks {
    public static final List<String> CASES = List.of("scale-reset", "scale-tiers", "scale-invalid", "scale-nodes", "flight-opt-in", "flight-revoked", "flight-restored", "flight-native", "flight-claim", "flight-double-jump", "warp-quotas", "warp-deletion", "warp-excess");
    static void check(boolean b, String why) {
        if (!b) {
            throw new AssertionError(why);
        }
    }

    public static void run(String name) {
        switch (name) {
            case "scale-reset" -> {
                for (int t = 0; t<4; t++) {
                    check(VipPolicy.scaleAllowed(1, t), "reset "+t);
                }
            }
            case "scale-tiers" -> {
                int[] maximums = {0, 20, 30, 40};
                for (int tier = 0; tier < maximums.length; tier++) {
                    for (int delta = 10; delta <= 50; delta += 10) {
                        for (int sign : new int[] {-1, 1}) {
                            check(VipPolicy.scaleAllowed(1 + sign * delta / 100.0, tier) == (delta <= maximums[tier]), "tier " + tier + " delta " + delta);
                        }
                    }
                }
            }
            case "scale-invalid" -> {
                double[] invalid = {
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    .15, .3, .5, 1.05, 2, 3, 5
                };
                for (double value : invalid) {
                    for (int tier = 0; tier < 4; tier++) {
                        check(!VipPolicy.scaleAllowed(value, tier), "invalid " + value);
                    }
                }
            }
            case "scale-nodes" -> {
                check(!VipPolicy.scaleAllowed(.8, 0), "scale node alone must not grant tier");
                check(VipPolicy.scaleAllowed(.8, 1), "tier grants without scale node, as legacy");
            }
            case "flight-opt-in" -> {
                check(!FlightPolicy.decide(false, true, false, false, false).continuous(), "permission alone");
                check(FlightPolicy.decide(true, true, false, false, false).continuous(), "session opt in");
            }
            case "flight-revoked" -> {
                var d = FlightPolicy.decide(true, false, false, false, false);
                check(!d.continuous() && !d.keepSession(), "revoked active cleared");
            }
            case "flight-restored" -> {
                for (boolean global : new boolean[] {false, true}) {
                    for (boolean flying : new boolean[] {false, true}) {
                        var d = FlightPolicy.decide(false, global, false, false, false);
                        check(!d.continuous() && !d.allowFlight(), "restored true,"+flying+" must be cleared");
                    }
                }
            }
            case "flight-native" -> {
                for (boolean active : new boolean[] {false, true}) {
                    check(FlightPolicy.decide(active, false, true, false, false).continuous(), "creative/spectator");
                }
            }
            case "flight-claim" -> {
                check(FlightPolicy.decide(false, false, false, true, false).continuous(), "native claim");
                check(!FlightPolicy.decide(false, false, false, false, false).continuous(), "outside claim");
            }
            case "flight-double-jump" -> {
                var d = FlightPolicy.decide(false, false, false, false, true);
                check(d.allowFlight() && !d.continuous(), "double jump keeps mayfly only");
            }
            case "warp-quotas" -> {
                check(WarpPolicy.quota(false, false, false) == 0, "none");
                check(WarpPolicy.quota(true, false, false) == 2, "2");
                check(WarpPolicy.quota(true, true, false) == 4, "4");
                check(WarpPolicy.quota(true, true, true) == 6, "6");
            }
            case "warp-deletion" -> {
                check(WarpPolicy.price(4, 4, 123) == 123, "full");
                check(WarpPolicy.price(4, 3, 123) == 0, "delete releases slot");
            }
            case "warp-excess" -> {
                for (int q : new int[] {0, 2, 4, 6}) {
                    for (int n = 0; n<9; n++) {
                        check(WarpPolicy.price(q, n, 123) == (n<q ? 0 : 123), "price/count");
                        check(WarpPolicy.remaining(q, n) == Math.max(0, q-n), "remaining");
                    }
                }
            }
            default -> throw new IllegalArgumentException(name);
        }
    }

    public static void main(String[] args) {
        for (String n : args.length == 0 ? CASES : List.of(args)) {
            run(n);
            System.out.println("PASS "+n);
        }
    }
}
