"""Root-owned exact18 fixture migrations and new19 native adapters."""
import json
import re
from pathlib import Path
HERE=Path(__file__).resolve().parent
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
TEST=COPY/'tests/contracts'
for name in ('PcbPassivePoseTrialSourceContractTest.java',
             'PcbPassiveSizingSourceContractTest.java',
             'PcbSourceSizedPoseSourceContractTest.java'):
    path=TEST/name; data=path.read_bytes()
    assert data.count(b'Rb56Plan.resolve(42L, 19)')+data.count(b'Rb56Plan.resolve(42L,19)')==1
    data=data.replace(b'Rb56Plan.resolve(42L, 19)',b'Rb56Plan.resolve(42L, 20)').replace(
        b'Rb56Plan.resolve(42L,19)',b'Rb56Plan.resolve(42L,20)')
    data=data.replace(b'interpretation19 rejects',b'interpretation20 rejects')
    path.write_bytes(data)
changes=[
    (b'plan.placementInterpretationVersion != 18))',
     b'plan.placementInterpretationVersion != 18 &&\r\n                plan.placementInterpretationVersion != 19))'),
    (b'Only explicit15/16/17/18 source intent is supported',
     b'Only explicit15/16/17/18/19 source intent is supported'),
    (b'version != 18) || plan == null',b'version != 18 && version != 19) || plan == null'),
    (b'(version == 15 ? 0 : version == 16 ? 1 : version == 17 ? 2 : 3)',
     b'(version == 15 ? 0 : version == 16 ? 1 : version == 17 ? 2 : version == 18 ? 3 : 4)'),
    (b'Pose observation requires its exact bound15/16/17/18 source',
     b'Pose observation requires its exact bound15/16/17/18/19 source'),
    (b'version == 17 ? "size17" : "pose18", System.nanoTime()',
     b'version == 17 ? "size17" : version == 18 ? "pose18" : "local19", System.nanoTime()')]
path=TEST/'PcbPostSelectionEdgeTrimContractTest.java'; data=path.read_bytes()
for before,after in changes:
    assert data.count(before)==1,before
    data=data.replace(before,after)
path.write_bytes(data)

prior=json.loads((HERE.parent/'passive-source-sizing-trial-r1/new-suites.json').read_text())
new={phase:[] for phase in prior}
for phase,rows in prior.items():
    for name,marker in rows:
        source=(TEST/(name+'.java')).read_text()
        target=name.replace('SourceSizedPose','LocalPose')
        # New source fixture starts from the migrated18 fixture; keep its unknown20.
        source=source.replace('SourceSizedPose','LocalPose').replace('SOURCE_SIZED_POSE','LOCAL_POSE')
        source=source.replace('source sized pose','local pose')
        source=re.sub(r'(?<!\d)18(?!\d)','19',source)
        source=source.replace('RB56_BOARD_NORMAL@4','RB56_BOARD_NORMAL@5')
        source=source.replace('passivePosePolicyVersion==3','passivePosePolicyVersion==4')
        source=source.replace('density14 policy3','density14 policy4').replace('identity4','identity5')
        source=source.replace('sizing-control source','local-pose source')
        source=source.replace('densityVersion18','densityVersion19').replace('passivePosePolicy18','passivePosePolicy19')
        for stem in ('sourcePlanSha256','graph','intent','layoutSeed','routingSeed','admissionIdentity',
                     'geometry','evaluations','demand','courtyard'):
            source=source.replace(stem+'18',stem+'19')
        out=TEST/(target+'.java'); assert not out.exists()
        out.write_bytes(source.replace('\r\n','\n').replace('\n','\r\n').encode())
        new[phase].append([target,marker.replace('source sized pose','local pose')])
new['unit'].insert(0,['PcbLocalPoseChoiceContractTest','PCB local pose choice contracts '])
(HERE/'new-suites.json').write_text(json.dumps(new,indent=2)+'\n')
print('Prepared18 new source/raw/route fixtures; actual-loop fixture owned by worker; six wrapper migrations')
