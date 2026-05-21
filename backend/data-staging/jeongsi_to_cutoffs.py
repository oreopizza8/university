# -*- coding: utf-8 -*-
"""integrated_jeongsi_results_2025.csv → kcue_domestic_cutoffs.csv (앱 적재용).
   계열 반영비율 부여 + 이상치 검증(오추출 의심값 제외)."""
import csv, io, collections, statistics
NAT=['공학','공과','컴퓨터','기계','전자','전기','화학','화공','물리','수학','의과','의예','약','간호','생명','소재','건축','통계','데이터','인공지능','반도체','에너지','바이오','정보','소프트','보건','식품','지구','환경','수의','치','보안','모빌리티','항공','해양','농','산림','과학']
HUM=['국어','국문','영어','영문','사학','철학','경영','경제','사회','정치','행정','교육','심리','미디어','법','문헌','문예','문화','관광','복지','신학','상담','어문','문학','예술','디자인','음악','미술','체육','무용','연극','글로벌','국제','자유전공','인문','경찰','세무','회계','무역','금융']
def ratios(d):
    if any(k in d for k in NAT): return (0.20,0.35,0.15,0.30)
    if any(k in d for k in HUM): return (0.30,0.25,0.20,0.25)
    return (0.25,0.30,0.20,0.25)

rows=list(csv.DictReader(io.open('integrated_jeongsi_results_2025.csv',encoding='utf-8-sig')))
for r in rows: r['p']=float(r['percentile70'])

# 1) 절대 범위: 4년제 정시 70%컷 백분위가 20 미만이면 오추출로 간주
kept=[r for r in rows if 20.0<=r['p']<=100.0]
dropped_abs=len(rows)-len(kept)

# 2) 대학별 이상치: 표본 5+ 대학에서 중앙값-25pp 미만은 오추출 의심 제외
by_uni=collections.defaultdict(list)
for r in kept: by_uni[r['universityName']].append(r)
final=[]; dropped_rel=0
for uni,rs in by_uni.items():
    if len(rs)>=5:
        med=statistics.median(r['p'] for r in rs)
        for r in rs:
            if r['p'] < med-25: dropped_rel+=1
            else: final.append(r)
    else:
        final+=rs

with io.open('kcue_domestic_cutoffs.csv','w',encoding='utf-8',newline='') as f:
    w=csv.writer(f); w.writerow(['universityName','departmentName','ratioKo','ratioMath','ratioEn','ratioTg','cutoffPercentile70'])
    for r in final:
        rk,rm,re_,rt=ratios(r['departmentName'])
        w.writerow([r['universityName'],r['departmentName'],rk,rm,re_,rt,r['p']])
print(f'적재 {len(final)}행 / 이상치 제외: 범위{dropped_abs} + 대학별{dropped_rel}')
