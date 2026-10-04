-- Effectif actuel des equipes : un joueur appartient a une equipe et occupe un
-- seul poste. Pas d'historique de transferts ni de staff en v1.
-- Plusieurs joueurs peuvent partager un poste (remplacants).

CREATE TABLE player (
    id          BIGSERIAL PRIMARY KEY,
    team_id     BIGINT       NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    pseudo      VARCHAR(50)  NOT NULL,
    nationality VARCHAR(50),
    position    VARCHAR(10)  NOT NULL,
    CONSTRAINT chk_player_position CHECK (position IN ('TOP', 'JGL', 'MID', 'ADC', 'SUPP'))
);

CREATE INDEX idx_player_team ON player(team_id);
