package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 会場のRepositoryインターフェース
 */
@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {

    /**
     * 会場名で検索
     *
     * @param name 会場名
     * @return 会場エンティティ
     */
    Optional<Venue> findByName(String name);

    /**
     * 会場名の存在確認
     *
     * @param name 会場名
     * @return 存在する場合true
     */
    boolean existsByName(String name);
}
