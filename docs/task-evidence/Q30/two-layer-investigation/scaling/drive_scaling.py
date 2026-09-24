"""Run the temp structural router harness with one bounded JVM per case."""
import argparse
import os
import pathlib
import subprocess
import sys


def parse_numbers(value):
    return [int(item) for item in value.split(",")]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--counts", default="20,25,30,33,35,40,45,50,56,60")
    parser.add_argument("--seeds", default="3,17,42")
    parser.add_argument("--policies", default="RESTRICTED_TWO_LAYER,FULLER_TWO_LAYER")
    parser.add_argument("--output", default="sweep-raw.txt")
    parser.add_argument("--timeout", type=int, default=15)
    parser.add_argument("--source-root", default=".")
    args = parser.parse_args()
    root = pathlib.Path(args.source_root).resolve()
    jdk = pathlib.Path(os.environ["JDK8_HOME"])
    java = jdk / "bin" / "java.exe"
    cp = os.pathsep.join(
        str(path)
        for path in (
            root / "scale-classes",
            root / ".tools" / "gwt-2.7.0" / "gwt-user-2.7.0.jar",
            root / ".tools" / "gwt-2.7.0" / "gwt-dev-2.7.0.jar",
        )
    )
    with (root / args.output).open("w", encoding="utf-8") as raw:
        raw.write("base=e0c368855a3891acd4673e94ce9afa732390e2bf\n")
        raw.write("timeoutPerRowSeconds=%d\n" % args.timeout)
        raw.write("maxExpansions=1000000\n")
        raw.write("policies=unchanged P07 prototype\n")
        for count in parse_numbers(args.counts):
            for seed in parse_numbers(args.seeds):
                for policy in args.policies.split(","):
                    command = [str(java), "-Xmx2g", "-cp", cp,
                               "com.lushprojects.circuitjs1.client.Q30TwoLayerScaling",
                               str(count), str(seed), policy]
                    try:
                        result = subprocess.run(command, cwd=str(root), capture_output=True,
                                                text=True, timeout=args.timeout)
                    except subprocess.TimeoutExpired:
                        line = "SCALE_TIMEOUT count=%d seed=%d policy=%s" % (count, seed, policy)
                    else:
                        stdout = result.stdout.strip()
                        if result.returncode == 0 and stdout.startswith("SCALE_ROW "):
                            line = stdout
                        else:
                            line = "SCALE_ERROR count=%d seed=%d policy=%s exit=%d stdout=%r stderr=%r" % (
                                count, seed, policy, result.returncode, stdout, result.stderr.strip())
                    raw.write(line + "\n")
                    raw.flush()
                    print(line, flush=True)


if __name__ == "__main__":
    main()
