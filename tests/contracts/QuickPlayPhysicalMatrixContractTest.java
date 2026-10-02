package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Vector;

/** Menu-discovered physical population; compiled solver admission is checked separately. */
public final class QuickPlayPhysicalMatrixContractTest {
    private static int checks;
    private static final int TARGET = 20;
    private static final int MAX_ROOTS = 48;
    private static final int SMALL_CATALOG_FAMILY_COUNT = 10;
    private static final String[] CATALOG_FAMILIES = {
        QuickPlayFamilyRegistry.LED_INDICATOR,
        QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR,
        QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR,
        QuickPlayFamilyRegistry.RC_DELAY,
        QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH,
        QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH,
        QuickPlayFamilyRegistry.RELAY_OUTPUT,
        QuickPlayFamilyRegistry.SENSOR_CONTROL,
        Rb15Plan.FAMILY_ID,
        ControlledIndicatorBlockContributions.FAMILY_ID,
        Rb30Plan.FAMILY_ID
    };

    public static void main(String[] args) {
        Vector<String> families = selectedFamilies(args);
        CirSim sim = new CirSim();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
        CircuitElm.sim = sim;
        int pairs = 0;
        for (String family : families) {
            DifficultyProfile profile = PlayerFamilyCatalog.candidateProfile(family);
            check(profile.isAvailable() && QuickPlayAdmission.supports(family, profile),
                "Menu family has no procedural admission: " + family);
            HashSet<String> physical = new HashSet<String>();
            HashSet<String> novelty = new HashSet<String>();
            HashSet<String> placements = new HashSet<String>();
            HashSet<String> routes = new HashSet<String>();
            HashSet<String> copper = new HashSet<String>();
            HashSet<String> macros = new HashSet<String>();
            HashSet<String> sizes = new HashSet<String>();
            int accepted = 0, rejected = 0, roots = 0;
            long elapsed = 0;
            while (accepted < TARGET && roots < MAX_ROOTS) {
                long seed = root(roots++);
                long started = System.currentTimeMillis();
                try {
                    GeneratedBoardInstance board = generate(family, profile, seed);
                    board.requireNormalPhysicalAdmission();
                    board.getPcbLayout().validateGeometry(board.getBoard());
                    String signature = PhysicalBoardFingerprint.of(board);
                    String placement = placement(board);
                    String route = routes(board);
                    String macro = macro(board);
                    Rectangle outline = board.getPcbLayout().getBoardOutline();
                    check(board.getPcbLayout().getGenerationPlacementAttempts() > 0 &&
                        board.getPcbLayout().getGenerationRoutingAttempts() > 0,
                        "Shared placement/routing not used: " + family);
                    check(board.getSeed() == seed && board.getCircuitFamilyId().equals(family),
                        "Root or family changed during construction");
                    check(signature.equals(PhysicalBoardFingerprint.of(replay(family, profile, seed))),
                        "Exact replay changed physical realization: " + family + '/' + seed);
                    check(physical.add(signature), "Duplicate physical realization: " + family + '/' + seed);
                    check(novelty.add(PhysicalBoardFingerprint.novelty(board)),
                        "Translated or outline-only PCB clone: " + family + '/' + seed);
                    placements.add(placement); routes.add(route);
                    copper.add(PhysicalBoardFingerprint.copperUnion(board.getPcbLayout(),
                        outline.x, outline.y));
                    macros.add(macro);
                    sizes.add(outline.width + "x" + outline.height);
                    accepted++;
                    elapsed += System.currentTimeMillis() - started;
                } catch (PcbRoutingRejectedException rejectedRoute) {
                    rejected++;
                    System.out.println("PHYSICAL_REJECT|" + family + '|' + profile + '|' + seed + "|ROUTE|" + rejectedRoute.getMessage());
                } catch (SupportedEnvelope.Rejected rejectedEnvelope) {
                    rejected++;
                    System.out.println("PHYSICAL_REJECT|" + family + '|' + profile + '|' + seed + "|ENVELOPE|" + rejectedEnvelope.getMessage());
                } catch (GenerationJob.Rejected rejectedNormalPolicy) {
                    check(Rb30Plan.FAMILY_ID.equals(family) &&
                        "Normal medium admission requires a selected P07 two-layer route".equals(
                            rejectedNormalPolicy.getMessage()),
                        "Unexpected normal-policy rejection: " + family);
                    rejected++;
                    System.out.println("PHYSICAL_REJECT|" + family + '|' + profile + '|' + seed + "|NORMAL_POLICY|" + rejectedNormalPolicy.getMessage());
                }
            }
            check(accepted == TARGET, "Fewer than 20 valid physical boards: " + family);
            check(physical.size() == TARGET && novelty.size() == TARGET &&
                placements.size() >= 12 && routes.size() >= 12 && copper.size() >= 12,
                "Placement or copper diversity collapsed: " + family);
            check(macros.size() >= 6, "Relative component layouts collapsed: " + family);
            check(sizes.size() >= 2, "All board outlines are fixed: " + family);
            System.out.println("PHYSICAL_MATRIX|" + family + '|' + profile + '|' + accepted +
                '|' + physical.size() + '|' + placements.size() + '|' + routes.size() +
                '|' + copper.size() + '|' + macros.size() + '|' + sizes.size() + '|' + rejected +
                '|' + roots + '|' + elapsed);
            pairs++;
        }
        check(pairs == families.size(), "Selected menu matrix was not exhausted");
        System.out.println("PASS: Quick Play physical matrix contracts pairs=" + pairs + " assertions=" + checks);
    }

    private static Vector<String> selectedFamilies(String[] args) {
        // Keep the implemented-family physical corpus even when normal
        // publication of one of those families is blocked.
        Vector<String> catalog = PlayerFamilyCatalog.registeredFamilies();
        check(catalog.size() == CATALOG_FAMILIES.length,
            "Player family catalog size changed: expected " + CATALOG_FAMILIES.length +
            " but found " + catalog.size());
        for (int i = 0; i < CATALOG_FAMILIES.length; i++)
            check(CATALOG_FAMILIES[i].equals(catalog.get(i)),
                "Player family catalog order changed at index " + i);

        Vector<String> selected = new Vector<String>();
        if (args.length == 0) {
            selected.addAll(catalog);
        } else if (args.length == 1 && "--small-catalog".equals(args[0])) {
            for (int i = 0; i < SMALL_CATALOG_FAMILY_COUNT; i++) selected.add(catalog.get(i));
        } else if (args.length == 2 && "--family".equals(args[0]) &&
                catalog.contains(args[1])) {
            selected.add(args[1]);
        } else {
            throw new IllegalArgumentException("Usage: QuickPlayPhysicalMatrixContractTest " +
                "[--small-catalog | --family <catalog-family-id>]");
        }

        check(!selected.isEmpty(), "Selected family list is empty");
        for (String family : selected)
            check(catalog.contains(family), "Selected family is outside the current catalog: " + family);
        if (args.length == 0)
            check(selected.equals(catalog), "Default selection must cover the full catalog");
        if (args.length == 1 && "--small-catalog".equals(args[0])) {
            check(selected.size() == SMALL_CATALOG_FAMILY_COUNT,
                "Small catalog must contain the prior nine leaves and composed family");
            for (int i = 0; i < SMALL_CATALOG_FAMILY_COUNT; i++)
                check(selected.get(i).equals(catalog.get(i)),
                    "Small catalog selection changed at index " + i);
        }
        return selected;
    }

    private static GeneratedBoardInstance generate(String family, DifficultyProfile profile, long seed) {
        PlayerLaunchRequest launch = PlayerLaunchRequest.random(family, Long.toString(seed), profile.name());
        return construct(launch.generation().candidate(0));
    }

    private static GeneratedBoardInstance replay(String family, DifficultyProfile profile, long seed) {
        PlayerLaunchRequest launch = PlayerLaunchRequest.random(family, Long.toString(seed), profile.name());
        PlayerLaunchRequest exact = PlayerLaunchRequest.parse(launch.accepted(seed).replay());
        check(!exact.candidateSearch && exact.seed == seed && exact.familyId.equals(family) &&
            exact.profile == profile, "Accepted replay descriptor changed identity");
        return construct(exact.generation());
    }

    private static GeneratedBoardInstance construct(GenerationRequest request) {
        GenerationRequest.Prepared prepared = request.resolve(new GenerationRequest.PlanCache());
        if (request.getExecutionPolicy() != GenerationExecutionPolicy.NORMAL_MEDIUM)
            return prepared.construct().instance;
        GenerationRequest.ConstructionSession session = prepared.beginConstruction();
        int steps = 0;
        while (!session.advance()) {
            if (++steps > GenerationCoordinator.MAX_JOB_STEPS)
                throw new AssertionError("Normal Q30 route exceeded the unchanged shared step bound");
        }
        return session.result().instance;
    }

    private static long root(int ordinal) {
        if (ordinal == 0) return 0;
        if (ordinal == 1) return 17;
        if (ordinal == 2) return Long.MIN_VALUE;
        if (ordinal == 3) return Long.MAX_VALUE;
        long z = 0x632be59bd9b4e019L + 0x9e3779b97f4a7c15L * ordinal;
        z = (z ^ z >>> 30) * 0xbf58476d1ce4e5b9L;
        z = (z ^ z >>> 27) * 0x94d049bb133111ebL;
        return z ^ z >>> 31;
    }

    private static String placement(GeneratedBoardInstance board) {
        PcbBoardLayout layout = board.getPcbLayout(); Rectangle o = layout.getBoardOutline();
        Vector<String> ids = board.getBoard().getComponentIds(); Collections.sort(ids);
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            PcbComponentPlacement p = layout.getComponent(id);
            out.append(id).append(':').append(p.getX() - o.x).append(',').append(p.getY() - o.y)
                .append(':').append(p.getRotation()).append(':').append(p.getMountingSide()).append(';');
        }
        return out.toString();
    }

    private static String routes(GeneratedBoardInstance board) {
        PcbBoardLayout layout = board.getPcbLayout(); Rectangle o = layout.getBoardOutline();
        Vector<String> paths = new Vector<String>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            StringBuilder path = new StringBuilder(trace.getNetId()).append(':');
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            for (int i = 0; i < x.length; i++) path.append(x[i] - o.x).append(',').append(y[i] - o.y).append('/');
            paths.add(path.toString());
        }
        Collections.sort(paths);
        return paths.toString();
    }

    private static String macro(GeneratedBoardInstance board) {
        PcbBoardLayout layout = board.getPcbLayout();
        Vector<String> ids = board.getBoard().getComponentIds(); Collections.sort(ids);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) for (int j = i + 1; j < ids.size(); j++) {
            PcbComponentPlacement a = layout.getComponent(ids.get(i)), b = layout.getComponent(ids.get(j));
            out.append(Integer.signum(a.getX() - b.getX())).append(',')
                .append(Integer.signum(a.getY() - b.getY())).append(';');
        }
        return out.toString();
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
