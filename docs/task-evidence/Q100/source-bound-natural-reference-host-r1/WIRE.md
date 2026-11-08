# Source-bound reference host context V1

Host kernel extraction exact node names:
ReaderError,need,sha,exact_keys,is_int,is_digest,_pairs,_parse_int,_parse_float,
_reject_constant,_json_load,_is_compact,strict_compact_line,strict_document,
host_document,_lstat_regular,_lstat_directory,_read,_canonical,_same_path,
_same_host_path,_validate_owner_call,_validate_process_stderr,_is_finite_number,
_validate_class_tree.
Constants MAX_JSON,MAX_CLASS_BYTES,MAX_CLASS_COUNT,HEX,CLASS_SCHEMA,PARENT_SCHEMA,
ROOT_CALL_OWNER_SCHEMA,PS5_SHA256,P9_ROOT_CALL_SHA256,P9_PROCESS_MODULE_SHA256.
Rename every identifier token in this node/constant set with prefix RH_, leaving
other bytes intact; required imports hashlib,json,math,os,re,stat,Path. Reference
parent imports/pure nodes unchanged except retired CLI nodes; use one new main.

Context exact keys (order is not identity; raw SHA is externally supplied):
schema,runId,sourceManifestSha256,wireContractSha256,producerManifest,sourcePaths,
classesRoot,classInventory,classInventorySha256,inheritedClassesRoot,
inheritedClassInventory,inheritedClassInventorySha256,legacyInventory,
gwtUser,gwtDev,java,javac,powershell,rootCall,processModule,compileCall,sourceList,
emptySourcepath,nativeCall,fixtureSpec.
sourcePaths exact map owner,study,driver, in compiler order. Every path is a
canonical absolute string. No context-contained expected argv or process fields.
runId strict lowercase UUID. sourceManifestSha256 fixed df27c7750f61e4d4c959d60988a3381d64b6e94737da785e2b5ff76eaa811ada;
wireContractSha256 fixed dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6.
Fresh inventory count173/bytes563848 and exact rows equal manifest.classes.
Inherited inventory4288/19644833 and rows equal legacyInventory.entries;
legacy metadata SHA7fe14c6f95ae1c99ddfb3f81d975ff7f9ee2a6e1851b09e32d9ca8f0d47889f9.
Both sidecars canonical sorted compact ASCII JSON+LF with exact schema,files,count,
bytes keys using inherited RH_CLASS_SCHEMA; no manufactured routing/original data.

Independent classpath = classesRoot + semicolon + inheritedClassesRoot + semicolon
+ gwtUser + semicolon + gwtDev. Source list bytes are one double-quoted forward-
slash absolute source per line, in owner/study/driver order, LF terminated.
Compile expected executable javac and arguments:
-source,7,-target,7,-encoding,UTF-8,-classpath,<classpath>,-sourcepath,
<emptySourcepath>,-d,<classesRoot>,@<sourceList>; limit60000.
Compile stdout empty, exact inherited source1.7 bootstrap warning stderr.
Native expected executable java and arguments:
-Xmx1024m,-cp,<classpath>,com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy,
GENERIC_ROUTING_REFERENCE_V1,<runId>,<sourceManifestSha256>,<wireContractSha256>;
limit480000; stdout equals exact T canonical packet, stderr empty.
No expected argv may be copied from actual request/process.

Fixed tool SHA: javae71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce;
javac61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64;
powershell60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f;
RootCall28aab617e686d2a4eec074a46b2e2861f6fa7c008544b33f7b070f0c6d77ea1d;
processModulefb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c;
gwtUserd3721bcdd7c6855a524801212dd74548967d8fb65cbc212a20890732ff2c6948;
gwtDevc7321e367fc24e5c7ad97b74d1cf2980c45fbd7cbe1fd412f8a8ba128f685135.

Success summary schema tsj-source-bound-natural-reference-summary-v1,
status PASS_SOURCE_BOUND_NATURAL_REFERENCE_ONLY; fields contextSha256,runId,
sourceManifestSha256,wireContractSha256,references2,found1,noPath1,
inputSha256,inputBytes,sourceClassesToolsBound=true,compileAndNativeReceiptsBound=true,
ownedRootsAndLogsAbsent=true,exclusiveWriteReadback=true,consumedInputsRechecked=true,
newNativeProducerInvocations0,newAuthoredInvocations0,fullD=false,R7=false.
Summary must be canonical ASCII JSON+LF via existing canonical_line, stdout only
on PASS, bounded sanitized uppercase error on stderr/exit2 otherwise. Actual
process/wrapper times live in sanitized root evidence, cleanup duration null.
Accepted output exactly GENERIC_NATURAL_REFERENCES_V1.json, raw bytes unchanged;
output starts empty and final closure one file. If a later recheck fails, preserved
file remains an unaccepted failure artifact; no success summary/receipt may issue.
