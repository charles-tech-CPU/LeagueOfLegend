-- Schema initial : equipes, competitions, sous-groupes, matchs.
-- Les classements et le tete-a-tete ne sont volontairement PAS des tables :
-- ils sont recalcules a la volee (voir StandingsService).

CREATE TABLE team (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    region      VARCHAR(50)
);

CREATE TABLE competition (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    region      VARCHAR(50),
    season      INT          NOT NULL,
    CONSTRAINT uq_competition_code_season UNIQUE (code, season)
);

CREATE TABLE competition_group (
    id              BIGSERIAL PRIMARY KEY,
    competition_id  BIGINT      NOT NULL REFERENCES competition(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL
);

CREATE TABLE match (
    id                  BIGSERIAL PRIMARY KEY,
    competition_id      BIGINT      NOT NULL REFERENCES competition(id) ON DELETE CASCADE,
    competition_group_id BIGINT     REFERENCES competition_group(id) ON DELETE SET NULL,
    round_label         VARCHAR(50) NOT NULL,
    date                DATE        NOT NULL,
    time                TIME,
    best_of             VARCHAR(10) NOT NULL,
    team1_id            BIGINT      NOT NULL REFERENCES team(id),
    team2_id            BIGINT      NOT NULL REFERENCES team(id),
    score1              INT,
    score2              INT,
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    CONSTRAINT chk_match_teams_distinct CHECK (team1_id <> team2_id),
    CONSTRAINT chk_match_scores_consistency CHECK (
        (score1 IS NULL AND score2 IS NULL) OR (score1 IS NOT NULL AND score2 IS NOT NULL)
    )
);

CREATE INDEX idx_match_competition ON match(competition_id);
CREATE INDEX idx_match_team1 ON match(team1_id);
CREATE INDEX idx_match_team2 ON match(team2_id);
CREATE INDEX idx_match_date ON match(date);
