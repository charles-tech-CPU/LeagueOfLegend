-- Historique des equipes d'un joueur : un "passage" (stint) = une periode dans une equipe.
-- start_date nul = date d'arrivee inconnue ; end_date nul = passage en cours (equipe actuelle).
-- player.team_id reste l'equipe actuelle et correspond toujours au passage en cours.
-- Les resultats d'un passage ne sont PAS stockes : ils sont deduits des matchs de l'equipe
-- joues entre start_date et end_date (voir PlayerService).

CREATE TABLE player_stint (
    id          BIGSERIAL PRIMARY KEY,
    player_id   BIGINT NOT NULL REFERENCES player(id) ON DELETE CASCADE,
    team_id     BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    start_date  DATE,
    end_date    DATE,
    CONSTRAINT chk_player_stint_dates CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_player_stint_player ON player_stint(player_id);
CREATE INDEX idx_player_stint_team ON player_stint(team_id);

-- Au plus un passage en cours par joueur.
CREATE UNIQUE INDEX uq_player_stint_current ON player_stint(player_id) WHERE end_date IS NULL;

-- Les joueurs deja rattaches a une equipe recoivent leur passage en cours (arrivee inconnue).
INSERT INTO player_stint (player_id, team_id)
SELECT id, team_id FROM player WHERE team_id IS NOT NULL;
