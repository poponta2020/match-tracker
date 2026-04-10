package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.MentorRelationshipCreateRequest;
import com.karuta.matchtracker.dto.MentorRelationshipDto;
import com.karuta.matchtracker.entity.MentorRelationship;
import com.karuta.matchtracker.entity.MentorRelationship.Status;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.Organization;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.repository.MentorRelationshipRepository;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class MentorRelationshipServiceTest {

    @Mock private MentorRelationshipRepository mentorRelationshipRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PlayerOrganizationRepository playerOrganizationRepository;

    @InjectMocks
    private MentorRelationshipService service;

    private Player mentor;
    private Player mentee;

    @BeforeEach
    void setUp() {
        mentor = new Player();
        mentor.setId(1L);
        mentor.setName("先輩");

        mentee = new Player();
        mentee.setId(2L);
        mentee.setName("後輩");
    }

    @Nested
    @DisplayName("createRelationship")
    class CreateRelationship {

        @Test
        @DisplayName("正常にメンター関係を作成できる")
        void success() {
            MentorRelationshipCreateRequest request = new MentorRelationshipCreateRequest(1L, 10L);
            when(playerRepository.findById(1L)).thenReturn(Optional.of(mentor));
            when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(1L, 10L)).thenReturn(true);
            when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(2L, 10L)).thenReturn(true);
            when(mentorRelationshipRepository.findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(1L, 2L, 10L, Status.ACTIVE))
                    .thenReturn(Optional.empty());
            when(mentorRelationshipRepository.findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(1L, 2L, 10L, Status.PENDING))
                    .thenReturn(Optional.empty());
            when(mentorRelationshipRepository.findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(1L, 2L, 10L, Status.REJECTED))
                    .thenReturn(Optional.empty());
            when(mentorRelationshipRepository.save(any())).thenAnswer(inv -> {
                MentorRelationship r = inv.getArgument(0);
                r.setId(100L);
                return r;
            });
            when(organizationRepository.findById(10L)).thenReturn(Optional.of(new Organization()));

            MentorRelationshipDto result = service.createRelationship(request, 2L);
            assertThat(result).isNotNull();
            verify(mentorRelationshipRepository).save(any());
        }

        @Test
        @DisplayName("自分自身を指名するとエラー")
        void selfNomination() {
            MentorRelationshipCreateRequest request = new MentorRelationshipCreateRequest(2L, 10L);
            assertThatThrownBy(() -> service.createRelationship(request, 2L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("REJECTED後に再指名できる")
        void reNominateAfterRejection() {
            MentorRelationshipCreateRequest request = new MentorRelationshipCreateRequest(1L, 10L);
            MentorRelationship rejected = MentorRelationship.builder()
                    .id(50L).mentorId(1L).menteeId(2L).organizationId(10L).status(Status.REJECTED).build();

            when(playerRepository.findById(1L)).thenReturn(Optional.of(mentor));
            when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(1L, 10L)).thenReturn(true);
            when(playerOrganizationRepository.existsByPlayerIdAndOrganizationId(2L, 10L)).thenReturn(true);
            when(mentorRelationshipRepository.findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(1L, 2L, 10L, Status.ACTIVE))
                    .thenReturn(Optional.empty());
            when(mentorRelationshipRepository.findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(1L, 2L, 10L, Status.PENDING))
                    .thenReturn(Optional.empty());
            when(mentorRelationshipRepository.findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(1L, 2L, 10L, Status.REJECTED))
                    .thenReturn(Optional.of(rejected));
            when(mentorRelationshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(mentee));
            when(organizationRepository.findById(10L)).thenReturn(Optional.of(new Organization()));

            MentorRelationshipDto result = service.createRelationship(request, 2L);
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("approveRelationship")
    class ApproveRelationship {

        @Test
        @DisplayName("メンター本人が承認できる")
        void success() {
            MentorRelationship rel = MentorRelationship.builder()
                    .id(1L).mentorId(1L).menteeId(2L).organizationId(10L).status(Status.PENDING).build();
            when(mentorRelationshipRepository.findById(1L)).thenReturn(Optional.of(rel));
            when(mentorRelationshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(mentor));
            when(playerRepository.findById(2L)).thenReturn(Optional.of(mentee));
            when(organizationRepository.findById(10L)).thenReturn(Optional.of(new Organization()));

            MentorRelationshipDto result = service.approveRelationship(1L, 1L);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("メンター以外が承認するとエラー")
        void forbiddenApproval() {
            MentorRelationship rel = MentorRelationship.builder()
                    .id(1L).mentorId(1L).menteeId(2L).organizationId(10L).status(Status.PENDING).build();
            when(mentorRelationshipRepository.findById(1L)).thenReturn(Optional.of(rel));
            assertThatThrownBy(() -> service.approveRelationship(1L, 2L))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("rejectRelationship")
    class RejectRelationship {

        @Test
        @DisplayName("拒否するとREJECTEDステータスに変更される（削除ではない）")
        void setsRejectedStatus() {
            MentorRelationship rel = MentorRelationship.builder()
                    .id(1L).mentorId(1L).menteeId(2L).organizationId(10L).status(Status.PENDING).build();
            when(mentorRelationshipRepository.findById(1L)).thenReturn(Optional.of(rel));
            when(mentorRelationshipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejectRelationship(1L, 1L);

            verify(mentorRelationshipRepository).save(argThat(entity ->
                    entity.getStatus() == Status.REJECTED));
            verify(mentorRelationshipRepository, never()).delete(any());
        }

        @Test
        @DisplayName("メンター以外が拒否するとエラー")
        void forbiddenRejection() {
            MentorRelationship rel = MentorRelationship.builder()
                    .id(1L).mentorId(1L).menteeId(2L).organizationId(10L).status(Status.PENDING).build();
            when(mentorRelationshipRepository.findById(1L)).thenReturn(Optional.of(rel));
            assertThatThrownBy(() -> service.rejectRelationship(1L, 2L))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("ACTIVE関係をrejectするとエラー")
        void rejectActiveRelationship() {
            MentorRelationship rel = MentorRelationship.builder()
                    .id(1L).mentorId(1L).menteeId(2L).organizationId(10L).status(Status.ACTIVE).build();
            when(mentorRelationshipRepository.findById(1L)).thenReturn(Optional.of(rel));
            assertThatThrownBy(() -> service.rejectRelationship(1L, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getMyMentors")
    class GetMyMentors {

        @Test
        @DisplayName("ACTIVEとPENDINGの両方が返される")
        void includesPending() {
            MentorRelationship active = MentorRelationship.builder()
                    .id(1L).mentorId(1L).menteeId(2L).organizationId(10L).status(Status.ACTIVE).build();
            MentorRelationship pending = MentorRelationship.builder()
                    .id(2L).mentorId(3L).menteeId(2L).organizationId(10L).status(Status.PENDING).build();
            when(mentorRelationshipRepository.findByMenteeIdAndStatusIn(eq(2L), any()))
                    .thenReturn(List.of(active, pending));
            when(playerRepository.findById(anyLong())).thenReturn(Optional.of(mentor));
            when(organizationRepository.findById(10L)).thenReturn(Optional.of(new Organization()));

            List<MentorRelationshipDto> result = service.getMyMentors(2L);
            assertThat(result).hasSize(2);
        }
    }
}
