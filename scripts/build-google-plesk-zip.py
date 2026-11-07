"""Genera .dist/airiq-google-plesk.zip para extraer sobre httpdocs/."""

from pathlib import Path
import shutil
import zipfile


ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "src" / "web"
OUTPUT = ROOT / ".dist" / "airiq-google-plesk.zip"
INSTRUCTIONS = ROOT / "doc" / "instrucciones-plesk-google.txt"

# Solo archivos modificados o nuevos para Google/Firebase. La ruta del ZIP es
# relativa a httpdocs/, por lo que se extrae sin crear una carpeta AERIQ.
FILES = [
    "login.html",
    "app.js",
    "auth-client.js",
    "auth.php",
    "mobile-auth.php",
    "styles.css",
    "google-auth.js",
    "server/FirebaseAuth.php",
    "server/FirebaseConfig.example.php",
    "server/AuthCore.php",
    "server/AeriqCore.php",
    "server/.htaccess",
]


def main() -> None:
    missing = [path for path in FILES if not (WEB / path).is_file()]
    if missing:
        raise SystemExit("Faltan archivos: " + ", ".join(missing))
    if not INSTRUCTIONS.is_file():
        raise SystemExit(f"Falta {INSTRUCTIONS}")
    if "server/FirebaseConfig.php" in FILES:
        raise SystemExit("FirebaseConfig.php no debe incluirse en el ZIP")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as package:
        package.write(INSTRUCTIONS, "INSTRUCCIONES-PLESK.txt")
        for relative in FILES:
            source = WEB / relative
            package.write(source, f"src/web/{relative}")

    # Copia separada para phpMyAdmin, fuera de la raíz pública.
    sql_source = ROOT / "src" / "database" / "anadir-firebase-google.sql"
    sql_target = ROOT / ".dist" / "anadir-firebase-google.sql"
    shutil.copy2(sql_source, sql_target)

    print(f"ZIP generado: {OUTPUT}")
    print(f"Archivos web: {len(FILES)} + INSTRUCCIONES-PLESK.txt")
    print(f"SQL separado: {sql_target}")
    print("Extraer sobre httpdocs/. No incluye FirebaseConfig.php ni credenciales privadas.")


if __name__ == "__main__":
    main()
