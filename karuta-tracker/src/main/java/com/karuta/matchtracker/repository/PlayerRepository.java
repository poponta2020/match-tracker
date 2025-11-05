package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    /**
     * 名前で選手を検索
     */
    List<Player> findByNameContaining(String name);

    /**
     * メールアドレスで選手を検索
     */
    Optional<Player> findByEmail(String email);

    /**
     * すべての選手を名前順で取得
     */
    List<Player> findAllByOrderByNameAsc();

    /**
     * 選手の総数を取得
     */
    @Query("SELECT COUNT(p) FROM Player p")
    long countAllPlayers();
}
