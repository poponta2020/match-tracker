package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.MentorRelationshipCreateRequest;
import com.karuta.matchtracker.dto.MentorRelationshipDto;
import com.karuta.matchtracker.entity.MentorRelationship;
import com.karuta.matchtracker.entity.MentorRelationship.Status;
import com.karuta.matchtracker.entity.Organization;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.MentorRelationshipRepository;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorRelationshipService {

    private final MentorRelationshipRepository mentorRelationshipRepository;
    private final PlayerRepository playerRepository;
    private final OrganizationRepository organizationRepository;
    private final PlayerOrganizationRepository playerOrganizationRepository;

    @Transactional
    public MentorRelationshipDto createRelationship(MentorRelationshipCreateRequest request, Long currentUserId) {
        Long menteeId = currentUserId;
        Long mentorId = request.getMentorId();
        Long organizationId = request.getOrganizationId();

        if (mentorId.equals(menteeId)) {
            throw new IllegalArgumentException("自分自身をメンターに指名することはできません");
        }

        playerRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", mentorId));

        if (!playerOrganizationRepository.existsByPlayerIdAndOrganizationId(mentorId, organizationId)) {
            throw new IllegalArgumentException("指定されたメンターはこの団体に所属していません");
        }
        if (!playerOrganizationRepository.existsByPlayerIdAndOrganizationId(menteeId, organizationId)) {
            throw new IllegalArgumentException("あなたはこの団体に所属していません");
        }

        // 既存の関係を確認（REJECTED なら再指名を許可）
        var existing = mentorRelationshipRepository
                .findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(mentorId, menteeId, organizationId, Status.ACTIVE);
        if (existing.isPresent()) {
            throw new DuplicateResourceException("MentorRelationship", "mentor_id, mentee_id", mentorId + ", " + menteeId);
        }
        var existingPending = mentorRelationshipRepository
                .findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(mentorId, menteeId, organizationId, Status.PENDING);
        if (existingPending.isPresent()) {
            throw new DuplicateResourceException("MentorRelationship", "mentor_id, mentee_id", mentorId + ", " + menteeId);
        }

        // REJECTED の既存レコードがあれば再利用（UNIQUE制約対応）
        var existingRejected = mentorRelationshipRepository
                .findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(mentorId, menteeId, organizationId, Status.REJECTED);
        MentorRelationship saved;
        if (existingRejected.isPresent()) {
            MentorRelationship reuse = existingRejected.get();
            reuse.setStatus(Status.PENDING);
            saved = mentorRelationshipRepository.save(reuse);
            log.info("メンター関係再指名（REJECTED→PENDING）: mentor={}, mentee={}, org={}", mentorId, menteeId, organizationId);
        } else {
            MentorRelationship entity = MentorRelationship.builder()
                    .mentorId(mentorId)
                    .menteeId(menteeId)
                    .organizationId(organizationId)
                    .status(Status.PENDING)
                    .build();
            saved = mentorRelationshipRepository.save(entity);
            log.info("メンター関係作成: mentor={}, mentee={}, org={}", mentorId, menteeId, organizationId);
        }

        return toDto(saved);
    }

    @Transactional
    public MentorRelationshipDto approveRelationship(Long id, Long currentUserId) {
        MentorRelationship entity = mentorRelationshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MentorRelationship", id));

        if (!entity.getMentorId().equals(currentUserId)) {
            throw new ForbiddenException("メンター本人のみ承認できます");
        }
        if (entity.getStatus() != Status.PENDING) {
            throw new IllegalArgumentException("承認待ちの関係のみ承認できます");
        }

        entity.setStatus(Status.ACTIVE);
        MentorRelationship saved = mentorRelationshipRepository.save(entity);
        log.info("メンター関係承認: id={}, mentor={}, mentee={}", id, entity.getMentorId(), entity.getMenteeId());

        return toDto(saved);
    }

    @Transactional
    public void rejectRelationship(Long id, Long currentUserId) {
        MentorRelationship entity = mentorRelationshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MentorRelationship", id));

        if (!entity.getMentorId().equals(currentUserId)) {
            throw new ForbiddenException("メンター本人のみ拒否できます");
        }

        if (entity.getStatus() != Status.PENDING) {
            throw new IllegalArgumentException("承認待ちの関係のみ拒否できます");
        }

        entity.setStatus(Status.REJECTED);
        mentorRelationshipRepository.save(entity);
        log.info("メンター関係拒否: id={}, mentor={}, mentee={}", id, entity.getMentorId(), entity.getMenteeId());
    }

    @Transactional
    public void deleteRelationship(Long id, Long currentUserId) {
        MentorRelationship entity = mentorRelationshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MentorRelationship", id));

        if (!entity.getMentorId().equals(currentUserId) && !entity.getMenteeId().equals(currentUserId)) {
            throw new ForbiddenException("メンター関係の当事者のみ解除できます");
        }

        mentorRelationshipRepository.delete(entity);
        log.info("メンター関係解除: id={}, by={}", id, currentUserId);
    }


    @Transactional(readOnly = true)
    public List<MentorRelationshipDto> getMyMentors(Long currentUserId) {
        List<MentorRelationship> relationships = mentorRelationshipRepository
                .findByMenteeIdAndStatusIn(currentUserId, List.of(Status.ACTIVE, Status.PENDING));
        return relationships.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MentorRelationshipDto> getMyMentees(Long currentUserId) {
        List<MentorRelationship> relationships = mentorRelationshipRepository
                .findByMentorIdAndStatus(currentUserId, Status.ACTIVE);
        return relationships.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MentorRelationshipDto> getPendingRequests(Long currentUserId) {
        List<MentorRelationship> relationships = mentorRelationshipRepository
                .findByMentorIdAndStatus(currentUserId, Status.PENDING);
        return relationships.stream().map(this::toDto).collect(Collectors.toList());
    }

    private MentorRelationshipDto toDto(MentorRelationship entity) {
        String mentorName = playerRepository.findById(entity.getMentorId())
                .map(Player::getName).orElse("不明");
        String menteeName = playerRepository.findById(entity.getMenteeId())
                .map(Player::getName).orElse("不明");
        String orgName = organizationRepository.findById(entity.getOrganizationId())
                .map(Organization::getName).orElse("不明");

        return MentorRelationshipDto.fromEntity(entity, mentorName, menteeName, orgName);
    }
}
