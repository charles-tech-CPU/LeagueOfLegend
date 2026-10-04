-- Un joueur peut desormais etre sans equipe (agent libre). Supprimer une equipe
-- ne supprime plus ses joueurs : ils deviennent sans equipe.

ALTER TABLE player ALTER COLUMN team_id DROP NOT NULL;

ALTER TABLE player DROP CONSTRAINT player_team_id_fkey;
ALTER TABLE player ADD CONSTRAINT player_team_id_fkey
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE SET NULL;
