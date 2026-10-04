-- Poste occupe pendant un passage : un role swap (ex: MID -> ADC en cours de saison)
-- termine le passage et en ouvre un nouveau dans la meme equipe avec l'autre poste.
-- player.position reste le poste actuel (celui du passage en cours).

ALTER TABLE player_stint ADD COLUMN position VARCHAR(10);

UPDATE player_stint s SET position = p.position FROM player p WHERE p.id = s.player_id;

ALTER TABLE player_stint ALTER COLUMN position SET NOT NULL;
