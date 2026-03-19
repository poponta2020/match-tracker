/**
 * 参加者チップ共通コンポーネント
 * 級別に枠線の色を変えて表示する
 */

const KYU_BORDER_COLORS = {
  'A級': 'border-pink-400',
  'B級': 'border-red-400',
  'C級': 'border-orange-400',
  'D級': 'border-yellow-400',
  'E級': 'border-lime-400',
};

const DEFAULT_BORDER_COLOR = 'border-gray-300';

/**
 * 級に応じた枠線色クラスを取得
 */
export const getKyuBorderColor = (kyuRank) => {
  return KYU_BORDER_COLORS[kyuRank] || DEFAULT_BORDER_COLOR;
};

/**
 * PlayerChip - 参加者表示用チップ
 *
 * @param {string} name - 表示名
 * @param {string} [kyuRank] - 級 (例: 'A級', 'B級', ...)
 * @param {string} [className] - 追加のCSSクラス（bg, text, fontなど）
 * @param {function} [onClick] - クリックハンドラ
 * @param {React.ReactNode} [children] - 追加の子要素（×ボタンなど）
 * @param {string} [as] - レンダリング要素 ('span' | 'button' | 'div')
 */
const PlayerChip = ({
  name,
  kyuRank,
  className = '',
  onClick,
  children,
  as: Component = onClick ? 'button' : 'span',
}) => {
  const borderColor = getKyuBorderColor(kyuRank);

  return (
    <Component
      {...(onClick && { onClick, type: 'button' })}
      className={`px-2.5 py-1 rounded-full border-2 ${borderColor} ${className}`}
    >
      {name}
      {children}
    </Component>
  );
};

export default PlayerChip;
