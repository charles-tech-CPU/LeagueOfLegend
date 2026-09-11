-- Hibernate mappe les champs Integer Java sur le type SQL "integer" (Types#INTEGER),
-- pas "smallint" : on corrige le type introduit par erreur dans V3.
ALTER TABLE match ALTER COLUMN next_match_slot TYPE INTEGER;
ALTER TABLE match ALTER COLUMN loser_next_match_slot TYPE INTEGER;
