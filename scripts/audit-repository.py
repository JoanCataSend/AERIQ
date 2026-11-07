"""Audita estructura, enlaces y sintaxis disponible sin contactar con servicios."""

import json
import hashlib
import re
import shutil
import subprocess
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "src" / "web"
failures = []
skipped = []


def check(condition, message):
    if not condition:
        failures.append(message)


class References(HTMLParser):
    def __init__(self):
        super().__init__()
        self.refs = []
        self.scripts = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        for key in ("src", "href"):
            if attrs.get(key):
                self.refs.append(attrs[key])
        if tag == "script" and attrs.get("src"):
            self.scripts.append(attrs["src"])


def check_reference(source, reference):
    link = urlsplit(reference)
    if link.scheme or link.netloc or not link.path or link.path.startswith("/"):
        return
    target = (source.parent / unquote(link.path)).resolve()
    check(target.exists(), f"Referencia inexistente: {source.relative_to(ROOT)} -> {reference}")


def run_syntax(binary, paths, flag):
    executable = shutil.which(binary)
    if binary == "php" and not executable and Path("C:/xampp/php/php.exe").is_file():
        executable = "C:/xampp/php/php.exe"
    if not executable:
        skipped.append(f"{binary}: no instalado; sintaxis no comprobada con {flag}")
        return
    for path in paths:
        result = subprocess.run(
            [executable, flag, str(path)], capture_output=True,
            text=True, encoding="utf-8", errors="replace", cwd=ROOT,
        )
        check(result.returncode == 0, f"Sintaxis: {path.relative_to(ROOT)}\n{result.stderr or result.stdout}")


def main():
    required = [
        "README.md", "src/README.md", "src/android/README.md",
        "src/web/README.md", "src/web/server/README.md", "src/web/assets/README.md",
        "src/android/design_assets/README.md", "src/database/README.md",
        "doc/README.md", "doc/arquitectura.md", "doc/web.md", "doc/android.md",
        "doc/api.md", "doc/base-de-datos.md", "doc/despliegue.md", "doc/pruebas.md",
        "doc/estado.md", "doc/contribucion.md", "doc/historico/README.md",
        "scripts/README.md", "scripts/build-plesk-package.py",
        "src/android/settings.gradle.kts", "src/web/api.php", "src/web/auth.php",
        "src/web/mobile-auth.php", "src/web/server/Logica.php",
        "src/web/server/AuthCore.php", "src/web/server/.htaccess", "src/web/vendor/.htaccess",
        "src/web/server/AeriqCore.php", "src/web/server/AeriqHttp.php",
        "src/database/schema.sql", "src/database/seed-demo.sql", "scripts/reset-database.py",
        "scripts/migrate-legacy.php", "doc/tareas-sprint.md",
    ]
    for name in required:
        check((ROOT / name).is_file(), f"Falta: {name}")
    assets_file = ROOT / "scripts/tests/original-assets.json"
    if assets_file.is_file():
        for relative, digest in json.loads(assets_file.read_text(encoding="utf-8")).items():
            asset = (ROOT / relative).resolve()
            check(asset.is_relative_to(ROOT) and asset.is_file() and
                  hashlib.sha256(asset.read_bytes()).hexdigest() == digest,
                  f"Recurso original perdido o cambiado: {relative}")

    documents = [ROOT / "README.md", *sorted((ROOT / "doc").glob("*.md"))]
    documents += [p for p in (ROOT / "src").rglob("README.md") if not any(
        part in {"build", ".gradle", ".kotlin", "vendor", ".idea"}
        for part in p.relative_to(ROOT / "src").parts
    )]
    documents += [ROOT / "scripts" / "README.md", ROOT / "doc/historico/README.md"]
    for doc in documents:
        if not doc.is_file():
            continue
        text = re.sub(r"```[\s\S]*?```", "", doc.read_text(encoding="utf-8-sig"))
        for ref in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
            check_reference(doc, ref)

    pages = sorted(WEB.glob("*.html"))
    check(len(pages) == 12, f"Se esperaban 12 páginas; encontradas {len(pages)}")
    for page in pages:
        parser = References()
        parser.feed(page.read_text(encoding="utf-8-sig"))
        for ref in parser.refs:
            check_reference(page, ref)
        for client in ("auth-client.js", "pbio-client.js"):
            if client in parser.scripts and "app.js" in parser.scripts:
                check("app.js" in parser.scripts and
                      parser.scripts.index(client) < parser.scripts.index("app.js"),
                      f"Orden de scripts incorrecto en {page.name}: {client}")
        check("auth-client.js" in parser.scripts or page.name == "solicitar.html",
              f"Falta cliente de sesión en {page.name}")

    try:
        composer = json.loads((WEB / "composer.json").read_text(encoding="utf-8-sig"))
        check("php" in composer.get("require", {}), "Composer no declara PHP")
    except (OSError, ValueError) as error:
        failures.append(f"composer.json inválido: {error}")

    git = shutil.which("git")
    if git and (ROOT / ".git").exists():
        for name in ("SDBaseDatos.php", "SMTPConfig.php", "MailConfig.php"):
            private = f"src/web/server/{name}"
            result = subprocess.run([git, "check-ignore", "--quiet", private], cwd=ROOT)
            check(result.returncode == 0, f"Configuración privada no ignorada: {name}")
            tracked = subprocess.run([git, "ls-files", "--error-unmatch", private],
                                     cwd=ROOT, capture_output=True)
            check(tracked.returncode != 0, f"Configuración privada versionada: {name}")
    else:
        skipped.append("Git: exclusión de configuraciones privadas no comprobada")

    run_syntax("node", sorted(WEB.glob("*.js")), "--check")
    run_syntax("php", sorted(WEB.glob("*.php")) + sorted((WEB / "server").glob("*.php")), "-l")
    for message in skipped:
        print(f"OMITIDO: {message}")
    for message in failures:
        print(f"ERROR: {message}")
    if failures:
        print(f"Auditoría: {len(failures)} fallo(s).")
        return 1
    print(f"Auditoría correcta: {len(documents)} documentos, {len(pages)} páginas y comprobaciones disponibles.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
