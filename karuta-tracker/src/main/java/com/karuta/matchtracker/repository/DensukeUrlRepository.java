package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.DensukeUrl;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DensukeUrlRepository extends JpaRepository<DensukeUrl, Long> {

    Optional<DensukeUrl> findByYearAndMonthAndOrganizationId(Integer year, Integer month, Long organizationId);

    List<DensukeUrl> findByYearAndMonth(Integer year, Integer month);

    /**
     * 行ロック付き取得（伝助スケジュール push の並行制御用）。
     * 同一 (year, month, organization_id) のレコードに対する並行 push の差分計算ズレを防ぐ。
     * トランザクション内で呼ぶこと。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM DensukeUrl u WHERE u.year = :year AND u.month = :month AND u.organizationId = :organizationId")
    Optional<DensukeUrl> findByYearAndMonthAndOrganizationIdForUpdate(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("organizationId") Long organizationId);
}
