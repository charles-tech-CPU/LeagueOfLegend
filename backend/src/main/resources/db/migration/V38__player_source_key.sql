-- Lien d'un joueur avec sa page Leaguepedia (ex: 'leaguepedia:Faker'), pose par l'import du
-- detail des matchs (import/import_games.py) : il retrouve le meme joueur d'une saison a
-- l'autre malgre les homonymes et les changements de pseudo. Nul pour un joueur saisi a la
-- main et pas encore relie.

ALTER TABLE player ADD COLUMN source_key VARCHAR(255);
ALTER TABLE player ADD CONSTRAINT uq_player_source_key UNIQUE (source_key);
