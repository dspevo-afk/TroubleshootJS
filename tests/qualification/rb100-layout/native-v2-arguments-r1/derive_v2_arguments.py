"""Pure checked migration of the current held native Study argument owner."""
import hashlib

SOURCE_CAP=4*1024*1024
FRAGMENT_CAP=64*1024
STUDY_BYTES=236974
STUDY_SHA='4e0563854a2282b10d5804da9e9843814ba8363b23400eea63a2459ca14de680'
FRAGMENT_BYTES=2397
FRAGMENT_SHA='b157b4baaf6edceea12764e907487706bd4971879262bf623c66b45988a170ff'
ARGUMENTS_START=b'    static final class Arguments {\n'
OLD_ARGUMENTS_BYTES=1735
OLD_ARGUMENTS_SHA='10b5d7457d3a4daf84c498aeef4a0d00ed52c06020488004cfb9d9ea259efa10'
INPUTS_START=b'        Object inputs(Arguments args) throws Exception {\n'
OLD_INPUTS_BYTES=320
OLD_INPUTS_SHA='f06007f2f09e6d882ac47a280aa588977a7c4b40ae151a40947385a96400456f'
OLD_MANIFEST_READ=b'r7ReadObject(m,args,"manifestSha")'
NEW_MANIFEST_READ=b'r7ReadObject(m,args,"sourceManifestSha256")'


class SourceError(ValueError):pass


def checked(raw,size,pin,cap,code):
    if type(raw) is not bytes or len(raw)!=size or len(raw)>cap:
        raise SourceError(code)
    if hashlib.sha256(raw).hexdigest()!=pin:raise SourceError(code)
    return raw


def clause(source,start,size,pin,code):
    if source.count(start)!=1:raise SourceError(code)
    offset=source.index(start)
    raw=source[offset:offset+size]
    checked(raw,size,pin,FRAGMENT_CAP,code)
    return raw


def derive(study,fragment):
    """Return Source bytes only; no files, classes, factory, graph or entrypoint."""
    study=checked(study,STUDY_BYTES,STUDY_SHA,SOURCE_CAP,'STUDY_SOURCE')
    fragment=checked(fragment,FRAGMENT_BYTES,FRAGMENT_SHA,FRAGMENT_CAP,'FRAGMENT_SOURCE')
    old_arguments=clause(study,ARGUMENTS_START,OLD_ARGUMENTS_BYTES,OLD_ARGUMENTS_SHA,'ARGUMENTS_CLAUSE')
    old_inputs=clause(study,INPUTS_START,OLD_INPUTS_BYTES,OLD_INPUTS_SHA,'INPUTS_CLAUSE')
    if study.count(OLD_MANIFEST_READ)!=1 or NEW_MANIFEST_READ in study:
        raise SourceError('MANIFEST_READ')
    legacy_start=b'    @SuppressWarnings("unchecked")\n    static SetupResult readInput('
    legacy=clause(study,legacy_start,1710,'5435d36f3731ad9bf03496147dd5ae0d7874d32e001846d27da681f621f54db8','LEGACY_INPUT_CLAUSE')
    result=study.replace(old_arguments,fragment,1).replace(old_inputs,b'',1)
    result=result.replace(legacy,b'',1)
    result=result.replace(OLD_MANIFEST_READ,NEW_MANIFEST_READ,1)
    if len(result)>SOURCE_CAP:raise SourceError('OUTPUT_CAP')
    return result
