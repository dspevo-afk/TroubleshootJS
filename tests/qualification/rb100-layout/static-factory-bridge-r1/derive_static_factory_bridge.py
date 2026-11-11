"""Pure exact current input-owner transition; no I/O or producer entrypoint."""
import hashlib
SOURCE_CAP=4*1024*1024
FRAGMENT_CAP=64*1024
SOURCE_BYTES=22823
SOURCE_SHA='d0e42cf2fd93657e2a086e4fff4e8b0111937b7d5c22899fac1107c1bf3b12e2'
FRAGMENT_BYTES=3693
FRAGMENT_SHA='eccbf026d9d9e449239206580ca7eddaefb1e6e2c2a39ec28b5053e23718deb4'
OLD_BYTES=2120
OLD_SHA='f7a1aa60b91fa0d52bb6a9a1e6b62f1dcded5bda7049cc0fc9972a6fe8c411bb'
START=b'    static CohortTerminalTelemetryStudy.SetupResult read('

class SourceError(ValueError):pass

def checked(raw,size,pin,cap,code):
    if type(raw) is not bytes or len(raw)!=size or len(raw)>cap:raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def old_clause(source):
    if source.count(START)!=1:raise SourceError('READ_ANCHOR')
    at=source.index(START)
    return checked(source[at:at+OLD_BYTES],OLD_BYTES,OLD_SHA,FRAGMENT_CAP,'OLD_READ')

def derive(source,fragment):
    source=checked(source,SOURCE_BYTES,SOURCE_SHA,SOURCE_CAP,'INPUT_SOURCE')
    fragment=checked(fragment,FRAGMENT_BYTES,FRAGMENT_SHA,FRAGMENT_CAP,'FRAGMENT_SOURCE')
    out=source.replace(old_clause(source),fragment+b'\n',1) # Restore owned method separator.
    if len(out)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return out
