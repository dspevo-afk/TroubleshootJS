"""Pure current phased-capture Source transform; no I/O or producer."""
import hashlib
SOURCE_CAP=4*1024*1024
FRAGMENT_CAP=64*1024
SOURCE_BYTES=14013
SOURCE_SHA='54f6b815b34cb3d3bb28d04830f0e4b8ac9b5e6d6d341869c30596c2ff8b7b1d'
BRIDGE_BYTES=359
BRIDGE_SHA='d018f1a0c8bf1e2013863d6966b08641c4064340593ae8e7b511383b02aafdbc'
PHASES_BYTES=4525
PHASES_SHA='28c5ea8ebaaf71af5ca6cc214b0f0a8864386880fd4852882d3b43113788b1b2'
COHORT_START=b'    static Map<String,Object> cohortBeforeBody('
COHORT_END=b'    /**\n     * Explicit staging entry point:'
CAPTURE_START=b'    /**\n     * Capture-bound staging composition.'
CAPTURE_END=b"    /** Install from this body's stable DTO views and the R7-owned cleanup scalar. */\n"

class SourceError(ValueError):pass

def checked(raw,n,pin,cap,code):
    if type(raw) is not bytes or len(raw)!=n or len(raw)>cap:raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def spans(source):
    anchors=[COHORT_START,COHORT_END,CAPTURE_START,CAPTURE_END]
    if any(source.count(a)!=1 for a in anchors):raise SourceError('METADATA_ANCHORS')
    a,b,c,d=[source.index(x) for x in anchors]
    if not a<b<c<d:raise SourceError('METADATA_ORDER')
    return a,b,c,d

def derive(source,bridge,phases):
    source=checked(source,SOURCE_BYTES,SOURCE_SHA,SOURCE_CAP,'METADATA_SOURCE')
    bridge=checked(bridge,BRIDGE_BYTES,BRIDGE_SHA,FRAGMENT_CAP,'BRIDGE_SOURCE')
    phases=checked(phases,PHASES_BYTES,PHASES_SHA,FRAGMENT_CAP,'PHASES_SOURCE')
    a,b,c,d=spans(source)
    result=source[:a]+bridge+b'\n'+source[b:c]+phases+b'\n'+source[d:]
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
