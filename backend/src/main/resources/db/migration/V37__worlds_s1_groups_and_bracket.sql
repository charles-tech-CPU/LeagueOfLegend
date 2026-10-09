-- Worlds Season 1 (DreamHack Summer 2011) : l'import V17 a range tous les matchs dans
-- une poule unique (onglets Leaguepedia "Round 1/2/3" et "5th Place" pris pour des
-- journees de poule). Format reel (source : Leaguepedia, Season 1 World Championship) :
--
--   Phase de groupes, 2 groupes de 4 en Bo1, les 3 premiers de chaque groupe qualifies :
--     Groupe A : Epik Gamer, against All authority, Fnatic, Team Pacific
--     Groupe B : TSM, Counter Logic Gaming, Team gamed!de, Xan
--   Phase finale en double elimination (6 equipes, Bo3 ; match pour la 5e place en Bo1) :
--     UB R1   : aAa (A2) - gamed!de (B3), CLG (B2) - Fnatic (A3) ; perdants -> 5e place
--     UB R2   : vainqueurs des groupes (TSM, Epik) contre les vainqueurs du R1
--     UB Finale, LB R1 (perdants du UB R2), LB Finale, Grande finale
--
-- Les lignes de match existantes sont mises a jour (pas recreees) : scores et details
-- deja saisis (manches, champions, bans, MVP) sont conserves.

CREATE TEMP TABLE s1_match (k TEXT PRIMARY KEY, match_id BIGINT NOT NULL);

INSERT INTO s1_match (k, match_id)
SELECT v.k, m.id
FROM (VALUES
    ('UB R1 1',      '2011-06-19'::date, '11:30:00'::time),
    ('UB R1 2',      '2011-06-19',       '12:45:00'),
    ('5e place',     '2011-06-19',       '15:00:00'),
    ('UB R2 1',      '2011-06-19',       '16:00:00'),
    ('UB R2 2',      '2011-06-19',       '20:00:00'),
    ('UB Finale',    '2011-06-20',       '13:30:00'),
    ('LB R1',        '2011-06-20',       '16:00:00'),
    ('LB Finale',    '2011-06-20',       '19:30:00'),
    ('Grande finale', '2011-06-20',      '23:00:00')
) AS v(k, d, t)
JOIN match m ON m.date = v.d AND m.time = v.t
JOIN competition c ON c.id = m.competition_id
WHERE c.source_key = 'leaguepedia:Season 1 World Championship';

-- ---------------------------------------------------------------------------
-- Phase de groupes : deux groupes distincts
-- ---------------------------------------------------------------------------
UPDATE competition_stage SET format = 'ROUND_ROBIN', best_of = 'BO1', team_count = 8, group_count = 2, advancing = 3
WHERE competition_id = (SELECT id FROM competition WHERE source_key = 'leaguepedia:Season 1 World Championship')
  AND position = 1;

INSERT INTO competition_group (competition_id, name)
VALUES ((SELECT id FROM competition WHERE source_key = 'leaguepedia:Season 1 World Championship'), 'Groupe A');
INSERT INTO competition_group (competition_id, name)
VALUES ((SELECT id FROM competition WHERE source_key = 'leaguepedia:Season 1 World Championship'), 'Groupe B');

-- Le 18 juin, le groupe A a joue ses 6 matchs de 10h30 a 14h15, le groupe B de 16h30
-- a 20h30 : l'horaire les distingue sans dependre des noms d'equipe (renommables).
UPDATE match m SET competition_group_id = g.id
FROM competition c, competition_group g
WHERE c.source_key = 'leaguepedia:Season 1 World Championship'
  AND m.competition_id = c.id
  AND g.competition_id = c.id
  AND m.round_label = 'Group Stage'
  AND g.name = CASE WHEN m.time < '16:00' THEN 'Groupe A' ELSE 'Groupe B' END;

UPDATE match m SET round_label = 'Groupe A'
FROM competition_group g WHERE g.id = m.competition_group_id AND g.name = 'Groupe A'
  AND m.competition_id = (SELECT id FROM competition WHERE source_key = 'leaguepedia:Season 1 World Championship');
UPDATE match m SET round_label = 'Groupe B'
FROM competition_group g WHERE g.id = m.competition_group_id AND g.name = 'Groupe B'
  AND m.competition_id = (SELECT id FROM competition WHERE source_key = 'leaguepedia:Season 1 World Championship');

-- ---------------------------------------------------------------------------
-- Phase finale : double elimination, avec le match pour la 5e place
-- ---------------------------------------------------------------------------
UPDATE competition_stage SET format = 'DOUBLE_ELIMINATION', best_of = NULL, team_count = 6, group_count = 1
WHERE competition_id = (SELECT id FROM competition WHERE source_key = 'leaguepedia:Season 1 World Championship')
  AND position = 2;

UPDATE match m SET
    stage_id = (SELECT id FROM competition_stage
                WHERE competition_id = m.competition_id AND position = 2),
    phase = 'PLAYOFFS',
    round_label = CASE s.k WHEN '5e place' THEN 'Match pour la 5e place' ELSE s.k END,
    bracket_side = CASE
        WHEN s.k = '5e place' THEN 'PLACEMENT'
        WHEN s.k LIKE 'UB%' THEN 'UPPER'
        WHEN s.k LIKE 'LB%' THEN 'LOWER'
        ELSE 'GRAND_FINAL'
    END
FROM s1_match s
WHERE s.match_id = m.id;

-- Liens du bracket : ou vont le vainqueur (next_match) et le perdant (loser_next_match).
UPDATE match m SET
    next_match_id = (SELECT match_id FROM s1_match WHERE k = l.win_to),
    next_match_slot = l.win_slot,
    loser_next_match_id = (SELECT match_id FROM s1_match WHERE k = l.lose_to),
    loser_next_match_slot = l.lose_slot
FROM (VALUES
    ('UB R1 1',   'UB R2 1',       1, '5e place',  1),
    ('UB R1 2',   'UB R2 2',       2, '5e place',  2),
    ('UB R2 1',   'UB Finale',     1, 'LB R1',     1),
    ('UB R2 2',   'UB Finale',     2, 'LB R1',     2),
    ('UB Finale', 'Grande finale', 1, 'LB Finale', 1),
    ('LB R1',     'LB Finale',     2, NULL,        NULL),
    ('LB Finale', 'Grande finale', 2, NULL,        NULL)
) AS l(k, win_to, win_slot, lose_to, lose_slot)
JOIN s1_match s ON s.k = l.k
WHERE m.id = s.match_id;

DROP TABLE s1_match;
