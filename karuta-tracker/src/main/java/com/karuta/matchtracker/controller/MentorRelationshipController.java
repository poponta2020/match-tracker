package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.MentorRelationshipCreateRequest;
import com.karuta.matchtracker.dto.MentorRelationshipDto;
import com.karuta.matchtracker.service.MentorRelationshipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mentor-relationships")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class MentorRelationshipController {

    private final MentorRelationshipService mentorRelationshipService;

    @PostMapping
    public ResponseEntity<MentorRelationshipDto> create(
            @Valid @RequestBody MentorRelationshipCreateRequest request,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("メンター指名: mentorId={}, orgId={}, by={}", request.getMentorId(), request.getOrganizationId(), currentUserId);
        MentorRelationshipDto created = mentorRelationshipService.createRelationship(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<MentorRelationshipDto> approve(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("メンター関係承認: id={}, by={}", id, currentUserId);
        MentorRelationshipDto approved = mentorRelationshipService.approveRelationship(id, currentUserId);
        return ResponseEntity.ok(approved);
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("メンター関係拒否: id={}, by={}", id, currentUserId);
        mentorRelationshipService.rejectRelationship(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("メンター関係解除: id={}, by={}", id, currentUserId);
        mentorRelationshipService.deleteRelationship(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-mentors")
    public ResponseEntity<List<MentorRelationshipDto>> getMyMentors(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        List<MentorRelationshipDto> mentors = mentorRelationshipService.getMyMentors(currentUserId);
        return ResponseEntity.ok(mentors);
    }

    @GetMapping("/my-mentees")
    public ResponseEntity<List<MentorRelationshipDto>> getMyMentees(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        List<MentorRelationshipDto> mentees = mentorRelationshipService.getMyMentees(currentUserId);
        return ResponseEntity.ok(mentees);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<MentorRelationshipDto>> getPending(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        List<MentorRelationshipDto> pending = mentorRelationshipService.getPendingRequests(currentUserId);
        return ResponseEntity.ok(pending);
    }
}
