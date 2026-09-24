"""Compile the exact Q30 graph with scratch-only CircuitJS Newton tracing."""
import argparse
import pathlib
import re
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[4]
CLIENT = ROOT / "src/com/lushprojects/circuitjs1/client"
PILOT = ROOT / "docs/task-evidence/Q30/Q30WholeBoardElectricalPilot.java"
DEBUG = pathlib.Path(__file__).resolve().parent


def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError("scratch injection drift: " + label)
    return text.replace(old, new)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--java-home", required=True)
    parser.add_argument("--gwt-home", required=True)
    parser.add_argument("--scenario", choices=("cold", "warm", "baseline"), required=True)
    parser.add_argument("--seeds", default="0,1,3,11")
    parser.add_argument("--trace-seed", type=int, default=-1)
    parser.add_argument("--coarse-cold", action="store_true")
    parser.add_argument("--brownout-hysteresis", action="store_true")
    parser.add_argument("--no-instrument", action="store_true")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="tsj-q30-brownout-") as temp:
        scratch = pathlib.Path(temp)
        files = {p.name: p for p in CLIENT.glob("*.java")}
        files.pop("PhysicalSpecificationDeveloperVerifier.java", None)
        cir = files.pop("CirSim.java").read_text(encoding="utf-8")
        cir, count = re.subn(
            r"public static native void console\(String text\)\s*/\*-\{\s*console\.log\(text\);\s*\}-\*/;",
            "public static void console(String text) { System.err.println(text); }",
            cir,
        )
        if count != 1:
            raise RuntimeError("JSNI logging pattern changed")
        if not args.no_instrument:
            cir = replace_once(cir, "iterationElements[i].doStep();",
                "{ boolean q30PriorConverged = converged;\n"
                "            iterationElements[i].doStep();\n"
                "            if (q30PriorConverged && !converged)\n"
                "                Q30DebugTrace.setter(this, iterationElements[i]); }",
                "solver setter")
        (scratch / "CirSim.java").write_text(cir, encoding="utf-8")

        if not args.no_instrument:
            rail = files.pop("AbstractRailRegulatorElm.java").read_text(encoding="utf-8")
            rail = replace_once(rail,
                "        boolean targetChanged = changed(lastTarget, target);",
                "        Q30DebugTrace.rail(sim, inputVoltage, outputVoltage, enableVoltage, "
                "target, evaluationDrop, requestedDrop, outputCurrent, outputConductance, "
                "targetInputSlope, targetEnableSlope, input.current, input.inputGain, "
                "input.outputGain, input.enableGain);\n"
                "        boolean targetChanged = changed(lastTarget, target);",
                "regulator trace")
            (scratch / "AbstractRailRegulatorElm.java").write_text(rail, encoding="utf-8")

            e04 = files.pop("E04SensorControlModel.java").read_text(encoding="utf-8")
            if args.brownout_hysteresis and (
                    "BROWNOUT_RECOVERY_FRACTION" not in e04):
                e04 = replace_once(e04,
                    "            } else if (railVoltage < minimumOperatingVoltage) {",
                    "            } else if (railVoltage < minimumOperatingVoltage +\n"
                    "                    (controlState == ControlState.BROWNOUT ? .05 : 0.0)) {",
                    "brownout recovery hysteresis")
            e04 = replace_once(e04, "            lastOutput = output;\n            sim.updateVoltageSource",
                "            Q30DebugTrace.decision(sim, this, sensorVoltage, referenceVoltage, "
                "railVoltage, prior, controlState, priorHigh, high, lastOutput, output);\n"
                "            lastOutput = output;\n            sim.updateVoltageSource",
                "decision trace")
            (scratch / "E04SensorControlModel.java").write_text(e04, encoding="utf-8")

        pilot = PILOT.read_text(encoding="utf-8")
        pilot = replace_once(pilot, "        State off = observe(",
            "        Q30DebugTrace.enabled = seed == " + str(args.trace_seed) + ";\n"
            "        Q30DebugTrace.scenario = " + (
                "null" if args.scenario == "baseline" else '"' + args.scenario + '"') + ";\n"
            "        if (Q30DebugTrace.scenario != null) {\n"
            "        if (\"cold\".equals(Q30DebugTrace.scenario)) {\n"
            "            observe(\"cold-fine-4v\", seed, 5, 5, true, true, true, true, 4);\n"
            "        } else observeWarmBrownout(seed, false, true);\n"
            "        } else { State off = observe(", "pilot entry")
        pilot = replace_once(pilot,
            "        System.out.println(\"PASS: Q30 whole-board electrical pilot seed=\" +\n            seed + \" assertions=\" + assertions);",
            "        System.out.println(\"PASS: Q30 whole-board electrical pilot seed=\" +\n"
            "            seed + \" assertions=\" + assertions); }", "pilot end")
        if not args.coarse_cold:
            pilot = replace_once(pilot,
                "        sim.maxTimeStep = 1e-4;\n        sim.minTimeStep = 1e-7;",
                "        sim.maxTimeStep = 5e-6;\n        sim.minTimeStep = 50e-12;",
                "cold fine timestep")
        pilot = replace_once(pilot, "        sim.analyzeCircuit();\n        try {\n            sim.solverExecutor.advanceFor(.03);",
            "        sim.analyzeCircuit();\n"
            "        Q30DebugTrace.phase = \"cold\";\n"
            "        try {\n            sim.solverExecutor.advanceFor(.03);", "cold phase")
        pilot = replace_once(pilot,
            "        sim.solverExecutor.advanceFor(.03);\n        System.out.println(new State(\"before-warm-4v\", candidate));",
            "        Q30DebugTrace.phase = \"startup\";\n"
            "        sim.solverExecutor.advanceFor(.03);\n"
            "        System.out.println(new State(\"before-warm-4v\", candidate));", "warm phase")
        pilot = replace_once(pilot,
            "        sim.analyzeCircuit();\n        try {\n            if (trace) {",
            "        sim.analyzeCircuit();\n"
            "        Q30DebugTrace.phase = \"transition\";\n"
            "        try {\n            if (trace) {", "transition phase")
        pilot = replace_once(pilot,
            "        State state = new State(\"warm-main-4v\", candidate);\n"
            "        System.out.println(state);\n        return state;",
            "        State state = new State(\"warm-main-4v\", candidate);\n"
            "        System.out.println(state);\n"
            "        candidate.assembly.power.getBinding(\"MAIN12\")\n"
            "            .getLimitedSupply().configure(12, .25);\n"
            "        sim.analyzeCircuit();\n"
            "        Q30DebugTrace.phase = \"recovery\";\n"
            "        sim.solverExecutor.advanceFor(.03);\n"
            "        System.out.println(new State(\"recovered-12v\", candidate));\n"
            "        return state;", "recovery")
        (scratch / "Q30WholeBoardElectricalPilot.java").write_text(pilot, encoding="utf-8")

        (scratch / "PhysicalSpecificationDeveloperVerifier.java").write_text(
            "package com.lushprojects.circuitjs1.client; final class "
            "PhysicalSpecificationDeveloperVerifier { static void verify(CirSim sim) "
            "{ throw new UnsupportedOperationException(); } }", encoding="utf-8")
        trace = (DEBUG / "Q30DebugTrace.java").read_text(encoding="utf-8")
        (scratch / "Q30DebugTrace.java").write_text(trace, encoding="utf-8")
        sources = list(files.values()) + list(scratch.glob("*.java"))
        source_list = scratch / "sources.txt"
        source_list.write_text("\n".join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding="utf-8")
        cp = str(scratch) + ";" + ";".join(str(p) for p in pathlib.Path(args.gwt_home).glob("*.jar"))
        javac = pathlib.Path(args.java_home) / "bin/javac.exe"
        java = pathlib.Path(args.java_home) / "bin/java.exe"
        build = subprocess.run([str(javac), "-source", "7", "-target", "7", "-encoding", "UTF-8",
            "-classpath", cp, "-d", str(scratch), "@" + str(source_list)],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        (DEBUG / "javac-log.txt").write_text(build.stdout, encoding="utf-8")
        print("javac exit", build.returncode, flush=True)
        if build.returncode:
            print(build.stdout[-3000:])
            return
        for seed in (int(s) for s in args.seeds.split(",")):
            cmd = [str(java), "-ea", "-cp", cp,
                "com.lushprojects.circuitjs1.client.Q30WholeBoardElectricalPilot",
                "seed=" + str(seed)]
            try:
                run = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    text=True, timeout=150)
                output = run.stdout
                code = str(run.returncode)
            except subprocess.TimeoutExpired as failure:
                output = failure.stdout.decode(errors="replace") if isinstance(failure.stdout, bytes) else failure.stdout or ""
                code = "TIMEOUT"
            tag = args.scenario + ("-coarse" if args.coarse_cold else "") + (
                "-hysteresis" if args.brownout_hysteresis else "") + (
                "-untraced" if args.no_instrument else "")
            (DEBUG / (tag + "-seed" + str(seed) + ".log")).write_text(output, encoding="utf-8")
            print(args.scenario, seed, "exit", code, "tail:", output[-500:], flush=True)


if __name__ == "__main__":
    main()
