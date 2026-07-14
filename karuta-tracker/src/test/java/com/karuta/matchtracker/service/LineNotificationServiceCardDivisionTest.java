package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.LineNotificationPreferenceDto;
import com.karuta.matchtracker.entity.LineNotificationPreference;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.LineNotificationPreferenceRepository;
import com.karuta.matchtracker.repository.MentorRelationshipRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 札分けリマインダー（{@code CARD_DIVISION_REMINDER}）の preference に関する単体テスト。
 *
 * <ul>
 *   <li>AC-4: 団体スコープの購読判定 {@code isCardDivisionReminderEnabled} が
 *       「レコード無し＝false／false＝false／true＝true」を返す（デフォルト OFF）。</li>
 *   <li>AC-5: preference DTO の往復（{@code updatePreferences} で保存・{@code getPreferences} で取得）で
 *       新フィールドが per-(player, org) に保存・取得される。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LineNotificationService 札分けリマインダー preference")
class LineNotificationServiceCardDivisionTest {

    @Mock private LineChannelRepository lineChannelRepository;
    @Mock private LineChannelAssignmentRepository lineChannelAssignmentRepository;
    @Mock private LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    @Mock private LineMessageLogService lineMessageLogService;
    @Mock private LineMessagingService lineMessagingService;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private LotteryQueryService lotteryQueryService;
    @Mock private VenueRepository venueRepository;
    @Mock private MentorRelationshipRepository mentorRelationshipRepository;

    private LineNotificationService service;

    private static final Long PLAYER_ID = 42L;
    private static final Long ORG_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new LineNotificationService(
                lineChannelRepository,
                lineChannelAssignmentRepository,
                lineNotificationPreferenceRepository,
                lineMessageLogService,
                lineMessagingService,
                practiceSessionRepository,
                practiceParticipantRepository,
                playerOrganizationRepository,
                playerRepository,
                lotteryQueryService,
                venueRepository,
                mentorRelationshipRepository);
    }

    @Nested
    @DisplayName("isCardDivisionReminderEnabled（AC-4 デフォルト OFF）")
    class PerOrgSubscription {

        @Test
        @DisplayName("レコード無し＝false（デフォルト OFF）")
        void noRecordIsFalse() {
            when(lineNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(PLAYER_ID, ORG_ID))
                    .thenReturn(Optional.empty());

            assertThat(service.isCardDivisionReminderEnabled(PLAYER_ID, ORG_ID)).isFalse();
        }

        @Test
        @DisplayName("card_division_reminder=false＝false")
        void falseFlagIsFalse() {
            when(lineNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(PLAYER_ID, ORG_ID))
                    .thenReturn(Optional.of(pref(false)));

            assertThat(service.isCardDivisionReminderEnabled(PLAYER_ID, ORG_ID)).isFalse();
        }

        @Test
        @DisplayName("card_division_reminder=true＝true")
        void trueFlagIsTrue() {
            when(lineNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(PLAYER_ID, ORG_ID))
                    .thenReturn(Optional.of(pref(true)));

            assertThat(service.isCardDivisionReminderEnabled(PLAYER_ID, ORG_ID)).isTrue();
        }

        private LineNotificationPreference pref(boolean cardDivisionReminder) {
            return LineNotificationPreference.builder()
                    .playerId(PLAYER_ID)
                    .organizationId(ORG_ID)
                    .cardDivisionReminder(cardDivisionReminder)
                    .build();
        }
    }

    @Nested
    @DisplayName("preference DTO 往復（AC-5）")
    class DtoRoundTrip {

        @Test
        @DisplayName("updatePreferences で card_division_reminder が per-(player, org) に保存される")
        void updateSavesFlag() {
            when(lineNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(PLAYER_ID, ORG_ID))
                    .thenReturn(Optional.empty());

            LineNotificationPreferenceDto dto = LineNotificationPreferenceDto.builder()
                    .playerId(PLAYER_ID)
                    .organizationId(ORG_ID)
                    .cardDivisionReminder(true)
                    .build();

            service.updatePreferences(dto);

            ArgumentCaptor<LineNotificationPreference> captor =
                    ArgumentCaptor.forClass(LineNotificationPreference.class);
            verify(lineNotificationPreferenceRepository).save(captor.capture());
            LineNotificationPreference saved = captor.getValue();
            assertThat(saved.getPlayerId()).isEqualTo(PLAYER_ID);
            assertThat(saved.getOrganizationId()).isEqualTo(ORG_ID);
            assertThat(saved.getCardDivisionReminder()).isTrue();
        }

        @Test
        @DisplayName("getPreferences が card_division_reminder を DTO に載せて返す")
        void getReturnsFlag() {
            LineNotificationPreference entity = LineNotificationPreference.builder()
                    .playerId(PLAYER_ID)
                    .organizationId(ORG_ID)
                    .cardDivisionReminder(true)
                    .build();
            when(lineNotificationPreferenceRepository.findByPlayerId(PLAYER_ID))
                    .thenReturn(List.of(entity));

            List<LineNotificationPreferenceDto> dtos = service.getPreferences(PLAYER_ID);

            assertThat(dtos).hasSize(1);
            assertThat(dtos.get(0).isCardDivisionReminder()).isTrue();
        }
    }
}
