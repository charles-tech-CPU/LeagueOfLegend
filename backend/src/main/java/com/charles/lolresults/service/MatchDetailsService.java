package com.charles.lolresults.service;

import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchGame;
import com.charles.lolresults.domain.MatchGameBan;
import com.charles.lolresults.domain.MatchGamePlayer;
import com.charles.lolresults.domain.Player;
import com.charles.lolresults.domain.Position;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.MatchDetailsDto;
import com.charles.lolresults.dto.MatchDetailsUpdateDto;
import com.charles.lolresults.repository.MatchGameRepository;
import com.charles.lolresults.repository.MatchRepository;
import com.charles.lolresults.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Details facultatifs d'une serie (manches, champions, K/D/A, MVP), saisis au cas par cas.
 * Un match sans details n'a aucune manche : le reste de l'appli (classements, brackets)
 * ne s'appuie que sur le score de la serie et ignore ces donnees.
 */
@Service
@Transactional
public class MatchDetailsService {

    private static final int TEAM_SIZE = 5;

    private final MatchRepository matchRepository;
    private final MatchGameRepository gameRepository;
    private final PlayerRepository playerRepository;

    public MatchDetailsService(
            MatchRepository matchRepository, MatchGameRepository gameRepository, PlayerRepository playerRepository) {
        this.matchRepository = matchRepository;
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
    }

    @Transactional(readOnly = true)
    public MatchDetailsDto find(Long matchId) {
        return MatchDetailsDto.from(findMatch(matchId), gameRepository.findByMatchIdOrderByGameNumber(matchId));
    }

    /** Remplace tous les details du match. Les manches entierement vides sont ignorees. */
    public MatchDetailsDto replace(Long matchId, MatchDetailsUpdateDto dto) {
        Match match = findMatch(matchId);
        List<MatchDetailsUpdateDto.Game> games = dto.games() == null
                ? List.of()
                : dto.games().stream().filter(MatchDetailsService::isFilled).toList();
        boolean hasDetails = !games.isEmpty() || dto.mvpPlayerId() != null;
        if (hasDetails && (match.getTeam1() == null || match.getTeam2() == null)) {
            throw new IllegalArgumentException(
                    "Les deux equipes du match doivent etre connues pour saisir ses details");
        }

        Set<Integer> numbers = new HashSet<>();
        Set<Long> playersOfSeries = new HashSet<>();
        List<MatchGame> built = new ArrayList<>();
        for (MatchDetailsUpdateDto.Game g : games) {
            if (g.gameNumber() > match.getBestOf().getMaxGames()) {
                throw new IllegalArgumentException(
                        "Game " + g.gameNumber() + " impossible dans un " + match.getBestOf());
            }
            if (!numbers.add(g.gameNumber())) {
                throw new IllegalArgumentException("Game " + g.gameNumber() + " saisie deux fois");
            }
            MatchGame game = buildGame(match, g);
            game.getPlayers()
                    .forEach(line -> playersOfSeries.add(line.getPlayer().getId()));
            built.add(game);
        }

        Player seriesMvp = findPlayerOrNull(dto.mvpPlayerId());
        if (seriesMvp != null && !playersOfSeries.isEmpty() && !playersOfSeries.contains(seriesMvp.getId())) {
            throw new IllegalArgumentException("Le MVP de la serie doit avoir joue une des manches saisies");
        }
        match.setMvp(seriesMvp);

        // Anciennes manches supprimees avant l'insertion : le numero de manche est unique par match.
        gameRepository.deleteAll(gameRepository.findByMatchIdOrderByGameNumber(matchId));
        gameRepository.flush();
        List<MatchGame> saved = gameRepository.saveAll(built).stream()
                .sorted(Comparator.comparing(MatchGame::getGameNumber))
                .toList();
        matchRepository.save(match);
        return MatchDetailsDto.from(match, saved);
    }

    private MatchGame buildGame(Match match, MatchDetailsUpdateDto.Game g) {
        String label = "Game " + g.gameNumber() + " : ";
        MatchGame game = new MatchGame(match, g.gameNumber());
        if (g.winnerTeamId() != null) {
            game.setWinner(teamOfMatch(match, g.winnerTeamId(), label));
        }

        Set<Long> players = new HashSet<>();
        Set<String> champions = new HashSet<>();
        Map<Long, Set<Position>> positionsByTeam = new HashMap<>();
        List<MatchDetailsUpdateDto.Line> lines = g.players() == null ? List.of() : g.players();
        for (MatchDetailsUpdateDto.Line l : lines) {
            Team team = teamOfMatch(match, l.teamId(), label);
            String champion = l.champion().trim();
            Set<Position> positions = positionsByTeam.computeIfAbsent(team.getId(), id -> new HashSet<>());
            if (!players.add(l.playerId())) {
                throw new IllegalArgumentException(label + "un joueur est saisi deux fois");
            }
            if (!champions.add(champion.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(label + champion + " est choisi deux fois");
            }
            if (!positions.add(l.position())) {
                throw new IllegalArgumentException(
                        label + "deux joueurs de " + team.getCode() + " au poste " + l.position());
            }
            if (positions.size() > TEAM_SIZE) {
                throw new IllegalArgumentException(label + "plus de " + TEAM_SIZE + " joueurs pour " + team.getCode());
            }
            MatchGamePlayer line = new MatchGamePlayer();
            line.setGame(game);
            line.setPlayer(findPlayer(l.playerId()));
            line.setTeam(team);
            line.setPosition(l.position());
            line.setChampion(champion);
            line.setKills(l.kills());
            line.setDeaths(l.deaths());
            line.setAssists(l.assists());
            game.getPlayers().add(line);
        }

        Player mvp = findPlayerOrNull(g.mvpPlayerId());
        if (mvp != null && !players.isEmpty() && !players.contains(mvp.getId())) {
            throw new IllegalArgumentException(label + "le MVP doit faire partie des joueurs de la manche");
        }
        game.setMvp(mvp);

        Set<String> bannedByTeam = new HashSet<>();
        List<MatchDetailsUpdateDto.Ban> bansDto = g.bans() == null ? List.of() : g.bans();
        for (MatchDetailsUpdateDto.Ban b : bansDto) {
            Team team = teamOfMatch(match, b.teamId(), label);
            String champion = b.champion().trim();
            if (!bannedByTeam.add(team.getId() + ":" + champion.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(label + champion + " est banni deux fois par " + team.getCode());
            }
            MatchGameBan ban = new MatchGameBan();
            ban.setGame(game);
            ban.setTeam(team);
            ban.setChampion(champion);
            game.getBans().add(ban);
        }
        return game;
    }

    private static boolean isFilled(MatchDetailsUpdateDto.Game g) {
        return g.winnerTeamId() != null
                || g.mvpPlayerId() != null
                || (g.players() != null && !g.players().isEmpty())
                || (g.bans() != null && !g.bans().isEmpty());
    }

    private static Team teamOfMatch(Match match, Long teamId, String label) {
        if (Objects.equals(teamId, match.getTeam1().getId())) {
            return match.getTeam1();
        }
        if (Objects.equals(teamId, match.getTeam2().getId())) {
            return match.getTeam2();
        }
        throw new IllegalArgumentException(label + "l'equipe " + teamId + " ne joue pas ce match");
    }

    private Match findMatch(Long id) {
        return matchRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Match introuvable : " + id));
    }

    private Player findPlayer(Long id) {
        return playerRepository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Joueur introuvable : " + id));
    }

    private Player findPlayerOrNull(Long id) {
        return id == null ? null : findPlayer(id);
    }
}
