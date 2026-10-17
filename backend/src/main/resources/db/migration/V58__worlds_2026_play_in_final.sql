-- Worlds 2026 : il manque la finale du play-in. Le vainqueur de la finale du
-- winner bracket (KC) doit y retrouver le vainqueur de la finale du loser
-- bracket (MVK ou FURIA, selon le resultat de "Play-In LB Final").
--
-- Le bracket a ete saisi a la main (ids differents selon la base) : on cible
-- les matchs par competition + libelle de round. Tolerant : ne fait rien si
-- la competition ou les finales manquent, ou si le match existe deja.
DO $$
DECLARE
    comp_id   BIGINT;
    ub_final  match%ROWTYPE;
    lb_final  match%ROWTYPE;
    new_id    BIGINT;
BEGIN
    SELECT id INTO comp_id FROM competition
     WHERE source_key = 'leaguepedia:2026 Season World Championship/Main Event'
        OR name = 'World Championship 2026'
     ORDER BY id LIMIT 1;
    IF comp_id IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM match WHERE competition_id = comp_id AND round_label = 'Play-In Final') THEN
        RETURN;
    END IF;

    SELECT * INTO ub_final FROM match
     WHERE competition_id = comp_id AND round_label = 'Play-In UB Final' ORDER BY id LIMIT 1;
    SELECT * INTO lb_final FROM match
     WHERE competition_id = comp_id AND round_label = 'Play-In LB Final' ORDER BY id LIMIT 1;
    IF ub_final.id IS NULL OR lb_final.id IS NULL THEN
        RETURN;
    END IF;

    -- Equipes deja connues si les finales ont ete jouees (meme regle que
    -- MatchService.propagateAdvancement : le vainqueur avance).
    INSERT INTO match (competition_id, round_label, date, best_of, team1_id, team2_id,
                       status, phase, bracket_side, stage_id)
    VALUES (comp_id, 'Play-In Final', GREATEST(lb_final.date + 1, DATE '2026-10-18'), 'BO5',
            CASE WHEN ub_final.status = 'COMPLETED'
                 THEN CASE WHEN ub_final.score1 > ub_final.score2 THEN ub_final.team1_id ELSE ub_final.team2_id END END,
            CASE WHEN lb_final.status = 'COMPLETED'
                 THEN CASE WHEN lb_final.score1 > lb_final.score2 THEN lb_final.team1_id ELSE lb_final.team2_id END END,
            'SCHEDULED', 'PLAYOFFS', 'PLAY_IN', ub_final.stage_id)
    RETURNING id INTO new_id;

    -- On ne remplace pas un lien saisi a la main entre-temps
    UPDATE match SET next_match_id = new_id, next_match_slot = 1
     WHERE id = ub_final.id AND next_match_id IS NULL;
    UPDATE match SET next_match_id = new_id, next_match_slot = 2
     WHERE id = lb_final.id AND next_match_id IS NULL;
END $$;
