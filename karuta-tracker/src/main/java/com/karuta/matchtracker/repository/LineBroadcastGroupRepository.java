package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineBroadcastGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 全体LINE配信グループリポジトリ
 */
@Repository
public interface LineBroadcastGroupRepository extends JpaRepository<LineBroadcastGroup, Long> {

    /** 団体の配信グループ一覧 */
    List<LineBroadcastGroup> findByOrganizationId(Long organizationId);

    /** 複数団体（管理スコープ）の配信グループ一覧 */
    List<LineBroadcastGroup> findByOrganizationIdIn(List<Long> organizationIds);

    /** 有効な配信グループ一覧（スケジューラの配信対象） */
    List<LineBroadcastGroup> findByEnabledTrue();
}
