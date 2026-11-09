"""Source-only exact-span emitter; never imports or invokes a Java owner."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE_SHA256 = "4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4"
FRAGMENT_SHA256 = "528a8b5f3cc8130cf20c04538fbaf6afeb1c982d7dc3516a9c73c6514b9e8c72"
SOURCE_BYTES = 182535
SOURCE_LIMIT = 4 * 1024 * 1024
START = b"private static Digest fileDigest(Path path,Meter meter) throws IOException {\n"
AFTER = b"\n\nprivate static String hashString(String value) {"
UNMETERED = (b"private static Digest fileDigest(Path path) throws IOException {\n"
             b"    return fileDigest(path,null);\n}")


def sha256(value):
    return hashlib.sha256(value).hexdigest()


def render(source, fragment):
    """Validate both byte pins before deriving output; return bytes and parity proof."""
    if len(source) != SOURCE_BYTES or len(source) > SOURCE_LIMIT or sha256(source) != SOURCE_SHA256:
        raise ValueError("SHARED_OWNER_SOURCE_PIN")
    if len(fragment) > SOURCE_LIMIT or sha256(fragment) != FRAGMENT_SHA256:
        raise ValueError("RECEIPT_DIGEST_FRAGMENT_PIN")
    if source.count(START) != 1 or source.count(AFTER) != 1 or source.count(UNMETERED) != 1:
        raise ValueError("RECEIPT_DIGEST_SOURCE_BOUNDARY")
    if not fragment.startswith(START) or not fragment.endswith(b"}\n"):
        raise ValueError("RECEIPT_DIGEST_FRAGMENT_BOUNDARY")
    begin = source.index(START)
    end = source.index(AFTER, begin)
    old = source[begin:end]
    if not old.endswith(b"\n}"):
        raise ValueError("RECEIPT_DIGEST_OLD_METHOD_END")
    prefix, suffix = source[:begin], source[end:]
    replacement = fragment[:-1]
    output = prefix + replacement + suffix
    if output[:begin] != prefix or output[begin + len(replacement):] != suffix:
        raise ValueError("RECEIPT_DIGEST_PARITY")
    if output.count(UNMETERED) != 1:
        raise ValueError("RECEIPT_DIGEST_UNMETERED_PARITY")
    proof = {
        "status": "SOURCE_ONLY_EMITTED",
        "sourceSha256": SOURCE_SHA256,
        "fragmentSha256": FRAGMENT_SHA256,
        "emittedSha256": sha256(output),
        "sourceBytes": len(source),
        "emittedBytes": len(output),
        "replacedStartByte": begin,
        "replacedEndByteExclusive": end,
        "oldSpanSha256": sha256(old),
        "replacementSha256": sha256(replacement),
        "unchangedPrefixBytes": len(prefix),
        "unchangedPrefixSha256": sha256(prefix),
        "unchangedSuffixBytes": len(suffix),
        "unchangedSuffixSha256": sha256(suffix),
        "unmeteredOverloadSha256": sha256(UNMETERED),
        "changedOnlyReceiptDigestSpan": True,
        "executionQualification": "NOT_RUN",
    }
    return output, proof


def bounded_read(path):
    if path.stat().st_size > SOURCE_LIMIT:
        raise ValueError("SOURCE_4MIB_LIMIT")
    with path.open("rb") as stream:
        data = stream.read(SOURCE_LIMIT + 1)
    if len(data) > SOURCE_LIMIT:
        raise ValueError("SOURCE_4MIB_LIMIT")
    return data


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "source" / "SHARED_OWNER.java")
    parser.add_argument("--fragment", type=Path, default=ROOT / "ReceiptDigestMembers.java.fragment")
    parser.add_argument("--output", type=Path, default=ROOT / "emitted" / "FailureCohortRoutingStudy.java")
    args = parser.parse_args(argv)
    # No output path, directory or file is created before both inputs validate.
    output, proof = render(bounded_read(args.source), bounded_read(args.fragment))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Existing releases are preserved. A later candidate must use a fresh output.
    with args.output.open("xb") as stream:
        stream.write(output)
    if args.output.read_bytes() != output:
        raise IOError("RECEIPT_DIGEST_EMITTED_READBACK")
    print(json.dumps(proof, sort_keys=True, separators=(",", ":")))
    return proof


if __name__ == "__main__":
    main()
