package org.nomiteam.vip;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一次请求的通知边界；不保存会员身份或支付状态。 */
final class MembershipFeedback {
    interface Audience {
        UUID id();
        boolean current();
        void dispatch(Runnable message);
        void message(String text);
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private final Audience audience;
    private final Clock clock;
    private final Instant deadline;
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicBoolean delivered = new AtomicBoolean();

    MembershipFeedback(Audience audience, Clock clock) {
        this.audience = audience;
        this.clock = clock;
        // 仅抑制过期回调；不是用固定等待推断成功，也不取消已经开始的 LP 保存。
        deadline = clock.instant().plus(Duration.ofMinutes(5));
    }

    void complete(MembershipGrant.Receipt receipt, Throwable failure) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        if (failure == null && (receipt == null || !audience.id().equals(receipt.player()))) {
            return;
        }
        audience.dispatch(() -> {
            if (!audience.current() || !clock.instant().isBefore(deadline)
                    || (failure == null && !clock.instant().isBefore(receipt.expiry()))
                    || !delivered.compareAndSet(false, true)) {
                return;
            }
            if (failure != null) {
                audience.message("§c会员开通/续期未能确认成功，请勿重复兑换并联系管理员。");
            } else {
                audience.message("§a" + receipt.group().toUpperCase(java.util.Locale.ROOT)
                        + " 会员开通/续期成功，已增加 " + receipt.days() + " 天，有效期至 "
                        + DATE.format(receipt.expiry()) + "（北京时间）。");
            }
        });
    }
}
