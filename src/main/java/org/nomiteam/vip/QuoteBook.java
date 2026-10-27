package org.nomiteam.vip;

import java.util.*;

/** One-use short-lived GUI quote. A changed cost requires reopening before any payment. */
public final class QuoteBook {
    public static final long TTL = 120_000_000_000L;
    private record Quote(long price, long time) {
    }

    private final Map<String, Quote> entries = new HashMap<>();
    private static String key(String p, String c) {
        return p+":"+c;
    }

    public synchronized void offer(String player, String currency, long price, long now) {
        entries.entrySet().removeIf(e -> now-e.getValue().time()>TTL);
        if (entries.size()>=4096) {
            entries.clear();
        }
        entries.put(key(player, currency), new Quote(price, now));
    }

    public synchronized boolean consume(String player, String currency, long price, long now) {
        var q = entries.remove(key(player, currency));
        return q != null && q.price() == price && now-q.time()<=TTL && now>=q.time();
    }
}
