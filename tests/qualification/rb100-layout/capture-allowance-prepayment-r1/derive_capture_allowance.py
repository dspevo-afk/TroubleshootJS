"""Pure checked allowance-owner transition; no I/O or producer entrypoint."""
import hashlib
SOURCE_CAP=4*1024*1024
FRAGMENT_CAP=64*1024
STUDY_BYTES=236757
STUDY_SHA='e20499b59ccedcdbaffd9e7bc1a2fdf5d3e0a4f96ccaed786656e701a5c5c103'
FRAGMENT_BYTES=7684
FRAGMENT_SHA='4c4bba99dfa3161389ea0610f2d86ad24b363aace48ee2fe4be9df0eaed621c0'
OLD_BYTES=4503
OLD_SHA='81849fbaf7cd978e8cf3ff9282a673fd86e3e752d3a89c616c141f66e152fe4d'
START=b'    static int beginCaptureAllowance('

class SourceError(ValueError):pass

def checked(raw,size,pin,cap,code):
    if type(raw) is not bytes or len(raw)!=size or len(raw)>cap:raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def old_clause(study):
    if study.count(START)!=1:raise SourceError('ALLOWANCE_ANCHOR')
    at=study.index(START)
    return checked(study[at:at+OLD_BYTES],OLD_BYTES,OLD_SHA,FRAGMENT_CAP,'OLD_ALLOWANCE')

def derive(study,fragment):
    study=checked(study,STUDY_BYTES,STUDY_SHA,SOURCE_CAP,'STUDY_SOURCE')
    fragment=checked(fragment,FRAGMENT_BYTES,FRAGMENT_SHA,FRAGMENT_CAP,'FRAGMENT_SOURCE')
    out=study.replace(old_clause(study),fragment,1)
    if len(out)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return out
