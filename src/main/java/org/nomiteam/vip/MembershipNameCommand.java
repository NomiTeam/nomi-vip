package org.nomiteam.vip;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/** 商户 %player% 名称适配；LP 已知账号可离线授予，保存回执复用 UUID 入口。 */
final class MembershipNameCommand implements CommandExecutor {
    private final Function<String, CompletableFuture<UUID>> identity;
    private final CommandExecutor grants;
    private final Consumer<Runnable> dispatch;
    private final Consumer<Throwable> report;

    MembershipNameCommand(Function<String, CompletableFuture<UUID>> identity, CommandExecutor grants,
                          Consumer<Runnable> dispatch, Consumer<Throwable> report) {
        this.identity = identity;
        this.grants = grants;
        this.dispatch = dispatch;
        this.report = report;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("此会员发货入口仅供本地控制台使用。");
            return true;
        }
        if (args.length != 3 || !args[0].matches("[A-Za-z0-9_]{1,16}")
                || !Set.of("vip", "svip", "mvip").contains(args[1])
                || !(args[2].equals("30") || args[2].equals("90"))) {
            sender.sendMessage("用法：nomivipgrantname <LP已知账号名称> <vip|svip|mvip> <30|90>");
            return true;
        }
        // 固定本次参数，不阻塞控制台/服务器线程等待存储。
        String name = args[0], group = args[1], days = args[2];
        try {
            identity.apply(name).whenComplete((id, failure) -> {
                try {
                    dispatch.accept(() -> {
                        if (failure != null || id == null) {
                            sender.sendMessage("会员发货请求被拒绝：LP账号未知、解析失败或映射歧义，未执行会员变更。");
                            if (failure != null) report.accept(failure);
                            return;
                        }
                        grants.onCommand(sender, command, label, new String[]{id.toString(), group, days});
                    });
                } catch (RuntimeException dispatchFailure) {
                    report.accept(dispatchFailure);
                }
            });
        } catch (RuntimeException failure) {
            sender.sendMessage("会员发货请求被拒绝：LP身份解析失败，未执行会员变更。");
            report.accept(failure);
        }
        return true;
    }
}
