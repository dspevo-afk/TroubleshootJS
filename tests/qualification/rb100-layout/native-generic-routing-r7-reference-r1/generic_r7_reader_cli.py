# Read-only CLI adapter for the embedded T reader and generic R7 kernel.
# Native execution and host receipt authentication remain separate contracts.

R7_WIRE_SHA256 = "4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741"
R7_CLI_FLAGS = ("--result", "--marker", "--run-id", "--source-sha",
                "--wire-sha", "--fixture-spec", "--base-generator", "--io-helper")
R7_PATH_FLAGS = ("--result", "--marker", "--fixture-spec", "--base-generator",
                 "--io-helper")


class R7CLIError(ValueError):
    def __init__(self, code):
        self.code = code if code.startswith("GR7_CLI_") else "GR7_CLI_" + code
        super().__init__(self.code)


def _r7_need(condition, code):
    if not condition:
        raise R7CLIError(code)


def _r7_raw_path(value, code):
    _r7_need(type(value) is str and bool(value) and "\x00" not in value,
             code + "_VALUE")
    if os.name == "nt":
        _r7_need("/" not in value, code + "_SEPARATOR")
        drive = re.match(r"^[A-Za-z]:\\", value)
        unc = value.startswith("\\\\")
        _r7_need(not value.startswith(("\\\\?\\", "\\\\.\\")),
                 code + "_DEVICE_PATH")
        _r7_need(drive is not None or unc, code + "_ABSOLUTE")
        parts = value[2:].split("\\") if unc else value[3:].split("\\")
        _r7_need(all(part not in ("", ".", "..") and ":" not in part
                     and not part.endswith((" ", ".")) for part in parts),
                 code + "_ALIAS")
    else:
        _r7_need(value.startswith("/") and not value.startswith("//"),
                 code + "_ABSOLUTE")
        parts = value.split("/")[1:]
        _r7_need(all(part not in ("", ".", "..") for part in parts),
                 code + "_ALIAS")
    try:
        path = Path(value)
    except (TypeError, ValueError, OSError):
        raise R7CLIError(code + "_PATH") from None
    _r7_need(path.is_absolute() and str(path) == value, code + "_CANONICAL")
    return path


def _r7_path_identity(value, code):
    path = _r7_raw_path(value, code)
    try:
        resolved = path.resolve(strict=True)
        _r7_need(str(resolved) == str(path), code + "_CANONICAL")
        for item in (path, *path.parents):
            info = item.lstat()
            _r7_need(not stat.S_ISLNK(info.st_mode)
                     and not (getattr(info, "st_file_attributes", 0) & 0x400),
                     code + "_REPARSE")
        info = path.lstat()
    except R7CLIError:
        raise
    except (OSError, RuntimeError, ValueError):
        raise R7CLIError(code + "_PATH") from None
    _r7_need(stat.S_ISREG(info.st_mode)
             and not stat.S_ISLNK(info.st_mode)
             and not (getattr(info, "st_file_attributes", 0) & 0x400),
             code + "_FILE")
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size,
            getattr(info, "st_mtime_ns", None), getattr(info, "st_ctime_ns", None),
            getattr(info, "st_file_attributes", 0))


def parse_r7_cli_args(argv):
    _r7_need(type(argv) in (list, tuple) and len(argv) == 2 * len(R7_CLI_FLAGS),
             "ARGUMENTS")
    options = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        _r7_need(type(flag) is str and flag in R7_CLI_FLAGS and flag not in options
                 and type(value) is str and bool(value) and "\x00" not in value
                 and not value.startswith("--"), "ARGUMENTS")
        options[flag] = value
    _r7_need(set(options) == set(R7_CLI_FLAGS), "ARGUMENTS")
    for flag in R7_PATH_FLAGS:
        _r7_raw_path(options[flag], "PATH")
    _r7_need(type(options["--run-id"]) is str
             and UUID.fullmatch(options["--run-id"]) is not None
             and type(options["--source-sha"]) is str
             and SHA.fullmatch(options["--source-sha"]) is not None
             and options["--wire-sha"] == R7_WIRE_SHA256,
             "EXPECTED_CONTEXT")
    normalized_paths = [os.path.normcase(options[flag]) for flag in R7_PATH_FLAGS]
    _r7_need(len(normalized_paths) == len(set(normalized_paths)), "PATH_ROLE_ALIAS")
    return options


def _r7_checked_read(options, flag):
    return checked_read(options[flag], options["--base-generator"], options["--io-helper"])


def _r7_recheck_inputs(options, identities, original):
    for flag in R7_PATH_FLAGS:
        _r7_need(_r7_path_identity(options[flag], flag[2:].upper().replace("-", "_"))
                 == identities[flag], "INPUT_IDENTITY_CHANGED")
    for flag in ("--result", "--marker", "--fixture-spec"):
        _r7_need(_r7_checked_read(options, flag) == original[flag],
                 "INPUT_BYTES_CHANGED")
    for flag in R7_PATH_FLAGS:
        _r7_need(_r7_path_identity(options[flag], flag[2:].upper().replace("-", "_"))
                 == identities[flag], "INPUT_IDENTITY_CHANGED")


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("GR7_PYTHON_ISOLATION_REQUIRED\n")
        return 2
    started = time.monotonic_ns()
    try:
        options = parse_r7_cli_args(sys.argv[1:] if argv is None else argv)
        identities = {flag: _r7_path_identity(
            options[flag], flag[2:].upper().replace("-", "_")) for flag in R7_PATH_FLAGS}
        _r7_need(len(set(identities.values())) == len(identities), "PATH_ROLE_ALIAS")
        original = {}
        for flag in ("--fixture-spec", "--result", "--marker"):
            original[flag] = _r7_checked_read(options, flag)
            _r7_need(_r7_path_identity(
                options[flag], flag[2:].upper().replace("-", "_")) == identities[flag],
                "INPUT_IDENTITY_CHANGED")
        load_fixture_spec(original["--fixture-spec"])
        summary = validate_result_and_marker(
            original["--result"], original["--marker"], options["--run-id"],
            options["--source-sha"], options["--wire-sha"], original["--fixture-spec"])
        _r7_recheck_inputs(options, identities, original)
        _r7_need(time.monotonic_ns() - started <= MAX_READER_NS, "READER_DEADLINE")
        line = canonical_line(summary)
        output = sys.stdout.buffer
        _r7_need(output.write(line) == len(line), "STDOUT_WRITE")
        output.flush()
        return 0
    except (R7CLIError, ReaderError, GR7_ReaderError) as failure:
        code = getattr(failure, "code", "GR7_INTERNAL")
        if type(code) is not str or not code or any(
                char not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for char in code):
            code = "GR7_INTERNAL"
        sys.stderr.write(code + "\n")
        return 2
    except Exception:
        sys.stderr.write("GR7_INTERNAL\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
