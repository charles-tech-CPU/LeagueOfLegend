package com.charles.lolresults.web;

import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.TeamCreateDto;
import com.charles.lolresults.dto.TeamDto;
import com.charles.lolresults.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamRepository teamRepository;

    public TeamController(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @GetMapping
    public List<TeamDto> findAll() {
        return teamRepository.findAll().stream()
                .sorted(Comparator.comparing(Team::getName))
                .map(TeamDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public TeamDto findOne(@PathVariable Long id) {
        return teamRepository.findById(id).map(TeamDto::from)
                .orElseThrow(() -> new EntityNotFoundException("Equipe introuvable : " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamDto create(@Valid @RequestBody TeamCreateDto dto) {
        Team team = new Team(dto.code(), dto.name(), dto.region());
        return TeamDto.from(teamRepository.save(team));
    }

    @PutMapping("/{id}")
    public TeamDto update(@PathVariable Long id, @Valid @RequestBody TeamCreateDto dto) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Equipe introuvable : " + id));
        team.setCode(dto.code());
        team.setName(dto.name());
        team.setRegion(dto.region());
        return TeamDto.from(teamRepository.save(team));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        teamRepository.deleteById(id);
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Equipe introuvable : " + id));
        if (team.getLogo() == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType contentType = team.getLogoContentType() != null
                ? MediaType.parseMediaType(team.getLogoContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(contentType).body(team.getLogo());
    }
}
