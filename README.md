# NomiVIP

最小会员飞行开关与饭团会员发货接口，面向 Paper/Purpur 26.3 及兼容实体调度 API 的 Folia 服务端。运行包只依赖服务端 API 和可选 LuckPerms 服务，不包含第三方依赖。

| 命令 | 契约 |
| --- | --- |
| `/nuomifly`、`/fly` | 玩家切换自己的当前连接会话，不接受目标或其他参数 |
| `nomivipgrant <UUID> <vip\|svip\|mvip> <30\|90>` | 可信本地控制台按固定档期授予并保存 LP 临时组，供现有饭团菜单使用 |

飞行必须主动开启且持续具有有效 `nuomi.vip.fly.global`。权限失效或主动关闭时，只撤销本开关授予的飞行状态；再次获得权限需重新开启。未开启的会话不改原生飞行 flags，创造和旁观模式保持原生行为。每个连接的权限复核、命令和状态写入均使用其实体调度器；退出清理该连接，旧连接任务不能操作重连会话。关闭自授飞行提供一次、最长十秒的摔落保护。停用时只取消任务，不从全局线程修改玩家；替换插件使用完整重启，不热卸载。

体型由 [TrMenu 菜单](config/TrMenu/menus/VipControl.yml) 直接执行原生 `minecraft:attribute %player_name% minecraft:scale base set <比例>`。复原开放；VIP允许±10/20%，SVIP允许±10/20/30%，MVIP允许±10/20/30/40%。条件在点击执行链内查询有效等级节点，不依赖按钮显示条件。控制台目标固定为点击者占位符，不给玩家 attribute 权限；没有体型轮询或到期强制复原。该文件是完整控制菜单，付款页及价格、期限不在迁移范围。

Residence 使用 [LP 原生权限配置](config/luckperms-flight.txt) 配合飞行开关，领地 fly/nofly 行为由 Residence 自己处理。`residence.bypass.fly` 与 `residence.bypass.nofly` 是独立权益，按现有组配置；不自动扩大档位权限。主插件没有 Residence API、领地判定、DH 反射或双跳适配。

饭团发货保留可信 UUID 异步 `loadUser`、过期节点清理、同档有效期限累加及 `saveUser` 回执。离线或无法通知不阻止保存；成功消息仅在保存完成后发给当次捕获且仍有效的本人连接。此接口不扣费，不提供钱包、订单去重、重放或退款，保存回执不证明支付原子性或跨服同步。扫码沿用支付系统原生命令与提示，本插件不注册扫码名称桥。

PlayerWarp 配额和报价源码保持原样；[compat](compat/README.md) 为保留的历史生成器，与主插件构建独立。

使用 JDK 25 和 Python 3 离线构建，以 Java 21 字节码输出；依赖由构建者提供：

```sh
python3 build.py --deps-dir "$NOMI_DEPS_DIR" --lp-jar "$LUCKPERMS_INPUT"
```

需要 Purpur API 26.3、对应基础库和实际 LuckPerms 5.5.85 JAR。默认输出 `build/NomiVIP-1.0.0.jar`，可用 `--output-dir` 指定目录。构建只运行最小飞行及保留发货入口的定向测试，检查运行包不含测试或第三方类；不运行历史兼容生成器。离线测试不替代目标服务端的菜单点击与实体调度验证。
