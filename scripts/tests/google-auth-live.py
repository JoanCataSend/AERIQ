#!/usr/bin/env python3
"""Comprueba el despliegue web de Google/Firebase sin usar una cuenta real.

Uso:
    python scripts/tests/google-auth-live.py
    python scripts/tests/google-auth-live.py --base https://ldiamur.upv.edu.es/src/web/

Solo envía un token inválido. No crea usuarios, no modifica la base de datos y
no intenta suplantar una sesión de Google.
"""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Optional


@dataclass
class Response:
    status: int
    content_type: str
    body: str


def request(url: str, opener: urllib.request.OpenerDirector, data: Optional[dict] = None) -> Response:
    payload = None
    headers = {"Accept": "application/json", "User-Agent": "AERIQ-Google-Test/1.0"}
    if data is not None:
        body = dict(data)
        csrf = str(body.pop("_csrf", ""))
        payload = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
        headers["X-CSRF-Token"] = csrf
    req = urllib.request.Request(url, data=payload, headers=headers, method="POST" if data is not None else "GET")
    try:
        with opener.open(req, timeout=20) as response:
            return Response(response.status, response.headers.get("content-type", ""), response.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as error:
        return Response(error.code, error.headers.get("content-type", ""), error.read().decode("utf-8", "replace"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="https://ldiamur.upv.edu.es/src/web/")
    args = parser.parse_args()
    base = args.base.rstrip("/") + "/"

    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    passed = 0
    failed = 0

    def check(condition: bool, label: str, detail: str = "") -> None:
        nonlocal passed, failed
        if condition:
            passed += 1
            print(f"OK   {label}")
        else:
            failed += 1
            print(f"FALLO {label}" + (f" -> {detail}" if detail else ""))

    session = request(base + "auth.php?action=session", opener)
    try:
        session_json = json.loads(session.body)
    except json.JSONDecodeError:
        session_json = {}
    csrf = str(session_json.get("csrf", ""))
    check(session.status == 200 and bool(csrf), "GET auth.php?action=session devuelve CSRF", f"HTTP {session.status}: {session.body[:120]}")

    config = request(base + "auth.php?action=firebase-config", opener)
    try:
        config_json = json.loads(config.body)
    except json.JSONDecodeError:
        config_json = {}
    check(config.status == 200, "GET auth.php?action=firebase-config responde 200", f"HTTP {config.status}: {config.body[:120]}")
    check(config_json.get("configured") is True, "Firebase está configurado en el servidor", config.body[:160])
    check((config_json.get("config") or {}).get("projectId") == "airiq-e9d70", "El endpoint devuelve projectId airiq-e9d70", config.body[:160])

    login = request(base + "login.html", opener)
    check("google-signin" in login.body and "google-auth.js" in login.body, "login.html carga el botón y google-auth.js", f"HTTP {login.status}")

    google_js = request(base + "google-auth.js", opener)
    check(google_js.status == 200 and "signInWithPopup" in google_js.body, "google-auth.js existe y usa el SDK oficial", f"HTTP {google_js.status}")
    check("getIdToken(true)" in google_js.body, "El cliente fuerza un ID token nuevo para evitar repetición", f"HTTP {google_js.status}")

    invalid = request(base + "auth.php?action=google", opener, {"idToken": "token-invalido", "_csrf": csrf})
    try:
        invalid_json = json.loads(invalid.body)
    except json.JSONDecodeError:
        invalid_json = {}
    check(invalid.status == 401, "El backend rechaza un token inválido con 401", f"HTTP {invalid.status}: {invalid.body[:160]}")
    check("error" in invalid_json, "El rechazo devuelve un error JSON controlado", invalid.body[:160])

    for path, forbidden_text, label in (
        ("server/FirebaseConfig.php", "AIza", "FirebaseConfig.php no expone la apiKey"),
        ("server/FirebaseConfig.example.php", "AIza", "FirebaseConfig.example.php no se sirve directamente"),
        ("server/FirebaseAuth.php", "firebase_verify_id_token", "FirebaseAuth.php no expone el código fuente"),
    ):
        response = request(base + path, opener)
        safe = forbidden_text not in response.body
        check(safe, label, f"HTTP {response.status}: {response.body[:120]}")

    print(f"\n{passed} comprobaciones correctas, {failed} fallidas.")
    if failed:
        print("Si el servidor aún ejecuta la versión anterior, despliega .dist/airiq-google-plesk.zip y vuelve a ejecutar.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
