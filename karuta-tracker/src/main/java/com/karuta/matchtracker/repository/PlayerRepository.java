package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 選手マスタのRepositoryインターフェース
 *
 * 選手の検索、認証、論理削除対応のクエリを提供します。
 */
@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    /**
     * 名前で選手を検索（完全一致）
     * ログイン認証で使用
     *
     * @param name 選手名
     * @return 選手（任意）
     */
    Optional<Player> findByName(String name);

    /**
     * 名前で選手を検索（部分一致）
     *
     * @param name 選手名の一部
     * @return 選手のリスト
     */
    List<Player> findByNameContaining(String name);

    /**
     * 削除されていない選手を全て取得（名前順）
     *
     * @return アクティブな選手のリスト
     */
    @Query("SELECT p FROM Player p WHERE p.deletedAt IS NULL ORDER BY p.name ASC")
    List<Player> findAllActiveOrderByName();

    /**
     * 削除されていない選手を全て取得
     *
     * @return アクティブな選手のリスト
     */
    @Query("SELECT p FROM Player p WHERE p.deletedAt IS NULL")
    List<Player> findAllActive();

    /**
     * 削除されていない選手の総数を取得
     *
     * @return アクティブな選手数
     */
    @Query("SELECT COUNT(p) FROM Player p WHERE p.deletedAt IS NULL")
    long countActive();

    /**
     * ロールで選手を検索（削除済みを除外）
     *
     * @param role ロール
     * @return 指定ロールの選手のリスト
     */
    @Query("SELECT p FROM Player p WHERE p.role = :role AND p.deletedAt IS NULL")
    List<Player> findByRoleAndActive(@Param("role") Player.Role role);

    /**
     * 名前とアクティブ状態で選手を検索
     * 認証時に削除済みユーザーを除外するために使用
     *
     * @param name 選手名
     * @return 選手（任意）
     */
    @Query("SELECT p FROM Player p WHERE p.name = :name AND p.deletedAt IS NULL")
    Optional<Player> findByNameAndActive(@Param("name") String name);
}
