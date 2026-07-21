#!/usr/bin/env python3
"""Build the governed E2B template without putting the API key in argv or files."""

import os
from pathlib import Path
import sys

from e2b import Template, default_build_logger


def main() -> int:
    if not os.environ.get("E2B_API_KEY"):
        raise RuntimeError("E2B_API_KEY must be set in the current terminal")
    root = Path(__file__).resolve().parent
    os.chdir(root)
    template_name = os.environ.get("YANBAN_E2B_TEMPLATE", "yanban-research-v1")
    template = Template().from_dockerfile("e2b.Dockerfile")
    result = Template.build(
        template,
        template_name,
        cpu_count=2,
        memory_mb=512,
        on_build_logs=default_build_logger(),
    )
    print(f"Template ready: {result.name} ({result.template_id})")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except Exception as error:
        print(f"Template build failed: {type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(1)
