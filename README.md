# NomiVIP

最小会员飞行开关与饭团会员发货接口，面向 Paper/Purpur 26.3 及兼容实体调度 API 的 Folia 服务端。运行包只依赖服务端 API 和可选 LuckPerms 服务，不包含第三方依赖。

| 命令 | 契约 |
| --- | --- |
| `/nuomifly`、`/fly` | 玩家切换自己的当前连接会话，不接受目标或其他参数 |
| `nomivipgrant <UUID> <vip\|svip\|mvip> <30\|90>` | 可信本地控制台按固定档期授予并保存 LP 临时组，供现有饭团菜单使用 |

飞行必须主动开启且持续具有有效 `nuomi.vip.fly.global`。权限失效或主动关闭时，只撤销本开关授予的飞行状态；再次获得权限需重新开启。未开启的会话不改原生飞行 flags，创造和旁观模式保持原生行为。每个连接的权限复核、命令和状态写入均使用其实体调度器；退出清理该连接，旧连接任务不能操作重连会话。关闭自授飞行提供一次、最长十秒的摔落保护。停用时只取消任务，不从全局线程修改玩家；替换插件使用完整重启，不热卸载。

体型由 [TrMenu 菜单](config/TrMenu/menus/VipControl.yml) 直接执行原生 `minecraft:attribute %player_name% minecraft:scale base set <比例>`。复原开放；VIP允许±10/20%，SVIP允许±10/20/30%，MVIP允许±10/20/30/40%。菜单按有效等级节点提供入口。控制台目标固定为点击者占位符，不给玩家 attribute 权限；没有体型轮询或到期强制复原。该文件是完整控制菜单，付款页及价格、期限不在迁移范围。

Residence 使用 [LP 原生权限配置](config/luckperms-flight.txt) 配合飞行开关，领地 fly/nofly 行为由 Residence 自己处理。配置仅记录 MVIP 在 `server=lobby`、`server=survival` 下的 `residence.bypass.fly`；不授予 `residence.bypass.nofly`，不移除或降低 VIP/SVIP 的既有权益。主插件没有 Residence API、领地判定、DH 反射或双跳适配。

饭团发货保留可信 UUID 异步 `loadUser`、过期节点清理、同档有效期限累加及 `saveUser` 回执。离线或无法通知不阻止保存；成功消息仅在保存完成后发给当次捕获且仍有效的本人连接。此接口不扣费，不提供钱包、订单去重、重放或退款，保存回执不证明支付原子性或跨服同步。扫码沿用支付系统原生命令与提示，本插件不注册扫码名称桥。

原生 PlayerWarp 2.8.1-free 使用 [创建菜单](config/TrMenu/menus/WarpCreateNative.yml) 按 PAPI `%playerWarp_num%` 当前持有数及有效权限分流免费额度 2/4/6，删除后按当前持有数重新判断。免费路径通过控制台仅为点击者执行 `playerwarp:playerWarp create`；收费路径进入原生创建页，沿用 20 饭团或 2000 米粒及原生名称输入。主菜单创建入口接此页，并保留已有地标列表入口。

[warpNumber 片段](config/PlayerWarp/warpNumber.fragment.yml) 只用于合并对应配置节，不是完整数据库配置，不能覆盖数据库或整份配置。收费路径使用 LP 临时容量权限，例如 `lp user %player_uuid% permission settemp playerWarp.number.menu_session true 2m server=lobby`；另一服将 `server=lobby` 改为 `server=survival`。这不保证真人流程、并发原子性或 LP 命令在延迟后已完成。TrMenu 原生菜单先收集条件结果再执行动作，不能将同一 YAML 动作树中位于 LP 命令后的条件当作执行期复核。

PlayerWarp 历史配额和报价源码保持原样；[compat](compat/README.md) 仅为保留的历史生成器，与原生配置和主插件构建独立。

使用 JDK 25 和 Python 3 离线构建，以 Java 21 字节码输出；依赖由构建者提供：

```sh
python3 build.py --deps-dir "$NOMI_DEPS_DIR" --lp-jar "$LUCKPERMS_INPUT"
```

需要 Purpur API 26.3、对应基础库和实际 LuckPerms 5.5.85 JAR。默认输出 `build/NomiVIP-1.0.0.jar`，可用 `--output-dir` 指定目录。构建只运行最小飞行及保留发货入口的定向测试，检查运行包不含测试或第三方类；不运行历史兼容生成器。离线测试不替代目标服务端的菜单点击与实体调度验证。
