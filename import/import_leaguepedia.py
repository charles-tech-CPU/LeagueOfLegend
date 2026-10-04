#!/usr/bin/env python3
"""
Importe l'historique des tournois depuis Leaguepedia (https://lol.fandom.com) et
genere une migration Flyway par saison.

Ce qui est importe : les ligues majeures (1re division de chaque region) et les
evenements internationaux officiels, plus les grands tournois hors Riot jusqu'en
2012 (IEM, IPL, MLG, DreamHack...). Les qualifications et promotions sont exclues.

Chaque tournoi est importe AVEC sa structure mais SANS les scores :
  * poules / saison reguliere : toutes les rencontres, equipes connues ;
  * swiss : toutes les rencontres reelles, equipes connues ;
  * playoffs : le bracket complet, relie (nextMatch / loserNextMatch) d'apres les
    vrais vainqueurs, mais seules les equipes entrant dans le bracket sont placees :
    les tours suivants se remplissent au fil des scores saisis dans l'appli.

Les donnees viennent de Special:CargoExport (l'API api.php limite trop les requetes
anonymes) et sont mises en cache dans import/cache/ (relancer reprend ou il s'etait arrete).

Usage :
    python import_leaguepedia.py fetch 2013          # telecharge (lent, rejouable)
    python import_leaguepedia.py sql 2013 > ../backend/src/main/resources/db/migration/V16__leaguepedia_2013.sql
    python import_leaguepedia.py preview 2013        # resume lisible de ce qui sera genere
"""
import hashlib
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import date as date_cls, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

EXPORT = "https://lol.fandom.com/wiki/Special:CargoExport"
USER_AGENT = "lol-results-import/1.0 (projet personnel non commercial)"
CACHE = Path(__file__).parent / "cache"
PARIS = ZoneInfo("Europe/Paris")

SPLIT_WORDS = ("Winter", "Spring", "Summer")
FIRST_YEAR, LAST_YEAR = 2011, 2025

# Codes courts des ligues et evenements (ceux de l'appli quand ils existent deja).
LEAGUE_CODES = {
    "World Championship": "WORLDS",
    "Mid-Season Invitational": "MSI",
    "All-Star Event": "ALL-STAR",
    "Rift Rivals": "RIFT RIVALS",
    "Mid-Season Cup": "MSC",
    "LoL The Champions": "Champions",
    "LoL Champions Korea": "LCK",
    "Tencent LoL Pro League": "LPL",
    "North America League Championship Series": "NA LCS",
    "League of Legends Championship Series": "LCS",
    "Europe League Championship Series": "EU LCS",
    "LoL EMEA Championship": "LEC",
    "League of Legends European Championship": "LEC",
    "LoL Master Series": "LMS",
    "Pacific Championship Series": "PCS",
    "League of Legends Championship Pacific": "LCP",
    "Garena Premier League": "GPL",
    "Vietnam Championship Series": "VCS",
    "Vietnam Championship Series A": "VCS",
    "Circuit Brazilian League of Legends": "CBLOL",
    "Campeonato Brasileiro de League of Legends": "CBLOL",
    "Turkish Championship League": "TCL",
    "LoL Japan League": "LJL",
    "Oceanic Pro League": "OPL",
    "League of Legends Circuit Oceania": "LCO",
    "League of Legends Continental League": "LCL",
    "Liga Latinoamerica": "LLA",
    "Copa Latinoamérica Sur": "CLS",
    "Liga Latinoamérica Norte": "LLN",
    "Intel Extreme Masters": "IEM",
    "IGN Pro League": "IPL",
    "MLG 2012 Fall": "MLG",
    "DreamHack": "DreamHack",
    "OGN": "OGN",
    "Battle of the Atlantic": "BotA",
    "National Electronic Sports Tournament": "NEST",
    "Latin America Cup": "CLA",
    "Latin American Regional Finals": "RF",
    "Brazil Regional Finals": "RF",
    "2014 International Wildcard Tournament": "IWCT",
    "International Wildcard Tournament": "IWCT",
    "International Wildcard Invitational": "IWCI",
    "International Wildcard Tournament Chile": "IWCT",
    "International Wildcard Tournament Turkey": "IWCT",
    "2016 International Wildcard Qualifier": "IWCQ",
    "Taiwan Regional Finals 2015": "RF",
    "Copa Latinoamérica Norte": "CLN",
    "LoL Circuit Oceania": "LCO",
    "LoL Continental League": "LCL",
    "SLTV StarSeries": "SLTV",
    "League of Legends SEA Tour": "LST",
    "SEA Tour": "LST",
    "League of Legends Championship of The Americas": "LTA",
    "League of Legends Championship of The Americas North": "LTA N",
    "League of Legends Championship of The Americas South": "LTA S",
    "Mid-Season Cup 2020": "MSC",
    "First Stand": "FST",
}

# Onglets Leaguepedia qui designent une phase a elimination (playoffs, bracket...).
KO_TAB = re.compile(
    r"final|semi|quarter|round of \d|ro\d|playoff|bracket|knockout|elimination|upper|lower|"
    r"third|3rd|decider|gauntlet|qualification match|grand",
    re.I,
)
TIEBREAK_TAB = re.compile(r"tie-?break", re.I)
PLAY_IN_TAB = re.compile(r"play-?in", re.I)
# Matchs de classement (3e, 5e place...) : hors du chemin vers le titre.
THIRD_PLACE_TAB = re.compile(r"third|3rd|fifth|5th|place match", re.I)

KO_LABELS = [
    (re.compile(r"^(grand )?finals?$", re.I), "Finale"),
    (re.compile(r"semi", re.I), "Demi-finale"),
    (re.compile(r"quarter", re.I), "Quart de finale"),
    (re.compile(r"round of 16|ro16", re.I), "Huitieme de finale"),
    (re.compile(r"third|3rd", re.I), "Petite finale"),
    (re.compile(r"fifth|5th", re.I), "Match pour la 5e place"),
]


# ---------------------------------------------------------------------------
# Acces a l'API Cargo (avec cache et attente si l'API limite le debit)
# ---------------------------------------------------------------------------

def normalize(row):
    """Champs 'DateTime UTC' -> 'DateTime_UTC', valeurs en texte (l'export renvoie des nombres)."""
    return {k.replace(" ", "_"): ("" if v is None else str(v)) for k, v in row.items()}


def cargo(tables, fields, where, order_by="", limit=500):
    """Requete via Special:CargoExport, bien moins limitee que l'API pour un usage anonyme."""
    rows, offset = [], 0
    while True:
        params = {"tables": tables, "fields": fields, "where": where, "format": "json",
                  "limit": limit, "offset": offset}
        if order_by:
            params["order by"] = order_by
        url = EXPORT + "?" + urllib.parse.urlencode(params)
        delay = 20
        while True:
            try:
                req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                data = json.load(urllib.request.urlopen(req, timeout=120))
                break
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
                print(f"  erreur ({e}), nouvel essai dans {delay}s", file=sys.stderr)
                time.sleep(delay)
                delay = min(delay * 2, 300)
        batch = [normalize(r) for r in data]
        rows.extend(batch)
        if len(batch) < limit:
            return rows
        offset += limit
        time.sleep(2)


def cached(name, fetch):
    path = CACHE / f"{name}.json"
    if path.exists():
        return [normalize(r) for r in json.loads(path.read_text(encoding="utf-8"))]
    print(f"Telechargement : {name}", file=sys.stderr)
    rows = fetch()
    CACHE.mkdir(exist_ok=True)
    path.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
    time.sleep(2)
    return rows


def quoted(values):
    return ",".join("'" + v.replace("'", "''") + "'" for v in values)


def chunks(values, size):
    values = list(values)
    for i in range(0, len(values), size):
        yield values[i:i + size]


# ---------------------------------------------------------------------------
# Selection et regroupement des tournois en competitions
# ---------------------------------------------------------------------------

def tournaments():
    return cached("tournaments", lambda: cargo(
        "Tournaments",
        "Name,OverviewPage,DateStart,Date,League,Region,Split,SplitNumber,Year,TournamentLevel,"
        "IsQualifier,IsPlayoffs,IsOfficial",
        f"Year>={FIRST_YEAR} AND Year<={LAST_YEAR} AND TournamentLevel='Primary'",
        "DateStart"))


def is_major_league(league):
    """Liste blanche : ligues de 1re division et evenements internationaux (cf. LEAGUE_CODES)."""
    return league in LEAGUE_CODES or league.startswith("Rift Rivals")


def is_selected(t):
    if t["IsQualifier"] == "1" or re.search(r"qualifier|promotion|relegation", t["Name"], re.I):
        return False
    if not is_major_league(t["League"]):
        return False
    if t["IsOfficial"] == "1":
        return True
    # Avant les ligues officielles (2013), les grands tournois de l'epoque etaient hors Riot.
    return int(t["Year"]) <= 2012


def split_of(t):
    if t["IsOfficial"] != "1":
        return None
    for word in SPLIT_WORDS:
        if word in (t["Split"] or "") or re.search(rf"\b{word}\b", t["Name"]):
            return word.upper()
    return None


def assign_splits(comps):
    """
    Ligues sans Winter/Spring/Summer dans le nom (Split 1/2/3, Opening/Closing, LCK Cup / Rounds...) :
    leurs competitions de l'annee sont reparties dans l'ordre, Winter/Spring/Summer s'il y en a
    trois, Spring/Summer s'il y en a deux, sinon d'apres le mois de debut.
    """
    by_league = defaultdict(list)
    for c in comps:
        first = c["pages"][0]
        regional = first["Region"] not in ("International", "Wildcard") and first["IsOfficial"] == "1"
        if c["split"] is None and regional and "Regional Finals" not in first["Name"]:
            by_league[first["League"]].append(c)
    for league_comps in by_league.values():
        league_comps.sort(key=lambda c: c["pages"][0]["DateStart"] or "")
        numbers = {c["id"]: split_number(c["pages"][0]["Name"]) for c in league_comps}
        if any(numbers.values()):
            # "Split 1/2/3", "Season 1/2", "Opening/Closing" : le numero prime sur l'ordre.
            three = max(n or 0 for n in numbers.values()) >= 3
            order = {1: "WINTER", 2: "SPRING", 3: "SUMMER"} if three else {1: "SPRING", 2: "SUMMER"}
            for c in league_comps:
                n = numbers[c["id"]]
                c["split"] = order.get(n)  # sans numero (Post-Season, Placements...) : hors split
            continue
        if len(league_comps) == 3:
            names = ["WINTER", "SPRING", "SUMMER"]
        elif len(league_comps) == 2:
            names = ["SPRING", "SUMMER"]
        else:
            names = [month_split(c["pages"][0]["DateStart"]) for c in league_comps]
        for c, split in zip(league_comps, names):
            c["split"] = split


def split_number(name):
    hit = re.search(r"\b(?:Split|Season) (\d)\b", name)
    if hit:
        return int(hit.group(1))
    if re.search(r"\bOpening\b", name):
        return 1
    if re.search(r"\bClosing\b", name):
        return 2
    return None


def month_split(date):
    month = int((date or "2000-06-01")[5:7])
    if month in (11, 12, 1, 2):
        return "WINTER"
    return "SPRING" if month <= 5 else "SUMMER"


def season_of(t):
    """Saison : l'annee du nom si elle y figure ("TCL 2014 Summer Playoffs" est classe 2015 par Leaguepedia)."""
    hit = re.search(r"\b(20\d\d)\b", t["Name"])
    return int(hit.group(1)) if hit else int(t["Year"])


def competitions_of(year):
    """Competitions d'une saison : un split de ligue (saison reguliere + playoffs) ou un evenement."""
    selected = [t for t in tournaments() if season_of(t) == year and is_selected(t)]
    selected.sort(key=lambda t: (t["DateStart"] or "", t["Name"]))
    comps, by_key = [], {}
    for t in selected:
        split = split_of(t)
        # L'annee du nom (ex: "TCL 2014 Summer Playoffs" classe en saison 2015) distingue les splits.
        name_year = re.search(r"\b(20\d\d)\b", t["Name"])
        if split:
            key = (t["League"], split, name_year.group(1) if name_year else t["Year"])
        elif t["IsPlayoffs"] == "1" and (t["League"], None) in by_key:
            key = (t["League"], None)  # playoffs d'une ligue sans split : avec sa saison reguliere
        else:
            key = ("page", t["OverviewPage"])
        if key not in by_key:
            by_key[key] = {"id": len(comps), "split": split, "pages": []}
            comps.append(by_key[key])
            if not split and t["IsPlayoffs"] != "1":
                by_key[(t["League"], None)] = by_key[key]
        by_key[key]["pages"].append(t)
    assign_splits(comps)
    return comps


# ---------------------------------------------------------------------------
# Matchs et equipes
# ---------------------------------------------------------------------------

MATCH_FIELDS = ("OverviewPage,Team1,Team2,Winner,BestOf,DateTime_UTC,Tab,Round,Phase,N_MatchInTab,"
                "N_TabInPage,N_Page,IsTiebreaker,MatchId")


def matches_of(year, pages):
    def fetch():
        rows = []
        for group in chunks(sorted(pages), 12):
            rows += cargo("MatchSchedule", MATCH_FIELDS, f"OverviewPage IN ({quoted(group)})", "DateTime_UTC")
            time.sleep(2)
        return rows
    # L'empreinte de la liste des pages invalide le cache si la selection de la saison change.
    digest = hashlib.sha1("|".join(sorted(pages)).encode("utf-8")).hexdigest()[:8]
    return cached(f"matches_{year}_{digest}", fetch)


# Un renommage ou un changement de marque garde l'equipe ; un rachat (acquire) cree une
# nouvelle equipe, sauf lien explicite dans team_lineage.json.
SAME_TEAM_VERBS = {"rename", "rebrand", "reband"}


def lineage():
    """Tables globales des equipes : redirections, renommages, fiches (code court, region)."""
    redirects = {r["AllName"]: r["Target"] for r in cached("team_redirects", lambda: cargo(
        "TeamRedirects", "AllName,_pageName=Target", "AllName IS NOT NULL", "AllName"))}
    pages = {r["OverviewPage"]: r for r in cached("teams_all", lambda: cargo(
        "Teams", "OverviewPage,Name,Short,Region,RenamedTo,IsDisbanded", "OverviewPage IS NOT NULL", "OverviewPage"))}
    renames = cached("team_renames", lambda: cargo(
        "TeamRenames", "Date,OriginalName,NewName,Verb,IsSamePage", "Date IS NOT NULL", "Date"))
    links = [(r["OriginalName"], r["NewName"], (r["Date"] or "")[:10]) for r in renames
             if (r["Verb"] or "").lower() in SAME_TEAM_VERBS]
    manual = json.loads((Path(__file__).parent / "team_lineage.json").read_text(encoding="utf-8"))["links"]
    links += [(m["from"], m["to"], m["date"]) for m in manual]
    links += [(p, r["RenamedTo"], "") for p, r in pages.items() if r["RenamedTo"]]
    successor = {}
    for orig, new, date in sorted(links, key=lambda x: x[2]):
        for key in {orig, redirects.get(orig, orig)}:
            if key != redirects.get(new, new):  # renommage sur la meme page : pas de boucle
                successor[key] = (new, date)
    return redirects, pages, successor, links


def canonical(name, redirects, successor, cuts=frozenset()):
    """
    Nom actuel de l'equipe, en suivant redirections et renommages jusqu'au bout. Un nom de
    `cuts` arrete la lignee : equipe fusionnee plus tard avec une adversaire (voir lineage_cuts).
    """
    if name in cuts:
        return name
    current, seen = redirects.get(name, name), set()
    while current in successor and current not in seen and current not in cuts:
        seen.add(current)
        current = redirects.get(successor[current][0], successor[current][0])
    return current


_CUTS = None


def lineage_cuts():
    """
    Deux equipes qui se sont affrontees peuvent avoir fusionne plus tard (meme nom actuel) :
    on garde la lignee de la plus presente dans l'historique importe, l'autre reste a part.
    Calcule une fois sur toutes les saisons en cache.
    """
    global _CUTS
    if _CUTS is not None:
        return _CUTS
    redirects, _, successor, _ = lineage()
    appearances, pairs = Counter(), set()
    for year in range(FIRST_YEAR, LAST_YEAR + 1):
        comps = competitions_of(year)
        for m in matches_of(year, [p["OverviewPage"] for c in comps for p in c["pages"]]):
            if m["Team1"] and m["Team2"]:
                appearances.update([m["Team1"], m["Team2"]])
                pairs.add((m["Team1"], m["Team2"]))
    cuts = set()
    for _ in range(5):
        conflicts = [(a, b) for a, b in pairs if a != b
                     and canonical(a, redirects, successor, cuts) == canonical(b, redirects, successor, cuts)]
        if not conflicts:
            break
        for a, b in conflicts:
            minor = min((a, b), key=lambda n: (appearances[n], n))
            cuts.update({minor, redirects.get(minor, minor)})
    _CUTS = frozenset(cuts)
    return _CUTS


def teams_of(names):
    """Pour chaque nom vu dans les matchs : equipe actuelle (code, region) et historique de ses noms."""
    redirects, pages, successor, links = lineage()
    cuts = lineage_cuts()
    by_name, chains = {}, defaultdict(dict)
    for orig, new, date in links:
        if orig == new or orig in cuts:
            continue  # une equipe mise a part ne reprend pas le nom de la fusion
        team = canonical(orig, redirects, successor, cuts)
        names_seen = chains[team]
        # Les liens sans date (fiche "renommee en") ne doivent pas effacer une date connue.
        before = names_seen.setdefault(orig, [None, None])
        before[1] = before[1] or date or None
        after = names_seen.setdefault(new, [None, None])
        after[0] = after[0] or date or None
    for name in names:
        team = canonical(name, redirects, successor, cuts)
        page = pages.get(team, {})
        history = []
        for alias, (start, end) in sorted(chains.get(team, {}).items(), key=lambda x: (x[1][0] or "", x[1][1] or "9")):
            alias_page = pages.get(redirects.get(alias, alias), {})
            history.append({"name": alias, "short": alias_page.get("Short") or None, "from": start, "to": end})
        if not any(h["name"] == team for h in history):
            history.append({"name": team, "short": page.get("Short") or None, "from": None, "to": None})
        by_name[name] = {"name": team, "short": page.get("Short", ""), "region": page.get("Region", ""),
                         "active": page.get("IsDisbanded") == "0", "history": history}
    return by_name


def team_code(team):
    short = (team.get("short") or "").strip()
    if short:
        return short.upper()[:20]
    words = re.findall(r"[A-Za-z0-9]+", team["name"])
    return ("".join(w[0] for w in words).upper() or team["name"][:4].upper())[:20]


# ---------------------------------------------------------------------------
# Analyse de la structure : phases, poules, brackets
# ---------------------------------------------------------------------------

def match_order(m):
    return (m["DateTime_UTC"] or "9999", int(m["N_TabInPage"] or 0), int(m["N_MatchInTab"] or 0))


def winner_loser(m):
    if m["Winner"] == "1":
        return m["Team1"], m["Team2"]
    if m["Winner"] == "2":
        return m["Team2"], m["Team1"]
    return None, None


def best_of(raw):
    return {"1": "BO1", "2": "BO2", "3": "BO3", "5": "BO5"}.get(str(raw or "").strip(), "BO1" if not raw else "BO5")


def majority_bo(rows):
    counts = Counter(best_of(m["BestOf"]) for m in rows)
    return counts.most_common(1)[0][0] if len(counts) == 1 else None


def group_label(tab):
    for pattern, prefix in ((r"^week (\d+)", "W"), (r"^day (\d+)", "J"), (r"^round (\d+)", "R")):
        hit = re.match(pattern, tab or "", re.I)
        if hit:
            return f"{prefix}{hit.group(1)}"
    return (tab or "Journee").strip()[:50]


def components(rows):
    """Poules = composantes connexes du graphe 'qui a joue contre qui'."""
    parent = {}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for m in rows:
        parent[find(m["Team1"])] = find(m["Team2"])
    groups = defaultdict(list)
    for m in sorted(rows, key=match_order):
        groups[find(m["Team1"])].append(m)
    return list(groups.values())


def analyse_groups(rows, context):
    """Phase de poules / saison reguliere / swiss : format et poules deduits des rencontres."""
    comps = components(rows)
    if all(len({m["Team1"] for m in c} | {m["Team2"] for m in c}) == 2 for c in comps):
        return "OTHER", [sorted(rows, key=match_order)]  # duels isoles : pas de poules
    formats = []
    for comp in comps:
        teams = {m["Team1"] for m in comp} | {m["Team2"] for m in comp}
        pairs = Counter(tuple(sorted((m["Team1"], m["Team2"]))) for m in comp)
        complete = len(pairs) == len(teams) * (len(teams) - 1) // 2
        named_swiss = re.search("swiss", context, re.I) or any(re.search("swiss", m["Tab"] or "", re.I) for m in comp)
        # Swiss sans le dire : onglets "Round N", poule incomplete, au moins 8 equipes (Worlds 2023+).
        rounds_only = all(re.fullmatch(r"Round \d+", m["Tab"] or "") for m in comp)
        if named_swiss or (rounds_only and not complete and len(teams) >= 8 and len(teams) % 2 == 0):
            formats.append("SWISS")
        elif not complete:
            formats.append("OTHER")
        else:
            formats.append("ROUND_ROBIN" if max(pairs.values()) == 1 else "DOUBLE_ROUND_ROBIN")
    fmt = formats[0] if len(set(formats)) == 1 else "OTHER"
    return fmt, comps


def analyse_bracket(rows):
    """Bracket : liens vainqueur/perdant deduits des vrais resultats, cotes deduits des liens."""
    rows = sorted(rows, key=match_order)
    links = {}       # index -> (next index, slot) pour le vainqueur
    loser_links = {}  # index -> (next index, slot) pour le perdant
    fed = set()      # (index, slot) alimentes par un match precedent
    for i, m in enumerate(rows):
        winner, loser = winner_loser(m)
        for team, target in ((winner, links), (loser, loser_links)):
            if not team:
                continue
            for j in range(i + 1, len(rows)):
                if team in (rows[j]["Team1"], rows[j]["Team2"]):
                    slot = 1 if rows[j]["Team1"] == team else 2
                    if (j, slot) not in fed:
                        target[i] = (j, slot)
                        fed.add((j, slot))
                    break

    third = {i for i, m in enumerate(rows) if THIRD_PLACE_TAB.search(m["Tab"] or "")}
    double = any(j not in third for (j, _) in loser_links.values())
    lower = set()
    for i in range(len(rows)):
        for src, (j, _) in loser_links.items():
            if j == i and i not in third:
                lower.add(i)
        for src, (j, _) in links.items():
            if j == i and src in lower:
                lower.add(i)
    # Finale : dernier match qui n'envoie son vainqueur nulle part (hors petite finale).
    finals = [i for i in range(len(rows)) if i not in links and i not in third]
    final = finals[-1] if finals else None
    if double and final is not None:
        lower.discard(final)

    sides = []
    for i in range(len(rows)):
        if i in third:
            sides.append("PLACEMENT")
        elif i == final:
            sides.append("GRAND_FINAL")
        elif double:
            sides.append("LOWER" if i in lower else "UPPER")
        else:
            sides.append("BRACKET")
    if not links and not loser_links:
        fmt = "OTHER"
    else:
        fmt = "DOUBLE_ELIMINATION" if double else "SINGLE_ELIMINATION"
    return fmt, rows, links, loser_links, fed, sides


def ko_label(tab):
    for pattern, label in KO_LABELS:
        if pattern.search(tab or ""):
            return label
    return (tab or "Playoffs").strip()[:45]


def build_competition(comp, matches_by_page):
    """Transforme une competition Leaguepedia en phases et matchs prets a ecrire en SQL."""
    pages = comp["pages"]
    first = pages[0]
    stages = []
    for page in pages:
        rows = [m for m in matches_by_page.get(page["OverviewPage"], []) if m["Team1"] and m["Team2"]]
        rows = [m for m in rows if m["Team1"] not in ("TBD", "") and m["Team2"] not in ("TBD", "")]
        if not rows:
            continue
        playoffs_page = page["IsPlayoffs"] == "1"
        sections = defaultdict(lambda: {"groups": [], "ko": [], "tiebreak": []})
        for m in rows:
            prefix = "Play-In" if PLAY_IN_TAB.search(m["Tab"] or "") else ""
            section = sections[prefix]
            if m["IsTiebreaker"] == "1" or TIEBREAK_TAB.search(m["Tab"] or ""):
                section["tiebreak"].append(m)
            elif playoffs_page or KO_TAB.search(m["Tab"] or "") or re.search("playoff", m["Phase"] or "", re.I):
                section["ko"].append(m)
            else:
                section["groups"].append(m)
        for prefix in sorted(sections, key=lambda p: p != "Play-In"):
            section = sections[prefix]
            label = f"{prefix} : " if prefix else ""
            if section["groups"]:
                fmt, comps = analyse_groups(section["groups"], page["OverviewPage"])
                teams = {m[k] for m in section["groups"] for k in ("Team1", "Team2")}
                if fmt == "OTHER" and len(section["groups"]) < 2 * len(teams) and len(comps) == 1:
                    # Trop peu de matchs pour une poule : un gauntlet / bracket a onglets "Round N".
                    section["ko"] = section["groups"] + section["ko"]
                    section["groups"] = []
            if section["groups"]:
                page_part = page["OverviewPage"].rsplit("/", 1)[-1]
                regular = comp["split"] and not prefix and not re.search(r"group|placement", page_part, re.I)
                if fmt == "SWISS":
                    name = "Phase suisse"
                elif re.search("placement", page_part, re.I):
                    name = "Placements"
                else:
                    name = "Saison reguliere" if regular else "Phase de groupes"
                stages.append({"kind": "groups", "name": label + name, "format": fmt, "groups": comps,
                               "tiebreak": section["tiebreak"], "prefix": prefix,
                               "bo": majority_bo(section["groups"])})
            elif section["tiebreak"]:
                section["ko"].extend(section["tiebreak"])
            if section["ko"]:
                fmt, ko_rows, links, loser_links, fed, sides = analyse_bracket(section["ko"])
                stages.append({"kind": "bracket", "name": label + ("Playoffs" if comp["split"] else "Phase finale"),
                               "format": fmt, "rows": ko_rows, "links": links, "loser_links": loser_links,
                               "fed": fed, "sides": sides, "prefix": prefix, "bo": majority_bo(ko_rows)})

    # Seule la derniere phase finale (hors play-in) designe le champion : les finales des
    # phases precedentes restent dans le bracket.
    brackets = [s for s in stages if s["kind"] == "bracket" and not s["prefix"]]
    for s in stages:
        if s["kind"] == "bracket" and (s["prefix"] or s is not brackets[-1]):
            side = "PLAY_IN" if s["prefix"] else "BRACKET"
            s["sides"] = [side if x == "GRAND_FINAL" else (side if s["prefix"] else x) for x in s["sides"]]

    name = re.sub(r"\s+Playoffs$", "", first["Name"]).strip()
    is_event = first["Region"] in ("International", "Wildcard") or first["IsOfficial"] != "1"
    dates = [d for p in pages for d in (p["DateStart"], p["Date"]) if d]
    code = LEAGUE_CODES.get(first["League"], first["League"][:30])
    if "Regional Finals" in first["Name"]:
        code = "RF"
    return {
        "source_key": "leaguepedia:" + first["OverviewPage"],
        "code": code,
        "name": name[:150],
        "type": "INTERNATIONAL_EVENT" if is_event else "REGIONAL_LEAGUE",
        "region": None if first["Region"] in ("International", "") else first["Region"],
        "season": season_of(first),
        "split": comp["split"],
        "start": min(dates) if dates else None,
        "end": max(dates) if dates else None,
        "stages": stages,
        "group_names": set(),
    }


# ---------------------------------------------------------------------------
# Generation SQL
# ---------------------------------------------------------------------------

def sql(value):
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def kickoff(m, fallback):
    raw = m["DateTime_UTC"]
    if not raw:
        return fallback, None
    utc = datetime.strptime(raw[:19], "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
    paris = utc.astimezone(PARIS)
    return paris.date().isoformat(), paris.strftime("%H:%M:00")


def generate(year):
    comps = competitions_of(year)
    pages = [p["OverviewPage"] for c in comps for p in c["pages"]]
    matches = matches_of(year, pages)
    by_page = defaultdict(list)
    for m in matches:
        by_page[m["OverviewPage"]].append(m)
    built = [build_competition(c, by_page) for c in comps]
    built = [c for c in built if c["stages"]]
    names = {m[k] for c in built for s in c["stages"] for m in stage_rows(s) for k in ("Team1", "Team2")}
    return built, teams_of(names)


def stage_rows(stage):
    if stage["kind"] == "bracket":
        return stage["rows"]
    return [m for g in stage["groups"] for m in g] + stage["tiebreak"]


def emit_sql(year):
    built, teams = generate(year)
    out = []
    w = out.append
    w(f"-- Historique {year} importe de Leaguepedia (https://lol.fandom.com, CC BY-SA 3.0) par")
    w("-- import/import_leaguepedia.py : NE PAS EDITER A LA MAIN, regenerer le fichier.")
    w("-- Structure des tournois (phases, poules, brackets relies) SANS les scores : les")
    w("-- resultats se saisissent dans l'appli, les playoffs avancent au fil des scores.")
    w("")
    w("CREATE TEMP TABLE lp_team (lp_name TEXT PRIMARY KEY, team_id BIGINT NOT NULL);")
    w("CREATE TEMP TABLE lp_match (k TEXT PRIMARY KEY, match_id BIGINT NOT NULL);")
    w("")
    w("-- Equipe actuelle (apres renommages) : existante de meme nom, ou de meme code si elle")
    w("-- existe encore aujourd'hui ; sinon creee (code rendu unique si deja pris).")
    w("CREATE FUNCTION pg_temp.lp_team(p_name TEXT, p_code TEXT, p_region TEXT, p_active BOOLEAN) RETURNS BIGINT AS $$")
    w("DECLARE v_id BIGINT; v_code TEXT := p_code; n INT := 1;")
    w("BEGIN")
    w("    SELECT id INTO v_id FROM team WHERE lower(name) = lower(p_name) ORDER BY id LIMIT 1;")
    w("    IF v_id IS NULL AND p_active THEN")
    w("        SELECT id INTO v_id FROM team WHERE code = p_code ORDER BY id LIMIT 1;")
    w("    END IF;")
    w("    IF v_id IS NOT NULL THEN RETURN v_id; END IF;")
    w("    WHILE EXISTS (SELECT 1 FROM team WHERE code = v_code) LOOP")
    w("        n := n + 1;")
    w("        v_code := left(p_code, 18) || n;")
    w("    END LOOP;")
    w("    INSERT INTO team (code, name, region) VALUES (v_code, p_name, p_region) RETURNING id INTO v_id;")
    w("    RETURN v_id;")
    w("END $$ LANGUAGE plpgsql;")
    w("")
    for name in sorted(teams):
        t = teams[name]
        w(f"INSERT INTO lp_team VALUES ({sql(name)}, pg_temp.lp_team({sql(t['name'][:100])}, {sql(team_code(t))}, "
          f"{sql((t['region'] or None) and t['region'][:50])}, {'TRUE' if t['active'] else 'FALSE'}));")
    w("")
    w("-- Historique des noms (ex: SK Telecom T1 K -> SK Telecom T1 -> T1).")
    done = set()
    for name in sorted(teams):
        t = teams[name]
        if t["name"] in done or len(t["history"]) < 2:
            continue  # equipe jamais renommee : pas d'historique a garder
        done.add(t["name"])
        for h in t["history"]:
            until = (date_cls.fromisoformat(h["to"]) - timedelta(days=1)).isoformat() if h["to"] else None
            w(f"INSERT INTO team_name_history (team_id, name, short_name, valid_from, valid_to) "
              f"SELECT team_id, {sql(h['name'][:100])}, {sql(h['short'] and h['short'][:20])}, {sql(h['from'])}, "
              f"{sql(until)} FROM lp_team WHERE lp_name = {sql(name)} ON CONFLICT (team_id, name) DO NOTHING;")
    for c in built:
        comp = f"(SELECT id FROM competition WHERE source_key = {sql(c['source_key'])})"
        w("")
        w(f"-- {c['name']}")
        w("INSERT INTO competition (code, name, type, region, season, split, start_date, end_date, source_key) VALUES "
          f"({sql(c['code'])}, {sql(c['name'])}, {sql(c['type'])}, {sql(c['region'])}, {c['season']}, "
          f"{sql(c['split'])}, {sql(c['start'])}, {sql(c['end'])}, {sql(c['source_key'])});")
        for pos, s in enumerate(c["stages"], start=1):
            stage = f"(SELECT id FROM competition_stage WHERE competition_id = {comp} AND position = {pos})"
            emit_stage(w, c, s, pos, comp, stage, teams)
    w("")
    w("DROP FUNCTION pg_temp.lp_team(TEXT, TEXT, TEXT, BOOLEAN);")
    w("DROP TABLE lp_match;")
    w("DROP TABLE lp_team;")
    return "\n".join(out) + "\n"


def emit_stage(w, c, s, pos, comp, stage, teams):
    rows = stage_rows(s)
    entrants = {m[k] for m in rows for k in ("Team1", "Team2")}
    group_count = len(s["groups"]) if s["kind"] == "groups" else 1
    w(f"INSERT INTO competition_stage (competition_id, position, name, format, best_of, team_count, group_count) "
      f"VALUES ({comp}, {pos}, {sql(s['name'])}, {sql(s['format'])}, {sql(s['bo'])}, {len(entrants)}, {group_count});")
    fallback = c["start"] or f"{c['season']}-01-01"
    team = lambda name: f"(SELECT team_id FROM lp_team WHERE lp_name = {sql(name)})"

    if s["kind"] == "groups":
        swiss = s["format"] == "SWISS"
        for g, group in enumerate(s["groups"]):
            group_id = "NULL"
            if len(s["groups"]) > 1:
                gname = f"{s['prefix'] + ' ' if s['prefix'] else ''}Groupe {chr(65 + g)}"
                # Deux phases de poules dans la meme competition : noms de groupe distincts.
                if gname in c["group_names"]:
                    gname = f"{s['name']} - {gname}"
                c["group_names"].add(gname)
                w(f"INSERT INTO competition_group (competition_id, name) VALUES ({comp}, {sql(gname)});")
                group_id = (f"(SELECT id FROM competition_group WHERE competition_id = {comp} "
                            f"AND name = {sql(gname)})")
            for m in group:
                emit_match(w, comp, stage, group_id, group_label(m["Tab"]), m, fallback,
                           "PLAYOFFS" if swiss else "REGULAR_SEASON", "SWISS_STAGE" if swiss else None,
                           team(m["Team1"]), team(m["Team2"]))
        for m in s["tiebreak"]:
            emit_match(w, comp, stage, "NULL", "Departage", m, fallback, "PLAYOFFS", "TIEBREAKER",
                       team(m["Team1"]), team(m["Team2"]))
        return

    labels = [ko_label(m["Tab"]) for m in s["rows"]]
    totals = Counter(labels)
    seen = Counter()
    keys = []
    for i, m in enumerate(s["rows"]):
        seen[labels[i]] += 1
        label = labels[i] + (f" {seen[labels[i]]}" if totals[labels[i]] > 1 else "")
        key = f"{c['source_key']}#{pos}#{i}"
        keys.append(key)
        t1 = "NULL" if (i, 1) in s["fed"] else team(m["Team1"])
        t2 = "NULL" if (i, 2) in s["fed"] else team(m["Team2"])
        emit_match(w, comp, stage, "NULL", label, m, fallback, "PLAYOFFS", s["sides"][i], t1, t2, key)
    for links, column in ((s["links"], "next_match"), (s["loser_links"], "loser_next_match")):
        for i, (j, slot) in sorted(links.items()):
            w(f"UPDATE match SET {column}_id = (SELECT match_id FROM lp_match WHERE k = {sql(keys[j])}), "
              f"{column}_slot = {slot} WHERE id = (SELECT match_id FROM lp_match WHERE k = {sql(keys[i])});")


def emit_match(w, comp, stage, group_id, label, m, fallback, phase, side, t1, t2, key=None):
    date, clock = kickoff(m, fallback)
    insert = (f"INSERT INTO match (competition_id, competition_group_id, stage_id, round_label, date, time, best_of, "
              f"team1_id, team2_id, status, phase, bracket_side) VALUES ({comp}, {group_id}, {stage}, "
              f"{sql(label[:50])}, {sql(date)}, {sql(clock)}, {sql(best_of(m['BestOf']))}, {t1}, {t2}, "
              f"'SCHEDULED', {sql(phase)}, {sql(side)})")
    if key:
        w(f"WITH ins AS ({insert} RETURNING id) INSERT INTO lp_match SELECT {sql(key)}, id FROM ins;")
    else:
        w(insert + ";")


# ---------------------------------------------------------------------------
# Ligne de commande
# ---------------------------------------------------------------------------

def preview(year):
    built, teams = generate(year)
    print(f"Saison {year} : {len(built)} competitions, {len(teams)} equipes")
    for c in built:
        print(f"\n{c['name']}  [{c['code']} | {c['type']} | {c['region']} | split={c['split']}] {c['start']} -> {c['end']}")
        for s in c["stages"]:
            rows = stage_rows(s)
            extra = f", {len(s['groups'])} groupe(s)" if s["kind"] == "groups" else ""
            print(f"   - {s['name']} : {s['format']} {s['bo'] or 'mixte'}, {len(rows)} matchs{extra}")
            if s["kind"] == "bracket":
                for i, m in enumerate(s["rows"]):
                    t1 = "  ?  " if (i, 1) in s["fed"] else m["Team1"]
                    t2 = "  ?  " if (i, 2) in s["fed"] else m["Team2"]
                    nxt = s["links"].get(i)
                    print(f"       {s['sides'][i]:11} {ko_label(m['Tab']):18} {t1} vs {t2}"
                          f"{'  -> #' + str(nxt[0]) if nxt else ''}")


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("fetch", "sql", "preview"):
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    command, year = sys.argv[1], int(sys.argv[2])
    if command == "fetch":
        built, teams = generate(year)
        print(f"{year} : {len(built)} competitions et {len(teams)} equipes en cache", file=sys.stderr)
    elif command == "preview":
        preview(year)
    else:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stdout.write(emit_sql(year))


if __name__ == "__main__":
    main()
