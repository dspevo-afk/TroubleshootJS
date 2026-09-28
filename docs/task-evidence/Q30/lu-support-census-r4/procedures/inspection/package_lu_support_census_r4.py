#!/usr/bin/env python3
"""Package the bounded Q30 LU support-census R4 evidence packet."""
from __future__ import annotations
import argparse, gzip, hashlib, json, re, subprocess, sys
from pathlib import Path

SCRATCH_DEFAULT = Path(r"<TASK_PATH>")
REPO_DEFAULT = Path(r"<TASK_PATH>")
PACKET_REL = Path("docs/task-evidence/Q30/lu-support-census-r4")
PLAN_SHA = "e7a7af380a86d73a8befa21adc94d77be2c4bd63812e631542d208ec3be39b19"
BINDING_SHA = "c1980c55187780676b8977fb3d42735e17d5fd67447807f77f8db27a9fff74bf"
SEQUENCE_SHA = "64bc892024ae0f7f4d04fce8ef706c67bb58fce2880ea4cd93fc26937c19e20c"
INTERPRETATION_SHA = "fd59c8140cdafe92093df2f7985eb8d00ea6f4d088b40a034e99449df374733e"
R4_AUDIT_SHA = "d021ec847e38fc792e7dbc095e2de8a012930010ede1aff1888e0c9f7a3d94d0"
ACTUAL_LU_TEST_SHA = "a30ddfc294a12d3a3aa6dc3076fd286080b32705d5d5379d93bf32e6e1bdefb3"
R2_COLLECTOR_SHA = "1c2aeaa150a766f4e6dcddacaa9f8865b58274f9e160294d90b8073646bfd5b5"
R2_MANIFEST_SHA = "b1b0eea88eeb71c12f1fbc757289cff722fb33689babe13a69f02062fe8efe17"
METADATA_VALIDATOR_SHA = "2646c9dd98b6ffc66bd9d21ed443bdd43ff3adb9b3adb8a3af9676be101352d6"
LABELS = ["lu-support-census-r4-01-off-10014", "lu-support-census-r4-02-on-10014", "lu-support-census-r4-03-off-10014"]
OVERLAYS = [
 "scripts/verify-current-contracts.ps1",
 "src/com/lushprojects/circuitjs1/client/CirSim.java",
 "src/com/lushprojects/circuitjs1/client/Q30CoordinatorQualificationVerifier.java",
 "src/com/lushprojects/circuitjs1/client/Q30LuSupportCensus.java",
 "tests/contracts/Q30LuSupportCensusContractTest.java",
 "tests/contracts/Q30LuSupportCensusActualLuContractTest.java",
]
FUSION_R2_REFS = [
 "source/base-and-reader-references.json", "source/parent/source-pair-audit.json",
 "source/parent/packet-inventory.json", "source/provenance.json",
 "source/measured-r2-overlays/scripts/verify-current-contracts.ps1.gz",
 "source/measured-r2-overlays/src/com/lushprojects/circuitjs1/client/CirSim.java.gz",
 "source/measured-r2-overlays/tests/contracts/Q30LuPivotCollectionContractTest.java.gz",
]
RUN_META = ["identity.json", "input-manifest-before.json", "input-manifest-after.json",
 "owned-processes-before.json", "owned-processes-before-close.json", "progress.json",
 "result.json", "run-start.json", "runner-before.json", "runner-after.json", "spec.json"]

def sha(b): return hashlib.sha256(b).hexdigest()
def jbytes(x): return (json.dumps(x, ensure_ascii=False, indent=2) + "\n").encode()
def readj(p): return json.loads(p.read_bytes())
def needfile(p):
 if not p.is_file(): raise RuntimeError("missing required input: " + p.name)
 return p

def sanitize(raw):
 try: text = raw.decode("utf-8")
 except UnicodeDecodeError: return raw, "exact-binary"
 original = text
 roots = [str(SCRATCH_DEFAULT), str(SCRATCH_DEFAULT).replace("\\", "\\\\"),
          SCRATCH_DEFAULT.as_posix(), str(REPO_DEFAULT), str(REPO_DEFAULT).replace("\\", "\\\\"),
          REPO_DEFAULT.as_posix(), r"<TASK_PATH>"]
 for value in sorted(set(roots), key=len, reverse=True): text = text.replace(value, "<TASK_PATH>")
 text = re.sub(r"""(?i)[A-Z]:(?:\\+|/)+Users(?:\\+|/)+[^\\/\s"'<>]+(?:\\+|/)+[^\s"'<>]*""", "<USER_PATH>", text)
 text = re.sub(r"""(?im)(\b(?:authorization|proxy-authorization)\s*[:=]\s*(?:bearer\s+)?)[A-Za-z0-9._~+/=-]{12,}""", r"\1<REDACTED>", text)
 text = re.sub(r"""(?im)(\b(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password)\s*[:=]\s*)[^\s,"'<>]{8,}""", r"\1<REDACTED>", text)
 cooked = text.encode("utf-8")
 return cooked, ("path-or-secret-redaction" if cooked != raw else "exact")

def private(b):
 return bool(re.search(rb"(?i)[A-Z]:(?:\\+|/)+Users(?:\\+|/)+[^\\/\s\"<>]+", b))
def secret(b):
 return bool(re.search(rb"(?im)(?:authorization|proxy-authorization)\s*[:=]\s*(?:bearer\s+)?[A-Za-z0-9._~+/=-]{12,}", b)) or bool(re.search(rb"(?im)(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password)\s*[:=]\s*[^\s,\"'<>]{8,}", b))

def gitblob(repo, commit, rel):
 spec = f"{commit}:{rel}"
 oid = subprocess.run(["git", "-C", str(repo), "rev-parse", "--verify", spec],
                      stdout=subprocess.PIPE, stderr=subprocess.PIPE)
 if oid.returncode: raise RuntimeError("missing committed reference " + rel)
 blob = subprocess.run(["git", "-C", str(repo), "cat-file", "blob", oid.stdout.decode().strip()],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
 if blob.returncode: raise RuntimeError("cannot read committed blob " + rel)
 return blob.stdout

class Packet:
 def __init__(self, repo, scratch, out, commit):
  self.repo, self.scratch, self.out, self.commit = repo.resolve(), scratch.resolve(), out.resolve(), commit
  expected = (self.repo / PACKET_REL).resolve()
  if self.out != expected: raise RuntimeError("output path must be " + str(expected))
  if not self.repo.is_dir() or not self.scratch.is_dir(): raise RuntimeError("repo or scratch root missing")
  self.resume = self.out.exists()
  self.resume_state = self.out/"provenance/packaging-attempts/attempt-1-partial-state.json"
  self.resume_failure = self.out/"provenance/packaging-attempts/attempt-1-failure.txt"
  if self.resume: self.validate_resume_checkpoint()
  else: self.out.mkdir(parents=True)
  self.rows, self.compressed, self.paths = [], [], set()
 def validate_resume_checkpoint(self):
  if not self.resume_state.is_file() or not self.resume_failure.is_file():
   raise RuntimeError("existing output lacks the task-created resume checkpoint")
  state = readj(self.resume_state)
  expected = state.get("existingFilesBeforeResume")
  if state.get("inventoryCreated") is not False or not isinstance(expected,list):
   raise RuntimeError("resume checkpoint is invalid")
  actual = {}
  for f in self.out.rglob("*"):
   if f.is_symlink() or not f.is_file(): continue
   rel=f.relative_to(self.out).as_posix()
   if rel==self.resume_state.relative_to(self.out).as_posix(): continue
   actual[rel]={"bytes":f.stat().st_size,"sha256":sha(f.read_bytes())}
  target={x["path"]:{"bytes":x["bytes"],"sha256":x["sha256"]} for x in expected}
  if actual != target: raise RuntimeError("existing output differs from the verified resume checkpoint")
  if (self.out/"inventory.json").exists(): raise RuntimeError("inventory already exists; refusing resume")
 def source_ref(self, src):
  if src is None: return "generated:package_lu_support_census_r4.py"
  try: return "generated:retained-packet-file/" + src.resolve().relative_to(self.out).as_posix()
  except ValueError: pass
  try: return "scratch/" + src.resolve().relative_to(self.scratch).as_posix()
  except ValueError: pass
  try: return f"repo/{self.commit}/" + src.resolve().relative_to(self.repo).as_posix()
  except ValueError: return "generated:external-reference"
 def put(self, rel, stored, *, src=None, source=None, payload=None, transform="exact"):
  rel = Path(rel).as_posix()
  if rel.startswith("../") or rel in self.paths: raise RuntimeError("unsafe or duplicate output " + rel)
  self.paths.add(rel)
  dest = self.out / rel
  if dest.exists():
   if dest.is_symlink() or not dest.is_file() or dest.read_bytes()!=stored:
    raise RuntimeError("existing output differs from reconstructed artifact " + rel)
  else:
   dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(stored)
  source = stored if source is None else source
  row = {"path": rel, "sourceRef": self.source_ref(src), "sourceBytes": len(source),
         "sourceSha256": sha(source), "storedBytes": len(stored), "storedSha256": sha(stored),
         "transform": transform}
  if payload is not None:
   row["payloadBytes"], row["payloadSha256"] = len(payload), sha(payload)
   self.compressed.append({"path": rel, "payloadBytes": len(payload), "payloadSha256": sha(payload),
                           "storedBytes": len(stored), "storedSha256": sha(stored),
                           "gzipMtimeZero": transform.startswith("gzip-mtime-0")})
  self.rows.append(row); return dest
 def copytext(self, src, rel, expected=None):
  raw = src.read_bytes()
  if expected and sha(raw) != expected: raise RuntimeError("source hash mismatch: " + src.name)
  cooked, change = sanitize(raw)
  return self.put(rel, cooked, src=src, source=raw, transform=change)
 def inspect(self, src, rel):
  raw = src.read_bytes(); cooked, red = sanitize(raw)
  text = cooked.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
  cooked = ("\n".join(x.rstrip(" \t") for x in text.split("\n")).rstrip("\n") + "\n").encode()
  norm = "inspection-LF-trim" if cooked != sanitize(raw)[0] else "exact"
  return self.put(rel, cooked, src=src, source=raw, transform=";".join(x for x in (red, norm) if x != "exact") or "exact")
 def gziptext(self, src, rel, expected=None):
  raw = src.read_bytes()
  if expected and sha(raw) != expected: raise RuntimeError("source hash mismatch: " + src.name)
  cooked, red = sanitize(raw); packed = gzip.compress(cooked, compresslevel=9, mtime=0)
  return self.put(rel, packed, src=src, source=raw, payload=cooked,
                  transform="gzip-mtime-0;" + red)
 def overlay(self, src, rel, expected=None):
  raw = src.read_bytes()
  if expected and sha(raw) != expected: raise RuntimeError("overlay hash mismatch: " + src.name)
  if private(raw) or secret(raw): raise RuntimeError("exact overlay has private path or secret: " + src.name)
  return self.put(rel, gzip.compress(raw, compresslevel=9, mtime=0), src=src, source=raw,
                  payload=raw, transform="gzip-mtime-0-exact-source-overlay")
 def original_gzip(self, src, rel, expected_payload=None):
  packed = src.read_bytes(); payload = gzip.decompress(packed)
  if expected_payload and sha(payload) != expected_payload: raise RuntimeError("gzip payload mismatch: " + src.name)
  if private(payload) or secret(payload): raise RuntimeError("gzip payload has private path or secret: " + src.name)
  return self.put(rel, packed, src=src, source=packed, payload=payload,
                  transform="exact-original-gzip-validated-payload")
 def repo_copy(self, relsrc, reldest):
  raw = gitblob(self.repo, self.commit, relsrc)
  return self.put(reldest, raw, src=self.repo / relsrc, source=raw, transform="exact-pinned-repository-reference")
 def repo_gzip(self, relsrc, reldest):
  raw = gitblob(self.repo, self.commit, relsrc)
  if private(raw) or secret(raw): raise RuntimeError("committed diff contains private path/secret " + relsrc)
  return self.put(reldest, gzip.compress(raw, compresslevel=9, mtime=0), src=self.repo/relsrc,
                  source=raw, payload=raw, transform="gzip-mtime-0-exact-committed-patch")
 def generated(self, rel, obj, transform="generated-json"):
  return self.put(rel, jbytes(obj), transform=transform)

 def privacy_check(self):
  bad = []
  for f in self.out.rglob("*"):
   if not f.is_file(): continue
   raw = f.read_bytes(); chunks = [raw]
   if f.suffix == ".gz": chunks.append(gzip.decompress(raw))
   if any(private(x) or secret(x) for x in chunks): bad.append(f.relative_to(self.out).as_posix())
  if bad: raise RuntimeError("private paths/secrets remain: " + ", ".join(bad[:10]))
 def finish(self, readme, lineage):
  self.generated("source/overlay-map.json", lineage, "generated-source-overlay-map")
  self.generated("provenance/compression-ledger.json",
   {"schema":"tsj-q30-lu-support-census-r4-compression/1","artifacts":self.compressed},
   "generated-compression-ledger")
  self.put("README.md", readme.encode(), transform="generated-evidence-summary")
  self.rows.sort(key=lambda x:x["path"])
  inv = {"schema":"tsj-q30-lu-support-census-r4-evidence/1",
   "status":"PACKAGED_PENDING_ROOT_PACKET_AUDIT","sourceCommitReference":self.commit,
   "planSha256":PLAN_SHA,"bindingSha256":BINDING_SHA,"sequenceSha256":SEQUENCE_SHA,
   "rootInterpretationSha256":INTERPRETATION_SHA,"sourceAuditSha256":R4_AUDIT_SHA,
   "artifactCountExcludingInventory":len(self.rows),"artifacts":self.rows}
  raw = jbytes(inv); (self.out/"inventory.json").write_bytes(raw)
  (self.out/"inventory.sha256").write_text(sha(raw)+"  inventory.json\n",encoding="ascii")
  self.privacy_check()
  return sha(raw), len(self.rows)

def refresh_source_attribution(repo, scratch, out):
 repo, scratch, out = repo.resolve(), scratch.resolve(), out.resolve()
 if out != (repo/PACKET_REL).resolve(): raise RuntimeError("refresh output path mismatch")
 invpath, shafile = out/"inventory.json", out/"inventory.sha256"
 oldraw=invpath.read_bytes(); oldsha=sha(oldraw)
 if shafile.read_text("ascii") != oldsha+"  inventory.json\n":
  raise RuntimeError("existing inventory hash receipt mismatch")
 inv=json.loads(oldraw); commit=inv.get("sourceCommitReference")
 rows=inv.get("artifacts")
 if not isinstance(rows,list) or inv.get("artifactCountExcludingInventory")!=len(rows):
  raise RuntimeError("existing inventory structure mismatch")
 bypath={r.get("path"):r for r in rows}
 if len(bypath)!=len(rows): raise RuntimeError("duplicate inventory paths")

 # Refresh the one packaged inspection copy from the final current helper source.
 helper=Path(__file__).resolve(); helper_rel=helper.relative_to(scratch).as_posix()
 helper_path="procedures/inspection/package_lu_support_census_r4.py"
 hrow=bypath.get(helper_path); htarget=out/helper_path
 if hrow is None or not htarget.is_file() or htarget.is_symlink():
  raise RuntimeError("packaged helper inspection copy is missing or unsafe")
 source=helper.read_bytes(); cooked,red=sanitize(source)
 text=cooked.decode("utf-8").replace("\r\n","\n").replace("\r","\n")
 cooked=("\n".join(x.rstrip(" \t") for x in text.split("\n")).rstrip("\n")+"\n").encode()
 norm="inspection-LF-trim" if cooked!=sanitize(source)[0] else "exact"
 htarget.write_bytes(cooked)
 hrow.update({"sourceRef":"scratch/"+helper_rel,"sourceBytes":len(source),"sourceSha256":sha(source),
  "storedBytes":len(cooked),"storedSha256":sha(cooked),
  "transform":";".join(x for x in (red,norm) if x!="exact") or "exact"})

 expected=set(bypath)|{"inventory.json","inventory.sha256"}; actual=set()
 for f in out.rglob("*"):
  st=f.lstat()
  if f.is_symlink() or (getattr(st,"st_file_attributes",0)&0x400):
   raise RuntimeError("reparse point in packet: "+f.relative_to(out).as_posix())
  if f.is_file(): actual.add(f.relative_to(out).as_posix())
 if actual!=expected: raise RuntimeError("packet file set differs from prior inventory")

 repo_verified=scratch_verified=retained_fixed=0
 for r in rows:
  rel=r["path"]; stored=(out/rel).read_bytes()
  if len(stored)!=r["storedBytes"] or sha(stored)!=r["storedSha256"]:
   raise RuntimeError("stored artifact hash mismatch: "+rel)
  if r.get("payloadSha256"):
   payload=gzip.decompress(stored)
   if len(payload)!=r["payloadBytes"] or sha(payload)!=r["payloadSha256"]:
    raise RuntimeError("payload hash mismatch: "+rel)
  ref=r["sourceRef"]
  if ref.startswith("scratch/"):
   sourcepath=(scratch/ref[len("scratch/"):]).resolve()
   if not sourcepath.is_file(): raise RuntimeError("missing scratch source: "+ref)
   source=sourcepath.read_bytes()
   if len(source)!=r["sourceBytes"] or sha(source)!=r["sourceSha256"]:
    raise RuntimeError("scratch source bytes differ from inventory: "+rel)
   scratch_verified+=1
  elif ref.startswith("repo/"):
   _,refcommit,refpath=ref.split("/",2)
   if refcommit!=commit: raise RuntimeError("repo source uses a different pinned commit: "+rel)
   oid=subprocess.run(["git","-C",str(repo),"rev-parse","--verify",f"{refcommit}:{refpath}"],
    stdout=subprocess.PIPE,stderr=subprocess.PIPE)
   if oid.returncode:
    candidate=(repo/refpath).resolve()
    target=(out/rel).resolve()
    if candidate!=target: raise RuntimeError("missing pinned repository source: "+refpath)
    if len(stored)!=r["sourceBytes"] or sha(stored)!=r["sourceSha256"]:
     raise RuntimeError("retained packet source hash mismatch: "+rel)
    r["sourceRef"]="generated:retained-packet-file/"+rel
    retained_fixed+=1
   else:
    blob=subprocess.run(["git","-C",str(repo),"cat-file","blob",oid.stdout.decode().strip()],
     stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if blob.returncode: raise RuntimeError("cannot read pinned Git blob: "+refpath)
    if len(blob.stdout)!=r["sourceBytes"] or sha(blob.stdout)!=r["sourceSha256"]:
     raise RuntimeError("repo source bytes differ from inventory: "+rel)
    repo_verified+=1
  elif ref.startswith("generated:retained-packet-file/"):
   if ref.split("/",1)[1]!=rel or len(stored)!=r["sourceBytes"] or sha(stored)!=r["sourceSha256"]:
    raise RuntimeError("retained packet source attribution mismatch: "+rel)
  elif ref.startswith("generated:"):
   pass
  else: raise RuntimeError("unsupported source reference: "+rel)

 raw=jbytes(inv); invpath.write_bytes(raw); shafile.write_text(sha(raw)+"  inventory.json\n",encoding="ascii")
 print(json.dumps({"status":"SOURCE_ATTRIBUTION_REFRESHED","artifactCount":len(rows),
  "inventorySha256":sha(raw),"repoSourceRefsVerifiedAgainstGit":repo_verified,
  "scratchSourceRefsVerifiedAgainstCurrentFiles":scratch_verified,
  "retainedPacketRefsCorrected":retained_fixed,"packagedHelperUpdated":True},indent=2))

def copy_run(p, scratch, label, summary):
 run = scratch/label
 result = json.loads(needfile(run/"result.json").read_bytes())
 if result.get("outcome") != "PASS": raise RuntimeError("run did not pass: "+label)
 cleanup=result.get("cleanup",{})
 if cleanup.get("status")!="PASS" or cleanup.get("errors") or cleanup.get("ownedSurvivors"):
  raise RuntimeError("run cleanup did not pass: "+label)
 cases=result.get("cases")
 if not isinstance(cases,list) or len(cases)!=1: raise RuntimeError("bad report binding: "+label)
 report_rel=cases[0].get("reportFile")
 if not report_rel or Path(report_rel).is_absolute() or ".." in Path(report_rel).parts:
  raise RuntimeError("unsafe report path: "+label)
 report=needfile(run/report_rel)
 if sha(report.read_bytes()) != summary.get("reportSha256"): raise RuntimeError("report/sequence hash mismatch: "+label)
 for name in RUN_META:
  f=run/name
  if f.is_file(): p.copytext(f,f"runs/{label}/{name}")
 p.gziptext(report,f"runs/{label}/cases/001-{label}.report.json.gz",summary["reportSha256"])
 for suffix in ("-spec.json",".log","-driver.log","-summary.json"):
  f=scratch/(label+suffix)
  if f.is_file(): p.copytext(f,f"runs/{label}/{f.name}")
 for pattern in (label+"-strict-reader-*.txt",label+"-strict-wrapper-*.json",
                label+"-vs-*-support-census-parity-*.json"):
  for f in sorted(scratch.glob(pattern)):
   p.copytext(f,f"runs/{label}/receipts/{f.name}")

def make_readme(i, gate):
 t=i["timings"]; cold=[x["cold"]["elapsedMs"] for x in t]; warm=[x["warm"]["elapsedMs"] for x in t]
 groups=i["cold"]["groups"]; shapes=", ".join(f"{k} ({v['samples']} samples)" for k,v in sorted(groups.items()))
 d=i["cold"]["separateOverlappingDomains"]
 return f"""# Q30 LU support census R4 — private structural evidence

Status: {i['status']}. Normal-player Q30 remains {i['normalAcceptance']}. This packet is a structural census, not optimization acceptance.

The frozen sequence used seed 10014 in OFF/ON/OFF order. Cold application elapsed values were {cold[0]:,}, {cold[1]:,}, and {cold[2]:,} ms. The controls span {i['controlColdRangeMs']:,} ms; ON differs from the first and second controls by {i['profileColdMinusEachControlMs'][0]:+,} and {i['profileColdMinusEachControlMs'][1]:+,} ms, inside the control range. Every row had 490 cold work units and 390 hypothesis units. Warm elapsed values were {warm[0]:,}, {warm[1]:,}, and {warm[2]:,} ms with 101 work units and one hypothesis unit per row. Cleanup passed for all three. Application elapsed fields retain the solver's existing wall-clock semantics; host operation duration is monotonic. These values support no speed or zero-overhead claim.

Sampling selected every 4096th eligible LU factor call without adding clocks inside LU. It retained {i['cold']['selectedFactorCalls']} cold samples from {i['cold']['eligibleFactorCalls']} eligible calls and {i['warm']['selectedFactorCalls']} warm samples from {i['warm']['eligibleFactorCalls']}. Cold matrix dimensions were {shapes}. Multiple current components occurred in all {i['cold']['multipleCurrentComponentSamples']} cold and {i['warm']['multipleCurrentComponentSamples']} warm samples. Stable original partitions and drift-free support samples were both zero. Sampled support added {i['cold']['supportAdded']['min']}–{i['cold']['supportAdded']['max']} edges and found {i['cold']['crossOriginalEdges']['min']}–{i['cold']['crossOriginalEdges']['max']} edges across original components.

Pivot, Lower, and Upper outside-current-component fractions were {d['Pivot']['outsideFraction']:.5f}, {d['Lower']['outsideFraction']:.5f}, and {d['Upper']['outsideFraction']:.5f}. The domains overlap; these structural counts are not elapsed time, nonzero arithmetic, exhaustive coverage, or evidence that rows can be skipped. No cross-group pivots or post-factor sentinel risks were observed, but fallback observation was unavailable and every sample remained exact=false. The root decision is NO-GO for a static original-matrix partition or block restriction.

Native7 passed with 4,224 collector assertions, 328 actual-LU assertions, and 388 existing LU-oracle assertions. Corrected GWT5, compiled canaries, strict readers, both parity comparisons, and the three owned cleanups passed. The first clone build and first native marker run remain recorded as failures alongside their corrected receipts. Root preflight status is {gate['status']}; it does not establish normal acceptance.

Source audits r2/r3/r4, current exact source overlays, the actual-LU test and prior review snapshot, and the minimal committed pivot-lower-fusion R2 lineage needed to reconstruct its measured candidate are included. Parent audit 0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197 and the separately stored parent source-pair audit retain distinct identities. Inventory and source/overlay-map.json bind each path, source hash, stored hash, and transform. No full candidate tree, generated WAR, JAR, cache, fixture bundle, or browser profile is included.

Normal limits remain 90,000 ms cumulative, 640 shared work units, and 5,000 ms per active operation. The private sequence used its separate 300,000 ms measurement ceiling. No production solver algorithm changed and no optimization was integrated. U06/U07 and later milestones remain unstarted. Packet audit is pending root review.
"""

def main():
 ap=argparse.ArgumentParser()
 ap.add_argument("--repo",type=Path,default=REPO_DEFAULT)
 ap.add_argument("--scratch",type=Path,default=SCRATCH_DEFAULT)
 ap.add_argument("--out",type=Path)
 ap.add_argument("--interpretation",type=Path)
 ap.add_argument("--refresh-source-attribution",action="store_true")
 a=ap.parse_args(); repo=a.repo.resolve(); scratch=a.scratch.resolve()
 out=(a.out or repo/PACKET_REL).resolve()
 if a.refresh_source_attribution:
  refresh_source_attribution(repo,scratch,out)
  return
 interp_path=(a.interpretation or scratch/"lu-support-census-r4-root-interpretation.json").resolve()
 planp=needfile(scratch/"lu-support-census-r4-measurement-plan.json")
 bindp=needfile(scratch/"lu-support-census-r4-execution-binding.json")
 seqp=needfile(scratch/"lu-support-census-r4-sequence-result.json")
 intp=needfile(interp_path); auditroot=scratch/"lu-support-census-r2-preparation"
 auditp=needfile(auditroot/"source-audit-r4.json")
 gatep=needfile(scratch/"lu-support-census-r4-root-gate-preflight.json")
 planraw,bindraw,seqraw,intraw,auditraw=(x.read_bytes() for x in (planp,bindp,seqp,intp,auditp))
 for raw,expected,name in ((planraw,PLAN_SHA,"plan"),(bindraw,BINDING_SHA,"binding"),
  (seqraw,SEQUENCE_SHA,"sequence"),(intraw,INTERPRETATION_SHA,"interpretation"),
  (auditraw,R4_AUDIT_SHA,"source audit")):
  if sha(raw)!=expected: raise RuntimeError(name+" hash mismatch")
 plan,bind,seq,interp,gate=(json.loads(planraw),json.loads(bindraw),json.loads(seqraw),json.loads(intraw),readj(gatep))
 if plan.get("status")!="PREDECLARED" or plan.get("productionAcceptance") is not False:
  raise RuntimeError("plan scope/status mismatch")
 if bind.get("status")!="FROZEN_READY_FOR_PRIVATE_MEASUREMENT" or bind.get("planSha256")!=PLAN_SHA:
  raise RuntimeError("frozen binding mismatch")
 if seq.get("status")!="PRIVATE_CENSUS_BOTH_COMPARISONS_PASS_PENDING_INTERPRETATION":
  raise RuntimeError("sequence did not finish passing")
 if [x.get("label") for x in seq.get("rows",[])]!=LABELS or seq.get("notRun")!=[]:
  raise RuntimeError("sequence order or NOT RUN ledger mismatch")
 if [x.get("cold",{}).get("elapsedMs") for x in seq["rows"]]!=[99209,99041,98243]:
  raise RuntimeError("cold values differ from root interpretation")
 if any(x.get("runnerOutcome")!="PASS" or x.get("cleanup",{}).get("status")!="PASS" for x in seq["rows"]):
  raise RuntimeError("row or cleanup failed")
 if len(seq.get("pairs",[]))!=2 or any(x.get("status")!="PASS" for x in seq["pairs"]):
  raise RuntimeError("both parity comparisons must pass")
 if interp.get("status")!="PRIVATE_CENSUS_VALIDATED_NOT_OPTIMIZATION_ACCEPTANCE":
  raise RuntimeError("root interpretation status mismatch")
 if interp.get("sequenceSha256")!=SEQUENCE_SHA or interp.get("rawReportSha256")!=seq["rows"][1]["reportSha256"]:
  raise RuntimeError("interpretation is not bound to final sequence/ON report")
 if interp.get("normalAcceptance")!="BLOCKED" or not interp.get("decision","").lower().startswith("no static original-matrix partition"):
  raise RuntimeError("interpretation exceeds authorized scope")
 if gate.get("status")!="PASS_FOCUSED_PRIVATE_CENSUS_GATES_NOT_ACCEPTANCE":
  raise RuntimeError("preflight status mismatch")
 head=subprocess.run(["git","-C",str(repo),"rev-parse","--verify","HEAD"],stdout=subprocess.PIPE,check=True).stdout.decode().strip()
 p=Packet(repo,scratch,out,head)
 if p.resume:
  p.copytext(p.resume_failure,"provenance/packaging-attempts/attempt-1-failure.txt")
  p.copytext(p.resume_state,"provenance/packaging-attempts/attempt-1-partial-state.json")

 # Frozen plan, binding, final sequence, independent interpretation, and root preflight.
 p.copytext(planp,"plan/measurement-plan.json",PLAN_SHA)
 p.copytext(bindp,"plan/execution-binding.json",BINDING_SHA)
 p.copytext(seqp,"receipts/sequence-result.json",SEQUENCE_SHA)
 p.copytext(intp,"interpretation/root-interpretation.json",INTERPRETATION_SHA)
 p.copytext(gatep,"receipts/root-gate-preflight.json")
 # Final and retained failed focused gates; files are copied individually, with logs sanitized.
 gatefiles=[
 "lu-support-census-r4-native7.json","lu-support-census-r4-native7.log","lu-support-census-r4-native7-receipt.txt",
 "lu-support-census-r2-gwt5.json","lu-support-census-r2-gwt5.log",
 "lu-support-census-r3-gwt5.json","lu-support-census-r3-gwt5.log",
 "lu-support-census-r3-native7.json","lu-support-census-r3-native7.log",
 "lu-support-census-r3-canaries-spec.json","lu-support-census-r3-canaries-summary.json","lu-support-census-r3-canaries.log",
 "lu-support-census-r3-a07-reader.log","lu-support-census-r3-driver-before-marker-repair.ps1",
 "lu-support-census-sequence-root-source-canary.json","lu-support-census-metadata-r2-root-selftest.json",
 "lu-sampler-metadata-canaries.json","native-lu-support-census.ps1",
]
 for name in gatefiles:
  f=scratch/name
  if f.is_file():
   if name.endswith(".ps1"): p.inspect(f,"procedures/history/"+name)
   else: p.copytext(f,"receipts/gates/"+name)
 # Retain the stale-manifest rejection and corrected isolated smoke receipt.
 for folder, label in (("lu-support-census-r2-collector-smoke","stale-manifest"),
                       ("lu-support-census-r2-collector-smoke-r2","corrected")):
  d=scratch/folder
  for name in ("result.json","run.log","compile.log"):
   f=d/name
   if f.is_file() and f.stat().st_size<=16384:
    p.copytext(f,"receipts/collector-smoke/"+label+"/"+name)
  # The frozen rows carry raw reports, input manifests, process cleanup, strict readers and parity.
 for label,row in zip(LABELS,seq["rows"]): copy_run(p,scratch,label,row)
 for name in ("census-r4-pair-1.log","census-r4-pair-2.log"):
  p.copytext(needfile(scratch/name),"receipts/pair-validation/"+name)
 strict=sorted(x for x in scratch.glob("lu-support-census-r4-*-strict-*") if x.is_file())
 for f in strict: p.copytext(f,"receipts/strict-canaries/"+f.name)

  # Preserve the corrected compiled R3 canary outcome with reports and lifecycle receipts.
 canary=scratch/"lu-support-census-r3-canaries"; cres=readj(needfile(canary/"result.json"))
 if cres.get("outcome")!="PASS": raise RuntimeError("R3 compiled canary did not pass")
 for name in RUN_META:
  f=canary/name
  if f.is_file(): p.copytext(f,"canaries/r3/"+name)
 for f in sorted((canary/"cases").iterdir()):
  if f.name.endswith(".report.json"):
   item=next((x for x in cres.get("cases",[]) if x.get("reportFile")=="cases/"+f.name),None)
   if item is None: raise RuntimeError("unbound canary report "+f.name)
   p.gziptext(f,"canaries/r3/cases/"+f.name+".gz",item.get("reportSha256"))
  else: p.copytext(f,"canaries/r3/cases/"+f.name)

  # Source audit chain, historical draft/failure, actual-LU test, and exact R4 source overlays.
 for name in ("source-audit.json","source-audit-r2.json","source-audit-r3.json","source-audit-r4.json"):
  f=needfile(auditroot/name); p.gziptext(f,"source/audits/"+name+".gz")
 wired=scratch/"scratch/lu-support-census-r2-wired-draft"
 for name in ("README.md","source-manifest.json","exact-diff.patch.gz"):
  f=needfile(wired/name)
  if name=="source-manifest.json" and sha(f.read_bytes())!=R2_MANIFEST_SHA: raise RuntimeError("R2 manifest hash mismatch")
  if name=="exact-diff.patch.gz":
   raw=needfile(wired/"exact-diff.patch").read_bytes()
   if gzip.decompress(f.read_bytes())!=raw: raise RuntimeError("R2 diff gzip is not byte-exact")
   p.original_gzip(f,"source/history/r2-wired-draft/exact-diff.patch.gz",sha(raw))
  else: p.copytext(f,"source/history/r2-wired-draft/"+name)
 for rel,expected in (
  ("src/com/lushprojects/circuitjs1/client/CirSim.java",None),
  ("src/com/lushprojects/circuitjs1/client/Q30CoordinatorQualificationVerifier.java",None),
  ("src/com/lushprojects/circuitjs1/client/Q30LuSupportCensus.java",R2_COLLECTOR_SHA),
  ("tests/contracts/Q30LuSupportCensusContractTest.java",None)):
  p.overlay(needfile(wired/rel),"source/history/r2-wired-draft/overlays/"+rel.replace("/","__")+".gz",expected)
 actual=scratch/"scratch/lu-support-census-r2-actual-lu-test"
 am=needfile(actual/"source-manifest.json"); amj=readj(am); testpath=needfile(actual/amj["test"]["path"])
 p.copytext(needfile(actual/"README.md"),"source/history/actual-lu-test/README.md")
 p.copytext(am,"source/history/actual-lu-test/source-manifest.json")
 p.overlay(testpath,"source/history/actual-lu-test/actual-lu-test.java.gz",ACTUAL_LU_TEST_SHA)
 prior=actual/"prior-root-review"
 for f in sorted(prior.rglob("*")):
  if f.is_file() and f.suffix.lower() in {".java",".md",".json"}:
   p.copytext(f,"source/history/actual-lu-test/"+f.relative_to(actual).as_posix())
 clone=scratch/"scratch/lu-support-census-r2-pre-gwt-clone-fix"
 p.overlay(needfile(clone/"Q30LuSupportCensus.java"),"source/history/pre-gwt-clone-fix/Q30LuSupportCensus.java.gz")
 p.copytext(needfile(clone/"source-manifest.json"),"source/history/pre-gwt-clone-fix/source-manifest.json")
 candidate=auditroot/"candidate"; overlayrows=[]
 for rel in OVERLAYS:
  f=needfile(candidate/rel); dest="source/r4-overlays/"+rel.replace("/","__")+".gz"
  before=len(p.rows); p.overlay(f,dest)
  overlayrows.append({"path":rel,"storedAt":dest,"bytes":p.rows[before]["payloadBytes"],
                      "sha256":p.rows[before]["payloadSha256"],"transform":p.rows[before]["transform"]})

  # Reusable read-only procedures, metadata-reader revisions, source canary, and bounded reviews.
 meta=scratch/"scratch/lu-support-census-metadata-r1-preserved"
 for name in ("LEDGER.md","lu-support-census-metadata-validator.md","lu-support-census-metadata-validator.py"):
  f=needfile(meta/name)
  if name.endswith(".py"): p.inspect(f,"source/history/metadata-r1-preserved/"+name)
  else: p.inspect(f,"source/history/metadata-r1-preserved/"+name)
 p.copytext(needfile(scratch/"lu-support-census-metadata-validator.py"),
  "procedures/inspection/lu-support-census-metadata-validator.py",METADATA_VALIDATOR_SHA)
 p.copytext(needfile(scratch/"lu-support-census-metadata-validator.md"),
  "procedures/inspection/lu-support-census-metadata-validator.md")
 for name in ("prepare_lu_support_census_sequence_root.py","run_lu_support_census_sequence_root.py",
  "run_lu_support_census_timing.py","validate_lu_support_census_pair_root.py",
  "finalize_census_sequence_root.py","canary_census_sequence_helpers_root.py",
  "check_lu_sampler_metadata_canaries.py","native-lu-support-census.ps1"):
  f=scratch/name
  if f.is_file(): p.inspect(f,"procedures/inspection/"+name)
 worker=scratch/"scratch/lu-support-census-sequence-worker-handoff"
 for name in ("prepare_lu_support_census_sequence_root.py","run_lu_support_census_timing.py",
  "validate_lu_support_census_pair_root.py","receipt.json"):
  f=worker/name
  if f.is_file(): p.inspect(f,"source/history/interrupted-worker-handoff/"+name)
 for name in ("lu-support-census-r1-independent-review.md","lu-support-census-r2-independent-review.md",
  "lu-support-census-r2-root-finalization-review.md","lu-support-census-metadata-independent-review.md",
  "lu-support-next-step-constraints.md","live-lu-support-owner-contract.md","generic-wire-observation-contract.md"):
  f=scratch/"scratch"/name
  if f.is_file(): p.inspect(f,"reviews/"+name)
 script=Path(__file__).resolve()
 if script.is_file(): p.inspect(script,"procedures/inspection/package_lu_support_census_r4.py")

  # Carry only the small committed lineage records and exact overlays required to reconstruct the parent.
 provraw=gitblob(repo,head,"docs/task-evidence/Q30/pivot-lower-fusion-r2/source/provenance.json")
 prov=json.loads(provraw); basehead=prov.get("baseHead")
 if not basehead or prov.get("measuredR2Candidate",{}).get("sourceAuditSha256")!="0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197":
  raise RuntimeError("committed pivot-lower-fusion R2 lineage mismatch")
 for rel in FUSION_R2_REFS:
  source_rel="docs/task-evidence/Q30/pivot-lower-fusion-r2/"+rel
  target="source/parent/pivot-lower-fusion-r2/"+rel.replace("/","__")
  if rel.startswith("source/measured-r2-overlays/"):
   raw=gitblob(repo,head,source_rel); payload=gzip.decompress(raw)
   if private(payload) or secret(payload): raise RuntimeError("committed gzip payload has private path/secret " + rel)
   p.put(target,raw,src=repo/source_rel,source=raw,payload=payload,
         transform="exact-original-gzip-validated-payload")
  else: p.repo_copy(source_rel,target)
 for patchrel in (
  "source/deltas/parent-candidate-to-measured-r2/scripts__verify-current-contracts.ps1.patch",
  "source/deltas/parent-candidate-to-measured-r2/src__com__lushprojects__circuitjs1__client__CirSim.java.patch",
  "source/deltas/parent-candidate-to-measured-r2/tests__contracts__Q30LuPivotCollectionContractTest.java.patch",
 ):
  p.repo_gzip("docs/task-evidence/Q30/pivot-lower-fusion-r2/"+patchrel,
   "source/parent/pivot-lower-fusion-r2/"+patchrel.replace("/","__")+".gz")
 pair=gitblob(repo,head,"docs/task-evidence/Q30/pivot-lower-fusion-r2/source/parent/source-pair-audit.json")
 stored=gitblob(repo,head,"docs/task-evidence/Q30/pivot-lower-fusion-r2/provenance/scratch/pivot-lower-fusion-r2-preparation/source-audit.json")
 storedsha=sha(stored)
 if storedsha=="0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197":
  raise RuntimeError("parent source-audit identities unexpectedly collapsed")
 p.repo_copy("docs/task-evidence/Q30/pivot-lower-fusion-r2/provenance/scratch/pivot-lower-fusion-r2-preparation/source-audit.json",
  "source/parent/pivot-lower-fusion-r2/stored-parent-source-audit.json")
 lineage={
  "schema":"tsj-q30-lu-support-census-r4-source-lineage/1",
  "pinnedEvidenceCommit":head,
  "parentFusionR2Packet":"docs/task-evidence/Q30/pivot-lower-fusion-r2",
  "parentFusionR2BaseHead":basehead,
  "parentMeasuredSourceAuditSha256":prov["measuredR2Candidate"]["sourceAuditSha256"],
  "parentStoredSourceAuditSha256":storedsha,
  "parentStoredSourcePairAuditSha256":sha(pair),
  "censusR2ParentSourceAuditSha256":"0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197",
  "censusR2StoredSourceAuditSha256":sha((auditroot/"source-audit.json").read_bytes()),
  "censusR2SourceAuditSha256":sha((auditroot/"source-audit-r2.json").read_bytes()),
  "censusR3SourceAuditSha256":sha((auditroot/"source-audit-r3.json").read_bytes()),
  "censusR4SourceAuditSha256":R4_AUDIT_SHA,
  "runtimeInputCount":1526,
  "runtimeSha256":"f7ea79156a7f313b4221e71870fc8cd0ff7fb7f48edd84151ad954e1461fdf15",
  "runtimeUnchangedFromGWTAndCanaries":True,
  "exactR4SourceOverlays":overlayrows,
  "sourceAuditR2R3R4StoredSeparately":True,
  "fullCandidateTreeGeneratedWarCacheAndBrowserProfileCopied":False,
 }
 readme=make_readme(interp,gate)
 digest,count=p.finish(readme,lineage)
 print(json.dumps({"status":"PACKAGED_PENDING_ROOT_PACKET_AUDIT","output":str(out),
  "artifactCount":count,"inventorySha256":digest},indent=2))

if __name__=="__main__":
 try: main()
 except Exception as exc:
  print("PACKET BUILD FAILED: "+str(exc),file=sys.stderr)
  raise
