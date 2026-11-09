"""Pinned Source-only inline attribution component; no assembly invocation."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import stat

CAP = 4 * 1024 * 1024
PINS = {
    "CURRENT_STUDY.java": "802d051413bdd0c5dde25bf327f29cbe18a633f82fa4d2f29d1c15996c1c1847",
    "Q_AUTHORED_STUDY.java": "d6485b0b0f9c518431d440fcb57e477143d50cae78302c29598d12aaa4601dbd",
    "OriginalAttribution.java.fragment": "76b5a463b8c38b9ad51e312d01fcd7475d77d29103eba825b7fdb938f49e90d1",
    "OriginalAttributionChecks.java.fragment": "06810d7758dc7cb18f78f7a48cecf8d095b27e596719a9a58018b0cef5e05a04",
}
MARKER = b"/*__ORIGINAL_ATTRIBUTION_INLINE__*/"

def sha(raw): return hashlib.sha256(raw).hexdigest()

def require(ok, code):
    if not ok: raise ValueError(code)

def pinned(raw, name):
    require(type(raw) is bytes and 0 < len(raw) <= CAP, "SOURCE_SIZE")
    require(sha(raw) == PINS[name], "SOURCE_SHA:" + name)

def derive(study, reference, inline, checks, template):
    for raw, name in ((study,"CURRENT_STUDY.java"), (reference,"Q_AUTHORED_STUDY.java"),
                      (inline,"OriginalAttribution.java.fragment"),
                      (checks,"OriginalAttributionChecks.java.fragment")):
        pinned(raw, name)
    require(type(template) is bytes and 0 < len(template) <= CAP, "TEMPLATE_SIZE")
    require(template.count(MARKER) == 1, "TEMPLATE_MARKER")
    require(b"public final class OriginalAttributionCanary" in template, "TEMPLATE_CLASS")
    require(b'    static Comparison assemble(Arguments args,long started) throws Exception {\n'
            b'        throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");\n'
            b'    }' in study, "ASSEMBLY_NOT_CLOSED")
    require(reference.count(b'm.charge(5);\n        final long originalWrapper=') == 1,
            "REFERENCE_BOUNDARY")
    reads = (b'field(m.meter, "units")', b'setup.units', b'field(original, "groupCount")',
             b'field(m.meter, "groups")', b'setup.groups')
    positions = [inline.index(b'm.charge(9L);')] + [inline.index(item) for item in reads]
    require(positions == sorted(positions), "PREPAY_READ_ORDER")
    require(all(inline.count(item) == 1 for item in reads), "READ_COUNT")
    require(inline.count(b'm.charge(') == 1 and b'm.copy(' not in inline
            and b'm.finish(' not in inline and b'new ' not in inline.replace(b'new java.io.IOException',b''),
            "NO_ADDITIONAL_STATE")
    require(inline.count(b'r7Subtract(') == 3 and inline.count(b'r5Add(') == 1, "ARITHMETIC_COUNT")
    require(b'r7BoundaryLong(' not in study and b'r7BoundaryInt(' not in study, "HELPERS_ALREADY_PRESENT")
    at = study.rfind(b'}')
    require(at > 0 and study[at+1:].strip() == b'', "CLASS_END")
    prefix, suffix = study[:at], study[at:]
    emitted = prefix + checks + b"\n" + suffix
    canary = template.replace(MARKER, inline.rstrip(b"\n"))
    require(len(emitted) <= CAP and len(canary) <= CAP, "OUTPUT_SIZE")
    proof = {"status":"PASS_SOURCE_DERIVATION_ONLY", "boundaryUnits":9,
             "sourceObservations":5, "checkedSubtractions":3, "checkedAdditions":1,
             "newCopiedEntries":0, "newResultDtos":0, "assemblyClosed":True,
             "preservedPrefixBytes":len(prefix), "preservedPrefixSha256":sha(prefix),
             "preservedSuffixBytes":len(suffix), "preservedSuffixSha256":sha(suffix),
             "inputs":[{"role":name,"bytes":len(raw),"sha256":sha(raw)} for raw,name in
                       ((study,"CURRENT_STUDY.java"),(reference,"Q_AUTHORED_STUDY.java"),
                        (inline,"OriginalAttribution.java.fragment"),
                        (checks,"OriginalAttributionChecks.java.fragment"),
                        (template,"OriginalAttributionCanary.java.in"))],
             "outputs":[{"role":"CohortTerminalTelemetryStudy.java","bytes":len(emitted),"sha256":sha(emitted)},
                        {"role":"OriginalAttributionCanary.java","bytes":len(canary),"sha256":sha(canary)}],
             "producerCalls":0,"genuineEnvelope":"UNSPENT_GATED"}
    return emitted, canary, proof

def snapshot(entry):
    # Reading may update access time. Identity/content metadata must stay fixed.
    return (entry.st_dev,entry.st_ino,entry.st_mode,entry.st_nlink,entry.st_size,
            entry.st_mtime_ns,entry.st_ctime_ns,getattr(entry,"st_file_attributes",0))

def read(path):
    path = Path(path)
    require(os.path.normcase(str(path.absolute())) == os.path.normcase(str(path.resolve(strict=True))), "SOURCE_PATH")
    for item in [path.absolute(), *path.absolute().parents]:
        entry = os.lstat(item)
        require(not stat.S_ISLNK(entry.st_mode) and not (getattr(entry,"st_file_attributes",0)&0x400), "SOURCE_REPARSE")
    before = path.stat()
    require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= CAP, "SOURCE_SIZE")
    raw = path.read_bytes()
    require(snapshot(path.stat()) == snapshot(before) and len(raw) == before.st_size, "SOURCE_CHANGED")
    return raw

def main(argv=None):
    parser=argparse.ArgumentParser()
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--component-root", required=True)
    parser.add_argument("--output-root", required=True)
    args=parser.parse_args(argv)
    source, component, output = map(Path,(args.source_root,args.component_root,args.output_root))
    results=derive(read(source/"CURRENT_STUDY.java"),read(source/"Q_AUTHORED_STUDY.java"),
                   read(component/"OriginalAttribution.java.fragment"),
                   read(component/"OriginalAttributionChecks.java.fragment"),
                   read(component/"OriginalAttributionCanary.java.in"))
    targets=(output/"CohortTerminalTelemetryStudy.java", output/"OriginalAttributionCanary.java",
             output/"ORIGINAL_ATTRIBUTION_SOURCE_PROOF_R1.json")
    require(all(not os.path.lexists(path) for path in targets), "OUTPUT_EXISTS")
    output.mkdir(parents=True, exist_ok=True)
    require(os.path.normcase(str(output.absolute())) == os.path.normcase(str(output.resolve(strict=True))), "OUTPUT_PATH")
    raws=results[:2]+((json.dumps(results[2],indent=2)+"\n").encode("utf-8"),)
    for path, raw in zip(targets,raws):
        with path.open("xb") as target:
            require(target.write(raw)==len(raw), "OUTPUT_WRITE")
    print(json.dumps({"status":"PASS_SOURCE_DERIVATION_ONLY", "boundaryUnits":9,
                      "outputs":results[2]["outputs"]}))

if __name__ == "__main__": main()
