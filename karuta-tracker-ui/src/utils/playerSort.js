/**
 * 選手ソート用ユーティリティ
 * ソート順: ロール（SUPER_ADMIN→ADMIN→PLAYER）→ 級位（A級→E級）→ 段位（八段→無段）→ 名前（あいうえお順）→ 未設定は最後
 */

const ROLE_ORDER = ['SUPER_ADMIN', 'ADMIN', 'PLAYER'];
const KYU_RANK_ORDER = ['A級', 'B級', 'C級', 'D級', 'E級'];
const DAN_RANK_ORDER = ['八段', '七段', '六段', '五段', '四段', '参段', '弐段', '初段', '無段'];

/**
 * 選手オブジェクトをロール→級位→段位→名前順でソートする
 * @param {Array} players - role, kyuRank, danRank, name を持つ選手オブジェクトの配列
 * @returns {Array} ソート済みの新しい配列
 */
export const sortPlayersByRank = (players) => {
  return [...players].sort((a, b) => {
    // ロールでソート（SUPER_ADMIN→ADMIN→PLAYER、未設定は最後）
    const roleA = a.role ? ROLE_ORDER.indexOf(a.role) : ROLE_ORDER.length;
    const roleB = b.role ? ROLE_ORDER.indexOf(b.role) : ROLE_ORDER.length;
    if (roleA !== -1 && roleB !== -1 && roleA !== roleB) return roleA - roleB;

    // 級位でソート（A級が先、未設定は最後）
    const kyuA = a.kyuRank ? KYU_RANK_ORDER.indexOf(a.kyuRank) : KYU_RANK_ORDER.length;
    const kyuB = b.kyuRank ? KYU_RANK_ORDER.indexOf(b.kyuRank) : KYU_RANK_ORDER.length;
    if (kyuA !== kyuB) return kyuA - kyuB;

    // 段位でソート（八段が先、未設定は最後）
    const danA = a.danRank ? DAN_RANK_ORDER.indexOf(a.danRank) : DAN_RANK_ORDER.length;
    const danB = b.danRank ? DAN_RANK_ORDER.indexOf(b.danRank) : DAN_RANK_ORDER.length;
    if (danA !== danB) return danA - danB;

    // 名前のあいうえお順
    return (a.name || '').localeCompare(b.name || '', 'ja');
  });
};
