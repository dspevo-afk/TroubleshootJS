from pathlib import Path
import hashlib,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
def original(name,pin):
    raw=(R/'source'/name).read_bytes()
    assert hashlib.sha256(raw).hexdigest()==pin
    return raw.decode('utf-8').replace('\r\n','\n')
def save(name,text):
    with (R/name).open('x',encoding='utf-8',newline='\n') as f:f.write(text)
s=original('CurrentV2InputSetup.java','7baab4caf45741f00d311e3b3854c794204356bfa03c0f3271399d44087df6a4')
old='bool(s, get(s, trial, "orderChanged"), false);'
assert s.count(old)==1
save('CurrentV2InputSetup.java',s.replace(old,'// RUN/TRIAL_EXECUTED is emitted only for a changed requested order.\n        bool(s, get(s, trial, "orderChanged"), true);'))
s=original('CurrentAssemblyMetadata.java','e2b6b05bc95b9295d15aba5305a188c1eb98cb39db4b689747e2f126a6835198')
assert s.count('import java.util.Map;')==1
s=s.replace('import java.util.Map;','import java.util.Map;\nimport java.util.List;')
marker='    static ReceiptContentBinding prepare('
assert s.count(marker)==1
method='''    /**
     * Derives the cohort before createBody needs its child map. The caller owns
     * the validated, unchanged setup and captured IDs exclusively throughout.
     * The temporary view borrows prior only for this synchronous call; the
     * returned projection contains only freshly derived lists.
     */
    static Map<String,Object> cohortBeforeBody(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup,
            List<String> actualCapturedIds) throws Exception {
        if (meter == null || setup == null || actualCapturedIds == null)
            throw new IOException("METADATA_ARGUMENTS");

        meter.charge(1);
        Map<String,Object> data = setup.data;
        meter.charge(1);
        Object prior = data.get("prior");
        meter.charge(1);
        Map<String,Object> view = new LinkedHashMap<String,Object>();
        meter.copy(1);
        view.put("qualifiedPrior", prior);
        return CurrentResultProjections.cohort(meter,
                CohortTerminalTelemetryStudy.deriveCohort(view, actualCapturedIds, meter));
    }

'''
save('CurrentAssemblyMetadata.java',s.replace(marker,method+marker))
print('AUTHORED_TWO_COMPONENT_SOURCES_NO_EXECUTION')
