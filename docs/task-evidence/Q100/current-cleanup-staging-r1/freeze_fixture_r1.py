"""Fresh synthetic DTOs and independent pre-native Source census."""
from pathlib import Path
import copy,hashlib,json,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
S='SYNTHETIC_COMPONENT_ONLY';G='a'*64;E='b'*64
ALIASES=['adapterSessionAliasDropped','adapterSearchAliasDropped','adapterCandidateAliasDropped','adapterRouteResultAliasDropped','adapterBoardTemplateAliasesDropped']
WORK_KEYS=['elapsedCutoffNanos','cleanupNanos','groupsBeforeExport','wrapperGroupsBeforeExport','maxGroupNanosBeforeExport','maximumUnitsPerGroupBeforeExport','unitsBeforeExport','setupUnits','setupGroups','setupCopiedEntries','setupCopyChunks','originalCaptureOwnerUnits','originalCaptureWrapperUnits','originalCaptureCallbackUnits','originalCaptureDiagnosticUnits','originalCaptureGroups','originalCaptureCopiedEntries','originalCaptureCopyChunks','nullOwnerUnits','nullWrapperUnits','nullObserverUnits','observeOwnerUnits','observeWrapperUnits','observeObserverUnits','observerUnits','meterUnits','metadataUnits','metadataGroups','metadataCopiedEntries','metadataCopyChunks','copiedEntries','copyChunks','copyChunkLimit','copiedEntryLimit']
def arm_cleanup(n):
    d={k:True for k in ALIASES}
    d.update(sourceGeometryBeforeSha256=G,sourceGeometryAfterSha256=G,contextRestored=True,cleanupNanos=n)
    return d
capture={'fixtureScope':S,'template':{'geometryBeforeSha256':G,'geometryAfterSha256':G,'effectsBeforeSha256':E,'effectsAfterSha256':E},'ownersReleased':True,'contextRestored':True,'cleanupNanos':17}
arms={'null':{'fixtureScope':S,'cleanup':arm_cleanup(23)},'observe':{'fixtureScope':S,'cleanup':arm_cleanup(31)}}
fixture={'runId':'00000000-0000-0000-0000-000000000001','sourceManifestSha256':'2'*64,'inputContextSha256':'3'*64,'originalCapture':capture,'cohort':{'fixtureScope':S},'arms':arms,'qualifiedPrior':{'fixtureScope':S}}
cleanup={'schema':'tsj-cohort-terminal-cleanup-v1','originalCaptureOwnersReleased':True,'originalCaptureContextRestored':True,'nullArmAliasesDropped':True,'observeArmAliasesDropped':True,'sourceGeometryUnchanged':True,'effectsScope':'ORIGINAL_CAPTURE_COPY_ONLY','originalCaptureCopyEffectsUnchanged':True,'contextRestored':True,'cleanupNanos':123}
body={'schema':'tsj-cohort-terminal-result-v2','mode':'OBSERVED_COHORT_TERMINAL_TELEMETRY','status':'COMPLETED_DIAGNOSTIC_ONLY','runId':fixture['runId'],'sourceManifestSha256':fixture['sourceManifestSha256'],'wireContractSha256':'1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed','inputContextSha256':fixture['inputContextSha256'],'qualifiedPrior':copy.deepcopy(fixture['qualifiedPrior']),'originalCapture':copy.deepcopy(capture),'cohort':copy.deepcopy(fixture['cohort']),'arms':copy.deepcopy(arms),'work':dict.fromkeys(WORK_KEYS,0),'cleanup':copy.deepcopy(cleanup)}
def ownership(v):
    if type(v) is dict:
        rows=[ownership(x) for x in v.values()]
        return 9+7*len(v)+sum(x[0] for x in rows),1+sum(x[1] for x in rows)
    if type(v) is list:
        rows=[ownership(x) for x in v]
        return 8+sum(2+x[0] for x in rows),1+sum(x[1] for x in rows)
    assert v is None or type(v) in (str,bool,int)
    return 3,0
def tree(v):
    if type(v) is dict:return 6+sum(6*len(k)+9+tree(x) for k,x in v.items())
    if type(v) is list:return 7+sum(2+tree(x) for x in v)
    if type(v) is str:
        assert v.isascii() and len(v)<=256
        return 6*len(v)+8+(1 if v else 0)
    if v is None:return 7
    if type(v) is bool:return 9
    assert type(v) is int;return 8
owned,containers=ownership(body);tree_units=tree(body)
handoff={'ownershipVisitUnits':owned,'containers':containers,'treeVisitUnits':tree_units,'r7FixedUnitsIncludingTraversalAllocations':187,'prefixUnits':83,'prefixCopies':25,'prefixChunks':4,
 'units':83+187+owned+tree_units,'copiedEntries':25+95+containers,'copyChunks':4+3+containers,'groups':1,
 'priorGroups':1,'groupsBeforeExport':2,'syntheticOwnerCreditFixture':1}
assert handoff['units']<32768 and containers==12
names=['pending_constructor','installed_true','null_alias_false','observe_alias_false','geometry_false','scalar_below_sum','scalar_at_limit','negative_scalar','over_limit_scalar','body_type','slot_missing','slot_occupied','child_view_missing','projection_read_error','projection_copy_guard','replacement_copy_guard','group_guard_before_read','write_group_guard','null_arguments','pending_export_reject','r7_handoff_true','r7_handoff_below_sum','r7_handoff_alias_false','r7_zero_credit_reject']
value={'status':'FROZEN_FRESH_SYNTHETIC_CLEANUP_FIXTURE_AND_INDEPENDENT_SOURCE_CENSUS','scope':S,'names':names,'smoke':names[:2],'suite':names[2:],'fixture':fixture,'expectedCleanup':cleanup,'workKeys':WORK_KEYS,'handoff':handoff,'installer':[67,11,2],'pendingConstructor':[14,13,1],'rawDurationSum':71,'genuineEnvelope':'UNSPENT_GATED','nativeCalls':0}
value['creditPolicy']='Real native beginOriginalOwner/admitOwnerGroup(0)/adoptOriginalOwner(1) fixture; no genuine owner instance or work. Preserve explicit zero-credit rejection.'
raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
with (R/'REFERENCE_R1.json').open('xb') as f:f.write(raw)
print(json.dumps({'referenceSha256':hashlib.sha256(raw).hexdigest(),'handoff':handoff}))
