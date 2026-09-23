-- La LPL et la LCK ne jouent pas une saison reguliere a poule unique : elles
-- sont scindees en deux groupes (source : Leaguepedia, recherches du
-- 2026-09-17). Les matchs de saison reguliere deja en base (round_label
-- W1..W5 pour la LPL, W10..W13 pour la LCK) sont deja, en pratique,
-- strictement internes a un groupe (verifie manuellement : aucun match ne
-- croise les deux groupes) ; il ne manquait que le rattachement a un
-- CompetitionGroup pour que /api/standings et /api/head-to-head puissent
-- les distinguer (groupId optionnel deja supporte par StandingsService).
--
-- LPL 2026 Split 3 : https://lol.fandom.com/wiki/LPL/2026_Season/Split_3
--   Group Ascend (8 equipes) : BLG, AL, TES, JDG, WE, LGD, TT, EDG
--   Group Nirvana (4 equipes) : NIP, IG, WBG, LNG
--
-- LCK 2026 Season, Rounds 3-4 : https://lol.fandom.com/wiki/LCK/2026_Season/Rounds_3-4
--   Legend Group (5 equipes) : T1, KT, GEN, DK, HLE
--   Rise Group (5 equipes)   : NS, KRX, DNS, BFX, BRO
--
-- Les matchs de playoffs (phase PLAYOFFS : KR/R1-R4 LPL, R1-R4 LCK) melangent
-- les deux groupes par construction (qualification) : ils restent volontairement
-- sans competition_group_id.

INSERT INTO competition_group (competition_id, name)
VALUES ((SELECT id FROM competition WHERE code = 'LPL' AND season = 2026), 'Group Ascend');
INSERT INTO competition_group (competition_id, name)
VALUES ((SELECT id FROM competition WHERE code = 'LPL' AND season = 2026), 'Group Nirvana');

UPDATE match SET competition_group_id = (
    SELECT id FROM competition_group
    WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026) AND name = 'Group Ascend'
)
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026)
  AND phase = 'REGULAR_SEASON'
  AND team1_id IN (SELECT id FROM team WHERE code IN ('BLG', 'AL', 'TES', 'JDG', 'WE', 'LGD', 'TT', 'EDG'));

UPDATE match SET competition_group_id = (
    SELECT id FROM competition_group
    WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026) AND name = 'Group Nirvana'
)
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026)
  AND phase = 'REGULAR_SEASON'
  AND team1_id IN (SELECT id FROM team WHERE code IN ('NIP', 'IG', 'WBG', 'LNG'));

INSERT INTO competition_group (competition_id, name)
VALUES ((SELECT id FROM competition WHERE code = 'LCK' AND season = 2026), 'Legend Group');
INSERT INTO competition_group (competition_id, name)
VALUES ((SELECT id FROM competition WHERE code = 'LCK' AND season = 2026), 'Rise Group');

UPDATE match SET competition_group_id = (
    SELECT id FROM competition_group
    WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCK' AND season = 2026) AND name = 'Legend Group'
)
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCK' AND season = 2026)
  AND phase = 'REGULAR_SEASON'
  AND team1_id IN (SELECT id FROM team WHERE code IN ('T1', 'KT', 'GEN', 'DK', 'HLE'));

UPDATE match SET competition_group_id = (
    SELECT id FROM competition_group
    WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCK' AND season = 2026) AND name = 'Rise Group'
)
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCK' AND season = 2026)
  AND phase = 'REGULAR_SEASON'
  AND team1_id IN (SELECT id FROM team WHERE code IN ('NS', 'KRX', 'DNS', 'BFX', 'BRO'));
