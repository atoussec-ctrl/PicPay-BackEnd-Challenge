from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scripts.check_docs import duplicate_values, project_markdown_files  # noqa: E402


class DuplicateValuesTest(unittest.TestCase):
    def test_returns_only_sorted_duplicates(self) -> None:
        self.assertEqual(["BR-001", "T-101"], duplicate_values(["T-101", "BR-001", "T-101", "BR-001", "FR-001"]))

    def test_returns_empty_list_when_values_are_unique(self) -> None:
        self.assertEqual([], duplicate_values(["BR-001", "FR-001", "T-101"]))


class FileEncodingTest(unittest.TestCase):
    def test_utf8_round_trip_for_portuguese_documents(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "spec.md"
            content = "transferência atômica e idempotência"
            path.write_text(content, encoding="utf-8")
            self.assertEqual(content, path.read_text(encoding="utf-8"))


class ProjectMarkdownFilesTest(unittest.TestCase):
    def test_ignores_dependency_and_repository_metadata_directories(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            expected = root / "docs" / "spec.md"
            expected.parent.mkdir()
            expected.write_text("# Spec", encoding="utf-8")
            for excluded in ("node_modules", ".git", ".venv"):
                ignored = root / excluded / "README.md"
                ignored.parent.mkdir()
                ignored.write_text("# Ignore", encoding="utf-8")

            self.assertEqual([expected], project_markdown_files(root))


if __name__ == "__main__":
    unittest.main()
