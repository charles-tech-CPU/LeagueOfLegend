-- LPL 2026 Regional Finals (source : Leaguepedia, recherches du 2026-09-17,
-- https://lol.fandom.com/wiki/LPL/2026_Season/Regional_Finals).
--
-- Tournoi distinct du split 3 / des playoffs LPL deja en base (code 'LPL') :
-- determine les 3e et 4e seeds LPL pour le World Championship 2026. Les
-- seeds 1 et 2 (champion du Split 3 + meilleur total de Championship Points,
-- soit BLG et AL d'apres le classement deja en base) sont qualifies
-- directement et ne jouent pas ce tournoi.
--
-- Format : 4 equipes (TES, IG, WE, JDG), double elimination, tous les
-- matchs en Bo5, du 17 au 19 septembre 2026 :
--   Upper Bracket Final : TES vs IG (17/09) -> le vainqueur est 3e seed Worlds
--   Lower Bracket SF    : WE vs JDG (18/09)
--   Lower Bracket Final : perdant Upper Final vs vainqueur Lower SF (19/09)
--                         -> le vainqueur est 4e (dernier) seed Worlds
-- Heures recuperees en UTC (nextmatch.lol) ; a ajuster si tu as une source
-- avec le fuseau exact du broadcast.

INSERT INTO competition (code, name, type, region, season)
VALUES ('LPL_RF', 'LPL 2026 Regional Finals', 'INTERNATIONAL_EVENT', 'LPL', 2026);

-- Upper Bracket Final : joue et termine (TES bat IG 3-1) d'apres Leaguepedia/Strafe/EsportNow.
INSERT INTO match (competition_id, round_label, date, time, best_of, team1_id, team2_id, score1, score2, status, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code = 'LPL_RF' AND season = 2026),
    'Upper Final', '2026-09-17', '09:00:00', 'BO5',
    (SELECT id FROM team WHERE code = 'TES'), (SELECT id FROM team WHERE code = 'IG'),
    3, 1, 'COMPLETED', 'PLAYOFFS', 'UPPER'
);

-- Lower Bracket Semifinale : pas encore jouee.
INSERT INTO match (competition_id, round_label, date, time, best_of, team1_id, team2_id, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code = 'LPL_RF' AND season = 2026),
    'Lower SF', '2026-09-18', '09:00:00', 'BO5',
    (SELECT id FROM team WHERE code = 'WE'), (SELECT id FROM team WHERE code = 'JDG'),
    'PLAYOFFS', 'LOWER'
);

-- Lower Bracket Final : team1 deja connue (IG, perdant de l'Upper Final), team2 en
-- attente du vainqueur de la Lower SF.
INSERT INTO match (competition_id, round_label, date, time, best_of, team1_id, team2_id, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code = 'LPL_RF' AND season = 2026),
    'Lower Final', '2026-09-19', '09:00:00', 'BO5',
    (SELECT id FROM team WHERE code = 'IG'), NULL,
    'PLAYOFFS', 'LOWER'
);

-- Liens d'avancement automatique (voir MatchService.propagateAdvancement) :
-- perdant de l'Upper Final -> deja pose en team1 ci-dessus, lien conserve pour coherence.
UPDATE match SET
    loser_next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL_RF' AND season = 2026) AND round_label = 'Lower Final'),
    loser_next_match_slot = 1
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL_RF' AND season = 2026) AND round_label = 'Upper Final';

-- vainqueur de la Lower SF -> Lower Final, slot 2.
UPDATE match SET
    next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL_RF' AND season = 2026) AND round_label = 'Lower Final'),
    next_match_slot = 2
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL_RF' AND season = 2026) AND round_label = 'Lower SF';
