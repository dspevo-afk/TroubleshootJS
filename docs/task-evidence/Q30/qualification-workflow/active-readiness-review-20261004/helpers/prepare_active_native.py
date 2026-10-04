import hashlib,json,re,shutil,uuid
from pathlib import Path
workspace=Path(__file__).resolve().parent
repo=Path(r'<repo>')
root=Path(r'<user-home>\AppData\Local\Temp')/('q30-active-power-'+uuid.uuid4().hex)
root.mkdir()
(root/'classes').mkdir(); (root/'empty-sourcepath').mkdir()
(workspace/'active-native-root.txt').write_text(str(root),encoding='utf-8')
snapshot=json.loads((Path((workspace/'remaining-native-root.txt').read_text(encoding='utf-8-sig').strip())/'source-snapshot.json').read_text(encoding='utf-8-sig'))
for row in snapshot['files']:
 raw=(repo/row['path']).read_bytes()
 assert len(raw)==row['size'] and hashlib.sha256(raw).hexdigest()==row['sha256'], row['path']
shutil.copyfile(Path((workspace/'remaining-native-root.txt').read_text(encoding='utf-8-sig').strip())/'source-snapshot.json',root/'source-snapshot.json')
client=repo/'src/com/lushprojects/circuitjs1/client'
source=(client/'CirSim.java').read_text(encoding='utf-8')
pattern=r'public static native void console\(String text\)\s*/\*\-\{\s*console\.log\(text\);\s*\}\-\*/;'
assert len(re.findall(pattern,source))==1
(root/'CirSim.java').write_text(re.sub(pattern,'public static void console(String text) { System.err.println(text); }',source,count=1),encoding='utf-8',newline='')
(root/'PhysicalSpecificationDeveloperVerifier.java').write_text('package com.lushprojects.circuitjs1.client; final class PhysicalSpecificationDeveloperVerifier { static void verify(CirSim sim) { throw new UnsupportedOperationException("Task35 verifier requires the separate GWT production gate"); } }\n',encoding='utf-8')
shutil.copyfile(workspace/'Q30ActivePowerDiagnostic.java',root/'Q30ActivePowerDiagnostic.java')
sources=sorted(p for p in client.glob('*.java') if p.name not in ('CirSim.java','PhysicalSpecificationDeveloperVerifier.java'))
sources += [root/'CirSim.java',root/'PhysicalSpecificationDeveloperVerifier.java',repo/'tests/contracts/Q30ServiceFlowContractTest.java',root/'Q30ActivePowerDiagnostic.java']
(root/'sources.txt').write_text('\n'.join('"'+p.as_posix()+'"' for p in sources)+'\n',encoding='utf-8')
(root/'source-binding.json').write_text(json.dumps({'status':'PREPARED_DIAGNOSTIC_NOT_ACCEPTANCE','publicSourceSnapshotFilesVerified':len(snapshot['files']),'compiledSourceFiles':[{'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources],'overlay':'Only maintained exact JSNI console bridge, maintained fail-closed Task35 verifier stub, and new observation-only diagnostic; no solver/model edits','seeds':['10387','10226','10014'],'simulationSecondsAfterIsolation':5,'perChildTimeoutMilliseconds':60000,'fullMatrixOrCold77':False},indent=2)+'\n',encoding='utf-8')
print(json.dumps({'status':'PREPARED_DIAGNOSTIC_NOT_ACCEPTANCE','root':str(root),'sources':len(sources),'verifiedPublicRaw':len(snapshot['files'])}))
