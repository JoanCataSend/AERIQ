"""Genera una carpeta Plesk sin secretos ni documentación, sin sobrescribir."""

import argparse
import shutil
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "src" / "web"


def deploy_files():
    """Lista explícita de código y recursos; no recorre configuración privada."""
    for path in sorted(WEB.iterdir()):
        if path.is_file() and (
            path.suffix in {".html", ".css", ".js", ".php"}
            or path.name in {".htaccess", "composer.json", "composer.lock"}
        ):
            yield path
    for path in sorted((WEB / "assets").rglob("*")):
        if path.is_file() and path.suffix.lower() in {
            ".png", ".jpg", ".jpeg", ".webp", ".svg", ".glb", ".ico",
        }:
            yield path
    for name in (
        "Logica.php", "AuthCore.php", "AeriqCore.php", "AeriqHttp.php", "SDBaseDatos.example.php",
        "SMTPConfig.example.php", "MailConfig.example.php", ".htaccess",
    ):
        yield WEB / "server" / name
    yield WEB / "vendor" / ".htaccess"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default=".dist/plesk/AERIQ")
    args = parser.parse_args()
    output = (ROOT / args.output).resolve()
    dist = (ROOT / ".dist").resolve()
    if output == dist or not output.is_relative_to(dist):
        parser.error("El destino debe ser una subcarpeta de .dist/.")
    if output.exists():
        parser.error("El destino ya existe. Usa --output con una carpeta nueva.")

    files = list(deploy_files())
    for source in files:
        if not source.is_file() or source.is_symlink():
            parser.error(f"Archivo ausente o enlace no admitido: {source.name}")
    for source in files:
        target = output / source.relative_to(WEB)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    print(f"Paquete generado: {output}")
    print(f"Archivos: {len(files)}. Sin configuraciones privadas ni vendor instalado.")
    print("Completa base de datos y correo según doc/despliegue.md.")


if __name__ == "__main__":
    main()
