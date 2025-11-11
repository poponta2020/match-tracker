package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.MatchPairing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MatchPairingRepository extends JpaRepository<MatchPairing, Long> {

    /**
     * 指定日の対戦組み合わせを取得
     */
    List<MatchPairing> findBySessionDateOrderByMatchNumber(LocalDate sessionDate);

    /**
     * 指定日・試合番号の対戦組み合わせを取得
     */
    List<MatchPairing> findBySessionDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber);

    /**
     * 指定日・試合番号の対戦組み合わせを削除
     */
    void deleteBySessionDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber);

    /**
     * 指定日・試合番号の対戦組み合わせが存在するか確認
     */
    boolean existsBySessionDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber);
}
