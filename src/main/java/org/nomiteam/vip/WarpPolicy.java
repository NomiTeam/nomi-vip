package org.nomiteam.vip;

public final class WarpPolicy {
    private WarpPolicy() {
    }

    public static int quota(boolean two, boolean four, boolean six) {
        return six ? 6 : four ? 4 : two ? 2 : 0;
    }

    public static int remaining(int quota, int owned) {
        return Math.max(0, quota-owned);
    }

    public static int price(int quota, int owned, int original) {
        return remaining(quota, owned)>0 ? 0 : original;
    }
}
