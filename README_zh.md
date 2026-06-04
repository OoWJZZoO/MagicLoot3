# MagicLoot3

**Slimefun 4 附属插件** — 为世界添加随机生成的遗迹建筑、ARPG 风格的随机装备系统、以及 NPC 装备鉴定机制。

由 [MagicLoot3](https://github.com/TheBusyBiscuit-plugin-archive/MagicLoot3)（2016，mrCookieSlime）翻新而来。

---

## 兼容性

| 组件 | 最低版本 | 测试通过 |
|------|---------|---------|
| Paper | 1.21.1 | 1.21.11 |
| Slimefun 4 | RC-37 | vb79ae49-Beta |
| Java | 21 | 21.0.7 |

Slimefun 为软依赖——没有 Slimefun 时插件仍可正常运行，仅不注册粘液科技物品。

---

## 玩法

### 世界遗迹生成

插件在世界中自动生成废弃建筑（遗迹），建筑内包含战利品宝箱和随机刷怪笼。

- 每个新区块触发一次判定，概率可配置
- 支持白名单模式，每个世界独立配置生成概率
- 建筑分两类：**普通遗迹** 和 **特殊建筑**（即迷失图书馆）

### 无魂鉴定师

#### 出现方式
- **自然生成**：迷失图书馆建筑中必然出现一只

#### 平替方式
- **粘液科技合成**：通过强化合成台制作"遗物鉴定桌"，摆放后右键使用，效果与无魂鉴定师一样

#### 鉴定玩法
手持"未鉴定"装备（物品名显示为乱码）右键图书管理员或鉴定桌，打开鉴定 GUI。可选择：

| 选项 | XP 花费 | 说明 |
|------|--------|------|
| 随机鉴定 | 10级 | 随机抽取一个品级进行鉴定 |
| 普通 | 2 级 | Common 品级 |
| 非凡 | 5 级 | Uncommon 品级 |
| 稀有 | 10 级 | Rare 品级 |
| 史诗 | 25 级 | Epic 品级 |
| 传说 | 50 级 | Legendary 品级 |

以上费用以及随机鉴定的概率均可在配置文件中修改。

### ARPG 随机装备系统

生成的装备具有随机品级、名称、附魔和药水词缀。

**五个品级**（带权重抽奖机制）：

| 品级 | 默认权重 | 附魔数量 | 药水词缀数量 |
|------|---------|---------|---------|
| 普通 Common | 11 | 1 | 0 |
| 非凡 Uncommon | 7 | 1~2 | 0~1 |
| 稀有 Rare | 4 | 1~3 | 0~2 |
| 史诗 Epic | 3 | 1~4 | 0~3 |
| 传说 Legendary | 1 | 1~5 | 0~4 |

**药水词缀系统**：
- 装备 lore 中 `+ 属性名 等级` 表示攻击时给自身增益，`- 属性名 等级` 表示给目标减益
- 药水效果持续时长为 (药水等级 * 3) 秒

**战利品类型**（可在配置中单独开关）：

| 类型 | 产出 |
|------|------|
| TOOL | 随机武器/工具/盔甲，带品级词缀 |
| TREASURE | 钻石、金锭等宝藏 |
| BOOK | 附魔书，带品级词缀 |
| POTION | 溅射/滞留药水，1~2 个随机效果 |
| SLIMEFUN | 随机粘液科技物品 |
| UNANALIZED | 未鉴定装备（乱码名），需找鉴定师鉴定 |

### 怪物装备系统

僵尸、骷髅、僵尸猪灵生成时有概率（默认 15%）穿戴随机魔法装备，可掉落。

### 圣诞彩蛋

每年 12 月 22 日至 26 日，生物有概率戴圣诞帽、手持传说级武器。

---

## 自定义遗迹

1. 在游戏中使用原版结构方块保存建筑为 `.nbt` 文件
2. 文件命名为小写下划线格式（如 `my_tower.nbt`）
3. 放入 `plugins/MagicLoot3/structures/` 文件夹
4. 执行 `/magicloot reload` 或重启服务器
5. 建筑自动作为普通遗迹在世界中生成

> 特殊的"迷失图书馆"建筑暂不支持配置。

---

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/magicloot version` | 无 | 查看版本、构建号、语言、调试状态 |
| `/magicloot debug` | `magicloot3.admin` (op) | 开关调试日志 |
| `/magicloot reload` | `magicloot3.admin` (op) | 重载所有配置、重新扫描结构文件 |
| `/magicloot generate <name>` | `magicloot3.admin` (op) | 在玩家位置生成指定结构 |
| `/magicloot language <zh\|en>` | `magicloot3.admin` (op) | 切换语言 |

所有命令支持 Tab 补全。

---

## 配置文件

所有配置文件位于 `plugins/MagicLoot3/`。

### config.yml — 主配置

```yaml
language: zh          # 默认语言（zh / en）

chances:
  mobs: 15            # 怪物穿戴装备概率（百分比）

# 世界配置：每个世界独立设置生成概率
worlds:
  world:
    ruin-chance: 8       # 遗迹生成概率
    building-chance: 12  # 遗迹为特殊建筑的概率
  world_the_end:
    ruin-chance: 35
    building-chance: 5

chest:
  items:
    min: 3
    max: 11            # 每箱物品数量范围
  treasure-stack:
    min: 2
    max: 9             # 财宝堆叠数量范围
  slimefun-stack:
    min: 1
    max: 3             # 粘液科技物品堆叠数量范围

durability:
  min: 10              # 装备最低剩余耐久（百分比）
  max: 90              # 装备最高剩余耐久（百分比）

costs:                 # 鉴定费用（XP 等级）
  RANDOM: 10
  COMMON: 2
  UNCOMMON: 5
  RARE: 10
  EPIC: 25
  LEGENDARY: 50

enable:                # 启用的战利品类型
  TREASURE: true
  TOOL: true
  SLIMEFUN: true
  BOOK: true
  POTION: true
  UNANALIZED: true

spawners:              # 刷怪笼生物池
- ZOMBIE
- SPIDER
- CAVE_SPIDER
- VEX
```

### Items.yml — 物品权重

三个独立池子：`tools`（装备）、`treasure`（财宝）、`slimefun`（粘液物品）。

```yaml
tools:
  DIAMOND_SWORD: 10    # 权重越大，抽中概率越高
  FLINT_AND_STEEL: 0   # 设为 0 即禁用
  ...
treasure:
  DIAMOND: 10
  GOLD_INGOT: 10
  ...
slimefun:
  LOST_BOOKSHELF: 10
  ...
```

### loot_tiers.yml — 品级参数

```yaml
COMMON:
  weight: 11            # 生成权重（越高越常见）
  applicable-weight: 8  # 鉴定师"随机鉴定"权重
  enchantments:
    min: 1              # 最少附魔数量
    max: 1              # 最多附魔数量
  effects:
    min: 0              # 最少药水词缀数
    max: 0              # 最多药水词缀数
```

### Enchantments.yml — 附魔等级上限

```yaml
sharpness:
  max-level: 5          # 锋利 I ~ V
protection:
  max-level: 4
```

### Effects.yml — 药水效果等级上限

```yaml
speed:
  max-level: 3          # 速度 I ~ III
instant_health:
  max-level: 1          # 瞬间治疗 I
fire_resistance:
  max-level: 1          # 抗火 I
```

### lang/zh.yml 和 lang/en.yml — 语言文件

控制装备命名词池、NPC 对话、GUI 文本、日志提示等。可编辑以自定义装备名称风格。

### ruin_settings/<name>.yml — 结构配置

```yaml
y-offset: 0             # 垂直偏移
underwater: false       # 是否允许在水下生成
```

---

## 数据文件夹结构

```
plugins/MagicLoot3/
├── config.yml
├── Items.yml
├── Enchantments.yml
├── Effects.yml
├── loot_tiers.yml
├── lang/
│   ├── zh.yml
│   └── en.yml
├── structures/          # 自定义结构文件（.nbt）
│   ├── farm.nbt
│   ├── house.nbt
│   └── ...
└── ruin_settings/       # 每个结构的独立配置
    ├── farm.yml
    └── ...
```

---

## 构建

需要 Java 21 + Maven 3.9+。

```bash
# 中文端（默认）
mvn clean package

# 英文端
mvn clean package -P en
```

产物：`target/MagicLoot3-3.5.0-zh.jar` 和 `target/MagicLoot3-3.5.0-en.jar`。

两个 JAR 的区别仅在于粘液科技物品名/描述的出厂语言、config.yml 注释语言。运行时 `/magicloot language` 可以切换其他所有文本。

---

## 致谢

- 原作者 [mrCookieSlime / TheBusyBiscuit](https://github.com/TheBusyBiscuit) — MagicLoot3 原始版本
- [Slimefun 4 社区](https://github.com/Slimefun/Slimefun4)
