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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 取り札記録（配置＋お手付き詳細）の取得・保存サービス（各プレイヤーの私的データ）。
 * オーナーは currentUserId（＝記録者本人）。保存時は参加者検証のうえ全置換する。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchCardRecordService {

    private final MatchRepository matchRepository;
    private final MatchCardPlacementRepository placementRepository;
    private final MatchOtetsukiDetailRepository otetsukiRepository;

    public MatchCardRecordDto getRecord(Long matchId, Long playerId) {
        List<CardPlacementDto> placements = placementRepository
                .findByMatchIdAndPlayerId(matchId, playerId)
                .stream().map(CardPlacementDto::fromEntity).toList();
        List<OtetsukiDetailDto> otetsuki = otetsukiRepository
                .findByMatchIdAndPlayerIdOrderBySeqAsc(matchId, playerId)
                .stream().map(OtetsukiDetailDto::fromEntity).toList();
        return MatchCardRecordDto.builder()
                .cardPlacements(placements)
                .otetsukiDetails(otetsuki)
                .build();
    }

    /**
     * 記録を全置換で保存する（送られた内容で当該 matchId×playerId のレコードを作り直す）。
     */
    @Transactional
    public MatchCardRecordDto saveRecord(Long matchId, Long playerId, MatchCardRecordDto req) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("試合が見つかりません: id=" + matchId));

        // 記録者は当該試合の参加者であること
        if (!playerId.equals(match.getPlayer1Id()) && !playerId.equals(match.getPlayer2Id())) {
            throw new ForbiddenException("この試合の参加者ではないため取り札記録を保存できません");
        }

        // 全置換
        placementRepository.deleteByMatchIdAndPlayerId(matchId, playerId);
        otetsukiRepository.deleteByMatchIdAndPlayerId(matchId, playerId);

        if (req.getCardPlacements() != null) {
            for (CardPlacementDto p : req.getCardPlacements()) {
                if (p == null || p.getCardNo() == null
                        || p.getTakenBy() == null || p.getField() == null
                        || p.getSide() == null || p.getTier() == null) {
                    continue; // 不完全な配置はスキップ（不明扱い）
                }
                placementRepository.save(MatchCardPlacement.builder()
                        .matchId(matchId)
                        .playerId(playerId)
                        .cardNo(p.getCardNo())
                        .takenBy(p.getTakenBy())
                        .field(p.getField())
                        .side(p.getSide())
                        .tier(p.getTier())
                        .build());
            }
        }

        if (req.getOtetsukiDetails() != null) {
            int seq = 1;
            for (OtetsukiDetailDto o : req.getOtetsukiDetails()) {
                if (o == null || o.getType() == null || o.getType().isBlank()) {
                    continue; // 種類未選択の枠はスキップ
                }
                otetsukiRepository.save(MatchOtetsukiDetail.builder()
                        .matchId(matchId)
                        .playerId(playerId)
                        .seq(seq++)
                        .otetsukiType(o.getType())
                        .hikkakeTarget(o.getHikkakeTarget())
                        .ankiDirection(o.getAnkiDirection())
                        .mishearingReadCardNo(o.getMishearingReadCardNo())
                        .mishearingTouchedCardNo(o.getMishearingTouchedCardNo())
                        .otherText(o.getOtherText())
                        .build());
            }
        }

        return getRecord(matchId, playerId);
    }
}
