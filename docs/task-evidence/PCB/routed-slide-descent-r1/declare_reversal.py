"""Declare only the reviewed adapter removals and three exact probe substitutions."""
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
def erase(marker):return {'kind':'erase','begin':'// BEGIN COPY-ONLY '+marker+'.','end':'// END COPY-ONLY '+marker+'.','count':1}
def replace(old,new):return {'kind':'replace','old':old,'new':new,'count':1}
wrapper='''        return compactAndVerify(board, plan, source, routed, seed,
            generationMetadata, null);
    }

    private static FinalEvaluation compactAndVerify(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan,
            PcbRoutedPassiveFeasibilityCensusContractTest.SelectedSlideSource source,
            PcbBoardLayout routed, long seed, String generationMetadata,
            PcbBoardLayout[] normalizedOutput) {
'''
record={'files':{
 'tests/contracts/PcbRoutedPassiveLegCensusContractTest.java':[
    erase('selected routed descent structural snapshot adapter'),
    replace('// END COPY-ONLY selected routed slide census adapter.\n\n    private PcbRoutedPassiveLegCensusContractTest()',
            '// END COPY-ONLY selected routed slide census adapter.\n    private PcbRoutedPassiveLegCensusContractTest()')],
 'tests/contracts/PcbRoutedSlideCompactionContractTest.java':[
    erase('selected routed descent compaction adapter'),replace(wrapper,''),
    replace('        if (normalizedOutput != null) normalizedOutput[0] = normalized;\n',''),
    replace('\n\n\n    private static void check(boolean condition, String message)',
            '\n\n    private static void check(boolean condition, String message)')]
}}
p=HERE/'adapter-reversal.json';assert not p.exists()
p.write_text(json.dumps(record,indent=2)+'\n')
print('Declared exact adapter inverse; no COPY mutation')
