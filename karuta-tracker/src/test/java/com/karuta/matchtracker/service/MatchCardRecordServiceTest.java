package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.CardPlacementDto;
import com.karuta.matchtracker.dto.MatchCardRecordDto;
import com.karuta.matchtracker.dto.OtetsukiDetailDto;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchCardPlacement;
import com.karuta.matchtracker.entity.MatchOtetsukiDetail;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.MatchCardPlacementRepository;
import com.karuta.matchtracker.repository.MatchOtetsukiDetailRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchCardRecordService 単体テスト")
class MatchCardRecordServiceTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchCardPlacementRepository placementRepository;
    @Mock
    private MatchOtetsukiDetailRepository otetsukiRepository;

    @InjectMocks
    private MatchCardRecordService service;

    private static final Long MATCH_ID = 10L;
    private static final Long P1 = 1L;
    private static final Long P2 = 2L;

    private Match match;

    @BeforeEach
    void setUp() {
        match = Match.builder().id(MATCH_ID).player1Id(P1).player2Id(P2).build();
    }

    private CardPlacementDto placement(int cardNo) {
        return CardPlacementDto.builder()
                .cardNo(cardNo).takenBy("SELF").field("OWN").side("LEFT").tier("TOP").build();
    }

    @Test
    @DisplayName("試合が存在しないと ResourceNotFoundException")
    void save_matchNotFound_throws() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveRecord(MATCH_ID, P1, new MatchCardRecordDto()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("参加者でないプレイヤーは ForbiddenException")
    void save_notParticipant_throws() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        assertThatThrownBy(() -> service.saveRecord(MATCH_ID, 99L, new MatchCardRecordDto()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("全置換: 既存を削除してから有効な配置・お手付きを保存")
    void save_replacesAndPersists() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(placementRepository.findByMatchIdAndPlayerId(MATCH_ID, P1)).thenReturn(List.of());
        when(otetsukiRepository.findByMatchIdAndPlayerIdOrderBySeqAsc(MATCH_ID, P1)).thenReturn(List.of());

        MatchCardRecordDto req = MatchCardRecordDto.builder()
                .cardPlacements(List.of(placement(17)))
                .otetsukiDetails(List.of(OtetsukiDetailDto.builder().type("HIKKAKE").hikkakeTarget("OWN_LEFT_TOP").build()))
                .build();

        service.saveRecord(MATCH_ID, P1, req);

        verify(placementRepository).deleteByMatchIdAndPlayerId(MATCH_ID, P1);
        verify(otetsukiRepository).deleteByMatchIdAndPlayerId(MATCH_ID, P1);
        verify(placementRepository, times(1)).save(any(MatchCardPlacement.class));
        verify(otetsukiRepository, times(1)).save(any(MatchOtetsukiDetail.class));
    }

    @Test
    @DisplayName("不完全な配置（欠損）・種類未選択のお手付きはスキップ")
    void save_skipsIncomplete() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(placementRepository.findByMatchIdAndPlayerId(MATCH_ID, P1)).thenReturn(List.of());
        when(otetsukiRepository.findByMatchIdAndPlayerIdOrderBySeqAsc(MATCH_ID, P1)).thenReturn(List.of());

        CardPlacementDto incomplete = CardPlacementDto.builder().cardNo(5).takenBy("SELF").build(); // field/side/tier欠損
        MatchCardRecordDto req = MatchCardRecordDto.builder()
                .cardPlacements(List.of(incomplete))
                .otetsukiDetails(List.of(new OtetsukiDetailDto())) // type=null
                .build();

        service.saveRecord(MATCH_ID, P1, req);

        verify(placementRepository, never()).save(any(MatchCardPlacement.class));
        verify(otetsukiRepository, never()).save(any(MatchOtetsukiDetail.class));
    }

    @Test
    @DisplayName("null リストでも例外なく全置換（クリア）できる")
    void save_nullLists_clears() {
        when(matchRepository.findById(MATCH_ID)).thenReturn(Optional.of(match));
        when(placementRepository.findByMatchIdAndPlayerId(MATCH_ID, P1)).thenReturn(List.of());
        when(otetsukiRepository.findByMatchIdAndPlayerIdOrderBySeqAsc(MATCH_ID, P1)).thenReturn(List.of());

        MatchCardRecordDto result = service.saveRecord(MATCH_ID, P1, new MatchCardRecordDto());

        verify(placementRepository).deleteByMatchIdAndPlayerId(eq(MATCH_ID), eq(P1));
        assertThat(result.getCardPlacements()).isEmpty();
        assertThat(result.getOtetsukiDetails()).isEmpty();
    }
}
