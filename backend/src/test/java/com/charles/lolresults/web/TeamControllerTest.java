package com.charles.lolresults.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.TeamCreateDto;
import com.charles.lolresults.dto.TeamDto;
import com.charles.lolresults.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private TeamController teamController;

    @Test
    void findAllTrieParNom() {
        when(teamRepository.findAll())
                .thenReturn(List.of(new Team("KC", "Karmine Corp", "EMEA"), new Team("FNC", "Fnatic", "EMEA")));

        assertThat(teamController.findAll()).extracting(TeamDto::code).containsExactly("FNC", "KC");
    }

    @Test
    void createEnregistreLEquipe() {
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamDto created = teamController.create(new TeamCreateDto("G2", "G2 Esports", "EMEA"));

        assertThat(created.name()).isEqualTo("G2 Esports");
    }

    @Test
    void deleteSupprimeLEquipe() {
        teamController.delete(1L);

        verify(teamRepository).deleteById(1L);
    }

    @Test
    void unLogoSansTypeEstServiEnBinaireGenerique() {
        Team team = team();
        team.setLogo(new byte[] {1});
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));

        assertThat(teamController.logo(1L).getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void findOneRenvoieLEquipe() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team()));

        assertThat(teamController.findOne(1L).code()).isEqualTo("G2");
    }

    @Test
    void uneEquipeInconnueLeveUneErreurPartout() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());
        TeamCreateDto dto = new TeamCreateDto("G2", "G2 Esports", "EMEA");
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> teamController.findOne(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Equipe introuvable : 99");
        assertThatThrownBy(() -> teamController.update(99L, dto)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> teamController.logo(99L)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> teamController.uploadLogo(99L, file)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateModifieLEquipe() {
        Team team = team();
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> invocation.getArgument(0));

        teamController.update(1L, new TeamCreateDto("G2", "G2 Esports", "EMEA"));

        assertThat(team.getName()).isEqualTo("G2 Esports");
        assertThat(team.getRegion()).isEqualTo("EMEA");
    }

    @Test
    void logoRenvoie404SansLogoEtLImageSinon() throws Exception {
        Team team = team();
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));

        assertThat(teamController.logo(1L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        teamController.uploadLogo(1L, new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1, 2}));
        verify(teamRepository).save(team);

        assertThat(teamController.logo(1L).getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(teamController.logo(1L).getBody()).containsExactly(1, 2);
    }

    private static Team team() {
        Team team = new Team("G2", "G2", null);
        team.setId(1L);
        return team;
    }
}
