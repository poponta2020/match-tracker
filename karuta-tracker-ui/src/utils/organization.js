// 団体（organization）表示ユーティリティ

// 既知団体は略称 override、未登録団体は名称の先頭2文字にフォールバックする。
// override を維持するのは、フォールバックだと "北海道大学かるた会"→"北海" のように
// 本番表示が退行するため（例: hokudai は「北大」を保つ）。任意コードの団体でも
// name があれば先頭2文字で表示でき、団体決め打ちを排除できる。
const SHORT_NAME_OVERRIDES = {
  wasura: 'わすら',
  hokudai: '北大',
};

export const getOrgShortName = (org) => {
  if (!org) return '';
  return SHORT_NAME_OVERRIDES[org.code] || (org.name ? org.name.substring(0, 2) : '');
};
