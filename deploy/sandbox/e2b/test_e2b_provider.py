import importlib.util
import io
import sys
import types
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path


class FakeCommandExitException(Exception):
    def __init__(self, stderr, stdout, exit_code, error=None):
        super().__init__(error)
        self.stderr = stderr
        self.stdout = stdout
        self.exit_code = exit_code


class E2bProviderCommandTest(unittest.TestCase):
    def load_provider(self):
        fake_e2b = types.ModuleType("e2b")
        fake_e2b.CommandExitException = FakeCommandExitException
        fake_e2b.Sandbox = type("Sandbox", (), {})
        fake_e2b.SandboxQuery = type("SandboxQuery", (), {})
        previous = sys.modules.get("e2b")
        sys.modules["e2b"] = fake_e2b
        try:
            path = Path(__file__).with_name("e2b_provider.py")
            spec = importlib.util.spec_from_file_location("e2b_provider_under_test", path)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            return module
        finally:
            if previous is None:
                sys.modules.pop("e2b", None)
            else:
                sys.modules["e2b"] = previous

    def test_user_command_nonzero_is_returned_without_provider_reclassification(self):
        provider = self.load_provider()
        provider.exact = lambda _name: types.SimpleNamespace(sandbox_id="sandbox-1")

        class Commands:
            def run(self, *_args, **_kwargs):
                raise FakeCommandExitException("compile failed\n", "partial output\n", 1)

        provider.Sandbox = types.SimpleNamespace(
            connect=lambda _sandbox_id: types.SimpleNamespace(commands=Commands())
        )
        stdout = io.StringIO()
        stderr = io.StringIO()
        args = types.SimpleNamespace(name="test", argv=["java", "Sort.java"])

        with redirect_stdout(stdout), redirect_stderr(stderr):
            code = provider.command_exec(args)

        self.assertEqual(1, code)
        self.assertEqual("partial output\n", stdout.getvalue())
        self.assertEqual("compile failed\n", stderr.getvalue())
        self.assertNotIn("E2B provider error", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
