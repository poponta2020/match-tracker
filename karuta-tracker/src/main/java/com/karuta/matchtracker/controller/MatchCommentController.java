package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.MatchCommentCreateRequest;
import com.karuta.matchtracker.dto.MatchCommentDto;
import com.karuta.matchtracker.service.MatchCommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matches/{matchId}/comments")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class MatchCommentController {

    private final MatchCommentService matchCommentService;

    @GetMapping
    public ResponseEntity<List<MatchCommentDto>> getComments(
            @PathVariable Long matchId,
            @RequestParam Long menteeId,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        List<MatchCommentDto> comments = matchCommentService.getComments(matchId, menteeId, currentUserId);
        return ResponseEntity.ok(comments);
    }

    @PostMapping
    public ResponseEntity<MatchCommentDto> createComment(
            @PathVariable Long matchId,
            @Valid @RequestBody MatchCommentCreateRequest request,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("コメント投稿: matchId={}, menteeId={}, by={}", matchId, request.getMenteeId(), currentUserId);
        MatchCommentDto created = matchCommentService.createComment(matchId, request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<MatchCommentDto> updateComment(
            @PathVariable Long matchId,
            @PathVariable Long commentId,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        String content = body.get("content");
        log.info("コメント編集: matchId={}, commentId={}, by={}", matchId, commentId, currentUserId);
        MatchCommentDto updated = matchCommentService.updateComment(matchId, commentId, content, currentUserId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long matchId,
            @PathVariable Long commentId,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("コメント削除: matchId={}, commentId={}, by={}", matchId, commentId, currentUserId);
        matchCommentService.deleteComment(matchId, commentId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
