from pathlib import Path
import hashlib,json
s=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
names=['prepare_lu_support_census_sequence_root.py','run_lu_support_census_timing.py','validate_lu_support_census_pair_root.py']
prior=s/'scratch/lu-support-census-sequence-worker-handoff';prior.mkdir()
for name in names:(prior/name).write_bytes((s/name).read_bytes())
(prior/'receipt.json').write_text(json.dumps({'status':'INTERRUPTED_SOURCE_ONLY_DRAFT_ROOT_TAKEOVER','files':[{'path':n,'sha256':sha(s/n)} for n in names],'reviewFindings':['OFF baseline has no census fields; draft falsely required false flag','Timing summary falsely listed all seven fields on every report','Gate/reader identities need binding; no sequence driver delivered']},indent=2)+'\n',encoding='utf-8')
p=s/names[2];t=p.read_text(encoding='utf-8')
start=t.index('    need(type(report.get("supportCensusRequested")) is bool,')
end=t.index('    need(report.get("supportCensusQuery")',start)
t=t[:start]+'''    if not enabled:
        need(present == set(), label + " baseline control unexpectedly exposes census fields")
        return {"enabled": False, "supportPaths": []}
    need(report.get("supportCensusRequested") is True,
         label + " ON must declare supportCensusRequested=true")
    need(present == set(SUPPORT_PATHS), label + " ON must expose exactly seven census paths")
'''+t[end:]
t=t.replace('need(set(off_removed) == {"/supportCensusRequested"},','need(off_removed == [],')
p.write_text(t,encoding='utf-8',newline='\n')
p=s/names[1];t=p.read_text(encoding='utf-8')
start=t.index('            "supportPathsPresent": [')
end=t.index('            "cold":',start)
t=t[:start]+'''            "supportPathsPresent": actual_support_paths(report),
'''+t[end:]
pos=t.index('\ndef main(')
t=t[:pos]+'''
def actual_support_paths(report, path=""):
    found = []
    if isinstance(report, dict):
        for key,value in report.items():
            child=path+"/"+key
            if "supportcensus" in key.casefold(): found.append(child)
            found.extend(actual_support_paths(value,child))
    elif isinstance(report,list):
        for index,value in enumerate(report):
            found.extend(actual_support_paths(value,path+"["+str(index)+"]"))
    return found

'''+t[pos:]
p.write_text(t,encoding='utf-8',newline='\n')
p=s/names[0];t=p.read_text(encoding='utf-8')
needle='        plan_bytes = json_bytes(plan)'
insert='''        gate_path = ROOT / "lu-support-census-r4-root-gate-preflight.json"
        gate = load_json(gate_path, "root focused gate preflight")
        need(sha256_file(gate_path) == "e66f323e5488ebee3a3f026d190b2c6f38e72a262801b13ce824ff4cbfb808a4",
             "root gate preflight changed")
        for entry in gate["files"]:
            need(sha256_file(ROOT/entry["path"]) == entry["sha256"], "gate receipt changed")
        pinned = {
            "validate_current_q30_timing_pair.py": "67430d7c56d872d115a7c38732bf60101cff52e8bf40f7af075213debc8e7e3b",
        }
        repo_pinned = {
            "docs/task-evidence/Q30/scale-plan-4/check_coordinator.py": "247ded434bcdbdba6f78e7edf8f9c8b3671aaf8465bfabb29c875d5b2512bddb",
            "docs/task-evidence/Q30/scale-plan-4/check_d01.py": "f58847541571cdf829835d05c200bac6ea0079dab065d90ebdf7f44914241929",
        }
        for name,digest in pinned.items(): need(sha256_file(ROOT/name)==digest, "adapter changed")
        for name,digest in repo_pinned.items(): need(sha256_file(repo/name)==digest, "reader changed")
        plan["clockBoundary"] = "Host operation/wrapper durations are monotonic. Existing coordinator cold/proof/routing milliseconds use its wall-clock path; do not claim these are monotonic partitions. Earlier separate stage-profile evidence supplies the monotonic partition."
'''
assert t.count(needle)==1;t=t.replace(needle,insert+needle)
needle='        binding_bytes = json_bytes(binding)'
insert='''        binding["gateFiles"] = gate["files"] + [{"path":gate_path.name,"sha256":sha256_file(gate_path)}]
        binding["pinnedFiles"] = pinned
        binding["pinnedRepoFiles"] = repo_pinned
'''
assert t.count(needle)==1;t=t.replace(needle,insert+needle)
p.write_text(t,encoding='utf-8',newline='\n')
print(json.dumps({'status':'ROOT_DRAFT_REPAIRED_NOT_RUN','files':[{n:sha(s/n)} for n in names]}))
