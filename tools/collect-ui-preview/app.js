(() => {
  const DATA = window.COLLECT_DATA;
  const CATEGORIES = DATA.categories;
  const CATEGORY_MAP = Object.fromEntries(CATEGORIES.map(c => [c.id, c]));
  const PROF_ITEMS = DATA.professionItems || {};
  const MATERIAL_RECIPES = DATA.materialRecipes || {};
  const PLAYER_NAME = "MaplePlayer";

  // 方块类材质集合（用于渲染立体方块边框质感）
  const BLOCK_MATERIALS = new Set([
    "MELON", "PUMPKIN", "SAND", "GRAVEL", "DIRT", "CLAY", "STONE", "OBSIDIAN",
    "GLOWSTONE", "CACTUS", "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG",
    "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG", "CHERRY_LOG", "CRIMSON_STEM",
    "WARPED_STEM", "BOOKSHELF", "CRAFTING_TABLE", "GOLD_BLOCK", "DIAMOND_BLOCK",
    "COBBLESTONE"
  ]);

  const LEATHER_ARMOR_MATERIALS = new Set([
    "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"
  ]);

  const PANE_TEXTURE_MAP = {
    "pane-black": "BLACK_STAINED_GLASS_PANE",
    "pane-gray": "GRAY_STAINED_GLASS_PANE",
    "pane-blue": "BLUE_STAINED_GLASS_PANE",
    "pane-lime": "LIME_STAINED_GLASS_PANE",
    "pane-light-blue": "LIGHT_BLUE_STAINED_GLASS_PANE",
    "pane-yellow": "YELLOW_STAINED_GLASS_PANE",
    "pane-red": "RED_STAINED_GLASS_PANE"
  };

  function textureUrl(material) {
    return `textures/${material}.png`;
  }

  function createMiniIconHtml(material) {
    return `<img class="mini-mc-tex" src="${textureUrl(material)}" alt="${material}" />`;
  }

  // 物品视觉图标兜底（如果贴图意外加载失败时回退）
  const MATERIAL_ICONS = {
    // 分类与界面图标
    COPPER_HOE: "🪓", COPPER_PICKAXE: "⛏️", COPPER_SWORD: "⚔️", COPPER_AXE: "🪓", FISHING_ROD: "🎣",
    BOOKSHELF: "📚", AMETHYST_SHARD: "🔮", HOPPER: "📥", GOLD_INGOT: "🥇", WRITABLE_BOOK: "📖",
    BOOK: "📘", COMPASS: "🧭", NETHER_STAR: "⭐", ARROW: "⬅️", BARRIER: "❌",
    CRAFTING_TABLE: "🛠️", EXPERIENCE_BOTTLE: "🧪", EMERALD: "💚", IRON_PICKAXE: "⛏️",
    ITEM_FRAME: "🖼️", CHEST_MINECART: "🛒", ENDER_CHEST: "🎒", BUNDLE: "🐾",
    COPPER_CHESTPLATE: "🛡️", LEATHER_CHESTPLATE: "🧥", REDSTONE_TORCH: "🏮", PAPER: "📜",
    // 农耕
    TORCHFLOWER_SEEDS: "🌺", PITCHER_POD: "🪴", WHEAT_SEEDS: "🌱", WHEAT: "🌾",
    CARROT: "🥕", POTATO: "🥔", BEETROOT: "🫜", MELON: "🍉", PUMPKIN: "🎃",
    SUGAR_CANE: "🎋", BAMBOO: "🎍", CACTUS: "🌵", COCOA_BEANS: "🫘",
    SWEET_BERRIES: "🫐", BROWN_MUSHROOM: "🍄", RED_MUSHROOM: "🍄", NETHER_WART: "🍒",
    KELP: "🌿", LEATHER: "🟤", RABBIT_HIDE: "🟫", RABBIT_FOOT: "🐾",
    BEEF: "🥩", PORKCHOP: "🥓", MUTTON: "🍖", CHICKEN: "🍗", RABBIT: "🐇", FEATHER: "🪶",
    // 挖矿
    SAND: "🟨", GRAVEL: "🪨", DIRT: "🟫", CLAY: "🧱", STONE: "🪨", OBSIDIAN: "⬛",
    GLOWSTONE: "💡", COAL: "⚫", RAW_COPPER: "🟠", COPPER_INGOT: "🟧",
    RAW_IRON: "⚙️", IRON_INGOT: "⬜", RAW_GOLD: "🟡", REDSTONE: "🔴",
    LAPIS_LAZULI: "🔷", QUARTZ: "🤍", DIAMOND: "💎", NETHERITE_SCRAP: "🔩",
    // 战斗
    ROTTEN_FLESH: "🧟", BONE: "🦴", STRING: "🕸️", SPIDER_EYE: "👁️",
    GUNPOWDER: "💥", SLIME_BALL: "🟢", ENDER_PEARL: "🟣", PHANTOM_MEMBRANE: "🪽",
    INK_SAC: "🖤", BLAZE_ROD: "🔥", MAGMA_CREAM: "🟠", GHAST_TEAR: "💧",
    SHULKER_SHELL: "🐚", BREEZE_ROD: "🌪️",
    // 伐木
    OAK_LOG: "🪵", SPRUCE_LOG: "🌲", BIRCH_LOG: "🪵", JUNGLE_LOG: "🌴",
    ACACIA_LOG: "🪵", DARK_OAK_LOG: "🌳", MANGROVE_LOG: "🪵", CHERRY_LOG: "🌸",
    CRIMSON_STEM: "🟥", WARPED_STEM: "🟦",
    // 钓鱼
    COD: "🐟", SALMON: "🐠", TROPICAL_FISH: "🐡", PUFFERFISH: "🐡",
    COOKED_COD: "🍣", COOKED_SALMON: "🍱",
    // 装备/工具
    LEATHER_HELMET: "⛑️", LEATHER_LEGGINGS: "👖", LEATHER_BOOTS: "🥾",
    IRON_HOE: "⛏️", DIAMOND_HOE: "💠", NETHERITE_HOE: "🖤",
    DIAMOND_PICKAXE: "💎", NETHERITE_PICKAXE: "🖤",
    IRON_SWORD: "🗡️", DIAMOND_SWORD: "⚔️", NETHERITE_SWORD: "🗡️",
    STICK: "🪵", DIAMOND_HELMET: "⛑️", DIAMOND_CHESTPLATE: "🛡️", DIAMOND_LEGGINGS: "👖", DIAMOND_BOOTS: "🥾"
  };

  // 构建原版材料中文名索引
  const VANILLA_NAMES = {};
  for (const c of CATEGORIES) {
    for (const it of c.items) {
      VANILLA_NAMES[it.material] = it.name;
    }
  }
  Object.assign(VANILLA_NAMES, {
    LEATHER_HELMET: "皮革头盔", LEATHER_CHESTPLATE: "皮革胸甲", LEATHER_LEGGINGS: "皮革护腿", LEATHER_BOOTS: "皮革靴子",
    IRON_HOE: "铁锄", DIAMOND_HOE: "钻石锄", NETHERITE_HOE: "下界合金锄",
    IRON_PICKAXE: "铁镐", DIAMOND_PICKAXE: "钻石镐", NETHERITE_PICKAXE: "下界合金镐",
    IRON_SWORD: "铁剑", DIAMOND_SWORD: "钻石剑", NETHERITE_SWORD: "下界合金剑",
    STICK: "木棍", COBBLESTONE: "圆石", GOLD_BLOCK: "金块", DIAMOND_BLOCK: "钻石块"
  });

  // 模拟其他服务器玩家用于排行榜演示
  const NPC_PLAYERS = [
    { name: "MapleKing", color: "#d97706", base: { FARMING: 620000, MINING: 540000, COMBAT: 95000, FORAGING: 78000, FISHING: 41000 } },
    { name: "SakuraMiner", color: "#db2777", base: { FARMING: 185000, MINING: 780000, COMBAT: 42000, FORAGING: 53000, FISHING: 12000 } },
    { name: "WheatMaster", color: "#65a30d", base: { FARMING: 890000, MINING: 120000, COMBAT: 19000, FORAGING: 32000, FISHING: 24000 } },
    { name: "CaveHunter", color: "#7c3aed", base: { FARMING: 92000, MINING: 310000, COMBAT: 260000, FORAGING: 44000, FISHING: 18000 } },
    { name: "LumberJack99", color: "#b45309", base: { FARMING: 64000, MINING: 85000, COMBAT: 31000, FORAGING: 340000, FISHING: 9000 } },
    { name: "OceanFisher", color: "#0284c7", base: { FARMING: 48000, MINING: 62000, COMBAT: 27000, FORAGING: 21000, FISHING: 185000 } },
    { name: "RedstonePro", color: "#dc2626", base: { FARMING: 55000, MINING: 420000, COMBAT: 51000, FORAGING: 39000, FISHING: 15000 } },
    { name: "NetherRunner", color: "#9333ea", base: { FARMING: 71000, MINING: 190000, COMBAT: 145000, FORAGING: 68000, FISHING: 11000 } },
    { name: "SkyBlockFan", color: "#0d9488", base: { FARMING: 230000, MINING: 175000, COMBAT: 63000, FORAGING: 59000, FISHING: 37000 } },
    { name: "CopperSmith", color: "#ea580c", base: { FARMING: 39000, MINING: 280000, COMBAT: 34000, FORAGING: 29000, FISHING: 8000 } },
    { name: "BerryFarmer", color: "#be123c", base: { FARMING: 310000, MINING: 44000, COMBAT: 12000, FORAGING: 22000, FISHING: 19000 } },
    { name: "PixelKnight", color: "#4f46e5", base: { FARMING: 28000, MINING: 94000, COMBAT: 182000, FORAGING: 35000, FISHING: 14000 } },
    { name: "EmeraldCat", color: "#059669", base: { FARMING: 115000, MINING: 155000, COMBAT: 48000, FORAGING: 47000, FISHING: 26000 } },
    { name: "BambooPanda", color: "#16a34a", base: { FARMING: 142000, MINING: 36000, COMBAT: 15000, FORAGING: 96000, FISHING: 12000 } },
    { name: "ObsidianFox", color: "#334155", base: { FARMING: 19000, MINING: 215000, COMBAT: 74000, FORAGING: 18000, FISHING: 6500 } },
    { name: "StarryNight", color: "#2563eb", base: { FARMING: 45000, MINING: 78000, COMBAT: 39000, FORAGING: 25000, FISHING: 64000 } },
    { name: "NewbieSteve", color: "#64748b", base: { FARMING: 2800, MINING: 4100, COMBAT: 1200, FORAGING: 1600, FISHING: 500 } }
  ];

  // 运行时模拟状态
  const state = {
    soundEnabled: true,
    progress: {}, // key: `${pageId}:${material}` -> number
    claims: {},   // key: `${pageId}:${material}` -> claimedTier
    mapleXp: 0,
    view: {
      type: "hub", // main | hub | category | detail | tierRecipe | leaderboard | missing | guide
      categoryId: "FARMING",
      page: 0,
      itemIndex: 0,
      tier: 1,
      recipeIdx: 0,
      recipeStack: [], // 原料下钻追踪栈：存放 professionItem.id
      lbCategoryId: null, // null = overall
      missingCategoryId: null
    },
    actionbarTimer: null
  };

  // DOM 引用
  const elGrid = document.getElementById("mc-grid");
  const elTitle = document.getElementById("gui-title");
  const elHint = document.getElementById("gui-hint");
  const elBreadcrumbs = document.getElementById("breadcrumb-bar");
  const elTooltip = document.getElementById("mc-tooltip");
  const elChatLog = document.getElementById("mc-chat-log");
  const elActionbar = document.getElementById("mc-actionbar");
  const elSimCat = document.getElementById("sim-category");
  const elSimItem = document.getElementById("sim-item");
  const elSimCurrent = document.getElementById("sim-current-box");
  const elPlayerStats = document.getElementById("player-stats-box");
  const elRecipeShortcuts = document.getElementById("recipe-shortcuts");

  // ---------- 数值与数学逻辑（与 Java 后端一致） ----------
  function cKey(cat, material) {
    return `${cat.pageId}:${material}`;
  }

  function requirements(cat) {
    return cat.extended ? DATA.tierRequirements12 : DATA.tierRequirements9;
  }

  function maxTier(cat) {
    return requirements(cat).length;
  }

  function currentTier(amount, cat) {
    const reqs = requirements(cat);
    let t = 0;
    for (const req of reqs) {
      if (amount < req) break;
      t++;
    }
    return t;
  }

  function mapleXpReward(tier) {
    return Math.max(1, Math.floor(tier / 2));
  }

  function isFortuneMilestone(cat, req) {
    return cat.extended && (req === 5000 || req === 50000 || req === 250000);
  }

  function roman(n) {
    return ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"][n] || String(n);
  }

  function formatNumber(val) {
    const abs = Math.abs(val);
    if (abs >= 1e9) return (val / 1e9).toFixed(1) + "B";
    if (abs >= 1e6) return (val / 1e6).toFixed(1) + "M";
    if (abs >= 1e3) return (val / 1e3).toFixed(1) + "k";
    return String(val);
  }

  function percent(cur, req) {
    if (req <= 0) return 100;
    return Math.max(0, Math.min(100, Math.floor((cur * 100) / req)));
  }

  function percentText(cur, req) {
    if (req <= 0) return "100%";
    const v = Math.max(0, Math.min(100, (cur * 100) / req));
    if (v >= 10 || v === Math.round(v)) return `${Math.round(v)}%`;
    return `${v.toFixed(1)}%`;
  }

  function progressBarHtml(cur, req) {
    const p = percent(cur, req);
    const segs = 20;
    const filled = Math.round((segs * p) / 100);
    let bars = '<span class="mc-bar">';
    for (let i = 0; i < segs; i++) {
      bars += `<span class="mc-bar-seg ${i < filled ? "on" : "off"}"></span>`;
    }
    bars += `</span><span class="c-yellow">${formatNumber(cur)}</span><span class="c-gold">/</span><span class="c-yellow">${formatNumber(req)}</span>`;
    return bars;
  }

  function getAmount(cat, material) {
    return state.progress[cKey(cat, material)] || 0;
  }

  function getClaimedTier(cat, material) {
    return state.claims[cKey(cat, material)] || 0;
  }

  function collectionFortune(catId) {
    const cat = CATEGORY_MAP[catId];
    if (!cat || !cat.extended) return 0;
    let total = 0;
    for (const item of cat.items) {
      const amt = getAmount(cat, item.material);
      for (const th of [5000, 50000, 250000]) {
        if (amt >= th) total++;
      }
    }
    return Math.min(30, total);
  }

  function getSummary() {
    let totalItems = 0, unlockedItems = 0, completedItems = 0;
    let curTiers = 0, maxTiers = 0, claimableTiers = 0, totalCollected = 0;
    for (const cat of CATEGORIES) {
      const mt = maxTier(cat);
      for (const item of cat.items) {
        totalItems++;
        maxTiers += mt;
        const amt = getAmount(cat, item.material);
        totalCollected += amt;
        if (amt > 0) unlockedItems++;
        const t = currentTier(amt, cat);
        curTiers += t;
        if (t >= mt) completedItems++;
        claimableTiers += Math.max(0, t - getClaimedTier(cat, item.material));
      }
    }
    return { totalItems, unlockedItems, completedItems, curTiers, maxTiers, claimableTiers, totalCollected };
  }

  function getCategorySummary(cat) {
    const mt = maxTier(cat);
    let unlocked = 0, completed = 0, curTiers = 0, claimable = 0, totalAmt = 0;
    let firstMissing = "全部满级";
    for (const item of cat.items) {
      const amt = getAmount(cat, item.material);
      totalAmt += amt;
      if (amt > 0) unlocked++;
      const t = currentTier(amt, cat);
      curTiers += t;
      claimable += Math.max(0, t - getClaimedTier(cat, item.material));
      if (t >= mt) {
        completed++;
      } else if (firstMissing === "全部满级") {
        firstMissing = `${item.name} ${roman(t + 1)}`;
      }
    }
    return {
      totalItems: cat.items.length,
      unlocked,
      completed,
      curTiers,
      maxTiers: cat.items.length * mt,
      claimable,
      totalAmt,
      firstMissing
    };
  }

  function getRecipesForTier(material, tier) {
    const m = MATERIAL_RECIPES[material];
    if (!m) return [];
    return m[String(tier)] || [];
  }

  function getAllRecipeTiersForMaterial(material) {
    const m = MATERIAL_RECIPES[material];
    if (!m) return [];
    return Object.keys(m).map(Number).sort((a, b) => a - b);
  }

  function findCollectionByMaterial(material) {
    for (const cat of CATEGORIES) {
      const idx = cat.items.findIndex(it => it.material === material);
      if (idx >= 0) return { cat, item: cat.items[idx], itemIndex: idx };
    }
    return null;
  }

  // ---------- Web Audio 合成音效 ----------
  let audioCtx = null;
  function playTone(type) {
    if (!state.soundEnabled) return;
    try {
      audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
      const now = audioCtx.currentTime;
      const playNote = (freq, start, dur, vol = 0.08, wave = "triangle") => {
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.type = wave;
        osc.frequency.setValueAtTime(freq, start);
        gain.gain.setValueAtTime(vol, start);
        gain.gain.exponentialRampToValueAtTime(0.001, start + dur);
        osc.connect(gain);
        gain.connect(audioCtx.destination);
        osc.start(start);
        osc.stop(start + dur);
      };
      if (type === "levelup") {
        playNote(523.25, now, 0.12, 0.09);
        playNote(659.25, now + 0.09, 0.12, 0.09);
        playNote(783.99, now + 0.18, 0.16, 0.1);
        playNote(1046.5, now + 0.28, 0.25, 0.1);
      } else if (type === "claim") {
        playNote(880, now, 0.09, 0.08, "sine");
        playNote(1174.66, now + 0.07, 0.15, 0.09, "sine");
      } else if (type === "click") {
        playNote(420, now, 0.04, 0.04, "square");
      }
    } catch (_) {}
  }

  // ---------- 聊天栏与 ActionBar ----------
  function pushChat(html, onClick) {
    const line = document.createElement("div");
    line.className = "chat-line";
    line.innerHTML = html;
    if (onClick) {
      line.querySelectorAll(".chat-clickable").forEach(el => {
        el.addEventListener("click", onClick);
      });
    }
    elChatLog.appendChild(line);
    elChatLog.scrollTop = elChatLog.scrollHeight;
  }

  function showActionbar(html) {
    elActionbar.innerHTML = html;
    elActionbar.classList.add("visible");
    clearTimeout(state.actionbarTimer);
    state.actionbarTimer = setTimeout(() => {
      elActionbar.classList.remove("visible");
    }, 3600);
  }

  // ---------- 核心操作：改进度 & 领奖 ----------
  function setItemAmount(cat, material, newAmount, notify = true) {
    const key = cKey(cat, material);
    const oldAmt = state.progress[key] || 0;
    const safe = Math.max(0, Math.floor(newAmount));
    const oldT = currentTier(oldAmt, cat);
    if (safe <= 0) {
      delete state.progress[key];
    } else {
      state.progress[key] = safe;
    }
    const newT = currentTier(safe, cat);
    if (notify && newT > oldT) {
      const item = cat.items.find(i => i.material === material);
      const itemName = item ? item.name : material;
      const range = newT > oldT + 1 ? `${roman(oldT + 1)} ~ ${roman(newT)}` : roman(newT);
      pushChat(
        `<span class="c-gold bold">[收藏升级]</span> <span class="c-gray">${cat.title} · </span><span class="c-yellow bold">${itemName} ${range}</span> <span class="c-green">已解锁！</span> <span class="c-aqua bold chat-clickable">[点击领取奖励]</span>`,
        () => {
          state.view.type = "hub";
          render();
        }
      );
      showActionbar(`<span class="c-gold bold">★ 收藏升级：</span><span class="c-yellow">${itemName} ${roman(newT)}</span> <span class="c-gray">（输入 /collect 领奖）</span>`);
      playTone("levelup");
    }
  }

  function addItemAmount(cat, material, delta, notify = true) {
    const cur = getAmount(cat, material);
    setItemAmount(cat, material, cur + delta, notify);
  }

  function claimScope(catId = null) {
    const targetCats = catId ? [CATEGORY_MAP[catId]] : CATEGORIES;
    let itemsClaimed = 0, tiersClaimed = 0, mapleGain = 0;

    for (const cat of targetCats) {
      for (const item of cat.items) {
        const key = cKey(cat, item.material);
        const curT = currentTier(getAmount(cat, item.material), cat);
        const prev = state.claims[key] || 0;
        if (curT <= prev) continue;
        itemsClaimed++;
        state.claims[key] = curT;
        for (let t = prev + 1; t <= curT; t++) {
          tiersClaimed++;
          mapleGain += mapleXpReward(t);
        }
      }
    }

    if (tiersClaimed === 0) {
      pushChat(`<span class="c-yellow">当前没有可领取的收藏奖励。</span>`);
      return false;
    }

    state.mapleXp += mapleGain;

    pushChat(`<span class="c-green">收藏奖励领取完成！</span> <span class="c-gray">层数:</span> <span class="c-yellow">${tiersClaimed}</span> <span class="c-gray">| 项数:</span> <span class="c-yellow">${itemsClaimed}</span> <span class="c-gray">| 枫叶经验:</span> <span class="c-aqua">+${formatNumber(mapleGain)}</span>`);
    showActionbar(`<span class="c-green bold">✔ 已领取 ${tiersClaimed} 层奖励</span> <span class="c-aqua">+${formatNumber(mapleGain)} 枫叶经验</span>`);
    playTone("claim");
    return true;
  }

  function claimItemUpToTier(cat, item, targetTier) {
    const key = cKey(cat, item.material);
    const curT = currentTier(getAmount(cat, item.material), cat);
    const prev = state.claims[key] || 0;
    const eff = Math.min(curT, targetTier);
    if (eff <= prev) {
      if (targetTier > curT) {
        pushChat(`<span class="c-red">${item.name} ${roman(targetTier)} 尚未解锁，无法领取。</span>`);
      } else {
        pushChat(`<span class="c-yellow">${item.name} ${roman(targetTier)} 奖励已领取过了。</span>`);
      }
      return false;
    }

    let tiersClaimed = 0, mapleGain = 0;
    for (let t = prev + 1; t <= eff; t++) {
      tiersClaimed++;
      mapleGain += mapleXpReward(t);
    }
    state.claims[key] = eff;
    state.mapleXp += mapleGain;

    pushChat(`<span class="c-green">已领取 </span><span class="c-yellow bold">${item.name} ${roman(eff)}</span><span class="c-green"> 奖励（共 ${tiersClaimed} 层）：</span><span class="c-aqua">+${mapleGain} 枫叶经验</span>`);
    showActionbar(`<span class="c-green bold">✔ 领取 ${item.name} ${roman(eff)}</span> <span class="c-aqua">+${mapleGain} 枫叶经验</span>`);
    playTone("claim");
    return true;
  }

  // ---------- 预设存档 ----------
  function applyPreset(name) {
    state.progress = {};
    state.claims = {};
    state.mapleXp = 0;

    if (name === "newbie") {
      setItemAmount(CATEGORY_MAP.FARMING, "WHEAT", 80, false);
      setItemAmount(CATEGORY_MAP.FARMING, "CARROT", 60, false);
      setItemAmount(CATEGORY_MAP.MINING, "COAL", 120, false);
      setItemAmount(CATEGORY_MAP.MINING, "STONE", 310, false);
      setItemAmount(CATEGORY_MAP.FORAGING, "OAK_LOG", 75, false);
      setItemAmount(CATEGORY_MAP.COMBAT, "ROTTEN_FLESH", 55, false);
      pushChat(`<span class="c-aqua">[预设]</span> <span class="c-gray">已载入「新手开荒」存档。</span>`);
    } else if (name === "midgame") {
      // 农耕与挖矿给足可查看真实配方的进度，且故意留出一部分未领取的奖励
      const seed = [
        ["FARMING", "WHEAT", 6200, 4],
        ["FARMING", "CARROT", 3100, 3],
        ["FARMING", "POTATO", 1200, 2],
        ["FARMING", "PUMPKIN", 54000, 6],
        ["FARMING", "SUGAR_CANE", 11000, 5],
        ["FARMING", "COCOA_BEANS", 800, 1],
        ["FARMING", "SWEET_BERRIES", 420, 2],
        ["FARMING", "BEEF", 260, 0],
        ["MINING", "COAL", 14000, 5],
        ["MINING", "RAW_COPPER", 5200, 4],
        ["MINING", "RAW_IRON", 28000, 6],
        ["MINING", "REDSTONE", 55000, 7],
        ["MINING", "LAPIS_LAZULI", 6500, 3],
        ["MINING", "DIAMOND", 12500, 4],
        ["MINING", "STONE", 60000, 9],
        ["COMBAT", "ROTTEN_FLESH", 3200, 4],
        ["COMBAT", "BONE", 1500, 3],
        ["COMBAT", "ENDER_PEARL", 620, 2],
        ["COMBAT", "BLAZE_ROD", 280, 0],
        ["FORAGING", "OAK_LOG", 5800, 5],
        ["FORAGING", "SPRUCE_LOG", 1200, 2],
        ["FORAGING", "CHERRY_LOG", 350, 0],
        ["FISHING", "COD", 1400, 3],
        ["FISHING", "SALMON", 650, 1],
        ["FISHING", "COOKED_COD", 180, 0]
      ];
      for (const [cid, mat, amt, cl] of seed) {
        const cat = CATEGORY_MAP[cid];
        state.progress[cKey(cat, mat)] = amt;
        if (cl > 0) {
          state.claims[cKey(cat, mat)] = cl;
          for (let t = 1; t <= cl; t++) {
            state.mapleXp += mapleXpReward(t);
          }
        }
      }
      pushChat(`<span class="c-aqua">[预设]</span> <span class="c-gray">已载入「中期体验」存档：可体验多分类高亮待领、逐层领奖、3×3 职业装备合成与实时冲榜！</span>`);
    } else if (name === "endgame") {
      for (const cat of CATEGORIES) {
        const topReq = requirements(cat)[maxTier(cat) - 1];
        for (const item of cat.items) {
          state.progress[cKey(cat, item.material)] = topReq;
          state.claims[cKey(cat, item.material)] = maxTier(cat) - 1; // 留最后一层可领
          for (let t = 1; t <= maxTier(cat) - 1; t++) {
            state.mapleXp += mapleXpReward(t);
          }
        }
      }
      pushChat(`<span class="c-gold">[预设]</span> <span class="c-gray">已载入「满级大佬」存档（每项保留最高层待领取体验）。</span>`);
    } else {
      pushChat(`<span class="c-red">[预设]</span> <span class="c-gray">已清空全部收藏进度与领取记录。</span>`);
    }
    render();
  }

  // ---------- 实时排行榜计算 ----------
  function buildLeaderboard(catId) {
    const entries = NPC_PLAYERS.map(npc => {
      const total = catId
        ? (npc.base[catId] || 0)
        : Object.values(npc.base).reduce((a, b) => a + b, 0);
      return { name: npc.name, color: npc.color, total, isSelf: false };
    });

    const selfTotal = catId
      ? getCategorySummary(CATEGORY_MAP[catId]).totalAmt
      : getSummary().totalCollected;
    if (selfTotal > 0) {
      entries.push({ name: PLAYER_NAME, color: "#10b981", total: selfTotal, isSelf: true });
    }
    entries.sort((a, b) => b.total - a.total || a.name.localeCompare(b.name));
    return entries;
  }

  // ---------- 槽位构建器 ----------
  function makeFiller(paneClass) {
    return { filler: true, paneClass, tooltip: null };
  }

  function makeIcon(material, titleHtml, loreLines, opts = {}) {
    return {
      material,
      titleHtml,
      loreLines,
      glow: !!opts.glow,
      count: opts.count || null,
      badge: opts.badge || null,
      badgeClass: opts.badgeClass || "",
      colorHex: opts.colorHex || null,
      extraClass: opts.extraClass || "",
      onClick: opts.onClick || null,
      onRightClick: opts.onRightClick || null
    };
  }

  function makePane(paneClass, label, titleHtml, loreLines, opts = {}) {
    return {
      paneClass,
      paneLabel: label,
      titleHtml,
      loreLines,
      glow: !!opts.glow,
      count: opts.count || null,
      badge: opts.badge || null,
      onClick: opts.onClick || null,
      onRightClick: opts.onRightClick || null
    };
  }

  function makeHead(name, bgColor, titleHtml, loreLines, opts = {}) {
    return {
      headText: name.slice(0, 2).toUpperCase(),
      headColor: bgColor,
      titleHtml,
      loreLines,
      glow: !!opts.glow,
      onClick: opts.onClick || null
    };
  }

  function resolveCompressionBaseMaterial(token, depth = 0) {
    if (!token || depth > 8) return null;
    if (token.startsWith("vanilla:")) return token.slice(8);
    const pi = PROF_ITEMS[token];
    if (!pi) return null;
    if (pi.compression) return resolveCompressionBaseMaterial(pi.compression, depth + 1);
    return pi.material || null;
  }

  function compressionTierOf(pi) {
    if (!pi || !pi.compression) return 0;
    const rv = pi.rawValue || 0;
    if (rv >= 6561) return 4;
    if (rv >= 729) return 3;
    if (rv >= 81) return 2;
    return 1;
  }

  function resolveProfessionVisual(pi) {
    if (!pi) return { material: "PAPER", glow: false, badge: null, badgeClass: "" };
    if (pi.material === "PAPER" && pi.compression) {
      const baseMat = resolveCompressionBaseMaterial(pi.compression) || "PAPER";
      const cTier = compressionTierOf(pi);
      return {
        material: baseMat,
        glow: true,
        badge: `压${roman(cTier)}`,
        badgeClass: `badge-c${cTier}`
      };
    }
    return { material: pi.material, glow: false, badge: null, badgeClass: "" };
  }

  function tokenDisplay(token) {
    if (!token) return { name: "空气", material: "AIR", profItem: null, visual: null };
    if (token.startsWith("vanilla:")) {
      const mat = token.slice(8);
      return { name: VANILLA_NAMES[mat] || mat, material: mat, profItem: null, visual: null };
    }
    const pi = PROF_ITEMS[token];
    if (!pi) return { name: token, material: "PAPER", profItem: null, visual: null };
    const visual = resolveProfessionVisual(pi);
    return { name: pi.name, material: visual.material, profItem: pi, visual };
  }

  function buildProfessionItemLore(pi) {
    const lines = [];
    const jobLabel = pi.job === "farming" ? "农业" : (pi.job === "mining" ? "挖矿" : "通用");
    if (pi.kind !== "material") {
      lines.push(`<span class="c-gray">${jobLabel}等级要求：<span class="c-yellow">Lv.${pi.level}</span></span>`);
    }
    if (pi.kind === "armor" && pi.setStats) {
      const s = pi.setStats;
      lines.push(`<span class="c-green">生命 +${s.health / 4}</span>  <span class="c-aqua">防御 +${s.defense / 4}</span>`);
      if (s.fortune > 0) lines.push(`<span class="c-yellow">职业幸运 +${s.fortune / 5}</span>`);
      if (s.xp > 0) lines.push(`<span class="c-aqua">职业经验 +${s.xp / 5}%</span>`);
      if (s.rare > 0) lines.push(`<span class="c-light_purple">稀有概率 +${s.rare / 5}%</span>`);
      if (s.event > 0) lines.push(`<span class="c-gold">事件概率 +${s.event / 5}%</span>`);
      lines.push(`<span class="c-gray">四件同套（${pi.setName}）：加成补足至整套数值</span>`);
      if (s.reduction > 0) lines.push(`<span class="c-green">同套职业怪物减伤 ${s.reduction}%</span>`);
    } else if (pi.kind === "tool") {
      lines.push(`<span class="c-yellow">职业幸运 +${pi.fortune}</span>  <span class="c-aqua">职业经验 +${pi.xp}%</span>`);
      if (pi.efficiency > 0) lines.push(`<span class="c-light_purple">自带效率 ${roman(pi.efficiency)}</span>`);
      if (pi.replant) lines.push(`<span class="c-green">成熟作物自动复种（消耗背包种子）</span>`);
    } else if (pi.kind === "weapon") {
      lines.push(`<span class="c-red">伤害 ${pi.damage}</span>  <span class="c-gold">力量 +${pi.strength}</span>`);
      lines.push(`<span class="c-yellow">对应职业怪物伤害 +${pi.mobBonus}%</span>`);
    } else if (pi.rawValue > 0) {
      lines.push(`<span class="c-aqua">压缩材料：相当于 ${pi.rawValue} 个基础原料</span>`);
    }

    // 收藏解锁门槛校验
    if (pi.requirements) {
      const req = pi.requirements;
      const reqLines = [];
      const cat = pi.job === "farming" ? CATEGORY_MAP.FARMING : CATEGORY_MAP.MINING;
      if (req.collections) {
        for (const [mat, need] of Object.entries(req.collections)) {
          const cur = getAmount(cat, mat);
          const ok = cur >= need;
          const cname = VANILLA_NAMES[mat] || mat;
          reqLines.push(`<span class="${ok ? "c-green" : "c-red"}">${ok ? "✔" : "✖"} ${cname} 收藏 ${formatNumber(cur)}/${formatNumber(need)}</span>`);
        }
      }
      if (req.diverse_count > 0) {
        const crops = ["WHEAT", "CARROT", "POTATO", "BEETROOT", "PUMPKIN", "SUGAR_CANE", "COCOA_BEANS"];
        const met = crops.filter(m => getAmount(CATEGORY_MAP.FARMING, m) >= req.diverse_amount).length;
        const ok = met >= req.diverse_count;
        reqLines.push(`<span class="${ok ? "c-green" : "c-red"}">${ok ? "✔" : "✖"} 任意 ${req.diverse_count} 种主作物各 ${formatNumber(req.diverse_amount)}（已达 ${met} 种）</span>`);
      }
      if (req.normal > 0) reqLines.push(`<span class="c-gray">• 普通职业事件击败 ${req.normal} 次</span>`);
      if (req.elite > 0) reqLines.push(`<span class="c-gray">• 精英职业事件击败 ${req.elite} 次</span>`);
      if (reqLines.length > 0) {
        lines.push("", `<span class="c-gold">合成解锁条件：</span>`, ...reqLines);
      }
    }
    return lines;
  }

  function rgbIntToHex(rgb) {
    if (!rgb || rgb <= 0) return null;
    return "#" + Number(rgb).toString(16).padStart(6, "0");
  }

  // ---------- 8 个 GUI 页面渲染器 ----------
  function buildSlots() {
    const v = state.view;
    if (v.type === "main") return buildMainGui();
    if (v.type === "hub") return buildHubGui();
    if (v.type === "category") return buildCategoryGui();
    if (v.type === "detail") return buildDetailGui();
    if (v.type === "tierRecipe") return buildTierRecipeGui();
    if (v.type === "leaderboard") return buildLeaderboardGui();
    if (v.type === "missing") return buildMissingGui();
    if (v.type === "guide") return buildGuideGui();
    return buildHubGui();
  }

  function buildMainGui() {
    elTitle.textContent = "主页 (/info-new)";
    elHint.textContent = "点击书架「收集中心」进入收藏玩法";
    const slots = Array.from({ length: 54 }, () => makeFiller("pane-black"));
    const sum = getSummary();
    const pct = sum.totalItems > 0 ? Math.floor((sum.unlockedItems * 100) / sum.totalItems) : 0;

    slots[13] = makeHead(PLAYER_NAME, "#10b981", `<span class="c-yellow">${PLAYER_NAME} 的头像</span>`, [
      `<span class="c-gray">枫叶经验：<span class="c-aqua">${formatNumber(state.mapleXp)}</span></span>`,
      `<span class="c-gray">总收藏等级：<span class="c-yellow">${sum.curTiers}/${sum.maxTiers}</span></span>`
    ]);

    slots[19] = makeIcon("COPPER_SWORD", `<span class="c-mint bold">你的技能等级</span>`, [
      `<span class="c-gray">查看你的技能进度和奖励。</span>`, "", `<span class="c-gray">（本模拟器聚焦收集中心流程）</span>`
    ]);

    slots[20] = makeIcon("BOOKSHELF", `<span class="c-mint bold">收集中心</span>`, [
      `<span class="c-gray">收集更多物品，解锁奖励，帮助你成为枫叶服务器高手！</span>`,
      "",
      `<span class="c-gray">收藏物品已解锁: <span class="c-yellow">${pct}</span><span class="c-gold">%</span></span>`,
      progressBarHtml(sum.unlockedItems, sum.totalItems),
      sum.claimableTiers > 0 ? `<span class="c-gold bold">✦ 当前有 ${sum.claimableTiers} 层奖励待领取！</span>` : "",
      "",
      `<span class="c-yellow bold">点击进入收集中心！</span>`
    ].filter(x => x !== null), {
      glow: sum.claimableTiers > 0,
      onClick: () => { state.view.type = "hub"; playTone("click"); render(); }
    });

    slots[21] = makeIcon("ITEM_FRAME", `<span class="c-mint bold">配方书</span>`, [`<span class="c-gray">收藏等级点进去也可直接预览 3×3 合成配方！</span>`, "", `<span class="c-yellow bold">开发中</span>`]);
    slots[22] = makeIcon("WRITABLE_BOOK", `<span class="c-mint bold">任务日志</span>`, [`<span class="c-gray">查看你当前正在进行的任务。</span>`]);
    slots[23] = makeIcon("BOOK", `<span class="c-mint bold">传送点</span>`, [`<span class="c-gray">快速前往各个地区。</span>`]);
    slots[24] = makeIcon("CHEST_MINECART", `<span class="c-mint bold">公会</span>`, [`<span class="c-gray">查看你的公会信息与成员。</span>`]);
    slots[25] = makeIcon("ENDER_CHEST", `<span class="c-mint bold">背包</span>`, [`<span class="c-gray">查看随身背包空间。</span>`]);
    slots[30] = makeIcon("GOLD_INGOT", `<span class="c-mint bold">银行</span>`, [`<span class="c-gray">查看存款与金币管理。</span>`]);
    slots[31] = makeIcon("CRAFTING_TABLE", `<span class="c-mint bold">工作台</span>`, [`<span class="c-gray">查看合成与制作。</span>`]);
    slots[32] = makeIcon("COPPER_CHESTPLATE", `<span class="c-mint bold">衣柜</span>`, [`<span class="c-gray">快速切换装备套装。</span>`]);
    slots[49] = makeIcon("BARRIER", `<span class="c-gold bold">关闭界面</span>`, [`<span class="c-gray">模拟关闭菜单（返回收集中心）</span>`], {
      onClick: () => { pushChat(`<span class="c-gray">已关闭主信息菜单。</span>`); }
    });
    return slots;
  }

  function buildHubGui() {
    elTitle.textContent = "收集中心 (/collect)";
    elHint.textContent = "点击 5 大分类查看物品，或点击下方功能按钮";
    const slots = Array.from({ length: 54 }, () => makeFiller("pane-black"));
    const sum = getSummary();

    slots[4] = makeIcon("BOOKSHELF", `<span class="c-gold bold">收集中心</span>`, [
      `<span class="c-gray">这里是收藏玩法的统一中心。</span>`,
      "",
      `<span class="c-gray">当前总收藏等级: <span class="c-yellow">${sum.curTiers}/${sum.maxTiers}</span></span>`,
      `<span class="c-gray">已解锁收藏项: <span class="c-aqua">${sum.unlockedItems}/${sum.totalItems}</span></span>`,
      `<span class="c-gray">已满级收藏项: <span class="c-green">${sum.completedItems}/${sum.totalItems}</span></span>`,
      `<span class="c-gray">可领取奖励层数: <span class="${sum.claimableTiers > 0 ? "c-gold" : "c-dark_gray"}">${sum.claimableTiers}</span></span>`,
      `<span class="c-gray">累计收藏数量: <span class="c-aqua">${formatNumber(sum.totalCollected)}</span></span>`
    ]);

    const catSlots = [20, 21, 22, 23, 24];
    CATEGORIES.forEach((cat, idx) => {
      const cs = getCategorySummary(cat);
      slots[catSlots[idx]] = makeIcon(cat.icon, `<span class="c-gold bold">${cat.title}</span>`, [
        `<span class="c-gray">${cat.description}</span>`,
        "",
        `<span class="c-gray">已解锁物品: <span class="c-aqua">${cs.unlocked}/${cs.totalItems}</span></span>`,
        `<span class="c-gray">已满级物品: <span class="c-green">${cs.completed}/${cs.totalItems}</span></span>`,
        `<span class="c-gray">当前收藏等级: <span class="c-yellow">${cs.curTiers}/${cs.maxTiers}</span></span>`,
        `<span class="c-gray">可领取奖励层数: <span class="${cs.claimable > 0 ? "c-gold" : "c-dark_gray"}">${cs.claimable}</span></span>`,
        `<span class="c-gray">首个未满级物品: <span class="c-aqua">${cs.firstMissing}</span></span>`,
        "",
        `<span class="c-yellow">点击打开详细收藏页。</span>`
      ], {
        glow: cs.claimable > 0,
        badge: cs.claimable > 0 ? `+${cs.claimable}` : null,
        onClick: () => {
          state.view.type = "category";
          state.view.categoryId = cat.id;
          state.view.page = 0;
          playTone("click");
          render();
        }
      });
    });

    slots[28] = makeIcon("AMETHYST_SHARD", `<span class="c-gold bold">一键领取奖励</span>`, [
      `<span class="c-gray">一键领取所有已解锁且未领取的收藏奖励。</span>`,
      "",
      `<span class="${sum.claimableTiers > 0 ? "c-gold" : "c-dark_gray"}">可领取层数: ${sum.claimableTiers}</span>`,
      `<span class="c-gray">自动发放枫叶经验。</span>`,
      "",
      `<span class="${sum.claimableTiers > 0 ? "c-green" : "c-yellow"}">${sum.claimableTiers > 0 ? "点击立即领取！" : "当前暂无可领取奖励。"}</span>`
    ], {
      glow: sum.claimableTiers > 0,
      onClick: () => { claimScope(null); render(); }
    });

    slots[30] = makeIcon("HOPPER", `<span class="c-gold bold">待领与目标清单</span>`, [
      `<span class="c-gray">查看待领取的奖励项与最接近升级的收藏目标。</span>`,
      "",
      `<span class="${sum.claimableTiers > 0 ? "c-gold" : "c-dark_gray"}">当前可领取层数: ${sum.claimableTiers}</span>`,
      `<span class="c-gray">点击项目可直达该物品详情页领奖。</span>`,
      "",
      `<span class="c-yellow">点击打开清单。</span>`
    ], {
      onClick: () => {
        state.view.type = "missing";
        state.view.missingCategoryId = null;
        state.view.page = 0;
        playTone("click");
        render();
      }
    });

    slots[32] = makeIcon("GOLD_INGOT", `<span class="c-gold bold">收藏排行榜</span>`, [
      `<span class="c-gray">查看全服收藏总量实时排行榜（含在线最新进度）。</span>`,
      `<span class="c-gray">支持按分类筛选、翻页浏览并显示你的个人排名。</span>`,
      "",
      `<span class="c-yellow">点击打开排行榜。</span>`
    ], {
      onClick: () => {
        state.view.type = "leaderboard";
        state.view.lbCategoryId = null;
        state.view.page = 0;
        playTone("click");
        render();
      }
    });

    slots[34] = makeIcon("WRITABLE_BOOK", `<span class="c-gold bold">收藏指南</span>`, [
      `<span class="c-gray">查看各类收藏获取途径、防刷规则、</span>`,
      `<span class="c-gray">职业采集幸运加成与常用指令说明。</span>`,
      "",
      `<span class="c-yellow">点击打开指南。</span>`
    ], {
      onClick: () => {
        state.view.type = "guide";
        playTone("click");
        render();
      }
    });

    slots[45] = makeIcon("ARROW", `<span class="c-gold bold">返回主页</span>`, [
      `<span class="c-gray">回到个人主信息菜单 (/info-new)。</span>`
    ], {
      onClick: () => { state.view.type = "main"; playTone("click"); render(); }
    });

    slots[49] = makeIcon("BARRIER", `<span class="c-gold bold">关闭界面</span>`, [
      `<span class="c-gray">点击关闭这个界面。</span>`
    ], {
      onClick: () => pushChat(`<span class="c-gray">已关闭收集中心界面（可随时继续点击或输入 /collect）。</span>`)
    });
    return slots;
  }

  function buildCategoryGui() {
    const cat = CATEGORY_MAP[state.view.categoryId] || CATEGORIES[0];
    const itemSlots = [
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34,
      37, 38, 39, 40, 41, 42, 43
    ];
    const maxPage = Math.max(0, Math.floor((cat.items.length - 1) / itemSlots.length));
    const page = Math.max(0, Math.min(state.view.page, maxPage));
    state.view.page = page;

    elTitle.textContent = `${cat.title}${maxPage > 0 ? ` (${page + 1}/${maxPage + 1})` : ""}`;
    elHint.textContent = "点击任意收藏物品查看等级玻璃板与合成配方";

    const slots = Array.from({ length: 54 }, () => makeFiller("pane-black"));
    const cs = getCategorySummary(cat);

    slots[4] = makeIcon(cat.icon, `<span class="c-gold bold">${cat.title}</span>`, [
      `<span class="c-gray">${cat.description}</span>`,
      "",
      `<span class="c-gray">已解锁物品: <span class="c-yellow">${cs.unlocked}/${cs.totalItems}</span></span>`,
      `<span class="c-gray">已满级物品: <span class="c-green">${cs.completed}/${cs.totalItems}</span></span>`,
      `<span class="c-gray">分类层级进度: <span class="c-yellow">${percentText(cs.curTiers, cs.maxTiers)}</span></span>`,
      progressBarHtml(cs.curTiers, cs.maxTiers),
      `<span class="c-gray">可领取奖励层数: <span class="${cs.claimable > 0 ? "c-gold" : "c-dark_gray"}">${cs.claimable}</span></span>`
    ]);

    const start = page * itemSlots.length;
    for (let i = 0; i < itemSlots.length; i++) {
      const idx = start + i;
      if (idx >= cat.items.length) break;
      const item = cat.items[idx];
      const collected = getAmount(cat, item.material);
      const mt = maxTier(cat);
      const ct = currentTier(collected, cat);
      const claimed = getClaimedTier(cat, item.material);
      const claimable = Math.max(0, ct - claimed);
      const maxed = ct >= mt;
      const nextT = Math.min(mt, ct + 1);
      const req = requirements(cat)[nextT - 1];
      const hasRecipes = getAllRecipeTiersForMaterial(item.material).length > 0;

      const lore = [
        `<span class="c-gray">${item.description}</span>`,
        "",
        `<span class="c-gray">当前等级: <span class="${ct > 0 ? "c-yellow" : "c-dark_gray"}">${ct > 0 ? roman(ct) : "未解锁"}</span> <span class="c-dark_gray">(${ct}/${mt})</span></span>`
      ];
      if (maxed) {
        lore.push(`<span class="c-gray">进度状态: <span class="c-green">已满级 (100%)</span></span>`);
        lore.push(progressBarHtml(collected, req));
      } else {
        lore.push(`<span class="c-gray">距离 ${item.name} ${roman(nextT)}: <span class="c-yellow">${percentText(collected, req)}</span></span>`);
        lore.push(progressBarHtml(collected, req));
        lore.push("", `<span class="c-gray">下一级解锁奖励:</span>`);
        lore.push(`<span class="c-dark_gray"> +<span class="c-aqua">${mapleXpReward(nextT)} 枫叶经验</span></span>`);
        if (isFortuneMilestone(cat, req)) {
          lore.push(`<span class="c-dark_gray"> +<span class="c-green">1 职业采集幸运（全局上限 +30）</span></span>`);
        }
      }
      if (claimable > 0) {
        lore.push("", `<span class="c-gold bold">✦ 有 ${claimable} 层奖励可领取！</span>`);
      }
      lore.push("", `<span class="c-yellow bold">点击查看等级与合成详情！</span>`);

      slots[itemSlots[i]] = makeIcon(item.material, `<span class="c-gold bold">${item.name}</span>`, lore, {
        glow: claimable > 0,
        badge: hasRecipes ? "⚒" : (claimable > 0 ? `+${claimable}` : null),
        onClick: () => {
          state.view.type = "detail";
          state.view.categoryId = cat.id;
          state.view.itemIndex = idx;
          syncInspectorSelection(cat.id, item.material);
          playTone("click");
          render();
        }
      });
    }

    slots[45] = makeIcon("ARROW", `<span class="c-gold bold">返回收集中心</span>`, [`<span class="c-gray">点击返回收集中心首页。</span>`], {
      onClick: () => { state.view.type = "hub"; playTone("click"); render(); }
    });
    if (page > 0) {
      slots[48] = makeIcon("ARROW", `<span class="c-gold bold">上一页</span>`, [`<span class="c-gray">切换到第 ${page} 页。</span>`], {
        onClick: () => { state.view.page = page - 1; playTone("click"); render(); }
      });
    }
    slots[49] = makeIcon("BARRIER", `<span class="c-gold bold">关闭界面</span>`, [`<span class="c-gray">回到收集中心。</span>`], {
      onClick: () => { state.view.type = "hub"; render(); }
    });
    if (page < maxPage) {
      slots[50] = makeIcon("ARROW", `<span class="c-gold bold">下一页</span>`, [`<span class="c-gray">切换到第 ${page + 2} 页。</span>`], {
        onClick: () => { state.view.page = page + 1; playTone("click"); render(); }
      });
    }
    return slots;
  }

  function buildDetailGui() {
    const cat = CATEGORY_MAP[state.view.categoryId] || CATEGORIES[0];
    const item = cat.items[state.view.itemIndex] || cat.items[0];
    elTitle.textContent = `${item.name} 收藏详情`;
    elHint.textContent = "左键点等级玻璃板 → 看奖励与 3×3 合成配方 | 右键快捷领奖";

    const slots = Array.from({ length: 54 }, () => makeFiller("pane-gray"));
    const collected = getAmount(cat, item.material);
    const reqs = requirements(cat);
    const mt = reqs.length;
    const ct = currentTier(collected, cat);
    const claimed = getClaimedTier(cat, item.material);
    const claimable = Math.max(0, ct - claimed);

    slots[4] = makeIcon(item.material, `<span class="c-gold bold">${item.name}</span>`, [
      `<span class="c-gray">${item.description}</span>`,
      "",
      `<span class="c-gray">当前累计: <span class="c-aqua">${formatNumber(collected)}</span></span>`,
      `<span class="c-gray">当前等级: <span class="c-yellow">${ct > 0 ? roman(ct) : "未解锁"} (${ct}/${mt})</span></span>`,
      `<span class="c-gray">已领奖励: <span class="c-green">${claimed > 0 ? roman(claimed) : "无"}</span></span>`,
      claimable > 0 ? `<span class="c-gold bold">✦ 当前有 ${claimable} 层待领取奖励！</span>` : `<span class="c-gray">点击下方任意等级查看该层解锁的合成配方与奖励。</span>`
    ], { glow: claimable > 0 });

    const tierSlots = [18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30];
    for (let i = 0; i < mt && i < tierSlots.length; i++) {
      const tier = i + 1;
      const req = reqs[i];
      const prevReq = i === 0 ? 0 : reqs[i - 1];
      const tierRecipes = getRecipesForTier(item.material, tier);

      let paneClass, nameColor, statusHtml, glow = false;
      if (collected >= req) {
        if (tier > claimed) {
          paneClass = "pane-light-blue";
          nameColor = "c-aqua";
          statusHtml = `<span class="c-gold bold">✦ 已解锁（左键看配方 / 右键直接领奖）</span>`;
          glow = true;
        } else {
          paneClass = "pane-lime";
          nameColor = "c-green";
          statusHtml = `<span class="c-green">✔ 奖励已领取</span>`;
        }
      } else if (collected >= prevReq) {
        paneClass = "pane-yellow";
        nameColor = "c-yellow";
        statusHtml = `<span class="c-yellow">进行中</span>`;
      } else {
        paneClass = "pane-red";
        nameColor = "c-red";
        statusHtml = `<span class="c-red">未解锁</span>`;
      }

      const lore = [
        `<span class="c-gray">状态: </span>${statusHtml}`,
        "",
        `<span class="c-gray">当前进度: <span class="c-yellow">${percentText(collected, req)}</span></span>`,
        progressBarHtml(collected, req),
        "",
        `<span class="c-gray">层级奖励:</span>`,
        `<span class="c-dark_gray"> +<span class="c-aqua">${mapleXpReward(tier)} 枫叶经验</span></span>`
      ];
      if (isFortuneMilestone(cat, req)) {
        lore.push(`<span class="c-dark_gray"> +<span class="c-green">1 职业采集幸运（自动生效，上限 +30）</span></span>`);
      }
      if (tierRecipes.length > 0) {
        lore.push("", `<span class="c-light_purple bold">⚒ 本层关联 ${tierRecipes.length} 个合成配方：</span>`);
        tierRecipes.slice(0, 4).forEach(r => {
          const pi = PROF_ITEMS[r.itemId];
          lore.push(`<span class="c-gray">  • <span class="c-gold">${pi ? pi.name : r.itemId}</span></span>`);
        });
        if (tierRecipes.length > 4) {
          lore.push(`<span class="c-dark_gray">  ... 共 ${tierRecipes.length} 个配方</span>`);
        }
      }
      lore.push("", `<span class="c-yellow bold">点击进入本级详情</span>`);
      if (collected >= req && tier > claimed) {
        lore.push(`<span class="c-aqua">右键：立即领取至 ${roman(tier)} 级奖励</span>`);
      }

      slots[tierSlots[i]] = makePane(
        paneClass,
        roman(tier),
        `<span class="${nameColor} bold">${item.name} ${roman(tier)}</span>`,
        lore,
        {
          glow,
          count: tier,
          badge: tierRecipes.length > 0 ? `⚒${tierRecipes.length}` : null,
          onClick: () => {
            state.view.type = "tierRecipe";
            state.view.tier = tier;
            state.view.recipeIdx = 0;
            state.view.recipeStack = [];
            playTone("click");
            render();
          },
          onRightClick: () => {
            claimItemUpToTier(cat, item, tier);
            render();
          }
        }
      );
    }

    slots[48] = makeIcon("ARROW", `<span class="c-gold bold">返回</span>`, [`<span class="c-gray">点击返回 ${cat.title}。</span>`], {
      onClick: () => {
        state.view.type = "category";
        state.view.page = Math.floor(state.view.itemIndex / 28);
        playTone("click");
        render();
      }
    });
    slots[50] = makeIcon("BARRIER", `<span class="c-gold bold">关闭界面</span>`, [`<span class="c-gray">回到收集中心。</span>`], {
      onClick: () => { state.view.type = "hub"; render(); }
    });
    return slots;
  }

  function buildTierRecipeGui() {
    const cat = CATEGORY_MAP[state.view.categoryId] || CATEGORIES[0];
    const item = cat.items[state.view.itemIndex] || cat.items[0];
    const reqs = requirements(cat);
    const mt = reqs.length;
    const tier = Math.max(1, Math.min(state.view.tier, mt));
    state.view.tier = tier;

    const req = reqs[tier - 1];
    const collected = getAmount(cat, item.material);
    const unlocked = collected >= req;
    const claimed = getClaimedTier(cat, item.material);
    const isClaimed = claimed >= tier;
    const tierRecipes = getRecipesForTier(item.material, tier);
    const recipeIdx = Math.max(0, Math.min(state.view.recipeIdx, Math.max(0, tierRecipes.length - 1)));
    state.view.recipeIdx = recipeIdx;
    const drillStack = Array.isArray(state.view.recipeStack)
      ? state.view.recipeStack.filter(id => PROF_ITEMS[id] && (PROF_ITEMS[id].compression || PROF_ITEMS[id].recipe))
      : [];
    state.view.recipeStack = drillStack;
    const isDrilling = drillStack.length > 0;
    const rootRef = tierRecipes.length > 0 ? tierRecipes[recipeIdx] : null;
    const rootPi = rootRef ? PROF_ITEMS[rootRef.itemId] : null;
    const activePi = isDrilling ? PROF_ITEMS[drillStack[drillStack.length - 1]] : rootPi;

    if (isDrilling && activePi) {
      elTitle.textContent = `${activePi.name} · 原料合成配方`;
      elHint.textContent = "已导航查看该原料的 3×3 合成配方 · 可继续点击可合成原料，或点击左下角返回上一层";
    } else {
      elTitle.textContent = `${item.name} ${roman(tier)} · 奖励与合成配方`;
      elHint.textContent = "左侧为 3×3 合成台摆放，右侧为成品，点击可合成原料可逐级导航查看合成";
    }

    const slots = Array.from({ length: 54 }, () => makeFiller("pane-gray"));

    // 顶部左侧：物品摘要
    slots[2] = makeIcon(item.material, `<span class="c-gold bold">${item.name}</span>`, [
      `<span class="c-gray">当前累计: <span class="c-aqua">${formatNumber(collected)}</span> / <span class="c-yellow">${formatNumber(req)}</span></span>`,
      progressBarHtml(collected, req),
      "",
      `<span class="c-gray">可在右侧调试面板直接加减本物品数量测试解锁效果。</span>`
    ]);

    // 顶部中央：当前层级玻璃板
    const paneClass = unlocked ? (isClaimed ? "pane-lime" : "pane-light-blue") : "pane-red";
    const tierLore = [
      `<span class="c-gray">解锁需求: <span class="c-yellow">${formatNumber(req)} 个 ${item.name}</span></span>`,
      `<span class="c-gray">本层状态: ${unlocked ? (isClaimed ? '<span class="c-green">✔ 已领取</span>' : '<span class="c-gold bold">✦ 已解锁待领取</span>') : '<span class="c-red">✖ 未解锁</span>'}</span>`,
      "",
      `<span class="c-gray">固定奖励:</span>`,
      `<span class="c-dark_gray"> +<span class="c-aqua">${mapleXpReward(tier)} 枫叶经验</span></span>`
    ];
    if (isFortuneMilestone(cat, req)) {
      tierLore.push(`<span class="c-dark_gray"> +<span class="c-green">1 职业采集幸运（全局上限 +30）</span></span>`);
    }
    slots[4] = makePane(paneClass, roman(tier), `<span class="c-gold bold">${item.name} ${roman(tier)} 级</span>`, tierLore, {
      glow: unlocked && !isClaimed,
      count: tier
    });

    // 顶部右侧：领取本层奖励按钮（已领取后不再显示）
    if (!isClaimed) {
      slots[6] = makeIcon(
        "AMETHYST_SHARD",
        `<span class="c-gold bold">${unlocked ? "点击领取本层奖励" : "尚未达到解锁门槛"}</span>`,
        [
          unlocked
            ? `<span class="c-green">点击将领取至 ${item.name} ${roman(tier)} 的全部未领奖励！</span>`
            : `<span class="c-red">还差 ${formatNumber(req - collected)} 个 ${item.name}。</span>`
        ],
        {
          glow: unlocked,
          onClick: () => { claimItemUpToTier(cat, item, tier); render(); }
        }
      );
    }

    // 3x3 合成工作台网格：slots [10,11,12, 19,20,21, 28,29,30]
    const craftSlots = [10, 11, 12, 19, 20, 21, 28, 29, 30];
    if (activePi) {
      const pi = activePi;
      // 按 ProfessionCrafting.java: shape("ANB", "APB", "ANB") 或 9格压缩
      const tokens = pi.compression
        ? Array(9).fill(pi.compression)
        : [
            pi.recipe.a, pi.recipe.n, pi.recipe.b,
            pi.recipe.a, pi.recipe.p, pi.recipe.b,
            pi.recipe.a, pi.recipe.n, pi.recipe.b
          ];

      tokens.forEach((tok, idx) => {
        const info = tokenDisplay(tok);
        const canDrill = !!(info.profItem && (info.profItem.compression || info.profItem.recipe));
        const colRef = !info.profItem ? findCollectionByMaterial(info.material) : null;
        const lore = info.profItem
          ? [
              `<span class="c-gray">来源：自定义职业物品 (<span class="c-yellow">${info.profItem.id}</span>)</span>`,
              "",
              ...buildProfessionItemLore(info.profItem),
              "",
              canDrill
                ? `<span class="c-aqua bold">▶ 点击进入查看「${info.name}」的 3×3 合成配方</span>`
                : `<span class="c-gray">（基础掉落材料，不可继续向下拆分）</span>`
            ].filter(Boolean)
          : [
              `<span class="c-gray">基础原材料：<span class="c-yellow">${info.name}</span> <span class="c-dark_gray">(${info.material})</span></span>`,
              `<span class="c-gray">工作台格位 #${idx + 1}</span>`,
              colRef
                ? `<span class="c-gray">当前已收集：<span class="c-aqua">${formatNumber(getAmount(colRef.cat, colRef.item.material))}</span> <span class="c-dark_gray">(${colRef.cat.title})</span></span>`
                : ""
            ].filter(Boolean);

        slots[craftSlots[idx]] = makeIcon(info.material, `<span class="c-yellow bold">${info.name}</span>`, lore, {
          glow: info.visual ? info.visual.glow : false,
          badge: info.visual ? info.visual.badge : null,
          badgeClass: info.visual ? info.visual.badgeClass : "",
          colorHex: info.profItem ? rgbIntToHex(info.profItem.color) : null,
          extraClass: "craft-grid",
          onClick: canDrill ? () => {
            if (drillStack[drillStack.length - 1] !== info.profItem.id) {
              state.view.recipeStack = [...drillStack, info.profItem.id];
            }
            pushChat(`<span class="c-aqua">[配方导航]</span> <span class="c-gray">已展开原料 <span class="c-gold">${info.name}</span> 的 3×3 合成配方（${info.profItem.compression ? `9 × ${tokenDisplay(info.profItem.compression).name} 压缩合成` : `3×3 职业合成表`}）。</span>`);
            playTone("click");
            render();
          } : null
        });
      });

      // 中间：工作台说明
      const chainNames = [
        ...(rootPi ? [rootPi.name] : []),
        ...drillStack.map(id => PROF_ITEMS[id].name)
      ];
      const craftSummaryLines = [
        `<span class="c-gray">当前配方：</span><span class="c-yellow">${pi.name}</span>`,
        `<span class="c-gray">合成类型：</span><span class="c-aqua">${pi.compression ? "九宫格压缩（9 合 1）" : "有序合成（ANB / APB / ANB）"}</span>`,
        pi.compression
          ? `<span class="c-gray">单次原料：</span><span class="c-green">9 × ${tokenDisplay(pi.compression).name}</span>`
          : "",
        isDrilling
          ? `<span class="c-gray">追踪路径：</span><span class="c-light_purple">${chainNames.join(" → ")}</span>`
          : (rootRef ? `<span class="c-gray">关联说明：</span><span class="c-green">${rootRef.reason}</span>` : ""),
        "",
        isDrilling
          ? `<span class="c-yellow">提示：点击下方追踪链或左下角箭头可逐级返回。</span>`
          : `<span class="c-gray">游戏内输入 /profession 可选择配方并在工作台合成。</span>`
      ].filter(Boolean);
      slots[22] = makeIcon("CRAFTING_TABLE", `<span class="c-gold bold">${isDrilling ? "3×3 合成规则（原料追踪）" : "3×3 合成规则"}</span>`, craftSummaryLines);

      // 右侧：合成成品槽
      const outVisual = resolveProfessionVisual(pi);
      slots[24] = makeIcon(outVisual.material, `<span class="c-gold bold">${pi.name}</span>`, [
        `<span class="c-gray">成品类型：<span class="c-yellow">${kindLabel(pi.kind)}</span>${pi.setName ? ` · 套装「${pi.setName}」` : ""}</span>`,
        "",
        ...buildProfessionItemLore(pi)
      ], {
        glow: true,
        badge: outVisual.badge,
        badgeClass: outVisual.badgeClass,
        colorHex: rgbIntToHex(pi.color),
        extraClass: "craft-output"
      });

      // 第 5 行（37..43）：下钻中显示配方追踪链，否则显示同一层级多配方切换栏
      const switcherSlots = [37, 38, 39, 40, 41, 42, 43];
      if (isDrilling) {
        const trail = [];
        if (rootPi) {
          trail.push({ pi: rootPi, depth: 0, label: "顶层配方" });
        }
        drillStack.forEach((id, i) => {
          trail.push({ pi: PROF_ITEMS[id], depth: i + 1, label: `原料追踪 #${i + 1}` });
        });
        const visibleTrail = trail.slice(-switcherSlots.length);
        visibleTrail.forEach((node, idx) => {
          const isCurrent = node.depth === drillStack.length;
          const subVisual = resolveProfessionVisual(node.pi);
          slots[switcherSlots[idx]] = makeIcon(
            subVisual.material,
            `<span class="${isCurrent ? "c-green" : "c-gold"} bold">${node.pi.name}</span>`,
            [
              `<span class="c-gray">${node.label}</span>`,
              `<span class="${isCurrent ? "c-green" : "c-yellow"}">${isCurrent ? "✔ 当前正在查看" : "点击返回查看此配方"}</span>`
            ],
            {
              glow: isCurrent || subVisual.glow,
              badge: subVisual.badge,
              badgeClass: subVisual.badgeClass,
              colorHex: rgbIntToHex(node.pi.color),
              onClick: isCurrent ? null : () => {
                state.view.recipeStack = drillStack.slice(0, node.depth);
                playTone("click");
                render();
              }
            }
          );
        });
      } else {
        tierRecipes.slice(0, switcherSlots.length).forEach((ref, idx) => {
          const subPi = PROF_ITEMS[ref.itemId];
          const subVisual = resolveProfessionVisual(subPi);
          const selected = idx === recipeIdx;
          slots[switcherSlots[idx]] = makeIcon(
            subVisual.material,
            `<span class="${selected ? "c-green" : "c-gold"} bold">${subPi.name}</span>`,
            [
              `<span class="c-gray">${ref.reason}</span>`,
              `<span class="${selected ? "c-green" : "c-yellow"}">${selected ? "✔ 当前正在预览" : "点击切换预览此合成配方"}</span>`
            ],
            {
              glow: selected || subVisual.glow,
              badge: subVisual.badge,
              badgeClass: subVisual.badgeClass,
              colorHex: rgbIntToHex(subPi.color),
              onClick: () => {
                state.view.recipeIdx = idx;
                state.view.recipeStack = [];
                playTone("click");
                render();
              }
            }
          );
        });
      }
    } else {
      // 该层级暂未绑定专属合成配方
      craftSlots.forEach(s => {
        slots[s] = makeFiller("pane-black");
      });
      slots[20] = makeIcon("CRAFTING_TABLE", `<span class="c-yellow bold">本层级暂无专属合成配方</span>`, [
        `<span class="c-gray">${item.name} ${roman(tier)} 当前版本发放固定枫叶经验奖励${isFortuneMilestone(cat, req) ? "与职业采集幸运" : ""}。</span>`,
        "",
        `<span class="c-gray">仅在包含合成配方的层级才会展示对应配方。</span>`
      ], { extraClass: "craft-grid" });

      slots[24] = makeIcon("EXPERIENCE_BOTTLE", `<span class="c-aqua bold">本层奖励预览</span>`, [
        `<span class="c-gray">• <span class="c-aqua">+${mapleXpReward(tier)} 枫叶经验</span></span>`,
        isFortuneMilestone(cat, req) ? `<span class="c-gray">• <span class="c-green">+1 职业采集幸运（自动生效）</span></span>` : ""
      ].filter(Boolean), { glow: unlocked && !isClaimed, extraClass: "craft-output" });
    }

    // 底部导航：下钻时优先提供「返回上一层配方」，否则返回物品详情 / 上一级 / 关闭 / 下一级
    if (isDrilling) {
      const prevPi = drillStack.length > 1 ? PROF_ITEMS[drillStack[drillStack.length - 2]] : rootPi;
      const prevName = prevPi ? prevPi.name : `${item.name} ${roman(tier)}`;
      slots[45] = makeIcon("ARROW", `<span class="c-aqua bold">返回上一层 (${prevName})</span>`, [
        `<span class="c-gray">退回上一个正在查看的合成配方。</span>`
      ], {
        onClick: () => {
          state.view.recipeStack = drillStack.slice(0, -1);
          playTone("click");
          render();
        }
      });
      slots[47] = makeIcon("ARROW", `<span class="c-gold bold">返回 ${item.name} 详情</span>`, [`<span class="c-gray">回到等级玻璃板总览。</span>`], {
        onClick: () => {
          state.view.recipeStack = [];
          state.view.type = "detail";
          playTone("click");
          render();
        }
      });
      slots[49] = makeIcon("BARRIER", `<span class="c-gold bold">关闭界面</span>`, [`<span class="c-gray">回到收集中心。</span>`], {
        onClick: () => {
          state.view.recipeStack = [];
          state.view.type = "hub";
          render();
        }
      });
    } else {
      slots[45] = makeIcon("ARROW", `<span class="c-gold bold">返回 ${item.name} 详情</span>`, [`<span class="c-gray">回到等级玻璃板总览。</span>`], {
        onClick: () => { state.view.type = "detail"; playTone("click"); render(); }
      });
      if (tier > 1) {
        slots[47] = makeIcon("ARROW", `<span class="c-yellow bold">上一级 (${roman(tier - 1)})</span>`, [`<span class="c-gray">查看 ${item.name} ${roman(tier - 1)} 的奖励与配方。</span>`], {
          onClick: () => { state.view.tier = tier - 1; state.view.recipeIdx = 0; state.view.recipeStack = []; playTone("click"); render(); }
        });
      }
      slots[49] = makeIcon("BARRIER", `<span class="c-gold bold">关闭界面</span>`, [`<span class="c-gray">回到收集中心。</span>`], {
        onClick: () => { state.view.type = "hub"; render(); }
      });
      if (tier < mt) {
        slots[51] = makeIcon("ARROW", `<span class="c-yellow bold">下一级 (${roman(tier + 1)})</span>`, [`<span class="c-gray">查看 ${item.name} ${roman(tier + 1)} 的奖励与配方。</span>`], {
          onClick: () => { state.view.tier = tier + 1; state.view.recipeIdx = 0; state.view.recipeStack = []; playTone("click"); render(); }
        });
      }
    }
    return slots;
  }

  function buildLeaderboardGui() {
    const lbCatId = state.view.lbCategoryId;
    const lbCat = lbCatId ? CATEGORY_MAP[lbCatId] : null;
    const entries = buildLeaderboard(lbCatId);
    const entrySlots = [28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43];
    const maxPage = Math.max(0, Math.floor((entries.length - 1) / entrySlots.length));
    const page = Math.max(0, Math.min(state.view.page, maxPage));
    state.view.page = page;

    const scopeTitle = lbCat ? lbCat.title : "全服收藏";
    elTitle.textContent = `${scopeTitle}排行榜${maxPage > 0 ? ` (${page + 1}/${maxPage + 1})` : ""}`;
    elHint.textContent = "点击全服总榜或 5 大分类切换榜单，支持翻页与实时合并你的最新进度";

    const slots = Array.from({ length: 54 }, () => makeFiller("pane-gray"));
    const selfIndex = entries.findIndex(e => e.isSelf);
    const selfTotal = lbCat ? getCategorySummary(lbCat).totalAmt : getSummary().totalCollected;

    slots[4] = makeIcon("COMPASS", `<span class="c-gold bold">${scopeTitle}榜单</span>`, [
      `<span class="c-gray">统计${lbCat ? "该分类" : "全服所有"}收藏总量（实时合并在线进度）。</span>`,
      "",
      `<span class="c-gray">你的当前排名: <span class="${selfIndex >= 0 ? "c-gold" : "c-dark_gray"}">${selfIndex >= 0 ? `第 #${selfIndex + 1} 名` : "暂未上榜"}</span> <span class="c-dark_gray">/ 共 ${entries.length} 人</span></span>`,
      `<span class="c-gray">你的收藏总量: <span class="c-aqua">${formatNumber(selfTotal)}</span></span>`
    ]);

    slots[19] = makeIcon("NETHER_STAR", `<span class="c-gold bold">全服总榜</span>`, [
      `<span class="c-gray">查看所有五大分类合计的收藏总量排名。</span>`,
      `<span class="${!lbCat ? "c-green" : "c-yellow"}">${!lbCat ? "✔ 当前已选中" : "点击切换"}</span>`
    ], {
      glow: !lbCat,
      onClick: () => { state.view.lbCategoryId = null; state.view.page = 0; playTone("click"); render(); }
    });

    const catBtns = [20, 21, 22, 23, 24];
    CATEGORIES.forEach((cat, idx) => {
      const sel = lbCatId === cat.id;
      slots[catBtns[idx]] = makeIcon(cat.icon, `<span class="c-gold bold">${cat.title}</span>`, [
        `<span class="c-gray">切换到 ${cat.title} 排行榜。</span>`,
        `<span class="${sel ? "c-green" : "c-yellow"}">${sel ? "✔ 当前已选中" : "点击切换"}</span>`
      ], {
        glow: sel,
        onClick: () => { state.view.lbCategoryId = cat.id; state.view.page = 0; playTone("click"); render(); }
      });
    });

    const start = page * entrySlots.length;
    for (let i = 0; i < entrySlots.length; i++) {
      const idx = start + i;
      if (idx >= entries.length) break;
      const e = entries[idx];
      const rank = idx + 1;
      const rankColor = rank === 1 ? "c-gold" : (rank === 2 ? "c-aqua" : (rank === 3 ? "c-green" : "c-yellow"));
      const lore = [
        `<span class="c-gray">${lbCat ? lbCat.title : "全服"} 收藏总量</span>`,
        "",
        `<span class="c-gray">累计数量: <span class="c-yellow">${formatNumber(e.total)}</span></span>`
      ];
      if (e.isSelf) lore.push("", `<span class="c-green bold">★ 这就是你！（增加进度可实时冲榜）</span>`);
      slots[entrySlots[i]] = makeHead(e.name, e.color, `<span class="${rankColor} bold">#${rank} ${e.name}</span>`, lore, {
        glow: e.isSelf
      });
    }

    slots[45] = makeIcon("ARROW", `<span class="c-gold bold">返回收集中心</span>`, [`<span class="c-gray">回到收集中心首页。</span>`], {
      onClick: () => { state.view.type = "hub"; playTone("click"); render(); }
    });
    if (page > 0) {
      slots[48] = makeIcon("ARROW", `<span class="c-gold bold">上一页</span>`, [`<span class="c-gray">切换到第 ${page} 页。</span>`], {
        onClick: () => { state.view.page = page - 1; playTone("click"); render(); }
      });
    }
    slots[49] = makeIcon("BARRIER", `<span class="c-gold bold">关闭界面</span>`, [`<span class="c-gray">回到收集中心。</span>`], {
      onClick: () => { state.view.type = "hub"; render(); }
    });
    if (page < maxPage) {
      slots[50] = makeIcon("ARROW", `<span class="c-gold bold">下一页</span>`, [`<span class="c-gray">切换到第 ${page + 2} 页。</span>`], {
        onClick: () => { state.view.page = page + 1; playTone("click"); render(); }
      });
    }
    return slots;
  }

  function buildMissingGui() {
    const filterCatId = state.view.missingCategoryId;
    const targetCats = filterCatId ? [CATEGORY_MAP[filterCatId]] : CATEGORIES;
    const entries = [];
    for (const cat of targetCats) {
      const mt = maxTier(cat);
      const reqs = requirements(cat);
      cat.items.forEach((item, itemIndex) => {
        const amt = getAmount(cat, item.material);
        const ct = currentTier(amt, cat);
        const cl = getClaimedTier(cat, item.material);
        const claimable = Math.max(0, ct - cl);
        if (claimable <= 0 && ct >= mt) return;
        const nextReq = reqs[Math.min(mt, ct + 1) - 1];
        const ratio = nextReq > 0 ? Math.min(1, amt / nextReq) : 1;
        entries.push({ cat, item, itemIndex, amt, ct, mt, cl, claimable, nextReq, ratio });
      });
    }
    entries.sort((a, b) =>
      (b.claimable > 0) - (a.claimable > 0) ||
      b.claimable - a.claimable ||
      b.ratio - a.ratio
    );

    const listSlots = [
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34,
      37, 38, 39, 40, 41, 42, 43
    ];
    const maxPage = Math.max(0, Math.floor((entries.length - 1) / listSlots.length));
    const page = Math.max(0, Math.min(state.view.page, maxPage));
    state.view.page = page;

    const claimableItems = entries.filter(e => e.claimable > 0).length;
    const claimableTiers = entries.reduce((s, e) => s + e.claimable, 0);

    elTitle.textContent = `待领取与推进目标${maxPage > 0 ? ` (${page + 1}/${maxPage + 1})` : ""}`;
    elHint.textContent = "发光项代表有未领取奖励，点击任意物品直达详情页";

    const slots = Array.from({ length: 54 }, () => makeFiller("pane-black"));
    slots[4] = makeIcon("HOPPER", `<span class="c-gold bold">待领取与目标清单</span>`, [
      `<span class="c-gray">发光物品代表有未领取奖励，排在最前。</span>`,
      "",
      `<span class="c-gray">待领取收藏项: <span class="c-gold">${claimableItems}</span></span>`,
      `<span class="c-gray">待领取总层数: <span class="c-yellow">${claimableTiers}</span></span>`,
      `<span class="c-gray">未满级项目数: <span class="c-aqua">${entries.length}</span></span>`
    ]);

    if (claimableTiers > 0) {
      slots[6] = makeIcon("AMETHYST_SHARD", `<span class="c-gold bold">一键领取当前范围奖励</span>`, [
        `<span class="c-gold">立即领取 ${claimableTiers} 层待领取奖励。</span>`,
        `<span class="c-gray">点击后自动发放枫叶经验。</span>`
      ], {
        glow: true,
        onClick: () => { claimScope(filterCatId); render(); }
      });
    }

    const start = page * listSlots.length;
    for (let i = 0; i < listSlots.length; i++) {
      const idx = start + i;
      if (idx >= entries.length) break;
      const e = entries[idx];
      const lore = [
        `<span class="c-gray">所属分类: ${e.cat.title}</span>`,
        `<span class="c-gray">当前等级: <span class="c-yellow">${e.ct > 0 ? roman(e.ct) : "未解锁"}</span> <span class="c-dark_gray">(${e.ct}/${e.mt})</span></span>`,
        `<span class="c-gray">累计收集: <span class="c-aqua">${formatNumber(e.amt)}</span></span>`
      ];
      if (e.claimable > 0) {
        lore.push("", `<span class="c-gold bold">✦ 有 ${e.claimable} 层奖励待领取！</span>`);
        lore.push(`<span class="c-gray">已领至 ${e.cl > 0 ? roman(e.cl) : "无"}，可领至 ${roman(e.ct)}</span>`);
      } else if (e.ct < e.mt) {
        lore.push("", `<span class="c-gray">距离 ${roman(e.ct + 1)}: <span class="c-yellow">${Math.round(e.ratio * 100)}%</span></span>`);
        lore.push(`<span class="c-aqua">还差 ${formatNumber(Math.max(0, e.nextReq - e.amt))} 个</span>`);
      }
      lore.push("", `<span class="c-yellow">点击打开该物品详情页。</span>`);

      slots[listSlots[i]] = makeIcon(e.item.material, `<span class="c-gold bold">${e.item.name}</span>`, lore, {
        glow: e.claimable > 0,
        badge: e.claimable > 0 ? `+${e.claimable}` : null,
        onClick: () => {
          state.view.type = "detail";
          state.view.categoryId = e.cat.id;
          state.view.itemIndex = e.itemIndex;
          syncInspectorSelection(e.cat.id, e.item.material);
          playTone("click");
          render();
        }
      });
    }

    slots[45] = makeIcon("ARROW", `<span class="c-gold bold">返回收集中心</span>`, [`<span class="c-gray">回到收集中心首页。</span>`], {
      onClick: () => { state.view.type = "hub"; playTone("click"); render(); }
    });
    if (page > 0) {
      slots[48] = makeIcon("ARROW", `<span class="c-gold bold">上一页</span>`, [`<span class="c-gray">第 ${page} 页</span>`], {
        onClick: () => { state.view.page = page - 1; playTone("click"); render(); }
      });
    }
    slots[49] = makeIcon("BARRIER", `<span class="c-gold bold">关闭界面</span>`, [`<span class="c-gray">回到收集中心。</span>`], {
      onClick: () => { state.view.type = "hub"; render(); }
    });
    if (page < maxPage) {
      slots[50] = makeIcon("ARROW", `<span class="c-gold bold">下一页</span>`, [`<span class="c-gray">第 ${page + 2} 页</span>`], {
        onClick: () => { state.view.page = page + 1; playTone("click"); render(); }
      });
    }
    return slots;
  }

  function buildGuideGui() {
    elTitle.textContent = "收藏指南 (/collect guide)";
    elHint.textContent = "悬停查看规则卡片，或点下方分类图标直达";
    const slots = Array.from({ length: 54 }, () => makeFiller("pane-blue"));
    const sum = getSummary();

    slots[4] = makeIcon("WRITABLE_BOOK", `<span class="c-gold bold">收藏指南</span>`, [
      `<span class="c-gray">收藏进度自动累计，奖励支持一键领或逐层领。</span>`,
      "",
      `<span class="c-yellow">当前收藏等级总和: ${sum.curTiers}/${sum.maxTiers}</span>`,
      `<span class="c-gold">当前可领取层数: ${sum.claimableTiers}</span>`
    ]);

    slots[19] = makeIcon("IRON_PICKAXE", `<span class="c-gold bold">1. 获取方式与防刷规则</span>`, [
      `<span class="c-gray">• 农耕/挖矿：在职业启用世界采集作物、矿石或击杀畜牧。</span>`,
      `<span class="c-gray">• 战斗：亲手击杀怪物掉落的战利品自动计入。</span>`,
      `<span class="c-gray">• 伐木：砍伐自然生成的原木与菌柄（放置方块不计入）。</span>`,
      `<span class="c-gray">• 钓鱼：通过钓鱼收获鱼类，或熔炉烤制熟鳕鱼/熟鲑鱼。</span>`,
      "",
      `<span class="c-yellow">提示：丢弃在地上再拾取的物品不会增加收藏进度。</span>`
    ]);

    slots[21] = makeIcon("EXPERIENCE_BOTTLE", `<span class="c-gold bold">2. 层级与奖励领取</span>`, [
      `<span class="c-gray">• 战斗 / 伐木 / 钓鱼：每项最高 9 级（上限 50,000）。</span>`,
      `<span class="c-gray">• 农耕 / 挖矿：每项最高 12 级（上限 500,000）。</span>`,
      `<span class="c-aqua">• 收藏层级奖励仅发放枫叶经验，不再发放技能经验。</span>`,
      `<span class="c-green">• 支持首页一键全领，或点进物品详情页逐层领取。</span>`
    ]);

    slots[23] = makeIcon("EMERALD", `<span class="c-gold bold">3. 职业采集幸运加成</span>`, [
      `<span class="c-gray">农耕与挖矿收藏会自动提供职业采集幸运：</span>`,
      `<span class="c-aqua">• 单项达到 5,000   → +1 采集幸运</span>`,
      `<span class="c-aqua">• 单项达到 50,000  → 再 +1 采集幸运</span>`,
      `<span class="c-aqua">• 单项达到 250,000 → 再 +1 采集幸运</span>`,
      "",
      `<span class="c-gold">每个职业通过收藏最高可叠加 +30 幸运。</span>`
    ]);

    slots[25] = makeIcon("BOOK", `<span class="c-gold bold">4. 常用命令速查</span>`, [
      `<span class="c-yellow">• /collect              打开收集中心首页</span>`,
      `<span class="c-yellow">• /collect claim [分类]  一键领取奖励</span>`,
      `<span class="c-yellow">• /collect missing      查看待领与目标面板</span>`,
      `<span class="c-yellow">• /collect leaderboard  查看全服与分类榜单</span>`,
      `<span class="c-yellow">• /collect admin ...    管理员增删改查</span>`
    ]);

    const catSlots = [29, 30, 31, 32, 33];
    CATEGORIES.forEach((cat, idx) => {
      const cs = getCategorySummary(cat);
      slots[catSlots[idx]] = makeIcon(cat.icon, `<span class="c-gold bold">${cat.title}</span>`, [
        `<span class="c-gray">点击直接进入 ${cat.title}。</span>`,
        "",
        `<span class="c-aqua">已解锁: ${cs.unlocked}/${cs.totalItems}</span>`,
        `<span class="c-green">已满级: ${cs.completed}/${cs.totalItems}</span>`,
        `<span class="c-gold">可领取: ${cs.claimable}</span>`
      ], {
        onClick: () => {
          state.view.type = "category";
          state.view.categoryId = cat.id;
          state.view.page = 0;
          playTone("click");
          render();
        }
      });
    });

    slots[45] = makeIcon("ARROW", `<span class="c-gold bold">返回收集中心</span>`, [`<span class="c-gray">回到收集中心首页。</span>`], {
      onClick: () => { state.view.type = "hub"; playTone("click"); render(); }
    });
    slots[49] = makeIcon("BARRIER", `<span class="c-gold bold">关闭界面</span>`, [`<span class="c-gray">回到收集中心。</span>`], {
      onClick: () => { state.view.type = "hub"; render(); }
    });
    return slots;
  }

  function kindLabel(kind) {
    return { armor: "职业护甲", tool: "职业工具", weapon: "职业武器", material: "压缩材料" }[kind] || kind;
  }

  // ---------- 渲染 DOM ----------
  function renderBreadcrumbs() {
    const v = state.view;
    const crumbs = [{ label: "主页 (/info-new)", onClick: () => { v.type = "main"; render(); } }];
    if (v.type !== "main") {
      crumbs.push({ label: "收集中心", onClick: () => { v.type = "hub"; render(); } });
    }
    if (["category", "detail", "tierRecipe"].includes(v.type)) {
      const cat = CATEGORY_MAP[v.categoryId];
      crumbs.push({ label: cat.title, onClick: () => { v.type = "category"; render(); } });
    }
    if (["detail", "tierRecipe"].includes(v.type)) {
      const cat = CATEGORY_MAP[v.categoryId];
      const item = cat.items[v.itemIndex];
      crumbs.push({ label: item.name, onClick: () => { v.type = "detail"; render(); } });
    }
    if (v.type === "tierRecipe") {
      const stack = Array.isArray(v.recipeStack) ? v.recipeStack : [];
      crumbs.push({
        label: `第 ${roman(v.tier)} 级 · 合成配方`,
        onClick: stack.length > 0 ? () => { v.recipeStack = []; render(); } : null
      });
      stack.forEach((id, i) => {
        const pi = PROF_ITEMS[id];
        if (!pi) return;
        crumbs.push({
          label: pi.name,
          onClick: i < stack.length - 1 ? () => { v.recipeStack = stack.slice(0, i + 1); render(); } : null
        });
      });
    } else if (v.type === "leaderboard") {
      crumbs.push({ label: "排行榜", onClick: null });
    } else if (v.type === "missing") {
      crumbs.push({ label: "待领与目标", onClick: null });
    } else if (v.type === "guide") {
      crumbs.push({ label: "收藏指南", onClick: null });
    }

    elBreadcrumbs.innerHTML = "";
    crumbs.forEach((c, idx) => {
      if (idx > 0) {
        const sep = document.createElement("span");
        sep.className = "crumb-sep";
        sep.textContent = "›";
        elBreadcrumbs.appendChild(sep);
      }
      const btn = document.createElement("button");
      const isLast = idx === crumbs.length - 1;
      btn.className = "crumb-btn" + (isLast ? " current" : "");
      btn.textContent = c.label;
      if (!isLast && c.onClick) {
        btn.addEventListener("click", () => { playTone("click"); c.onClick(); });
      }
      elBreadcrumbs.appendChild(btn);
    });
  }

  function renderGrid() {
    const slots = buildSlots();
    elGrid.innerHTML = "";
    hideTooltip();

    slots.forEach((s) => {
      const cell = document.createElement("div");
      cell.className = "mc-slot";
      if (s.filler) cell.classList.add("filler");
      if (s.glow) cell.classList.add("glow");
      if (s.extraClass) cell.classList.add(s.extraClass);
      if (s.onClick || s.onRightClick) cell.classList.add("clickable");

      if (s.paneClass && !s.material) {
        const pane = document.createElement("div");
        pane.className = `slot-pane ${s.paneClass}`;
        const paneMat = PANE_TEXTURE_MAP[s.paneClass];
        if (paneMat) {
          const img = document.createElement("img");
          img.className = "pane-tex";
          img.src = textureUrl(paneMat);
          img.alt = paneMat;
          pane.appendChild(img);
        }
        if (s.paneLabel) {
          const lbl = document.createElement("span");
          lbl.className = "pane-label";
          lbl.textContent = s.paneLabel;
          pane.appendChild(lbl);
        }
        cell.appendChild(pane);
      } else if (s.headText) {
        const head = document.createElement("div");
        head.className = "slot-head";
        head.style.background = s.headColor;
        head.textContent = s.headText;
        cell.appendChild(head);
      } else if (s.material) {
        if (s.colorHex && LEATHER_ARMOR_MATERIALS.has(s.material)) {
          const wrap = document.createElement("div");
          wrap.className = "slot-armor-tex";
          const baseSrc = textureUrl(s.material);
          const tint = document.createElement("div");
          tint.className = "armor-tint";
          tint.style.backgroundColor = s.colorHex;
          tint.style.webkitMaskImage = `url("${baseSrc}")`;
          tint.style.maskImage = `url("${baseSrc}")`;
          wrap.appendChild(tint);

          const baseImg = document.createElement("img");
          baseImg.className = "armor-base";
          baseImg.src = baseSrc;
          baseImg.alt = s.material;
          wrap.appendChild(baseImg);

          const overlayImg = document.createElement("img");
          overlayImg.className = "armor-overlay";
          overlayImg.src = textureUrl(`${s.material}_OVERLAY`);
          overlayImg.alt = "";
          wrap.appendChild(overlayImg);
          cell.appendChild(wrap);
        } else {
          const icon = document.createElement("div");
          icon.className = "slot-icon";
          const img = document.createElement("img");
          img.className = "mc-tex" + (BLOCK_MATERIALS.has(s.material) ? " mc-tex-block" : "");
          img.src = textureUrl(s.material);
          img.alt = s.material;
          img.onerror = () => {
            img.remove();
            icon.textContent = MATERIAL_ICONS[s.material] || "📦";
          };
          icon.appendChild(img);
          cell.appendChild(icon);
        }
      }

      if (s.count) {
        const cnt = document.createElement("span");
        cnt.className = "slot-count";
        cnt.textContent = s.count;
        cell.appendChild(cnt);
      }
      if (s.badge) {
        const b = document.createElement("span");
        b.className = "slot-badge" + (s.badgeClass ? ` ${s.badgeClass}` : "");
        b.textContent = s.badge;
        cell.appendChild(b);
      }

      if (s.titleHtml) {
        cell.addEventListener("mouseenter", (ev) => showTooltip(s, ev));
        cell.addEventListener("mousemove", (ev) => moveTooltip(ev));
        cell.addEventListener("mouseleave", hideTooltip);
      }
      if (s.onClick) {
        cell.addEventListener("click", (ev) => {
          ev.preventDefault();
          s.onClick();
        });
      }
      if (s.onRightClick) {
        cell.addEventListener("contextmenu", (ev) => {
          ev.preventDefault();
          s.onRightClick();
        });
      }
      elGrid.appendChild(cell);
    });
  }

  function showTooltip(slot, ev) {
    let html = `<div class="tt-title">${slot.titleHtml}</div>`;
    if (slot.loreLines && slot.loreLines.length) {
      for (const line of slot.loreLines) {
        if (line === "") {
          html += `<div class="tt-spacer"></div>`;
        } else {
          html += `<div class="tt-line">${line}</div>`;
        }
      }
    }
    elTooltip.innerHTML = html;
    elTooltip.hidden = false;
    moveTooltip(ev);
  }

  function moveTooltip(ev) {
    const pad = 14;
    const rect = elTooltip.getBoundingClientRect();
    let x = ev.clientX + pad;
    let y = ev.clientY + pad;
    if (x + rect.width > window.innerWidth - 10) {
      x = Math.max(10, ev.clientX - rect.width - pad);
    }
    if (y + rect.height > window.innerHeight - 10) {
      y = Math.max(10, ev.clientY - rect.height - pad);
    }
    elTooltip.style.left = `${x}px`;
    elTooltip.style.top = `${y}px`;
  }

  function hideTooltip() {
    elTooltip.hidden = true;
  }

  // ---------- 右侧调试面板 ----------
  function initInspector() {
    elSimCat.innerHTML = CATEGORIES.map(c => `<option value="${c.id}">${c.title} (${c.id})</option>`).join("");
    elSimCat.addEventListener("change", () => {
      populateSimItems(elSimCat.value);
      renderInspectorCurrent();
    });
    elSimItem.addEventListener("change", renderInspectorCurrent);
    populateSimItems(CATEGORIES[0].id);

    // 推荐职业套装配方快捷按钮（筛选含 3 阶以上/装备配方的 12 个核心物品）
    const recipeMats = Object.keys(MATERIAL_RECIPES).filter(mat => {
      const tiers = getAllRecipeTiersForMaterial(mat);
      return tiers.some(t => getRecipesForTier(mat, t).some(r => {
        const pi = PROF_ITEMS[r.itemId];
        return pi && pi.kind !== "material";
      }));
    });
    elRecipeShortcuts.innerHTML = "";
    recipeMats.forEach(mat => {
      // 找到对应分类与物品索引
      for (const cat of CATEGORIES) {
        const idx = cat.items.findIndex(it => it.material === mat);
        if (idx >= 0) {
          const tiers = getAllRecipeTiersForMaterial(mat);
          const btn = document.createElement("button");
          btn.className = "shortcut-tag";
          btn.innerHTML = `${createMiniIconHtml(mat)}<span>${cat.items[idx].name} (${tiers.length}档)</span>`;
          btn.addEventListener("click", () => {
            state.view.type = "tierRecipe";
            state.view.categoryId = cat.id;
            state.view.itemIndex = idx;
            state.view.tier = tiers[0];
            state.view.recipeIdx = 0;
            state.view.recipeStack = [];
            syncInspectorSelection(cat.id, mat);
            playTone("click");
            render();
          });
          elRecipeShortcuts.appendChild(btn);
          break;
        }
      }
    });
  }

  function populateSimItems(catId) {
    const cat = CATEGORY_MAP[catId];
    elSimItem.innerHTML = cat.items.map(it => {
      const hasRec = getAllRecipeTiersForMaterial(it.material).length > 0 ? " ⚒" : "";
      return `<option value="${it.material}">${it.name}${hasRec}</option>`;
    }).join("");
  }

  function syncInspectorSelection(catId, material) {
    if (elSimCat.value !== catId) {
      elSimCat.value = catId;
      populateSimItems(catId);
    }
    elSimItem.value = material;
    renderInspectorCurrent();
  }

  function renderInspectorCurrent() {
    const cat = CATEGORY_MAP[elSimCat.value];
    const mat = elSimItem.value;
    const item = cat.items.find(i => i.material === mat) || cat.items[0];
    const amt = getAmount(cat, item.material);
    const ct = currentTier(amt, cat);
    const mt = maxTier(cat);
    const cl = getClaimedTier(cat, item.material);
    const nextReq = requirements(cat)[Math.min(mt, ct + 1) - 1];
    elSimCurrent.innerHTML = `
      <div class="sim-item-head">${createMiniIconHtml(item.material)}<strong>${item.name}</strong> <span class="c-gray">(${cat.title})</span></div>
      <div>当前数量：<span class="c-aqua bold">${amt.toLocaleString()}</span> / ${nextReq.toLocaleString()}</div>
      <div>收藏等级：<span class="c-yellow bold">${ct}/${mt}</span> · 已领：<span class="c-green">${cl}</span> · 待领：<span class="c-gold">${Math.max(0, ct - cl)}</span></div>
    `;
  }

  function renderPlayerStats() {
    const sum = getSummary();
    const farmFort = collectionFortune("FARMING");
    const mineFort = collectionFortune("MINING");
    elPlayerStats.innerHTML = `
      <div class="stat-chip"><span class="k">枫叶经验</span><span class="v">+${formatNumber(state.mapleXp)}</span></div>
      <div class="stat-chip"><span class="k">总收藏等级</span><span class="v">${sum.curTiers}/${sum.maxTiers}</span></div>
      <div class="stat-chip"><span class="k">农耕收藏幸运</span><span class="v">+${farmFort} / 30</span></div>
      <div class="stat-chip"><span class="k">挖矿收藏幸运</span><span class="v">+${mineFort} / 30</span></div>
      <div class="stat-chip"><span class="k">已解锁 / 总物品</span><span class="v">${sum.unlockedItems} / ${sum.totalItems}</span></div>
      <div class="stat-chip"><span class="k">待领取层数</span><span class="v">${sum.claimableTiers} 层</span></div>
    `;
  }

  // ---------- 模拟指令解析 ----------
  function parseCategoryToken(token) {
    if (!token) return null;
    const raw = token.trim().toLowerCase();
    if (["all", "overall", "全部"].includes(raw)) return null;
    const map = {
      farming: "FARMING", farm: "FARMING", hoe: "FARMING", "锄头": "FARMING", "农耕": "FARMING",
      mining: "MINING", mine: "MINING", pickaxe: "MINING", "镐子": "MINING", "挖矿": "MINING",
      combat: "COMBAT", sword: "COMBAT", "战斗": "COMBAT", "剑": "COMBAT",
      foraging: "FORAGING", axe: "FORAGING", "伐木": "FORAGING", "斧头": "FORAGING",
      fishing: "FISHING", rod: "FISHING", "钓鱼": "FISHING"
    };
    return map[raw] || null;
  }

  function executeCommand(rawCmd) {
    const trimmed = rawCmd.trim();
    if (!trimmed) return;
    pushChat(`<span class="c-dark_gray">&gt; ${trimmed}</span>`);
    const cleaned = trimmed.startsWith("/") ? trimmed.slice(1) : trimmed;
    const parts = cleaned.split(/\s+/);
    const root = parts[0].toLowerCase();

    if (root === "info-new") {
      showCollectWorkspace("main");
      return;
    }
    if (root === "itemmenu" || root === "armory" || root === "weapon" || root === "gear") {
      if (window.MAPLE_ARMORY && typeof window.MAPLE_ARMORY.open === "function") {
        window.MAPLE_ARMORY.open();
      }
      return;
    }
    if (root !== "collect") {
      pushChat(`<span class="c-red">未知指令，请使用 /collect、/info-new 或 /itemmenu。</span>`);
      return;
    }

    const sub = (parts[1] || "").toLowerCase();
    if (!sub) {
      state.view.type = "hub";
      render();
      return;
    }
    if (sub === "open") {
      const cid = parseCategoryToken(parts[2]);
      if (!cid) {
        pushChat(`<span class="c-red">用法：/collect open &lt;farming|mining|combat|foraging|fishing&gt;</span>`);
        return;
      }
      state.view.type = "category";
      state.view.categoryId = cid;
      state.view.page = 0;
      render();
      return;
    }
    if (sub === "claim") {
      const cid = parseCategoryToken(parts[2]);
      claimScope(cid);
      state.view.type = "hub";
      render();
      return;
    }
    if (sub === "leaderboard" || sub === "top") {
      state.view.type = "leaderboard";
      state.view.lbCategoryId = parseCategoryToken(parts[2]);
      state.view.page = 0;
      render();
      return;
    }
    if (sub === "missing") {
      state.view.type = "missing";
      state.view.missingCategoryId = parseCategoryToken(parts[2]);
      state.view.page = 0;
      render();
      return;
    }
    if (sub === "guide" || sub === "help") {
      state.view.type = "guide";
      render();
      return;
    }
    if (sub === "admin") {
      const act = (parts[2] || "").toLowerCase();
      if (act === "add" || act === "set") {
        const cid = parseCategoryToken(parts[4]);
        const itemToken = (parts[5] || "").toUpperCase();
        const amount = Number(parts[6]);
        if (!cid || !itemToken || Number.isNaN(amount)) {
          pushChat(`<span class="c-red">用法：/collect admin ${act} &lt;玩家&gt; &lt;分类&gt; &lt;物品&gt; &lt;数量&gt;</span>`);
          return;
        }
        const cat = CATEGORY_MAP[cid];
        const found = cat.items.find(i => i.material === itemToken || i.name === parts[5]);
        if (!found) {
          pushChat(`<span class="c-red">分类 ${cat.title} 中找不到物品：${parts[5]}</span>`);
          return;
        }
        if (act === "set") setItemAmount(cat, found.material, amount, true);
        else addItemAmount(cat, found.material, amount, true);
        pushChat(`<span class="c-green">[Admin] 已更新 ${found.name} 进度为 ${formatNumber(getAmount(cat, found.material))}。</span>`);
        render();
        return;
      }
      if (act === "reset" || act === "resetclaims") {
        const cid = parseCategoryToken(parts[4]);
        const targetCats = cid ? [CATEGORY_MAP[cid]] : CATEGORIES;
        for (const cat of targetCats) {
          for (const it of cat.items) {
            const k = cKey(cat, it.material);
            if (act === "reset") delete state.progress[k];
            delete state.claims[k];
          }
        }
        pushChat(`<span class="c-green">[Admin] 已重置 ${cid ? CATEGORY_MAP[cid].title : "全部收藏"} ${act === "reset" ? "进度与领取记录" : "已领奖励记录"}。</span>`);
        render();
        return;
      }
      if (act === "view") {
        const sum = getSummary();
        pushChat(`<span class="c-gold">=== 收藏数据：${PLAYER_NAME} ===</span>`);
        for (const cat of CATEGORIES) {
          const cs = getCategorySummary(cat);
          pushChat(`<span class="c-yellow">[${cat.title}]</span> <span class="c-aqua">总量 ${formatNumber(cs.totalAmt)}</span> | <span class="c-gray">等级 ${cs.curTiers}/${cs.maxTiers}</span> | <span class="c-gold">可领 ${cs.claimable}</span>`);
        }
        pushChat(`<span class="c-green">合计：总量 ${formatNumber(sum.totalCollected)} | 总等级 ${sum.curTiers} | 待领 ${sum.claimableTiers}</span>`);
        return;
      }
      pushChat(`<span class="c-gold">用法：/collect admin &lt;view|add|set|reset|resetclaims&gt; ...</span>`);
      return;
    }

    state.view.type = "hub";
    render();
  }

  function render() {
    if (state.view.type !== "tierRecipe" && state.view.recipeStack && state.view.recipeStack.length > 0) {
      state.view.recipeStack = [];
    }
    renderBreadcrumbs();
    renderGrid();
    renderInspectorCurrent();
    renderPlayerStats();
  }

  const elCollectWorkspace = document.getElementById("collect-workspace");
  const elArmoryWorkspace = document.getElementById("armory-workspace");
  const btnOpenMain = document.getElementById("btn-open-main");
  const btnOpenHub = document.getElementById("btn-open-hub");
  const btnOpenArmory = document.getElementById("btn-open-armory");

  function setTopbarMode(mode) {
    if (btnOpenMain) btnOpenMain.classList.toggle("primary", mode === "main");
    if (btnOpenHub) btnOpenHub.classList.toggle("primary", mode === "hub");
    if (btnOpenArmory) btnOpenArmory.classList.toggle("primary", mode === "armory");
    if (elCollectWorkspace) elCollectWorkspace.hidden = (mode === "armory");
    if (elArmoryWorkspace) elArmoryWorkspace.hidden = (mode !== "armory");
    hideTooltip();
  }

  function showCollectWorkspace(viewType) {
    if (viewType) state.view.type = viewType;
    setTopbarMode(state.view.type === "main" ? "main" : "hub");
    render();
  }

  function findRecipeLocation(profItemId) {
    for (const [mat, tierMap] of Object.entries(MATERIAL_RECIPES)) {
      const tiers = Object.keys(tierMap).map(Number).sort((a, b) => a - b);
      for (const tier of tiers) {
        const list = tierMap[String(tier)] || [];
        const rIdx = list.findIndex(r => r.itemId === profItemId);
        if (rIdx >= 0) {
          const found = findCollectionByMaterial(mat);
          if (found) return { cat: found.cat, itemIndex: found.itemIndex, tier, recipeIdx: rIdx, mat };
        }
      }
    }
    return null;
  }

  function openProfessionRecipe(profItemId) {
    const loc = findRecipeLocation(profItemId);
    if (!loc) return false;
    state.view.type = "tierRecipe";
    state.view.categoryId = loc.cat.id;
    state.view.itemIndex = loc.itemIndex;
    state.view.tier = loc.tier;
    state.view.recipeIdx = loc.recipeIdx;
    state.view.recipeStack = [];
    syncInspectorSelection(loc.cat.id, loc.mat);
    setTopbarMode("hub");
    playTone("click");
    render();
    return true;
  }

  window.MAPLE_SIM = {
    playTone,
    setTopbarMode,
    showCollectWorkspace,
    openProfessionRecipe,
    findRecipeLocation,
    showTooltip,
    moveTooltip,
    hideTooltip
  };

  // ---------- 事件绑定 ----------
  document.getElementById("btn-sound").addEventListener("click", (ev) => {
    state.soundEnabled = !state.soundEnabled;
    ev.currentTarget.textContent = state.soundEnabled ? "🔊 音效: 开" : "🔇 音效: 关";
    ev.currentTarget.classList.toggle("active", state.soundEnabled);
  });

  btnOpenMain.addEventListener("click", () => {
    playTone("click");
    showCollectWorkspace("main");
  });

  btnOpenHub.addEventListener("click", () => {
    playTone("click");
    showCollectWorkspace("hub");
  });

  if (btnOpenArmory) {
    btnOpenArmory.addEventListener("click", () => {
      playTone("click");
      if (window.MAPLE_ARMORY && typeof window.MAPLE_ARMORY.open === "function") {
        window.MAPLE_ARMORY.open();
      } else {
        setTopbarMode("armory");
      }
    });
  }

  document.querySelectorAll("[data-preset]").forEach(btn => {
    btn.addEventListener("click", () => applyPreset(btn.dataset.preset));
  });

  document.querySelectorAll("[data-delta]").forEach(btn => {
    btn.addEventListener("click", () => {
      const cat = CATEGORY_MAP[elSimCat.value];
      const mat = elSimItem.value;
      addItemAmount(cat, mat, Number(btn.dataset.delta), true);
      render();
    });
  });

  document.getElementById("btn-sim-max").addEventListener("click", () => {
    const cat = CATEGORY_MAP[elSimCat.value];
    const mat = elSimItem.value;
    const reqs = requirements(cat);
    setItemAmount(cat, mat, reqs[reqs.length - 1], true);
    render();
  });

  document.getElementById("btn-sim-set").addEventListener("click", () => {
    const val = Number(document.getElementById("sim-custom-amount").value);
    if (Number.isNaN(val) || val < 0) return;
    const cat = CATEGORY_MAP[elSimCat.value];
    setItemAmount(cat, elSimItem.value, val, true);
    render();
  });

  document.getElementById("btn-sim-zero").addEventListener("click", () => {
    const cat = CATEGORY_MAP[elSimCat.value];
    const mat = elSimItem.value;
    setItemAmount(cat, mat, 0, false);
    delete state.claims[cKey(cat, mat)];
    render();
  });

  document.getElementById("mc-command-form").addEventListener("submit", (ev) => {
    ev.preventDefault();
    const input = document.getElementById("mc-command-input");
    executeCommand(input.value);
    input.value = "";
  });

  // 初始化
  initInspector();
  applyPreset("midgame");
  pushChat(`<span class="c-green">欢迎来到收集中心 UI 模拟器！</span> <span class="c-gray">点进分类 → 点物品 → 点任意等级玻璃板即可看到 3×3 合成配方与奖励。</span>`);
})();
