(() => {
  const EQ = window.EQUIPMENT_DATA;
  if (!EQ) return;

  const SIM = () => window.MAPLE_SIM;

  const LEGACY_COLORS = {
    "0": "#000000", "1": "#0000aa", "2": "#00aa00", "3": "#00aaaa",
    "4": "#aa0000", "5": "#aa00aa", "6": "#ffaa00", "7": "#aaaaaa",
    "8": "#555555", "9": "#5555ff", "a": "#55ff55", "b": "#55ffff",
    "c": "#ff5555", "d": "#ff55ff", "e": "#ffff55", "f": "#ffffff"
  };

  const LEATHER_ARMOR_MATERIALS = new Set([
    "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"
  ]);

  const STAT_LABELS = {
    health: { name: "生命", color: "#55ff55", prefix: "+" },
    defense: { name: "防禦", color: "#55ff55", prefix: "+" },
    damage: { name: "傷害", color: "#ff5555", prefix: "+" },
    base_damage: { name: "基礎傷害", color: "#ff5555", prefix: "+" },
    strength: { name: "力量", color: "#ff5555", prefix: "+" },
    intelligence: { name: "智力", color: "#55ffff", prefix: "+" },
    speed: { name: "速度", color: "#ffffff", prefix: "" },
    crit_chance: { name: "暴擊率", color: "#ffff55", prefix: "+", suffix: "%" },
    crit_damage: { name: "暴擊傷害", color: "#ff5555", prefix: "+", suffix: "%" },
    bonus_attack_speed: { name: "攻速", color: "#ff5555", prefix: "+" },
    ability_damage_percent: { name: "能力傷害", color: "#55ffff", prefix: "+", suffix: "%" },
    fortune: { name: "职业幸运", color: "#55ff55", prefix: "+" },
    xp: { name: "职业经验", color: "#55ffff", prefix: "+", suffix: "%" },
    rare: { name: "稀有提升", color: "#ff55ff", prefix: "+", suffix: "%" },
    event: { name: "事件提升", color: "#ffaa00", prefix: "+", suffix: "%" },
    reduction: { name: "职业减伤", color: "#55ff55", prefix: "", suffix: "%" }
  };

  const CATEGORIES = [
    { id: "all", label: "全部整合", icon: "NETHER_STAR" },
    { id: "weapons", label: "洞窟武器", icon: "DIAMOND_SWORD" },
    { id: "legendary_sets", label: "传说套装", icon: "LEATHER_CHESTPLATE", colorHex: "#FF8C00" },
    { id: "dungeon_sets", label: "洞窟套装", icon: "LEATHER_CHESTPLATE", colorHex: "#9b7bc0" },
    { id: "helmets", label: "特殊头盔与面具", icon: "PLAYER_HEAD", skullTexture: "textures/skulls/immortal_skull.png" },
    { id: "echoes", label: "地心回声与道具", icon: "PLAYER_HEAD", skullTexture: "textures/skulls/cave_echo_firefly.png" },
    { id: "prof_farming", label: "农业职业装备", icon: "NETHERITE_HOE" },
    { id: "prof_mining", label: "挖矿职业装备", icon: "NETHERITE_PICKAXE" }
  ];

  const RARITY_FILTERS = [
    { id: "all", label: "全部稀有度" },
    { id: "稀有", label: "稀有" },
    { id: "史詩", label: "史詩" },
    { id: "傳說", label: "傳說" },
    { id: "永恆", label: "永恆" },
    { id: "职业", label: "职业装备" }
  ];

  // Runtime state for Armory Compendium
  const state = {
    category: "all",
    search: "",
    rarity: "all",
    floorBySet: {},     // setId -> "F1" | "F2" | "F3"
    setProgress: 4,     // 0..4 for {set_progress} preview
    selected: null      // { type: "item"|"setPiece", item, set? }
  };

  // Initialize default floors
  for (const s of EQ.dungeonSets) {
    if (s.floors && s.floors.length) {
      state.floorBySet[s.id] = s.defaultFloor || s.floors[0];
    }
  }

  // DOM refs
  const elTabs = document.getElementById("armory-tabs");
  const elRarityBar = document.getElementById("armory-rarity-bar");
  const elSearch = document.getElementById("armory-search");
  const elSearchClear = document.getElementById("armory-search-clear");
  const elCatalog = document.getElementById("armory-catalog");
  const elInspector = document.getElementById("armory-inspector");

  // ------------------------------------------------------------------
  // 1:1 Minecraft Legacy + &#RRGGBB / #RRGGBB Renderer
  // Matches LegacyRgbParser.java & WeaponManager.java
  // ------------------------------------------------------------------
  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function isHex6(str, idx) {
    if (idx + 6 > str.length) return false;
    for (let k = 0; k < 6; k++) {
      const c = str.charCodeAt(idx + k);
      const ok = (c >= 48 && c <= 57) || (c >= 65 && c <= 70) || (c >= 97 && c <= 102);
      if (!ok) return false;
    }
    return true;
  }

  function renderMcText(raw, defaultColor = "#aaaaaa") {
    if (raw == null || raw === "") return "";
    const input = String(raw);
    let out = "";
    let buf = "";
    let color = defaultColor;
    let bold = false;
    let italic = false;
    let underline = false;
    let strike = false;
    let emittedTextSinceFormat = false;

    const flush = () => {
      if (!buf) return;
      const cls = [];
      if (bold) cls.push("bold");
      if (italic) cls.push("mc-italic");
      if (underline) cls.push("mc-underline");
      if (strike) cls.push("mc-strike");
      const classAttr = cls.length ? ` class="${cls.join(" ")}"` : "";
      out += `<span style="color:${color}"${classAttr}>${escapeHtml(buf)}</span>`;
      buf = "";
      emittedTextSinceFormat = true;
    };

    let i = 0;
    while (i < input.length) {
      const ch = input[i];
      if ((ch === "&" || ch === "§") && i + 1 < input.length) {
        const next = input[i + 1];
        if (next === "#" && isHex6(input, i + 2)) {
          flush();
          color = "#" + input.slice(i + 2, i + 8);
          i += 8;
          continue;
        }
        const low = next.toLowerCase();
        if (LEGACY_COLORS[low]) {
          flush();
          color = LEGACY_COLORS[low];
          // Standard MC resets decorations on a new color code after text has been emitted,
          // while allowing prefix formatting like "&l&f絆線鉤" before any text is emitted.
          if (emittedTextSinceFormat) {
            bold = italic = underline = strike = false;
          }
          i += 2;
          continue;
        }
        if (low === "l") { flush(); bold = true; emittedTextSinceFormat = false; i += 2; continue; }
        if (low === "o") { flush(); italic = true; emittedTextSinceFormat = false; i += 2; continue; }
        if (low === "n") { flush(); underline = true; emittedTextSinceFormat = false; i += 2; continue; }
        if (low === "m") { flush(); strike = true; emittedTextSinceFormat = false; i += 2; continue; }
        if (low === "k") { flush(); i += 2; continue; }
        if (low === "r") {
          flush();
          color = defaultColor;
          bold = italic = underline = strike = false;
          emittedTextSinceFormat = false;
          i += 2;
          continue;
        }
      }
      if (ch === "#" && isHex6(input, i + 1)) {
        flush();
        color = "#" + input.slice(i + 1, i + 7);
        i += 7;
        continue;
      }
      buf += ch;
      i++;
    }
    flush();
    return out;
  }

  function resolveLoreLines(lines, setProgressVal) {
    const prog = `${setProgressVal}/4`;
    return (lines || []).map(l => {
      const s = String(l).replace(/\{set_progress\}/g, prog);
      if (s.trim() === "") return "";
      return renderMcText(s, "#aaaaaa");
    });
  }

  function getActiveSetData(setObj) {
    if (setObj.floorsData) {
      const fl = state.floorBySet[setObj.id] || setObj.defaultFloor || setObj.floors[0];
      return { floor: fl, ...setObj.floorsData[fl] };
    }
    return { floor: null, totalStats: setObj.totalStats || {}, pieces: setObj.pieces || [] };
  }

  // ------------------------------------------------------------------
  // Minecraft Pixel Slot DOM Builder
  // ------------------------------------------------------------------
  function buildSlotVisual(cell, item) {
    if (item.skullTexture) {
      const icon = document.createElement("div");
      icon.className = "slot-icon";
      const img = document.createElement("img");
      img.className = "mc-tex";
      img.src = item.skullTexture;
      img.alt = item.namePlain || item.id;
      img.onerror = () => {
        img.src = "textures/PLAYER_HEAD.png";
      };
      icon.appendChild(img);
      cell.appendChild(icon);
      return;
    }
    if (item.colorHex && LEATHER_ARMOR_MATERIALS.has(item.material)) {
      const wrap = document.createElement("div");
      wrap.className = "slot-armor-tex";
      const baseSrc = `textures/${item.material}.png`;
      const tint = document.createElement("div");
      tint.className = "armor-tint";
      tint.style.backgroundColor = item.colorHex;
      tint.style.webkitMaskImage = `url("${baseSrc}")`;
      tint.style.maskImage = `url("${baseSrc}")`;
      wrap.appendChild(tint);

      const baseImg = document.createElement("img");
      baseImg.className = "armor-base";
      baseImg.src = baseSrc;
      baseImg.alt = item.material;
      wrap.appendChild(baseImg);

      const overlayImg = document.createElement("img");
      overlayImg.className = "armor-overlay";
      overlayImg.src = `textures/${item.material}_OVERLAY.png`;
      overlayImg.alt = "";
      wrap.appendChild(overlayImg);
      cell.appendChild(wrap);
      return;
    }
    const icon = document.createElement("div");
    icon.className = "slot-icon";
    const img = document.createElement("img");
    img.className = "mc-tex";
    img.src = `textures/${item.material}.png`;
    img.alt = item.material;
    img.onerror = () => {
      img.remove();
      icon.textContent = "⚔️";
    };
    icon.appendChild(img);
    cell.appendChild(icon);
  }

  function buildTooltipPayload(item) {
    return {
      titleHtml: renderMcText(item.displayName, item.defaultTitleColor || "#ffffff"),
      loreLines: resolveLoreLines(item.lore, state.setProgress)
    };
  }

  function attachHoverTooltip(el, getItemFn) {
    el.addEventListener("mouseenter", (ev) => {
      const sim = SIM();
      if (sim) sim.showTooltip(buildTooltipPayload(getItemFn()), ev);
    });
    el.addEventListener("mousemove", (ev) => {
      const sim = SIM();
      if (sim) sim.moveTooltip(ev);
    });
    el.addEventListener("mouseleave", () => {
      const sim = SIM();
      if (sim) sim.hideTooltip();
    });
  }

  // ------------------------------------------------------------------
  // Search & Filter Matching
  // ------------------------------------------------------------------
  function itemRarityKey(item, setObj) {
    if ((item && item.job) || (setObj && (setObj.category === "prof_farming" || setObj.category === "prof_mining"))) {
      return "职业";
    }
    return (item && item.rarity) || (setObj && setObj.rarity) || "特殊";
  }

  function matchesFilters(textBlob, rarityKey) {
    if (state.rarity !== "all" && rarityKey !== state.rarity) {
      return false;
    }
    const q = state.search.trim().toLowerCase();
    if (!q) return true;
    return textBlob.toLowerCase().includes(q);
  }

  function itemSearchText(item, extra = "") {
    return [
      item.id,
      item.namePlain,
      item.material,
      item.subGroup || "",
      item.dropInfo || "",
      (item.lore || []).join(" "),
      extra
    ].join(" ");
  }

  function setSearchText(setObj) {
    const active = getActiveSetData(setObj);
    const pieceTexts = active.pieces.map(p => `${p.namePlain} ${(p.lore || []).join(" ")}`).join(" ");
    return [
      setObj.id,
      setObj.name,
      setObj.series || "",
      setObj.branchLabel || "",
      setObj.abilityTitle || "",
      setObj.abilityDesc || "",
      setObj.dropInfo || "",
      pieceTexts
    ].join(" ");
  }

  function formatStatChips(statsObj, limit = 5) {
    if (!statsObj) return "";
    const entries = Object.entries(statsObj).filter(([k, v]) => STAT_LABELS[k] && v !== 0 && v != null);
    return entries.slice(0, limit).map(([k, v]) => {
      const meta = STAT_LABELS[k];
      const num = typeof v === "number" ? (Number.isInteger(v) ? v : Number(v.toFixed(1))) : v;
      const sign = (typeof num === "number" && num < 0) ? "" : meta.prefix;
      return `<span class="mini-stat">${meta.name} <span style="color:${meta.color};font-weight:700">${sign}${num}${meta.suffix || ""}</span></span>`;
    }).join("");
  }

  function rarityBadgeHtml(rarityKey) {
    const label = rarityKey === "职业" ? "职业装备" : rarityKey;
    return `<span class="rarity-tag rarity-${rarityKey}">${label}</span>`;
  }

  // ------------------------------------------------------------------
  // Selection & Inspector
  // ------------------------------------------------------------------
  function isSelected(itemId) {
    return state.selected && state.selected.item && state.selected.item.id === itemId;
  }

  function selectItem(item, setObj = null) {
    state.selected = {
      type: setObj ? "setPiece" : "item",
      item,
      set: setObj
    };
    const sim = SIM();
    if (sim) sim.playTone("click");
    renderCatalog();
    renderInspector();
  }

  function ensureDefaultSelection() {
    if (state.selected && state.selected.item) return;
    if (EQ.weapons && EQ.weapons.length) {
      state.selected = { type: "item", item: EQ.weapons[0], set: null };
    }
  }

  function renderInspector() {
    ensureDefaultSelection();
    if (!state.selected || !state.selected.item) {
      elInspector.innerHTML = `<div class="armory-empty">点击左侧任意武器或装备，即可按 MC 游戏内样式查看完整物品信息。</div>`;
      return;
    }

    let { item, set } = state.selected;
    // If viewing a multi-floor set, refresh piece reference for active floor
    let activeSetData = null;
    if (set) {
      activeSetData = getActiveSetData(set);
      const matched = activeSetData.pieces.find(p => p.slot === item.slot) || activeSetData.pieces[0];
      if (matched) {
        item = matched;
        state.selected.item = matched;
      }
    }

    const rKey = itemRarityKey(item, set);
    const tt = buildTooltipPayload(item);

    elInspector.innerHTML = "";

    // Card 1: 1:1 Minecraft Tooltip Stage
    const stage = document.createElement("div");
    stage.className = "inspector-mc-stage";

    // Banner with 56x56 MC slot
    const banner = document.createElement("div");
    banner.className = "inspector-item-banner";

    const slotBox = document.createElement("div");
    slotBox.className = "mc-slot armory-slot-box" + (item.glow ? " glow" : "");
    buildSlotVisual(slotBox, item);
    banner.appendChild(slotBox);

    const bText = document.createElement("div");
    bText.className = "inspector-banner-text";
    bText.innerHTML = `
      <div class="inspector-banner-title">${tt.titleHtml}</div>
      <div class="inspector-banner-sub">
        ${rarityBadgeHtml(rKey)}
        <span>${set ? set.name : (item.subGroup || item.material)}</span>
        ${item.level ? `<span>· 需求 Lv.${item.level}</span>` : ""}
      </div>
    `;
    banner.appendChild(bText);
    stage.appendChild(banner);

    // 4-piece switcher if armor set
    if (set && activeSetData && activeSetData.pieces.length > 1) {
      const pieceBar = document.createElement("div");
      pieceBar.className = "inspector-piece-bar";
      activeSetData.pieces.forEach((p) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "piece-switch-btn" + (p.slot === item.slot ? " active" : "");
        btn.textContent = p.slotLabel || p.slot;
        btn.addEventListener("click", () => selectItem(p, set));
        pieceBar.appendChild(btn);
      });
      stage.appendChild(pieceBar);
    }

    // Floor switcher (for multi-floor dungeon sets) & Set progress switcher
    if (set && set.floors && set.floors.length > 1) {
      const row = document.createElement("div");
      row.className = "inspector-controls-row";
      row.innerHTML = `<span class="inspector-ctrl-label">洞窟楼层属性切换：</span>`;
      const pills = document.createElement("div");
      pills.className = "armory-floor-pills";
      set.floors.forEach((fl) => {
        const b = document.createElement("button");
        b.type = "button";
        b.className = "floor-pill" + (activeSetData.floor === fl ? " active" : "");
        b.textContent = fl;
        b.addEventListener("click", () => {
          state.floorBySet[set.id] = fl;
          const sim = SIM();
          if (sim) sim.playTone("click");
          renderCatalog();
          renderInspector();
        });
        pills.appendChild(b);
      });
      row.appendChild(pills);
      stage.appendChild(row);
    }

    if (set && set.hasSetProgress) {
      const row = document.createElement("div");
      row.className = "inspector-controls-row";
      row.innerHTML = `<span class="inspector-ctrl-label">模拟穿戴件数 (Lore 显示)：</span>`;
      const pills = document.createElement("div");
      pills.className = "armory-floor-pills";
      [0, 1, 2, 3, 4].forEach((n) => {
        const b = document.createElement("button");
        b.type = "button";
        b.className = "floor-pill" + (state.setProgress === n ? " active" : "");
        b.textContent = `${n}/4`;
        b.addEventListener("click", () => {
          state.setProgress = n;
          const sim = SIM();
          if (sim) sim.playTone("click");
          renderInspector();
        });
        pills.appendChild(b);
      });
      row.appendChild(pills);
      stage.appendChild(row);
    }

    // Authentic Static 1:1 Minecraft Tooltip Box
    const tooltipCard = document.createElement("div");
    tooltipCard.className = "mc-tooltip-card";
    let ttHtml = `<div class="tt-title">${tt.titleHtml}</div>`;
    for (const line of tt.loreLines) {
      if (line === "") {
        ttHtml += `<div class="tt-spacer"></div>`;
      } else {
        ttHtml += `<div class="tt-line">${line}</div>`;
      }
    }
    tooltipCard.innerHTML = ttHtml;
    stage.appendChild(tooltipCard);

    if (item.equippedDoubleStats) {
      const notice = document.createElement("div");
      notice.className = "armory-notice-banner";
      notice.innerHTML = `✨ <strong>双倍属性机制：</strong>MC 物品 Lore 上显示基础 1× 数值，实际穿戴在头上时插件会将生命 / 防禦 / 力量属性自动翻倍（×2）生效！`;
      stage.appendChild(notice);
    }

    // Jump to 3x3 crafting recipe for profession items
    const sim = SIM();
    if (item.job && sim && sim.findRecipeLocation && sim.findRecipeLocation(item.id)) {
      const recipeBtn = document.createElement("button");
      recipeBtn.type = "button";
      recipeBtn.className = "ctrl-btn accent";
      recipeBtn.style.width = "100%";
      recipeBtn.textContent = "⚒ 前往收集中心查看 3×3 合成配方 →";
      recipeBtn.addEventListener("click", () => {
        sim.openProfessionRecipe(item.id);
      });
      stage.appendChild(recipeBtn);
    }

    elInspector.appendChild(stage);

    // Card 2: Set Total Stats (when viewing an armor set)
    if (set && activeSetData && activeSetData.totalStats && Object.keys(activeSetData.totalStats).length > 0) {
      const setCard = document.createElement("div");
      setCard.className = "panel-card";
      const floorLabel = activeSetData.floor ? ` (${activeSetData.floor})` : "";
      setCard.innerHTML = `
        <h2>🛡️ 整套四件合计属性${floorLabel}</h2>
        <div class="armory-set-stats">${formatStatChips(activeSetData.totalStats, 12)}</div>
      `;
      elInspector.appendChild(setCard);
    }

    // Card 3: Technical Metadata & Give Command
    const metaCard = document.createElement("div");
    metaCard.className = "panel-card";
    const metaRows = [];
    metaRows.push(["物品 ID", item.id]);
    metaRows.push(["MC 材质", item.material]);
    if (item.colorHex) {
      metaRows.push(["皮革染色", `<span class="color-swatch" style="display:inline-block;vertical-align:middle;background:${item.colorHex};margin-right:5px"></span>${item.colorHex.toUpperCase()}`]);
    }
    const dropText = item.dropInfo || (set && set.dropInfo);
    if (dropText) {
      metaRows.push(["获取 / 掉落", escapeHtml(dropText)]);
    }
    if (item.requirements && item.requirements.length) {
      metaRows.push(["合成前置", escapeHtml(item.requirements.join(" · "))]);
    }
    const srcFile = item.sourceFile || (set && set.sourceFile);
    if (srcFile) {
      metaRows.push(["配置文件", escapeHtml(srcFile)]);
    }

    metaCard.innerHTML = `
      <h2>📦 物品信息与指令</h2>
      <div class="inspector-meta-list">
        ${metaRows.map(([k, v]) => `<div class="meta-row"><span class="mk">${k}</span><span class="mv">${v}</span></div>`).join("")}
      </div>
    `;

    const cmdStr = item.giveCmd || (item.job ? "/professions book" : null);
    if (cmdStr) {
      const cmdWrap = document.createElement("div");
      cmdWrap.style.marginTop = "9px";
      cmdWrap.innerHTML = `
        <div class="cmd-copy-row">
          <code class="cmd-copy-code">${escapeHtml(cmdStr)}</code>
          <button type="button" class="ctrl-btn" id="btn-copy-give-cmd">复制</button>
        </div>
      `;
      metaCard.appendChild(cmdWrap);
      const copyBtn = cmdWrap.querySelector("#btn-copy-give-cmd");
      copyBtn.addEventListener("click", () => {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(cmdStr).catch(() => {});
        }
        copyBtn.textContent = "已复制 ✔";
        setTimeout(() => { copyBtn.textContent = "复制"; }, 1400);
      });
    }

    elInspector.appendChild(metaCard);
  }

  // ------------------------------------------------------------------
  // Catalog Rendering (Categorized Weapons, Sets, Helmets, Prof Gear)
  // ------------------------------------------------------------------
  function renderStandaloneCard(item) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "armory-item-card" + (isSelected(item.id) ? " selected" : "");

    const slot = document.createElement("div");
    slot.className = "mc-slot armory-slot-box" + (item.glow ? " glow" : "");
    buildSlotVisual(slot, item);
    card.appendChild(slot);

    const meta = document.createElement("div");
    meta.className = "armory-item-meta";
    const rKey = itemRarityKey(item, null);
    const subBits = [rarityBadgeHtml(rKey)];
    if (item.level) subBits.push(`<span>Lv.${item.level}</span>`);
    else if (item.equippedDoubleStats) subBits.push(`<span>穿戴×2</span>`);
    else if (item.dropInfo) subBits.push(`<span>${escapeHtml(item.dropInfo)}</span>`);

    meta.innerHTML = `
      <div class="armory-item-name">${renderMcText(item.displayName, item.defaultTitleColor || "#ffffff")}</div>
      <div class="armory-item-sub">${subBits.join("")}</div>
    `;
    card.appendChild(meta);

    attachHoverTooltip(card, () => item);
    card.addEventListener("click", () => selectItem(item, null));
    return card;
  }

  function renderSetCard(setObj) {
    const active = getActiveSetData(setObj);
    const hasSelected = active.pieces.some(p => isSelected(p.id));
    const rKey = itemRarityKey(null, setObj);

    const card = document.createElement("div");
    card.className = "armory-set-card" + (hasSelected ? " has-selected" : "");

    // Top row: swatch + set name + rarity / floor pills
    const top = document.createElement("div");
    top.className = "armory-set-top";

    const titleWrap = document.createElement("div");
    titleWrap.className = "armory-set-title-wrap";
    if (setObj.colorHex) {
      const sw = document.createElement("span");
      sw.className = "color-swatch";
      sw.style.backgroundColor = setObj.colorHex;
      titleWrap.appendChild(sw);
    }
    const textCol = document.createElement("div");
    const subParts = [rarityBadgeHtml(rKey)];
    if (setObj.branchLabel) subParts.push(`<span>${escapeHtml(setObj.branchLabel)}</span>`);
    else if (setObj.series) subParts.push(`<span>${escapeHtml(setObj.series)}</span>`);
    if (setObj.level) subParts.push(`<span>Lv.${setObj.level}</span>`);
    if (setObj.dropInfo) subParts.push(`<span>${escapeHtml(setObj.dropInfo)}</span>`);

    textCol.innerHTML = `
      <div class="armory-set-name">${escapeHtml(setObj.name)}</div>
      <div class="armory-set-sub">${subParts.join(" ")}</div>
    `;
    titleWrap.appendChild(textCol);
    top.appendChild(titleWrap);

    if (setObj.floors && setObj.floors.length > 1) {
      const pills = document.createElement("div");
      pills.className = "armory-floor-pills";
      setObj.floors.forEach((fl) => {
        const b = document.createElement("button");
        b.type = "button";
        b.className = "floor-pill" + (active.floor === fl ? " active" : "");
        b.textContent = fl;
        b.addEventListener("click", (ev) => {
          ev.stopPropagation();
          state.floorBySet[setObj.id] = fl;
          const sim = SIM();
          if (sim) sim.playTone("click");
          renderCatalog();
          if (state.selected && state.selected.set && state.selected.set.id === setObj.id) {
            renderInspector();
          }
        });
        pills.appendChild(b);
      });
      top.appendChild(pills);
    }

    card.appendChild(top);

    // 4-piece Minecraft Chest Row
    const slotsRow = document.createElement("div");
    slotsRow.className = "armory-set-slots";
    active.pieces.forEach((piece) => {
      const cell = document.createElement("div");
      cell.className = "mc-slot clickable" + (piece.glow ? " glow" : "") + (isSelected(piece.id) ? " selected-slot" : "");
      buildSlotVisual(cell, piece);
      attachHoverTooltip(cell, () => piece);
      cell.addEventListener("click", () => selectItem(piece, setObj));
      slotsRow.appendChild(cell);
    });
    card.appendChild(slotsRow);

    // Total stats chips
    const statsHtml = formatStatChips(active.totalStats, 5);
    if (statsHtml) {
      const stWrap = document.createElement("div");
      stWrap.className = "armory-set-stats";
      stWrap.innerHTML = statsHtml;
      card.appendChild(stWrap);
    }

    return card;
  }

  function makeSection(title, subtitle) {
    const sec = document.createElement("section");
    sec.className = "armory-section";
    sec.innerHTML = `
      <div class="armory-section-head">
        <h2 class="armory-section-title">${title}</h2>
        <span class="armory-section-sub">${subtitle}</span>
      </div>
    `;
    return sec;
  }

  function renderCatalog() {
    elCatalog.innerHTML = "";
    const cat = state.category;
    let renderedCount = 0;

    // 1. Weapons
    if (cat === "all" || cat === "weapons") {
      const list = EQ.weapons.filter(it => matchesFilters(itemSearchText(it), itemRarityKey(it, null)));
      if (list.length) {
        const sec = makeSection(`⚔️ 洞窟武器 (${list.length})`, "悬停预览 MC 悬浮框 · 点击右侧锁定查看完整技能与数值");
        const grid = document.createElement("div");
        grid.className = "armory-item-grid";
        list.forEach(it => grid.appendChild(renderStandaloneCard(it)));
        sec.appendChild(grid);
        elCatalog.appendChild(sec);
        renderedCount += list.length;
      }
    }

    // 2. Legendary Blood Sets
    if (cat === "all" || cat === "legendary_sets") {
      const sets = EQ.legendarySets.filter(s => matchesFilters(setSearchText(s), itemRarityKey(null, s)));
      if (sets.length) {
        const sec = makeSection(`🛡️ 传说套装 (${sets.length} 套 · ${sets.length * 4} 件)`, "每套含 4 件染色皮甲，穿齐四件激活专属传说套装效果");
        const grid = document.createElement("div");
        grid.className = "armory-set-grid";
        sets.forEach(s => grid.appendChild(renderSetCard(s)));
        sec.appendChild(grid);
        elCatalog.appendChild(sec);
        renderedCount += sets.length;
      }
    }

    // 3. Dungeon Sets
    if (cat === "all" || cat === "dungeon_sets") {
      const sets = EQ.dungeonSets.filter(s => matchesFilters(setSearchText(s), itemRarityKey(null, s)));
      if (sets.length) {
        const sec = makeSection(`🏰 洞窟套装与昇华装备 (${sets.length} 套)`, "多楼层套装可直接点右上角 F1 / F2 / F3 实时对比属性");
        const grid = document.createElement("div");
        grid.className = "armory-set-grid";
        sets.forEach(s => grid.appendChild(renderSetCard(s)));
        sec.appendChild(grid);
        elCatalog.appendChild(sec);
        renderedCount += sets.length;
      }
    }

    // 4. Special Helmets & Masks (grouped by subGroup)
    if (cat === "all" || cat === "helmets") {
      const list = EQ.helmets.filter(it => matchesFilters(itemSearchText(it), itemRarityKey(it, null)));
      if (list.length) {
        const sec = makeSection(`🪖 特殊头盔与面具 (${list.length})`, "含定制 MC 3D 头颅贴图 · 黄金/钻石系列穿戴时实际属性 ×2");
        const groups = new Map();
        list.forEach(it => {
          const g = it.subGroup || "其他头盔";
          if (!groups.has(g)) groups.set(g, []);
          groups.get(g).push(it);
        });
        groups.forEach((items, gName) => {
          const lbl = document.createElement("div");
          lbl.className = "armory-subgroup-label";
          lbl.textContent = `${gName} (${items.length})`;
          sec.appendChild(lbl);
          const grid = document.createElement("div");
          grid.className = "armory-item-grid";
          items.forEach(it => grid.appendChild(renderStandaloneCard(it)));
          sec.appendChild(grid);
        });
        elCatalog.appendChild(sec);
        renderedCount += list.length;
      }
    }

    // 5. Cave Echoes & Special Items
    if (cat === "all" || cat === "echoes") {
      const list = EQ.echoes.filter(it => matchesFilters(itemSearchText(it), itemRarityKey(it, null)));
      if (list.length) {
        const sec = makeSection(`🔮 地心回声与特殊道具 (${list.length})`, "洞窟回声头颅、品质升级与建设纪念徽章");
        const grid = document.createElement("div");
        grid.className = "armory-item-grid";
        list.forEach(it => grid.appendChild(renderStandaloneCard(it)));
        sec.appendChild(grid);
        elCatalog.appendChild(sec);
        renderedCount += list.length;
      }
    }

    // 6. Profession Farming & Mining
    const profConfigs = [
      { key: "prof_farming", data: EQ.profFarming, title: "🌾 农业职业装备", sub: "包含农业武器、自动复种锄头与 16 套职业皮甲（支持一键跳转 3×3 配方）" },
      { key: "prof_mining", data: EQ.profMining, title: "⛏️ 挖矿职业装备", sub: "包含挖矿武器、高阶效率镐子与 16 套职业皮甲（支持一键跳转 3×3 配方）" }
    ];
    for (const pc of profConfigs) {
      if (cat !== "all" && cat !== pc.key) continue;
      const wList = pc.data.weapons.filter(it => matchesFilters(itemSearchText(it, pc.title), "职业"));
      const tList = pc.data.tools.filter(it => matchesFilters(itemSearchText(it, pc.title), "职业"));
      const sList = pc.data.sets.filter(s => matchesFilters(setSearchText(s) + " " + pc.title, "职业"));
      if (!wList.length && !tList.length && !sList.length) continue;

      const totalCount = wList.length + tList.length + sList.length * 4;
      const sec = makeSection(`${pc.title} (${totalCount} 件)`, pc.sub);

      if (wList.length || tList.length) {
        const lbl = document.createElement("div");
        lbl.className = "armory-subgroup-label";
        lbl.textContent = `职业武器与工具 (${wList.length + tList.length})`;
        sec.appendChild(lbl);
        const grid = document.createElement("div");
        grid.className = "armory-item-grid";
        [...wList, ...tList].forEach(it => grid.appendChild(renderStandaloneCard(it)));
        sec.appendChild(grid);
      }

      const mainSets = sList.filter(s => s.branch === "main");
      const branchSets = sList.filter(s => s.branch !== "main");
      if (mainSets.length) {
        const lbl = document.createElement("div");
        lbl.className = "armory-subgroup-label";
        lbl.textContent = `主线进阶套装 (${mainSets.length} 套)`;
        sec.appendChild(lbl);
        const grid = document.createElement("div");
        grid.className = "armory-set-grid";
        mainSets.forEach(s => grid.appendChild(renderSetCard(s)));
        sec.appendChild(grid);
      }
      if (branchSets.length) {
        const lbl = document.createElement("div");
        lbl.className = "armory-subgroup-label";
        lbl.textContent = `分支专精套装 (${branchSets.length} 套)`;
        sec.appendChild(lbl);
        const grid = document.createElement("div");
        grid.className = "armory-set-grid";
        branchSets.forEach(s => grid.appendChild(renderSetCard(s)));
        sec.appendChild(grid);
      }

      elCatalog.appendChild(sec);
      renderedCount += totalCount;
    }

    if (renderedCount === 0) {
      elCatalog.innerHTML = `<div class="armory-empty">没有找到匹配「${escapeHtml(state.search)}」的武器或装备，请尝试清空搜索词或切换稀有度/分类。</div>`;
    }
  }

  // ------------------------------------------------------------------
  // Category Tabs & Rarity Filter Bar
  // ------------------------------------------------------------------
  function getCategoryCount(catId) {
    const counts = {
      weapons: EQ.weapons.length,
      legendary_sets: EQ.legendarySets.length * 4,
      dungeon_sets: EQ.dungeonSets.length * 4,
      helmets: EQ.helmets.length,
      echoes: EQ.echoes.length,
      prof_farming: EQ.profFarming.weapons.length + EQ.profFarming.tools.length + EQ.profFarming.sets.length * 4,
      prof_mining: EQ.profMining.weapons.length + EQ.profMining.tools.length + EQ.profMining.sets.length * 4
    };
    if (catId === "all") {
      return Object.values(counts).reduce((a, b) => a + b, 0);
    }
    return counts[catId] || 0;
  }

  function renderTabs() {
    elTabs.innerHTML = "";
    CATEGORIES.forEach((c) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "armory-tab" + (state.category === c.id ? " active" : "");
      const iconSrc = c.skullTexture || `textures/${c.icon}.png`;
      btn.innerHTML = `
        <img class="mini-mc-tex" src="${iconSrc}" alt="" />
        <span>${c.label}</span>
        <span class="armory-tab-count">${getCategoryCount(c.id)}</span>
      `;
      btn.addEventListener("click", () => {
        state.category = c.id;
        const sim = SIM();
        if (sim) sim.playTone("click");
        renderTabs();
        renderCatalog();
      });
      elTabs.appendChild(btn);
    });
  }

  function renderRarityBar() {
    elRarityBar.innerHTML = "";
    RARITY_FILTERS.forEach((rf) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "rarity-pill" + (state.rarity === rf.id ? " active" : "");
      btn.textContent = rf.label;
      btn.addEventListener("click", () => {
        state.rarity = rf.id;
        const sim = SIM();
        if (sim) sim.playTone("click");
        renderRarityBar();
        renderCatalog();
      });
      elRarityBar.appendChild(btn);
    });
  }

  // Search bindings
  elSearch.addEventListener("input", () => {
    state.search = elSearch.value;
    elSearchClear.hidden = !state.search;
    renderCatalog();
  });
  elSearchClear.addEventListener("click", () => {
    state.search = "";
    elSearch.value = "";
    elSearchClear.hidden = true;
    renderCatalog();
  });

  function renderAll() {
    renderTabs();
    renderRarityBar();
    renderCatalog();
    renderInspector();
  }

  function openArmory() {
    const sim = SIM();
    if (sim) sim.setTopbarMode("armory");
    renderAll();
  }

  window.MAPLE_ARMORY = {
    open: openArmory,
    renderAll,
    renderMcText
  };

  // Pre-render once so switching is instant
  renderAll();
})();
