"""Generate the reviewable profession catalog; runtime reads the generated YAML only."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CROPS = ['WHEAT', 'CARROT', 'POTATO', 'BEETROOT', 'PUMPKIN', 'SUGAR_CANE', 'COCOA_BEANS']
items = {}
sets = {}

def vanilla(material):
    return 'vanilla:' + material

def compressed(material, tier):
    return material.lower() + '_compressed_' + str(tier)

def special(job, kind):
    return job + '_' + kind

def requirements(job, branch, tier):
    r = {'collections': {}, 'normal': 0, 'elite': 0}
    if branch == 'main':
        if job == 'farming':
            if tier == 2:
                r['collections'] = dict(WHEAT=250, CARROT=250)
            elif tier >= 3:
                r.update(diverse_count={3: 2, 4: 3, 5: 3, 6: 4}[tier],
                         diverse_amount={3: 2500, 4: 10000, 5: 100000, 6: 250000}[tier])
        else:
            r['collections'] = [{}, dict(COAL=250, RAW_IRON=250), dict(RAW_IRON=2500, RAW_COPPER=1000),
                dict(RAW_IRON=10000, REDSTONE=5000), dict(RAW_IRON=100000, REDSTONE=50000, DIAMOND=5000),
                dict(RAW_IRON=250000, REDSTONE=100000, LAPIS_LAZULI=100000, DIAMOND=50000)][tier-1]
        if tier == 5: r['normal'] = 50
        if tier == 6: r['elite'] = 30
    else:
        high = tier == 2
        if job == 'farming':
            r['collections'] = {
                'yield': dict(WHEAT=250000 if high else 5000, CARROT=250000 if high else 5000),
                'experience': dict(WHEAT=100000 if high else 2500, POTATO=100000 if high else 2500),
                'balanced': {},
                'rare': dict(PUMPKIN=250000 if high else 10000, COCOA_BEANS=250000 if high else 10000),
                'event': dict(PUMPKIN=250000 if high else 10000, COCOA_BEANS=100000 if high else 5000)}[branch]
            if branch == 'balanced': r.update(diverse_count=4 if high else 3, diverse_amount=100000 if high else 5000)
        else:
            r['collections'] = {
                'yield': dict(RAW_IRON=250000 if high else 5000, RAW_COPPER=100000 if high else 5000),
                'experience': dict(COAL=100000 if high else 2500, RAW_IRON=100000 if high else 2500),
                'balanced': dict(RAW_IRON=250000 if high else 5000, RAW_COPPER=100000 if high else 2500, REDSTONE=100000 if high else 2500),
                'rare': dict(LAPIS_LAZULI=250000 if high else 10000, DIAMOND=50000 if high else 2500),
                'event': dict(REDSTONE=250000 if high else 10000, LAPIS_LAZULI=100000 if high else 5000)}[branch]
        if branch == 'event': r.update(normal=100 if high else 10, elite=10 if high else 0)
    return r

def materials(job, tier):
    pairs = ([('WHEAT','CARROT',0,0), ('WHEAT','POTATO',1,1), ('POTATO','SUGAR_CANE',2,2),
        ('PUMPKIN','COCOA_BEANS',3,3), ('WHEAT','SUGAR_CANE',4,4), ('PUMPKIN','COCOA_BEANS',4,4)]
        if job == 'farming' else [('COAL','RAW_IRON',0,0), ('RAW_IRON','RAW_COPPER',1,1),
        ('RAW_IRON','REDSTONE',2,2), ('RAW_IRON','LAPIS_LAZULI',3,2), ('RAW_IRON','DIAMOND',4,2), ('RAW_IRON','DIAMOND',4,3)])
    a,b,ta,tb = pairs[tier-1]
    return [compressed(a,ta) if ta else vanilla(a), compressed(b,tb) if tb else vanilla(b)]

parts = {'helmet': ('LEATHER_HELMET','头盔'), 'chestplate': ('LEATHER_CHESTPLATE','胸甲'),
         'leggings': ('LEATHER_LEGGINGS','护腿'), 'boots': ('LEATHER_BOOTS','靴子')}
branches = ['yield','experience','balanced','rare','event']
for material in ['WHEAT','CARROT','POTATO','SUGAR_CANE','PUMPKIN','COCOA_BEANS','COAL','RAW_IRON','RAW_COPPER','REDSTONE','LAPIS_LAZULI','DIAMOND']:
    for tier, label in enumerate(['压缩','精炼','致密','极致'],1):
        items[compressed(material,tier)] = dict(name=label+{'WHEAT':'小麦','CARROT':'胡萝卜','POTATO':'土豆','SUGAR_CANE':'甘蔗','PUMPKIN':'南瓜','COCOA_BEANS':'可可豆','COAL':'煤炭','RAW_IRON':'粗铁','RAW_COPPER':'粗铜','REDSTONE':'红石','LAPIS_LAZULI':'青金石','DIAMOND':'钻石'}[material],
            material='PAPER', kind='material', compression=vanilla(material) if tier==1 else compressed(material,tier-1), raw_value=9**tier)

for job in ['farming','mining']:
    farm = job == 'farming'
    nucleus = '穗' if farm else '矿'
    for kind,label,base in [('rare_fragment', nucleus+'晶碎片', None), ('rare_bundle','精炼'+nucleus+'晶碎片','rare_fragment'),
                            ('normal_core','活性'+nucleus+'核',None), ('normal_bundle','精炼活性'+nucleus+'核','normal_core'),
                            ('elite_fragment',('丰收' if farm else '星核')+'核心碎片',None),
                            ('elite_core',('丰收' if farm else '星核')+'核心','elite_fragment')]:
        items[special(job,kind)] = dict(name=label, material='PRISMARINE_SHARD' if 'fragment' in kind else 'AMETHYST_SHARD', kind='material', job=job)
        if base: items[special(job,kind)]['compression'] = special(job,base)
    names = ['田野','丰苗','沃土','丰穰','灵穗','丰收圣约'] if farm else ['勘矿','铁脉','深岩','晶脉','地心','星核']
    alternative = [['聚穗','金穗'],['农学研习','农学贤者'],['田园匠心','百谷协奏'],['寻珍','天赐穗晶'],['巡田','镇田守望']] if farm else [['富矿','聚晶'],['矿研','地质贤者'],['探岩','万矿协奏'],['淘珍','星痕寻宝'],['唤矿','地脉共鸣']]
    for branch in ['main']+branches:
        for tier in range(1,7 if branch=='main' else 3):
            main = branch == 'main'; high = tier == 2; bi = branches.index(branch) if not main else -1
            name = names[tier-1] if main else alternative[bi][tier-1]
            level = [0,8,18,30,42,55][tier-1] if main else [[22,48],[18,45],[25,50],[30,50],[30,48]][bi][tier-1]
            fortune = [20,40,80,120,180,240][tier-1] if main else [[130,320],[20,60],[80,180],[40,90],[30,80]][bi][tier-1]
            xp = 0 if main else [[0,0],[15,25],[8,12],[0,0],[0,0]][bi][tier-1]
            rare = [35,75][tier-1] if branch=='rare' else 0
            event = [50,100][tier-1] if branch=='event' else 0
            hp = ([40,60,80,120,160,200] if farm else [40,60,80,100,120,160])[tier-1] if main else ([80,120] if farm else [60,100])[tier-1]
            defense = ([8,12,20,28,40,60] if farm else [20,40,60,100,160,240])[tier-1] if main else ([16,28] if farm else [80,140])[tier-1]
            event_tier = (0 if tier<=2 else 1 if tier<=4 else 2) if main else tier
            reduction = {4:10,5:15,6:20}.get(tier,0) if main else [5,15][tier-1] if branch=='event' else 0
            setid = f'{job}_{branch}_{tier}'
            color = ((0x53782F if farm else 0x53677C) + (bi+1)*0x080403 + tier*0x080807) & 0xFFFFFF
            req = requirements(job,branch,tier)
            sets[setid] = dict(name=name,job=job,branch=branch,tier=tier,level=level,fortune=fortune,xp=xp,rare=rare,event=event,event_tier=event_tier,reduction=reduction,health=hp,defense=defense,requirements=req)
            template = tier if main else [[3,5],[3,5],[4,5],[4,6],[4,6]][bi][tier-1]
            a,b = materials(job,template)
            n = ([vanilla('WHEAT_SEEDS' if farm else 'RAW_COPPER'),special(job,'rare_fragment'),special(job,'rare_bundle'),special(job,'normal_core'),special(job,'normal_bundle'),special(job,'elite_core')][tier-1]
                if main else special(job, [['rare_bundle','normal_bundle'],['rare_fragment','normal_bundle'],['rare_bundle','normal_bundle'],['normal_core','elite_core'],['normal_core','elite_core']][bi][tier-1]))
            for part,(material,suffix) in parts.items():
                identity = setid+'_'+part
                p = vanilla(material) if tier==1 else f'{job}_{branch}_{tier-1}_{part}'
                items[identity] = dict(name=name+suffix,material=material,kind='armor',job=job,set=setid,part=part,level=level,color=color,recipe=dict(a=a,b=b,n=n,p=p))
            if main:
                identity = f'{job}_tool_{tier}'
                items[identity] = dict(name=name+('锄' if farm else '镐'), material=('IRON_' if tier<=2 else 'DIAMOND_' if tier<=4 else 'NETHERITE_')+('HOE' if farm else 'PICKAXE'), kind='tool',job=job,level=level,
                    fortune=[10,20,40,60,90,130][tier-1],xp={4:5,5:10,6:15}.get(tier,0),replant=farm and tier>=3,efficiency=0 if farm else min(5,tier),
                    gate=setid,recipe=dict(a=a,b=b,n=n,p=vanilla('IRON_HOE' if farm else 'IRON_PICKAXE') if tier==1 else f'{job}_tool_{tier-1}'))
                if tier in [2,4,6]:
                    wt=tier//2
                    items[f'{job}_weapon_{wt}'] = dict(name=(['驱虫短刃','护田镰刃','丰收裁决'] if farm else ['碎岩短剑','裂晶战锤','地心裁决'])[wt-1],material=['IRON_SWORD','DIAMOND_SWORD','NETHERITE_SWORD'][wt-1],
                        kind='weapon',job=job,level=level,damage=[35,70,110][wt-1],strength=[10,20,35][wt-1],mob_bonus=[50,75,100][wt-1],gate=setid,
                        recipe=dict(a=a,b=b,n=n,p=vanilla('IRON_SWORD') if wt==1 else f'{job}_weapon_{wt-1}'))

assert len(sets)==32 and sum(x['kind']=='armor' for x in items.values())==128
# JSON is a YAML 1.2 subset accepted by Bukkit's YAML loader, with exact decimal values.
target=ROOT/'underground_item/src/main/resources/professions.yml'
target.write_text(json.dumps(dict(schema_version=1,sets=sets,items=items),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'{len(sets)} sets; {len(items)} unique items; generated {target.name}')
