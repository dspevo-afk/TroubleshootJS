"""Bind the predeclared R12 campaign to actual current-source GWT artifacts."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import read_qualification as reader


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ordered(paths, root):
    # Match Windows OrdinalIgnoreCase over full paths. These build paths are ASCII.
    for path in paths:
        path.relative_to(root).as_posix().encode("ascii")
    return sorted(paths, key=lambda path: str(path.relative_to(root)).replace("/", "\\").upper())


def compiler_input(variant):
    root = reader.ROOT
    records = ["contract=troubleshootjs-production-build-identity-v1", "gwtVersion=2.7.0",
               "target=Compile", "style=OBF", "variant=" + variant,
               "module=com.lushprojects.circuitjs1." + ("developer" if variant == "Developer" else "circuitjs1")]
    source_root = root / "src"
    sources = [path for path in source_root.rglob("*") if path.is_file() and
               "__pycache__" not in path.parts and path.suffix.lower() != ".pyc"]
    definitions = [("src", source_root, sources),
                   ("build", root / "scripts", [root / "scripts/build.ps1"]),
                   ("classpath", root / ".tools/gwt-2.7.0",
                    list((root / ".tools/gwt-2.7.0").glob("*.jar")))]
    reader.need(len(definitions[-1][2]) == 9, "nine pinned compiler jars required")
    for name, base, paths in definitions:
        for path in ordered(paths, base):
            records.append(name + "/" + path.relative_to(base).as_posix() + "=" + file_hash(path))
    return hashlib.sha256("\n".join(records).encode("utf-8")).hexdigest(), len(records) - 6


def output_inventory():
    base = reader.ROOT / "war/circuitjs1"
    paths = ordered([path for path in base.rglob("*") if path.is_file()], base)
    return [{"Path": path.relative_to(base).as_posix(), "Sha256": file_hash(path),
             "Bytes": path.stat().st_size} for path in paths]


def parse_manifest_oracle(path):
    raw = path.read_bytes()
    reader.need(len(raw) <= 1024 * 1024, "native manifest oracle output exceeds reader bound")
    try:
        source = raw.decode("utf-8-sig")
    except UnicodeDecodeError as failure:
        raise ValueError("native manifest oracle output must be raw UTF-8 JSON") from failure

    def pairs(items):
        result = {}
        for key, value in items:
            reader.need(key not in result, "duplicate native manifest JSON field")
            result[key] = value
        return result

    def constant(value):
        raise ValueError("nonfinite native manifest JSON constant: " + value)

    try:
        rows = json.loads(source, object_pairs_hook=pairs, parse_constant=constant)
    except (json.JSONDecodeError, UnicodeDecodeError) as failure:
        raise ValueError("invalid native manifest oracle JSON") from failure
    reader.need(type(rows) is list, "native manifest oracle must be a JSON array")
    expected = [(partition, ordinal, root) for partition, roots in reader.PARTITIONS.items()
                for ordinal, root in enumerate(roots)]
    reader.need(len(rows) == len(expected) == 30,
                "native manifest oracle must contain the exact thirty roots")
    manifests = {partition: [] for partition in reader.PARTITIONS}
    for index, (row, (partition, ordinal, root)) in enumerate(zip(rows, expected)):
        reader.need(type(row) is dict and set(row) == {
            "partition", "ordinal", "root", "manifestHashProtocol", "requestSha256",
            "candidateSha256"} and
            row.get("manifestHashProtocol") == reader.MANIFEST_HASH_PROTOCOL and
            row.get("partition") == partition and type(row.get("ordinal")) is int and
            row["ordinal"] == ordinal and row.get("root") == root,
            "native manifest root/order differs at row " + str(index))
        candidate_count = 1 if partition == "profile" else reader.LIMITS["maximumCandidates"]
        request_digest = row.get("requestSha256")
        candidate_digests = row.get("candidateSha256")
        reader.need(type(request_digest) is str and re.fullmatch(r"[0-9a-f]{64}", request_digest) and
                    type(candidate_digests) is list and len(candidate_digests) == candidate_count and
                    all(type(digest) is str and re.fullmatch(r"[0-9a-f]{64}", digest)
                        for digest in candidate_digests),
                    "native manifest digest census differs at " + partition + "/" + str(ordinal))
        manifests[partition].append({"root": root, "requestSha256": request_digest,
                                     "candidateSha256": candidate_digests})
    reader.validate_manifest_plan(manifests)
    return manifests


def artifact(receipt_path, size_path, inventory_path, variant):
    receipt, receipt_hash = reader.strict_json(receipt_path)
    sizes, size_hash = reader.strict_json(size_path)
    inventory, inventory_hash = reader.strict_json(inventory_path)
    reader.need(set(receipt) == {"Protocol", "Variant", "Style", "InputDigest", "InputFileCount",
                                "OutputDigest", "OutputFileCount"} and
                receipt["Protocol"] == "troubleshootjs-production-build-identity-v1" and
                receipt["Variant"] == variant and receipt["Style"] == "OBF", "build receipt differs")
    digest, count = compiler_input(variant)
    reader.need(receipt["InputDigest"] == digest and type(receipt["InputFileCount"]) is int and
                receipt["InputFileCount"] == count,
                "accepted build inputs differ from current source")
    reader.need(type(receipt["OutputDigest"]) is str and
                re.fullmatch(r"[0-9a-f]{64}", receipt["OutputDigest"]) and
                type(receipt["OutputFileCount"]) is int and receipt["OutputFileCount"] > 0,
                "build receipt output identity is invalid")
    reader.need(type(sizes) is dict and set(sizes) ==
                {"variant", "elapsedSeconds", "cacheCount", "cacheBytes", "files"} and
                sizes.get("variant") == variant and type(sizes.get("cacheCount")) is int and
                sizes["cacheCount"] == 5 and type(sizes.get("files")) is list and
                len(sizes["files"]) == 5,
                "five actual variant cache measurements required")
    elapsed = sizes.get("elapsedSeconds")
    reader.need(type(elapsed) in (int, float) and math.isfinite(float(elapsed)) and elapsed >= 0,
                "build elapsed time is invalid")
    reader.need(type(inventory) is list, "full output inventory array required")
    reader.output_file_records(inventory)
    inventory = reader.canonical_inventory(inventory)
    reader.need(len(inventory) == receipt["OutputFileCount"] and
                reader.inventory_digest(inventory) == receipt["OutputDigest"],
                "full output inventory differs from build receipt")
    by_path = {row["Path"]: row for row in inventory}
    cache_paths = [row["Path"] for row in inventory if row["Path"].endswith(".cache.js")]
    reader.need(len(cache_paths) == 5, "full output inventory does not contain five caches")
    names = []
    cache_bytes = 0
    for row in sizes["files"]:
        reader.need(type(row) is dict and set(row) == {"name", "bytes", "sha256"},
                    "cache size row fields differ")
        name = row.get("name")
        byte_count = row.get("bytes")
        cache_digest = row.get("sha256")
        reader.need(type(name) is str and name.endswith(".cache.js") and
                    re.fullmatch(r"[A-F0-9]{32}\.cache\.js", name) is not None and
                    type(byte_count) is int and byte_count >= 0 and
                    type(cache_digest) is str and re.fullmatch(r"[0-9a-f]{64}", cache_digest),
                    "cache size or digest value is invalid")
        actual = by_path.get(name)
        reader.need(actual is not None and actual["Bytes"] == byte_count and
                    actual["Sha256"] == cache_digest,
                    "cache size/file digest differs from full inventory")
        names.append(name)
        cache_bytes += byte_count
    reader.need(len(set(names)) == 5 and sorted(names) == cache_paths and
                type(sizes.get("cacheBytes")) is int and sizes["cacheBytes"] == cache_bytes,
                "cache census or aggregate size differs")
    return {"inputDigest": digest, "inputFileCount": count, "outputDigest": receipt["OutputDigest"],
            "outputFileCount": receipt["OutputFileCount"], "builds": [name[:-9] for name in cache_paths],
            "files": inventory, "receiptSha256": receipt_hash, "sizeSha256": size_hash,
            "inventorySha256": inventory_hash}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("developer-receipt", "developer-size", "developer-inventory",
                 "player-receipt", "player-size", "player-inventory", "manifests", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    reader.need(not args.output.exists() and not args.output.is_symlink(),
                "refusing to replace an existing frozen plan")
    manifests = parse_manifest_oracle(args.manifests)
    manifest_output_hash = file_hash(args.manifests)
    developer = artifact(args.developer_receipt, args.developer_size,
                         args.developer_inventory, "Developer")
    player = artifact(args.player_receipt, args.player_size, args.player_inventory, "Player")
    deployed = output_inventory()
    reader.need(deployed == developer["files"],
                "actual deployed Developer directory differs from accepted full inventory")
    inputs = {name: file_hash(path) for name, path in reader.input_paths().items()}
    plan = {"schema": "tsj-r12-medium-plan/5", "baseCommit": reader.BASE,
            "partitions": reader.PARTITIONS, "profilePackages": [40, 55, 60],
            "epochs": reader.EPOCHS, "budgets": reader.LIMITS, "manifests": manifests,
            "manifestHashProtocol": reader.MANIFEST_HASH_PROTOCOL,
            "replacementBlindCampaignSha256": reader.REPLACEMENT_BLIND_CAMPAIGN_SHA256,
            "replacementBlind2CampaignSha256": reader.REPLACEMENT_BLIND_2_CAMPAIGN_SHA256,
            "manifestOracleOutputSha256": manifest_output_hash,
            "inputs": inputs, "sourceBinding": reader.binding(inputs),
            "developer": developer, "player": player,
            "readerSha256": file_hash(Path(reader.__file__))}
    reader.validate_plan(plan)
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(plan, indent=2) + "\n")
    print("PASS: frozen finite R12 plan; source, full manifests, caches and both complete build inventories")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError) as failure:
        print("FAIL:", failure)
        raise SystemExit(2)
