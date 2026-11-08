"""Derive one byte-pinned native-only P07 selected-head observation overlay."""
from __future__ import annotations
import hashlib,json,os,stat,sys,types
from pathlib import Path

PARENT_SHA256='c382f3bc08f8ad65b3501dad57c927dd5c1f9f1c04914ac79edb772f4b4f4e3e'
IO_HELPER_SHA256='df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923'
MAX_SOURCE_BYTES=4*1024*1024
SCHEMA='tsj-native-branch-limit-selection-overlay-v1'

class OverlayError(ValueError):
    def __init__(self,code):super().__init__(code);self.code=code
def need(condition,code):
    if not condition:raise OverlayError(code)
def sha(raw):return hashlib.sha256(raw).hexdigest()
def replace_once(raw,before,after):
    need(type(raw) is bytes and type(before) is bytes and type(after) is bytes,'SPLICE_TYPES')
    need(before and raw.count(before)==1,'SPLICE_COUNT')
    return raw.replace(before,after,1)

MARKER=b'''    /** Native-only selected-head facts at the local branch expansion cap. */
    interface BranchLimitSelectionDiagnostics extends TerminalOnlyDiagnostics {
        void onBranchLimitSelected(BranchSnapshot snapshot,BranchLimitSelection selection);
    }
'''
SELECTION=b'''    /** Scalars from the identity-live node polled before local-cap termination. */
    static final class BranchLimitSelection {
        final int branchOrdinal,cell,layer,direction,transitions,cost,storedHeuristic,
            serial,key,storedPriority;
        private BranchLimitSelection(Node selected,int branchOrdinal) {
            this.branchOrdinal=branchOrdinal; cell=selected.cell; layer=selected.layer;
            direction=selected.direction; transitions=selected.transitions;
            cost=selected.cost; storedHeuristic=selected.heuristic;
            serial=selected.serial; key=selected.key;
            storedPriority=selected.cost+selected.heuristic;
        }
    }
'''
DETACHED=b'''    private static final BranchLimitSelectionDiagnostics DETACHED_BRANCH_LIMIT_SELECTION_DIAGNOSTICS=
            new BranchLimitSelectionDiagnostics() {
        public void onTerminalBranch(BranchSnapshot snapshot) { }
        public void onBranchLimitSelected(BranchSnapshot snapshot,BranchLimitSelection selection) { }
    };
'''

def derive(source):
    need(type(source) is bytes and 0<len(source)<=MAX_SOURCE_BYTES,'SOURCE_SIZE')
    need(sha(source)==PARENT_SHA256,'SOURCE_HASH')
    need(b'\r' not in source,'PINNED_SOURCE_LINE_ENDINGS')
    result=source;changes=[]
    def edit(name,before,after):
        nonlocal result
        result=replace_once(result,before,after);changes.append(name)
    before=b'    interface TerminalOnlyDiagnostics extends Diagnostics { }\n'
    edit('OPT_IN_MARKER',before,before+MARKER)
    before=b'    /** Immutable scalars captured once when one real branch terminates. */\n'
    edit('PRIMITIVE_SELECTION_DTO',before,SELECTION+before)
    before=b'''    private static final TerminalOnlyDiagnostics DETACHED_TERMINAL_ONLY_DIAGNOSTICS=new TerminalOnlyDiagnostics() {
        public void onTerminalBranch(BranchSnapshot snapshot) { }
    };
'''
    edit('DETACHED_NOOP_MARKER',before,before+DETACHED)
    before=b'''            diagnostics=source.diagnostics==null?null:
                source.diagnostics instanceof TerminalOnlyDiagnostics?
                    DETACHED_TERMINAL_ONLY_DIAGNOSTICS:DETACHED_DIAGNOSTICS;'''
    after=b'''            diagnostics=source.diagnostics==null?null:
                source.diagnostics instanceof BranchLimitSelectionDiagnostics?
                    DETACHED_BRANCH_LIMIT_SELECTION_DIAGNOSTICS:
                source.diagnostics instanceof TerminalOnlyDiagnostics?
                    DETACHED_TERMINAL_ONLY_DIAGNOSTICS:DETACHED_DIAGNOSTICS;'''
    edit('DETACHED_MODE_BEFORE_SUPERTYPE',before,after)
    before=b'''        private void emitBranchSnapshot(BranchSnapshot snapshot) {
            if(diagnostics==null) return;
            try {
                diagnostics.onTerminalBranch(snapshot);'''
    after=b'''        private void emitBranchSnapshot(BranchSnapshot snapshot) {
            emitBranchSnapshot(snapshot,null);
        }
        private void emitBranchSnapshot(BranchSnapshot snapshot,BranchLimitSelection selection) {
            if(diagnostics==null) return;
            try {
                if(selection!=null && diagnostics instanceof BranchLimitSelectionDiagnostics)
                    ((BranchLimitSelectionDiagnostics)diagnostics).onBranchLimitSelected(snapshot,selection);
                else diagnostics.onTerminalBranch(snapshot);'''
    edit('SHARED_SESSION_DISPATCH_AND_ABORT',before,after)
    before=b'''            private boolean emitTerminal(String terminalReason) {
                if(terminalEmitted)'''
    after=b'''            private boolean emitTerminal(String terminalReason) {
                return emitTerminal(terminalReason,null);
            }
            private boolean emitTerminal(String terminalReason,Node selected) {
                if(terminalEmitted)'''
    edit('TERMINAL_LOCAL_NODE_PARAMETER',before,after)
    before=b'''                        relaxedDistanceBound==null?0:relaxedDistanceBound.getViaProbes(),
                        relaxedBoundRefreshes));'''
    after=b'''                        relaxedDistanceBound==null?0:relaxedDistanceBound.getViaProbes(),
                        relaxedBoundRefreshes),
                        selected!=null && "BRANCH_SEARCH_LIMIT".equals(terminalReason) &&
                            owner.diagnostics instanceof BranchLimitSelectionDiagnostics?
                            new BranchLimitSelection(selected,branchOrdinal):null);'''
    edit('CONSTANT_SIZE_OPT_IN_SELECTION',before,after)
    before=b'''                if(expanded>=MAX_BRANCH_EXPANSIONS) {
                    if(emitTerminal("BRANCH_SEARCH_LIMIT")) return;'''
    after=b'''                if(expanded>=MAX_BRANCH_EXPANSIONS) {
                    if(emitTerminal("BRANCH_SEARCH_LIMIT",current)) return;'''
    edit('ONLY_LOCAL_CAP_SUPPLIES_SELECTED_NODE',before,after)
    need(len(changes)==8 and len(result)<=MAX_SOURCE_BYTES,'DERIVED_SOURCE_SIZE')
    return result,changes

def load_io(path_value):
    path=Path(path_value)
    need(path.is_absolute(),'IO_HELPER_ABSOLUTE')
    try:
        for item in (path,*path.parents):
            info=item.lstat()
            need(not stat.S_ISLNK(info.st_mode) and not(getattr(info,'st_file_attributes',0)&0x400),'IO_HELPER_REPARSE')
        need(path.resolve(strict=True)==path,'IO_HELPER_CANONICAL')
        info=path.lstat();need(stat.S_ISREG(info.st_mode) and 0<info.st_size<=256*1024,'IO_HELPER_FILE')
        with path.open('rb') as stream:raw=stream.read(256*1024+1)
    except OverlayError:raise
    except (OSError,RuntimeError):raise OverlayError('IO_HELPER_READ') from None
    need(0<len(raw)<=256*1024 and sha(raw)==IO_HELPER_SHA256,'IO_HELPER_HASH')
    module=types.ModuleType('_pinned_branch_limit_overlay_io');module.__file__=str(path)
    exec(compile(raw,str(path),'exec'),module.__dict__)
    return module

def parse_args(argv):
    keys={'--source','--io-helper','--output-dir'}
    need(type(argv) in (list,tuple) and len(argv)==6,'CLI_ARGUMENTS')
    values={}
    for at in range(0,6,2):
        key,value=argv[at:at+2]
        need(type(key) is str and key in keys and key not in values and type(value) is str and value and '\0' not in value and not value.startswith('--'),'CLI_ARGUMENTS')
        values[key]=value
    need(set(values)==keys,'CLI_ARGUMENTS')
    return values

def generate(options):
    io=load_io(options['--io-helper'])
    output=io._checked_directory(options['--output-dir'],'OUTPUT_DIRECTORY')
    need(not io._directory_names(output,'OUTPUT_DIRECTORY'),'OUTPUT_NOT_EMPTY')
    original=io._read_regular(options['--source'],MAX_SOURCE_BYTES,'OWNER_SOURCE')
    derived,changes=derive(original)
    proof={'schema':SCHEMA,'status':'GENERATED_NATIVE_ONLY_NOT_COMPILED','parentSha256':sha(original),'parentBytes':len(original),'derivedSha256':sha(derived),'derivedBytes':len(derived),'ioHelperSha256':IO_HELPER_SHA256,'changes':changes,'selectionFields':['branchOrdinal','cell','layer','direction','transitions','cost','storedHeuristic','serial','key','storedPriority'],'observationMeaning':'IDENTITY_LIVE_HEAD_SELECTED_AND_POPPED_AT_LOCAL_CAP_BEFORE_EXPANSION_AND_REFRESH; NOT_MINIMUM_LEGAL_PATH_WITNESS','defaultBranchSnapshot':'UNCHANGED','productionOwner':'UNCHANGED_PRIVATE_NATIVE_OVERLAY_ONLY','authoredInvocations':0,'budgets':'UNCHANGED'}
    proof_raw=(json.dumps(proof,sort_keys=True,indent=2)+'\n').encode('ascii')
    files={'PcbLayerRoutingPrototype.java':derived,'SOURCE_PROOF.json':proof_raw}
    for name,raw in files.items():
        output=io._checked_directory(output,'OUTPUT_DIRECTORY')
        io._write_exclusive(output/name,raw,MAX_SOURCE_BYTES,'OUTPUT_FILE')
    output=io._checked_directory(output,'OUTPUT_DIRECTORY')
    names=io._directory_names(output,'OUTPUT_DIRECTORY')
    need(len(names)==2 and set(names)==set(files),'OUTPUT_CLOSURE')
    for name,raw in files.items():need(io._read_regular(output/name,MAX_SOURCE_BYTES,'OUTPUT_READBACK')==raw,'OUTPUT_READBACK_CHANGED')
    return proof

def main(argv=None):
    if not(sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write('BRANCH_SELECTION_OVERLAY_FAIL PYTHON_ISOLATION\n');return 2
    try:
        result=generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code=getattr(failure,'code','INTERNAL_ERROR')
        if type(code) is not str or not code or any(c not in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789' for c in code):code='INTERNAL_ERROR'
        sys.stderr.write('BRANCH_SELECTION_OVERLAY_FAIL '+code+'\n');return 2
    sys.stdout.write(json.dumps({'schema':SCHEMA,'status':result['status'],'derivedSha256':result['derivedSha256'],'files':2},separators=(',',':'))+'\n');return 0
if __name__=='__main__':raise SystemExit(main())
