-- Retour en arriere sur V6 : les Regional Finals ne sont pas une competition
-- a part, mais un onglet dedie de la ligue LPL elle-meme (demande utilisateur).
-- On supprime la competition 'LPL_RF' et on recree les 3 memes matchs
-- directement dans la competition 'LPL' (season 2026), avec un bracket_side
-- prefixe 'REGIONAL_' pour que le frontend puisse les distinguer des
-- playoffs du Split 3 (memes UPPER/LOWER, mais un autre tournoi) et les
-- afficher dans leur propre onglet plutot que dans l'onglet "Bracket".

DELETE FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL_RF' AND season = 2026);
DELETE FROM competition WHERE code = 'LPL_RF' AND season = 2026;

-- Upper Bracket Final : joue et termine (TES bat IG 3-1) d'apres Leaguepedia/Strafe/EsportNow.
INSERT INTO match (competition_id, round_label, date, time, best_of, team1_id, team2_id, score1, score2, status, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026),
    'RF Upper Final', '2026-09-17', '09:00:00', 'BO5',
    (SELECT id FROM team WHERE code = 'TES'), (SELECT id FROM team WHERE code = 'IG'),
    3, 1, 'COMPLETED', 'PLAYOFFS', 'REGIONAL_UPPER'
);

-- Lower Bracket Semifinale : pas encore jouee.
INSERT INTO match (competition_id, round_label, date, time, best_of, team1_id, team2_id, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026),
    'RF Lower SF', '2026-09-18', '09:00:00', 'BO5',
    (SELECT id FROM team WHERE code = 'WE'), (SELECT id FROM team WHERE code = 'JDG'),
    'PLAYOFFS', 'REGIONAL_LOWER'
);

-- Lower Bracket Final : team1 deja connue (IG, perdant de l'Upper Final), team2 en
-- attente du vainqueur de la Lower SF.
INSERT INTO match (competition_id, round_label, date, time, best_of, team1_id, team2_id, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026),
    'RF Lower Final', '2026-09-19', '09:00:00', 'BO5',
    (SELECT id FROM team WHERE code = 'IG'), NULL,
    'PLAYOFFS', 'REGIONAL_LOWER'
);

-- Liens d'avancement automatique (voir MatchService.propagateAdvancement) :
UPDATE match SET
    loser_next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026) AND round_label = 'RF Lower Final'),
    loser_next_match_slot = 1
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026) AND round_label = 'RF Upper Final';

UPDATE match SET
    next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026) AND round_label = 'RF Lower Final'),
    next_match_slot = 2
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LPL' AND season = 2026) AND round_label = 'RF Lower SF';
