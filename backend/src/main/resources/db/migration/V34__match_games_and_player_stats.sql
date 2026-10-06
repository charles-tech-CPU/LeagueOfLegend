-- Details facultatifs d'une serie, saisis au cas par cas : manches (games) avec le
-- champion et le K/D/A de chaque joueur, et MVP de chaque manche et de la serie.
-- Un match sans details n'a simplement aucune ligne ici : rien ne change pour lui.

-- MVP de la serie (facultatif).
ALTER TABLE match ADD COLUMN mvp_player_id BIGINT REFERENCES player(id) ON DELETE SET NULL;

-- Une manche d'une serie (Game 1 a 5).
CREATE TABLE match_game (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    game_number     INT    NOT NULL,
    -- Vainqueur de la manche, facultatif (une des deux equipes de la serie).
    winner_team_id  BIGINT REFERENCES team(id) ON DELETE SET NULL,
    -- "Player of the Game", facultatif.
    mvp_player_id   BIGINT REFERENCES player(id) ON DELETE SET NULL,
    CONSTRAINT chk_match_game_number CHECK (game_number BETWEEN 1 AND 5),
    CONSTRAINT uq_match_game UNIQUE (match_id, game_number)
);

CREATE INDEX idx_match_game_match ON match_game(match_id);

-- Ligne de stats d'un joueur sur une manche. L'equipe et le poste sont ceux de la
-- manche (le joueur a pu changer d'equipe ou de poste depuis).
CREATE TABLE match_game_player (
    id          BIGSERIAL PRIMARY KEY,
    game_id     BIGINT      NOT NULL REFERENCES match_game(id) ON DELETE CASCADE,
    player_id   BIGINT      NOT NULL REFERENCES player(id) ON DELETE CASCADE,
    team_id     BIGINT      NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    position    VARCHAR(10) NOT NULL,
    champion    VARCHAR(30) NOT NULL,
    -- K/D/A facultatifs : le champion peut etre connu sans le score du joueur.
    kills       INT,
    deaths      INT,
    assists     INT,
    CONSTRAINT chk_match_game_player_position CHECK (position IN ('TOP', 'JGL', 'MID', 'ADC', 'SUPP')),
    CONSTRAINT chk_match_game_player_kda CHECK (
        (kills IS NULL OR kills >= 0) AND (deaths IS NULL OR deaths >= 0) AND (assists IS NULL OR assists >= 0)),
    CONSTRAINT uq_match_game_player UNIQUE (game_id, player_id),
    -- Un champion ne peut etre choisi qu'une fois par manche (draft).
    CONSTRAINT uq_match_game_champion UNIQUE (game_id, champion)
);

CREATE INDEX idx_match_game_player_game ON match_game_player(game_id);
CREATE INDEX idx_match_game_player_player ON match_game_player(player_id);
