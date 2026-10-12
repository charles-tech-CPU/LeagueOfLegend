-- Un champion n'est unique que par equipe dans une manche : en blind pick (5e manche des Bo5
-- en Coree jusqu'en 2015, Mid-Season Cup 2020...), les deux equipes peuvent jouer le meme.

ALTER TABLE match_game_player DROP CONSTRAINT uq_match_game_champion;
ALTER TABLE match_game_player ADD CONSTRAINT uq_match_game_team_champion UNIQUE (game_id, team_id, champion);
