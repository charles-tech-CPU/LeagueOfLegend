package com.charles.lolresults.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.charles.lolresults.domain.Position;
import com.charles.lolresults.dto.PlayerCreateDto;
import com.charles.lolresults.dto.PlayerDto;
import com.charles.lolresults.dto.PlayerStintCreateDto;
import com.charles.lolresults.dto.PlayerStintDto;
import com.charles.lolresults.dto.PlayerTransferDto;
import com.charles.lolresults.service.PlayerService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Le controleur ne fait que deleguer : la logique est testee dans PlayerServiceTest. */
@ExtendWith(MockitoExtension.class)
class PlayerControllerTest {

    private static final PlayerDto CAPS = new PlayerDto(5L, 1L, "G2", "G2 Esports", "Caps", "DK", Position.MID);
    private static final PlayerStintDto STINT = new PlayerStintDto(
            10L, 1L, "G2", "G2 Esports", false, null, null, true, 0, 0, 0, 0, 0, List.of(), List.of());

    @Mock
    private PlayerService playerService;

    @InjectMocks
    private PlayerController playerController;

    @Test
    void lesLecturesDeleguentAuService() {
        when(playerService.findAll()).thenReturn(List.of(CAPS));
        when(playerService.findOne(5L)).thenReturn(CAPS);
        when(playerService.findByTeam(1L)).thenReturn(List.of(CAPS));
        when(playerService.findStints(5L)).thenReturn(List.of(STINT));

        assertThat(playerController.findAll()).containsExactly(CAPS);
        assertThat(playerController.findOne(5L)).isEqualTo(CAPS);
        assertThat(playerController.findByTeam(1L)).containsExactly(CAPS);
        assertThat(playerController.findStints(5L)).containsExactly(STINT);
    }

    @Test
    void lesEcrituresSurLeJoueurDeleguentAuService() {
        PlayerCreateDto dto = new PlayerCreateDto("Caps", "DK", Position.MID);
        PlayerTransferDto transfer = new PlayerTransferDto(2L, LocalDate.of(2026, 1, 1));
        when(playerService.create(1L, dto)).thenReturn(CAPS);
        when(playerService.update(5L, dto)).thenReturn(CAPS);
        when(playerService.transfer(5L, transfer)).thenReturn(CAPS);

        assertThat(playerController.create(1L, dto)).isEqualTo(CAPS);
        assertThat(playerController.update(5L, dto)).isEqualTo(CAPS);
        assertThat(playerController.transfer(5L, transfer)).isEqualTo(CAPS);
        playerController.delete(5L);
        verify(playerService).delete(5L);
    }

    @Test
    void lesEcrituresSurLHistoriqueDeleguentAuService() {
        PlayerStintCreateDto dto = new PlayerStintCreateDto(1L, null, LocalDate.of(2025, 1, 1));
        when(playerService.addStint(5L, dto)).thenReturn(STINT);
        when(playerService.updateStint(10L, dto)).thenReturn(STINT);

        assertThat(playerController.addStint(5L, dto)).isEqualTo(STINT);
        assertThat(playerController.updateStint(10L, dto)).isEqualTo(STINT);
        playerController.deleteStint(10L);
        verify(playerService).deleteStint(10L);
    }
}
