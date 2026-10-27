# Residence / PlayerWarp 兼容生成器

本目录发布自有兼容源码与变换程序，不发布第三方成品包、不需要后端散装源码。运行时辅助类嵌入对应插件的类加载器，类名使用 `org.nomiteam.vip`；NomiVIP 主 JAR 不包含第三方实现。

本次审查的 Residence 玩家 `playerHas(UUID, world, flag, default)` 两个重载没有触发 `ResidenceFlagCheckEvent`，公开事件无法无侵入扩展该检查。保留两个 fly 检查入口，替换旧 helper 为公开 `ResidenceGrant`，并增加当前位置属于 claim、世界未禁用和 fly 全局启用检查。原生进入/退出领地、nofly 和调度逻辑仍在 Residence；NomiVIP 通过真实 `getByLoc`、`playerHas` 等 API 复核当前合法领地权益，忽略陈旧的 metadata。

PlayerWarp 公开事件只有创建成功等事后事件，无法替代创建前价格和扣费。替代旧补丁的具体变更：

- 移除旧 GUI 注入中对数量参数的错误改写，改写真正的 long 价格变量。数量提示继续使用原生参数。
- 用实际 `PlayerWarpApi.getPlayerWarps(UUID).size()` 计算当前并发持有数。删除后额度立即由下次查询释放；零免费配额无需查库。
- GUI 和 Points、Vault、原生自定义货币扣费入口统一计算免费额度。原价读取原插件配置，无硬编码价格。
- GUI 保存两分钟的一次性报价；权益或数量导致实际价格变化时拒绝扣费，提示重新打开界面。不根据陈旧 GUI 扣不同价格。
- `nuomi.warp.paid` 仅放宽数量上限，不再提前绕过原插件的禁用世界、领地限制和 API 创建开关。
- 真实异步 `rawSlotClick` 整段在有界 UUID 分片锁下执行，覆盖本进程内检查、扣费至同步 DB 添加返回，避免连续点击争用免费额度。锁不写数据库；多实例同时为相同 UUID 创建地标不具备分布式事务保证。

生成器接受以下已审核的精确输入（2.8.1-free 为未补丁原包，其余表内旧版已带旧补丁），拒绝其他版本、重复 ZIP 条目和签名归档，拒绝覆盖现有输出。升级第三方版本必须先审查真实字节码与 API，再新增受测的精确变换；不能仅更换 SHA 绕过检查。

| 组件 | 输入 SHA256 |
| --- | --- |
| Residence 6.0.3.3 | `b275107efa695a4c4b54035f8523a49ffcec37948699c90672661a10550945ce` |
| PlayerWarp 2.8.0-free | `68aceac6f875a91a80fb881b9554ba9c56315004eb4116b673b59f4109cab2ee` |
| PlayerWarp 2.8.1-free 原包 | `0374ecb6d237bf3ca3a6eb824682bf35e99699f5413427ad49b102511085063a` |

构建者准备合法持有的依赖和两个对应输入文件，在仓库内使用本地私有输出位置：

```sh
python3 compat/patch.py residence "$RESIDENCE_INPUT" .work/Residence-compat.jar --deps-dir "$NOMI_DEPS_DIR"
python3 compat/patch.py warp "$PLAYERWARP_INPUT" .work/PlayerWarp-compat.jar --deps-dir "$NOMI_DEPS_DIR"
```

工具使用 JDK 编译公开 helper，核对精确的变换点数量，运行 JVM `-Xverify:all`、ASM 数据流验证、价格/扣费/限额/串行化字节码检查，以及全部未修改条目的逐字节比较。Residence 原 LICENSE 原样保留；最后报告输出 SHA256。固定 ZIP 时间戳使相同输入和编译器的输出可重复。无法通过检查时不生成最终输出。

由维护协调者完成升级与回退：

1. 在内测环境记录现有 JAR 的 SHA 和插件版本，私下保留原 JAR 及配置/数据备份。确认输入 SHA 符合上表；独立复建并核对生成器输出指纹。
2. 在对应菜单插件迁移中移除旧 Bridge 的 `VipControls` 创建、命令 executor、监听器、start/shutdown 调用，以及 `nuomiscale`/`nuomifly` 命令声明。其他目录由各自负责人修改。本插件检测到旧声明时拒绝双重接管；不能让旧守卫继续并行清飞行。
3. 完成当前 DH 的真实适配与独立验收后，再安排维护窗口停止内测实例、替换兼容组件并加入 NomiVIP，完整启动。不要热加载或同时装两份 Residence/PlayerWarp。SparrowSync 的 flight-status 配置无需因此关闭。
4. 控制台执行 `nomivip status`、`nomivip selftest`，核对 `/fly` 的实际归属和两个新 helper 的实际引用/存在证据。记录服务器原生日志、兼容组件 SHA、测试玩家真实状态和经济流水。
5. 内测通过后由协调者决定生产部署。回退时停止实例，移除 NomiVIP、恢复原兼容组件，并与旧 Bridge VIP 注册的恢复同步执行；完整启动后核对指纹与命令。不要同时恢复旧守卫并保留新守卫。补丁不修改数据库结构；已发生的合法创建/经济交易仍应由原插件数据备份流程处理。

独立验收至少覆盖：无权限/有权未开/主动开启、关闭、撤权后重授、退出重进、跨服恢复两种 flags、恢复已 flying、世界/模式切换、创造与旁观、领地 fly/anyres/子领地边界/退出/禁用世界/nofly、原生 DH 单次双跳/冷却/适用世界/已取消事件；会员飞行退出的摔落保护；地标免费 2/4/6 边界、删除释放、超额原价、Points/Vault/自定义币、过期或变化报价不扣费、连续点击、原生禁止创建规则。服务器内联调、真实扣费、跨服恢复时序和 DH 都尚未完成现场验收。

2.8.1-free 适配沿用线上 `WarpPricing`、`WarpPolicy`、`QuoteBook`，仅在原生价格、三种扣费、数量上限和点击锁入口补回调用；不执行旧包前缀删除。新原包优先参与编译和 JVM/ASM 验证，避免旧 Warp 遮蔽 API。

额外离线验收命令：

```sh
python3 tests/warp_281_check.py "$INPUTS" .work/warp-281/PlayerWarp-2.8.1-free-compat.jar --deps-dir "$NOMI_DEPS_DIR"
```

该测试撤去新增位点后比较完整原生指令，比较线上旧包四个 helper，执行会员配额与报价边界，并核对旧 NomiVIP 的资源探测契约。旧 NomiVIP 没有直接 Warp API 调用；此检查不等于运行服务器命令或真实经济扣费验收。
