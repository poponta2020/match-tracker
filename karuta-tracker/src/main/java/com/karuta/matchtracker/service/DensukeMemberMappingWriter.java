package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeMemberMapping;
import com.karuta.matchtracker.repository.DensukeMemberMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 伝助メンバーマッピングの INSERT を呼び出し元トランザクションから隔離する専用 Bean（Issue #1036）。
 *
 * <p>{@link DensukeMemberMapping} は IDENTITY 採番のため {@code save()} 時点で即 INSERT が発行され、
 * 一意制約違反が起きると PostgreSQL は現トランザクション全体を abort 状態
 * （25P02: current transaction is aborted）にする。呼び出し元の書き込みバッチと同一トランザクションで
 * INSERT すると、TOCTOU 競合 1 件で catch 後の救済クエリもバッチ内の後続 DB 操作
 * （他プレイヤーのマッピング・row_id 保存・dirty=false 更新）もすべて失敗し、コミットが
 * {@code UnexpectedRollbackException} になってバッチ全体が破棄される。
 *
 * <p>{@code REQUIRES_NEW}（別コネクション）に INSERT を隔離することで、制約違反時に abort するのは
 * 内側トランザクションのみとなり、呼び出し元は健全なトランザクションのまま TOCTOU 救済とバッチ継続が
 * できる。マッピングは冪等なマスターデータであり、外側バッチが後で rollback しても先行コミットが
 * 残ることに害はない（次回は事前チェックで同一プレイヤー成功扱いになるだけ）。
 *
 * <p>Spring の self-invocation ではプロキシを通らず {@code @Transactional} が効かないため、
 * {@link DensukeWriteService} 内のメソッドではなく別 Bean として切り出している。
 */
@Service
@RequiredArgsConstructor
public class DensukeMemberMappingWriter {

    private final DensukeMemberMappingRepository densukeMemberMappingRepository;

    /**
     * マッピングを新規トランザクションで INSERT し、メソッド復帰時に即コミットする。
     *
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         一意制約違反時。内側トランザクションのみ rollback 済みで、呼び出し元の
     *         トランザクションは健全なまま（呼び出し元が再取得による TOCTOU 救済を行う）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertInNewTransaction(Long densukeUrlId, Long playerId, String densukeMemberId) {
        densukeMemberMappingRepository.save(DensukeMemberMapping.builder()
                .densukeUrlId(densukeUrlId)
                .playerId(playerId)
                .densukeMemberId(densukeMemberId)
                .build());
    }
}
