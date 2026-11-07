"""CREATE_NEW P9 manifest maker. No compiler, native call or output overwrite.

Initial authority is the caller's reviewed maker/auditor byte hashes, recorded
before this program starts. The maker verifies its pinned auditor's stable
cached bytes; subsequent externally trusted pins bind all seventeen sources.
"""
from __future__ import annotations

import sys
if not (sys.flags.isolated and sys.dont_write_bytecode):
    sys.stderr.buffer.write(b"P9_RESERVATION_INPUT_REJECT P9_INPUT_TOOL_IDENTITY\n")
    sys.stderr.buffer.flush()
    raise SystemExit(1)

import hashlib
import os
from pathlib import Path
import stat
import types

OPTIONS = {"--repository-root", "--p8-pins", "--p8-pins-sha256", "--p9-root", "--python-exe", "--java-home", "--powershell-exe", "--output-pins", "--output-inventory"}
AUDITOR_SHA = "f2080fa993046bb24627233403a3e499ce843044c7403e8bad5bdfe09ea56998"


class BootstrapReject(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def bootstrap_need(ok, code):
    if not ok:
        raise BootstrapReject(code)


def arguments(args):
    bootstrap_need(len(args) == 18, "P9_INPUT_USAGE")
    values = {}
    for i in range(0, len(args), 2):
        key, value = args[i:i + 2]
        bootstrap_need(key in OPTIONS and key not in values and value and not value.startswith("--"), "P9_INPUT_USAGE")
        values[key] = value
    bootstrap_need(set(values) == OPTIONS, "P9_INPUT_USAGE")
    return values


def load_auditor(root_value):
    # No sibling import/path search: compile the same stable bytes read here.
    bootstrap_need(sys.flags.isolated and sys.dont_write_bytecode, "P9_INPUT_TOOL_IDENTITY")
    root = Path(root_value)
    bootstrap_need(root.is_absolute() and str(root).isascii() and len(root.drive) == 2 and root.drive[0].isalpha() and root.drive[1] == ":" and ":" not in str(root)[2:] and ".." not in root.parts, "P9_INPUT_ROOT_BINDING")
    bootstrap_need(not any(tag in part.lower() for part in root.parts for tag in ("hard", "psychotic", "full30", "full-30", "full_30")), "P9_INPUT_PROTECTED_PATH")
    for path in (root, *root.parents):
        info = path.lstat()
        bootstrap_need(not stat.S_ISLNK(info.st_mode) and not getattr(info, "st_file_attributes", 0) & 0x400, "P9_INPUT_ROOT_BINDING")
    root = root.resolve(strict=True)
    bootstrap_need(str(root_value) == str(root), "P9_INPUT_ROOT_BINDING")
    bootstrap_need(root.name == "canonical-reservation-provenance-r1" and Path(__file__).resolve(strict=True) == root / "make_inputs.py", "P9_INPUT_SELF_PIN")
    path = root / "audit_inputs.py"
    before = path.lstat()
    bootstrap_need(stat.S_ISREG(before.st_mode) and not getattr(before, "st_file_attributes", 0) & 0x400 and before.st_size <= 100 * 1024 * 1024, "P9_INPUT_SELF_PIN")
    raw = path.read_bytes()
    after = path.lstat()
    bootstrap_need((before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns) and len(raw) == before.st_size, "P9_INPUT_SELF_PIN")
    bootstrap_need(hashlib.sha256(raw).hexdigest() == AUDITOR_SHA, "P9_INPUT_SELF_PIN")
    module = types.ModuleType("p9_stable_input_auditor")
    module.__file__ = str(path)
    exec(compile(raw, str(path), "exec"), module.__dict__)
    return module, root, raw


def destination(api, value, basename, roots):
    path = Path(value)
    api.need(path.is_absolute() and ".." not in path.parts and path.name == basename, "P9_INPUT_ROOT_BINDING")
    parent = api.canonical(path.parent, "dir", exact=True)
    api.need(str(value) == str(parent / basename), "P9_INPUT_ROOT_BINDING")
    api.need(not path.exists() and not path.is_symlink() and all(not api.below(parent, old) and not api.below(old, parent) for old in roots.values()) and not api.below(parent, Path(roots["SOURCE33"]).parent), "P9_INPUT_ROOT_BINDING")
    api.need(api.below(parent, api.os_temp()), "P9_INPUT_ROOT_BINDING")
    return parent / basename


def write_new(path, raw):
    # xb is CREATE_NEW. Failures retain partial evidence rather than deleting it.
    with path.open("xb") as stream:
        stream.write(raw)
        stream.flush()
        os.fsync(stream.fileno())


def create_inputs(values):
    api, root, auditor_source = load_auditor(values["--p9-root"])
    reads = api.Reads()
    api.need(values["--p8-pins-sha256"] == api.SOURCE33_SHA, "P9_INPUT_DEPENDENCY_CLOSURE")
    context = api.load_existing(reads, values["--p8-pins"], values["--repository-root"])
    for flag, key, kind in (("--python-exe", "pythonExe", "file"), ("--java-home", "javaHome", "dir"), ("--powershell-exe", "powershellExe", "file")):
        api.need(str(api.canonical(values[flag], kind, exact=True)) == context["tools"][key], "P9_INPUT_TOOL_IDENTITY")
    api._attest_tools(context, reads)
    context = api._prepare(context, root, reads)
    api.need(context["filesData"]["audit_inputs.py"] == auditor_source, "P9_INPUT_SELF_PIN")
    inventory_path = destination(api, values["--output-inventory"], "native-source-inventory.jsonl", context["roots"])
    pins_path = destination(api, values["--output-pins"], "input-provenance-pins.json", context["roots"])
    api.need(inventory_path.parent == pins_path.parent and inventory_path != pins_path, "P9_INPUT_ROOT_BINDING")
    inventory_raw = api.inventory_bytes(context["roots"], context["sourceRows"])
    api.need(len(inventory_raw) <= api.MAX_INVENTORY and len(context["sourceRows"]) == 3456 and all(len(line) <= api.MAX_LINE for line in inventory_raw.splitlines(keepends=True)), "P9_INPUT_INVENTORY_ARITHMETIC")
    for row in context["rows"].values():
        reads.read(row["path"], expected=row)
    file_rows = []
    for path in api.top_paths(context, inventory_path):
        raw = inventory_raw if api.normpath(path) == api.normpath(inventory_path) else reads.read(path)
        file_rows.append({"path": path, "bytes": len(raw), "sha256": api.sha(raw)})
    pins = {"schema": api.INPUT_SCHEMA, "sourceParent": api.PARENT,
            "repositoryRoot": context["repositoryRoot"],
            "p8Prerequisite": {"pinsPath": context["p8PinsPath"], "pinsSha256": api.SOURCE33_SHA, "sourceInventorySha256": api.P8_INVENTORY_SHA},
            "publishedP8": api.publication_for(context),
            "sourceInventory": {"path": str(inventory_path), "sha256": api.sha(inventory_raw)},
            "roles": api.roles_for(root, context["repositoryRoot"]),
            "toolPaths": context["p8Pins"]["toolPaths"], "files": file_rows}
    pins_raw = api.json_bytes(pins)
    api.need(len(pins_raw) <= api.MAX_PINS and len(file_rows) == 27, "P9_INPUT_TOP_CENSUS")
    # All genuine/derived inputs have passed before either destination is opened.
    write_new(inventory_path, inventory_raw)
    api.need(reads.read(inventory_path, api.MAX_INVENTORY) == inventory_raw, "P9_INPUT_FILE_HASH")
    write_new(pins_path, pins_raw)
    api.need(reads.read(pins_path, api.MAX_PINS) == pins_raw, "P9_INPUT_FILE_HASH")
    reads.recheck()
    return api.json_bytes({"pinsPath": str(pins_path), "pinsSha256": api.sha(pins_raw), "inventoryPath": str(inventory_path), "sourceInventorySha256": api.sha(inventory_raw), "topFiles": 27, "totalFiles": 3456})


def main():
    try:
        raw = create_inputs(arguments(sys.argv[1:]))
        sys.stdout.buffer.write(b"P9_RESERVATION_INPUTS_CREATED " + raw)
        sys.stdout.buffer.flush()
        return 0
    except Exception as error:
        code = getattr(error, "code", "P9_INPUT_UNEXPECTED_FAILURE")
        if not isinstance(code, str) or not code.isascii() or not code.replace("_", "").isalnum():
            code = "P9_INPUT_UNEXPECTED_FAILURE"
        sys.stderr.buffer.write(("P9_RESERVATION_INPUT_REJECT " + code + "\n").encode("ascii"))
        sys.stderr.buffer.flush()
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
