-- Competitions de l'ete 2026 saisies a la main (import Excel, V11) : reliees a leur page
-- Leaguepedia pour que import/import_games.py en importe le detail des matchs.
UPDATE competition c SET source_key = 'leaguepedia:' || k.page
FROM (VALUES
    ('CBLOL 2026', 'CBLOL/2026 Season/Split 2'),
    ('LCK 2026', 'LCK/2026 Season/Rounds 3-4'),
    ('LCP 2026', 'LCP/2026 Season/Split 3'),
    ('LCS 2026', 'LCS/2026 Season/Summer Season'),
    ('LEC 2026', 'LEC/2026 Season/Summer Season'),
    ('LPL 2026', 'LPL/2026 Season/Split 3'),
    ('World Championship 2026', '2026 Season World Championship/Main Event'),
    ('2026 Demacia Cup Global Invitational', '2026 Demacia Cup Global Invitational'),
    ('2026 World Star Challengers Invitational', '2026 World Star Challengers Invitational'),
    ('EMEA Masters 2026 Summer LCQ', 'EMEA Masters/2026 Season/Summer LCQ'),
    ('EMEA Masters 2026 Summer Main Event', 'EMEA Masters/2026 Season/Summer Main Event')
) AS k(name, page)
WHERE c.name = k.name AND c.season = 2026 AND c.source_key IS NULL;
