-- Corrections/complements des playoffs 2026 a partir de Leaguepedia/Liquipedia
-- (recherches du 2026-09-11). N'inclut que les points confirmes avec un bon niveau
-- de confiance ; les points restes incertains sont documentes en commentaire et
-- volontairement PAS appliques (pas de bracket_side/next_match devine).

-- =====================================================================
-- LEC 2026 Summer Playoffs (code LEC) : 6 equipes, double elimination, Bo5.
-- Topologie reconstruite a partir des resultats deja en base (auto-coherente) :
--   Upper R1 : VIT vs G2 (G2 gagne), KC vs GX (KC gagne)
--   Upper Final ('R3') : G2 vs KC (G2 gagne) -> attend en Grand Final
--   Lower R1 ('R1' reutilise) : GX(perdant upper) vs NAVI, VIT(perdant upper) vs MKOI
-- Lower SF / Lower Final / Grand Final n'existaient pas encore : ajoutes en
-- placeholder. Dates ESTIMEES (fenetre officielle Berlin/Nice) faute de calendrier
-- detaille confirme : a corriger si tu as les vraies dates.
-- =====================================================================

UPDATE match SET bracket_side = 'UPPER'
WHERE competition_id = (SELECT id FROM competition WHERE code='LEC' AND season=2026)
  AND phase = 'PLAYOFFS' AND round_label IN ('R1', 'R3')
  AND date IN ('2026-09-05', '2026-09-06');

UPDATE match SET bracket_side = 'LOWER'
WHERE competition_id = (SELECT id FROM competition WHERE code='LEC' AND season=2026)
  AND phase = 'PLAYOFFS' AND round_label = 'R1'
  AND date IN ('2026-09-11', '2026-09-12');

INSERT INTO match (competition_id, round_label, date, best_of, team1_id, team2_id, phase, bracket_side)
VALUES ((SELECT id FROM competition WHERE code='LEC' AND season=2026), 'Lower SF', '2026-09-13', 'BO5', NULL, NULL, 'PLAYOFFS', 'LOWER');

INSERT INTO match (competition_id, round_label, date, best_of, team1_id, team2_id, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code='LEC' AND season=2026), 'Lower Final', '2026-09-19', 'BO5',
    (SELECT id FROM team WHERE code='KC'), NULL, 'PLAYOFFS', 'LOWER'
);

INSERT INTO match (competition_id, round_label, date, best_of, team1_id, team2_id, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code='LEC' AND season=2026), 'Grand Final', '2026-09-20', 'BO5',
    (SELECT id FROM team WHERE code='G2'), NULL, 'PLAYOFFS', 'GRAND_FINAL'
);

-- Liens d'avancement automatique (le vainqueur/perdant est insere par l'appli des
-- qu'un score est saisi sur le match source, voir MatchService.propagateAdvancement) :
UPDATE match SET next_match_id = (SELECT id FROM match WHERE competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026) AND round_label='Lower SF'), next_match_slot = 1
WHERE competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026) AND bracket_side='LOWER' AND round_label='R1'
  AND team1_id=(SELECT id FROM team WHERE code='GX');

UPDATE match SET next_match_id = (SELECT id FROM match WHERE competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026) AND round_label='Lower SF'), next_match_slot = 2
WHERE competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026) AND bracket_side='LOWER' AND round_label='R1'
  AND team1_id=(SELECT id FROM team WHERE code='VIT');

UPDATE match SET
    next_match_id = (SELECT id FROM match WHERE competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026) AND round_label='Grand Final'),
    loser_next_match_id = (SELECT id FROM match WHERE competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026) AND round_label='Lower Final'),
    next_match_slot = 1, loser_next_match_slot = 1
WHERE competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026) AND round_label='R3' AND bracket_side='UPPER';

UPDATE match SET next_match_id = (SELECT id FROM match WHERE competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026) AND round_label='Lower Final'), next_match_slot = 2
WHERE round_label = 'Lower SF' AND competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026);

UPDATE match SET next_match_id = (SELECT id FROM match WHERE competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026) AND round_label='Grand Final'), next_match_slot = 2
WHERE round_label = 'Lower Final' AND competition_id=(SELECT id FROM competition WHERE code='LEC' AND season=2026);


-- =====================================================================
-- LCK 2026 Season Playoffs (code LCK) : double elimination Bo5.
-- LCK a AUSSI un mecanisme de choix d'adversaire -> la topologie exacte des
-- rounds intermediaires n'est pas deductible de facon fiable : bracket_side
-- volontairement laisse NULL pour ces matchs (a verifier manuellement sur
-- https://lol.fandom.com/wiki/LCK/2026_Season/Season_Playoffs si besoin).
-- Seuls 2 matchs sont confirmes avec certitude : Upper Final (GEN bat HLE
-- 3-1) et Lower Final (HLE vs T1, a venir). Grand Final (GEN vs vainqueur du
-- Lower Final) confirmee pour le 2026-09-13, ajoutee en placeholder.
-- =====================================================================

UPDATE match SET bracket_side = 'UPPER'
WHERE competition_id = (SELECT id FROM competition WHERE code='LCK' AND season=2026)
  AND round_label = 'R4' AND team1_id=(SELECT id FROM team WHERE code='GEN') AND team2_id=(SELECT id FROM team WHERE code='HLE');

UPDATE match SET bracket_side = 'LOWER'
WHERE competition_id = (SELECT id FROM competition WHERE code='LCK' AND season=2026)
  AND round_label = 'R4' AND team1_id=(SELECT id FROM team WHERE code='HLE') AND team2_id=(SELECT id FROM team WHERE code='T1');

INSERT INTO match (competition_id, round_label, date, time, best_of, team1_id, team2_id, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code='LCK' AND season=2026), 'Grand Final', '2026-09-13', '10:00:00', 'BO5',
    (SELECT id FROM team WHERE code='GEN'), NULL, 'PLAYOFFS', 'GRAND_FINAL'
);

UPDATE match SET next_match_id = (SELECT id FROM match WHERE competition_id=(SELECT id FROM competition WHERE code='LCK' AND season=2026) AND round_label='Grand Final'), next_match_slot = 2
WHERE competition_id = (SELECT id FROM competition WHERE code='LCK' AND season=2026) AND bracket_side = 'LOWER';


-- =====================================================================
-- LPL 2026 Split 3 Playoffs (code LPL) : double elimination Bo5, choix
-- d'adversaire pour le mieux classe, play-in "Knights Rivals" (round_label
-- 'KR'). Topologie entierement reconstructible et coherente a partir des
-- resultats deja en base.
-- =====================================================================

-- IDs verifies directement contre la base le 2026-09-11 (GET /api/matches?competitionId=6).
UPDATE match SET bracket_side = 'PLAY_IN' WHERE id IN (216, 218);
UPDATE match SET bracket_side = 'UPPER'   WHERE id IN (223, 232, 243, 245, 261);
UPDATE match SET bracket_side = 'LOWER'   WHERE id IN (246, 248, 254, 256, 262, 265);

INSERT INTO match (competition_id, round_label, date, time, best_of, team1_id, team2_id, phase, bracket_side)
VALUES (
    (SELECT id FROM competition WHERE code='LPL' AND season=2026), 'Grand Final', '2026-09-13', '17:00:00', 'BO5',
    (SELECT id FROM team WHERE code='BLG'), NULL, 'PLAYOFFS', 'GRAND_FINAL'
);

UPDATE match SET next_match_id = (SELECT id FROM match WHERE competition_id=(SELECT id FROM competition WHERE code='LPL' AND season=2026) AND round_label='Grand Final'), next_match_slot = 2
WHERE id = 265;


-- =====================================================================
-- CBLOL 2026 Split 2 Playoffs (code CBLOL) : 6 equipes, double elimination,
-- Bo5. Seeds 1-2 (LOUD, LOS) : bye direct en Upper Bracket R2. Seeds 3-6
-- (VKS, FUR, RED, PAIN) : Upper Bracket R1. Deja coherent avec les 4 matchs
-- en base. Lower Bracket / Grand Finale pas encore programmes cote ligue
-- (template de seeding manuel, pas previsible) : non ajoutes, a completer
-- via l'appli une fois connus.
-- =====================================================================

UPDATE match SET bracket_side = 'UPPER'
WHERE competition_id = (SELECT id FROM competition WHERE code='CBLOL' AND season=2026) AND phase = 'PLAYOFFS';


-- =====================================================================
-- LCP 2026 Split 3 (code LCP) : PAS de saison reguliere classique. Format
-- reel = Swiss Stage (R1-R5, appariement dynamique, qualif/elimination a 3
-- victoires/defaites) + Seeding Match (S, fixe les seeds 1-4) + bracket
-- final (QF/SF/F). Recommandation confirmee par la recherche : NE PAS
-- reclassifier en REGULAR_SEASON, un classement vide est normal pour LCP
-- avec ce format (le Swiss Stage n'est pas un round-robin classique).
-- bracket_side ajoute a titre informatif pour distinguer les 3 etapes.
--
-- INCERTAIN : l'equipe GAM apparait dans DEUX matchs 'QF' differents (id du
-- match TSW vs GAM et MVK vs GAM), impossible en simple elimination directe.
-- Vient probablement d'une erreur d'etiquetage dans le fichier Excel source
-- (une des deux lignes n'est peut-etre pas vraiment un quart de finale).
-- A VERIFIER MANUELLEMENT sur https://lol.fandom.com/wiki/LCP/2026_Season/Split_3
-- avant de poser des liens next_match_id sur QF/SF/F (non fait ici).
-- =====================================================================

UPDATE match SET bracket_side = 'SWISS_STAGE' WHERE competition_id = (SELECT id FROM competition WHERE code='LCP' AND season=2026) AND round_label IN ('R1','R2','R3','R4','R5');
UPDATE match SET bracket_side = 'SEEDING'     WHERE competition_id = (SELECT id FROM competition WHERE code='LCP' AND season=2026) AND round_label = 'S';
UPDATE match SET bracket_side = 'BRACKET'     WHERE competition_id = (SELECT id FROM competition WHERE code='LCP' AND season=2026) AND round_label IN ('QF','SF','F');


-- =====================================================================
-- LCS / "LTA North" 2026 Split 2 Playoffs (code LCS) : double elimination
-- Bo5 confirmee (6 equipes : FlyQuest, Cloud9, Team Liquid, Shopify
-- Rebellion, 100 Thieves, Dignitas), mais la page de bracket exacte n'a pas
-- pu etre recuperee (confusion avec "Liga Regional Norte", ligue Tier 2
-- differente ; puis rate-limit). Les 2 matchs deja en base (C9 vs SR,
-- SEN vs FLY, tous deux 'R1') sont marques UPPER par deduction raisonnable
-- (coherent avec un Upper Bracket R1 a 4 equipes + 2 byes) mais A VERIFIER
-- sur https://lol.fandom.com/wiki/LTA_North avant d'ajouter la suite du bracket.
-- =====================================================================

UPDATE match SET bracket_side = 'UPPER'
WHERE competition_id = (SELECT id FROM competition WHERE code='LCS' AND season=2026) AND phase = 'PLAYOFFS';
