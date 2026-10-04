-- Historique des noms d'une equipe (ex: SK Telecom T1 K -> SK Telecom T1 -> T1).
-- team.name reste le nom actuel ; chaque ligne est un nom porte sur une periode.
-- valid_from nul = depuis l'origine connue, valid_to nul = nom actuel.

CREATE TABLE team_name_history (
    id          BIGSERIAL PRIMARY KEY,
    team_id     BIGINT       NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    short_name  VARCHAR(20),
    valid_from  DATE,
    valid_to    DATE,
    CONSTRAINT uq_team_name_history UNIQUE (team_id, name)
);

CREATE INDEX idx_team_name_history_team ON team_name_history(team_id);
