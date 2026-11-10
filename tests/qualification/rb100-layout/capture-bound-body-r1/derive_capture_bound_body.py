"""Pure pinned capture-bound staging splice; no I/O or producer execution."""
import hashlib
SOURCE_CAP=4*1024*1024
FRAGMENT_CAP=64*1024
SOURCE_BYTES=12486
SOURCE_SHA='49f9930a4e1d8aed297236dd4ee742233c73cffcded9f495fd5815f58d11687b'
FRAGMENT_BYTES=1526
FRAGMENT_SHA='a83a7b32fee87302d4c053adced3647186cea50ffede201f725d5441c39a3994'
ANCHOR=b"    /** Install from this body's stable DTO views and the R7-owned cleanup scalar. */\n"

class SourceError(ValueError):pass

def checked(raw,n,pin,cap,code):
    if type(raw) is not bytes or len(raw)!=n or len(raw)>cap:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def derive(source,fragment):
    source=checked(source,SOURCE_BYTES,SOURCE_SHA,SOURCE_CAP,'METADATA_SOURCE')
    fragment=checked(fragment,FRAGMENT_BYTES,FRAGMENT_SHA,FRAGMENT_CAP,'BINDING_SOURCE')
    if source.count(ANCHOR)!=1 or b'captureBoundBody' in source:
        raise SourceError('METADATA_ANCHOR')
    result=source.replace(ANCHOR,fragment+b'\n'+ANCHOR,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
