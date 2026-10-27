package org.nomiteam.vip;

import java.time.*;
import java.util.*;

public final class MembershipFeedbackTest {
    static final class Time extends Clock {
        Instant now = Instant.parse("2026-10-26T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    static final class Viewer implements MembershipFeedback.Audience {
        final UUID id = UUID.randomUUID();
        boolean current = true;
        final List<Runnable> tasks = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        public UUID id() { return id; }
        public boolean current() { return current; }
        public void dispatch(Runnable task) { tasks.add(task); }
        public void message(String text) { messages.add(text); }
        void flush() {
            for (var task : List.copyOf(tasks)) task.run();
        }
    }

    static MembershipGrant.Receipt receipt(Viewer viewer, Time time) {
        return new MembershipGrant.Receipt(viewer.id, "vip", 30, time.now.plus(Duration.ofDays(30)));
    }

    public static void main(String[] args) {
        Time time = new Time();
        Viewer self = new Viewer();
        var feedback = new MembershipFeedback(self, time);
        feedback.complete(receipt(self, time), null);
        assert self.messages.isEmpty() : "必须进入玩家调度后才发送";
        feedback.complete(receipt(self, time), null);
        self.flush();
        self.flush();
        assert self.tasks.size() == 1 && self.messages.size() == 1 : "重复完成/调度不得重复提示";
        assert self.messages.getFirst().contains("成功") && self.messages.getFirst().contains("30 天");

        for (String mode : List.of("wrong-self", "expired-callback", "reconnect", "disabled", "expired-membership")) {
            Viewer viewer = new Viewer();
            Time clock = new Time();
            var delivery = new MembershipFeedback(viewer, clock);
            var result = receipt(viewer, clock);
            if (mode.equals("wrong-self")) result = receipt(new Viewer(), clock);
            if (mode.equals("expired-membership")) {
                result = new MembershipGrant.Receipt(viewer.id, "vip", 30, clock.now.minusSeconds(1));
            }
            delivery.complete(result, null);
            if (mode.equals("expired-callback")) clock.now = clock.now.plusSeconds(300);
            if (mode.equals("reconnect") || mode.equals("disabled")) viewer.current = false;
            viewer.flush();
            assert viewer.messages.isEmpty() : mode;
        }
        Viewer failed = new Viewer();
        new MembershipFeedback(failed, new Time()).complete(null, new IllegalStateException("save failed"));
        failed.flush();
        assert failed.messages.size() == 1;
        assert !failed.messages.getFirst().startsWith("§a") : "保存异常不得成功提示";
        assert failed.messages.getFirst().contains("未能确认成功");
        System.out.println("PASS membership feedback: entity dispatch, success, failure, wrong UUID, stale callback, expired membership, reconnect/disable, duplicate");
    }
}
