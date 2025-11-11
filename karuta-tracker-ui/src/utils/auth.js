/**
 * 認証・権限管理のユーティリティ関数
 */

/**
 * ロールの定義
 */
export const ROLES = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  ADMIN: 'ADMIN',
  PLAYER: 'PLAYER',
};

/**
 * 現在のユーザーを取得
 * @returns {Object|null} 現在のユーザー情報
 */
export const getCurrentPlayer = () => {
  try {
    const player = localStorage.getItem('currentPlayer');
    return player ? JSON.parse(player) : null;
  } catch (e) {
    console.error('Failed to parse currentPlayer from localStorage', e);
    return null;
  }
};

/**
 * 現在のユーザーを設定
 * @param {Object} player ユーザー情報
 */
export const setCurrentPlayer = (player) => {
  localStorage.setItem('currentPlayer', JSON.stringify(player));
};

/**
 * 現在のユーザーのロールを取得
 * @returns {string|null} ロール
 */
export const getCurrentRole = () => {
  const player = getCurrentPlayer();
  return player?.role || null;
};

/**
 * 指定されたロールのいずれかを持っているかチェック
 * @param {string|string[]} roles チェックするロール（配列または単一のロール）
 * @returns {boolean} 権限があればtrue
 */
export const hasRole = (roles) => {
  const currentRole = getCurrentRole();
  if (!currentRole) return false;

  const rolesArray = Array.isArray(roles) ? roles : [roles];
  return rolesArray.includes(currentRole);
};

/**
 * スーパー管理者かチェック
 * @returns {boolean} スーパー管理者ならtrue
 */
export const isSuperAdmin = () => {
  return hasRole(ROLES.SUPER_ADMIN);
};

/**
 * 管理者（SUPERまたはADMIN）かチェック
 * @returns {boolean} 管理者ならtrue
 */
export const isAdmin = () => {
  return hasRole([ROLES.SUPER_ADMIN, ROLES.ADMIN]);
};

/**
 * 一般プレイヤーかチェック
 * @returns {boolean} 一般プレイヤーならtrue
 */
export const isPlayer = () => {
  return hasRole(ROLES.PLAYER);
};

/**
 * ログアウト処理
 */
export const logout = () => {
  localStorage.removeItem('currentPlayer');
  localStorage.removeItem('authToken');
};
