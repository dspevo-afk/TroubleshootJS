"""Pure in-memory binding adaptation for the finite fresh relay diagnostic.

The adapter accepts the borrowed exporter and host-adapter source strings and
returns changed strings plus an exact reversible replacement ledger.  It does
not read or write files, import project code, or perform any runtime work.
"""


def _newline_style(source: str) -> str:
    if "\r\n" in source:
        return "\r\n"
    if "\r" in source:
        return "\r"
    return "\n"


def _with_newlines(text: str, newline: str) -> str:
    return text.replace("\n", newline)


def _replace_once(source: str, old: str, new: str, owner: str,
                  label: str, changes: list) -> str:
    count = source.count(old)
    if count != 1:
        raise ValueError("%s replacement count is %d, expected 1" % (label, count))
    changes.append({"owner": owner, "label": label, "old": old, "new": new})
    return source.replace(old, new, 1)


def adapt(exporter: str, adapter: str) -> dict:
    """Return adapted source strings and an exact reversible change ledger."""
    if not isinstance(exporter, str) or not isinstance(adapter, str):
        raise TypeError("exporter and adapter must be source strings")

    changes = []
    exporter_newline = _newline_style(exporter)
    adapter_newline = _newline_style(adapter)

    exporter_usage = _with_newlines(
        '                "Usage: --family <RC_DELAY|COMPOSED_CONTROLLED_INDICATOR> '
        '--seed <canonical signed-long>");', exporter_newline)
    exporter_usage_new = _with_newlines(
        '                "Usage: --family <RC_DELAY|COMPOSED_CONTROLLED_INDICATOR|'
        'RELAY_OUTPUT> --seed <canonical signed-long>");', exporter_newline)
    exporter = _replace_once(
        exporter, exporter_usage, exporter_usage_new,
        "exporter", "extend_sparse_family_usage", changes)

    exporter_profiles = _with_newlines(
        "        if (RC_FAMILY.equals(family)) profile = DifficultyProfile.EASY.name();\n"
        "        else if (COMPOSED_FAMILY.equals(family)) profile = DifficultyProfile.MEDIUM.name();",
        exporter_newline)
    exporter_profiles_new = _with_newlines(
        "        if (RC_FAMILY.equals(family)) profile = DifficultyProfile.EASY.name();\n"
        "        else if (COMPOSED_FAMILY.equals(family)) profile = DifficultyProfile.MEDIUM.name();\n"
        "        else if (\"RELAY_OUTPUT\".equals(family)) profile = DifficultyProfile.EASY.name();",
        exporter_newline)
    exporter = _replace_once(
        exporter, exporter_profiles, exporter_profiles_new,
        "exporter", "bind_relay_family_to_easy_profile", changes)

    exporter_seed_guard = _with_newlines(
        "        if ((RC_FAMILY.equals(family) && seed != -9187226374781659024L) ||\n"
        "                (COMPOSED_FAMILY.equals(family) && seed != -2750102463469069236L))",
        exporter_newline)
    exporter_seed_guard_new = _with_newlines(
        "        if ((RC_FAMILY.equals(family) && seed != -9187226374781659024L) ||\n"
        "                (COMPOSED_FAMILY.equals(family) && seed != -2750102463469069236L) ||\n"
        "                (\"RELAY_OUTPUT\".equals(family) && seed != -397178238694963708L))",
        exporter_newline)
    exporter = _replace_once(
        exporter, exporter_seed_guard, exporter_seed_guard_new,
        "exporter", "pin_relay_family_seed", changes)

    adapter_family = _with_newlines(
        "    [Parameter(Mandatory=$true)][ValidateSet('COMPOSED_CONTROLLED_INDICATOR')]"
        "[string]$TaskExportFamily,", adapter_newline)
    adapter_family_new = _with_newlines(
        "    [Parameter(Mandatory=$true)][ValidateSet('RELAY_OUTPUT')]"
        "[string]$TaskExportFamily,", adapter_newline)
    adapter = _replace_once(
        adapter, adapter_family, adapter_family_new,
        "adapter", "allow_relay_export_family", changes)

    return {"exporter": exporter, "adapter": adapter, "changes": changes}
