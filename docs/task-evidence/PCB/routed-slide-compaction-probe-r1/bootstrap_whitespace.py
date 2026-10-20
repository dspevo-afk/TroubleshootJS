"""Expose the existing native whitespace measurement without extra player reports."""
import hashlib,json
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
read=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
COPY=Path(read(HERE/'local-state.json')['scratch'])
name='tests/contracts/PcbFunctionalGroupingContractTest.java'
raw=(ROOT/name).read_bytes();assert (COPY/name).read_bytes()==raw
anchor=b'    static int reportWhitespace(TroubleshootBoard board,PcbBoardLayout layout,long seed,int version) {\r\n'
assert raw.count(anchor)==1
bridge=b"""    // BEGIN COPY-ONLY selected slide quiet whitespace.
    private static boolean selectedSlideWhitespaceQuiet;
    static int measureSelectedSlideWhitespace(TroubleshootBoard board,PcbBoardLayout layout,
            long seed,int version) {
        boolean previous=selectedSlideWhitespaceQuiet;
        selectedSlideWhitespaceQuiet=true;
        try { return reportWhitespace(board,layout,seed,version); }
        finally { selectedSlideWhitespaceQuiet=previous; }
    }
    static boolean isSelectedSlideWhitespaceQuiet() { return selectedSlideWhitespaceQuiet; }
    // END COPY-ONLY selected slide quiet whitespace.
""".replace(b'\n',b'\r\n')
raw=raw.replace(anchor,bridge+anchor)
anchor=b'        System.out.println("PCB_WHITESPACE_CASE {\\"seed\\":\\""+seed+"\\",\\"version\\":"+version+\r\n'
assert raw.count(anchor)==1
before=b"""        // BEGIN COPY-ONLY selected slide quiet whitespace.
        if(!selectedSlideWhitespaceQuiet)
        // END COPY-ONLY selected slide quiet whitespace.
""".replace(b'\n',b'\r\n')
raw=raw.replace(anchor,before+anchor)
(COPY/name).write_bytes(raw)
p=HERE/'bootstrap-audit.json';a=read(p);a['overrides'].append(name)
a['quietWhitespace']='Existing full sampler; only diagnostic wrapper output suppressed with finally restoration'
p.write_text(json.dumps(a,indent=2)+'\n')
print('Prepared quiet native sampler adapter;4 overrides/1470 copy files before worker source')
