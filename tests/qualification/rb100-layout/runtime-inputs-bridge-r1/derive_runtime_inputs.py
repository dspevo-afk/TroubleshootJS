"""Pure checked insertion into the current native Parent adapter; no I/O."""
import hashlib
SOURCE_CAP=4*1024*1024
FRAGMENT_CAP=64*1024
STUDY_BYTES=235615
STUDY_SHA='3fe46f10773b5f04c1c7a4b89f2390f21681658568c8ce0de4f32faee1193890'
FRAGMENT_BYTES=542
FRAGMENT_SHA='63501572299d929e602823b253b46b3ef6e0529c2cdb791b4a13e55503b449f8'
ANCHOR=b'        Object meter(long started) throws Exception {\n'

class SourceError(ValueError):pass

def checked(raw,size,pin,cap,code):
    if type(raw) is not bytes or len(raw)!=size or len(raw)>cap:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw

def derive(study,fragment):
    study=checked(study,STUDY_BYTES,STUDY_SHA,SOURCE_CAP,'STUDY_SOURCE')
    fragment=checked(fragment,FRAGMENT_BYTES,FRAGMENT_SHA,FRAGMENT_CAP,'FRAGMENT_SOURCE')
    if study.count(ANCHOR)!=1 or b'Object runtimeInputs(' in study:
        raise SourceError('PARENT_ANCHOR')
    result=study.replace(ANCHOR,fragment+ANCHOR,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
