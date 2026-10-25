package org.nomiteam.vip;

public final class TransitionTest {
    public static void main(String[] args) {
        String n = args[0];
        switch (n) {
            case "restored" -> {
                for (boolean f : new boolean[] {false, true}) {
                    var a = FlightTransition.plan(false, true, false, false, false, false, true, f);
                    assert !a.allowFlight() && !a.flying();
                    assert a.fallGrace() == f;
                }
            }
            case "off" -> {
                var a = FlightTransition.plan(false, true, false, false, false, false, true, true);
                assert !a.flying() && !a.allowFlight() && a.fallGrace();
            }
            case "revoke" -> {
                var a = FlightTransition.plan(true, false, false, false, false, false, true, true);
                assert !a.active() && !a.flying() && a.fallGrace();
            }
            case "claim" -> {
                var a = FlightTransition.plan(false, false, false, true, false, false, true, true);
                assert a.allowFlight() && a.flying() && !a.fallGrace();
            }
            case "native" -> {
                var a = FlightTransition.plan(false, false, true, false, false, true, true, true);
                assert a.allowFlight() && a.flying();
            }
            case "jump" -> {
                var a = FlightTransition.plan(false, false, false, false, true, false, true, true);
                assert a.allowFlight() && !a.flying() && a.fallGrace();
            }
            case "nofly" -> {
                var a = FlightTransition.plan(true, true, false, true, true, true, true, true);
                assert a.active() && !a.allowFlight() && !a.flying();
            }
            case "quote" -> {
                QuoteBook b = new QuoteBook();
                assert !b.consume("p", "vault", 0, 0);
                b.offer("p", "vault", 0, 0);
                assert b.consume("p", "vault", 0, 1);
                assert !b.consume("p", "vault", 0, 2);
            }
            case "stale-quote" -> {
                QuoteBook b = new QuoteBook();
                b.offer("p", "point", 0, 0);
                assert !b.consume("p", "point", 10, 1);
                b.offer("p", "vault", 5, 0);
                assert !b.consume("p", "vault", 5, QuoteBook.TTL+1);
            }
            default -> throw new AssertionError(n);
        }
        System.out.println("PASS "+n);
    }
}
