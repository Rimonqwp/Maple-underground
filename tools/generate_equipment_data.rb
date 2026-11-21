#!/usr/bin/env ruby
# frozen_string_literal: true

# Reads real plugin YAML configs from underground_item & underground_mayor
# and generates tools/collect-ui-preview/equipment-data.js so the web UI
# simulator mirrors in-game Minecraft displayName, Lore, colors, and stats 1:1.

require 'yaml'
require 'json'

ROOT = File.expand_path('..', __dir__)
WEAPONS_DIR = File.join(ROOT, 'underground_item/src/main/resources/weapons')
GEAR_DIR = File.join(ROOT, 'underground_item/src/main/resources/gear')
PROF_YML = File.join(ROOT, 'underground_item/src/main/resources/professions.yml')
MAYOR_BADGE_YML = File.join(ROOT, 'underground_mayor/src/main/resources/rewards/mayor_badge.yml')
SKULLS_DIR = File.join(ROOT, 'tools/collect-ui-preview/textures/skulls')
OUT_JS = File.join(ROOT, 'tools/collect-ui-preview/equipment-data.js')

ARMOR_SLOTS = %w[helmet chest leggings boots].freeze
SLOT_MATERIALS = {
  'helmet' => 'LEATHER_HELMET',
  'chest' => 'LEATHER_CHESTPLATE',
  'leggings' => 'LEATHER_LEGGINGS',
  'boots' => 'LEATHER_BOOTS'
}.freeze
SLOT_LABELS = {
  'helmet' => '头盔',
  'chest' => '胸甲',
  'leggings' => '护腿',
  'boots' => '战靴'
}.freeze

ROMAN = %w[_ I II III IV V VI VII VIII IX X XI XII].freeze

def roman(n)
  ROMAN[n.to_i] || n.to_s
end

def strip_mc_codes(str)
  return '' if str.nil?
  str.to_s
     .gsub(/[&§]#[0-9a-fA-F]{6}/, '')
     .gsub(/#[0-9a-fA-F]{6}/, '')
     .gsub(/[&§][0-9a-fA-Fk-oK-OrR]/, '')
     .strip
end

def match_rarity_word(text)
  return '永恆' if text.include?('永恆') || text.include?('永恒')
  return '神話' if text.include?('神話') || text.include?('神话')
  return '傳說' if text.include?('傳說') || text.include?('传说') || text.include?('傳奇') || text.include?('传奇')
  return '史詩' if text.include?('史詩') || text.include?('史诗')
  return '稀有' if text.include?('稀有')
  return '普通' if text.include?('普通')
  nil
end

def detect_rarity(explicit_rarity, lore_lines)
  r = explicit_rarity.to_s.strip
  return normalize_rarity(r) unless r.empty?
  plain_lines = Array(lore_lines).map { |l| strip_mc_codes(l) }.reject(&:empty?)
  # 1. Prefer the bracketed MC rarity footer line, e.g. "[稀有洞窟面具]"
  bracket_line = plain_lines.reverse.find { |l| l.include?('[') && l.include?(']') }
  if bracket_line
    found = match_rarity_word(bracket_line)
    return found if found
  end
  # 2. Otherwise check the last line, then all lines
  found = match_rarity_word(plain_lines.last.to_s)
  return found if found
  match_rarity_word(plain_lines.join(' ')) || '特殊'
end

def normalize_rarity(r)
  case r
  when '傳奇', '传奇', '传说' then '傳說'
  when '史诗' then '史詩'
  when '神话' then '神話'
  when '永恒' then '永恆'
  else r
  end
end

def rgb_hex_from_cfg(cfg, default_hex = '#777777')
  hex = cfg['leather_color_hex'].to_s.strip
  return (hex.start_with?('#') ? hex : "##{hex}") if hex.match?(/\A#?[0-9a-fA-F]{6}\z/)
  rgb = cfg['leather_color_rgb']
  if rgb.is_a?(Array) && rgb.length >= 3
    return format('#%02x%02x%02x', rgb[0].to_i, rgb[1].to_i, rgb[2].to_i)
  end
  default_hex
end

def int_color_to_hex(val)
  format('#%06x', val.to_i & 0xFFFFFF)
end

def replace_placeholders(lines, map)
  Array(lines).map do |line|
    s = line.to_s.dup
    map.each do |k, v|
      s.gsub!("{#{k}}", v.to_s)
    end
    s
  end
end

def skull_tex_for(id, tex_id)
  if !tex_id.to_s.strip.empty? && File.exist?(File.join(SKULLS_DIR, "#{id}.png"))
    "textures/skulls/#{id}.png"
  else
    nil
  end
end

def format_drop(drop)
  return nil unless drop.is_a?(Hash) && !drop.empty?
  parts = []
  if drop['floors'].is_a?(Array) && !drop['floors'].empty?
    parts << drop['floors'].join('/')
  elsif drop['floor']
    parts << drop['floor'].to_s
  end
  if drop['entity_type'] || drop['monster_type']
    mob_map = {
      'ZOMBIE_TANK' => '坦克僵尸',
      'WITCH' => '地牢女巫',
      'SKELETON' => '地牢骷髅'
    }
    raw_mob = (drop['monster_type'] || drop['entity_type']).to_s
    parts << (mob_map[raw_mob] || raw_mob)
  end
  if drop['chance']
    pct = (drop['chance'].to_f * 100.0)
    pct_str = (pct == pct.to_i) ? "#{pct.to_i}%" : format('%.1f%%', pct)
    parts << "#{pct_str} 掉落"
  end
  parts.empty? ? nil : parts.join(' · ')
end

def fmt_num(v)
  f = v.to_f
  (f == f.to_i) ? f.to_i.to_s : f.to_s
end

# ------------------------------------------------------------------
# 1. WEAPONS (underground_item/src/main/resources/weapons/*.yml)
# ------------------------------------------------------------------
WEAPON_ORDER = %w[
  tremor_blade_1 tremor_blade_2 tremor_blade_3
  love_tap_stone dreadlord_iron zombie_knight_iron scarf_adaptive_sword
  emerald_blade teleport_diamond dragon_rage_diamond
  cyclone_stick showtime_hook
].freeze

weapon_files = Dir.glob(File.join(WEAPONS_DIR, '*.yml')).map { |p| [File.basename(p, '.yml'), p] }.to_h
ordered_weapon_ids = WEAPON_ORDER.select { |id| weapon_files.key?(id) } + (weapon_files.keys - WEAPON_ORDER).sort

weapons = ordered_weapon_ids.map do |id|
  path = weapon_files[id]
  cfg = YAML.load_file(path)
  ph = {}
  (cfg['stats'] || {}).each { |k, v| ph[k.to_s] = v.to_s }
  if cfg['ability'].is_a?(Hash)
    ab = cfg['ability']
    ph['skill_name'] = ab['name'].to_s if ab.key?('name')
    ph['mana_cost'] = ab['mana_cost'].to_i.to_s if ab.key?('mana_cost')
    ph['base_ability_damage'] = ab['base_ability_damage'].to_i.to_s if ab.key?('base_ability_damage')
    ph['ability_scaling'] = ab['ability_scaling'].to_s if ab.key?('ability_scaling')
    ph['cooldown_seconds'] = ab['cooldown_seconds'].to_i.to_s if ab.key?('cooldown_seconds')
    ph['teleport_blocks'] = ab['teleport_blocks'].to_i.to_s if ab.key?('teleport_blocks')
    ph['speed_bonus'] = ab['speed_bonus'].to_i.to_s if ab.key?('speed_bonus')
    ph['speed_duration_seconds'] = ab['speed_duration_seconds'].to_s if ab.key?('speed_duration_seconds')
  end
  if cfg['skill'].is_a?(Hash)
    sk = cfg['skill']
    ph['skill_name'] = sk['name'].to_s if sk.key?('name')
    ph['mana_cost'] = sk['mana_cost'].to_i.to_s if sk.key?('mana_cost')
    ph['base_ability_damage'] = sk['base_ability_damage'].to_i.to_s if sk.key?('base_ability_damage')
    ph['ability_scaling'] = sk['ability_scaling'].to_s if sk.key?('ability_scaling')
    if sk['type'].to_s.downcase == 'freeze'
      base_d = sk['base_ability_damage'].to_f
      scaling = sk['ability_scaling'].to_f
      example_dmg = base_d * (1.0 + (200.0 / 100.0) * scaling)
      ph['skill_damage'] = format('%.0f', example_dmg)
    end
  end
  if cfg['class_bonus'].is_a?(Hash)
    cfg['class_bonus'].each do |ck, cv|
      next unless cv.is_a?(Hash)
      cv.each { |k, v| ph["#{ck}_#{k}"] = v.to_s }
    end
  end
  if cfg['set_bonus'].is_a?(Hash)
    sb = cfg['set_bonus']
    ph['set_bonus_name'] = (sb['bonus_name'] || sb['name']).to_s if sb['bonus_name'] || sb['name']
    ph['set_bonus_strength'] = sb['strength'].to_s if sb.key?('strength')
  end

  lore = replace_placeholders(cfg['lore'], ph)
  display_raw = cfg['display_name'].to_s
  {
    id: id,
    category: 'weapons',
    namePlain: strip_mc_codes(display_raw),
    displayName: display_raw,
    defaultTitleColor: '#ffaa00',
    material: cfg['material'] || 'BLAZE_ROD',
    lore: lore,
    rarity: detect_rarity(cfg['rarity'], lore),
    stats: cfg['stats'] || {},
    dropInfo: format_drop(cfg['drop']),
    giveCmd: "/gear give weapon #{id}",
    sourceFile: "underground_item/src/main/resources/weapons/#{id}.yml"
  }
end

# ------------------------------------------------------------------
# 2. LEGENDARY BLOOD SETS (gear/blood_*_set.yml)
# ------------------------------------------------------------------
BLOOD_SET_IDS = %w[
  blood_holy_set blood_protective_set blood_wise_set blood_unstable_set
  blood_young_set blood_old_set blood_strong_set blood_superior_set
].freeze

legendary_sets = BLOOD_SET_IDS.map do |set_id|
  path = File.join(GEAR_DIR, "#{set_id}.yml")
  cfg = YAML.load_file(path)
  color_hex = rgb_hex_from_cfg(cfg, '#C41E3A')
  sb = cfg['set_bonus'].is_a?(Hash) ? cfg['set_bonus'] : {}
  total_stats = Hash.new(0)
  pieces = ARMOR_SLOTS.map do |slot|
    st = (cfg['stats'] && cfg['stats'][slot]) || {}
    st.each { |k, v| total_stats[k] += v.to_i if v.is_a?(Numeric) }
    ph = {}
    st.each { |k, v| ph[k.to_s] = v.to_s }
    sb.each { |k, v| ph[k.to_s] = v.to_s }
    raw_name = (cfg['pieces'] && cfg['pieces'][slot]) || "#{cfg['display_name_prefix']}#{SLOT_LABELS[slot]}"
    lore = replace_placeholders((cfg['lore'] && cfg['lore'][slot]) || [], ph)
    {
      id: "#{set_id}:#{slot}",
      slot: slot,
      slotLabel: SLOT_LABELS[slot],
      namePlain: strip_mc_codes(raw_name),
      displayName: raw_name,
      material: SLOT_MATERIALS[slot],
      colorHex: color_hex,
      lore: lore,
      stats: st,
      giveCmd: "/gear give blood #{set_id} #{slot}"
    }
  end
  sample_lore = pieces.first ? pieces.first[:lore] : []
  {
    id: set_id,
    category: 'legendary_sets',
    name: "#{cfg['display_name_prefix']}套装",
    series: cfg['series'] || '传说套装',
    rarity: detect_rarity(cfg['rarity'], sample_lore),
    colorHex: color_hex,
    hasSetProgress: true,
    defaultSetProgress: 4,
    abilityTitle: cfg.dig('ability', 'name'),
    abilityDesc: cfg.dig('ability', 'description'),
    totalStats: total_stats,
    sourceFile: "underground_item/src/main/resources/gear/#{set_id}.yml",
    pieces: pieces
  }
end

# ------------------------------------------------------------------
# 3. DUNGEON SETS (defeat_tank, sieve, archer, skeleton_soldier,
#    skeleton_master, cavern_explorer, adaptive, sublimation_azur)
# ------------------------------------------------------------------
def build_floor_set(set_id, filename, give_cmd_fn, extra_ph_fn, defense_plus_sign: false)
  path = File.join(GEAR_DIR, filename)
  cfg = YAML.load_file(path)
  color_hex = rgb_hex_from_cfg(cfg, '#666666')
  floors_hash = cfg['floors'] || {}
  floor_keys = floors_hash.keys
  floors_data = {}

  floor_keys.each do |fl|
    total_stats = Hash.new(0)
    pieces = ARMOR_SLOTS.map do |slot|
      st = (floors_hash[fl] && floors_hash[fl][slot]) || {}
      st.each { |k, v| total_stats[k] += v.to_i if v.is_a?(Numeric) }
      ph = {}
      st.each do |k, v|
        if defense_plus_sign && k == 'defense'
          ph[k.to_s] = (v.to_i >= 0 ? "+#{v.to_i}" : v.to_i.to_s)
        else
          ph[k.to_s] = v.to_s
        end
      end
      ph.merge!(extra_ph_fn.call(cfg, fl, slot))
      raw_base = (cfg['pieces'] && cfg['pieces'][slot]) || "#{cfg['display_name_prefix']}#{SLOT_LABELS[slot]}"
      raw_name = "#{raw_base} &e(#{fl})"
      lore_src = cfg['lore'].is_a?(Hash) ? (cfg['lore'][slot] || []) : Array(cfg['lore'])
      lore = replace_placeholders(lore_src, ph)
      {
        id: "#{set_id}:#{fl}:#{slot}",
        slot: slot,
        slotLabel: SLOT_LABELS[slot],
        floor: fl,
        namePlain: strip_mc_codes(raw_name),
        displayName: raw_name,
        material: SLOT_MATERIALS[slot],
        colorHex: color_hex,
        lore: lore,
        stats: st,
        giveCmd: give_cmd_fn.call(slot, fl)
      }
    end
    floors_data[fl] = { totalStats: total_stats, pieces: pieces }
  end

  first_fl = floor_keys.first
  sample_lore = floors_data[first_fl][:pieces].first[:lore]
  has_progress = sample_lore.any? { |l| l.include?('{set_progress}') }
  {
    id: set_id,
    category: 'dungeon_sets',
    name: strip_mc_codes(cfg['display_name_prefix'] || set_id),
    series: floor_keys.length > 1 ? "洞窟楼层套装 (#{floor_keys.join('/')})" : "洞窟 #{first_fl} 套装",
    rarity: detect_rarity(cfg['rarity'], sample_lore),
    colorHex: color_hex,
    floors: floor_keys,
    defaultFloor: first_fl,
    hasSetProgress: has_progress,
    defaultSetProgress: 4,
    dropInfo: format_drop(cfg['drop']),
    sourceFile: "underground_item/src/main/resources/gear/#{filename}",
    floorsData: floors_data
  }
end

dungeon_sets = [
  build_floor_set(
    'defeat_tank', 'defeat_tank_set.yml',
    ->(slot, fl) { "/gear give defeat_tank #{slot} #{fl}" },
    ->(cfg, _fl, _slot) {
      { 'speed_per_50_defense' => cfg.dig('set_bonus', 'speed_per_50_defense') || 1 }
    },
    defense_plus_sign: true
  ),
  build_floor_set(
    'sieve_set', 'sieve_set.yml',
    ->(slot, fl) { "/gear give sieve #{slot} #{fl}" },
    ->(cfg, _fl, _slot) {
      ar = cfg['arrow_knockback_resistance_per_piece'] || 15
      sbar = cfg['set_bonus_arrow_resistance'] || 20
      {
        'arrow_resist' => ar,
        'arrow_knockback_resistance_per_piece' => ar,
        'set_bonus_arrow_resistance' => sbar
      }
    }
  ),
  build_floor_set(
    'archer_set', 'archer_set.yml',
    ->(slot, fl) { "/gear give archer #{slot} #{fl}" },
    ->(cfg, _fl, _slot) {
      { 'arrow_damage' => cfg['arrow_damage_percent_per_piece'] || 5 }
    }
  ),
  build_floor_set(
    'skeleton_soldier_set', 'skeleton_soldier_set.yml',
    ->(slot, fl) { "/gear give skeleton_soldier #{slot} #{fl}" },
    ->(cfg, _fl, _slot) {
      ad = cfg['arrow_damage_percent_per_piece'] || 5
      sbad = cfg['set_bonus_arrow_damage_percent'] || 25
      {
        'arrow_damage' => ad,
        'arrow_damage_percent_per_piece' => ad,
        'set_bonus_arrow_damage' => sbad,
        'set_bonus_arrow_damage_percent' => sbad
      }
    }
  ),
  build_floor_set(
    'skeleton_master_set', 'skeleton_master_set.yml',
    ->(slot, fl) { "/gear give skeleton_master #{slot} #{fl}" },
    ->(cfg, _fl, _slot) {
      ad = cfg['arrow_damage_percent_per_piece'] || 5
      sbad = cfg['set_bonus_arrow_damage_percent'] || 25
      {
        'arrow_damage' => ad,
        'arrow_damage_percent_per_piece' => ad,
        'set_bonus_arrow_damage_percent' => sbad
      }
    }
  ),
  build_floor_set(
    'cavern_explorer_set', 'cavern_explorer_set.yml',
    ->(slot, _fl) { "/gear give cavern_explorer #{slot}" },
    ->(cfg, _fl, _slot) {
      {
        'walk_speed_bonus' => cfg.dig('set_bonus', 'walk_speed_bonus') || 5,
        'damage_reduction_percent' => cfg.dig('set_bonus', 'damage_reduction_percent') || 5
      }
    }
  ),
  build_floor_set(
    'adaptive_set', 'adaptive_set.yml',
    ->(slot, _fl) { "/gear give adaptive #{slot}" },
    ->(_cfg, _fl, _slot) { {} }
  )
]

# Also include Sublimation Azur Set (昇華 · 蒼界套裝) from SublimationWorkbenchManager.java
RARE_PALETTE = %w[7FAFFF 8AB2FF 96B6FF A1B9FF ADBDFF B8C0FF C4C4FF CFC7FF].freeze
def gradient_name(text)
  chars = text.chars
  out = +'&l'
  chars.each_with_index do |ch, i|
    hex = RARE_PALETTE[(i * RARE_PALETTE.length) / [chars.length, 1].max]
    out << "&##{hex}#{ch}"
  end
  out
end

sublimation_pieces = [
  ['helmet', '頭盔', 'NETHERITE_HELMET', 43, 29],
  ['chest', '胸甲', 'NETHERITE_CHESTPLATE', 72, 47],
  ['leggings', '護腿', 'NETHERITE_LEGGINGS', 66, 31],
  ['boots', '靴子', 'NETHERITE_BOOTS', 21, 20]
].map do |slot, cname, mat, hp, df|
  raw_name = gradient_name("昇華 · 蒼界#{cname}")
  {
    id: "sublimation_azur:#{slot}",
    slot: slot,
    slotLabel: SLOT_LABELS[slot],
    namePlain: "昇華 · 蒼界#{cname}",
    displayName: raw_name,
    material: mat,
    colorHex: nil,
    glow: true,
    lore: [
      '&8可放在鐵砧/附魔台',
      '&7洞窟評分: &d-',
      "&7生命: &a+#{hp}",
      "&7防禦: &a+#{df} &8(每級保護附魔額外 +5 防禦)",
      '',
      '&7似乎為二次終界之城所遺留下來的殘骸',
      '&7洗鍊多次所昇華出來的精華',
      "&7遠遠不如1000年前的村民#{cname}",
      '',
      '&#D6FFB4不&#A7FF95可&#79FF76破&#4AFF57壞',
      '&#7FAFFF[&#8AB2FF稀&#96B6FF有&#A1B9FF洞&#ADBDFF窟&#B8C0FF裝&#C4C4FF備&#CFC7FF]'
    ],
    stats: { 'health' => hp, 'defense' => df },
    giveCmd: '/sublimation_workbench'
  }
end

dungeon_sets << {
  id: 'sublimation_azur',
  category: 'dungeon_sets',
  name: '昇華 · 蒼界套裝',
  series: '昇華工作台重鑄裝備',
  rarity: '稀有',
  colorHex: '#7FAFFF',
  hasSetProgress: false,
  dropInfo: '使用 /sublimation_workbench 昇華工作台轉換',
  sourceFile: 'underground_item/src/main/java/maple/underground_caves/gear/SublimationWorkbenchManager.java',
  totalStats: { 'health' => 202, 'defense' => 127 },
  pieces: sublimation_pieces
}

# ------------------------------------------------------------------
# 4. SPECIAL HELMETS & MASKS
# ------------------------------------------------------------------
HELMET_GROUPS = [
  ['immortal_skull', '特殊面具 / 保命', '/immortalskull', false],
  ['second_wind_helmet', '特殊头盔 / 保命', '/gear give second_wind_helmet', false],
  ['arrow_distance_helmet', '特殊头盔 / 弓箭', '/gear give arrow_distance_helmet', false],
  ['mending_helmet_1', '治疗头盔系列', '/gear give mending_helmet_1', false],
  ['mending_helmet_2', '治疗头盔系列', '/gear give mending_helmet_2', false],
  ['mending_helmet_3', '治疗头盔系列', '/gear give mending_helmet_3', false],
  ['ability_damage_helmet_1', '法术增幅头盔系列', '/gear give ability_damage_helmet_1', false],
  ['ability_damage_helmet_2', '法术增幅头盔系列', '/gear give ability_damage_helmet_2', false],
  ['ability_damage_helmet_3', '法术增幅头盔系列', '/gear give ability_damage_helmet_3', false],
  ['golden_villager_helmet', '黄金 / 钻石双倍头盔', '/gear give golden_villager_helmet', true],
  ['golden_panda_helmet', '黄金 / 钻石双倍头盔', '/gear give golden_panda_helmet', true],
  ['golden_sheep_helmet', '黄金 / 钻石双倍头盔', '/gear give golden_sheep_helmet', true],
  ['diamond_villager_helmet', '黄金 / 钻石双倍头盔', '/gear give diamond_villager_helmet', true],
  ['diamond_panda_helmet', '黄金 / 钻石双倍头盔', '/gear give diamond_panda_helmet', true],
  ['diamond_sheep_helmet', '黄金 / 钻石双倍头盔', '/gear give diamond_sheep_helmet', true],
  ['underground_cave_helmet_f1', '地下洞穴基础头盔', '/gear give cave_helmet F1', false],
  ['underground_cave_helmet_f2', '地下洞穴基础头盔', '/gear give cave_helmet F2', false],
  ['underground_cave_helmet_f3', '地下洞穴基础头盔', '/gear give cave_helmet F3', false]
].freeze

helmets = HELMET_GROUPS.map do |id, sub_group, give_cmd, double_stats|
  path = File.join(GEAR_DIR, "#{id}.yml")
  cfg = YAML.load_file(path)
  st = cfg['stats'] || {}
  eff = cfg['effect'] || {}
  ph = {}
  st.each { |k, v| ph[k.to_s] = v.to_s }
  eff.each { |k, v| ph[k.to_s] = v.to_s }
  ph['effect_duration_seconds'] = eff['duration_seconds'].to_s if eff.key?('duration_seconds')
  ph['cooldown_seconds'] = cfg['cooldown_seconds'].to_s if cfg.key?('cooldown_seconds')
  if cfg['ability'].is_a?(Hash)
    cfg['ability'].each { |k, v| ph[k.to_s] = v.to_s }
  end
  lore = replace_placeholders(cfg['lore'], ph)
  display_raw = cfg['display_name'].to_s
  {
    id: id,
    category: 'helmets',
    subGroup: sub_group,
    namePlain: strip_mc_codes(display_raw),
    displayName: display_raw,
    material: 'PLAYER_HEAD',
    skullTexture: skull_tex_for(id, cfg['texture_id']),
    textureId: cfg['texture_id'].to_s,
    lore: lore,
    rarity: detect_rarity(cfg['rarity'], lore),
    stats: st,
    equippedDoubleStats: double_stats,
    dropInfo: format_drop(cfg['drop']),
    giveCmd: give_cmd,
    sourceFile: "underground_item/src/main/resources/gear/#{id}.yml"
  }
end

# ------------------------------------------------------------------
# 5. CAVE ECHOES & SPECIAL ITEMS
# ------------------------------------------------------------------
ECHO_IDS = %w[
  cave_echo_motherrock cave_echo_stalactite cave_echo_firefly
  cave_echo_leyline cave_echo_obsidian quality_upgrade
].freeze

echoes = ECHO_IDS.map do |id|
  path = File.join(GEAR_DIR, "#{id}.yml")
  cfg = YAML.load_file(path)
  lore = Array(cfg['lore']).map(&:to_s)
  display_raw = cfg['display_name'].to_s
  {
    id: id,
    category: 'echoes',
    subGroup: id.start_with?('cave_echo_') ? '地心回声系列' : '特殊功能道具',
    namePlain: strip_mc_codes(display_raw),
    displayName: display_raw,
    material: 'PLAYER_HEAD',
    skullTexture: skull_tex_for(id, cfg['texture_id']),
    textureId: cfg['texture_id'].to_s,
    lore: lore,
    rarity: detect_rarity(cfg['rarity'], lore),
    stats: cfg['stats'] || {},
    giveCmd: id == 'quality_upgrade' ? '/gear give quality_upgrade' : "/gear give #{id}",
    sourceFile: "underground_item/src/main/resources/gear/#{id}.yml"
  }
end

if File.exist?(MAYOR_BADGE_YML)
  mcfg = YAML.load_file(MAYOR_BADGE_YML)['item'] || {}
  mlore = replace_placeholders(mcfg['lore'], {
    'contributed_this_turn' => '1,000',
    'player_contribution' => '5,000'
  })
  echoes << {
    id: 'mayor_badge',
    category: 'echoes',
    subGroup: '特殊功能道具',
    namePlain: strip_mc_codes(mcfg['display_name']),
    displayName: mcfg['display_name'].to_s,
    material: mcfg['material'] || 'PAPER',
    glow: !!mcfg['glow'],
    lore: mlore,
    rarity: detect_rarity(nil, mlore),
    stats: {},
    giveCmd: '市长建设活动结算发放',
    sourceFile: 'underground_mayor/src/main/resources/rewards/mayor_badge.yml'
  }
end

# ------------------------------------------------------------------
# 6. PROFESSION GEAR (underground_item/src/main/resources/professions.yml)
# ------------------------------------------------------------------
prof = YAML.load_file(PROF_YML)
prof_sets_cfg = prof['sets'] || {}
prof_items_cfg = prof['items'] || {}

FIELD_LABELS = {
  'fortune' => '职业幸运',
  'xp' => '职业经验',
  'rare' => '稀有概率相对提升',
  'event' => '事件概率相对提升'
}.freeze

BRANCH_LABELS = {
  'main' => '主线套装',
  'yield' => '产量专精',
  'experience' => '经验专精',
  'balanced' => '均衡专精',
  'rare' => '稀有专精',
  'event' => '事件专精'
}.freeze

RARITY_PALETTES = {
  '普通' => %w[E9FFF2 DDFBEE D1F7EA C5F3E6 BAEFE3 AEEBDF A2E7DB 96E3D7],
  '稀有' => %w[7FAFFF 8AB2FF 96B6FF A1B9FF ADBDFF B8C0FF C4C4FF CFC7FF],
  '史诗' => %w[C09BFF C79EF8 CFA2F1 D6A5EA DDA9E3 E4ACDC ECB0D5 F3B3CE],
  '传说' => %w[FFE4B8 FFDFC0 FFD9C8 FFD4CF FFCED7 FFC9DF FFC3E8 FFBEF0],
  '神话' => %w[FFB6F0 FFAFE8 FFA9E0 FFA2D8 FF9CD0 FF95C8 FF8FC0 FF88B8],
  '永恒' => %w[FFEDEE FFE7EA FFE1E6 FFDBE2 FFD5DE FFCFDA FFC9D6 FFC3D2]
}.freeze

def prof_rarity_of(set_cfg)
  return '普通' unless set_cfg
  branch = set_cfg['branch'].to_s
  tier = set_cfg['tier'].to_i
  if branch == 'main'
    case tier
    when 1 then '普通'
    when 2 then '稀有'
    when 3 then '史诗'
    when 4 then '传说'
    when 5 then '神话'
    else '永恒'
    end
  else
    tier >= 2 ? '传说' : '史诗'
  end
end

def prof_gradient(text, rarity, bold: false)
  palette = RARITY_PALETTES[rarity] || RARITY_PALETTES['普通']
  chars = text.chars
  out = +(bold ? '&l' : '')
  chars.each_with_index do |ch, i|
    hex = palette[(i * palette.length) / [chars.length, 1].max]
    out << "&##{hex}#{ch}"
  end
  out
end

def fmt_stat(v)
  f = v.to_f
  (f == f.to_i) ? f.to_i.to_s : f.to_s
end

def build_prof_lore(item_cfg, set_cfg)
  kind = item_cfg['kind'].to_s
  job_cn = item_cfg['job'] == 'farming' ? '农业' : '挖矿'
  rarity = prof_rarity_of(set_cfg)
  lore = []
  lore << '&8可放在铁砧/附魔台'
  lore << "&7#{job_cn}等级要求: &e#{item_cfg['level'].to_i}"
  if kind == 'armor' && set_cfg
    lore << "&7生命: &a+#{fmt_stat(set_cfg['health'].to_f / 4.0)}"
    lore << "&7防御: &a+#{fmt_stat(set_cfg['defense'].to_f / 4.0)}"
    %w[fortune xp rare event].each do |field|
      val = set_cfg[field].to_f
      next unless val > 0
      color = case field
              when 'fortune' then '&a'
              when 'xp' then '&b'
              when 'rare' then '&d'
              else '&6'
              end
      suffix = field == 'fortune' ? '' : '%'
      lore << "&7#{FIELD_LABELS[field]}: #{color}+#{fmt_stat(val / 5.0)}#{suffix}"
    end
    lore << ''
    lore << '&#FFE066◆ &#FFCB49套&#FFC13A装&#FFB72C奖&#FFAD1D励 &#FF9800◆'
    lore << '&7四件同套：加成补足至整套数值'
    if set_cfg['reduction'].to_f > 0
      lore << "&7同套职业怪物减伤 &a#{fmt_stat(set_cfg['reduction'])}%"
    end
  elsif kind == 'tool'
    lore << "&7职业幸运: &a+#{fmt_stat(item_cfg['fortune'])}"
    if item_cfg['xp'].to_f > 0
      lore << "&7职业经验: &b+#{fmt_stat(item_cfg['xp'])}%"
    end
    if item_cfg['efficiency'].to_i > 0
      lore << "&7效率: &b#{roman(item_cfg['efficiency'].to_i)}"
    end
    if item_cfg['replant']
      lore << ''
      lore << '&#D946EF✦ &#E46178被&#E76B50动&#EB783F效&#EE852E果 &#F59E0B✦'
      lore << '&6能力：&e自动复种'
      lore << '&7成熟作物自动复种，消耗背包种植材料。'
    end
  elsif kind == 'weapon'
    lore << "&7伤害: &c+#{fmt_stat(item_cfg['damage'])}"
    lore << "&7力量: &c+#{fmt_stat(item_cfg['strength'])}"
    lore << ''
    lore << '&#D946EF✦ &#E46178被&#E76B50动&#EB783F效&#EE852E果 &#F59E0B✦'
    lore << '&6能力：&e职业猎手'
    lore << "&7对对应职业怪物造成伤害 &c+#{fmt_stat(item_cfg['mob_bonus'])}%&7。"
  end
  lore << ''
  lore << '&#D6FFB4不&#A7FF95可&#79FF76破&#4AFF57坏'
  lore << prof_gradient("[#{rarity}#{job_cn}世界装备]", rarity, bold: false)
  lore
end

def build_prof_requirements(item_cfg, set_cfg)
  reqs = []
  reqs << "技能等级 #{item_cfg['level'].to_i}"
  r = set_cfg && set_cfg['requirements']
  if r.is_a?(Hash)
    if r['collections'].is_a?(Hash)
      r['collections'].each { |m, amt| reqs << "#{m} 收藏 #{amt}" }
    end
    if r['diverse_count'].to_i > 0
      reqs << "指定作物任意 #{r['diverse_count']} 种各 #{r['diverse_amount']}"
    end
    reqs << "普通事件击败 #{r['normal']} 次" if r['normal'].to_i > 0
    reqs << "精英事件击败 #{r['elite']} 次" if r['elite'].to_i > 0
  end
  reqs
end

def build_profession_section(job, prof_sets_cfg, prof_items_cfg)
  weapons_and_tools = []
  armor_by_set = {}

  prof_items_cfg.each do |id, ic|
    next if ic['kind'] == 'material'
    next unless ic['job'] == job
    set_id = (ic['set'] || ic['gate']).to_s
    sc = prof_sets_cfg[set_id]
    eff = ic['efficiency'].to_i
    rarity = prof_rarity_of(sc)
    mc_lore = build_prof_lore(ic, sc)

    item_obj = {
      id: id,
      category: "prof_#{job}",
      kind: ic['kind'],
      job: job,
      setId: set_id,
      level: ic['level'].to_i,
      rarity: normalize_rarity(rarity),
      namePlain: ic['name'].to_s,
      displayName: prof_gradient(ic['name'].to_s, rarity, bold: true),
      material: ic['material'],
      colorHex: ic.key?('color') ? int_color_to_hex(ic['color']) : nil,
      glow: eff > 0,
      lore: mc_lore,
      requirements: build_prof_requirements(ic, sc),
      recipe: ic['recipe'],
      sourceFile: 'underground_item/src/main/resources/professions.yml'
    }

    if ic['kind'] == 'armor'
      slot = case ic['material']
             when 'LEATHER_HELMET' then 'helmet'
             when 'LEATHER_CHESTPLATE' then 'chest'
             when 'LEATHER_LEGGINGS' then 'leggings'
             else 'boots'
             end
      item_obj[:slot] = slot
      item_obj[:slotLabel] = SLOT_LABELS[slot]
      armor_by_set[set_id] ||= []
      armor_by_set[set_id] << item_obj
    else
      weapons_and_tools << item_obj
    end
  end

  sets = prof_sets_cfg.select { |sid, _| armor_by_set.key?(sid) }.map do |sid, sc|
    pcs = armor_by_set[sid]
    pcs.sort_by! { |p| ARMOR_SLOTS.index(p[:slot]) || 9 }
    branch_key = sid.sub(/\A#{job}_/, '').sub(/_\d+\z/, '')
    tier_num = sid[/\d+\z/].to_i
    first_name = pcs.first[:namePlain].sub(/(头盔|胸甲|护腿|靴子)\z/, '')
    {
      id: sid,
      category: "prof_#{job}",
      name: "#{first_name}套装",
      rarity: normalize_rarity(prof_rarity_of(sc)),
      branch: branch_key,
      branchLabel: "#{BRANCH_LABELS[branch_key] || branch_key} · T#{tier_num}",
      level: pcs.first[:level],
      colorHex: pcs.first[:colorHex],
      totalStats: {
        'health' => sc['health'],
        'defense' => sc['defense'],
        'fortune' => sc['fortune'],
        'xp' => sc['xp'],
        'rare' => sc['rare'],
        'event' => sc['event'],
        'reduction' => sc['reduction']
      }.reject { |_, v| v.nil? || v.to_f == 0.0 },
      requirements: pcs.first[:requirements],
      sourceFile: 'underground_item/src/main/resources/professions.yml',
      pieces: pcs
    }
  end

  {
    weapons: weapons_and_tools.select { |x| x[:kind] == 'weapon' },
    tools: weapons_and_tools.select { |x| x[:kind] == 'tool' },
    sets: sets
  }
end

prof_farming = build_profession_section('farming', prof_sets_cfg, prof_items_cfg)
prof_mining = build_profession_section('mining', prof_sets_cfg, prof_items_cfg)

payload = {
  generatedAt: Time.now.strftime('%Y-%m-%d %H:%M:%S'),
  weapons: weapons,
  legendarySets: legendary_sets,
  dungeonSets: dungeon_sets,
  helmets: helmets,
  echoes: echoes,
  profFarming: prof_farming,
  profMining: prof_mining
}

js = "// Auto-generated by tools/generate_equipment_data.rb from real plugin YAMLs.\n" \
     "// Do not edit manually; re-run `ruby tools/generate_equipment_data.rb` after changing weapon/gear/profession configs.\n" \
     "window.EQUIPMENT_DATA = #{JSON.pretty_generate(payload)};\n"

File.write(OUT_JS, js)

puts "Generated #{OUT_JS} (#{js.bytesize} bytes)"
puts "  weapons: #{weapons.length}"
puts "  legendarySets: #{legendary_sets.length} (#{legendary_sets.sum { |s| s[:pieces].length }} pieces)"
puts "  dungeonSets: #{dungeon_sets.length}"
puts "  helmets: #{helmets.length}"
puts "  echoes: #{echoes.length}"
puts "  profFarming: #{prof_farming[:weapons].length} weapons, #{prof_farming[:tools].length} tools, #{prof_farming[:sets].length} sets"
puts "  profMining: #{prof_mining[:weapons].length} weapons, #{prof_mining[:tools].length} tools, #{prof_mining[:sets].length} sets"
