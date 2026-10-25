package org.nomiteam.vip;

import java.time.*;
import java.util.*;
import org.bukkit.command.*;
import net.luckperms.api.node.NodeBuilderRegistry;

public final class MembershipCommandTest {
    public static void main(String[] args) throws Exception {
        var builders = (NodeBuilderRegistry) Class.forName(
                "me.lucko.luckperms.common.api.implementation.ApiNodeBuilderRegistry")
                .getField("INSTANCE").get(null);
        var console = MembershipGrantTest.proxy(ConsoleCommandSender.class, (name, values) -> null);
        var player = MembershipGrantTest.proxy(CommandSender.class, (name, values) -> null);
        var fixture = new MembershipGrantTest.Fixture(builders);
        var viewer = new MembershipFeedbackTest.Viewer();
        // Fixture的固定UUID与本次购买者相同；通知只允许这一个接收者。
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        MembershipFeedback.Audience audience = new MembershipFeedback.Audience() {
            public UUID id() { return id; }
            public boolean current() { return viewer.current; }
            public void dispatch(Runnable task) { viewer.dispatch(task); }
            public void message(String text) { viewer.message(text); }
        };
        List<Throwable> errors = new ArrayList<>();
        var command = new MembershipCommand(fixture.service(), target -> target.equals(id) ? audience : null,
                errors::add, Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC));
        String[] valid = {id.toString(), "vip", "30"};
        command.onCommand(player, null, "nomivipgrant", valid);
        assert fixture.adds == 0 : "玩家不能调用发货";
        for (String[] invalid : List.of(new String[]{}, new String[]{id.toString(), "vip", "030"},
                new String[]{"1-1-1-1-1", "vip", "30"},
                new String[]{id.toString(), "admin", "30"},
                new String[]{id.toString(), "vip", "30", "extra"})) {
            command.onCommand(console, null, "nomivipgrant", invalid);
        }
        assert fixture.adds == 0 && fixture.saves == 0;
        command.onCommand(console, null, "nomivipgrant", valid);
        assert fixture.adds == 1 && fixture.saves == 1;
        assert viewer.tasks.isEmpty() : "未保存完成不得通知";
        fixture.saved.complete(null);
        viewer.flush();
        assert viewer.messages.size() == 1 && viewer.messages.getFirst().contains("成功，");
        assert errors.isEmpty() : "非法参数应在调用LP之前拒绝";

        var failed = new MembershipGrantTest.Fixture(builders);
        viewer.messages.clear();
        viewer.tasks.clear();
        var failingCommand = new MembershipCommand(failed.service(), target -> audience, errors::add,
                Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC));
        failingCommand.onCommand(console, null, "nomivipgrant", valid);
        failed.saved.completeExceptionally(new IllegalStateException("storage failure"));
        viewer.flush();
        assert viewer.messages.size() == 1 && viewer.messages.getFirst().contains("未能确认成功");
        for (String group : List.of("vip", "svip", "mvip")) {
            for (String days : List.of("30", "90")) {
                var named = new MembershipGrantTest.Fixture(builders);
                viewer.messages.clear();
                viewer.tasks.clear();
                viewer.current = true;
                var uuidCommand = new MembershipCommand(named.service(), target -> audience, errors::add,
                        Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC));
                var nameCommand = new MembershipNameCommand(name -> name.equals("Buyer_1") ? id : null,
                        uuidCommand);
                String[] namedArgs = {"Buyer_1", group, days};
                nameCommand.onCommand(player, null, "nomivipgrantname", namedArgs);
                for (String[] invalid : List.of(new String[]{"Offline", group, days},
                        new String[]{"buyer_1", group, days}, new String[]{"%player%", group, days},
                        new String[]{"Buyer_1", group}, new String[]{"Buyer_1", "admin", days})) {
                    nameCommand.onCommand(console, null, "nomivipgrantname", invalid);
                }
                assert named.adds == 0 : "名称入口必须拒绝玩家、离线、错名和非法参数";
                nameCommand.onCommand(console, null, "nomivipgrantname", namedArgs);
                assert named.adds == 1 && named.saves == 1;
                assert viewer.tasks.isEmpty() : "扫码发货派发不等于保存成功";
                named.saved.complete(null);
                viewer.flush();
                viewer.flush();
                assert viewer.messages.size() == 1;
                assert viewer.messages.getFirst().contains(group.toUpperCase(Locale.ROOT));
                assert viewer.messages.getFirst().contains(days + " 天");
            }
        }
        for (String mode : List.of("save-failure", "reconnect", "disabled", "late")) {
            var named = new MembershipGrantTest.Fixture(builders);
            var clock = new MembershipFeedbackTest.Time();
            viewer.current = true;
            viewer.messages.clear();
            viewer.tasks.clear();
            var uuidCommand = new MembershipCommand(named.service(), target -> audience, errors::add, clock);
            var nameCommand = new MembershipNameCommand(name -> id, uuidCommand);
            nameCommand.onCommand(console, null, "nomivipgrantname", new String[]{"Buyer_1", "vip", "30"});
            if (mode.equals("save-failure")) {
                named.saved.completeExceptionally(new IllegalStateException("storage failure"));
            } else {
                named.saved.complete(null);
            }
            if (mode.equals("reconnect") || mode.equals("disabled")) viewer.current = false;
            if (mode.equals("late")) clock.now = clock.now.plusSeconds(300);
            viewer.flush();
            viewer.flush();
            if (mode.equals("save-failure")) {
                assert viewer.messages.size() == 1 && viewer.messages.getFirst().contains("未能确认成功");
            } else {
                assert viewer.messages.isEmpty() : mode;
            }
        }
        System.out.println("PASS name adapter: six fixed plans, console only, exact online identity, invalid/offline rejected, LP save pending, one success");
        System.out.println("PASS membership command: console only, canonical UUID, exact args/terms, online self, pending save, success and storage failure delivery");
    }
}
