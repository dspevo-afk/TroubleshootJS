"""Pure checked migration of the current native carrier wrapper; no I/O."""
import hashlib
SOURCE_CAP=4*1024*1024
FRAGMENT_CAP=64*1024
STUDY_BYTES=236157
STUDY_SHA='19f74da6cf1fa210dc0204977935d92b7456f8de3e30dabc5481fc096d174a18'
FRAGMENT_BYTES=1142
FRAGMENT_SHA='28184dd2eadf4c715d9f2e5010fdbb63673ef8575ff34bd1a32e3a78a59390cc'
OLD_BYTES=542
OLD_SHA='63501572299d929e602823b253b46b3ef6e0529c2cdb791b4a13e55503b449f8'
START=b'        /** Runtime identity/output carrier only; no capture or expectation-map conversion. */\n'

class SourceError(ValueError):pass

def checked(raw,size,pin,cap,code):
    if type(raw) is not bytes or len(raw)!=size or len(raw)>cap:raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def old_clause(study):
    if study.count(START)!=1:raise SourceError('CARRIER_ANCHOR')
    at=study.index(START)
    return checked(study[at:at+OLD_BYTES],OLD_BYTES,OLD_SHA,FRAGMENT_CAP,'OLD_CARRIER')

def derive(study,fragment):
    study=checked(study,STUDY_BYTES,STUDY_SHA,SOURCE_CAP,'STUDY_SOURCE')
    fragment=checked(fragment,FRAGMENT_BYTES,FRAGMENT_SHA,FRAGMENT_CAP,'FRAGMENT_SOURCE')
    out=study.replace(old_clause(study),fragment,1)
    if len(out)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return out
