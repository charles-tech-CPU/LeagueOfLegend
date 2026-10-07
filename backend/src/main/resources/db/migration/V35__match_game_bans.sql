-- Champions bannis par equipe sur une manche, saisis au cas par cas. Nombre libre
-- (generalement 5 par equipe, mais rien n'impose cette limite). Un match sans ban
-- saisi n'a simplement aucune ligne ici : rien ne change pour lui.

CREATE TABLE match_game_ban (
    id        BIGSERIAL PRIMARY KEY,
    game_id   BIGINT      NOT NULL REFERENCES match_game(id) ON DELETE CASCADE,
    team_id   BIGINT      NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    champion  VARCHAR(30) NOT NULL,
    -- Un champion ne peut etre banni deux fois par la meme equipe sur une manche.
    CONSTRAINT uq_match_game_ban UNIQUE (game_id, team_id, champion)
);

CREATE INDEX idx_match_game_ban_game ON match_game_ban(game_id);
