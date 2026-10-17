-- Worlds 2026 : phase suisse (R1 a R5) creee a l'avance, sans equipes, pour
-- n'avoir plus qu'a renseigner equipes et scores une fois le tirage fait.
-- Calendrier Leaguepedia (2026 Season World Championship/Main Event), heures de Paris.
-- Ajoute aussi les phases (comme Worlds 2025) et les horaires manquants du tableau final.
--
-- Tolerant (la prod a des saisies manuelles) : ne fait rien si la competition
-- manque, ne cree la suisse que si aucun match suisse n'existe, ne cree les
-- phases que si la competition n'en a aucune, et ne remplit que les horaires vides.
DO $$
DECLARE
    comp_id   BIGINT;
    swiss_id  BIGINT;
    final_id  BIGINT;
    play_in_id BIGINT;
BEGIN
    SELECT id INTO comp_id FROM competition
     WHERE source_key = 'leaguepedia:2026 Season World Championship/Main Event'
        OR name = 'World Championship 2026'
     ORDER BY id LIMIT 1;
    IF comp_id IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM competition_stage WHERE competition_id = comp_id) THEN
        INSERT INTO competition_stage (competition_id, position, name, format, best_of, team_count, group_count, advancing)
        VALUES (comp_id, 1, 'Play-In', 'DOUBLE_ELIMINATION', 'BO5', 4, 1, 1)
        RETURNING id INTO play_in_id;
        INSERT INTO competition_stage (competition_id, position, name, format, best_of, team_count, group_count, advancing)
        VALUES (comp_id, 2, 'Phase suisse', 'SWISS', NULL, 16, 1, 8)
        RETURNING id INTO swiss_id;
        INSERT INTO competition_stage (competition_id, position, name, format, best_of, team_count, group_count, advancing)
        VALUES (comp_id, 3, 'Phase finale', 'SINGLE_ELIMINATION', 'BO5', 8, 1, NULL)
        RETURNING id INTO final_id;

        UPDATE match SET stage_id = play_in_id
         WHERE competition_id = comp_id AND stage_id IS NULL AND bracket_side = 'PLAY_IN';
        UPDATE match SET stage_id = final_id
         WHERE competition_id = comp_id AND stage_id IS NULL AND bracket_side IN ('BRACKET', 'GRAND_FINAL');
    ELSE
        SELECT id INTO swiss_id FROM competition_stage
         WHERE competition_id = comp_id AND format = 'SWISS' ORDER BY position LIMIT 1;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM match WHERE competition_id = comp_id AND bracket_side = 'SWISS_STAGE') THEN
        INSERT INTO match (competition_id, round_label, date, time, best_of, status, phase, bracket_side, stage_id)
        SELECT comp_id, s.round_label, s.date, s.time, s.best_of, 'SCHEDULED', 'PLAYOFFS', 'SWISS_STAGE', swiss_id
          FROM (VALUES
            ('R1', DATE '2026-10-23', TIME '19:00', 'BO1'),
            ('R1', DATE '2026-10-23', TIME '20:00', 'BO1'),
            ('R1', DATE '2026-10-23', TIME '21:00', 'BO1'),
            ('R1', DATE '2026-10-23', TIME '22:00', 'BO1'),
            ('R1', DATE '2026-10-23', TIME '23:00', 'BO1'),
            ('R1', DATE '2026-10-24', TIME '00:00', 'BO1'),
            ('R1', DATE '2026-10-24', TIME '01:00', 'BO1'),
            ('R1', DATE '2026-10-24', TIME '02:00', 'BO1'),
            ('R2', DATE '2026-10-24', TIME '19:00', 'BO1'),
            ('R2', DATE '2026-10-24', TIME '20:00', 'BO1'),
            ('R2', DATE '2026-10-24', TIME '21:00', 'BO1'),
            ('R2', DATE '2026-10-24', TIME '22:00', 'BO1'),
            ('R2', DATE '2026-10-24', TIME '23:00', 'BO1'),
            ('R2', DATE '2026-10-25', TIME '00:00', 'BO1'),
            ('R2', DATE '2026-10-25', TIME '01:00', 'BO1'),
            ('R2', DATE '2026-10-25', TIME '02:00', 'BO1'),
            ('R3', DATE '2026-10-25', TIME '18:00', 'BO1'),
            ('R3', DATE '2026-10-25', TIME '19:00', 'BO1'),
            ('R3', DATE '2026-10-25', TIME '20:00', 'BO3'),
            ('R3', DATE '2026-10-25', TIME '23:00', 'BO3'),
            ('R3', DATE '2026-10-26', TIME '18:00', 'BO1'),
            ('R3', DATE '2026-10-26', TIME '19:00', 'BO1'),
            ('R3', DATE '2026-10-26', TIME '20:00', 'BO3'),
            ('R3', DATE '2026-10-26', TIME '23:00', 'BO3'),
            ('R4', DATE '2026-10-28', TIME '21:00', 'BO3'),
            ('R4', DATE '2026-10-29', TIME '00:00', 'BO3'),
            ('R4', DATE '2026-10-29', TIME '21:00', 'BO3'),
            ('R4', DATE '2026-10-30', TIME '00:00', 'BO3'),
            ('R4', DATE '2026-10-30', TIME '21:00', 'BO3'),
            ('R4', DATE '2026-10-31', TIME '00:00', 'BO3'),
            ('R5', DATE '2026-10-31', TIME '18:00', 'BO3'),
            ('R5', DATE '2026-10-31', TIME '21:00', 'BO3'),
            ('R5', DATE '2026-11-01', TIME '00:00', 'BO3')
          ) AS s(round_label, date, time, best_of);
    END IF;

    -- Horaires manquants (heure de Paris), sans toucher a ceux deja saisis
    UPDATE match m SET time = h.time
      FROM (VALUES
        ('Play-In UB Final', DATE '2026-10-16', TIME '20:00'),
        ('Play-In LB Final', DATE '2026-10-17', TIME '20:00'),
        ('Play-In Final',    DATE '2026-10-18', TIME '20:00'),
        ('Quarterfinal 1',   DATE '2026-11-03', TIME '23:00'),
        ('Quarterfinal 2',   DATE '2026-11-04', TIME '23:00'),
        ('Quarterfinal 3',   DATE '2026-11-05', TIME '23:00'),
        ('Quarterfinal 4',   DATE '2026-11-06', TIME '23:00'),
        ('Semifinal 1',      DATE '2026-11-07', TIME '23:00'),
        ('Semifinal 2',      DATE '2026-11-08', TIME '23:00'),
        ('Grand Final',      DATE '2026-11-14', TIME '20:00')
      ) AS h(round_label, date, time)
     WHERE m.competition_id = comp_id AND m.round_label = h.round_label
       AND m.date = h.date AND m.time IS NULL;
END $$;
