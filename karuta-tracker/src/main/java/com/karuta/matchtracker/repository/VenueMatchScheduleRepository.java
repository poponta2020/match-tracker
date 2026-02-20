package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.VenueMatchSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会場試合時間割のRepositoryインターフェース
 */
@Repository
public interface VenueMatchScheduleRepository extends JpaRepository<VenueMatchSchedule, Long> {

    /**
     * 会場IDで試合時間割を取得
     *
     * @param venueId 会場ID
     * @return 試合時間割リスト
     */
    List<VenueMatchSchedule> findByVenueIdOrderByMatchNumberAsc(Long venueId);

    /**
     * 会場IDで試合時間割を削除
     *
     * @param venueId 会場ID
     */
    void deleteByVenueId(Long venueId);
}
