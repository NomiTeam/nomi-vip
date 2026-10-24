# NomiVIP 1.0.0

独立管理会员体型、当前会话的全图飞行，以及 Residence / PlayerWarp 的会员兼容逻辑。面向 Paper/Purpur 26.3 和提供对应实体调度 API 的 Folia/Lophine 26.3。没有价格、群标识、服务器地址或生产配置。

当前已完成离线编译、领域测试、真实依赖字节码验证及 DeluxeHub 3.8.5 的只读双跳适配；**尚未启动真实服务器验收，不能将当前版本认定为大厅可部署版本**。

使用 JDK 25、Python 3，所有依赖由构建者在外部提供，不下载、不打包第三方 JAR：

```sh
python3 build.py --deps-dir "$NOMI_DEPS_DIR"
# 同时执行真实 DH 字节码行为测试（仅只读输入）
python3 build.py --deps-dir "$NOMI_DEPS_DIR" --dh-jar "$DELUXEHUB_INPUT"
# 或设置 NOMI_DEPS_DIR 后执行 python3 build.py
```

依赖目录可使用包含 `lobby/libraries` 和 `lobby/plugins` 的离线依赖树。需要 Purpur API 26.3、其 Adventure 等编译依赖、Residence 6.0.3.3、PlayerWarp 2.8.0-free；兼容生成器另外需要 ASM 9.x 的 core/tree/util/analysis。JDK 25 已实际以 `--release 21` 编译成功；这不表示依赖 Java 25 的服务端能在 Java 21 上运行。输出为 `build/NomiVIP-1.0.0.jar`，只含本项目类和 plugin.yml。`build/`、`.work/` 和测试日志均被忽略。

| 命令 | 用途 |
| --- | --- |
| `/nuomiscale <比例>` | 调整自己的体型；`1` 为复原 |
| `/nuomifly`、`/fly` | 切换自己当前连接会话的全图飞行；不接受目标玩家 |
| `nomivip status [玩家]` | 仅控制台：读取真实 flags、权限、模式、领地、会话、命令归属与兼容类证据 |
| `nomivip selftest` | 仅控制台：执行 13 组实际领域断言；不改变玩家或数据库 |

全部权限默认 `false`，没有通配权限和自动继承。创造/旁观模式的固有飞行无需会员权限；审计命令不向玩家授予管理能力，控制台直接可用。

| 权限 | 保留的语义 |
| --- | --- |
| `nuomi.vip.vip` | 正常体型及 ±10%、±20% |
| `nuomi.vip.svip` | 正常体型及 ±10%、±20%、±30% |
| `nuomi.vip.mvip` | 正常体型及 ±10%、±20%、±30%、±40% |
| `nuomi.vip.scale.10/20/30/40` | 逐个声明兼容旧节点；旧实现仅按等级判断，这些节点单独不解锁体型 |
| `nuomi.vip.fly.global` | 有效权限并在当前会话主动开启，才授权全图飞行 |
| `nuomi.vip.fly.anyres` | 仅有效 claim 内扩展 Residence 的玩家 fly 检查；需要兼容生成器 |
| `nuomi.warp.free.2/4/6` | 三个独立节点，取最大同时拥有地标的免费配额；需要 PlayerWarp 兼容生成器 |
| `nuomi.warp.paid` | 超出免费额度后允许按原价格继续创建，仍遵循原生世界/领地/创建限制 |

全图飞行不从持久化 allowFlight/flying 推导授权。加入时新建关闭的会话；起飞、世界/模式/传送事件和每 tick 复核都检查当前权限与环境。撤权即清除主动状态，之后重新获得权限也需再次主动开启。同步恢复的 `(true,false)`、`(true,true)` 都会被纠正；合法领地飞行和创造/旁观会保留。关闭非法飞行时清 flying、清 fallDistance，并给予最长 10 秒的一次摔落伤害保护，不修改游戏模式。

玩家属性与飞行变更均在玩家实体调度器执行。会话身份隔离旧连接的任务和退出回调；退出取消周期任务；插件停用取消本插件任务。请采用维护窗口完整重启，勿热卸载正在授权飞行的插件。现有 Residence nofly 规则及其管理员/拥有者/绕过判断保持生效；全图开关不会强行压过 nofly。

DH 适配仅支持已审查的 DeluxeHub 3.8.5（输入 SHA256 `d42836a1669f6fb45a4f2f9326343d8d7397fb79b6342dc4c8873a1090cf97b3`），运行时核对版本与双跳、模块、模块管理器和权限类指纹。只读取实际启用并注册 NORMAL 起飞监听的双跳模块，使用其真实 `inDisabledWorld(Location)` 判断有效世界范围（含模块管理器已处理的反转列表），排除创造、旁观与 `deluxehub.bypass.doublejump`。没有额外双跳授予权限；DH 自带飞行命令的权限和设置 flags 也不构成持续飞行授权。

`JumpAuthorization` 仅保留符合上述条件的现有 mayfly，不主动补发。DH 在 NORMAL 取消起飞后处理地面判断、冷却、速度与动作，成功发射清除 mayfly，由其移动监听重新准备；守卫不查询或消耗冷却，不在空中重新准备。NomiVIP 在 HIGHEST 忽略已取消事件，保留 DH 的合法取消及发射结果。即使同步恢复了 mayfly，在 DH 范围内最多保留双跳准备；恢复的持续 flying 仍会被清除。合法领地、创造与旁观继续由原有独立授权判定。模块关闭、禁用世界、bypass 或缺依赖均不授予双跳豁免，原有 nofly 规则继续生效。

启动日志和 `nomivip status` 显示 DH 校验及关闭原因；缺失、版本/字节码不匹配或反射查询失败时关闭双跳豁免，持续飞行守卫继续工作。服务端重映射若改变已核对类的字节码，也会失败关闭，需要协调者核对实际运行状态；不应跳过指纹校验。所有玩家查询仍在实体调度上下文执行。未改 DH、大厅保护或其配置。

Residence 与 PlayerWarp 的迁移、输入指纹、可重复生成、升级回退及内测清单见 [compat/README.md](compat/README.md)。兼容生成器输出只留在本地私有工作目录，不是公开发行内容。

`build.py` 执行 13 组策略测试及 9 组状态/报价测试。实际迁移时已先观察各组 RED，再保留 GREEN 日志于私有工作目录。另有 DH 状态回归；提供 `--dh-jar` 时还用真实 DH 类执行监听、发射/冷却/取消及适配器失败关闭测试（Bukkit 边界使用隔离替身，需要 SnakeYAML 2.2 等运行依赖）。离线行为测试不包含真实物理、声音动作或调度并发。兼容生成器另执行真实依赖的 JVM/ASM 验证和逐条目检查；这些检查不能替代真实服务端、经济插件和跨服同步验收。控制台 selftest 只运行明确标示的领域测试。

新源码尚未选择统一开源许可证。第三方依赖不随本仓库发布；来源许可情况与保留的 Residence GPL 文本见 [LICENSE](LICENSE) 和 [NOTICE.md](NOTICE.md)。
