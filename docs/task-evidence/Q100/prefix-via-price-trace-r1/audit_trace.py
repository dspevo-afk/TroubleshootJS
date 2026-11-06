"""Finite, read-only audit of the two accepted canonical routing traces."""
import hashlib,json,math,os,re,stat,sys
from collections import Counter
from pathlib import Path

PLAN='3b50a694a4eb78bbfc2b88a0c96cc5f3dfc2b640eef622704888ab8d05f6fc48'
CONTRACT='7dca9055cd8e0f5271598d5dc2dcb502147e306967e4668d275958cc96e89337'
QUAL='b4bb35cfbe2f6452f416c7cd9b7e283490a4fc8b42820817154a9c2e55a1b57e'
INPUTS=(('BASE',377135,'108b66d6ff45111e6dee2be0e912af4089cce0a6463a2e5c6358d42096c2bf4f'),
        ('RAMP',384463,'afd36d53cc1ae58cbc3f8db99b37b23e55cbbc16573922d3e3156bdc4f55a276'))
MAX_BYTES=8388608
HEX=re.compile('[0-9a-f]{64}\\Z')
class Rejected(ValueError):pass
def need(v,m):
    if not v:raise Rejected(m)
def sha(b):return hashlib.sha256(b).hexdigest()
def pairs(rows):
    d={}
    for k,v in rows:
        need(k not in d,'DUPLICATE_JSON_KEY');d[k]=v
    return d
def parse(raw):
    def integer(s):need(s!='-0','NEGATIVE_ZERO');return int(s)
    def constant(_):raise Rejected('NONFINITE')
    need(type(raw) is bytes and not raw.startswith(b'\xef\xbb\xbf'),'JSON_BOM_OR_TYPE')
    try:d=json.loads(raw.decode('utf-8','strict'),object_pairs_hook=pairs,parse_int=integer,parse_constant=constant)
    except (ValueError,UnicodeError) as e:raise Rejected('JSON_INVALID:'+str(e)) from e
    def check(v):
        if type(v) is str:need(not any(0xd800<=ord(c)<=0xdfff for c in v),'SURROGATE')
        elif type(v) is float:need(math.isfinite(v),'NONFINITE')
        elif type(v) is list:
            for x in v:check(x)
        elif type(v) is dict:
            for k,x in v.items():check(k);check(x)
    check(d);return d
def parse_records(raw):
    need(type(raw) is bytes and 0<len(raw)<=MAX_BYTES,'RECEIPT_BYTES')
    need(raw.endswith(b'\n') and b'\r' not in raw,'RECEIPT_EOF_OR_CR')
    lines=raw.split(b'\n')[:-1]
    need(all(0<len(x)+1<=65536 for x in lines),'RECEIPT_LINE_BYTES')
    rows=[parse(x) for x in lines];need(all(type(x) is dict for x in rows),'RECORD_OBJECT')
    return rows
def no_reparse(path):
    p=Path(os.path.abspath(path))
    for q in (p,*p.parents):
        s=q.lstat();need(not stat.S_ISLNK(s.st_mode) and not getattr(s,'st_file_attributes',0)&0x400,'REPARSE')
    return p
def read(path,maximum):
    p=no_reparse(path);a=p.stat();need(stat.S_ISREG(a.st_mode) and a.st_size<=maximum,'FILE_SIZE_OR_KIND')
    def identity(s):return s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_nlink
    with p.open('rb') as f:
        before=os.fstat(f.fileno());b=f.read(maximum+1);after=os.fstat(f.fileno())
    z=p.stat();need(identity(a)==identity(before)==identity(after)==identity(z)
        and a.st_ctime_ns==z.st_ctime_ns and before.st_ctime_ns==after.st_ctime_ns
        and len(b)==z.st_size<=maximum,'FILE_CHANGED')
    no_reparse(p);return b
def integer(v,hi,name,lo=0):
    need(type(v) is int and lo<=v<=hi,name);return v
def same(a,b):
    if type(a) is not type(b):return False
    if type(a) is dict:return set(a)==set(b) and all(same(a[k],b[k]) for k in a)
    if type(a) is list:return len(a)==len(b) and all(same(x,y) for x,y in zip(a,b))
    return a==b
def identity(row):
    s=row['snapshot'];integer(s['pass'],2,'PASS')
    for k in ('netId','startPadId','rootPadId'):need(type(s[k]) is str and 0<len(s[k])<=256,'IDENTITY')
    return [s[k] for k in ('pass','netId','startPadId','rootPadId')]
def cap_facts(s,profile):
    need(type(s) is dict and profile in ('BASE','RAMP'),'CAP_CONTEXT')
    h=integer(s['prefixViaCount'],48,'PREFIX_VIAS')
    for k in ('branchExpanded','totalExpanded','frontierCount'):integer(s[k],1000000,k)
    need(type(s['terminalReason']) is str and 0<len(s['terminalReason'])<=256,'TERMINAL_REASON')
    return dict(prefixViaCount=h,remainingHoleSlots=48-h,maximumBranchTransitions=4,
       remainingBranchTransitions=min(48-h,4),expectedBranchPrice=60 if profile=='BASE' else 60+60*min(h,48)//48,
       priceObservation='DERIVED_NOT_EXPORTED',geometricFeasibility='NOT_PROVEN',
       **{k:s[k] for k in ('terminalReason','branchExpanded','totalExpanded','frontierCount')})
def summarize(branches,expected_expansions):
    need(type(branches) is list and 0<len(branches)<=10000,'BRANCH_CENSUS')
    integer(expected_expansions,1000000,'EXPECTED_EXPANSIONS')
    total=0;counts=Counter()
    for ordinal,row in enumerate(branches):
        need(type(row) is dict and row.get('record')=='BRANCH' and type(row.get('ordinal')) is int and row['ordinal']==ordinal,'BRANCH_ORDINAL')
        need(type(row.get('prefixSha256')) is str and HEX.fullmatch(row['prefixSha256']),'GEOMETRY_DIGEST')
        integer(row.get('prefixBytes'),MAX_BYTES,'PREFIX_BYTES',1)
        identity(row);s=row['snapshot'];cap_facts(s,'BASE')
        total+=s['branchExpanded'];need(s['totalExpanded']==total,'CUMULATIVE_EXPANSIONS')
        counts[s['prefixViaCount']]+=1
    need(total==expected_expansions,'EXPANSION_SUM')
    return dict(branchCount=len(branches),prefixViaCountHistogram={str(h):counts[h] for h in sorted(counts)},
                totalExpanded=branches[-1]['snapshot']['totalExpanded'],sumBranchExpanded=total,lastBranch=branches[-1]['snapshot'])
def digest(row):return dict(sha256=row['prefixSha256'],bytes=row['prefixBytes'])
def compare_traces(base,ramp):
    need(type(base) is list and type(ramp) is list,'TRACE_LISTS')
    end=0
    while end<min(len(base),len(ramp)) and same(identity(base[end]),identity(ramp[end])):end+=1
    mismatch=None
    if end<len(base) or end<len(ramp):mismatch=dict(index=end,baseIdentity=identity(base[end]) if end<len(base) else None,rampIdentity=identity(ramp[end]) if end<len(ramp) else None)
    first=None;geometry=None
    for i in range(end):
        a=base[i]['snapshot'];b=ramp[i]['snapshot']
        fields=[dict(field=k,base=a.get(k),ramp=b.get(k)) for k in sorted(set(a)|set(b)) if k not in a or k not in b or not same(a[k],b[k])]
        if first is None and fields:first=dict(index=i,identity=identity(base[i]),fields=fields)
        if geometry is None and not same(digest(base[i]),digest(ramp[i])):geometry=dict(index=i,base=digest(base[i]),ramp=digest(ramp[i]))
    return dict(equalIdentityPrefix=end,firstIdentityDifference=mismatch,firstAlignedSnapshotDifference=first,firstGeometryDigestDifference=geometry)
def audit(base_raw,ramp_raw,qual_raw):
    need(sha(qual_raw)==QUAL,'QUALIFICATION_HASH');q=parse(qual_raw)
    need(q['status']=='ACCEPTED_FINITE_DIAGNOSTIC_ONLY' and q['decision']=='NOT_ADOPTED','QUALIFICATION_CONTEXT')
    traces=[];facts=[]
    for raw,(profile,size,expected_sha) in zip((base_raw,ramp_raw),INPUTS):
        need(len(raw)==size and sha(raw)==expected_sha,'INPUT_HASH_'+profile)
        records=parse_records(raw);branches=[x for x in records if x.get('record')=='BRANCH']
        results=[x for x in records if x.get('record')=='RESULT'];need(len(results)==1,'RESULT_COUNT')
        accepted=next(c for c in q['cases'] if c['profile']==profile and c['kind']=='AUTHORED')
        need(same(results[0],accepted['result']),'RESULT_QUALIFICATION')
        summary=summarize(branches,results[0]['receipt']['expansions'])
        need(summary['branchCount']==accepted['branchCount'] and same(summary['prefixViaCountHistogram'],accepted['prefixViaCountHistogram']),'QUALIFICATION_COUNTS')
        need(same(summary['lastBranch'],accepted['lastBranchSnapshot']),'QUALIFICATION_LAST_BRANCH')
        facts.append(dict(profile=profile,summary=summary,terminalCapFacts=cap_facts(branches[-1]['snapshot'],profile),
            result=results[0],recordBytes=len(raw),recordSha256=sha(raw)))
        traces.append(branches)
    return dict(schema='tsj-via-price-trace-audit-v1',status='PASS_FINITE_TRACE_AUDIT_ONLY',
        planSha256=PLAN,sourceContractSha256=CONTRACT,qualificationSha256=QUAL,
        cases=facts,comparison=compare_traces(*traces),decision='NOT_ADOPTED',
        geometryDigestMeaning='serialized geometry fingerprint; not complete physical/search-state equality',
        causality='NOT_PROVEN',feasibleContinuation='NOT_PROVEN',playable100=0,
        nativeRun='NOT_RUN',electricalMeasurements='NOT_RUN',scope='TWO_ACCEPTED_CANONICAL_REQUEST_RECEIPTS_ONLY')
def main():
    need(sys.dont_write_bytecode and len(sys.argv)==5,'USAGE: python -B audit_trace.py BASE RAMP QUALIFICATION NEW_OUTPUT')
    home=Path(__file__).resolve().parent
    plan=read(home/'PLAN_R1.md',65536);contract=read(home/'SOURCE_CONTRACT.json',65536)
    need(sha(plan)==PLAN and sha(contract)==CONTRACT,'CONTROL_HASH')
    paths=list(map(Path,sys.argv[1:4]));raw=[read(p,MAX_BYTES) for p in paths]
    result=audit(*raw)
    for p,b in zip(paths,raw):need(read(p,MAX_BYTES)==b,'INPUT_CLOSE_CHANGED')
    need(read(home/'PLAN_R1.md',65536)==plan and read(home/'SOURCE_CONTRACT.json',65536)==contract,'CONTROL_CLOSE_CHANGED')
    out=Path(sys.argv[4]);need(out.is_absolute(),'OUTPUT_ABSOLUTE');no_reparse(out.parent)
    need(not os.path.lexists(out),'OUTPUT_EXISTS')
    encoded=(json.dumps(result,indent=2,sort_keys=True,ensure_ascii=True,allow_nan=False)+'\n').encode()
    need(len(encoded)<=1048576,'OUTPUT_BYTES')
    with out.open('xb') as f:f.write(encoded);f.flush()
    marker=dict(status=result['status'],bytes=len(encoded),sha256=sha(encoded))
    sys.stdout.buffer.write((json.dumps(marker,separators=(',',':'))+'\n').encode());sys.stdout.buffer.flush()
if __name__=='__main__':main()
