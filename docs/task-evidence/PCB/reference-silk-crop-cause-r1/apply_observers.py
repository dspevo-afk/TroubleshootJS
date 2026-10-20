from pathlib import Path
import hashlib, sys

ROOT=Path(__file__).resolve().parents[4]
PACKET=Path(__file__).resolve().parent
REF=ROOT/'docs/task-evidence/PCB/reference-lead-clearance-r1'
GEN='src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java'
TEST='tests/contracts/PcbFunctionalGroupingContractTest.java'
GEN_ANCHOR=(b'        edgeMargin=accessEdgeMargin(board.getPlacementConstraints(),\n'
            b'            plan.footprints,occupied,edgeMargin);\n')
GEN_OBSERVER=(b'        System.out.println("SILKCROP_ROW_BEGIN;candidate=" + plan.candidate +\n'
              b'                ";variation=" + variationMode + ";occupied=" + occupied.x + "," +\n'
              b'                occupied.y + "," + occupied.width + "," + occupied.height +\n'
              b'                ";edgeMargin=" + edgeMargin + ";fingerprint=" +\n'
              b'                layout.geometryFingerprint() + ";SILKCROP_ROW_END");\n')
TEST_ANCHOR=b'    static PcbBoardLayout measure(long seed,int index,int version) {\r\n'
TEST_OBSERVER=(b'        System.out.println("SILKCROP_BEGIN;seed=" + seed + ";index=" + index +\r\n'
               b'            ";version=" + version);\r\n')


def sha(data): return hashlib.sha256(data).hexdigest()
def observed(data, anchor, insertion, name):
    if data.count(anchor)!=1: raise RuntimeError(f'{name}: expected one contextual insertion point, found {data.count(anchor)}')
    if b'SILKCROP_' in data: raise RuntimeError(f'{name}: observer already present in archive')
    return data.replace(anchor,anchor+insertion,1)
def expected_sources(phase):
    if phase not in ('baseline','candidate'): raise ValueError(phase)
    source=REF/('accepted-source' if phase=='baseline' else 'final-source')
    gen=observed((source/GEN).read_bytes(),GEN_ANCHOR,GEN_OBSERVER,GEN)
    test=observed((REF/'final-source'/TEST).read_bytes(),TEST_ANCHOR,TEST_OBSERVER,TEST)
    return gen,test

def main():
    if len(sys.argv)!=3 or sys.argv[1] not in ('dry-run','install','check'):
        raise SystemExit('usage: apply_observers.py dry-run|install|check baseline|candidate')
    mode,phase=sys.argv[1:]
    gen,test=expected_sources(phase)
    gp=ROOT/GEN; tp=ROOT/TEST
    if mode=='install':
        gp.write_bytes(gen); tp.write_bytes(test)
    if mode=='check' or mode=='install':
        if gp.read_bytes()!=gen or tp.read_bytes()!=test:
            raise SystemExit('FAIL phase sources are not exact archive plus insertion-only observers')
    print(f'{mode.upper()} phase={phase} generatorSha256={sha(gen)} testSha256={sha(test)}')
if __name__=='__main__': main()