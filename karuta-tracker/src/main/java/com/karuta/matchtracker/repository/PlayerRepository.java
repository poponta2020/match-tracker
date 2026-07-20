package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.Player;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 名前とアクティブ状態で選手を検索し、その行に排他ロックを取る（ログイン専用）
     *
     * <p>パスワード変更と並行するログインを直列化するために使う（auth-tokenization）。
     * ロックが無いと次の競合が起きる:
     * <ol>
     *   <li>ログインが変更前のパスワードハッシュを読み、照合に成功する</li>
     *   <li>パスワード変更トランザクションが {@code revokeAllForPlayer} で既存トークンを失効させる</li>
     *   <li>その<b>後で</b>ログインがトークンを INSERT する → 一括失効の対象外として生き残る</li>
     * </ol>
     * 結果、変更前のパスワードから得たトークンが約1年間有効なまま残り、AC-12
     * （パスワード変更で発行済みトークンがすべて無効になる）が破れる。
     *
     * <p>この行ロックにより、ログインが先ならパスワード変更側が確実にそのトークンを失効でき、
     * パスワード変更が先ならログインは変更後のハッシュを読んで旧パスワードを拒否する。
     * ロック対象は当該選手の1行のみで、他の選手のログインは待たされない。
     *
     * @param name 選手名
     * @return 選手（任意）
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Player p WHERE p.name = :name AND p.deletedAt IS NULL")
    Optional<Player> findByNameAndActiveForUpdate(@Param("name") String name);

    /**
     * ロールと管理団体IDで選手を検索（削除済みを除外）
     *
     * @param role ロール
     * @param orgId 管理団体ID
     * @return 指定条件の選手のリスト
     */
    @Query("SELECT p FROM Player p WHERE p.role = :role AND p.adminOrganizationId = :orgId AND p.deletedAt IS NULL")
    List<Player> findByRoleAndAdminOrganizationIdAndActive(@Param("role") Player.Role role, @Param("orgId") Long orgId);

    /**
     * iCalフィードトークンでアクティブな選手を検索
     * 論理削除済みは取得しない（フィードURL経由のアクセスを拒否するため）
     *
     * @param token iCalフィードトークン
     * @return 選手（任意）
     */
    @Query("SELECT p FROM Player p WHERE p.icalFeedToken = :token AND p.deletedAt IS NULL")
    Optional<Player> findByIcalFeedTokenAndActive(@Param("token") String token);
}
