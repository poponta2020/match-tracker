package com.karuta.matchtracker.exception;

/**
 * サーバ側の状態がクライアントの前提と食い違い、処理を安全に継続できない場合の競合例外。
 * HTTP 409 Conflict にマッピングする。
 *
 * <ul>
 *   <li>B-2: 抽選プレビュー時と確定時で母集団（対象PENDING）が変化した（再プレビューが必要）</li>
 *   <li>B-4: 参加登録の楽観ロックで版が不一致（他端末/伝助で更新済み。再読込が必要）</li>
 * </ul>
 */
public class ConflictStateException extends RuntimeException {
    public ConflictStateException(String message) {
        super(message);
    }
}
