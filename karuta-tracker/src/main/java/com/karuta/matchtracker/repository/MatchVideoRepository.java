package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.MatchVideo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 試合動画のRepositoryインターフェース
 *
 * (match_date, match_number, player1_id, player2_id) の自然キーによる検索、
 * 選手別・日付別の検索、動画台帳（倉庫）のページング検索クエリを提供します。
 */
@Repository
public interface MatchVideoRepository extends JpaRepository<MatchVideo, Long> {

    /**
     * 日付・試合番号・両選手で完全一致する動画を検索（upsert・試合詳細表示用）
     * player1Id < player2Id が保証されている前提
     *
     * @param matchDate 対戦日
     * @param matchNumber 試合番号
     * @param player1Id 選手1のID
     * @param player2Id 選手2のID
     * @return 動画エンティティ（存在しない場合は空）
     */
    @Query("SELECT mv FROM MatchVideo mv WHERE mv.matchDate = :matchDate AND mv.matchNumber = :matchNumber " +
           "AND mv.player1Id = :player1Id AND mv.player2Id = :player2Id")
    Optional<MatchVideo> findByMatchDateAndMatchNumberAndPlayers(@Param("matchDate") LocalDate matchDate,
                                                                 @Param("matchNumber") Integer matchNumber,
                                                                 @Param("player1Id") Long player1Id,
                                                                 @Param("player2Id") Long player2Id);

    /**
     * 日付別の動画を取得（試合番号の昇順）
     *
     * @param matchDate 対戦日
     * @return 動画のリスト
     */
    @Query("SELECT mv FROM MatchVideo mv WHERE mv.matchDate = :matchDate ORDER BY mv.matchNumber ASC")
    List<MatchVideo> findByMatchDate(@Param("matchDate") LocalDate matchDate);

    /**
     * 選手の動画を取得（日付・試合番号の降順）
     *
     * @param playerId 選手ID
     * @return 動画のリスト
     */
    @Query("SELECT mv FROM MatchVideo mv WHERE mv.player1Id = :playerId OR mv.player2Id = :playerId " +
           "ORDER BY mv.matchDate DESC, mv.matchNumber DESC")
    List<MatchVideo> findByPlayerId(@Param("playerId") Long playerId);

    /**
     * 選手＋日付集合に一致する動画を一括取得（一覧表示時のN+1回避用）
     *
     * @param playerId 選手ID
     * @param dates 日付リスト
     * @return 動画のリスト
     */
    @Query("SELECT mv FROM MatchVideo mv WHERE (mv.player1Id = :playerId OR mv.player2Id = :playerId) " +
           "AND mv.matchDate IN :dates")
    List<MatchVideo> findByPlayerIdAndMatchDateIn(@Param("playerId") Long playerId,
                                                  @Param("dates") List<LocalDate> dates);

    /**
     * 動画台帳（倉庫）のページング検索
     *
     * 各条件は nullable で、null の場合はその条件を無視する。
     * 並びは matchDate DESC, matchNumber DESC。
     * 年月絞り込みは呼び出し側で年月→startDate/endDate範囲に変換して渡す。
     *
     * <p>nullable な LocalDate パラメータをそのまま JPQL に渡すと、PostgreSQL JDBC ドライバが
     * パラメータ型を推論できず {@code operator does not exist: date >= bytea} エラーになることがある。
     * {@code CAST(:startDate AS date)} で明示的に date 型へキャストし、これを回避する。</p>
     *
     * @param playerId 選手ID（null可）
     * @param startDate 開始日（null可、この日を含む）
     * @param endDate 終了日（null可、この日を含む）
     * @param pageable ページング情報
     * @return 動画のページ
     */
    @Query("SELECT mv FROM MatchVideo mv WHERE " +
           "(:playerId IS NULL OR mv.player1Id = :playerId OR mv.player2Id = :playerId) " +
           "AND (CAST(:startDate AS date) IS NULL OR mv.matchDate >= :startDate) " +
           "AND (CAST(:endDate AS date) IS NULL OR mv.matchDate <= :endDate) " +
           "ORDER BY mv.matchDate DESC, mv.matchNumber DESC")
    Page<MatchVideo> search(@Param("playerId") Long playerId,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate,
                            Pageable pageable);
}
