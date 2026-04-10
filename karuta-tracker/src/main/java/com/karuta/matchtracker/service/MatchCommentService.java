package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.MatchCommentCreateRequest;
import com.karuta.matchtracker.dto.MatchCommentDto;
import com.karuta.matchtracker.entity.MatchComment;
import com.karuta.matchtracker.entity.MentorRelationship;
import com.karuta.matchtracker.entity.MentorRelationship.Status;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.MatchCommentRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.repository.MentorRelationshipRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchCommentService {

    private final MatchCommentRepository matchCommentRepository;
    private final MatchRepository matchRepository;
    private final MentorRelationshipRepository mentorRelationshipRepository;
    private final PlayerRepository playerRepository;
    private final LineNotificationService lineNotificationService;

    @Transactional(readOnly = true)
    public List<MatchCommentDto> getComments(Long matchId, Long menteeId, Long currentUserId) {
        validateCommentAccess(menteeId, currentUserId);
        validateMatchBelongsToMentee(matchId, menteeId);

        List<MatchComment> comments = matchCommentRepository.findByMatchIdAndMenteeId(matchId, menteeId);
        return comments.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public MatchCommentDto createComment(Long matchId, MatchCommentCreateRequest request, Long currentUserId) {
        Long menteeId = request.getMenteeId();
        validateCommentAccess(menteeId, currentUserId);
        validateMatchBelongsToMentee(matchId, menteeId);

        MatchComment entity = MatchComment.builder()
                .matchId(matchId)
                .menteeId(menteeId)
                .authorId(currentUserId)
                .content(request.getContent())
                .build();

        MatchComment saved = matchCommentRepository.save(entity);
        log.info("コメント投稿: matchId={}, menteeId={}, authorId={}", matchId, menteeId, currentUserId);
        lineNotificationService.sendMentorCommentNotification(currentUserId, menteeId, matchId, request.getContent());

        return toDto(saved);
    }

    @Transactional
    public MatchCommentDto updateComment(Long matchId, Long commentId, String content, Long currentUserId) {
        MatchComment entity = matchCommentRepository.findActiveById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchComment", commentId));

        validateCommentAccess(entity.getMenteeId(), currentUserId);
        if (!entity.getMatchId().equals(matchId)) {
            throw new ResourceNotFoundException("MatchComment", commentId);
        }
        if (!entity.getAuthorId().equals(currentUserId)) {
            throw new ForbiddenException("コメントの編集は投稿者本人のみ可能です");
        }

        entity.setContent(content);
        MatchComment saved = matchCommentRepository.save(entity);
        log.info("コメント編集: commentId={}, by={}", commentId, currentUserId);

        return toDto(saved);
    }

    @Transactional
    public void deleteComment(Long matchId, Long commentId, Long currentUserId) {
        MatchComment entity = matchCommentRepository.findActiveById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchComment", commentId));

        validateCommentAccess(entity.getMenteeId(), currentUserId);
        if (!entity.getMatchId().equals(matchId)) {
            throw new ResourceNotFoundException("MatchComment", commentId);
        }
        if (!entity.getAuthorId().equals(currentUserId)) {
            throw new ForbiddenException("コメントの削除は投稿者本人のみ可能です");
        }

        entity.setDeletedAt(JstDateTimeUtil.now());
        matchCommentRepository.save(entity);
        log.info("コメント論理削除: commentId={}, by={}", commentId, currentUserId);
    }

    /**
     * コメントアクセス権の検証。
     * メンティー本人、またはACTIVEなメンター関係を持つメンターのみアクセス可能。
     */
    /**
     * matchIdがmenteeIdの試合であることを検証する。
     */
    private void validateMatchBelongsToMentee(Long matchId, Long menteeId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match", matchId));
        if (!menteeId.equals(match.getPlayer1Id()) && !menteeId.equals(match.getPlayer2Id())) {
            throw new IllegalArgumentException("指定された試合はこのメンティーの試合ではありません");
        }
    }

    private void validateCommentAccess(Long menteeId, Long currentUserId) {
        if (menteeId.equals(currentUserId)) {
            return;
        }

        List<MentorRelationship> activeRelationships = mentorRelationshipRepository
                .findByMentorIdAndStatus(currentUserId, Status.ACTIVE);

        boolean isMentor = activeRelationships.stream()
                .anyMatch(r -> r.getMenteeId().equals(menteeId));

        if (!isMentor) {
            throw new ForbiddenException("メンター関係がないためコメントにアクセスできません");
        }
    }

    private MatchCommentDto toDto(MatchComment entity) {
        String authorName = playerRepository.findById(entity.getAuthorId())
                .map(Player::getName).orElse("不明");
        return MatchCommentDto.fromEntity(entity, authorName);
    }
}
