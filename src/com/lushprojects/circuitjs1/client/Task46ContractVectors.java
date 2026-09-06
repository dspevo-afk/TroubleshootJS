package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Developer-only, pure Task 46 contract corpus.
 *
 * <p>The expected seed, draw, and selection values below are literals.  They
 * are deliberately not produced by this class or by the production
 * implementation.  The companion task46_seed_reference.py independently
 * recomputes the same values from the frozen integer algorithm.</p>
 */
final class Task46ContractVectors {
    private static int assertions;
    private static int negativeCases;

    /*
     * Columns are: id, root seed, intent id, intent version, scope, block
     * key, concern token, concern revision, semantic key, derived seed,
     * draw zero, draw one, draw two, candidate IDs separated by '|', and
     * selected candidate.  Keep this simple one-row-per-line shape so the
     * independent Python oracle can compare it literally with the TSV.
     */
    private static final String[][] GOLDEN = new String[][] {
        {"v01", "0", "intent-main", "1", "device", "-", "topology", "1", "root-zero", "8570236341297704927", "-5613160340344259904", "562147381039080573", "-1651943333461123239", "z|a|m", "z"},
        {"v02", "-1", "intent-main", "1", "device", "-", "fault", "1", "negative", "6337562364839608619", "-3237827904353353348", "-3049239734774576325", "-1515037864869433993", "fault-b|fault-a|fault-c", "fault-a"},
        {"v03", "2147483647", "intent-main", "1", "device", "-", "scenario", "1", "signed-32-max", "-5298837036835246541", "-8037170020178081904", "-7423339811986283159", "-2242743429263401076", "z|a", "a"},
        {"v04", "2147483648", "intent-main", "1", "device", "-", "placement", "1", "unsigned-32-cross", "-1798547587849950301", "102376090273450860", "-1659430842634320502", "-8526770894255543878", "z|a|m", "a"},
        {"v05", "-2147483648", "intent-main", "1", "device", "-", "routing", "1", "signed-32-min", "-1200053360302739405", "8543060877636774311", "-3016262376503941390", "1560371412356762136", "z|a|m", "z"},
        {"v06", "-2147483649", "intent-main", "1", "device", "-", "presentation", "1", "signed-32-below", "-2045790159163994747", "8211966871246329396", "-2398394531992769265", "-1986651965535285088", "z|a", "a"},
        {"v07", "9007199254740991", "intent-main", "1", "device", "-", "topology", "1", "two53-minus-one", "-6008527564801839889", "8041298604081853177", "1289206149367017148", "-5288393764281962931", "z|a|m|q", "a"},
        {"v08", "9007199254740992", "intent-main", "1", "device", "-", "support", "1", "two53", "-2226046595912817952", "7655041952411213880", "-5593755259723266222", "4108463340920184297", "z|a|m|q", "a"},
        {"v09", "9007199254740993", "intent-main", "1", "device", "-", "fault", "1", "two53-plus-one", "6271561374429723782", "-3373283146513270237", "8497903215730333039", "9011515020966163393", "z|a|m|q", "m"},
        {"v10", "9223372036854775807", "intent-main", "1", "device", "-", "scenario", "7", "long-max", "-334251481619539588", "-2100202218193579875", "-762143448244481869", "7301849698641160794", "z|a|m", "a"},
        {"v11", "-9223372036854775808", "intent-main", "1", "device", "-", "placement", "7", "long-min", "-3094510703452759904", "8358893635254138790", "7808667535097850211", "7573175840079606994", "z|a|m", "z"},
        {"v12", "12", "3", "1", "device", "-", "topology", "1", "boundary-root-id-a", "-50758082439090755", "6615030607717450623", "1182380295102644326", "1204454696574536019", "z|a", "z"},
        {"v13", "1", "23", "1", "device", "-", "topology", "1", "boundary-root-id-b", "6630750756334465380", "6921762708106584533", "7505222276996859999", "-5032064761907128827", "z|a", "a"},
        {"v14", "7", "intent-main", "1", "block", "block-a", "block", "1", "semantic-ab", "7272437860146722519", "2901135016747937879", "3884970152904213561", "9052944476641955008", "z|a|m", "z"},
        {"v15", "7", "intent-main", "1", "block", "block-ab", "block", "1", "semantic-c", "4145853259584522761", "-3884088889783320539", "1065920275542782060", "-4901645473175228410", "z|a|m", "z"},
        {"v16", "7", "intent-main", "1", "block", "block-a", "values", "2", "resistor-R1", "-4692680285126695607", "7272131541421749114", "-6263064287916086660", "5368932409380408342", "z|a|m", "a"},
        {"v17", "7", "intent-main", "1", "block", "block-b", "support", "1", "pad-1", "5192710939162357368", "-4724655445728942666", "4542539997451859703", "-6966554157572112529", "z|a|m", "z"},
        {"v18", "7", "intent-main", "1", "block", "block-b", "fault", "1", "fault-choice", "5854855275889787184", "1748067723613948065", "2485666070263485421", "-1882184660117201825", "z|a|m", "m"},
        {"v19", "7", "intent-main", "1", "block", "block-b", "scenario", "1", "scenario-choice", "-7667775977373133820", "1484644488741857165", "4850536707747874647", "-6083429828837541300", "z|a|m", "z"},
        {"v20", "7", "intent-main", "1", "block", "block-b", "presentation", "1", "ui-choice", "-956605820455377178", "140750540366832964", "-8797531607480999741", "-8685341857797510736", "z|a|m", "z"},
        {"v21", "-9223372036854775807", "intent-main", "2", "device", "-", "routing", "1", "high-bit-minus-one", "1485869843608203979", "-4226399413377501654", "-8517577157193434458", "-6621381696860287298", "z|a|m", "z"},
        {"v22", "9223372036854775806", "intent-main", "2", "device", "-", "routing", "2", "high-bit-max-minus-one", "-1222531781087577344", "7485315352703764242", "-1781180077158910303", "4717470744343766429", "z|a|m", "a"}
    };

    private static final String UNSPECIFIED_CONSTRAINTS =
        "tsj-constraints/1;blocks=~;components=~;diagnostic-depth=~;domains=~;"
        + "input-transitions=~;instruments=~;isolation-actions=~;"
        + "parallel-ambiguity=~;plausible-owners=~;purposeful-auxiliaries=~;"
        + "temporal-evidence=~;temporal-samples=~";

    private Task46ContractVectors() { }

    /** Run every pure Task 46 A/B assertion and return stable diagnostic text. */
    static String run() {
        assertions = 0;
        negativeCases = 0;
        StringBuilder output = new StringBuilder();
        descriptorRoundTrips(output);
        constraintsRoundTrips(output);
        descriptorNegativeCases(output);
        constraintNegativeCases(output);
        namedVectors(output);
        namedIsolation(output);
        output.append("assertions=").append(assertions).append('\n');
        output.append("negative-cases=").append(negativeCases).append('\n');
        return output.toString();
    }

    static int getAssertionCount() {
        return assertions;
    }

    static int getNegativeCaseCount() {
        return negativeCases;
    }

    static int goldenVectorCount() {
        return GOLDEN.length;
    }

    private static void descriptorRoundTrips(StringBuilder output) {
        ChallengeDescriptor legacy = ChallengeDescriptor.legacy("LED_INDICATOR", 0L);
        String expected = "tsj-challenge/1\n"
            + "constraints=" + UNSPECIFIED_CONSTRAINTS + "\n"
            + "device-intent=LED_INDICATOR@1\n"
            + "difficulty-profile=legacy-default@1\n"
            + "generator=legacy-leaf@1\n"
            + "geometry=3\n"
            + "root-seed=0";
        equal(expected, legacy.toCanonical(), "legacy canonical ordering");
        ChallengeDescriptor parsedLegacy = ChallengeDescriptor.parse(expected);
        equal(expected, parsedLegacy.toCanonical(), "legacy canonical round trip");
        equal(0L, parsedLegacy.getRootSeed(), "legacy seed");
        equal("legacy-leaf", parsedLegacy.getGenerator().getId(), "legacy generator id");
        equal(1, parsedLegacy.getGenerator().getVersion(), "legacy generator version");
        equal("LED_INDICATOR", parsedLegacy.getDeviceIntent().getId(), "legacy family id");
        equal(3, parsedLegacy.getGeometryVersion().getValue(), "legacy geometry version");
        check(parsedLegacy.getConstraints().isUnspecified(), "legacy constraints are explicitly unspecified");

        long[] seedBoundaries = {
            0L, -1L, Integer.MAX_VALUE, (long) Integer.MAX_VALUE + 1L,
            Integer.MIN_VALUE, (long) Integer.MIN_VALUE - 1L,
            9007199254740991L, 9007199254740992L, 9007199254740993L,
            Long.MAX_VALUE, Long.MIN_VALUE
        };
        for (long seed : seedBoundaries) {
            ChallengeDescriptor boundary = ChallengeDescriptor.legacy(
                "LED_INDICATOR", seed);
            String text = boundary.toCanonical();
            equal(Long.toString(seed), fieldValue(text, "root-seed"),
                "seed boundary has exact decimal spelling " + seed);
            equal(seed, ChallengeDescriptor.parse(text).getRootSeed(),
                "seed boundary round trips " + seed);
        }

        List<GenerationConstraints.CountRequest> requests = validRequests();
        GenerationConstraints constraints = new GenerationConstraints(
            GenerationConstraints.VERSION1, requests,
            GenerationConstraints.Requirement.REQUIRED,
            GenerationConstraints.Requirement.FORBIDDEN,
            GenerationConstraints.AllowedInstruments.only(
                Arrays.asList("DIODE", "DC_VOLTAGE")));
        ChallengeDescriptor descriptor = new ChallengeDescriptor(
            1, 9007199254740993L,
            new ChallengeDescriptor.VersionedId("generator-x", 4),
            new ChallengeDescriptor.VersionedId("intent-x", 2),
            new ChallengeDescriptor.VersionedId("profile-x", 9),
            new PcbGeometryContractVersion(3), constraints);
        String canonical = descriptor.toCanonical();
        equal(canonical, ChallengeDescriptor.parse(canonical).toCanonical(),
            "full descriptor canonical round trip");
        equal(9007199254740993L,
            ChallengeDescriptor.parse(canonical).getRootSeed(),
            "full descriptor preserves 2^53 plus one");
        equal(1, descriptor.getSchemaVersion(), "descriptor schema version");
        equal(4, descriptor.getGenerator().getVersion(), "generator version");
        equal(2, descriptor.getDeviceIntent().getVersion(), "intent version");
        equal(9, descriptor.getDifficultyProfile().getVersion(), "profile version");
        equal(3, descriptor.getGeometryVersion().getValue(), "descriptor geometry");
        equal(1, descriptor.getConstraints().getCount(
            GenerationConstraints.CountMetric.BLOCKS).getMinimum(),
            "blocks lower bound");
        equal(3, descriptor.getConstraints().getCount(
            GenerationConstraints.CountMetric.BLOCKS).getMaximum(),
            "blocks upper bound");
        check(!descriptor.getConstraints().getCount(
            GenerationConstraints.CountMetric.DOMAINS).isSpecified(),
            "unspecified metric remains distinct");
        equal(GenerationConstraints.Requirement.REQUIRED,
            descriptor.getConstraints().getParallelAmbiguity(),
            "parallel requirement");
        equal(GenerationConstraints.Requirement.FORBIDDEN,
            descriptor.getConstraints().getTemporalEvidence(),
            "temporal requirement");
        equal(Arrays.asList("DC_VOLTAGE", "DIODE"),
            descriptor.getConstraints().getAllowedInstruments().getIds(),
            "instrument IDs are sorted");

        String permutedDescriptor = permuteDescriptorLines(canonical);
        equal(canonical, ChallengeDescriptor.parse(permutedDescriptor).toCanonical(),
            "descriptor field permutation canonicalizes");
        output.append("descriptor.legacy=").append(escape(canonicalOf(legacy))).append('\n');
        output.append("descriptor.full=").append(escape(canonical)).append('\n');
        output.append("descriptor.permutation=PASS\n");

        // Inputs are copied before publication; mutating the source lists does
        // not mutate the already constructed immutable request.
        List<GenerationConstraints.CountRequest> sourceRequests = validRequests();
        List<String> sourceInstruments = new ArrayList<String>(Arrays.asList("DIODE"));
        final GenerationConstraints copied = new GenerationConstraints(
            GenerationConstraints.VERSION1, sourceRequests,
            GenerationConstraints.Requirement.UNSPECIFIED,
            GenerationConstraints.Requirement.UNSPECIFIED,
            GenerationConstraints.AllowedInstruments.only(sourceInstruments));
        sourceRequests.clear();
        sourceInstruments.clear();
        check(copied.getCount(GenerationConstraints.CountMetric.BLOCKS).isSpecified(),
            "constraint request list defensively copied");
        equal(Arrays.asList("DIODE"), copied.getAllowedInstruments().getIds(),
            "instrument list defensively copied");
        expectUnmodifiable(new Runnable() { public void run() {
            copied.getCounts().clear();
        }}, "counts are immutable");
        expectUnmodifiable(new Runnable() { public void run() {
            copied.getAllowedInstruments().getIds().clear();
        }}, "instrument IDs are immutable");
        expectIllegalState(new Runnable() { public void run() {
            copied.getCount(GenerationConstraints.CountMetric.DOMAINS).getMinimum();
        }}, "unspecified range getters reject");
    }

    private static void constraintsRoundTrips(StringBuilder output) {
        GenerationConstraints unspecified = GenerationConstraints.unspecified();
        equal(UNSPECIFIED_CONSTRAINTS, unspecified.toCanonical(),
            "unspecified constraint canonical ordering");
        equal(UNSPECIFIED_CONSTRAINTS,
            GenerationConstraints.parse(UNSPECIFIED_CONSTRAINTS).toCanonical(),
            "unspecified constraint round trip");
        check(unspecified.isUnspecified(), "unspecified marker is explicit");
        equal(GenerationConstraints.Requirement.UNSPECIFIED,
            unspecified.getParallelAmbiguity(), "unspecified parallel requirement");
        equal(GenerationConstraints.Requirement.UNSPECIFIED,
            unspecified.getTemporalEvidence(), "unspecified temporal requirement");
        check(!unspecified.getAllowedInstruments().isSpecified(),
            "unspecified instrument set is distinct from empty set");

        GenerationConstraints emptyInstruments = new GenerationConstraints(
            GenerationConstraints.VERSION1, Collections.<GenerationConstraints.CountRequest>emptyList(),
            GenerationConstraints.Requirement.UNSPECIFIED,
            GenerationConstraints.Requirement.UNSPECIFIED,
            GenerationConstraints.AllowedInstruments.only(
                Collections.<String>emptyList()));
        check(emptyInstruments.getAllowedInstruments().isSpecified(),
            "empty instrument set is specified");
        equal("tsj-constraints/1;blocks=~;components=~;diagnostic-depth=~;domains=~;"
            + "input-transitions=~;instruments=[];isolation-actions=~;"
            + "parallel-ambiguity=~;plausible-owners=~;purposeful-auxiliaries=~;"
            + "temporal-evidence=~;temporal-samples=~", emptyInstruments.toCanonical(),
            "empty instrument canonical form");
        equal(emptyInstruments.toCanonical(),
            GenerationConstraints.parse(emptyInstruments.toCanonical()).toCanonical(),
            "empty instrument round trip");

        String custom = new GenerationConstraints(
            GenerationConstraints.VERSION1, validRequests(),
            GenerationConstraints.Requirement.REQUIRED,
            GenerationConstraints.Requirement.FORBIDDEN,
            GenerationConstraints.AllowedInstruments.only(
                Arrays.asList("DIODE", "DC_VOLTAGE"))).toCanonical();
        equal(custom, GenerationConstraints.parse(custom).toCanonical(),
            "custom constraints round trip");
        equal(custom, GenerationConstraints.parse(permuteConstraintFields(custom)).toCanonical(),
            "constraint field permutation canonicalizes");
        output.append("constraints.unspecified=").append(UNSPECIFIED_CONSTRAINTS).append('\n');
        output.append("constraints.empty-instruments=").append(emptyInstruments.toCanonical()).append('\n');
        output.append("constraints.custom=").append(custom).append('\n');
    }

    private static void descriptorNegativeCases(StringBuilder output) {
        final String base = ChallengeDescriptor.legacy("LED_INDICATOR", 0L).toCanonical();
        expect(ChallengeContractException.Code.UNSUPPORTED_VERSION, new Runnable() { public void run() {
            ChallengeDescriptor.parse(base.replace("tsj-challenge/1", "tsj-challenge/2"));
        }}, "descriptor unsupported schema");
        expect(ChallengeContractException.Code.MISSING_FIELD, new Runnable() { public void run() {
            ChallengeDescriptor.parse(removeDescriptorField(base, "generator"));
        }}, "descriptor missing generator");
        expect(ChallengeContractException.Code.DUPLICATE_DECLARATION, new Runnable() { public void run() {
            ChallengeDescriptor.parse(base + "\nroot-seed=0");
        }}, "descriptor duplicate root seed");
        expect(ChallengeContractException.Code.UNKNOWN_FIELD, new Runnable() { public void run() {
            ChallengeDescriptor.parse(base + "\nunknown=x");
        }}, "descriptor unknown field");
        expect(ChallengeContractException.Code.INVALID_ENCODING, new Runnable() { public void run() {
            ChallengeDescriptor.parse(base.replace("tsj-challenge/1", "not-a-descriptor"));
        }}, "descriptor invalid header");
        expect(ChallengeContractException.Code.INVALID_ENCODING, new Runnable() { public void run() {
            ChallengeDescriptor.parse(replaceDescriptorField(base, "root-seed", "+0"));
        }}, "descriptor plus seed rejected");
        expect(ChallengeContractException.Code.INVALID_ENCODING, new Runnable() { public void run() {
            ChallengeDescriptor.parse(replaceDescriptorField(base, "root-seed", "-0"));
        }}, "descriptor negative zero rejected");
        expect(ChallengeContractException.Code.INVALID_ENCODING, new Runnable() { public void run() {
            ChallengeDescriptor.parse(replaceDescriptorField(base, "root-seed", "01"));
        }}, "descriptor leading zero rejected");
        expect(ChallengeContractException.Code.INVALID_ID, new Runnable() { public void run() {
            ChallengeDescriptor.parse(replaceDescriptorField(base,
                "device-intent", "bad/id@1"));
        }}, "descriptor malformed parsed identity");
        expect(ChallengeContractException.Code.INVALID_VERSION, new Runnable() { public void run() {
            ChallengeDescriptor.parse(replaceDescriptorField(base,
                "device-intent", "intent@0"));
        }}, "descriptor nonpositive parsed identity version");
        expect(ChallengeContractException.Code.INVALID_RANGE, new Runnable() { public void run() {
            ChallengeDescriptor.parse(replaceDescriptorField(base, "root-seed", "9223372036854775808"));
        }}, "descriptor long overflow rejected");
        expect(ChallengeContractException.Code.INVALID_RANGE, new Runnable() { public void run() {
            ChallengeDescriptor.parse(replaceDescriptorField(base, "root-seed", "-9223372036854775809"));
        }}, "descriptor long underflow rejected");
        expect(ChallengeContractException.Code.INVALID_ID, new Runnable() { public void run() {
            new ChallengeDescriptor.VersionedId("bad/id", 1);
        }}, "descriptor malformed ID");
        expect(ChallengeContractException.Code.INVALID_VERSION, new Runnable() { public void run() {
            new ChallengeDescriptor.VersionedId("id", 0);
        }}, "descriptor nonpositive identity version");
        expect(ChallengeContractException.Code.UNSUPPORTED_VERSION, new Runnable() { public void run() {
            new ChallengeDescriptor(2, 0L,
                new ChallengeDescriptor.VersionedId("g", 1),
                new ChallengeDescriptor.VersionedId("i", 1),
                new ChallengeDescriptor.VersionedId("p", 1),
                new PcbGeometryContractVersion(3), GenerationConstraints.unspecified());
        }}, "descriptor unsupported constructor schema");
        expectIllegalArgument(new Runnable() { public void run() {
            new PcbGeometryContractVersion(0);
        }}, "descriptor invalid geometry version");
        output.append("descriptor.negative-cases=PASS\n");
    }

    private static void constraintNegativeCases(StringBuilder output) {
        final List<GenerationConstraints.CountRequest> duplicate = validRequests();
        duplicate.add(new GenerationConstraints.CountRequest(
            GenerationConstraints.CountMetric.BLOCKS,
            GenerationConstraints.CountRange.exact(9)));
        expect(ChallengeContractException.Code.DUPLICATE_DECLARATION, new Runnable() { public void run() {
            new GenerationConstraints(GenerationConstraints.VERSION1, duplicate,
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.AllowedInstruments.unspecified());
        }}, "duplicate count metric");
        expect(ChallengeContractException.Code.INVALID_BOUND, new Runnable() { public void run() {
            GenerationConstraints.CountRange.between(-1, 2);
        }}, "negative range bound");
        expect(ChallengeContractException.Code.INVALID_RANGE, new Runnable() { public void run() {
            GenerationConstraints.CountRange.between(3, 2);
        }}, "inverted range");
        equal(Integer.MAX_VALUE,
            GenerationConstraints.CountRange.exact(Integer.MAX_VALUE).getMaximum(),
            "maximum signed integer range bound");
        expect(ChallengeContractException.Code.UNSUPPORTED_VERSION, new Runnable() { public void run() {
            new GenerationConstraints(2,
                Collections.<GenerationConstraints.CountRequest>emptyList(),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.AllowedInstruments.unspecified());
        }}, "constraint unsupported version");
        expect(ChallengeContractException.Code.DUPLICATE_DECLARATION, new Runnable() { public void run() {
            GenerationConstraints.AllowedInstruments.only(Arrays.asList("DIODE", "DIODE"));
        }}, "duplicate instrument ID");
        expect(ChallengeContractException.Code.UNSUPPORTED_ID, new Runnable() { public void run() {
            GenerationConstraints.AllowedInstruments.only(Arrays.asList("AC_VOLTAGE"));
        }}, "unsupported instrument ID");
        expect(ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT, new Runnable() { public void run() {
            new GenerationConstraints(GenerationConstraints.VERSION1,
                singletonRequest(GenerationConstraints.CountMetric.TEMPORAL_SAMPLES,
                    GenerationConstraints.CountRange.exact(1)),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.FORBIDDEN,
                GenerationConstraints.AllowedInstruments.unspecified());
        }}, "forbidden temporal evidence with temporal samples");
        expect(ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT, new Runnable() { public void run() {
            new GenerationConstraints(GenerationConstraints.VERSION1,
                singletonRequest(GenerationConstraints.CountMetric.TEMPORAL_SAMPLES,
                    GenerationConstraints.CountRange.exact(0)),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.REQUIRED,
                GenerationConstraints.AllowedInstruments.unspecified());
        }}, "required temporal evidence with zero samples");
        expect(ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT, new Runnable() { public void run() {
            new GenerationConstraints(GenerationConstraints.VERSION1,
                singletonRequest(GenerationConstraints.CountMetric.DIAGNOSTIC_DEPTH,
                    GenerationConstraints.CountRange.exact(0)),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.REQUIRED,
                GenerationConstraints.AllowedInstruments.unspecified());
        }}, "required temporal evidence implies one sample for depth");
        expect(ChallengeContractException.Code.CONTRADICTORY_CONSTRAINT, new Runnable() { public void run() {
            new GenerationConstraints(GenerationConstraints.VERSION1,
                Arrays.asList(
                    new GenerationConstraints.CountRequest(
                        GenerationConstraints.CountMetric.DIAGNOSTIC_DEPTH,
                        GenerationConstraints.CountRange.exact(2)),
                    new GenerationConstraints.CountRequest(
                        GenerationConstraints.CountMetric.INPUT_TRANSITIONS,
                        GenerationConstraints.CountRange.exact(1)),
                    new GenerationConstraints.CountRequest(
                        GenerationConstraints.CountMetric.ISOLATION_ACTIONS,
                        GenerationConstraints.CountRange.exact(1)),
                    new GenerationConstraints.CountRequest(
                        GenerationConstraints.CountMetric.TEMPORAL_SAMPLES,
                        GenerationConstraints.CountRange.exact(1))),
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.AllowedInstruments.unspecified());
        }}, "diagnostic depth below explicit action minima");
        final String constraints = GenerationConstraints.unspecified().toCanonical();
        expect(ChallengeContractException.Code.MISSING_FIELD, new Runnable() { public void run() {
            GenerationConstraints.parse(removeConstraintField(constraints, "components"));
        }}, "constraint missing full key");
        expect(ChallengeContractException.Code.DUPLICATE_DECLARATION, new Runnable() { public void run() {
            GenerationConstraints.parse(constraints + ";blocks=~");
        }}, "constraint duplicate key");
        expect(ChallengeContractException.Code.UNKNOWN_FIELD, new Runnable() { public void run() {
            GenerationConstraints.parse(constraints + ";unknown=~");
        }}, "constraint unknown key");
        expect(ChallengeContractException.Code.INVALID_RANGE, new Runnable() { public void run() {
            GenerationConstraints.parse(constraints.replace("blocks=~", "blocks=3:2"));
        }}, "constraint malformed range");
        GenerationConstraints boundaryConstraints = GenerationConstraints.parse(
            constraints.replace("blocks=~", "blocks=0:2147483647"));
        equal(2147483647,
            boundaryConstraints.getCount(GenerationConstraints.CountMetric.BLOCKS)
                .getMaximum(),
            "encoded range accepts signed integer maximum");
        expect(ChallengeContractException.Code.INVALID_RANGE, new Runnable() { public void run() {
            GenerationConstraints.parse(constraints.replace("blocks=~", "blocks=-1:2"));
        }}, "constraint negative encoded bound");
        expect(ChallengeContractException.Code.INVALID_RANGE, new Runnable() { public void run() {
            GenerationConstraints.parse(constraints.replace("blocks=~", "blocks=0:2147483648"));
        }}, "constraint encoded bound overflow");
        output.append("constraints.negative-cases=PASS\n");
    }

    private static void namedVectors(StringBuilder output) {
        for (String[] vector : GOLDEN) {
            long rootSeed = Long.parseLong(vector[1]);
            int intentVersion = Integer.parseInt(vector[3]);
            NamedRandomStreams.Concern concern = concern(vector[6]);
            int revision = Integer.parseInt(vector[7]);
            NamedRandomStreams streams = new NamedRandomStreams(1, rootSeed,
                vector[2], intentVersion);
            long actualSeed;
            NamedRandomStreams.Stream stream;
            if ("device".equals(vector[4])) {
                actualSeed = streams.deviceSeed(concern, revision, vector[8]);
                stream = streams.openDevice(concern, revision, vector[8]);
            } else {
                actualSeed = streams.blockSeed(vector[5], concern, revision, vector[8]);
                stream = streams.openBlock(vector[5], concern, revision, vector[8]);
            }
            equal(Long.parseLong(vector[9]), actualSeed, vector[0] + " seed literal");
            equal(Long.parseLong(vector[10]), stream.nextLong(), vector[0] + " draw zero literal");
            equal(Long.parseLong(vector[11]), stream.nextLong(), vector[0] + " draw one literal");
            equal(Long.parseLong(vector[12]), stream.nextLong(), vector[0] + " draw two literal");
            List<String> candidates = splitCandidates(vector[13]);
            List<String> canonical = NamedRandomStreams.canonicalCandidates(candidates);
            equal(canonical, sortedUnique(candidates), vector[0] + " canonical candidates");
            equal(vector[14], NamedRandomStreams.select(actualSeed, candidates),
                vector[0] + " selection literal");
            equal(vector[14], NamedRandomStreams.select(actualSeed, canonical),
                vector[0] + " selection ignores candidate permutation");
            check(canonical.contains(vector[14]), vector[0] + " selection is eligible");
            // A fresh local cursor always reopens at the same beginning.
            NamedRandomStreams.Stream reopened;
            if ("device".equals(vector[4])) {
                reopened = streams.openDevice(concern, revision, vector[8]);
            } else {
                reopened = streams.openBlock(vector[5], concern, revision, vector[8]);
            }
            equal(Long.parseLong(vector[10]), reopened.nextLong(), vector[0] + " stream reopens");
            output.append("vector=").append(vector[0])
                .append(";seed=").append(vector[9])
                .append(";draw0=").append(vector[10])
                .append(";draw1=").append(vector[11])
                .append(";draw2=").append(vector[12])
                .append(";selection=").append(vector[14]).append('\n');
        }

        expect(ChallengeContractException.Code.INVALID_SCOPE, new Runnable() { public void run() {
            new NamedRandomStreams(1, 0L, "intent-main", 1)
                .deviceSeed(NamedRandomStreams.Concern.BLOCK, 1, "illegal");
        }}, "block concern cannot use device scope");
        expect(ChallengeContractException.Code.INVALID_SCOPE, new Runnable() { public void run() {
            new NamedRandomStreams(1, 0L, "intent-main", 1)
                .deviceSeed(NamedRandomStreams.Concern.VALUES, 1, "illegal");
        }}, "values concern cannot use device scope");
        expect(ChallengeContractException.Code.INVALID_BOUND, new Runnable() { public void run() {
            new NamedRandomStreams.Stream(0L).nextInt(0);
        }}, "zero stream bound rejected");
        expect(ChallengeContractException.Code.EMPTY_CANDIDATES, new Runnable() { public void run() {
            NamedRandomStreams.canonicalCandidates(Collections.<String>emptyList());
        }}, "empty candidate set rejected");
        expect(ChallengeContractException.Code.DUPLICATE_DECLARATION, new Runnable() { public void run() {
            NamedRandomStreams.canonicalCandidates(Arrays.asList("a", "a"));
        }}, "duplicate candidate set rejected");
        output.append("named.vectors=").append(GOLDEN.length).append("\n");
    }

    private static void namedIsolation(StringBuilder output) {
        NamedRandomStreams streams = new NamedRandomStreams(1, 77L, "intent-main", 1);
        long valuesBefore = streams.blockSeed("driver", NamedRandomStreams.Concern.VALUES, 1, "reserved");
        long faultBefore = streams.deviceSeed(NamedRandomStreams.Concern.FAULT, 1, "fault");
        long faultDraw = streams.openDevice(NamedRandomStreams.Concern.FAULT, 1, "fault").nextLong();
        NamedRandomStreams.Stream values = streams.openBlock("driver", NamedRandomStreams.Concern.VALUES, 1, "R1");
        values.nextLong(); values.nextLong(); values.nextLong();
        equal(valuesBefore, streams.blockSeed("driver", NamedRandomStreams.Concern.VALUES, 1, "reserved"),
            "reading a seed remains diagnostic-pure");
        equal(faultBefore, streams.deviceSeed(NamedRandomStreams.Concern.FAULT, 1, "fault"),
            "draws in one concern do not advance another");
        equal(faultDraw,
            streams.openDevice(NamedRandomStreams.Concern.FAULT, 1, "fault").nextLong(),
            "extra draws in one concern do not advance another");

        long topologyRevision1 = streams.deviceSeed(NamedRandomStreams.Concern.TOPOLOGY, 1, "topology");
        long topologyRevision2 = streams.deviceSeed(NamedRandomStreams.Concern.TOPOLOGY, 2, "topology");
        long supportRevision1 = streams.deviceSeed(NamedRandomStreams.Concern.SUPPORT, 1, "support");
        check(topologyRevision1 != topologyRevision2, "concern revision salts its own scope");
        equal(supportRevision1, streams.deviceSeed(NamedRandomStreams.Concern.SUPPORT, 1, "support"),
            "unrelated concern is unchanged");

        evaluateNamedFixtureIsolation(output);
        List<String> changedCandidates = Arrays.asList("a", "m", "z", "new-eligible");
        String changedSelection = NamedRandomStreams.select(valuesBefore, changedCandidates);
        check(changedCandidates.contains(changedSelection),
            "changed eligibility selection remains in new population");
        output.append("named.isolation=PASS\n");
        output.append("named.changed-eligibility=allowed\n");
    }

    private static final List<String> FIXTURE_FAULT_CANDIDATES =
        Collections.unmodifiableList(Arrays.asList("fault-a", "fault-b", "fault-c"));
    private static final List<String> FIXTURE_SCENARIO_CANDIDATES =
        Collections.unmodifiableList(Arrays.asList("scenario-a", "scenario-b", "scenario-c"));

    /**
     * Walk immutable block fixtures through the named-stream boundaries only.
     * This deliberately does not compose a runtime board or touch CircuitJS.
     */
    private static void evaluateNamedFixtureIsolation(StringBuilder output) {
        FunctionalBlockDescriptor driver = FunctionalBlockExamples.lowSideDriver("driver");
        FunctionalBlockDescriptor lamp = FunctionalBlockExamples.indicatorLoad("lamp");
        FunctionalBlockDescriptor optional = FunctionalBlockExamples.indicatorLoad("optional");

        List<FunctionalBlockDescriptor> inventory =
            new ArrayList<FunctionalBlockDescriptor>(Arrays.asList(driver, lamp));
        FixtureEvaluation baseline = evaluateFixture(inventory,
            FIXTURE_FAULT_CANDIDATES, FIXTURE_SCENARIO_CANDIDATES, false);
        inventory.add(optional);
        FixtureEvaluation insertion = evaluateFixture(inventory,
            FIXTURE_FAULT_CANDIDATES, FIXTURE_SCENARIO_CANDIDATES, true);
        check(inventory.remove(optional), "optional fixture is removable");
        FixtureEvaluation deletion = evaluateFixture(inventory,
            FIXTURE_FAULT_CANDIDATES, FIXTURE_SCENARIO_CANDIDATES, false);
        FixtureEvaluation permutation = evaluateFixture(Arrays.asList(lamp, driver),
            FIXTURE_FAULT_CANDIDATES, FIXTURE_SCENARIO_CANDIDATES, false);
        FixtureEvaluation reorderedCandidates = evaluateFixture(inventory,
            Arrays.asList("fault-c", "fault-a", "fault-b"),
            Arrays.asList("scenario-c", "scenario-a", "scenario-b"), false);

        compareFixtureOutputs(baseline, insertion, "optional insertion");
        compareFixtureOutputs(baseline, deletion, "optional deletion");
        compareFixtureOutputs(baseline, permutation, "collection permutation");
        compareFixtureOutputs(baseline, reorderedCandidates, "candidate permutation");
        check(!baseline.valuesSeeds.get("driver").equals(
                baseline.valuesSeeds.get("lamp")),
            "driver and lamp VALUES/R1 streams are independently keyed");
        check(!baseline.blockSeeds.get("driver").equals(
                baseline.blockSeeds.get("lamp")),
            "driver and lamp block streams are independently keyed");
        output.append("named.fixture=PASS\n");
        output.append("named.fixture-cases=baseline,insertion,deletion,permutation,candidate-reorder\n");
    }

    private static FixtureEvaluation evaluateFixture(
            Collection<FunctionalBlockDescriptor> blocks,
            Collection<String> faultCandidates,
            Collection<String> scenarioCandidates,
            boolean drawOptionalStream) {
        BlockNamespace namespace = new BlockNamespace("device", 1, blocks);
        NamedRandomStreams streams = new NamedRandomStreams(1, 77L,
            "intent-main", 1);
        Map<String, Long> valuesSeeds = new TreeMap<String, Long>();
        Map<String, Long> valuesDraws = new TreeMap<String, Long>();
        Map<String, Long> blockSeeds = new TreeMap<String, Long>();
        Map<String, String> identities = new TreeMap<String, String>();
        for (FunctionalBlockDescriptor block : blocks) {
            String blockKey = block.getInstanceKey();
            for (String padId : block.getPads().keySet()) {
                identities.put(blockKey + "/pad/" + padId,
                    namespace.idFor(blockKey, FunctionalBlockDescriptor.EntityKind.PAD,
                        padId));
            }
            long valuesSeed = streams.blockSeed(blockKey,
                NamedRandomStreams.Concern.VALUES, 1, "R1");
            NamedRandomStreams.Stream values = streams.openBlock(blockKey,
                NamedRandomStreams.Concern.VALUES, 1, "R1");
            valuesSeeds.put(blockKey, Long.valueOf(valuesSeed));
            valuesDraws.put(blockKey, Long.valueOf(values.nextLong()));
            if (drawOptionalStream && "optional".equals(blockKey)) {
                values.nextLong();
                values.nextLong();
                values.nextLong();
            }
            blockSeeds.put(blockKey, Long.valueOf(streams.blockSeed(blockKey,
                NamedRandomStreams.Concern.BLOCK, 1, "block")));
        }
        long faultSeed = streams.deviceSeed(NamedRandomStreams.Concern.FAULT,
            1, "fault");
        long scenarioSeed = streams.deviceSeed(NamedRandomStreams.Concern.SCENARIO,
            1, "scenario");
        return new FixtureEvaluation(valuesSeeds, valuesDraws, blockSeeds,
            identities, NamedRandomStreams.select(faultSeed, faultCandidates),
            NamedRandomStreams.select(scenarioSeed, scenarioCandidates));
    }

    private static void compareFixtureOutputs(FixtureEvaluation expected,
            FixtureEvaluation actual, String label) {
        for (String blockKey : Arrays.asList("driver", "lamp")) {
            equal(expected.valuesSeeds.get(blockKey), actual.valuesSeeds.get(blockKey),
                label + " preserves " + blockKey + " VALUES/R1 seed");
            equal(expected.valuesDraws.get(blockKey), actual.valuesDraws.get(blockKey),
                label + " preserves " + blockKey + " VALUES/R1 output");
            equal(expected.blockSeeds.get(blockKey), actual.blockSeeds.get(blockKey),
                label + " preserves " + blockKey + " block seed");
            for (String identityKey : expected.identities.keySet()) {
                if (identityKey.startsWith(blockKey + "/")) {
                    equal(expected.identities.get(identityKey), actual.identities.get(identityKey),
                        label + " preserves " + identityKey);
                }
            }
        }
        equal(expected.faultSelection, actual.faultSelection,
            label + " preserves fault selection");
        equal(expected.scenarioSelection, actual.scenarioSelection,
            label + " preserves scenario selection");
    }

    private static final class FixtureEvaluation {
        private final Map<String, Long> valuesSeeds;
        private final Map<String, Long> valuesDraws;
        private final Map<String, Long> blockSeeds;
        private final Map<String, String> identities;
        private final String faultSelection;
        private final String scenarioSelection;

        FixtureEvaluation(Map<String, Long> valuesSeeds,
                Map<String, Long> valuesDraws, Map<String, Long> blockSeeds,
                Map<String, String> identities, String faultSelection,
                String scenarioSelection) {
            this.valuesSeeds = valuesSeeds;
            this.valuesDraws = valuesDraws;
            this.blockSeeds = blockSeeds;
            this.identities = identities;
            this.faultSelection = faultSelection;
            this.scenarioSelection = scenarioSelection;
        }
    }

    private static List<GenerationConstraints.CountRequest> validRequests() {
        return new ArrayList<GenerationConstraints.CountRequest>(Arrays.asList(
            request(GenerationConstraints.CountMetric.BLOCKS, 1, 3),
            request(GenerationConstraints.CountMetric.COMPONENTS, 2, 2),
            new GenerationConstraints.CountRequest(
                GenerationConstraints.CountMetric.DOMAINS,
                GenerationConstraints.CountRange.unspecified()),
            request(GenerationConstraints.CountMetric.PLAUSIBLE_OWNERS, 1, 2),
            request(GenerationConstraints.CountMetric.DIAGNOSTIC_DEPTH, 5, 10),
            request(GenerationConstraints.CountMetric.INPUT_TRANSITIONS, 2, 3),
            request(GenerationConstraints.CountMetric.ISOLATION_ACTIONS, 1, 2),
            request(GenerationConstraints.CountMetric.TEMPORAL_SAMPLES, 0, 0),
            request(GenerationConstraints.CountMetric.PURPOSEFUL_AUXILIARIES, 0, 1)));
    }

    private static GenerationConstraints.CountRequest request(
            GenerationConstraints.CountMetric metric, int minimum, int maximum) {
        return new GenerationConstraints.CountRequest(metric,
            GenerationConstraints.CountRange.between(minimum, maximum));
    }

    private static List<GenerationConstraints.CountRequest> singletonRequest(
            GenerationConstraints.CountMetric metric,
            GenerationConstraints.CountRange range) {
        return Collections.singletonList(
            new GenerationConstraints.CountRequest(metric, range));
    }

    private static NamedRandomStreams.Concern concern(String token) {
        for (NamedRandomStreams.Concern candidate : NamedRandomStreams.Concern.values()) {
            if (candidate.getToken().equals(token)) return candidate;
        }
        throw new AssertionError("unknown vector concern " + token);
    }

    private static List<String> splitCandidates(String text) {
        return new ArrayList<String>(Arrays.asList(text.split("\\|")));
    }

    private static List<String> sortedUnique(Collection<String> values) {
        ArrayList<String> sorted = new ArrayList<String>(values);
        Collections.sort(sorted);
        return sorted;
    }

    private static String canonicalOf(ChallengeDescriptor descriptor) {
        return descriptor.toCanonical();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String permuteDescriptorLines(String canonical) {
        String[] lines = canonical.split("\\n");
        StringBuilder result = new StringBuilder(lines[0]);
        for (int i = lines.length - 1; i >= 1; i--) {
            result.append('\n').append(lines[i]);
        }
        return result.toString();
    }

    private static String replaceDescriptorField(String canonical, String field, String value) {
        String[] lines = canonical.split("\\n");
        StringBuilder result = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            result.append('\n');
            if (lines[i].startsWith(field + "=")) result.append(field).append('=').append(value);
            else result.append(lines[i]);
        }
        return result.toString();
    }

    private static String fieldValue(String canonical, String field) {
        String[] lines = canonical.split("\\n");
        for (String line : lines) {
            if (line.startsWith(field + "=")) return line.substring(field.length() + 1);
        }
        throw new AssertionError("missing field " + field);
    }

    private static String removeDescriptorField(String canonical, String field) {
        String[] lines = canonical.split("\\n");
        StringBuilder result = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].startsWith(field + "=")) result.append('\n').append(lines[i]);
        }
        return result.toString();
    }

    private static String permuteConstraintFields(String canonical) {
        String[] fields = canonical.split(";");
        StringBuilder result = new StringBuilder(fields[0]);
        for (int i = fields.length - 1; i >= 1; i--) result.append(';').append(fields[i]);
        return result.toString();
    }

    private static String removeConstraintField(String canonical, String field) {
        String[] fields = canonical.split(";");
        StringBuilder result = new StringBuilder(fields[0]);
        for (int i = 1; i < fields.length; i++) {
            if (!fields[i].startsWith(field + "=")) result.append(';').append(fields[i]);
        }
        return result.toString();
    }

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(ChallengeContractException.Code code, Runnable action,
            String message) {
        negativeCases++;
        try {
            action.run();
        } catch (ChallengeContractException exception) {
            assertions++;
            if (exception.getCode() != code) {
                throw new AssertionError(message + ": expected " + code + ", got "
                    + exception.getCode(), exception);
            }
            check(exception.getFieldId() != null && exception.getFieldId().length() != 0,
                message + ": diagnostic field is required");
            return;
        }
        throw new AssertionError(message + ": expected " + code);
    }

    private static void expectUnmodifiable(Runnable action, String message) {
        assertions++;
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void expectIllegalState(Runnable action, String message) {
        assertions++;
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
