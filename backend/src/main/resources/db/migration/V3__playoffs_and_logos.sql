-- Phase de la saison (saison reguliere / playoffs) : le classement ne doit
-- compter que les matchs de saison reguliere. Les playoffs (round_label hors
-- pattern "W<n>" : R1..R5, QF, SF, F, S, KR...) font avancer les equipes dans
-- un bracket au lieu d'alimenter un tableau victoires/defaites.
ALTER TABLE match ADD COLUMN phase VARCHAR(20) NOT NULL DEFAULT 'REGULAR_SEASON';
UPDATE match SET phase = 'PLAYOFFS' WHERE round_label !~ '^W[0-9]+$';

-- Cote du bracket (UPPER/LOWER/PLAY_IN/GROUP...), libre, pertinent seulement
-- pour les matchs de phase PLAYOFFS. Sert a regrouper/ordonner l'affichage.
ALTER TABLE match ADD COLUMN bracket_side VARCHAR(30);

-- Un match de playoffs peut exister avant que ses deux equipes soient connues
-- (elles dependent du resultat d'un match precedent dans le bracket).
ALTER TABLE match ALTER COLUMN team1_id DROP NOT NULL;
ALTER TABLE match ALTER COLUMN team2_id DROP NOT NULL;

-- Pointeurs vers le match suivant dans le bracket : des qu'un match est
-- complete, le vainqueur (et pour un bracket a double elimination, le
-- perdant) y sont automatiquement inseres (voir MatchService).
ALTER TABLE match ADD COLUMN next_match_id BIGINT REFERENCES match(id) ON DELETE SET NULL;
ALTER TABLE match ADD COLUMN next_match_slot SMALLINT CHECK (next_match_slot IN (1, 2));
ALTER TABLE match ADD COLUMN loser_next_match_id BIGINT REFERENCES match(id) ON DELETE SET NULL;
ALTER TABLE match ADD COLUMN loser_next_match_slot SMALLINT CHECK (loser_next_match_slot IN (1, 2));

CREATE INDEX idx_match_next_match ON match(next_match_id);
CREATE INDEX idx_match_loser_next_match ON match(loser_next_match_id);
CREATE INDEX idx_match_phase ON match(phase);

-- Logo d'equipe (image binaire), servi par GET /api/teams/{id}/logo.
ALTER TABLE team ADD COLUMN logo BYTEA;
ALTER TABLE team ADD COLUMN logo_content_type VARCHAR(50);
