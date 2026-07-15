from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EXCLUDED_DIRECTORY_NAMES = {".git", ".venv", "node_modules"}
REQUIRED_DOCUMENTS = {
    Path(f"docs/{number:02d}-{name}.md")
    for number, name in (
        (1, "product-spec"),
        (2, "rastreabilidade"),
        (3, "arquitetura"),
        (4, "modelagem-dados"),
        (5, "estrategia-testes"),
        (6, "seguranca"),
        (7, "resiliencia-observabilidade"),
        (8, "cicd-ambientes"),
        (9, "roadmap-backlog"),
        (10, "runbook"),
        (11, "contratos-integracoes"),
        (12, "referencias-e-evidencias"),
    )
} | {Path("README.md"), Path("docs/openapi.yaml")}


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def duplicate_values(values: list[str]) -> list[str]:
    counts = Counter(values)
    return sorted(value for value, count in counts.items() if count > 1)


def project_markdown_files(root: Path = ROOT) -> list[Path]:
    return sorted(
        path
        for path in root.rglob("*.md")
        if not EXCLUDED_DIRECTORY_NAMES.intersection(path.relative_to(root).parts)
    )


def find_unbalanced_fences(markdown_files: list[Path]) -> list[str]:
    return sorted(
        str(path.relative_to(ROOT))
        for path in markdown_files
        if read_text(path).count("```") % 2 != 0
    )


def find_broken_local_links(markdown_files: list[Path]) -> list[str]:
    failures: list[str] = []
    link_pattern = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
    for path in markdown_files:
        for target in link_pattern.findall(read_text(path)):
            if target.startswith(("http://", "https://", "mailto:", "#")):
                continue
            file_target = target.split("#", maxsplit=1)[0]
            if file_target and not (path.parent / file_target).resolve().exists():
                relative_path = path.relative_to(ROOT)
                failures.append(f"{relative_path} -> {target}")
    return sorted(failures)


def validate_repository() -> list[str]:
    failures: list[str] = []
    markdown_files = project_markdown_files()

    missing_documents = sorted(
        str(path) for path in REQUIRED_DOCUMENTS if not (ROOT / path).exists()
    )
    if missing_documents:
        failures.append(f"documentos obrigatórios ausentes: {missing_documents}")

    failures.extend(
        f"link local quebrado: {failure}"
        for failure in find_broken_local_links(markdown_files)
    )
    failures.extend(
        f"code fence desbalanceada: {failure}"
        for failure in find_unbalanced_fences(markdown_files)
    )

    product = read_text(ROOT / "docs/01-product-spec.md")
    traceability = read_text(ROOT / "docs/02-rastreabilidade.md")
    backlog = read_text(ROOT / "docs/09-roadmap-backlog.md")
    readme = read_text(ROOT / "README.md")
    openapi = read_text(ROOT / "docs/openapi.yaml")

    requirement_definitions = re.findall(
        r"^\| ((?:BR|FR|NFR)-\d{3}) \|", product, flags=re.MULTILINE
    )
    duplicate_requirements = duplicate_values(requirement_definitions)
    if duplicate_requirements:
        failures.append(f"requisitos duplicados: {duplicate_requirements}")

    missing_traceability = sorted(
        requirement
        for requirement in requirement_definitions
        if requirement not in traceability
    )
    if missing_traceability:
        failures.append(f"requisitos sem rastreabilidade: {missing_traceability}")

    task_definitions = re.findall(r"^\| (T-\d{3}) \|", backlog, flags=re.MULTILINE)
    duplicate_tasks = duplicate_values(task_definitions)
    if duplicate_tasks:
        failures.append(f"tarefas duplicadas: {duplicate_tasks}")

    task_references = set(re.findall(r"\bT-\d{3}\b", traceability))
    undefined_tasks = sorted(task_references - set(task_definitions))
    if undefined_tasks:
        failures.append(f"tarefas rastreadas mas não definidas: {undefined_tasks}")

    unindexed_documents = sorted(
        str(path)
        for path in REQUIRED_DOCUMENTS
        if path.suffix == ".md"
        and path != Path("README.md")
        and str(path).replace("\\", "/") not in readme
    )
    if unindexed_documents:
        failures.append(f"documentos ausentes do índice: {unindexed_documents}")

    if not openapi.startswith("openapi: 3.1.1\n"):
        failures.append("OpenAPI deve declarar a versão 3.1.1")
    if not re.search(r"^  /transfer:\n    post:", openapi, flags=re.MULTILINE):
        failures.append("OpenAPI deve conter POST /transfer")
    if "security: []" not in openapi:
        failures.append("OpenAPI deve explicitar autenticação fora do escopo")

    adr_ids = [path.name.split("-", maxsplit=1)[0] for path in (ROOT / "docs/adr").glob("*.md")]
    duplicate_adrs = duplicate_values(adr_ids)
    if duplicate_adrs:
        failures.append(f"ADRs duplicados: {duplicate_adrs}")

    return failures


def main() -> int:
    failures = validate_repository()
    if failures:
        print("Document quality checks failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    markdown_count = len(project_markdown_files())
    task_count = len(
        re.findall(
            r"^\| T-\d{3} \|",
            read_text(ROOT / "docs/09-roadmap-backlog.md"),
            flags=re.MULTILINE,
        )
    )
    requirement_count = len(
        re.findall(
            r"^\| (?:BR|FR|NFR)-\d{3} \|",
            read_text(ROOT / "docs/01-product-spec.md"),
            flags=re.MULTILINE,
        )
    )
    print(
        "Document quality checks passed: "
        f"{markdown_count} Markdown files, "
        f"{requirement_count} requirements, {task_count} tasks."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
