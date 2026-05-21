# -*- coding: utf-8 -*-
"""정시/수시 추출본 전수검증·정제 → 앱 적재용 CSV 생성 + 품질 리포트.
   - 절대범위 + 대학별 분포 이상치 제거, 전형명 접두어 정리, 노이즈 학과 제거.
출력: kcue_domestic_cutoffs.csv(정시), kcue_susi_cutoffs.csv(수시), _quality_report.txt"""
import csv, io, re, statistics, collections
from ratio_config import ratios   # 계열 반영비율 추정치(공유 단일 출처)

PREFIX=['교과우수','지역균형','고른기회','기회균등','일반전형','일반학생','학생부교과','학생부종합','논술전형','정시','일반','나군','가군','다군','추가모집','기초생활수급자등전형','특성화고','농어촌학생전형','국가보훈대상자전형','장애인등대상자전형']
NOISE=['합계','소계','평균','구분','모집','인원','총점','반영','대학별','순위','경쟁','충원','지원자','합격','비고','참고']
# 대학별 분포 이상치(±dev) 필터 면제 카테고리. 대형학원은 의·치·한의·약·수의를 최상위 별도티어로,
# 예체능은 학교별 편차가 커 같은 척도로 비교하지 않는다. 절대범위·이름노이즈 필터는 이들에도 그대로 적용됨.
EXEMPT_DEV=['의예','의학','치의','치예','한의','약학','약과','수의','미술','음악','무용','체육','예술','디자인','연극','성악','작곡','회화','조소','국악','실용음악','뷰티','애니','패션','만화','게임','영상','공연','스포츠','태권','초등교육','유아교육','신학']

def norm_gun(g):
    """모집군 정규화: '가군 일반전형'·'수능(...)(나군)'·'나 군'·'가' 등 → 가군/나군/다군. 불명확하면 ''."""
    g=(g or '').strip()
    m=re.search(r'([가나다])\s*군', g)
    if m: return m.group(1)+'군'
    if g in ('가','나','다'): return g+'군'
    return ''

def clean_dept(d):
    d=d.strip()
    changed=True
    while changed:
        changed=False
        for p in PREFIX:
            if d.startswith(p) and len(d)>len(p):
                d=d[len(p):].strip(); changed=True
    return d

def valid_dept(d):
    if not d or len(d)>25: return False
    if not re.search(r'[가-힣]',d): return False
    if any(k in d for k in NOISE): return False
    if not re.search(r'(학과|학부|학|과|전공|계열|대학|교육과)$', d):
        # 학과류 접미 없으면 노이즈 가능성 — 단, 짧은 한글명은 허용
        if len(d)<3: return False
    return True

rep=io.open('_quality_report.txt','w',encoding='utf-8')
def log(s): rep.write(s+'\n')

def process(infile, value_key, lo, hi, dev, n_min, extra=None):
    rows=list(csv.DictReader(io.open(infile,encoding='utf-8-sig')))
    # 수동 수집 보충본(입학처 등) 병합 — 자동추출(어디가)과 분리 보관해 재추출에도 보존.
    import os
    for ef in (extra or []):
        if os.path.exists(ef):
            rows+=list(csv.DictReader(io.open(ef,encoding='utf-8-sig')))
    for r in rows:
        r['dept2']=clean_dept(r['departmentName'])
        try: r['v']=float(r[value_key])
        except (ValueError, TypeError, KeyError): r['v']=None
    drop_abs=[r for r in rows if r['v'] is None or not (lo<=r['v']<=hi)]
    keep=[r for r in rows if r['v'] is not None and lo<=r['v']<=hi]
    drop_name=[r for r in keep if not valid_dept(r['dept2'])]
    keep=[r for r in keep if valid_dept(r['dept2'])]
    # 대학별 분포 이상치
    by=collections.defaultdict(list)
    for r in keep: by[r['universityName']].append(r)
    final=[]; drop_dev=[]
    for uni,rs in by.items():
        if len(rs)>=n_min:
            med=statistics.median(r['v'] for r in rs)
            for r in rs:
                exempt=any(k in r['dept2'] for k in EXEMPT_DEV)
                (final if exempt or abs(r['v']-med)<=dev else drop_dev).append(r)
        else: final+=rs
    log(f'\n===== {infile} =====')
    log(f'입력 {len(rows)} → 적재 {len(final)} | 제외: 범위{len(drop_abs)} 이름{len(drop_name)} 대학별이상치{len(drop_dev)}')
    log('  [이상치 샘플(대학별 분포 벗어남)]')
    for r in drop_dev[:15]: log(f'    {r["universityName"]} {r["dept2"]} = {r["v"]}')
    log('  [이름 노이즈 샘플]')
    for r in drop_name[:10]: log(f'    {r["universityName"]} | 원본:{r["departmentName"]!r}')
    return final

# 정시: 백분위 20~100, 대학별 중앙값±20pp, 표본4+
J=process('integrated_jeongsi_results_2025.csv','percentile70',20,100,20,4,
          extra=['manual_supplement_jeongsi.csv'])   # 입학처 수동수집(한양대 등) 병합
with io.open('kcue_domestic_cutoffs.csv','w',encoding='utf-8',newline='') as f:
    w=csv.writer(f); w.writerow(['universityName','departmentName','ratioKo','ratioMath','ratioEn','ratioTg','cutoffPercentile70',
                                 'recruitCount','competitionRate','additionalRank','koreanCut70','mathCut70','tamguCut70',
                                 'region','admissionGroup','dataSource'])
    JEONGSI_SRC='2025 어디가 공식 정시 입결(70%컷 백분위) · 반영비율 계열 추정'
    seen=set()
    for r in sorted(J,key=lambda x:(x['universityName'],x['dept2'],-x['v'])):
        k=(r['universityName'],r['dept2'])
        if k in seen: continue
        seen.add(k)
        rk,rm,re_,rt=ratios(r['dept2'])
        w.writerow([r['universityName'],r['dept2'],rk,rm,re_,rt,r['v'],
                    r.get('recruitCount',''),r.get('competitionRate',''),r.get('additionalRank',''),
                    r.get('koreanCut70',''),r.get('mathCut70',''),r.get('tamguCut70',''),
                    r.get('region',''),norm_gun(r.get('admissionGroup','')),
                    r.get('dataSource') or JEONGSI_SRC])

# 수시: 등급 1~9, 대학별 중앙값±2.5등급, 표본6+
S=process('integrated_susi_results_2025.csv','grade70',1.0,9.0,2.5,6)
with io.open('kcue_susi_cutoffs.csv','w',encoding='utf-8',newline='') as f:
    w=csv.writer(f); w.writerow(['universityName','admissionType','departmentName','grade50','grade70'])
    seen=set()
    for r in sorted(S,key=lambda x:(x['universityName'],x.get('admissionType',''),x['dept2'])):
        k=(r['universityName'],r.get('admissionType',''),r['dept2'])
        if k in seen: continue
        seen.add(k)
        w.writerow([r['universityName'],r.get('admissionType',''),r['dept2'],r.get('grade50',''),r['v']])
rep.close()
print(f'정시 {len(set((r["universityName"],r["dept2"]) for r in J))} / 수시 {len(S)} 정제 완료. 리포트: _quality_report.txt')
