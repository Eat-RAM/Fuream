# Fuream Spigot/Purpur 移植版

这是 Fabric 1.20.1 服务端模组 Fuream 的 Bukkit 移植。插件提供可扩展的熔炉输入、燃料和输出槽，累积经验，以及冲突配方选择，并保留 Fabric 使用的根级 `FureamData` NBT 结构。

## 版本与 Jar

| 服务端版本 | 使用文件 | `api-version` | 最低 Java |
| --- | --- | --- | --- |
| 1.7-1.12.x | `fuream-legacy-1.7-1.12.jar` | 无 | Java 8 |
| 1.13-1.20.4 | `fuream-flattening-1.13-1.20.4.jar` | `1.13` | Java 8 或服务端要求版本 |
| 1.20.5-26.2 | `fuream-components-1.20.5-26.2.jar` | `1.20.5` | 服务端要求版本 |

保证范围是 Spigot 和 Purpur。三个 Jar 使用同一套配置和 `FureamData` 格式，世界升级时不需要手动转换。插件主体编译为 Java 8 字节码；高版本服务器仍须使用该版本服务端要求的 Java。

插件没有 NBTAPI 或其他外置插件依赖。Byte Buddy、Java 8 JRE 自附加所需的 JNA，以及原生 NBT 桥均已打入 Jar。

## 行为

- 每种炉子可分别配置输入、燃料和输出槽数量，超过一页时可翻页。
- 每位玩家有独立的 GUI 会话和页码；只有炉子内的物品数据共享。
- 支持拖拽、双击、数字键、丢弃键、Shift 点击、输出游标和燃料区空桶操作。
- 1.13+ 使用服务端配方注册顺序选取首个匹配配方；冲突列表按稳定配方 ID 排序并记住选择。
- 1.7-1.12 只使用原版唯一熔炉配方，不显示配方选择功能；旧存档内的 override 数据会保留但忽略。
- 输出可合并并分散到多个槽。湿海绵只有在燃料区另有空槽时才把桶转换为水桶。
- 自定义烧炼会触发服务器版本支持的 Bukkit 熔炉事件，并接受取消、燃烧时间、烧炼时间和产物修改。
- 漏斗及漏斗矿车通过原生三槽投影和 `InventoryMoveItemEvent` 交互；底部先抽桶/水桶，再抽输出。
- 比较器只观察 Fabric 的输入、燃料、输出三槽遮罩视图，而不是全部扩展槽。
- 炉子锁、八格使用距离、区块状态和方块类型会在 GUI 会话期间持续检查。

## 原生 NBT

数据直接存于炉子方块实体根 NBT，不使用 PDC：

```text
Furnace BlockEntity root
|- FureamData
|  |- Inputs
|  |- Fuels
|  |- Outputs
|  |- RecipeOverridingInput
|  |- OverriddenRecipes
|  |- RunningRecipe
|  `- Xp
|- BurnTime
|- CookTime
`- CookTimeTotal
```

物品编码由运行版本适配：1.12 及以前保留数字 ID/耐久值，1.13-1.20.4 使用扁平化物品 NBT，1.20.5+ 使用数据组件格式。`FureamData` 的键名和层级保持不变，可用于 Fabric 1.20.1 -> Spigot/Purpur -> Fabric 的往返迁移。

插件在 `onLoad` 阶段为当前 NMS 炉子类匹配加载/保存方法并安装根 NBT 钩子。如果钩子安装失败，插件会在接管任何炉子前拒绝启用，以免静默丢失扩展槽物品。旧版本曾写入 `PublicBukkitValues` 的 Fuream 数据会在首次加载时迁移到根级标签，其他插件的 PDC 不受影响。

在切回 Fabric 前，应让 Fuream 插件保持启用并正常停服。不要先移除插件再用 Bukkit 服务端加载和保存世界，否则原版方块实体可能丢弃未知标签。

## 启用、禁用与配置

普通世界的配置文件是 `<world>/fuream.json`。槽位和 GUI 外观/i18n 字段接受 Fabric 配置格式；写回配置时会完整保存全部已知字段，并保留第三方未知 JSON 字段。

下界和末地按以下顺序查找配置：

1. 当前维度的 `fuream.json`
2. 当前维度的 `serverconfig/fuream.json` 或 `serverconfig/fuream.json5`
3. `server.properties` 的 `level-name` 所指主世界中的相同路径

维度世界没有找到配置时只使用内存默认值，不会自动生成文件。普通世界没有配置时会在自己的世界目录创建默认文件，因此独立普通世界继续保持独立配置。

没有 `FureamData` 且未启用的炉子不会被迁移或清空。已有 `FureamData` 但当前禁用的炉子进入被动模式：扩展槽仍保存在 NBT，首个有效输入、燃料和输出投影到原版三槽运行。重新启用时会原子收回投影，不复制物品。配置重载、区块卸载和停服都会关闭失效 GUI 并同步数据。

管理命令：

```text
/fuream reload
/fuream status
```

`status` 会显示当前版本适配器、根 NBT 钩子状态、每个世界启用的炉子类型及实际配置来源。

## 构建

在仓库根目录执行：

```powershell
.\gradlew.bat :spigot:test :spigot:assemble
```

产物位于 `spigot/build/libs/`：

```text
fuream-legacy-1.7-1.12.jar
fuream-flattening-1.13-1.20.4.jar
fuream-components-1.20.5-26.2.jar
```

## 验证状态

纯核心测试覆盖多槽插入/合并、跨槽产出、湿海绵、比较器遮罩、经验与配置完整往返等行为。使用 `Z:\fuream-servers` 中的 Jar 作为只读模板，在一次性本地目录实测了：

| 服务端 | 结果 |
| --- | --- |
| Spigot 1.14.4 | 插件加载、根 NBT 钩子、真实熔炼与持久化通过 |
| Purpur 1.15.2、1.16.5 | 纯 Java 8 JRE 下原生自附加、根 NBT 钩子和插件启用通过 |
| Purpur 1.17.1、1.18.2、1.19.4 | 根 NBT 钩子和插件启用通过 |
| Purpur 1.20.1 | 熔炼、启停无损投影、多槽输出、比较器通过 |
| Purpur 1.20.4 | 根 NBT 钩子、插件启用和关闭通过 |
| Purpur 1.20.6 | 数据组件物品读写、熔炼与持久化通过 |
| Purpur 1.21.1 | 根 NBT 钩子、插件启用和关闭通过 |
| Purpur 1.21.4 | 数据组件物品读写、熔炼与持久化通过 |

本次可用模板中没有 1.7.10、1.8.8、1.12.2、1.13.2 和 26.2 服务端 Jar，因此这些边界版本只完成了编译期隔离、1.8.8 API 依赖扫描和版本能力测试，发布前仍应补做对应服务端启动及手工 GUI 验收。双玩家独立分页、距离、锁和快捷键也仍需在目标服务器上做人工验收。

## 已知保留问题

破坏熔炉时仍会掉落“配方设置输入槽”的物品。这是 Fabric 基准版本本身的已知问题，为保持基准行为，本移植不修复这一项。
