package org.nomiteam.vip;

/** Legacy tier boundaries; scale permission nodes are descriptive, not independent grants. */
public final class VipPolicy {
    private VipPolicy() {
    }

    public static boolean scaleAllowed(double value, int tier) {
        if (!Double.isFinite(value)) {
            return false;
        }
        if (Math.abs(value-1)<0.00001) {
            return true;
        }
        double delta = Math.abs(value-1)*100;
        int percent = (int)Math.round(delta);
        return Math.abs(delta-percent)<0.00001 && percent%10 == 0 && percent>=10 && percent<=switch (tier) {
            case 1 -> 20;
            case 2 -> 30;
            case 3 -> 40;
            default -> 0;
        };
    }
}
