#!/usr/bin/env python3
"""
Telecharge les logos des equipes de l'historique depuis Leaguepedia et genere une
migration Flyway qui les enregistre en base (colonne team.logo).

Les equipes sont celles produites par import_leaguepedia.py (nom actuel, apres
renommages). La migration ne remplit que les equipes SANS logo : un logo deja
present (charge a la main via l'appli) n'est jamais ecrase.

Deux temps :
  1. l'API MediaWiki (imageinfo) donne, 50 fichiers a la fois, l'adresse d'une
     miniature de 256 px et signale les fichiers absents ;
  2. les miniatures sont telechargees sur le CDN de Fandom par un seul curl (le CDN
     refuse les connexions repetees, pas les requetes enchainees sur une connexion).
Tout est mis en cache dans import/cache/ : relancer reprend ou il s'etait arrete.

Usage :
    python import_logos.py > ../backend/src/main/resources/db/migration/V32__team_logos.sql
"""
import base64
import json
import subprocess
import sys
import time
import urllib.parse

import import_leaguepedia as lp

API = "https://lol.fandom.com/api.php"
LOGOS = lp.CACHE / "logos"
WIDTH = 256
# User-Agent court : passe par subprocess sous Windows, la version longue est refusee par le CDN.
USER_AGENT = "lol-results-import/1.0"


def curl(url, output=None):
    """curl plutot qu'urllib : Fandom refuse le client HTTP de Python (403). Renvoie (statut, type)."""
    command = ["curl", "-s", "-L", "-m", "60", "-A", USER_AGENT, "-w", "%{http_code} %{content_type}"]
    command += ["-o", str(output)] if output else ["-o", "NUL" if sys.platform == "win32" else "/dev/null"]
    result = subprocess.run(command + [url], capture_output=True, text=True)
    status, _, content_type = result.stdout.partition(" ")
    return status, content_type.split(";")[0].strip()


def team_names():
    """Noms actuels de toutes les equipes de l'historique importe."""
    names = set()
    for year in range(lp.FIRST_YEAR, lp.LAST_YEAR + 1):
        _, teams = lp.generate(year)
        names.update(t["name"] for t in teams.values())
    return sorted(names)


def images_of(names):
    """Fichier image Leaguepedia de chaque equipe (champ Teams.Image)."""
    def fetch():
        rows = []
        for group in lp.chunks(names, 40):
            rows += lp.cargo("Teams", "OverviewPage,Image", f"OverviewPage IN ({lp.quoted(group)})")
            time.sleep(1)
        return rows
    digest = lp.hashlib.sha1("|".join(names).encode("utf-8")).hexdigest()[:8]
    return {r["OverviewPage"]: r["Image"] for r in lp.cached(f"team_images_{digest}", fetch) if r["Image"]}


def thumbnails_of(images):
    """Adresse de la miniature de chaque fichier (absent du resultat si le fichier n'existe pas)."""
    def fetch():
        thumbs = []
        target = LOGOS.parent / "imageinfo.download.json"
        for group in lp.chunks(sorted(set(images)), 50):
            params = {"action": "query", "format": "json", "prop": "imageinfo", "iiprop": "url|mime",
                      "iiurlwidth": WIDTH, "titles": "|".join("File:" + name for name in group)}
            delay = 20
            while True:
                status, _ = curl(API + "?" + urllib.parse.urlencode(params), target)
                data = json.loads(target.read_text(encoding="utf-8")) if status == "200" else {}
                if "query" in data:
                    break
                print(f"  imageinfo : {status or data.get('error')}, attente {delay}s", file=sys.stderr)
                time.sleep(delay)
                delay = min(delay * 2, 300)
            titles = {n["to"]: n["from"] for n in data["query"].get("normalized", [])}
            for page in data["query"]["pages"].values():
                info = (page.get("imageinfo") or [{}])[0]
                url = info.get("thumburl") or info.get("url")
                if url:
                    original = titles.get(page["title"], page["title"])
                    thumbs.append({"image": original.removeprefix("File:"), "url": url})
            time.sleep(2)
        target.unlink(missing_ok=True)
        return thumbs
    digest = lp.hashlib.sha1("|".join(sorted(set(images))).encode("utf-8")).hexdigest()[:8]
    return {t["image"]: t["url"] for t in lp.cached(f"logo_urls_{digest}", fetch)}


def image_type(data):
    """Type MIME d'apres les premiers octets du fichier."""
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return "image/png"
    if data[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    if data[:4] == b"GIF8":
        return "image/gif"
    if b"<svg" in data[:500]:
        return "image/svg+xml"
    return None


def cached_name(image):
    return "".join(c if c.isalnum() or c in "._-" else "_" for c in image)


def download_all(urls):
    """
    Telecharge toutes les miniatures absentes du cache avec UN SEUL curl : le CDN freine les
    connexions repetees (403), pas les requetes enchainees sur une meme connexion.
    """
    LOGOS.mkdir(parents=True, exist_ok=True)
    for attempt in range(4):
        todo = {image: url for image, url in urls.items() if not (LOGOS / cached_name(image)).exists()}
        if not todo:
            return
        print(f"  {len(todo)} logos a telecharger (passe {attempt + 1})", file=sys.stderr)
        config = LOGOS.parent / "logos.curl"
        with config.open("w", encoding="utf-8") as f:
            for image, url in todo.items():
                target = (LOGOS / (cached_name(image) + ".download")).as_posix()
                f.write(f'url = "{url}"\noutput = "{target}"\n')
        subprocess.run(["curl", "-s", "-L", "-m", "120", "-A", USER_AGENT, "-K", str(config)], capture_output=True)
        config.unlink()
        for image in todo:
            part = LOGOS / (cached_name(image) + ".download")
            if part.exists():
                data = part.read_bytes()
                part.unlink()
                if image_type(data):
                    (LOGOS / cached_name(image)).write_bytes(data)
        time.sleep(30)


def load(image):
    path = LOGOS / cached_name(image)
    if not path.exists():
        return None
    data = path.read_bytes()
    return data, image_type(data)


def main():
    names = team_names()
    images = images_of(names)
    urls = thumbnails_of(images.values())
    out = sys.stdout
    out.reconfigure(encoding="utf-8")
    out.write("-- Logos des equipes de l'historique (Leaguepedia, https://lol.fandom.com), generes par\n")
    out.write("-- import/import_logos.py : NE PAS EDITER A LA MAIN. Seules les equipes sans logo sont\n")
    out.write("-- completees, un logo deja present n'est jamais ecrase.\n\n")
    found = missing = 0
    download_all(urls)
    for name in names:
        image = images.get(name)
        logo = load(image) if image in urls else None
        if not logo:
            missing += 1
            print(f"  pas de logo : {name}", file=sys.stderr)
            continue
        data, content_type = logo
        found += 1
        encoded = base64.b64encode(data).decode("ascii")
        out.write(f"UPDATE team SET logo = decode('{encoded}', 'base64'), logo_content_type = {lp.sql(content_type)} "
                  f"WHERE lower(name) = lower({lp.sql(name[:100])}) AND logo IS NULL;\n")
    print(f"{found} logos, {missing} equipes sans logo", file=sys.stderr)


if __name__ == "__main__":
    main()
