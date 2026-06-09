package com.github.oowjzzoo.magicloot3;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineTier;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.LoreBuilder;

import io.github.thebusybiscuit.slimefun4.implementation.items.VanillaItem;

import com.github.oowjzzoo.magicloot3.items.ActivatedSculkShrieker;
import com.github.oowjzzoo.magicloot3.items.PastRune;
import com.github.oowjzzoo.magicloot3.items.RenameRune;
import com.github.oowjzzoo.magicloot3.machines.EquipmentSplitter;
import com.github.oowjzzoo.magicloot3.machines.LivingDropper;
import com.github.oowjzzoo.magicloot3.machines.PotionAffixDisenchanter;
import com.github.oowjzzoo.magicloot3.machines.AutoAppraiser;
import com.github.oowjzzoo.magicloot3.machines.DirtGenerator;
import com.github.oowjzzoo.magicloot3.machines.CopperUnifier;
import com.github.oowjzzoo.magicloot3.machines.PiglinSimulator;
import com.github.oowjzzoo.magicloot3.machines.PotionAffixEnchanter;

/**
 * Slimefun integration. SF item names are bound to the build language
 * and never change at runtime. This class is only loaded when Slimefun
 * is installed (guarded by isPluginEnabled check).
 */
final class SlimefunHook implements SlimefunAddon {

    /** Build-time language. Read from filtered build-info.properties. */
    private static final String LANG = loadBuildLang();

    private final JavaPlugin plugin;

    private SlimefunHook(JavaPlugin plugin) { this.plugin = plugin; }

    static void registerItems(Plugin plugin) {
        new SlimefunHook((JavaPlugin) plugin).doRegister();
    }

    private void doRegister() {
        boolean zh = "zh".equals(LANG);

        // ── Parent NestedItemGroup ──
        SlimefunItemStack iconStack = new SlimefunItemStack(
                "MAGICLOOT_ICON", Material.BOOKSHELF,
                zh ? "§5魔法战利品" : "§5MagicLoot");
        NestedItemGroup parentGroup = new NestedItemGroup(
                new NamespacedKey(plugin, "magicloot"), iconStack);

        // ── SubItemGroups ──

        // Machines (energy-consuming AContainers)
        ItemStack machinesIcon = new ItemStack(Material.SMITHING_TABLE);
        ItemMeta machinesMeta = machinesIcon.getItemMeta();
        machinesMeta.setDisplayName(zh ? "§6机器" : "§6Machines");
        machinesIcon.setItemMeta(machinesMeta);
        SubItemGroup machinesGroup = new SubItemGroup(
                new NamespacedKey(plugin, "magicloot_machines"), parentGroup, machinesIcon);

        // Basic Machines (non-energy machines + desk)
        ItemStack basicMachinesIcon = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta basicMachinesMeta = basicMachinesIcon.getItemMeta();
        basicMachinesMeta.setDisplayName(zh ? "§b基础机器" : "§bBasic Machines");
        basicMachinesIcon.setItemMeta(basicMachinesMeta);
        SubItemGroup basicMachinesGroup = new SubItemGroup(
                new NamespacedKey(plugin, "magicloot_basic_machines"), parentGroup, basicMachinesIcon);

        // Items (materials, runes, tools, dummies, etc.)
        ItemStack itemsIcon = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta itemsMeta = itemsIcon.getItemMeta();
        itemsMeta.setDisplayName(zh ? "§d物品" : "§dItems");
        itemsIcon.setItemMeta(itemsMeta);
        SubItemGroup itemsGroup = new SubItemGroup(
                new NamespacedKey(plugin, "magicloot_items"), parentGroup, itemsIcon);

        // Lost Bookshelf
        String shelfName = zh ? "§d旧日书架" : "§dLost Bookshelf";
        String[] shelfLore = zh
                ? new String[]{"", "§7装满了过气老书，充斥着狗血剧情",
                        "§7但是屎里淘金的话...",
                        "§7说不定真能找到有用的旧书(存疑)"}
                : new String[]{"", "§7Scrambled parts of an ancient library",
                        "§7Who knows what useful (or useless)",
                        "§7knowledge lies within..."};

        SlimefunItemStack bookshelfStack = new SlimefunItemStack(
                "LOST_BOOKSHELF", Material.BOOKSHELF,
                shelfName, shelfLore);

        ItemStack[] bookshelfRecipe = {
                new ItemStack(Material.BOOKSHELF), null, new ItemStack(Material.BOOKSHELF),
                SlimefunItems.MAGIC_LUMP_3, SlimefunItems.MAGICAL_BOOK_COVER, SlimefunItems.MAGIC_LUMP_3,
                new ItemStack(Material.BOOKSHELF), null, new ItemStack(Material.BOOKSHELF)};

        SlimefunItem bookshelf = new SlimefunItem(
                itemsGroup, bookshelfStack, RecipeType.ENHANCED_CRAFTING_TABLE,
                bookshelfRecipe, new SlimefunItemStack(bookshelfStack, 2));
        bookshelf.register(this);

        // Dummy item for unidentified equipment recipe matching
        String unidName = zh ? "&7&kMEH WANNA BE EXAMINED" : "&7&kMEH WANNA BE EXAMINED";
        String[] unidLore = zh
                ? new String[]{"", "§8品级：§b§d§e§c未鉴定", "", "§7即任意 §c未鉴定 §7的装备"}
                : new String[]{"", "§8Tier: §b§d§e§cUnknown", "", "§7Represents any §cunidentified §7equipment"};
        SlimefunItemStack unidStack = new SlimefunItemStack(
                "MAGICLOOT_UNIDENTIFIED", Material.STONE_HOE, unidName, unidLore);
        SlimefunItem unidDummy = new SlimefunItem(itemsGroup, unidStack, RecipeType.NULL, new ItemStack[9]);
        unidDummy.setHidden(true);
        unidDummy.register(this);

        // Lost Librarian's Desk
        String deskName = zh ? "§d遗物鉴定桌" : "§dLost Librarian's Desk";
        String[] deskLore = zh
                ? new String[]{"", "§7其实那几个旧书架没什么用",
                        "§7只不过因为看起来旧所以显得雅致",
                        "§7有助于进入状态研究装备"}
                : new String[]{"", "§7Basically like a Lost Librarian",
                        "§7Those old bookshelves just set the mood",
                        "§7Perfect for examining mysterious relics"};

        SlimefunItemStack deskStack = new SlimefunItemStack(
                "LOST_LIBRARIANS_DESK", Material.CRAFTING_TABLE,
                deskName, deskLore);

        ItemStack[] deskRecipe = {
                bookshelfStack, null, bookshelfStack,
                null, SlimefunItems.COMMON_TALISMAN, null,
                bookshelfStack, null, bookshelfStack};

        // Display recipes: unidentified → identified at each tier
        ItemStack displayUnid = SlimefunItem.getById("MAGICLOOT_UNIDENTIFIED")
                .getItem().clone();
        displayUnid.setType(Material.STONE_HOE);
        LootTier[] displayTiers = {LootTier.COMMON, LootTier.UNCOMMON,
                LootTier.RARE, LootTier.EPIC, LootTier.LEGENDARY};

        class DeskItem extends SlimefunItem implements io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem {
            DeskItem(ItemGroup g, SlimefunItemStack s, RecipeType r, ItemStack[] p) { super(g, s, r, p); }
            public @Nonnull List<ItemStack> getDisplayRecipes() {
                List<ItemStack> list = new java.util.ArrayList<>();
                for (LootTier t : displayTiers) {
                    list.add(displayUnid.clone());
                    list.add(ItemManager.applyTier(new ItemStack(Material.STONE_HOE), t));
                }
                return list;
            }
        }
        DeskItem desk = new DeskItem(basicMachinesGroup, deskStack, RecipeType.ENHANCED_CRAFTING_TABLE, deskRecipe);
        desk.addItemHandler((BlockUseHandler) event -> {
            event.cancel();
            LostLibrarian.openMenu(event.getPlayer(), true);
        });
        desk.register(this);

        // Copper Unifier
        String copperName = zh ? "§6铜锭大一统机" : "§6Copper Unifier";
        String[] copperLore = {"",
                zh ? "&7我受够了!!!" : "&7I've had enough!!!",
                zh ? "&7...本该如此" : "&7...as it should be",
                "",
                zh ? "&7可以转化粘液科技与原版的铜锭" : "&7Converts between Slimefun and vanilla copper ingots"};
        SlimefunItemStack copperStack = new SlimefunItemStack(
                "COPPER_UNIFIER", Material.COPPER_BLOCK, copperName, copperLore);
        ItemStack[] copperRecipe = {
                SlimefunItems.COPPER_INGOT, new ItemStack(Material.COPPER_INGOT), SlimefunItems.COPPER_INGOT,
                new ItemStack(Material.COPPER_INGOT), new ItemStack(Material.CRAFTING_TABLE), new ItemStack(Material.COPPER_INGOT),
                SlimefunItems.COPPER_INGOT, new ItemStack(Material.COPPER_INGOT), SlimefunItems.COPPER_INGOT};
        new CopperUnifier(basicMachinesGroup, copperStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, copperRecipe)
                .register(this);

        // Equipment Splitter
        String splitterName = zh ? "§a装备分流器" : "§aEquipment Splitter";
        String[] splitterLore = zh
                ? new String[]{"", "&7词条太多太杂?", "&7懒得手动分类?", "&7试试装备分流机"}
                : new String[]{"", "&7Too many affixes to sort?", "&7Tired of manual classification?", "&7Try the Equipment Splitter"};
        SlimefunItemStack splitterStack = new SlimefunItemStack(
                "EQUIPMENT_SPLITTER", Material.STONECUTTER, splitterName, splitterLore);
        ItemStack[] splitterRecipe = {
                null, SlimefunItems.REINFORCED_ALLOY_INGOT, null,
                bookshelfStack, new ItemStack(Material.DISPENSER), bookshelfStack,
                bookshelfStack, SlimefunItems.PORTABLE_DUSTBIN, bookshelfStack};
        new EquipmentSplitter(basicMachinesGroup, splitterStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, splitterRecipe)
                .register(this);

        // Lost Librarian's Brain
        String brainName = zh ? "§2无魂鉴定师之脑" : "§2Lost Librarian's Brain";
        String[] brainLore = zh
                ? new String[]{"", "&7无魂鉴定师免疫物理攻击",
                        "&7不免疫魔法(药水效果)攻击",
                        "&7不过说真的",
                        "&7这不太道德..."}
                : new String[]{"", "&7The Lost Librarian is immune to physical attacks",
                        "&7But not immune to magic (potion effects)",
                        "&7Though honestly...",
                        "&7This feels unethical"};
        SlimefunItemStack brainStack = new SlimefunItemStack(
                "LOST_LIBRARIAN_BRAIN", Material.ROTTEN_FLESH, brainName, brainLore);
        ItemStack eggIcon = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta eggMeta = eggIcon.getItemMeta();
        eggMeta.setDisplayName(zh ? "§5§l无魂鉴定师" : "§5§lLost Librarian");
        eggMeta.setLore(zh ? List.of("",
                "§f自然生成在失落图书馆中",
                "§f免疫物理伤害",
                "§f使用带有伤害型药水词缀的武器杀死",
                "§f或者使用喷溅型药水杀死")
                : List.of("",
                "§fNaturally spawns in Lost Libraries",
                "§fImmune to physical damage",
                "§fKill with a weapon imbued with",
                "§fdamaging potion affixes or splash potions"));
        eggIcon.setItemMeta(eggMeta);

        ItemStack dropIcon = new ItemStack(Material.IRON_SWORD);
        ItemMeta dropMeta = dropIcon.getItemMeta();
        dropMeta.setDisplayName(zh ? "§5§l无魂鉴定师掉落" : "§5§lLost Librarian Drop");
        dropIcon.setItemMeta(dropMeta);
        RecipeType librarianDrop = new RecipeType(
                new NamespacedKey(plugin, "librarian_drop"), dropIcon);
        new SlimefunItem(itemsGroup, brainStack, librarianDrop,
                new ItemStack[]{null, null, null, null, eggIcon, null, null, null, null})
                .register(this);

        // Hidden player head for recipes (obtained by suicide)
        String headIngredientName = zh ? "&e玩家的头" : "&ePlayer Head";
        SlimefunItemStack headIngredientStack = new SlimefunItemStack(
                "MAGICLOOT_PLAYER_HEAD", Material.PLAYER_HEAD, headIngredientName, new String[0]);

        // RecipeType icon (left side): diamond sword
        ItemStack recipeTypeIcon = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta rtm = recipeTypeIcon.getItemMeta();
        rtm.setDisplayName(zh ? "§4§l自刎归天" : "§4§lSudoku");
        recipeTypeIcon.setItemMeta(rtm);
        RecipeType suicideDrop = new RecipeType(
                new NamespacedKey(plugin, "suicide_drop"), recipeTypeIcon);

        // Recipe center display: skeleton skull with lore
        ItemStack skullIcon = new ItemStack(Material.SKELETON_SKULL);
        ItemMeta skullMeta = skullIcon.getItemMeta();
        skullMeta.setDisplayName(zh ? "§e玩家自杀掉落" : "§ePlayer Suicide Drop");
        skullMeta.setLore(List.of(zh
                ? "§7玩家因自身装备的药水词缀"
                : "§7When a player dies from their own",
                zh
                ? "§7或者自我击杀"
                : "§7Harm/Poison/Wither or self-kill",
                zh
                ? "§7而死亡时掉落"
                : "§7equipment's potion affix effects"));
        skullIcon.setItemMeta(skullMeta);

        SlimefunItem headIngredient = new SlimefunItem(itemsGroup, headIngredientStack,
                suicideDrop, new ItemStack[]{null, null, null, null, skullIcon, null, null, null, null});
        headIngredient.setHidden(true);
        headIngredient.register(this);

        // Magic Silicone Dummy
        String dummyName = zh ? "&e魔法硅胶假人" : "&eMagic Silicone Dummy";
        String[] dummyLore = zh
                ? new String[]{"", "&7QQ弹弹", "&7可以用来做什么呢..."}
                : new String[]{"", "&7Squishy and bouncy", "&7What could this be used for..."};
        SlimefunItemStack dummyStack = new SlimefunItemStack(
                "MAGIC_SILICONE_DUMMY", Material.ARMOR_STAND, dummyName, dummyLore);
        // Display recipe: uses hidden head ingredient
        ItemStack[] dummyRecipe = {
                SlimefunItems.MAGIC_LUMP_3, headIngredientStack, SlimefunItems.MAGIC_LUMP_3,
                SlimefunItems.BACKPACK_MEDIUM, new ItemStack(Material.ARMOR_STAND), SlimefunItems.BACKPACK_MEDIUM,
                SlimefunItems.MAGIC_LUMP_3, headIngredientStack, SlimefunItems.MAGIC_LUMP_3};
        new SlimefunItem(itemsGroup, dummyStack, RecipeType.ANCIENT_ALTAR, dummyRecipe)
                .register(this);
        // Implicit recipe: real PLAYER_HEAD with Notch skin for actual matching
        ItemStack notchHead = new ItemStack(Material.PLAYER_HEAD);
        if (notchHead.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer("Notch"));
            notchHead.setItemMeta(skull);
        }
        RecipeType.ANCIENT_ALTAR.register(
                new ItemStack[]{SlimefunItems.MAGIC_LUMP_3, notchHead, SlimefunItems.MAGIC_LUMP_3,
                        SlimefunItems.BACKPACK_MEDIUM, new ItemStack(Material.ARMOR_STAND), SlimefunItems.BACKPACK_MEDIUM,
                        SlimefunItems.MAGIC_LUMP_3, notchHead, SlimefunItems.MAGIC_LUMP_3},
                dummyStack);
        // Robustness: also accept plain PLAYER_HEAD with no meta
        RecipeType.ANCIENT_ALTAR.register(
                new ItemStack[]{SlimefunItems.MAGIC_LUMP_3, new ItemStack(Material.PLAYER_HEAD), SlimefunItems.MAGIC_LUMP_3,
                        SlimefunItems.BACKPACK_MEDIUM, new ItemStack(Material.ARMOR_STAND), SlimefunItems.BACKPACK_MEDIUM,
                        SlimefunItems.MAGIC_LUMP_3, new ItemStack(Material.PLAYER_HEAD), SlimefunItems.MAGIC_LUMP_3},
                dummyStack);

        // Living Dropper
        String dropperName = zh
                ? "&e投掷器 &8&l[&f&l活的!&8&l]"
                : "&eDropper &8&l[&f&lLiving!&8&l]";
        String[] dropperLore = zh
                ? new String[]{"",
                        "&7就像真的玩家一样!",
                        "&7必须绑定玩家且玩家在线",
                        "&7否则无法工作",
                        "",
                        "&a空手 Shift+右键 打开配置界面"}
                : new String[]{"",
                        "&7Just like a real player!",
                        "&7Must be bound to an online player",
                        "&7or it won't work",
                        "",
                        "&aShift+Right-click with empty hand to configure"};
        SlimefunItemStack dropperStack = new SlimefunItemStack(
                "LIVING_DROPPER", Material.DROPPER, dropperName, dropperLore);
        ItemStack[] dropperRecipe = {
                null, new ItemStack(Material.DROPPER), null,
                null, dummyStack, null,
                null, SlimefunItems.ANDROID_MEMORY_CORE, null};
        new LivingDropper(basicMachinesGroup, dropperStack, RecipeType.ENHANCED_CRAFTING_TABLE, dropperRecipe)
                .register(this);

        // Training Dummy
        String dummyName2 = zh
                ? "&e训练假人 &8&l[&b&l普通&8&l]"
                : "&eTraining Dummy &8&l[&b&lNormal&8&l]";
        String[] trainingDummyLore = zh
                ? new String[]{"", "&7这不对吧", "&7它摸起来是温的，还是软的!", "&7安心啦，这是魔法的效果",
                        "&7尽情使用它吧", "", "&aShift+右键 拆除假人"}
                : new String[]{"", "&7This can't be right", "&7It's warm and soft!", "&7Don't worry, it's magic",
                        "&7Use it to your heart's content", "", "&aShift+Right-click to dismantle"};
        SlimefunItemStack trainingDummyStack = new SlimefunItemStack(
                "TRAINING_DUMMY", Material.ARMOR_STAND, dummyName2, trainingDummyLore);
        ItemStack[] trainingDummyRecipe = {
                null, new ItemStack(Material.OBSERVER), null,
                null, dummyStack, null,
                null, SlimefunItems.GOLD_24K, null};
        new SlimefunItem(itemsGroup, trainingDummyStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, trainingDummyRecipe)
                .register(this);

        // Training Dummy [Undead] (Skeleton variant)
        String undeadName = zh
                ? "&e训练假人 &8&l[&f&l亡灵&8&l]"
                : "&eTraining Dummy &8&l[&f&lUndead&8&l]";
        String[] undeadLore = zh
                ? new String[]{"", "&7...这太血腥了",
                        "&7另外，把血肉拆掉变成骨头架子", "&7就真的是亡灵生物了吗",
                        "&7魔法，神奇吧!", "", "&aShift+右键 拆除假人"}
                : new String[]{"", "&7...This is gruesome",
                        "&7And also, stripping flesh to make a skeleton", "&7Is that really undead?",
                        "&7Magic, right!", "", "&aShift+Right-click to dismantle"};
        ItemStack[] undeadRecipe = {
                null, null, null,
                new ItemStack(Material.IRON_SWORD), trainingDummyStack, new ItemStack(Material.IRON_AXE),
                null, new ItemStack(Material.BONE), null};
        SlimefunItemStack undeadStack = new SlimefunItemStack(
                "TRAINING_DUMMY_UNDEAD", Material.ARMOR_STAND, undeadName, undeadLore);
        new SlimefunItem(itemsGroup, undeadStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, undeadRecipe)
                .register(this);

        // Garbled Voucher — traded for unidentified items at the desk/librarian
        String voucherName = zh ? "&7&kMEH WANNA BE EXAMINED" : "&7&kMEH WANNA BE EXAMINED";
        String[] voucherLore = zh
                ? new String[]{"&7乱码凭证",
                        "", "&7远古祭坛上围8个",
                        "&7中间放空白符文",
                        "&7也可合成 &7古代符文 &8&l[&e&l往日&8&l]"}
                : new String[]{"&7Garbled Voucher",
                        "", "&7Place 8 around an Ancient Altar",
                        "&7with a Blank Rune",
                        "&7to craft an &7Ancient Rune &8&l[&e&lPast&8&l]"};
        SlimefunItemStack voucherStack = new SlimefunItemStack(
                "GARBLED_VOUCHER", Material.PAPER, voucherName, voucherLore);

        ItemStack voucherDeskIcon = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta voucherDeskMeta = voucherDeskIcon.getItemMeta();
        voucherDeskMeta.setDisplayName(zh
                ? "§f兑换"
                : "§fExchange");
        voucherDeskMeta.setLore(List.of(zh
                ? "§f在 §5§l无魂鉴定师§f/§5§l遗物鉴定桌 §f处兑换"
                : "§fExchange at the §5§lLost Librarian§f / §5§lDesk"));
        voucherDeskIcon.setItemMeta(voucherDeskMeta);
        RecipeType voucherRecipeType = new RecipeType(
                new NamespacedKey(plugin, "voucher_exchange"), voucherDeskIcon);
        new SlimefunItem(itemsGroup, voucherStack, voucherRecipeType,
                new ItemStack[]{null, null, null, null, unidDummy.getItem(), null, null, null, null})
                .register(this);

        // --- Shared crafting materials (must be created before machines that use them) ---

        // Ancient Rune of Past (往日)
        String runeName = zh ? "§7古代符文 §8§l[§e§l往日§8§l]"
                : "§7Ancient Rune §8§l[§e§lPast§8§l]";
        String[] runeLore = zh
                ? new String[]{"§e把符文丢向你已经丢出的装备物品",
                        "§e该物品会变为 §4未鉴定 §e的乱码装备",
                        "", "§7往日种种，你当真不记得了?",
                        "§7往日种种? 往日...", "§7你说的可是往日..."}
                : new String[]{"§eDrop this rune onto an equipment item",
                        "§eThe item will become §4Unidentified §ewith garbled name",
                        "", "§7Past memories, you truly don't remember?",
                        "§7Past memories? Past...", "§7Are you talking about the past..."};

        // SF's ColoredFireworkStar pattern: effect color + HIDE_ADDITIONAL_TOOLTIP
        ItemStack runeBase = new ItemStack(Material.FIREWORK_STAR);
        FireworkEffectMeta fwMeta = (FireworkEffectMeta) runeBase.getItemMeta();
        fwMeta.setEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST).withColor(Color.YELLOW).build());
        fwMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        runeBase.setItemMeta(fwMeta);
        SlimefunItemStack runeStack = new SlimefunItemStack(
                "ANCIENT_RUNE_PAST", runeBase, runeName, runeLore);

        ItemStack[] runeRecipe = {
                new ItemStack(Material.ANCIENT_DEBRIS),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE),
                new ItemStack(Material.SCULK_CATALYST),
                new ItemStack(Material.CHORUS_FLOWER),
                SlimefunItems.VILLAGER_RUNE,
                new ItemStack(Material.CHORUS_FLOWER),
                new ItemStack(Material.SCULK_CATALYST),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE),
                new ItemStack(Material.ANCIENT_DEBRIS)};

        new PastRune(itemsGroup, runeStack, RecipeType.ANCIENT_ALTAR, runeRecipe,
                new SlimefunItemStack(runeStack, 3))
                .register(this);

        // Second recipe: 8 vouchers + blank rune → 1 Ancient Rune [Past]
        RecipeType.ANCIENT_ALTAR.register(
                new ItemStack[]{voucherStack, voucherStack, voucherStack,
                        voucherStack, SlimefunItems.BLANK_RUNE, voucherStack,
                        voucherStack, voucherStack, voucherStack},
                new SlimefunItemStack(runeStack, 1));

        // Ancient Rune of Rename (重命名)
        String renameRuneName = zh
                ? "§7古代符文 §8§l[§3§l重命名§8§l]"
                : "§7Ancient Rune §8§l[§3§lRename§8§l]";
        String[] renameRuneLore = zh
                ? new String[]{"&e把符文丢向你已经丢出的物品",
                        "&e该物品会被随机重命名",
                        "",
                        "&7为什么我的神剑叫做雷霆大内裤",
                        "&7我不满意!!!",
                        "",
                        "&4&l对于特殊物品请谨慎使用",
                        "&4&l可能导致它们无法被识别"}
                : new String[]{"&eDrop this rune onto a dropped item",
                        "&eThe item will be randomly renamed",
                        "",
                        "&7Why is my legendary sword called",
                        "&7Mighty Underpants?!",
                        "",
                        "&4&lUse with caution on special items",
                        "&4&lIt may make them unrecognizable"};

        ItemStack renameRuneBase = new ItemStack(Material.FIREWORK_STAR);
        FireworkEffectMeta renameFwMeta = (FireworkEffectMeta) renameRuneBase.getItemMeta();
        renameFwMeta.setEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST).withColor(Color.AQUA).build());
        renameFwMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        renameRuneBase.setItemMeta(renameFwMeta);
        SlimefunItemStack renameRuneStack = new SlimefunItemStack(
                "ANCIENT_RUNE_RENAME", renameRuneBase, renameRuneName, renameRuneLore);

        ItemStack[] renameRuneRecipe = {
                new ItemStack(Material.NAME_TAG), SlimefunItems.RAINBOW_LEATHER, runeStack,
                SlimefunItems.RAINBOW_LEATHER, brainStack, SlimefunItems.RAINBOW_LEATHER,
                runeStack, SlimefunItems.RAINBOW_LEATHER, new ItemStack(Material.NAME_TAG)};
        new RenameRune(itemsGroup, renameRuneStack, RecipeType.ANCIENT_ALTAR,
                renameRuneRecipe, new SlimefunItemStack(renameRuneStack, 12))
                .register(this);

        // Time of Exploration (探索的时光)
        String timeName = zh ? "&b&l探索的时光" : "&b&lAdventuring Time";
        String[] timeLore = zh
                ? new String[]{"", "&7一缕光阴"}
                : new String[]{"", "&7A sliver of time"};
        SlimefunItemStack timeStack = new SlimefunItemStack(
                "TIME_OF_EXPLORATION", Material.NETHER_STAR, timeName, timeLore);
        ItemStack[] timeRecipe = {
                runeStack, new ItemStack(Material.DRAGON_HEAD), runeStack,
                new ItemStack(Material.WAXED_WEATHERED_COPPER_BULB),
                new ItemStack(Material.BEACON),
                new ItemStack(Material.WAXED_WEATHERED_COPPER_BULB),
                runeStack, new ItemStack(Material.DRAGON_HEAD), runeStack};
        new SlimefunItem(itemsGroup, timeStack, RecipeType.ANCIENT_ALTAR, timeRecipe,
                new SlimefunItemStack(timeStack, 4))
                .register(this);

        // Activated Sculk Shrieker (can summon Warden)
        String shriekerName = zh
                ? "§f幽匿尖啸体 §8§l[§3§l活化§8§l]"
                : "§fSculk Shrieker §8§l[§3§lActivated§8§l]";
        String[] shriekerLore = zh
                ? new String[]{"",
                        "&7在朋友家放一个",
                        "&7然后...桀桀桀",
                        "",
                        "&7可以召唤监守者"}
                : new String[]{"",
                        "&7Place one at your friend's base",
                        "&7and then... hehehe",
                        "",
                        "&7Can summon the Warden"};
        SlimefunItemStack shriekerStack = new SlimefunItemStack(
                "ACTIVATED_SCULK_SHRIEKER", Material.SCULK_SHRIEKER, shriekerName, shriekerLore);
        ItemStack[] shriekerRecipe = {
                new ItemStack(Material.ECHO_SHARD), new ItemStack(Material.MUSIC_DISC_5), new ItemStack(Material.ECHO_SHARD),
                new ItemStack(Material.SOUL_TORCH), new ItemStack(Material.SCULK_SHRIEKER), new ItemStack(Material.SOUL_TORCH),
                new ItemStack(Material.ECHO_SHARD), new ItemStack(Material.MUSIC_DISC_5), new ItemStack(Material.ECHO_SHARD)};
        new ActivatedSculkShrieker(itemsGroup, shriekerStack,
                RecipeType.ANCIENT_ALTAR, shriekerRecipe)
                .register(this);

        // Dragon Head (VanillaItem with Ancient Altar recipe) — last in items
        ItemStack[] dragonHeadRecipe = {
                new ItemStack(Material.DRAGON_BREATH), new ItemStack(Material.SHULKER_SHELL), new ItemStack(Material.DRAGON_BREATH),
                SlimefunItems.ENDER_LUMP_3, new ItemStack(Material.NETHERITE_BLOCK), SlimefunItems.ENDER_LUMP_3,
                new ItemStack(Material.DRAGON_BREATH), new ItemStack(Material.SHULKER_SHELL), new ItemStack(Material.DRAGON_BREATH)};
        new VanillaItem(itemsGroup, new ItemStack(Material.DRAGON_HEAD), "DRAGON_HEAD",
                RecipeType.ANCIENT_ALTAR, dragonHeadRecipe)
                .register(this);

        // --- Machines ---

        // Dirt Generator (Magic Bulldozer) — first
        String[] dirtGenLore = {"",
                zh ? "&7泥土遍地都是" : "&7Dirt is everywhere",
                zh ? "&7但是手挖是不健康的行为" : "&7But digging by hand is unhealthy",
                zh ? "&7魔法推土机，你值得拥有" : "&7Magic Bulldozer, you deserve it",
                "",
                LoreBuilder.machine(MachineTier.MEDIUM, MachineType.MACHINE),
                LoreBuilder.speed(4), LoreBuilder.powerPerSecond(32)};
        String dirtGenName = zh ? "§6魔法推土机" : "§6Magic Bulldozer";
        SlimefunItemStack dirtGenStack = new SlimefunItemStack(
                "DIRT_GENERATOR", Material.MUD_BRICKS, dirtGenName, dirtGenLore);
        ItemStack[] dirtGenRecipe = {
                null, new ItemStack(Material.DIAMOND_SHOVEL), null,
                SlimefunItems.EXPLOSIVE_SHOVEL, new ItemStack(Material.MUD_BRICKS), SlimefunItems.EXPLOSIVE_SHOVEL,
                new ItemStack(Material.PISTON), new ItemStack(Material.CAULDRON), new ItemStack(Material.PISTON)};
        new DirtGenerator(machinesGroup, dirtGenStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, dirtGenRecipe)
                .register(this);

        // Piglin Simulator — second
        String piglinName = zh ? "§e§l猪灵模拟机" : "§e§lPiglin Simulator";
        String[] piglinLore = {"",
                zh ? "&7金光闪闪!" : "&7Shiny gold!",
                zh ? "&7产物真多!" : "&7So many drops!",
                zh ? "&7物流抓不过来啦!" : "&7Cargo can't keep up!",
                "",
                LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE),
                zh ? "&8⇨ &b⚡ &7速度: &b1x~64x" : "&8⇨ &b⚡ &7Speed: &b1x~64x",
                LoreBuilder.powerPerSecond(144)};
        SlimefunItemStack piglinStack = new SlimefunItemStack(
                "PIGLIN_SIMULATOR", Material.GILDED_BLACKSTONE, piglinName, piglinLore);
        ItemStack[] piglinRecipe = {
                SlimefunItems.GOLD_24K_BLOCK, new ItemStack(Material.PIGLIN_HEAD), SlimefunItems.GOLD_24K_BLOCK,
                SlimefunItems.PRODUCE_COLLECTOR, SlimefunItems.CARGO_MANAGER, SlimefunItems.AUTO_BREEDER,
                SlimefunItems.REINFORCED_PLATE, SlimefunItems.REINFORCED_PLATE, SlimefunItems.REINFORCED_PLATE};
        new PiglinSimulator(machinesGroup, piglinStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, piglinRecipe)
                .register(this);

        String[] machineLore = {"",
                zh ? "&7花费了大量的 &b&l探索的时光" : "&7After spending a great deal of &b&lAdventuring Time",
                zh ? "&7你终于打造出了一套神装" : "&7you have finally crafted a set of godly gear",
                "",
                LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE),
                LoreBuilder.speed(5), LoreBuilder.powerPerSecond(150)};

        // Auto Appraiser
        String[] appraiserLore = {"",
                zh ? "&7将 &c未鉴定 &7装备自动鉴定为指定品级" : "&7Auto-appraises &cUnidentified &7equipment to a selected tier",
                zh ? "&7消耗附魔之瓶或学识之瓶作为经验来源" : "&7Consumes Bottles o' Enchanting or Flasks of Knowledge as XP",
                "",
                LoreBuilder.machine(MachineTier.END_GAME, MachineType.MACHINE),
                LoreBuilder.speed(3), LoreBuilder.powerPerSecond(128)};
        String appraiserName = zh ? "&c&l自动鉴定仪" : "&c&lAuto Appraiser";
        SlimefunItemStack appraiserStack = new SlimefunItemStack(
                "AUTO_APPRAISER", Material.GRINDSTONE, appraiserName, appraiserLore);
        ItemStack[] appraiserRecipe = {
                null, SlimefunItems.NECROTIC_SKULL, null,
                renameRuneStack, brainStack, renameRuneStack,
                SlimefunItems.BLISTERING_INGOT_3, SlimefunItems.PROGRAMMABLE_ANDROID_2, SlimefunItems.BLISTERING_INGOT_3};
        new AutoAppraiser(machinesGroup, appraiserStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, appraiserRecipe)
                .register(this);

        // Potion Affix Disenchanter
        String disName = zh ? "§6§l药水词缀祛魔机" : "§6§lPotion Affix Disenchanter";
        SlimefunItemStack disStack = new SlimefunItemStack(
                "POTION_AFFIX_DISENCHANTER", Material.NOTE_BLOCK, disName, machineLore);
        ItemStack[] disRecipe = {
                timeStack, SlimefunItems.CARBONADO, timeStack,
                SlimefunItems.PROGRAMMABLE_ANDROID_3_FISHERMAN,
                SlimefunItems.AUTO_DISENCHANTER_2,
                SlimefunItems.PROGRAMMABLE_ANDROID_3_BUTCHER,
                timeStack, SlimefunItems.NETHER_STAR_REACTOR, timeStack};
        new PotionAffixDisenchanter(machinesGroup, disStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, disRecipe)
                .register(this);

        // Potion Affix Enchanter
        String enchName = zh ? "§6§l药水词缀附魔机" : "§6§lPotion Affix Enchanter";
        SlimefunItemStack enchStack = new SlimefunItemStack(
                "POTION_AFFIX_ENCHANTER", Material.JUKEBOX, enchName, machineLore);
        ItemStack[] enchRecipe = {
                timeStack, SlimefunItems.CARBONADO, timeStack,
                SlimefunItems.PROGRAMMABLE_ANDROID_3_FISHERMAN,
                SlimefunItems.AUTO_ENCHANTER_2,
                SlimefunItems.PROGRAMMABLE_ANDROID_3_BUTCHER,
                timeStack, SlimefunItems.NETHER_STAR_REACTOR, timeStack};
        new PotionAffixEnchanter(machinesGroup, enchStack,
                RecipeType.ENHANCED_CRAFTING_TABLE, enchRecipe)
                .register(this);

        plugin.getLogger().info(Messages.get("log.items_registered"));
    }

    @Override
    public JavaPlugin getJavaPlugin() { return plugin; }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/OoWJZZoO/MagicLoot3/issues";
    }

    private static String loadBuildLang() {
        try (InputStream in = SlimefunHook.class.getResourceAsStream("/build-info.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                return p.getProperty("lang", "zh");
            }
        } catch (Exception ignored) {}
        return "zh";
    }
}
