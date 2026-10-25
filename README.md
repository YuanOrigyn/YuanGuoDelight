<h1 align="center">
  Yuan来是果 | YuanGuoDelight<br>
  <sub>果园乐事 · Paper 插件版</sub>
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.x-3fb950" alt="Minecraft 1.21.x">
  <img src="https://img.shields.io/badge/CraftEngine-26.8.2%2B-5865F2" alt="CraftEngine 26.8.2+">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Folia-supported-blueviolet" alt="Folia 支持">
  <img src="https://img.shields.io/badge/PapersDelight-1.2.4%2B-blue" alt="PapersDelight 1.2.4+">
</p>

<p align="center"><i>在 Paper / Folia 服务端上还原《果园乐事》的主要内容，无需安装模组。</i></p>

---

## 📖 简介

Yuan来是果把 *Fruits Delight（果园乐事）* 的主要内容搬到了现代 Paper 服务端：22 种水果、果树、果酱与果冻、盛宴与盆栽，由 **CraftEngine** 驱动，玩法逻辑集中在本插件 jar 内。

个别依赖模组内部机制、在 Paper 上无法等价实现的效果已经移除（详见「已知限制」），其余内容与效果均可正常使用。

- 方块、物品、模型、贴图、合成配方：**CraftEngine 内容包**
- 炼药锅、季节联动、状态效果、果树生成：**本插件**
- 厨锅、砧板、液罐配方：**PapersDelight** 的 `papersdelight_recipes` 章节

## ✨ 功能

| 机制 | 说明 |
| --- | --- |
| 🍎 **22 种水果** | 苹果、蓝莓、发光浆果、哈密瓜、西瓜、山楂、荔枝、芒果、橘子、桃子、梨、柿子、菠萝、柠檬、蔓越莓、山竹、甜浆果、紫颂果、杨梅、猕猴桃、无花果、榴莲 |
| 🌳 **果树与作物** | 树叶 → 开花 → 结果，右键采摘；树苗、蓝莓丛、蔓越莓丛、菠萝、哈密瓜按季节推进 |
| 🥣 **炼药锅** | 水 → 果汁 → 果酱 → 果冻的完整链条，加糖 / 粘液球转换，瓶子 / 碗盛取 |
| 🍯 **果酱与果冻** | 22 种果酱瓶、22 种果冻，以及对应的果酱块、果冻块 |
| 🍞 **果酱面包** | 面包 + 果酱合成；也可在液罐中用 125 mB 果酱浸泡，比合成省一半 |
| 🫙 **液罐联动** | 果酱可倒入农夫乐事液罐，空瓶可装回果酱（需要 NyanaFluid） |
| 🍲 **厨锅 / 砧板** | 45 个厨锅配方 + 6 个砧板配方，由 PapersDelight 加载 |
| 🎉 **盛宴与盆栽** | 无花果布丁、山竹蛋糕、菠萝炒饭等盛宴方块；果树盆栽 |
| ✨ **状态效果** | 91 条食物效果，带 BossBar 倒计时显示；少数无法实现的效果已移除 |
| 🌦️ **季节联动** | 与 RealisticSeasons 联动，按季节开花、结果、生长 |
| 🏷️ **分类与标签** | 创造模式分类、物品标签、`c:` 通用标签 |

## 🧩 运行依赖

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Paper **或** Folia | 1.21.x | `api-version: 1.21` |
| **CraftEngine** | **26.8.2+** | **必需**，承载全部内容 |
| **PapersDelight** | **1.2.4-CE+** | **必需**，提供厨锅、砧板、液罐 |
| Java | 21 | 服务端运行时 |
| NyanaFluid | 1.0.0+ | 可选，启用液罐流体（农夫乐事 1.2.4 起的前置） |
| RealisticSeasons | 11.x | 可选，启用季节联动 |

> **PapersDelight 是硬依赖**：缺少它时插件会拒绝启动，并在控制台输出错误。
> 厨锅、砧板、液罐这些方块都属于 PapersDelight，本包的对应配方也需要它才能加载。

## 🚀 安装

1. 把 **CraftEngine** 放进 `plugins/`，启动一次服务器让它生成目录结构。
2. 把 **PapersDelight**（插件 + 农夫乐事内容包）和 **NyanaFluid** 安装好（农夫乐事 1.2.4 起液罐依赖 NyanaFluid）。
3. 把 `YuanGuoDelight-1.0.0.jar` 放进 `plugins/`。
4. 启动服务器。插件会自动把内置内容包释放到：
   ```text
   plugins/CraftEngine/resources/YuanGuoDelight/
   ```
5. 执行 `/ce reload` 或重启服务器，让内容包生效。

如果想自己改内容包，把 `config.yml` 里的 `pack.auto-install` 设为 `false`，
再手动把 `pack/` 的内容放到上面那个目录即可。

## 🧭 季节联动

季节映射与原模组的 `SeasonCompat` 保持一致：

| 季节 | 水果 |
| --- | --- |
| 🌸 春 SPRING | 水蜜桃、蓝莓、梨 |
| ☀️ 夏 SUMMER | 哈密瓜、柠檬、菠萝、山竹、芒果、荔枝、杨梅、榴莲 |
| 🍂 秋 FALL | 苹果、橘子、无花果、猕猴桃、蔓越莓 |
| ❄️ 冬 WINTER | 柿子、山楂 |

全年可生长：发光浆果、西瓜、甜浆果、紫颂果。

行为：

- 果树 `树叶 → 开花 → 结果`，只有对应季节才会开花、结果，结果后右键采摘。
- 树苗只在对应季节成长并生成树木。
- 作物（蓝莓、蔓越莓、菠萝、哈密瓜）只在对应季节推进生长阶段。
- 没有安装 RealisticSeasons 时，全部内容全年正常生长。

> RealisticSeasons 的作物系统只认识原版 `Material`，无法管理 CraftEngine 自定义方块。
> 因此季节限制全部实现在本插件自己的 CE 方块行为里，通过反射读取季节 API，
> 不产生硬编译依赖；没有季节插件时插件也能正常工作。

## ⚙️ 配置文件

`plugins/YuanGuoDelight/config.yml`

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| `debug` | `false` | 在控制台输出季节读取等调试信息 |
| `season.enabled` | `true` | 季节联动总开关。`false` = 全部全年生长 |
| `season.auto-detect` | `true` | 检测到 RealisticSeasons 时自动启用季节联动 |
| `season.notify-season-change` | `false` | 季节切换时在控制台打印日志 |
| `season.growth.restrict-growth` | `true` | `true` 仅当季可生长；`false` 非当季也能长但变慢 |
| `season.growth.in-season-multiplier` | `1.0` | 当季生长倍率 |
| `season.growth.out-of-season-multiplier` | `0.25` | 非当季生长倍率（仅在 `restrict-growth: false` 时生效） |
| `pack.auto-install` | `true` | 启动时自动释放内置内容 |
| `pack.cauldron` | `true` | 炼药锅交互总开关 |
| `pack.overwrite` | `false` | `true` = 每次启动都覆盖服务器上的内容包 |
| `effects.enabled` | `true` | 食物状态效果总开关 |
| `effects.boss-bar` | `true` | 用 Boss 血条显示自定义效果剩余时间 |
| `effects.disabled` / `effects.bar-disabled` | `[]` | 按效果 id 单独关闭效果 / 倒计时条 |

## 🎯 指令与权限

| 指令 | 权限 | 说明 |
| --- | --- | --- |
| `/yuanguo status` | `yuanguo.admin` | 查看季节联动、依赖检测结果与配置状态 |
| `/yuanguo season` | — | 查看当前世界季节 |
| `/yuanguo effects` | — | 查看自己身上的自定义效果 |
| `/yuanguo reload` | `yuanguo.admin` | 重新加载配置并重启季节联动与状态效果 |

## 🍯 炼药锅

炼药锅的整套流程由插件实现，方块状态由 CraftEngine 渲染，锅身与液面用实体展示。

| 操作 | 结果 |
| --- | --- |
| 水瓶 / 水桶 → 空炼药锅 | 装满水 |
| 柠檬片 → 满水锅 | 变成淡柠檬水锅 |
| 果酱瓶 → 满水锅 | 变成对应水果锅（等级 4） |
| 水果 → 同种水果锅 | 浓度提升，需热源 |
| 水果 → 淡柠檬水锅 | 变成该水果的锅，需热源 |
| 果酱瓶 → 同种水果锅 | 浓度 +4 |
| 糖 → 满级水果锅 | 变成对应果酱锅，需热源 |
| 粘液球 → 果酱锅 | 变成对应果冻锅，需热源 |
| 玻璃瓶 → 果酱锅 | 取出对应果酱瓶，锅降一级 |
| 碗 → 果冻锅 | 取出对应果冻，锅降一级 |

内容包通过 `configuration/fluids.yml` 向 NyanaFluid 声明 22 种果酱流体（`yuanguo:<fruit>_jam`），并把果酱瓶声明为 `bottle` 容器，液罐因此能直接识别。

## 🫙 液罐

果酱可以装入农夫乐事的液罐（`farmersdelight:jug` / `glass_jug`）：

| 操作 | 结果 |
| --- | --- |
| 果酱瓶 → 液罐 | 倒入 250 mB 对应果酱，瓶子变空玻璃瓶 |
| 空玻璃瓶 → 果酱液罐 | 装回对应果酱瓶 |
| 面包 → 果酱液罐（GUI 输入槽） | 40 tick 后产出对应果酱面包，消耗 125 mB |

> 一瓶 250 mB 果酱能做两个面包；工作台合成则是「1 面包 + 1 整瓶果酱」。
> 浸泡配方是本插件额外加的，原模组没有。

## 📦 内容规模

| 项目 | 数量 |
| --- | --- |
| 物品 | 265 个（另有 445 个实体展示物品） |
| 方块 | 157 个 |
| 合成配方 | 165 个 |
| 厨锅配方 | 45 个 |
| 砧板配方 | 6 个 |
| 液罐配方 | 22 个浸泡配方（倒出 / 灌装由 NyanaFluid 的容器声明提供） |
| 世界生成特性 | 31 个 |
| 食物效果 | 91 条 |
| 语言文件 | 100 个 |
| 资源包 | 1348 个模型、904 张贴图 |

## 🗂️ 目录结构

```text
src/main/java/dev/yuan/yuanguo/     插件源码
src/main/resources/                 paper-plugin.yml / config.yml / effects.yml
pack/                               CraftEngine 内容包（生成物）
libs/                               编译用依赖（离线构建）
work/gen/                           内容包生成器（从模组数据生成）
build.ps1                           编译 + 打包脚本
YuanGuoDelight-1.0.0.jar            构建产物（未随仓库提交）
```

## 🔨 构建

需要 **JDK 21+**。仓库自带 Gradle Wrapper，克隆后直接构建：

```powershell
.\gradlew.bat clean jar
```

产物在 `build/libs/YuanGuoDelight-1.0.0.jar`。

也可以不装 Gradle，用纯 JDK 脚本构建：

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

`build.ps1` 会自动寻找 JDK（`JAVA_HOME` → `C:\Program Files\Java` → `PATH`），
并用 JDK 自带的 `javac` / `jar` 编译打包。

两种方式都会把所有依赖从公开 Maven 仓库下载到本地缓存，**首次构建需要联网**：

- Paper：`repo.papermc.io`
- CraftEngine：`repo.momirealms.net`
- PapersDelight API：`mvn.hezhongkj.top`
- Adventure / Guava 等：Maven Central

`build.ps1` 的依赖缓存在 `libs/`（该目录已加入 `.gitignore`），下载完成后可离线重复构建。
可以用 `YGD_LIBS` 指定其他缓存目录：

```powershell
$env:YGD_LIBS = "D:\temp\ygd-libs"
powershell -ExecutionPolicy Bypass -File build.ps1
```

输出目录默认是 `build/`；如果该目录不可写，脚本会自动改用 `build-local/`，也可以用 `YGD_BUILD` 指定：

```powershell
$env:YGD_BUILD = "D:\temp\ygd-build"
powershell -ExecutionPolicy Bypass -File build.ps1
```

编译失败时，`build.ps1` 会打印输出目录下的 `compile.log`。

> 两种构建都会把整个 `pack/` 打成 `pack.zip` 放在 jar 根目录。插件启动时由
> `PackInstaller` 解压到 `plugins/CraftEngine/resources/YuanGuoDelight/`，
> 所以下载源码自行构建的 jar 同样会自动释放内容包，不需要手动复制 `pack/`。
## 🧪 从源码重新生成内容包

内容包由 `work/gen` 中的生成器从模组数据生成，再打包进 jar。

生成器需要的输入**不在仓库里**（原模组与 Mojang 素材不随仓库分发），克隆后请自行准备：

```text
work/gen/mod-1.1.3/    解包后的 Fruits Delight 模组 jar（assets / data / dev）
work/vanilla-water/    原版 water_still.png 及其 mcmeta
```

然后运行：

```powershell
java -cp "snakeyaml.jar;gson.jar;work/gen/classes" Gen work/gen/mod-1.1.3 pack
```

> ⚠️ `Gen.main` 会先清空并完整重写 `pack/`。如果只改了一小部分内容，
> 建议直接手改 `pack/` 下对应文件，同时同步 `work/gen` 里的生成器，避免丢失手工调整。

> ⚠️ 原模组 Fruits Delight 为 LGPL-2.1，其贴图与模型版权归原作者所有；本仓库只分发由它派生并重新编排的
> 内容包数据。若你要再分发素材，请遵守原模组的许可证。

## ⚠️ 已知限制

- 果酱 / 果汁不是真实流体方块：安装 NyanaFluid 时会声明果酱流体供液罐等设备使用，
  但炼药锅的灌注与取出仍按模组逻辑以物品交互实现。
- 液罐里的果酱使用 `minecraft:block/water_still` 贴图加 `color` 染色，不是模组的 `jelly` 贴图
  （NyanaFluid 默认用水的流动贴图，颜色由流体的 `color` 决定）。
- 食物效果用原版药水效果加 Boss 血条模拟，自定义效果没有原版图标。
- 少数依赖模组内部机制的效果在 Paper 上无法等价实现，已从内容中移除，不参与食物效果表：
  - **穿扬**（piercing）：依赖模组弹射物穿透判定
  - **涩味**（astringent）：依赖模组冰面移动手感，无法在不干扰视角的前提下还原
  - **滑行**（sliding）：依赖模组冰面滑行速度模型
- 直接食用蛋糕 / 布丁类方块通过 CE 交互事件实现，依赖 CraftEngine 的事件钩子。
- 未提供 Botany Pots 兼容。

## 📜 授权

原模组 Fruits Delight 为 LGPL-2.1；CraftEngine 为 GPL-3.0；NyanaFluid 为 MIT；
PapersDelight-API 为 AGPL-3.0。本作品作为它们的衍生组合，需以 **GPL-3.0** 发布。