# -*- coding: utf-8 -*-
"""
대교협 '어디가' 2025 지역별 대입 전형결과 PDF → 정시/수시 통합 CSV.
- 표(extract_tables) 기반 + 병합셀 forward-fill + 이중헤더 처리 + 노이즈 제거.
- 정시 풍부 스키마: 모집인원·경쟁률·충원합격순위(추합)·국/수/탐 70%컷 + 수능평균70%컷(범위검색 메인 인덱스) + 계열 추정 반영비율.
사용: python extract_adiga_2025.py *.pdf
"""
import pdfplumber, re, sys, io, csv, glob
from ratio_config import ratios   # 계열 반영비율 추정치(공유 단일 출처)

UNIV_RE = re.compile(r'^[가-힣A-Za-z·\(\)]{2,15}대학교$')
NUM_RE  = re.compile(r'^-?\d+(?:\.\d+)?$')
HEADER_KW = ['모집단위','모집인원','경쟁률','충원','순위','cut','백분위','등급','전형','구분',
             '교과','지원자','합격','총점','평균','수능','환산','대학별','반영','지원']
NOISE_KW = ['합계','소계','구분','모집단위','수능 성적','산출','안내','기준','반영','참고','비고',
            '대학별 산출','직접','비교','지원자','합격자','불합격','※','평균','총점','환산점수']
# 표지(간지) 대학명 복원용: 지역 머리말·일반어 제거 토큰. '부산'(머리말)은 빼되 '부산가톨릭'(이름 일부)은 보존됨.
REGION_TOK = {'서울','부산','대구','인천','광주','대전','울산','세종','경기','강원','충북','충남','전북','전남','경북','경남','제주'}
COVER_DROP = {'대입','수시모집','정시모집','입시결과','전형결과','수시','정시','모집','결과','입시'}

def clean(c):
    if c is None: return ''
    return str(c).replace('\n',' ').replace('\r',' ').strip()

def norm(c):
    """헤더 키워드 매칭용: 공백·슬래시 제거."""
    return clean(c).replace(' ','').replace('/','')

def is_num(s):
    s=clean(s); return bool(NUM_RE.match(s))

def to_int(s):
    s=clean(s)
    if s in ('','-','–','‐'): return ''
    m=re.search(r'\d+', s); return m.group(0) if m else ''

def to_float(s):
    m=re.search(r'-?\d+(?:\.\d+)?', clean(s)); return m.group(0) if m else ''

def to_rate(s):
    """경쟁률: '6.8:1' → 6.8, '4.13' → 4.13."""
    return to_float(clean(s).split(':')[0])

def region_of(path):
    m=re.search(r'\(([^)_]+)', path)
    return m.group(1) if m else ''

def big_univ(page):
    try: words=page.extract_words(extra_attrs=['size'])
    except: return None
    big=[w for w in words if w.get('size',0)>=50]
    if not big: return None
    # 1) 단일단어 ○○대학교 (기존 인식 보존 — 기존 추출 대학 불변)
    for w in sorted(big, key=lambda x:-x['size']):
        if UNIV_RE.match(w['text']): return w['text']
    # 2) 폴백: 표지 큰글씨를 읽기순서로 결합 → 지역 머리말·일반어 제거 후 ○○대학교 복원.
    #    예: '부산'+'국립부경'+'대학교' → '국립부경대학교', '부산'+'부산가톨릭'+'대학교' → '부산가톨릭대학교'
    ordered=sorted(big, key=lambda x:(round(x.get('top',0)), x.get('x0',0)))
    toks=[w['text'] for w in ordered
          if w['text'] not in REGION_TOK and w['text'] not in COVER_DROP and not re.fullmatch(r'20\d\d.*', w['text'])]
    m=re.search(r'[가-힣A-Za-z·]{2,20}대학교', ''.join(toks))
    return m.group(0) if m else None

def col_headers(table):
    """헤더 행들을 식별하고 컬럼별 헤더 문자열을 합쳐 반환. (headers, n_header_rows)"""
    ncol=max(len(r) for r in table)
    headers=['']*ncol
    nh=0
    for r in table:
        cells=[clean(c) for c in r]+['']*(ncol-len(r))
        joined=' '.join(cells)
        nums=sum(1 for c in cells if is_num(c))
        has_kw=any(k in joined for k in HEADER_KW)
        kor=any(re.search(r'[가-힣]{2,}', c) and not any(k in c for k in ['모집단위','전형','구분']) for c in cells)
        if has_kw and nums<=1 and not (kor and nums>=2):
            for i,c in enumerate(cells):
                if c: headers[i]=(headers[i]+' '+c).strip()
            nh+=1
        else:
            break
    return headers, nh

def find_col(headers, *kws, exclude=()):
    for i,h in enumerate(headers):
        hn=norm(h)
        if any(norm(k) in hn for k in kws) and not any(norm(e) in hn for e in exclude):
            return i
    return -1

def subject_cols_70(table):
    """Format B(과목별 백분위) 70% 그룹의 국/수/탐1/탐2 컬럼 인덱스. 50%/70% 두 그룹 중 마지막(=70%)을 사용."""
    for row in table[:7]:
        cells=[norm(c) for c in row]
        if cells.count('국')>=1 and cells.count('수')>=1 and ('영' in cells or '한' in cells):
            def last(*labels):
                idx=[i for i,c in enumerate(cells) if c in labels]
                return idx[-1] if idx else None
            ko=last('국'); ma=last('수'); t1=last('탐1','탐'); t2=last('탐2')
            if ko is not None and ma is not None:
                return {'ko':ko,'ma':ma,'t1':t1,'t2':t2}
    return None

def cell_pct(cells, idx):
    """과목 백분위(10~100) 안전 추출. 영어·한국사 등급칸/빈칸은 None."""
    if idx is None or idx>=len(cells): return None
    v=cells[idx]
    if not is_num(v): return None
    f=float(v)
    return f if 10<=f<=100 else None

def parse_pdf(path, jeongsi, susi):
    region=region_of(path); univ=None
    with pdfplumber.open(path) as pdf:
        for p in pdf.pages:
            u=big_univ(p)
            if u: univ=u
            if univ is None: continue
            for table in p.extract_tables():
                if not table or len(table)<2: continue
                ncol=max(len(r) for r in table)
                headers,nh=col_headers(table)
                hjoined=' '.join(headers)
                dept_col=find_col(headers,'모집단위')
                if dept_col<0: dept_col=0
                gun_col=find_col(headers,'구분')
                jeon_col=find_col(headers,'전형')
                baeg_col=find_col(headers,'백분위')
                cut70_col=find_col(headers,'70%','70%cut', exclude=('백분위',))
                cut50_col=find_col(headers,'50%','50%cut', exclude=('백분위',))
                # 정시 부가 컬럼(모집인원/경쟁률/추합)
                cnt_col=find_col(headers,'A+B')
                if cnt_col<0: cnt_col=find_col(headers,'모집인원')
                comp_col=find_col(headers,'경쟁률')
                chung_col=find_col(headers,'충원')
                subj=subject_cols_70(table)   # Format B
                is_jeongsi = subj is not None or baeg_col>=0 or ('수능' in hjoined and '백분위' in hjoined)
                ff={}
                for r in table[nh:]:
                    cells=[clean(c) for c in r]+['']*(ncol-len(r))
                    for ci in (gun_col,dept_col,jeon_col):
                        if ci is not None and ci>=0:
                            if cells[ci]: ff[ci]=cells[ci]
                            elif ci in ff: cells[ci]=ff[ci]
                    dept=(cells[dept_col] if dept_col>=0 else '').strip()
                    if not dept or not re.search(r'[가-힣]',dept) or len(dept)>30: continue
                    if any(k in dept for k in NOISE_KW): continue
                    gun=cells[gun_col] if (gun_col is not None and gun_col>=0) else ''
                    cnt=to_int(cells[cnt_col]) if (cnt_col>=0 and cnt_col<len(cells)) else ''
                    comp=to_rate(cells[comp_col]) if (comp_col>=0 and comp_col<len(cells)) else ''
                    chung=to_int(cells[chung_col]) if (chung_col>=0 and chung_col<len(cells)) else ''
                    if is_jeongsi:
                        ko=ma=tg=''
                        if subj is not None:                       # Format B: 과목별 백분위
                            pko=cell_pct(cells,subj['ko']); pma=cell_pct(cells,subj['ma'])
                            pt=[v for v in (cell_pct(cells,subj['t1']),cell_pct(cells,subj['t2'])) if v is not None]
                            comp_vals=[v for v in (pko,pma)+tuple(pt) if v is not None]
                            if len(comp_vals)<2: continue
                            avg=round(sum(comp_vals)/len(comp_vals),1)
                            ko=pko if pko is not None else ''
                            ma=pma if pma is not None else ''
                            tg=round(sum(pt)/len(pt),1) if pt else ''
                        elif baeg_col>=0:                          # Format A: 평균 백분위 단일 칸
                            v=cells[baeg_col]
                            if not is_num(v): continue
                            avg=float(v)
                            if not (0<=avg<=100): continue
                        else: continue
                        rk,rm,re_,rt=ratios(dept)
                        jeongsi.append([region,univ,gun,dept,cnt,comp,chung,ko,ma,tg,avg,rk,rm,re_,rt])
                    elif cut70_col>=0:                             # 수시 내신 등급
                        v70=cells[cut70_col]; v50=cells[cut50_col] if cut50_col>=0 else ''
                        if not is_num(v70): continue
                        g70=float(v70)
                        if not (1.0<=g70<=9.0): continue
                        g50=float(v50) if is_num(v50) and 1.0<=float(v50)<=9.0 else ''
                        jeon=cells[jeon_col] if (jeon_col is not None and jeon_col>=0) else ''
                        susi.append([region,univ,jeon,dept,g50,g70])

def main(files):
    jeongsi=[]; susi=[]
    for f in files: parse_pdf(f, jeongsi, susi)
    def dedup_j(rows):
        seen={}
        for r in rows:
            k=(r[1],r[3])              # 대학,모집단위
            if k not in seen or r[10]>seen[k][10]: seen[k]=r   # 더 높은 평균백분위(보수적) 유지
        return list(seen.values())
    def dedup_s(rows):
        seen={}
        for r in rows:
            k=(r[1],r[2],r[3])
            if k not in seen: seen[k]=r
        return list(seen.values())
    J=sorted(dedup_j(jeongsi), key=lambda x:(x[1],x[3]))
    S=sorted(dedup_s(susi), key=lambda x:(x[1],x[2],x[3]))
    with io.open('integrated_jeongsi_results_2025.csv','w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f)
        w.writerow(['region','universityName','admissionGroup','departmentName',
                    'recruitCount','competitionRate','additionalRank',
                    'koreanCut70','mathCut70','tamguCut70','percentile70',
                    'ratioKor','ratioMath','ratioEng','ratioTam'])
        w.writerows(J)
    with io.open('integrated_susi_results_2025.csv','w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f); w.writerow(['region','universityName','admissionType','departmentName','grade50','grade70'])
        w.writerows(S)
    print('JEONGSI rows=%d univ=%d'%(len(J),len(set(r[1] for r in J))))
    print('SUSI    rows=%d univ=%d'%(len(S),len(set(r[1] for r in S))))
    return J,S

if __name__=='__main__':
    files=[]
    for a in sys.argv[1:]: files+=glob.glob(a)
    if not files: files=glob.glob('*.pdf')
    main(files)
