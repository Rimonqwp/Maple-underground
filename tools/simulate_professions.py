"""Deterministic leveling integration and seeded collection/event model (no server/database)."""
import json, math, random, re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'underground_state/src/main/java/maple/underground_state/skills/CombatLevelTable.java').read_text(encoding='utf-8')
xp=[int(v.replace('_','').replace('L','')) for v in re.search(r'XP_REQUIRED\s*=\s*\{(.*?)\}',source,re.S).group(1).split(',') if v.strip()]
catalog=json.loads((ROOT/'underground_item/src/main/resources/professions.yml').read_text(encoding='utf-8'))
levels=[0,8,18,30,42,55]

def gear(job,route,level):
    tier=max(i+1 for i,l in enumerate(levels) if level>=l)
    main=catalog['sets'][f'{job}_main_{tier}']
    choices=[s for s in catalog['sets'].values() if s['job']==job and s['branch']==route and s['level']<=level]
    return max(choices,key=lambda s:s['tier']) if choices else main, catalog['items'][f'{job}_tool_{tier}']

def leveling(job,route,target):
    hours=0
    for level in range(target):
        armor,tool=gear(job,route,level)
        gain=min(40,armor['xp']+tool['xp'])
        hours+=xp[level]/((5000 if job=='farming' else 6000)*(1+(0.06 if job=='farming' else .05)*level**2)*(1+gain/100))
    return hours

def stationary(job,route,hours=1000):
    # Level 55, one ordinary base portion/action, no collection fortune, enchantment or skill baseline bonuses.
    armor,tool=gear(job,route,55)
    fortune=110+armor['fortune']+tool['fortune']
    interval=3600/(6250 if job=='farming' else 3750)
    rng=random.Random(20261001)
    counts=dict(products=0,rare=0,normal=0,elite=0,elite_core=0)
    rare_misses=normal_misses=elite_misses=core_misses=0
    normal_ready=elite_ready=active_until=0
    actions=int(hours*3600/interval)
    for i in range(actions):
        now=i*interval
        counts['products']+=1+int(fortune//100)+(rng.random()<fortune%100/100)
        rare_misses+=1
        if rare_misses>=150 or rng.random()<.02*(1+armor['rare']/100): counts['rare']+=1;rare_misses=0
        if now<active_until or armor['event_tier']==0:continue
        if armor['event_tier']>=2 and now>=elite_ready:
            elite_misses+=1
            if elite_misses>=3000 or rng.random()<.001*(1+armor['event']/100):
                counts['elite']+=1;elite_misses=0;elite_ready=now+300;active_until=now+90
                core_misses+=1
                if core_misses>=20 or rng.random()<.1*(1+armor['rare']/100):counts['elite_core']+=1;core_misses=0
                continue
        if now>=normal_ready:
            normal_misses+=1
            if normal_misses>=1000 or rng.random()<.003*(1+armor['event']/100):counts['normal']+=1;normal_misses=0;normal_ready=now+90;active_until=now+20
    return {k:round(v/hours,2) for k,v in counts.items()},round(1+fortune/100,2)

lines=['# 主世界职业平衡模型','',
    '此报告由 `python tools/simulate_professions.py` 生成。经验使用仓库实际 60 级数组与装备配置；装备达到使用等级后立即换装，暂不计算制作和个人门槛等待。',
    '农业基础经验 5,000/小时，挖矿 6,000/小时。经验分支升级更快，但毕业仍需完成收藏、配方和事件挑战。','',
    '| 职业 | 路线 | 55级采集小时 | 60级采集小时 | 55级普通产物倍数 | 稀有碎片/小时 | 普通事件/小时 | 精英事件/小时 | 完整精英核心/小时 |',
    '|---|---|---:|---:|---:|---:|---:|---:|---:|']
names={'main':'主线','experience':'经验','yield':'产量','balanced':'综合','rare':'稀有','event':'事件'}
for job in ['farming','mining']:
    for route in ['main','experience','yield','balanced','rare','event']:
        result,multiplier=stationary(job,route)
        lines.append(f"| {'农业' if job=='farming' else '挖矿'} | {names[route]} | {leveling(job,route,55):.2f} | {leveling(job,route,60):.2f} | {multiplier} | {result['rare']} | {result['normal']} | {result['elite']} | {result['elite_core']} |")
lines+=['','事件模型为固定 55 级、1,000 小时抽样，种子 20261001；农业每小时 6,250 次有效采集，挖矿 3,750 次。每次普通事件占用 20 秒、精英占用 90 秒，期间停止新事件抽取；冷却从召唤时开始，计数与实际实现一致。采集可在战斗期间继续进行；实际服务器通常会更慢。',
    '普通产物倍数含 55 级技能幸运 110、装备与 T6 工具，不含收藏幸运、原版时运及其他基础属性；基础主产物每次一份。稀有碎片含 150 次保底；完整精英核心含 20 次击败保底，另有每次精英必掉的核心碎片。石材不包含在此模型中。','',
    '## 制作与挑战瓶颈','',
    '- T5 每件消耗 A/B 各 3 个极致材料时，单种原料为 19,683；四件套加工具为 98,415，武器另计。矿业 T5 的 B 为精炼钻石，单件 243 钻石。',
    '- T6 农业每件需要南瓜、可可各 19,683；四件套、工具和终阶武器合计各 118,098。T6 挖矿六件合计粗铁 118,098、钻石 13,122。配方门槛同时要求个人钻石收藏 50,000。',
    '- 进入 T5 前需普通事件击败 50 次；进入 T6 前需精英事件击败 30 次。装备、工具和武器的 T5 每件需 18 个普通核心，T6 每件需 2 个精英核心。',
    '- T6 六件需 12 个完整精英核心。30 次精英保底掉 30 碎片，只能压成 3 核心，其余必须继续挑战、依赖额外核心掉落或交易。',
    '- 经验专精牺牲产量、防护；产量专精减少专门经验加成；稀有分支不提高事件概率，事件分支不提高完整核心概率。',
    '- 高阶副套装只消耗同分支低阶物品，主线、其他副线的个人挑战与收藏条件不会隐式继承。',
    '- 150–200 小时是主线目标，并非已测得毕业保证。不同作物和矿物的基础经验、农场设计、探索与市场交易会改变最终节奏。','',
    '## 测试服待验收','',
    '需要 Paper 26.3 测试服与可用的 state 数据库。当前报告不代表真实玩家实测；需按 docs/professions.md 的清单走通权限保护、合成交互和职业战斗，再用服务器采集日志校准基础经验速率。']
target=ROOT/'docs/profession-balance.md';target.parent.mkdir(exist_ok=True);target.write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('\n'.join(lines[5:11]))
