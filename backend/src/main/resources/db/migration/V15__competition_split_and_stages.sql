-- Historique des tournois depuis 2011 : une competition est rangee par saison (annee)
-- et par split (WINTER / SPRING / SUMMER, nul pour un evenement ponctuel), et decrit
-- son format comme une suite de phases (poules, swiss, elimination simple/double...).

-- ---------------------------------------------------------------------------
-- Competition : split, dates et cle de la source d'import
-- ---------------------------------------------------------------------------
ALTER TABLE competition ADD COLUMN split VARCHAR(20);
ALTER TABLE competition ADD COLUMN start_date DATE;
ALTER TABLE competition ADD COLUMN end_date DATE;
-- Page d'origine (ex: "leaguepedia:EU LCS/Season 3/Spring Season") : rend l'import rejouable.
ALTER TABLE competition ADD COLUMN source_key VARCHAR(255);

ALTER TABLE competition ADD CONSTRAINT chk_competition_split
    CHECK (split IS NULL OR split IN ('WINTER', 'SPRING', 'SUMMER'));
ALTER TABLE competition ADD CONSTRAINT uq_competition_source_key UNIQUE (source_key);

-- Une ligue a plusieurs splits par saison (LCK 2013 Spring / Summer) et un meme
-- organisateur plusieurs evenements par an (IEM) : code + saison n'est plus unique.
ALTER TABLE competition DROP CONSTRAINT uq_competition_code_season;
ALTER TABLE competition ADD CONSTRAINT uq_competition_name UNIQUE (name);

-- Dates des competitions existantes, deduites de leurs matchs.
UPDATE competition c SET
    start_date = (SELECT MIN(m.date) FROM match m WHERE m.competition_id = c.id),
    end_date = (SELECT MAX(m.date) FROM match m WHERE m.competition_id = c.id);

-- Les ligues regionales 2026 deja saisies sont le split d'ete (matchs de juillet a octobre).
UPDATE competition SET split = 'SUMMER' WHERE type = 'REGIONAL_LEAGUE' AND season = 2026;

CREATE INDEX idx_competition_season ON competition(season);

-- ---------------------------------------------------------------------------
-- Phases d'une competition (format), dans l'ordre ou elles se jouent
-- ---------------------------------------------------------------------------
CREATE TABLE competition_stage (
    id              BIGSERIAL PRIMARY KEY,
    competition_id  BIGINT       NOT NULL REFERENCES competition(id) ON DELETE CASCADE,
    position        INT          NOT NULL,
    name            VARCHAR(100) NOT NULL,
    format          VARCHAR(30)  NOT NULL,
    -- Format des series de la phase ; nul si plusieurs formats (ex: Bo3 puis finale en Bo5).
    best_of         VARCHAR(10),
    team_count      INT,
    group_count     INT,
    -- Nombre d'equipes qualifiees pour la phase suivante (par groupe le cas echeant).
    advancing       INT,
    CONSTRAINT chk_stage_format CHECK (format IN (
        'ROUND_ROBIN', 'DOUBLE_ROUND_ROBIN', 'SWISS', 'SINGLE_ELIMINATION', 'DOUBLE_ELIMINATION', 'OTHER')),
    CONSTRAINT chk_stage_best_of CHECK (best_of IS NULL OR best_of IN ('BO1', 'BO2', 'BO3', 'BO5'))
);

CREATE INDEX idx_competition_stage_competition ON competition_stage(competition_id);

-- Le Bo2 existe en saison reguliere (LPL, LCK des annees 2010) : il faut l'accepter cote match.
-- (best_of n'a pas de contrainte CHECK sur match : seul l'enum Java BestOf evolue.)

-- Un match appartient a une phase (nul pour les matchs saisis avant les phases).
ALTER TABLE match ADD COLUMN stage_id BIGINT REFERENCES competition_stage(id) ON DELETE SET NULL;
CREATE INDEX idx_match_stage ON match(stage_id);
