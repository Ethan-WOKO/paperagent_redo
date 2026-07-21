#!/usr/bin/env python3
"""Fixed multi-language runner installed in the E2B template."""

from pathlib import Path, PurePosixPath
import os
import subprocess
import sys


ROOT = Path("/home/user/project")


def trusted_source(value, suffixes):
    relative = PurePosixPath(value)
    if relative.is_absolute() or not relative.parts or any(part in ("", ".", "..") for part in relative.parts):
        raise ValueError("untrusted source path")
    if not any(value.endswith(suffix) for suffix in suffixes):
        raise ValueError("source extension does not match profile")
    target = ROOT.joinpath(*relative.parts).resolve(strict=True)
    target.relative_to(ROOT.resolve(strict=True))
    if not target.is_file():
        raise ValueError("source is not a regular file")
    return target


def run(argv):
    return subprocess.run(argv, cwd=ROOT, check=False).returncode


def main():
    if len(sys.argv) != 3:
        raise ValueError("expected language and one source path")
    language, value = sys.argv[1:]
    if language == "python":
        source = trusted_source(value, (".py",))
        return run(["python3", "-I", str(source)])
    if language == "java":
        source = trusted_source(value, (".java",))
        return run(["java", str(source)])
    output = Path("/tmp") / f"yanban-candidate-{os.getpid()}"
    if language == "c":
        source = trusted_source(value, (".c",))
        compile_code = run(["gcc", "-std=c17", "-O0", "-Wall", "-Wextra", str(source), "-o", str(output)])
    elif language == "cpp":
        source = trusted_source(value, (".cc", ".cpp", ".cxx"))
        compile_code = run(["g++", "-std=c++20", "-O0", "-Wall", "-Wextra", str(source), "-o", str(output)])
    else:
        raise ValueError("unsupported language profile")
    return compile_code if compile_code else run([str(output)])


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except Exception as error:
        print(f"yanban-runner: {error}", file=sys.stderr)
        raise SystemExit(64)
