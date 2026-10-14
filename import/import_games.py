#!/usr/bin/env python3
"""
Importe le detail des matchs depuis Leaguepedia (https://lol.fandom.com, CC BY-SA 3.0) et
genere une migration Flyway par saison :
  * scores des series encore sans score (et equipes des tours de playoffs pas encore remplis) ;
  * manches : vainqueur, MVP ("Player of the Game"), champion et K/D/A de chaque joueur, bans ;
  * joueurs : relies a leur page Leaguepedia (player.source_key), pseudo actuel, nationalite
    (code ISO), poste ; crees s'ils n'existent pas ;
  * passages en equipe (player_stint) d'apres les contrats Leaguepedia (table Tenures).

Rien de ce qui est deja saisi n'est ecrase : une serie qui a un score ou des manches garde les
siens (seuls les champs vides sont completes), un passage existant n'est pas modifie.

Les competitions sont celles deja en base (import_leaguepedia.py). La base locale sert de
reference pour relier matchs, equipes et joueurs : appliquer la migration d'une saison
(Flyway) avant de generer la suivante, les joueurs crees servant aux saisons d'apres.

Usage :
    python import_games.py fetch 2011     # telecharge dans import/cache/ (lent, rejouable)
    python import_games.py report 2011    # ce qui sera importe, et les ecarts avec la base
    python import_games.py sql 2011 > ../backend/src/main/resources/db/migration/V39__leaguepedia_games_2011.sql

Base locale : variables PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE (defauts du backend)
et PSQL (chemin de psql).
"""
import ast
import hashlib
import html
import json
import os
import re
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import date as date_cls, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import import_leaguepedia as lp  # noqa: E402

HERE = Path(__file__).parent
CHAMPIONS_JS = HERE.parent / "frontend" / "src" / "champions.js"
COUNTRIES_JSON = HERE.parent / "frontend" / "node_modules" / "flag-icons" / "country.json"

ROLES = {"top": "TOP", "jungle": "JGL", "mid": "MID", "bot": "ADC", "support": "SUPP",
         "ad carry": "ADC", "adc": "ADC"}

# Noms de pays Leaguepedia absents (ou ecrits autrement) de flag-icons.
COUNTRY_ALIASES = {
    "United States": "US", "USA": "US", "South Korea": "KR", "Korea": "KR", "Republic of Korea": "KR",
    "United Kingdom": "GB", "England": "GB", "Scotland": "GB", "Wales": "GB", "Northern Ireland": "GB",
    "Czechia": "CZ", "Czech Republic": "CZ", "Russia": "RU", "Taiwan": "TW", "Vietnam": "VN",
    "Hong Kong": "HK", "Macau": "MO", "Macao": "MO", "Turkey": "TR", "Türkiye": "TR", "Bosnia": "BA",
    "Bosnia and Herzegovina": "BA", "North Macedonia": "MK", "Macedonia": "MK", "Moldova": "MD",
    "Ivory Coast": "CI", "Côte d'Ivoire": "CI", "Kosovo": "XK", "Laos": "LA", "Iran": "IR",
    "Syria": "SY", "Palestine": "PS", "Bolivia": "BO", "Venezuela": "VE", "Tanzania": "TZ",
    "Brunei": "BN", "Cape Verde": "CV", "Netherlands": "NL", "The Netherlands": "NL",
    "Dominican Republic": "DO", "Puerto Rico": "PR", "Slovakia": "SK", "Philippines": "PH",
    "United Arab Emirates": "AE", "UAE": "AE", "Mainland China": "CN", "China": "CN",
}


# ---------------------------------------------------------------------------
# Base locale (psql)
# ---------------------------------------------------------------------------

def query(sql_text):
    """Execute une requete dans la base locale et renvoie ses lignes (dictionnaires)."""
    env = dict(os.environ)
    env.setdefault("PGHOST", "localhost")
    env.setdefault("PGPORT", "5432")
    env.setdefault("PGUSER", "lol_user")
    env.setdefault("PGPASSWORD", "lol_password")
    env.setdefault("PGDATABASE", "lol_results")
    env["PGCLIENTENCODING"] = "UTF8"
    psql = os.environ.get("PSQL", r"C:\Program Files\PostgreSQL\17\bin\psql.exe")
    wrapped = f"SELECT coalesce(json_agg(t), '[]') FROM ({sql_text}) t"
    out = subprocess.run([psql, "-X", "-At", "-v", "ON_ERROR_STOP=1", "-c", wrapped],
                         env=env, capture_output=True, check=True)
    return json.loads(out.stdout.decode("utf-8"))


class Db:
    """Etat de la base locale utile a une saison."""

    def __init__(self, year):
        self.competitions = {c["source_key"]: c for c in query(
            f"SELECT id, source_key, start_date FROM competition WHERE season = {year} AND source_key IS NOT NULL")}
        ids = ",".join(str(c["id"]) for c in self.competitions.values()) or "0"
        self.matches = query(f"""
            SELECT m.id, c.source_key, m.date::text AS date, m.time::text AS time, m.round_label,
                   m.team1_id, m.team2_id, t1.name AS team1, t2.name AS team2, m.score1, m.score2,
                   m.status, m.mvp_player_id, m.best_of
            FROM match m JOIN competition c ON c.id = m.competition_id
            LEFT JOIN team t1 ON t1.id = m.team1_id LEFT JOIN team t2 ON t2.id = m.team2_id
            WHERE m.competition_id IN ({ids})""")
        match_ids = ",".join(str(m["id"]) for m in self.matches) or "0"
        self.games = query(f"""
            SELECT g.id, g.match_id, g.game_number, g.winner_team_id, g.mvp_player_id,
                   (SELECT count(*) FROM match_game_ban b WHERE b.game_id = g.id) AS bans
            FROM match_game g WHERE g.match_id IN ({match_ids})""")
        game_ids = ",".join(str(g["id"]) for g in self.games) or "0"
        self.game_players = query(f"""
            SELECT gp.game_id, gp.player_id, p.pseudo, gp.team_id, gp.position, gp.champion,
                   gp.kills, gp.deaths, gp.assists
            FROM match_game_player gp JOIN player p ON p.id = gp.player_id WHERE gp.game_id IN ({game_ids})""")
        self.game_bans = query(f"SELECT game_id, team_id, champion FROM match_game_ban WHERE game_id IN ({game_ids})")
        self.teams = query("SELECT id, code, name FROM team")
        has_key = query("SELECT 1 FROM information_schema.columns WHERE table_name = 'player' "
                        "AND column_name = 'source_key'")
        key = "source_key" if has_key else "NULL::text AS source_key"
        self.players = query(f"SELECT id, pseudo, nationality, position, team_id, {key} FROM player")
        self.stints = query("SELECT s.id, s.player_id, s.team_id, t.name AS team, s.position, "
                            "s.start_date::text AS start_date, s.end_date::text AS end_date "
                            "FROM player_stint s JOIN team t ON t.id = s.team_id")


# ---------------------------------------------------------------------------
# Donnees Leaguepedia d'une saison (en cache)
# ---------------------------------------------------------------------------

SCHEDULE_FIELDS = "OverviewPage,MatchId,Team1,Team2,Team1Score,Team2Score,Winner,MVP,FF,BestOf,DateTime_UTC,Tab"
SCHEDULE_GAME_FIELDS = "OverviewPage,MatchId,GameId,N_GameInMatch,Blue,Red,Winner,MVP,FF"
GAME_FIELDS = "OverviewPage,MatchId,GameId,N_GameInMatch,Team1,Team2,WinTeam,Team1Bans,Team2Bans,DateTime_UTC"
PLAYER_GAME_FIELDS = "OverviewPage,GameId,Link,Champion,Kills,Deaths,Assists,Team,Role,IngameRole,Side"


# Pages Leaguepedia d'une competition en base qu'import_leaguepedia ne selectionne pas
# (qualifications), par source_key.
EXTRA_PAGES = {
    "leaguepedia:LPL/2026 Season/Split 3": ["LPL/2026 Season/Regional Finals"],
    "leaguepedia:2026 Season World Championship/Main Event": ["2026 Season World Championship/Play-In"],
}


def by_pages(name, year, pages, table, fields):
    def fetch():
        rows = []
        for group in lp.chunks(sorted(pages), 12):
            rows += lp.cargo(table, fields, f"OverviewPage IN ({lp.quoted(group)})", limit=5000)
        return rows
    digest = hashlib.sha1("|".join(sorted(pages)).encode("utf-8")).hexdigest()[:8]
    return lp.cached(f"{name}_{year}_{digest}", fetch)


def by_values(name, year, values, table, fields, column):
    """Lignes d'une table dont `column` vaut une des valeurs (par lots), en cache par saison."""
    def fetch():
        rows = []
        for group in lp.chunks(sorted(values), 100):
            rows += lp.cargo(table, fields, f"{column} IN ({lp.quoted(group)})", limit=5000)
        return rows
    digest = hashlib.sha1("|".join(sorted(values)).encode("utf-8")).hexdigest()[:8]
    return lp.cached(f"{name}_{year}_{digest}", fetch)


class Season:
    """Donnees Leaguepedia des competitions d'une saison deja en base."""

    def __init__(self, year, db):
        self.year = year
        self.page_key = {}  # OverviewPage -> source_key de la competition
        self.comp_pages = {}
        for comp in lp.competitions_of(year):
            key = "leaguepedia:" + comp["pages"][0]["OverviewPage"]
            if key in db.competitions:
                self.comp_pages[key] = comp["pages"]
                for p in comp["pages"]:
                    self.page_key[p["OverviewPage"]] = key
        # Competitions hors selection d'import_leaguepedia (tournois secondaires, saisies a la
        # main puis reliees a Leaguepedia) : la page de leur source_key, plus EXTRA_PAGES.
        for key in db.competitions:
            if key not in self.comp_pages and key.startswith("leaguepedia:"):
                self.comp_pages[key] = [{"OverviewPage": key.split(":", 1)[1]}]
                self.page_key[key.split(":", 1)[1]] = key
            for page in EXTRA_PAGES.get(key, ()):
                if key in self.comp_pages and page not in self.page_key:
                    self.comp_pages[key].append({"OverviewPage": page})
                    self.page_key[page] = key
        pages = sorted(self.page_key)
        self.schedule = by_pages("schedule", year, pages, "MatchSchedule", SCHEDULE_FIELDS)
        found = {self.page_key[r["OverviewPage"]] for r in self.schedule if r["OverviewPage"] in self.page_key}
        self.missing_competitions = sorted(set(db.competitions) - found)
        self.schedule_games = by_pages("schedule_games", year, pages, "MatchScheduleGame", SCHEDULE_GAME_FIELDS)
        self.games = by_pages("scoreboard_games", year, pages, "ScoreboardGames", GAME_FIELDS)
        self.player_games = by_pages("scoreboard_players", year, pages, "ScoreboardPlayers", PLAYER_GAME_FIELDS)
        links = {r["Link"] for r in self.player_games if r["Link"]}
        links |= {r["MVP"] for r in self.schedule + self.schedule_games if r["MVP"]}
        self.links = sorted(links)
        redirects = by_values("player_redirects", year, self.links, "PlayerRedirects",
                              "AllName,OverviewPage,ID", "AllName")
        exact = {r["AllName"]: r["OverviewPage"] for r in redirects}
        folded = defaultdict(set)
        for r in redirects:
            folded[r["AllName"].lower()].add(r["OverviewPage"])
        self.page_of = {}
        for link in self.links:
            if link in exact:
                self.page_of[link] = exact[link]
            elif len(folded.get(link.lower(), ())) == 1:
                self.page_of[link] = next(iter(folded[link.lower()]))
            else:
                self.page_of[link] = link  # pas de redirection connue : la page porte ce nom
        player_pages = sorted(set(self.page_of.values()))
        self.player_info = {r["OverviewPage"]: r for r in by_values(
            "players", year, player_pages, "Players",
            "OverviewPage,ID,Name,NationalityPrimary,Country,Role", "OverviewPage")}
        # Tous les noms d'une page (anciens pseudos) : pour retrouver un joueur saisi a la main.
        self.names_of = defaultdict(set)
        for r in by_values("player_names", year, player_pages, "PlayerRedirects", "AllName,OverviewPage",
                           "OverviewPage"):
            self.names_of[r["OverviewPage"]].add(r["AllName"])
        tenure_names = sorted({n for names in self.names_of.values() for n in names} | set(player_pages))
        self.tenures = defaultdict(list)
        for r in by_values("tenures", year, tenure_names, "Tenures",
                           "Player,Team,DateJoin,DateLeave,IsCurrent", "Player"):
            page = self.page_by_name(r["Player"])
            if page:
                self.tenures[page].append(r)
        # Role de chaque arrivee (joueur, remplacant, coach, dirigeant...) : seuls les contrats
        # de joueur deviennent des passages.
        self.join_roles = {}
        for r in by_values("roster_changes", year, tenure_names, "RosterChanges",
                           "Player,Team,Date_Sort,Direction,Roles", "Player"):
            page = self.page_by_name(r["Player"])
            if page and r["Direction"] == "Join" and r["Date_Sort"]:
                roles = [x.strip() for x in parse_bans(html.unescape(r["Roles"])) if x.strip()]
                self.join_roles[(page, r["Team"], r["Date_Sort"][:10])] = roles

    def page_by_name(self, name):
        if name in self.names_of:
            return name
        for page, names in self.names_of.items():
            if name in names:
                return page
        lowered = [p for p, names in self.names_of.items() if name.lower() in {n.lower() for n in names}]
        return lowered[0] if len(lowered) == 1 else None


def fetch(year):
    db = Db(year)
    s = Season(year, db)
    print(f"{year} : {len(s.comp_pages)} competitions, {len(s.schedule)} series, {len(s.schedule_games)} manches "
          f"prevues, {len(s.games)} feuilles de match, {len(s.player_games)} lignes joueurs, "
          f"{len(s.player_info)} joueurs", file=sys.stderr)
    if s.missing_competitions:
        print(f"  competitions en base introuvables sur Leaguepedia : {s.missing_competitions}", file=sys.stderr)




# ---------------------------------------------------------------------------
# Referentiels : champions et pays
# ---------------------------------------------------------------------------

def champion_names():
    text = CHAMPIONS_JS.read_text(encoding="utf-8")
    block = text.split("export const CHAMPIONS = [", 1)[1].split("]", 1)[0]
    return set(ast.literal_eval("[" + block + "]"))


def country_codes():
    codes = {c["name"]: c["code"].upper() for c in json.loads(COUNTRIES_JSON.read_text(encoding="utf-8"))
             if c.get("iso") or c["code"] == "xk"}
    codes.update(COUNTRY_ALIASES)
    return codes


# ---------------------------------------------------------------------------
# Equipes : nom Leaguepedia -> equipe en base
# ---------------------------------------------------------------------------

# Equipes saisies a la main sous un autre nom que leur page Leaguepedia : nom Leaguepedia -> nom en base.
TEAM_ALIASES = {
    "ZSK (Chinese Team)": "ZSK",
    "LODIS (Polish Team)": "LODIS",
    "Unicorns of Love Sexy Edition": "UOL Sexy Edition",
    "Senshi eSports (Benelux Team)": "Senshi eSports",
    "Team Phantasma": "Phantasma",
    "Team Heretics Academy": "Los Heretics",
    "eSuba": "ESB",
}


class Teams:
    def __init__(self, db_teams, names):
        self.info = lp.teams_of(names)
        self.by_name, self.by_code = {}, {}
        for t in sorted(db_teams, key=lambda t: t["id"]):
            self.by_name.setdefault(t["name"].lower(), t["id"])
            self.by_code.setdefault(t["code"], t["id"])
        self.names = {t["id"]: t["name"] for t in db_teams}

    def canonical(self, name):
        t = self.info.get(name)
        return t["name"] if t else name

    def id(self, name):
        """Equipe en base, avec la meme regle que l'import des competitions (nom, sinon code)."""
        if name in TEAM_ALIASES:
            return self.by_name.get(TEAM_ALIASES[name].lower())
        t = self.info.get(name)
        if not t:
            return self.by_name.get(name.lower())
        found = self.by_name.get(t["name"].lower())
        if found is None and t["active"]:
            found = self.by_code.get(lp.team_code(t))
        return found


# ---------------------------------------------------------------------------
# Rapprochement Leaguepedia -> base, saison par saison
# ---------------------------------------------------------------------------

def to_int(value):
    value = (value or "").strip()
    return int(value) if re.fullmatch(r"\d+", value) else None


def role_of(row):
    for raw in (row.get("IngameRole"), row.get("Role")):
        pos = ROLES.get((raw or "").strip().lower())
        if pos:
            return pos
    return None


def parse_bans(raw):
    raw = (raw or "").strip()
    if not raw:
        return []
    try:
        values = ast.literal_eval(raw) if raw.startswith("[") else raw.split(",")
    except (ValueError, SyntaxError):
        values = raw.strip("[]").split(",")
    return [str(v).strip().strip("'\"") for v in values if v and str(v).strip()]


class SeasonPlan:
    """Ce que Leaguepedia apporte a une saison, rapproche des matchs en base."""

    def __init__(self, year, champions):
        self.year = year
        self.db = Db(year)
        self.lp = Season(year, self.db)
        s = self.lp
        # L'export Cargo echappe le HTML (ex: "Nunu &amp; Willump").
        for row in s.player_games:
            row["Champion"] = html.unescape(row["Champion"])
        for row in s.games:
            row["Team1Bans"], row["Team2Bans"] = html.unescape(row["Team1Bans"]), html.unescape(row["Team2Bans"])
        names = {r[k] for r in s.schedule for k in ("Team1", "Team2") if r[k]}
        names |= {r[k] for r in s.schedule_games for k in ("Blue", "Red") if r[k]}
        names |= {r[k] for r in s.games for k in ("Team1", "Team2", "WinTeam") if r[k]}
        names |= {r["Team"] for r in s.player_games if r["Team"]}
        names |= {t["Team"] for ts in s.tenures.values() for t in ts if t["Team"]}
        self.teams = Teams(self.db.teams, names)
        self.champions = champions
        self.issues = []          # anomalies a signaler (texte)
        self.series = []          # rapprochements serie Leaguepedia -> match en base
        self.unmatched_db = []
        self._match()
        self._games()

    def issue(self, text):
        self.issues.append(text)

    # --- series -------------------------------------------------------------

    def _match(self):
        s, teams = self.lp, self.teams
        by_slot = defaultdict(list)
        by_day = defaultdict(list)
        for m in self.db.matches:
            by_slot[(m["source_key"], m["date"], m["time"])].append(m)
            by_day[(m["source_key"], m["date"])].append(m)
        used = {}
        for r in sorted(s.schedule, key=lambda r: (r["DateTime_UTC"] or "", r["MatchId"])):
            key = s.page_key.get(r["OverviewPage"])
            if not key or not r["Team1"] or not r["Team2"] or "TBD" in (r["Team1"], r["Team2"]):
                continue
            fallback = self.db.competitions[key]["start_date"] or f"{self.year}-01-01"
            day, clock = lp.kickoff(r, fallback)
            t1, t2 = teams.id(r["Team1"]), teams.id(r["Team2"])

            def fits(m, strict=False):
                ids = (m["team1_id"], m["team2_id"])
                if strict:
                    return set(ids) == {t1, t2}
                return all(i is None or i in (t1, t2) for i in ids)

            cands = [m for m in by_slot.get((key, day, clock), []) if fits(m) and m["id"] not in used]
            if len(cands) > 1:
                strict = [m for m in cands if fits(m, True)]
                cands = strict if strict else cands
            if len(cands) != 1:
                cands = [m for m in by_day.get((key, day), []) if fits(m, True) and m["id"] not in used]
            if len(cands) != 1:
                cands = [m for m in self.db.matches if m["source_key"] == key and fits(m, True)
                         and m["id"] not in used]
                # Meme affiche plusieurs fois (double round robin) : la plus proche en date.
                if len(cands) > 1:
                    cands.sort(key=lambda m: abs((date_cls.fromisoformat(m["date"])
                                                  - date_cls.fromisoformat(day)).days))
                    if abs((date_cls.fromisoformat(cands[0]["date"]) - date_cls.fromisoformat(day)).days) > 3:
                        cands = []
                    else:
                        cands = cands[:1]
            if len(cands) != 1:
                if t1 is None or t2 is None:
                    self.issue(f"equipe inconnue en base : {r['Team1']} / {r['Team2']} ({r['MatchId']})")
                else:
                    self.issue(f"serie introuvable en base : {r['Team1']} - {r['Team2']} le {day} {clock} "
                               f"({r['MatchId']})")
                continue
            m = cands[0]
            used[m["id"]] = r["MatchId"]
            swapped = m["team1_id"] == t2 or m["team2_id"] == t1
            if t1 == t2:
                swapped = False
            ids = {"1": m["team1_id"] or (t2 if swapped else t1), "2": m["team2_id"] or (t1 if swapped else t2)}
            lp_ids = {"1": ids["2"] if swapped else ids["1"], "2": ids["1"] if swapped else ids["2"]}
            sc1, sc2 = to_int(r["Team1Score"]), to_int(r["Team2Score"])
            if swapped:
                sc1, sc2 = sc2, sc1
            self.series.append({
                "db": m, "lp": r, "swapped": swapped, "team1_id": ids["1"], "team2_id": ids["2"],
                "names": {teams.canonical(r["Team1"]): lp_ids["1"], teams.canonical(r["Team2"]): lp_ids["2"]},
                "raw": {r["Team1"]: lp_ids["1"], r["Team2"]: lp_ids["2"]},
                "score1": sc1, "score2": sc2, "mvp": r["MVP"] or None, "games": [],
            })
        self.unmatched_db = [m for m in self.db.matches if m["id"] not in used]

    def board_fits(self, serie, board):
        """Au moins une des equipes de la feuille de match joue la serie."""
        ids = {self.teams.id(board["Team1"]), self.teams.id(board["Team2"])}
        if ids & {serie["team1_id"], serie["team2_id"]}:
            return True
        return any(self.team_in(serie, board[k]) for k in ("Team1", "Team2"))

    def game_teams(self, serie, board, rows):
        """
        Equipes d'une manche : par leur nom, sinon par elimination (une manche oppose les deux
        equipes de la serie, ex: "Counter Logic Gaming" sur la feuille, "CLG Prime" au calendrier).
        """
        names = {board["Team1"], board["Team2"]} | {r["Team"] for r in rows}
        names.discard("")
        team_of = {n: self.team_in(serie, n) for n in names}
        both = {serie["team1_id"], serie["team2_id"]}
        unknown = [n for n, tid in team_of.items() if tid is None]
        found = {tid for tid in team_of.values() if tid}
        if len(unknown) == 1 and len(found) == 1 and len(both - found) == 1:
            team_of[unknown[0]] = next(iter(both - found))
        return team_of

    def sched_winner(self, serie, sched):
        if sched["Winner"] not in ("1", "2"):
            return None
        return self.team_in(serie, sched["Blue"] if sched["Winner"] == "1" else sched["Red"])

    def team_in(self, serie, name):
        if not name:
            return None
        if name in serie["raw"]:
            return serie["raw"][name]
        return serie["names"].get(self.teams.canonical(name))

    # --- manches ------------------------------------------------------------

    def _games(self):
        s = self.lp
        by_match = {x["lp"]["MatchId"]: x for x in self.series}
        planned = defaultdict(list)  # MatchId -> lignes MatchScheduleGame
        for g in s.schedule_games:
            if g["MatchId"] in by_match and to_int(g["N_GameInMatch"]):
                planned[g["MatchId"]].append(g)
        boards = {g["GameId"]: g for g in s.games}
        boards_of = defaultdict(list)  # MatchId (du calendrier) -> feuilles de match
        orphans = defaultdict(list)
        for g in s.games:
            # Feuille rattachee a une serie dont aucune equipe ne joue (identifiants decales) : orpheline.
            mine = g["MatchId"] in by_match and self.board_fits(by_match[g["MatchId"]], g)
            (boards_of if mine else orphans)[g["MatchId"]].append(g)
        # Feuilles dont l'identifiant de serie differe du calendrier (vieilles pages) :
        # rattachees a la serie de la meme page, memes equipes, a un jour pres.
        for gid, rows in sorted(orphans.items(), key=lambda kv: min(r["DateTime_UTC"] for r in kv[1])):
            first = rows[0]
            ids = {self.teams.id(first["Team1"]), self.teams.id(first["Team2"])}
            day = lp.kickoff(first, "2000-01-01")[0] if first["DateTime_UTC"] else None
            cands = [x for x in self.series if x["lp"]["OverviewPage"] == first["OverviewPage"]
                     and {x["team1_id"], x["team2_id"]} == ids and not boards_of[x["lp"]["MatchId"]]
                     and (day is None or abs((date_cls.fromisoformat(x["db"]["date"])
                                              - date_cls.fromisoformat(day)).days) <= 1)]
            if len(cands) > 1 and day:
                gap = lambda x: abs((date_cls.fromisoformat(x["db"]["date"]) - date_cls.fromisoformat(day)).days)
                best = min(gap(x) for x in cands)
                cands = [x for x in cands if gap(x) == best]
            if len(cands) > 1:
                played = lambda x: sum(1 for g in planned[x["lp"]["MatchId"]] if not g["FF"])
                cands = [x for x in cands if played(x) == len(rows)] or cands
            if len(cands) > 1:
                # Meme jour, meme nombre de manches : dans l'ordre chronologique.
                cands.sort(key=lambda x: (x["db"]["date"], x["db"]["time"] or ""))
                cands = cands[:1]
            if len(cands) == 1:
                boards_of[cands[0]["lp"]["MatchId"]] = rows
            else:
                self.issue(f"feuilles de match sans serie : {gid} ({len(cands)} candidates)")
        players = defaultdict(list)
        for p in s.player_games:
            players[p["GameId"]].append(p)

        for match_id, serie in by_match.items():
            scheds = sorted(planned[match_id], key=lambda g: to_int(g["N_GameInMatch"]))
            linked = {g["GameId"] for g in scheds if g["GameId"] in boards
                      and self.board_fits(serie, boards[g["GameId"]])}
            free = sorted((b for b in boards_of[match_id] if b["GameId"] not in linked),
                          key=lambda b: (to_int(b["N_GameInMatch"]) or 0, b["DateTime_UTC"]))
            pairs = []
            for sched in scheds:
                board = boards.get(sched["GameId"]) if sched["GameId"] in linked else None
                if board is None and not sched["FF"] and free:
                    candidate = free[0]
                    sched_winner = self.sched_winner(serie, sched)
                    board_winner = self.team_in(serie, candidate["WinTeam"])
                    if sched_winner is None or board_winner is None or sched_winner == board_winner:
                        board = free.pop(0)
                pairs.append((sched, board))
            pairs += [(None, b) for b in free]
            games = []
            for n, (sched, board) in enumerate(pairs, start=1):
                winner = self.team_in(serie, board["WinTeam"]) if board and board["WinTeam"] else None
                if winner is None and sched:
                    winner = self.sched_winner(serie, sched)
                if not board and (winner is None or (sched and sched["FF"])):
                    # Manche pas jouee, ou gagnee par forfait (ex: avantage d'une manche en
                    # playoffs des Worlds S1) : comptee dans le score de la serie, pas comme manche.
                    continue
                game = {"n": n, "winner": winner, "mvp": (sched or {}).get("MVP") or None,
                        "ff": bool(sched and sched["FF"]), "players": [], "bans": [],
                        "id": (board or sched)["GameId"]}
                if board:
                    team_of = self.game_teams(serie, board, players.get(board["GameId"], []))
                    for side, col in ((board["Team1"], "Team1Bans"), (board["Team2"], "Team2Bans")):
                        tid = team_of.get(side)
                        for champ in parse_bans(board[col]):
                            if champ in self.champions and tid and (tid, champ) not in game["bans"]:
                                game["bans"].append((tid, champ))
                            elif champ not in self.champions and champ.lower() not in ("none", "loss of ban", "-"):
                                self.issue(f"ban inconnu '{champ}' ({board['GameId']})")
                    seen_champ, seen_player = set(), set()
                    for p in players.get(board["GameId"], []):
                        page = s.page_of.get(p["Link"])
                        tid = team_of.get(p["Team"])
                        pos = role_of(p)
                        champ = p["Champion"]
                        if not page or not tid or not pos or champ not in self.champions:
                            self.issue(f"ligne joueur ignoree : {p['Link']} / {p['Team']} / {p['Role']} / "
                                       f"{champ} ({board['GameId']})")
                            continue
                        if (tid, champ) in seen_champ or page in seen_player:
                            if (tid, champ) not in seen_champ or page not in seen_player:
                                self.issue(f"doublon ignore : {p['Link']} {champ} ({board['GameId']})")
                            continue  # ligne en double dans Leaguepedia
                        seen_champ.add((tid, champ))
                        seen_player.add(page)
                        game["players"].append({"page": page, "team": tid, "pos": pos, "champ": champ,
                                                "k": to_int(p["Kills"]), "d": to_int(p["Deaths"]),
                                                "a": to_int(p["Assists"])})
                games.append(game)
            # Numeros de manche : 1..n dans l'ordre (Leaguepedia en saute parfois).
            for i, g in enumerate(sorted(games, key=lambda g: g["n"]), start=1):
                g["n"] = i
            serie["games"] = [g for g in games if g["n"] <= 5]
            if len(games) > 5:
                self.issue(f"plus de 5 manches : {match_id}")


# ---------------------------------------------------------------------------
# Joueurs et passages, sur toutes les saisons
# ---------------------------------------------------------------------------

# Un poste tenu moins de journees que ca dans une equipe (depannage) ne coupe pas le passage.
MIN_DAYS_FOR_ROLE_SWAP = 3

# Joueurs saisis a la main que les noms ne permettent pas de relier : page Leaguepedia ->
# (pseudo en base, equipe d'un de ses passages).
MANUAL_LINKS = {
    "Kevin (Lee Sang-jun)": ("Kev1n", "Little Hippo"),
}


def base_name(page):
    return re.sub(r"\s*\(.*\)$", "", page).strip()


def day(value):
    return date_cls.fromisoformat(value[:10]) if value else None


class Import:
    def __init__(self, years):
        champions = champion_names()
        self.years = list(years)
        self.plans = {y: SeasonPlan(y, champions) for y in self.years}
        self.countries = country_codes()
        self.issues = defaultdict(list)  # saison -> anomalies
        for y, plan in self.plans.items():
            self.issues[y].extend(plan.issues)
        last = self.plans[self.years[-1]]
        names = set()
        for plan in self.plans.values():
            names |= set(plan.teams.info)
        self.teams = Teams(last.db.teams, names)
        self.db_players = last.db.players
        self.db_stints = last.db.stints
        self.game_rows = Counter(r["player_id"] for r in query("SELECT player_id FROM match_game_player"))
        self._appearances()
        self._link_players()
        self._player_fields()
        self._stints()

    # --- apparitions ----------------------------------------------------------

    def _appearances(self):
        self.apps = defaultdict(list)    # page -> [(date, team_id, position, year)]
        self.first_year = {}
        self.info, self.names, self.tenures = {}, defaultdict(set), defaultdict(list)
        self.join_roles = {}
        self.slots = defaultdict(Counter)  # joueur en base -> pages Leaguepedia a ses places
        for y, plan in self.plans.items():
            s = plan.lp
            self.info.update(s.player_info)
            for page, names in s.names_of.items():
                self.names[page] |= names
            for page, rows in s.tenures.items():
                for r in rows:
                    if r not in self.tenures[page]:
                        self.tenures[page].append(r)
            self.join_roles.update(s.join_roles)
            saisies = defaultdict(list)
            for g in plan.db.games:
                saisies[g["match_id"]].append(g)
            players_of = defaultdict(list)
            for p in plan.db.game_players:
                players_of[p["game_id"]].append(p)
            for serie in plan.series:
                d = serie["db"]["date"]
                mvps = [serie["mvp"]] + [g["mvp"] for g in serie["games"]]
                for link in filter(None, mvps):
                    page = s.page_of.get(link, link)
                    self.first_year[page] = min(self.first_year.get(page, y), y)
                for g in serie["games"]:
                    for p in g["players"]:
                        self.apps[p["page"]].append((d, p["team"], p["pos"], y))
                        self.first_year[p["page"]] = min(self.first_year.get(p["page"], y), y)
                # Manches deja saisies : qui la saisie place-t-elle la ou Leaguepedia met tel joueur ?
                existing = sorted(saisies[serie["db"]["id"]], key=lambda g: g["game_number"])
                if existing and len(existing) == len(serie["games"]):
                    for have, g in zip(existing, serie["games"]):
                        by_slot = {(p["team_id"], p["position"]): p for p in players_of[have["id"]]}
                        for p in g["players"]:
                            h = by_slot.get((p["team"], p["pos"]))
                            if h:
                                self.slots[h["player_id"]][p["page"]] += 1
        for page in self.apps:
            self.apps[page].sort()
        names = {t["Team"] for ts in self.tenures.values() for t in ts if t["Team"]}
        self.teams = Teams(self.plans[self.years[-1]].db.teams, set(self.teams.info) | names)

    def page_teams(self, page):
        teams = {a[1] for a in self.apps[page]}
        for t in self.tenures.get(page, []):
            tid = self.teams.id(t["Team"]) if t["Team"] else None
            if tid:
                teams.add(tid)
        return teams

    # --- rattachement aux joueurs deja saisis ------------------------------------

    def _link_players(self):
        by_key = {p["source_key"]: p for p in self.db_players if p["source_key"]}
        players = {p["id"]: p for p in self.db_players}
        unlinked = defaultdict(list)
        for p in self.db_players:
            if not p["source_key"]:
                unlinked[p["pseudo"].lower()].append(p)
        stint_teams = defaultdict(set)
        for st in self.db_stints:
            stint_teams[st["player_id"]].add(st["team_id"])
        team_ids = {name: i for i, name in self.teams.names.items()}

        self.player_of = {}              # page -> joueur en base garde
        self.merged = defaultdict(list)  # page -> joueurs en base fusionnes dans le joueur garde
        claims = defaultdict(list)       # joueur en base -> pages candidates
        cands_of = {}
        for page in sorted(self.first_year):
            key = "leaguepedia:" + page
            if key in by_key:
                self.player_of[page] = by_key[key]
                continue
            info = self.info.get(page, {})
            wanted = self.names[page] | {page, info.get("ID") or ""}
            wanted = {n.lower() for n in wanted | {base_name(n) for n in wanted} if n}
            teams = self.page_teams(page)
            cands, others = {}, []
            for n in wanted:
                for p in unlinked.get(n, []):
                    if stint_teams[p["id"]] & teams or not stint_teams[p["id"]]:
                        cands[p["id"]] = p
                    else:
                        others.append(p)
            # Joueur saisi a la place exacte ou Leaguepedia met ce joueur, sur toutes ses manches.
            for pid, pages in self.slots.items():
                if len(pages) == 1 and page in pages and not players[pid]["source_key"]:
                    cands[pid] = players[pid]
            if page in MANUAL_LINKS:
                pseudo, team = MANUAL_LINKS[page]
                for p in unlinked.get(pseudo.lower(), []):
                    if team_ids.get(team) in stint_teams[p["id"]]:
                        cands[p["id"]] = p
            for p in others:
                if p["id"] not in cands:
                    self.issues[self.first_year[page]].append(
                        f"{p['pseudo']} (base) n'est pas {page} : aucune equipe en commun, joueur distinct")
            cands_of[page] = list(cands.values())
            for p in cands.values():
                claims[p["id"]].append(page)
        # Un joueur en base reclame par plusieurs pages : celle qui partage le plus ses equipes.
        owner = {}
        for pid, pages in claims.items():
            def score(page, pid=pid):
                return (self.slots[pid][page], len(stint_teams[pid] & self.page_teams(page)))
            pages.sort(key=lambda page: (-score(page)[0], -score(page)[1], self.first_year[page], page))
            owner[pid] = pages[0]
            if len(pages) > 1:
                self.issues[self.first_year[pages[0]]].append(
                    f"{players[pid]['pseudo']} (base) relie a {pages[0]} plutot qu'a {', '.join(pages[1:])}")
        for page, cands in cands_of.items():
            mine = [p for p in cands if owner[p["id"]] == page]
            if not mine:
                continue
            info = self.info.get(page, {})
            current = (info.get("ID") or base_name(page)).lower()
            mine.sort(key=lambda p: (-self.game_rows[p["id"]], p["pseudo"].lower() != current, p["id"]))
            self.player_of[page] = mine[0]
            self.merged[page] = mine[1:]
            if mine[1:]:
                self.issues[self.first_year[page]].append(
                    f"fusion : {', '.join(p['pseudo'] for p in mine[1:])} -> {mine[0]['pseudo']} ({page})")

    # --- pseudo, nationalite, poste ----------------------------------------------

    def _player_fields(self):
        self.fields = {}
        for page in self.first_year:
            info = self.info.get(page, {})
            existing = self.player_of.get(page)
            pseudo = (info.get("ID") or base_name(page)).strip()[:50]
            nat_name = (info.get("NationalityPrimary") or info.get("Country") or "").strip()
            nationality = self.countries.get(nat_name) if nat_name else None
            if nat_name and not nationality:
                self.issues[self.first_year[page]].append(f"pays inconnu '{nat_name}' pour {page}")
            if not nationality and existing:
                nationality = existing["nationality"]
            position = ROLES.get((info.get("Role") or "").strip().lower())
            if not position and self.apps[page]:
                # Plus joueur (staff, retraite) : son poste le plus joue (le plus recent a egalite).
                counts = Counter(a[2] for a in self.apps[page])
                last_seen = {a[2]: i for i, a in enumerate(self.apps[page])}
                position = max(counts, key=lambda pos: (counts[pos], last_seen[pos]))
            if not position:
                position = existing["position"] if existing else "MID"
            self.fields[page] = {"pseudo": pseudo, "nationality": nationality, "position": position}

    # --- passages ----------------------------------------------------------------

    def _intervals(self, page):
        """Contrats Leaguepedia dans des equipes en base : [team, debut, fin exclue|None, en cours]."""
        apps = [(day(a[0]), a[1]) for a in self.apps[page]]
        rows = []
        for t in self.tenures.get(page, []):
            tid = self.teams.id(t["Team"]) if t["Team"] else None
            start, leave = day(t["DateJoin"]), day(t["DateLeave"])
            if tid is None or start is None or (leave is not None and leave <= start):
                continue  # equipe hors base, ou contrat vide (arrivee et depart le meme jour)
            roles = self.join_roles.get((page, t["Team"], t["DateJoin"][:10]))
            playing = [ROLES[r.lower()] for r in roles or [] if r.lower() in ROLES]
            if roles and not playing:
                continue  # coach, manager, dirigeant... : pas un passage de joueur
            if not playing and not any(t_id == tid and start <= d and (leave is None or d < leave)
                                       for d, t_id in apps):
                continue  # role inconnu et aucune manche jouee : contrat douteux
            rows.append([tid, start, leave, t["IsCurrent"] == "1", playing[0] if playing else None])
        rows.sort(key=lambda r: (r[0], r[1]))
        merged = []
        for r in rows:
            prev = merged[-1] if merged else None
            if prev and prev[0] == r[0] and (prev[2] is None or r[1] <= prev[2] + timedelta(days=1)):
                if prev[2] is not None and (r[2] is None or r[2] > prev[2]):
                    prev[2] = r[2]
                prev[3] = prev[3] or r[3]
                prev[4] = prev[4] or r[4]
            else:
                merged.append(r)
        for r in merged:
            if r[2] is None and not r[3]:
                # Contrat sans fin connue, pas en cours : jusqu'a la derniere manche dans l'equipe.
                games = [d for d, t in apps if t == r[0] and d >= r[1]]
                r[2] = (max(games) if games else r[1]) + timedelta(days=1)
        # Manches jouees hors de tout contrat connu : un passage le temps de ces manches.
        loose = []
        for d, tid in apps:
            if any(r[0] == tid and r[1] <= d and (r[2] is None or d < r[2]) for r in merged):
                continue
            if loose and loose[-1][0] == tid:
                loose[-1][2] = d + timedelta(days=1)
            else:
                loose.append([tid, d, d + timedelta(days=1), False, None])
        merged += loose
        merged.sort(key=lambda r: (r[1], -(r[2] or date_cls.max).toordinal()))
        # Pas de chevauchement (l'appli l'interdit) : un contrat s'arrete quand le suivant commence,
        # et un contrat inclus dans un autre (pret, double contrat) est ignore.
        clean = []
        for r in merged:
            prev = clean[-1] if clean else None
            if prev and (prev[2] is None or prev[2] > r[1]):
                if prev[2] is None or (r[2] is not None and prev[2] >= r[2]):
                    continue
                prev[2], prev[3] = r[1], False
                if prev[2] <= prev[1]:
                    clean.pop()
            clean.append(r)
        return clean

    def _stints(self):
        self.stints = {}  # page -> passages de toute sa carriere
        for page in self.first_year:
            apps = [(day(a[0]), a[1], a[2]) for a in self.apps[page]]
            result = []
            for tid, start, end, current, role in self._intervals(page):
                games = [(d, pos) for d, t, pos in apps if t == tid and start <= d and (end is None or d < end)]
                runs = self._runs(games) if games else []
                if not runs:
                    near = min(apps, key=lambda a: abs((a[0] - start).days)) if apps else None
                    pos = role or (near[2] if near else self.fields[page]["position"])
                    runs = [{"pos": pos, "first": start}]
                for i, run in enumerate(runs):
                    s = start if i == 0 else run["first"]
                    if i + 1 < len(runs):
                        e = runs[i + 1]["first"] - timedelta(days=1)
                    else:
                        e = None if current else end - timedelta(days=1)
                    if e is None or e >= s:
                        result.append({"page": page, "team": tid, "pos": run["pos"], "start": s, "end": e})
            # Contrats successifs dans la meme equipe au meme poste (renouvellement) : un seul passage.
            merged = []
            for st in sorted(result, key=lambda st: st["start"]):
                prev = merged[-1] if merged else None
                if prev and prev["team"] == st["team"] and prev["pos"] == st["pos"] and prev["end"] is not None \
                        and st["start"] <= prev["end"] + timedelta(days=1):
                    prev["end"] = st["end"]
                else:
                    merged.append(st)
            self.stints[page] = merged

    def _runs(self, games):
        """Postes successifs, sans couper pour un depannage de quelques journees."""
        runs = []
        for d, pos in sorted(games):
            if runs and runs[-1]["pos"] == pos:
                runs[-1]["days"].add(d)
                runs[-1]["games"].append(pos)
            else:
                runs.append({"pos": pos, "days": {d}, "games": [pos]})
        while len(runs) > 1:
            small = min(range(len(runs)), key=lambda i: len(runs[i]["days"]))
            if len(runs[small]["days"]) >= MIN_DAYS_FOR_ROLE_SWAP:
                break
            if small == 0:
                target = 1
            elif small == len(runs) - 1:
                target = small - 1
            else:
                target = small - 1 if len(runs[small - 1]["days"]) >= len(runs[small + 1]["days"]) else small + 1
            runs[target]["days"] |= runs[small]["days"]
            runs[target]["games"] += runs[small]["games"]
            del runs[small]
            for r in runs:
                # Poste d'une periode : le plus joue, depannages absorbes compris.
                r["pos"] = Counter(r["games"]).most_common(1)[0][0]
            merged = []
            for r in runs:
                if merged and merged[-1]["pos"] == r["pos"]:
                    merged[-1]["days"] |= r["days"]
                    merged[-1]["games"] += r["games"]
                else:
                    merged.append(r)
            runs = merged
        for r in runs:
            r["first"] = min(r["days"])
        runs.sort(key=lambda r: r["first"])
        return runs


# ---------------------------------------------------------------------------
# Ce que la migration d'une saison ecrit (d'apres l'etat de la base locale)
# ---------------------------------------------------------------------------

def season_rows(imp, year):
    """Lignes a ecrire pour une saison, et ecarts avec ce qui est deja saisi."""
    plan = imp.plans[year]
    db = plan.db
    games_of = defaultdict(list)
    for g in db.games:
        games_of[g["match_id"]].append(g)
    players_of = defaultdict(list)
    for p in db.game_players:
        players_of[p["game_id"]].append(p)
    bans_of = defaultdict(set)
    for b in db.game_bans:
        bans_of[b["game_id"]].add((b["team_id"], b["champion"]))
    pid = lambda page: (imp.player_of.get(page) or {}).get("id")
    pseudo = lambda page: imp.fields[page]["pseudo"] if page in imp.fields else page

    out = {"series": [], "games": [], "game_players": [], "bans": [], "fixes": [], "diffs": [], "kept": 0}
    diff = out["diffs"].append
    for serie in plan.series:
        m = serie["db"]
        label = f"{m['date']} {m['team1'] or '?'} - {m['team2'] or '?'} (match {m['id']})"
        row = {"match_id": m["id"], "source_key": m["source_key"], "date": m["date"], "time": m["time"],
               "team1_id": serie["team1_id"], "team2_id": serie["team2_id"], "score1": serie["score1"],
               "score2": serie["score2"], "mvp": None}
        if serie["mvp"]:
            row["mvp"] = "leaguepedia:" + plan.lp.page_of.get(serie["mvp"], serie["mvp"])
        if m["score1"] is not None and serie["score1"] is not None and \
                (m["score1"], m["score2"]) != (serie["score1"], serie["score2"]):
            diff(f"{label} : score saisi {m['score1']}-{m['score2']}, Leaguepedia "
                 f"{serie['score1']}-{serie['score2']} (conserve)")
        needs = (m["score1"] is None and serie["score1"] is not None) or m["team1_id"] is None \
            or m["team2_id"] is None or (row["mvp"] and m["mvp_player_id"] is None)
        if needs:
            out["series"].append(row)

        existing = sorted(games_of[m["id"]], key=lambda g: g["game_number"])
        lp_games = serie["games"]
        if existing and len(existing) != len(lp_games):
            diff(f"{label} : {len(existing)} manche(s) saisie(s), {len(lp_games)} sur Leaguepedia (conserve)")
            out["kept"] += 1
            continue
        for i, g in enumerate(lp_games):
            have = existing[i] if existing else None
            number = have["game_number"] if have else g["n"]
            mvp = "leaguepedia:" + plan.lp.page_of.get(g["mvp"], g["mvp"]) if g["mvp"] else None
            out["games"].append({"match_id": m["id"], "n": number, "winner": g["winner"], "mvp": mvp})
            if have:
                glabel = f"{label} manche {number}"
                if have["winner_team_id"] and g["winner"] and have["winner_team_id"] != g["winner"]:
                    diff(f"{glabel} : vainqueur saisi {plan.teams.names.get(have['winner_team_id'])}, "
                         f"Leaguepedia {plan.teams.names.get(g['winner'])} (conserve)")
                if not have["winner_team_id"] and g["winner"]:
                    diff(f"{glabel} : vainqueur manquant, complete ({plan.teams.names.get(g['winner'])})")
                saisis = players_of[have["id"]]
                if saisis:
                    by_slot = {(p["team_id"], p["position"]): p for p in saisis}
                    for p in g["players"]:
                        h = by_slot.get((p["team"], p["pos"]))
                        if not h:
                            diff(f"{glabel} : {pseudo(p['page'])} ({p['pos']}) absent de la saisie")
                            continue
                        merged_ids = {x["id"] for x in imp.merged.get(p["page"], [])}
                        same_player = pid(p["page"]) == h["player_id"] or h["player_id"] in merged_ids
                        kda_h = (h["kills"], h["deaths"], h["assists"])
                        kda_l = (p["k"], p["d"], p["a"])
                        if not same_player or h["champion"] != p["champ"] or kda_h != kda_l:
                            out["fixes"].append({"match_id": m["id"], "n": number, "team": p["team"], "pos": p["pos"],
                                                 "old_player": h["player_id"], "old_champ": h["champion"],
                                                 "player": "leaguepedia:" + p["page"], "champ": p["champ"],
                                                 "k": p["k"], "d": p["d"], "a": p["a"]})
                        notes = []
                        if not same_player:
                            notes.append(f"joueur {h['pseudo']} -> {pseudo(p['page'])}")
                        if h["champion"] != p["champ"]:
                            notes.append(f"champion {h['champion']} -> {p['champ']}")
                        if kda_h != kda_l:
                            notes.append("K/D/A " + "/".join(map(str, kda_h)) + " -> " + "/".join(map(str, kda_l)))
                        if notes:
                            diff(f"{glabel} {p['pos']} {plan.teams.names.get(p['team'])} : " + ", ".join(notes)
                                 + " (corrige)")
                    continue  # manche deja detaillee : rien n'est ajoute
                if bans_of[have["id"]] and g["bans"] and bans_of[have["id"]] != set(g["bans"]):
                    diff(f"{glabel} : bans differents (conserves)")
            for p in g["players"]:
                out["game_players"].append({"match_id": m["id"], "n": number, "player": "leaguepedia:" + p["page"],
                                            "team": p["team"], "pos": p["pos"], "champ": p["champ"],
                                            "k": p["k"], "d": p["d"], "a": p["a"]})
            if not (have and bans_of[have["id"]]):
                for tid, champ in g["bans"]:
                    out["bans"].append({"match_id": m["id"], "n": number, "team": tid, "champ": champ})
    return out


def season_players(imp, year):
    """Joueurs apparus cette saison : relies (et fusionnes) ou crees, avec toute leur carriere."""
    rows = []
    for page in sorted(p for p, y in imp.first_year.items() if y == year):
        existing = imp.player_of.get(page)
        if existing and existing["source_key"]:
            continue  # deja relie par une migration precedente
        rows.append({"key": "leaguepedia:" + page, "page": page, **imp.fields[page],
                     "existing": existing, "merged": imp.merged.get(page, []),
                     "stints": imp.stints.get(page, [])})
    return rows


# ---------------------------------------------------------------------------
# Rapport
# ---------------------------------------------------------------------------

def load(year):
    years = [y for y in range(lp.FIRST_YEAR, lp.LAST_YEAR + 1) if cached_season(y)]
    if year not in years:
        sys.exit(f"saison {year} pas encore telechargee : python import_games.py fetch {year}")
    return Import(years)


def cached_season(year):
    return any(lp.CACHE.glob(f"tenures_{year}_*.json"))


def report(year):
    imp = load(year)
    plan = imp.plans[year]
    rows = season_rows(imp, year)
    players = season_players(imp, year)
    names = imp.teams.names
    print(f"=== Saison {year} (historique charge : {imp.years[0]}-{imp.years[-1]}) ===")
    print(f"Series Leaguepedia rapprochees : {len(plan.series)} / matchs en base : {len(plan.db.matches)}")
    for m in plan.unmatched_db:
        print(f"  match en base sans equivalent Leaguepedia : {m['id']} {m['date']} {m['team1']} - {m['team2']}")
    filled = sum(1 for r in rows["series"] if r["score1"] is not None)
    print(f"Series completees : {len(rows['series'])} (dont {filled} avec score)")
    print(f"Manches : {len(rows['games'])}, lignes joueurs : {len(rows['game_players'])}, bans : {len(rows['bans'])}, "
          f"corrections de saisie : {len(rows['fixes'])}")
    linked = [p for p in players if p["existing"]]
    print(f"\nJoueurs apparus en {year} : {len(players)} ({len(linked)} relies a une fiche existante, "
          f"{len(players) - len(linked)} crees)")
    for p in players:
        e = p["existing"]
        merged = f" (+ fusion de {', '.join(x['pseudo'] for x in p['merged'])})" if p["merged"] else ""
        if e:
            changes = [f"{k} {e[k]} -> {p[k]}" for k in ("pseudo", "nationality", "position") if e[k] != p[k]]
            if changes or merged:
                print(f"  {e['pseudo']} : " + ", ".join(changes) + merged)
        else:
            print(f"  nouveau : {p['pseudo']} ({p['nationality']}, {p['position']}) [{p['page']}]")
    print("\nPassages (carriere complete des joueurs ci-dessus, d'apres Leaguepedia) :")
    old = defaultdict(list)
    for st in imp.db_stints:
        old[st["player_id"]].append(st)
    for p in players:
        ids = [x["id"] for x in ([p["existing"]] if p["existing"] else []) + p["merged"]]
        before = sorted((x for i in ids for x in old[i]), key=lambda x: x["start_date"] or "")
        print(f"  {p['pseudo']} :")
        for x in before:
            print(f"      - saisi : {x['team']} {x['position']} {x['start_date']} -> {x['end_date']}")
        for st in p["stints"]:
            print(f"      + {names.get(st['team'])} {st['pos']} {st['start']} -> {st['end'] or 'en cours'}")
    print(f"\nEcarts avec les donnees saisies ({len(rows['diffs'])}) :")
    for d in rows["diffs"]:
        print("  " + d)
    print(f"\nAnomalies ({len(imp.issues[year])}) :")
    for i in imp.issues[year]:
        print("  " + i)


# ---------------------------------------------------------------------------
# Generation SQL
# ---------------------------------------------------------------------------

def sql(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def values(w, table, columns, rows):
    """Remplit une table temporaire, par paquets."""
    for chunk in lp.chunks(rows, 500):
        w(f"INSERT INTO {table} ({', '.join(columns)}) VALUES")
        w(",\n".join("    (" + ", ".join(sql(v) for v in row) + ")" for row in chunk) + ";")


def check(w, condition_sql, message):
    """
    Arrete la migration si la condition est fausse : conversion en entier d'un texte qui depend
    du resultat (un texte constant serait converti, et l'erreur levee, meme condition vraie).
    """
    w(f"SELECT ({sql('ERREUR import Leaguepedia : ' + message + ' ')} || c.ok::text)::int "
      f"FROM (SELECT ({condition_sql}) AS ok) c WHERE NOT c.ok;")


def emit_sql(year):
    imp = load(year)
    plan = imp.plans[year]
    rows = season_rows(imp, year)
    players = season_players(imp, year)
    out = []
    w = out.append
    w(f"-- Detail des matchs {year} importe de Leaguepedia (https://lol.fandom.com, CC BY-SA 3.0) par")
    w("-- import/import_games.py : NE PAS EDITER A LA MAIN, regenerer le fichier.")
    w("-- Scores des series sans score, manches (vainqueur, MVP, champions, K/D/A, bans) des series")
    w("-- sans manche, joueurs relies a Leaguepedia (pseudo actuel, nationalite, poste, passages).")
    w("-- Les verifications 'ERREUR import Leaguepedia' arretent la migration si la base ne")
    w("-- correspond pas a celle d'ou le fichier a ete genere.")

    # --- equipes referencees : verification ---------------------------------------
    team_ids = set()
    for r in rows["series"]:
        team_ids |= {r["team1_id"], r["team2_id"]}
    team_ids |= {g["winner"] for g in rows["games"]} | {p["team"] for p in rows["game_players"]}
    team_ids |= {b["team"] for b in rows["bans"]} | {st["team"] for p in players for st in p["stints"]}
    team_ids |= {f["team"] for f in rows["fixes"]}
    team_ids.discard(None)
    if team_ids:
        w("")
        w("CREATE TEMP TABLE lp_team (id BIGINT PRIMARY KEY, name TEXT NOT NULL);")
        values(w, "lp_team", ["id", "name"], [(t, imp.teams.names[t]) for t in sorted(team_ids)])
        check(w, "(SELECT count(*) FROM lp_team l JOIN team t ON t.id = l.id AND t.name = l.name) = "
                 "(SELECT count(*) FROM lp_team)", "equipe introuvable")

    # --- joueurs -------------------------------------------------------------------
    if players:
        w("")
        w("-- Joueurs : relies a leur page Leaguepedia (fiche existante, doublons fusionnes) ou crees.")
        w("CREATE TEMP TABLE lp_player (source_key TEXT PRIMARY KEY, pseudo TEXT NOT NULL, nationality TEXT, "
          "position TEXT NOT NULL, existing_id BIGINT, existing_pseudo TEXT);")
        values(w, "lp_player", ["source_key", "pseudo", "nationality", "position", "existing_id", "existing_pseudo"],
               [(p["key"], p["pseudo"], p["nationality"], p["position"],
                 p["existing"]["id"] if p["existing"] else None,
                 p["existing"]["pseudo"] if p["existing"] else None) for p in players])
        check(w, "(SELECT count(*) FROM lp_player l JOIN player p ON p.id = l.existing_id "
                 "AND p.pseudo = l.existing_pseudo AND p.source_key IS NULL) = "
                 "(SELECT count(*) FROM lp_player WHERE existing_id IS NOT NULL)", "joueur existant introuvable")
        merges = [(x["id"], x["pseudo"], p["existing"]["id"]) for p in players for x in p["merged"]]
        if merges:
            w("")
            w("-- Doublons : manches, MVP et passages passent sur la fiche gardee, le doublon est supprime.")
            w("CREATE TEMP TABLE lp_merge (dup_id BIGINT PRIMARY KEY, dup_pseudo TEXT NOT NULL, "
              "keep_id BIGINT NOT NULL);")
            values(w, "lp_merge", ["dup_id", "dup_pseudo", "keep_id"], merges)
            check(w, "(SELECT count(*) FROM lp_merge m JOIN player p ON p.id = m.dup_id "
                     "AND p.pseudo = m.dup_pseudo) = (SELECT count(*) FROM lp_merge)", "doublon introuvable")
            w("UPDATE match_game_player gp SET player_id = m.keep_id FROM lp_merge m WHERE gp.player_id = m.dup_id;")
            w("UPDATE match_game g SET mvp_player_id = m.keep_id FROM lp_merge m WHERE g.mvp_player_id = m.dup_id;")
            w("UPDATE match x SET mvp_player_id = m.keep_id FROM lp_merge m WHERE x.mvp_player_id = m.dup_id;")
            w("DELETE FROM player_stint s USING lp_merge m WHERE s.player_id = m.dup_id;")
            w("DELETE FROM player p USING lp_merge m WHERE p.id = m.dup_id;")
            w("DROP TABLE lp_merge;")
        w("")
        w("UPDATE player p SET source_key = l.source_key FROM lp_player l WHERE p.id = l.existing_id;")
        w("INSERT INTO player (pseudo, nationality, position, source_key) "
          "SELECT l.pseudo, l.nationality, l.position, l.source_key FROM lp_player l WHERE l.existing_id IS NULL;")
        w("UPDATE player p SET pseudo = l.pseudo, nationality = l.nationality, position = l.position "
          "FROM lp_player l WHERE p.source_key = l.source_key;")
        w("")
        w("-- Passages : toute la carriere d'apres les contrats Leaguepedia (remplace les passages saisis).")
        w("DELETE FROM player_stint s USING lp_player l, player p "
          "WHERE p.source_key = l.source_key AND s.player_id = p.id;")
        stints = [(p["key"], st["team"], st["pos"], st["start"].isoformat(), st["end"] and st["end"].isoformat())
                  for p in players for st in p["stints"]]
        if stints:
            w("CREATE TEMP TABLE lp_stint (source_key TEXT NOT NULL, team_id BIGINT NOT NULL, position TEXT NOT NULL, "
              "start_date DATE NOT NULL, end_date DATE);")
            values(w, "lp_stint", ["source_key", "team_id", "position", "start_date", "end_date"], stints)
            w("INSERT INTO player_stint (player_id, team_id, position, start_date, end_date) "
              "SELECT p.id, s.team_id, s.position, s.start_date, s.end_date FROM lp_stint s "
              "JOIN player p ON p.source_key = s.source_key;")
            w("DROP TABLE lp_stint;")
        w("-- Equipe actuelle = celle du passage en cours (aucune pour un joueur sans contrat).")
        w("UPDATE player p SET team_id = (SELECT s.team_id FROM player_stint s WHERE s.player_id = p.id "
          "AND s.end_date IS NULL) FROM lp_player l WHERE p.source_key = l.source_key;")

    # --- series --------------------------------------------------------------------
    db_match = {m["id"]: m for m in plan.db.matches}
    targets = {r["match_id"]: r for r in rows["series"]}
    for g in rows["games"]:
        targets.setdefault(g["match_id"], None)
    for f in rows["fixes"]:
        targets.setdefault(f["match_id"], None)
    w("")
    w("-- Series : retrouvees par competition, date et heure (verification), puis completees.")
    w("CREATE TEMP TABLE lp_series (match_id BIGINT PRIMARY KEY, source_key TEXT NOT NULL, date DATE NOT NULL, "
      "time TIME, team1_id BIGINT, team2_id BIGINT, score1 INT, score2 INT, mvp TEXT);")
    series_rows = []
    for mid in sorted(targets):
        r, m = targets[mid] or {}, db_match[mid]
        series_rows.append((mid, m["source_key"], m["date"], m["time"], r.get("team1_id"), r.get("team2_id"),
                            r.get("score1"), r.get("score2"), r.get("mvp")))
    if series_rows:
        values(w, "lp_series", ["match_id", "source_key", "date", "time", "team1_id", "team2_id", "score1",
                                "score2", "mvp"], series_rows)
    check(w, "(SELECT count(*) FROM lp_series s JOIN match m ON m.id = s.match_id AND m.date = s.date "
             "AND m.time IS NOT DISTINCT FROM s.time JOIN competition c ON c.id = m.competition_id "
             "AND c.source_key = s.source_key) = (SELECT count(*) FROM lp_series)", "match introuvable")
    w("UPDATE match m SET team1_id = coalesce(m.team1_id, s.team1_id), team2_id = coalesce(m.team2_id, s.team2_id), "
      "score1 = s.score1, score2 = s.score2, status = 'COMPLETED' "
      "FROM lp_series s WHERE m.id = s.match_id AND m.score1 IS NULL AND s.score1 IS NOT NULL;")
    w("UPDATE match m SET team1_id = coalesce(m.team1_id, s.team1_id), team2_id = coalesce(m.team2_id, s.team2_id) "
      "FROM lp_series s WHERE m.id = s.match_id AND (m.team1_id IS NULL OR m.team2_id IS NULL);")
    w("UPDATE match m SET mvp_player_id = p.id FROM lp_series s JOIN player p ON p.source_key = s.mvp "
      "WHERE m.id = s.match_id AND m.mvp_player_id IS NULL;")

    # --- manches -------------------------------------------------------------------
    if rows["games"]:
        w("")
        w("-- Manches : creees si absentes ; vainqueur et MVP completes s'ils manquent.")
        w("CREATE TEMP TABLE lp_game (match_id BIGINT NOT NULL, game_number INT NOT NULL, winner_team_id BIGINT, "
          "mvp TEXT, PRIMARY KEY (match_id, game_number));")
        values(w, "lp_game", ["match_id", "game_number", "winner_team_id", "mvp"],
               [(g["match_id"], g["n"], g["winner"], g["mvp"]) for g in rows["games"]])
        w("INSERT INTO match_game (match_id, game_number, winner_team_id, mvp_player_id) "
          "SELECT g.match_id, g.game_number, g.winner_team_id, p.id FROM lp_game g "
          "LEFT JOIN player p ON p.source_key = g.mvp WHERE NOT EXISTS "
          "(SELECT 1 FROM match_game x WHERE x.match_id = g.match_id AND x.game_number = g.game_number);")
        w("UPDATE match_game x SET winner_team_id = coalesce(x.winner_team_id, g.winner_team_id), "
          "mvp_player_id = coalesce(x.mvp_player_id, p.id) FROM lp_game g LEFT JOIN player p ON p.source_key = g.mvp "
          "WHERE x.match_id = g.match_id AND x.game_number = g.game_number;")
        w("DROP TABLE lp_game;")
    if rows["game_players"]:
        w("")
        w("-- Champions et K/D/A, pour les manches qui n'en ont pas encore.")
        w("CREATE TEMP TABLE lp_game_player (match_id BIGINT NOT NULL, game_number INT NOT NULL, player TEXT NOT NULL, "
          "team_id BIGINT NOT NULL, position TEXT NOT NULL, champion TEXT NOT NULL, kills INT, deaths INT, "
          "assists INT);")
        values(w, "lp_game_player", ["match_id", "game_number", "player", "team_id", "position", "champion",
                                     "kills", "deaths", "assists"],
               [(p["match_id"], p["n"], p["player"], p["team"], p["pos"], p["champ"], p["k"], p["d"], p["a"])
                for p in rows["game_players"]])
        w("INSERT INTO match_game_player (game_id, player_id, team_id, position, champion, kills, deaths, assists) "
          "SELECT x.id, p.id, l.team_id, l.position, l.champion, l.kills, l.deaths, l.assists FROM lp_game_player l "
          "JOIN match_game x ON x.match_id = l.match_id AND x.game_number = l.game_number "
          "JOIN player p ON p.source_key = l.player "
          "WHERE NOT EXISTS (SELECT 1 FROM match_game_player y WHERE y.game_id = x.id);")
        check(w, "(SELECT count(*) FROM lp_game_player l JOIN match_game x ON x.match_id = l.match_id "
                 "AND x.game_number = l.game_number JOIN player p ON p.source_key = l.player "
                 "JOIN match_game_player y ON y.game_id = x.id AND y.player_id = p.id) = "
                 "(SELECT count(*) FROM lp_game_player)", "ligne joueur non importee")
        w("DROP TABLE lp_game_player;")
    if rows["bans"]:
        w("")
        w("-- Bans, pour les manches qui n'en ont pas encore.")
        w("CREATE TEMP TABLE lp_ban (match_id BIGINT NOT NULL, game_number INT NOT NULL, team_id BIGINT NOT NULL, "
          "champion TEXT NOT NULL);")
        values(w, "lp_ban", ["match_id", "game_number", "team_id", "champion"],
               [(b["match_id"], b["n"], b["team"], b["champ"]) for b in rows["bans"]])
        w("INSERT INTO match_game_ban (game_id, team_id, champion) "
          "SELECT x.id, l.team_id, l.champion FROM lp_ban l "
          "JOIN match_game x ON x.match_id = l.match_id AND x.game_number = l.game_number "
          "WHERE NOT EXISTS (SELECT 1 FROM match_game_ban y WHERE y.game_id = x.id);")
        w("DROP TABLE lp_ban;")
    if rows["fixes"]:
        w("")
        w("-- Corrections de manches deja saisies (joueur, champion, K/D/A) d'apres Leaguepedia.")
        for f in rows["fixes"]:
            game = f"(SELECT id FROM match_game WHERE match_id = {f['match_id']} AND game_number = {f['n']})"
            w(f"UPDATE match_game_player SET player_id = (SELECT id FROM player WHERE source_key = {sql(f['player'])}), "
              f"champion = {sql(f['champ'])}, kills = {sql(f['k'])}, deaths = {sql(f['d'])}, "
              f"assists = {sql(f['a'])} WHERE game_id = {game} AND team_id = {f['team']} "
              f"AND position = {sql(f['pos'])} AND champion = {sql(f['old_champ'])};")
    w("")
    w("DROP TABLE lp_series;")
    if team_ids:
        w("DROP TABLE lp_team;")
    if players:
        w("DROP TABLE lp_player;")
    return "\n".join(out) + "\n"


def main():
    args = sys.argv[1:]
    if len(args) != 2 or args[0] not in COMMANDS or not args[1].isdigit():
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    sys.stdout.reconfigure(encoding="utf-8")
    COMMANDS[args[0]](int(args[1]))


COMMANDS = {"fetch": fetch, "report": report, "sql": lambda year: sys.stdout.write(emit_sql(year))}

if __name__ == "__main__":
    main()
