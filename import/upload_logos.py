#!/usr/bin/env python3
"""
Recupere sur Leaguepedia le logo de chaque equipe presente en base et l'envoie a
l'appli (POST /api/teams/{id}/logo, stocke en base dans team.logo).

Contrairement a import_logos.py (qui genere une migration a partir de l'historique
importe), ce script part des equipes reellement en base, y compris celles creees a la
main ou par l'import Excel. Seules les equipes SANS logo sont completees : un logo deja
present n'est jamais ecrase.

Correspondance equipe -> page Leaguepedia : nom de l'equipe (sans tenir compte de la
casse), en suivant les redirections Leaguepedia (anciens noms, variantes). Les
telechargements reutilisent le cache d'import_logos.py (import/cache/logos).

Le backend doit tourner. Usage :
    python upload_logos.py --dry-run     # affiche les correspondances, n'envoie rien
    python upload_logos.py               # envoie les logos trouves
    python upload_logos.py --api http://localhost:8081/api
"""
import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid

import import_leaguepedia as lp
import import_logos as logos

DEFAULT_API = "http://localhost:8080/api"
EXTENSIONS = {"image/png": "png", "image/jpeg": "jpg", "image/webp": "webp", "image/gif": "gif", "image/svg+xml": "svg"}


def api_get(api, path):
    with urllib.request.urlopen(api + path, timeout=30) as response:
        return json.load(response)


def upload(api, team_id, data, content_type):
    """Envoi multipart du fichier, comme le ferait un formulaire d'upload."""
    boundary = uuid.uuid4().hex
    filename = f"logo.{EXTENSIONS.get(content_type, 'img')}"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
            f"Content-Type: {content_type}\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
    request = urllib.request.Request(f"{api}/teams/{team_id}/logo", data=body, method="POST",
                                     headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.status


def leaguepedia_pages():
    """Nom (en minuscules) -> page Leaguepedia de l'equipe : noms, pages et redirections."""
    redirects, pages, _, _ = lp.lineage()
    by_name = {}
    for page, row in pages.items():
        by_name.setdefault(page.lower(), page)
        if row.get("Name"):
            by_name.setdefault(row["Name"].lower(), page)
    for alias, target in redirects.items():
        if target in pages:
            by_name.setdefault(alias.lower(), target)
    return by_name


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default=DEFAULT_API, help=f"adresse de l'API (defaut : {DEFAULT_API})")
    parser.add_argument("--dry-run", action="store_true", help="affiche les correspondances sans rien envoyer")
    args = parser.parse_args()
    # Noms d'equipes non latins : la console Windows (cp1252) ne sait pas tous les afficher
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

    try:
        teams = [t for t in api_get(args.api, "/teams") if not t.get("hasLogo")]
    except urllib.error.URLError as e:
        sys.exit(f"API injoignable ({args.api}) : {e.reason}. Le backend tourne-t-il ?")
    print(f"{len(teams)} equipes sans logo", file=sys.stderr)

    by_name = leaguepedia_pages()
    page_of = {t["id"]: by_name.get(t["name"].lower()) for t in teams}
    unknown = [t for t in teams if not page_of[t["id"]]]

    images = logos.images_of(sorted({p for p in page_of.values() if p}))
    urls = logos.thumbnails_of(list(images.values()))
    logos.download_all(urls)

    sent, without_image, refused = 0, [], []
    for team in sorted(teams, key=lambda t: t["name"].lower()):
        page = page_of[team["id"]]
        if not page:
            continue
        image = images.get(page)
        if image not in urls:
            without_image.append(team)  # pas d'image sur sa fiche Leaguepedia
            continue
        logo = logos.load(image)
        if not logo or not logo[1]:
            refused.append(team)  # telechargement refuse par le CDN : a relancer plus tard
            continue
        data, content_type = logo
        if args.dry_run:
            print(f"  {team['code']:8} {team['name']} -> {page} ({image})")
        else:
            upload(args.api, team["id"], data, content_type)
        sent += 1

    verb = "a envoyer" if args.dry_run else "envoyes"
    print(f"{sent} logos {verb}, {len(refused)} telechargements refuses par le CDN (relancer le script plus "
          f"tard : le cache reprend ou il s'est arrete), {len(without_image)} equipes sans image sur "
          f"Leaguepedia, {len(unknown)} equipes introuvables sur Leaguepedia", file=sys.stderr)
    for team in without_image:
        print(f"  sans image : {team['code']} {team['name']}", file=sys.stderr)
    for team in unknown:
        print(f"  introuvable : {team['code']} {team['name']}", file=sys.stderr)


if __name__ == "__main__":
    main()
