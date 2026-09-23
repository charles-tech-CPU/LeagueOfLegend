-- Reorganisation des playoffs LCP 2026 Split 3 d'apres Leaguepedia/Liquipedia
-- (recherches du 2026-09-17, https://lol.fandom.com/wiki/LCP/2026_Season/Split_3).
--
-- V5 avait deliberement laisse cette partie de coter : les 3 matchs 'QF'
-- inseres par V2 etaient incoherents (GAM y apparaissait dans DEUX matchs
-- differents, impossible en simple elimination directe -> erreur de
-- saisie dans le fichier Excel source). Recherche confirmee : les 4
-- qualifies du Swiss Stage sont TSW (3-0), CFO (3-1), MVK (3-1), GAM (3-2),
-- et le format des playoffs est une double elimination a 4 equipes (pas un
-- simple quart de finale a 8) :
--   Upper R1  : CFO bat MVK 3-0 ; TSW bat GAM 3-1
--   Lower R2  : MVK (perdant upper) bat GAM (perdant upper) 3-2
--   Upper Final (R3) : TSW bat CFO 3-0 -> TSW qualifie pour la Grand Finale
--   Lower Final (R4) : CFO bat MVK 3-1 -> CFO qualifie pour la Grand Finale
--   Grand Finale (F)  : TSW bat CFO 3-0 -> TSW champion LCP Split 3 2026
-- Les 3 'QF' et 2 'SF' de V2 sont donc renommes/retagges en consequence
-- (round_label + bracket_side), avec les liens d'avancement automatique
-- (voir MatchService.propagateAdvancement).
--
-- Remarque : si cette migration s'applique a une base qui contient deja ce
-- correctif (fait manuellement via l'appli avant d'etre formalise ici), les
-- WHERE round_label='QF'/'SF' ne trouvent plus rien et les UPDATE ne font
-- rien : sans danger, idempotent en pratique.

UPDATE match SET round_label = 'R1', bracket_side = 'UPPER'
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND round_label = 'QF' AND team1_id = (SELECT id FROM team WHERE code = 'CFO') AND team2_id = (SELECT id FROM team WHERE code = 'MVK');

UPDATE match SET round_label = 'R1', bracket_side = 'UPPER'
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND round_label = 'QF' AND team1_id = (SELECT id FROM team WHERE code = 'TSW') AND team2_id = (SELECT id FROM team WHERE code = 'GAM');

UPDATE match SET round_label = 'R2', bracket_side = 'LOWER'
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND round_label = 'QF' AND team1_id = (SELECT id FROM team WHERE code = 'MVK') AND team2_id = (SELECT id FROM team WHERE code = 'GAM');

UPDATE match SET round_label = 'R3', bracket_side = 'UPPER'
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND round_label = 'SF' AND team1_id = (SELECT id FROM team WHERE code = 'CFO') AND team2_id = (SELECT id FROM team WHERE code = 'TSW');

UPDATE match SET round_label = 'R4', bracket_side = 'LOWER'
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND round_label = 'SF' AND team1_id = (SELECT id FROM team WHERE code = 'CFO') AND team2_id = (SELECT id FROM team WHERE code = 'MVK');

UPDATE match SET bracket_side = 'GRAND_FINAL'
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND round_label = 'F' AND team1_id = (SELECT id FROM team WHERE code = 'TSW') AND team2_id = (SELECT id FROM team WHERE code = 'CFO');

-- Liens d'avancement (winner -> next_match, loser -> loser_next_match).
-- Attention : les round_label R1..R4 sont reutilises par le Swiss Stage
-- (voir V2/V5) -- ET le Swiss Stage est lui aussi en phase='PLAYOFFS' pour
-- LCP (choix delibere de V5 : pas de vraie saison reguliere). La seule
-- colonne qui distingue vraiment le bracket final est bracket_side.
UPDATE match SET
    next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026) AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R3'),
    next_match_slot = 1,
    loser_next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026) AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R2'),
    loser_next_match_slot = 1
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R1' AND team1_id = (SELECT id FROM team WHERE code = 'CFO') AND team2_id = (SELECT id FROM team WHERE code = 'MVK');

UPDATE match SET
    next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026) AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R3'),
    next_match_slot = 2,
    loser_next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026) AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R2'),
    loser_next_match_slot = 2
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R1' AND team1_id = (SELECT id FROM team WHERE code = 'TSW') AND team2_id = (SELECT id FROM team WHERE code = 'GAM');

UPDATE match SET
    next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026) AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R4'),
    next_match_slot = 2
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R2' AND team1_id = (SELECT id FROM team WHERE code = 'MVK') AND team2_id = (SELECT id FROM team WHERE code = 'GAM');

UPDATE match SET
    next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026) AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'F'),
    next_match_slot = 1,
    loser_next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026) AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R4'),
    loser_next_match_slot = 1
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R3' AND team1_id = (SELECT id FROM team WHERE code = 'CFO') AND team2_id = (SELECT id FROM team WHERE code = 'TSW');

UPDATE match SET
    next_match_id = (SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026) AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'F'),
    next_match_slot = 2
WHERE competition_id = (SELECT id FROM competition WHERE code = 'LCP' AND season = 2026)
  AND bracket_side IN ('UPPER', 'LOWER', 'GRAND_FINAL') AND round_label = 'R4' AND team1_id = (SELECT id FROM team WHERE code = 'CFO') AND team2_id = (SELECT id FROM team WHERE code = 'MVK');
