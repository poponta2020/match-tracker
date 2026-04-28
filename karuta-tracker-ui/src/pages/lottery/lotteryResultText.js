const JAPANESE_WEEKDAYS = ['日', '月', '火', '水', '木', '金', '土'];

export function getJapaneseWeekday(date) {
  return JAPANESE_WEEKDAYS[date.getDay()];
}

export function formatSessionHeader(sessionDate, venueName) {
  const date = new Date(sessionDate);
  const m = date.getMonth() + 1;
  const d = date.getDate();
  const weekday = getJapaneseWeekday(date);
  const venue = venueName && String(venueName).length > 0 ? venueName : '会場未設定';
  return `${m}/${d}（${weekday}）${venue}`;
}

function getWaitlistedSorted(match) {
  return (match.waitlisted || [])
    .filter((p) => p.status === 'WAITLISTED')
    .slice()
    .sort(
      (a, b) =>
        (a.waitlistNumber ?? Number.MAX_SAFE_INTEGER) -
        (b.waitlistNumber ?? Number.MAX_SAFE_INTEGER)
    );
}

function sessionHasWaitlisted(session) {
  if (!session.matchResults) return false;
  for (const match of Object.values(session.matchResults)) {
    if (getWaitlistedSorted(match).length > 0) return true;
  }
  return false;
}

export function hasAnyWaitlisted(sessions) {
  return (sessions || []).some(sessionHasWaitlisted);
}

function buildSessionBlock(session) {
  const lines = [formatSessionHeader(session.sessionDate, session.venueName)];
  const entries = Object.entries(session.matchResults || {})
    .map(([num, match]) => [Number(num), match])
    .sort((a, b) => a[0] - b[0]);
  for (const [matchNum, match] of entries) {
    lines.push(`★${matchNum}試合目★`);
    const waitlisted = getWaitlistedSorted(match);
    if (waitlisted.length === 0) {
      lines.push('（なし）');
    } else {
      waitlisted.forEach((p, idx) => {
        const num = p.waitlistNumber ?? idx + 1;
        lines.push(`${num}. ${p.playerName}`);
      });
    }
  }
  return lines.join('\n');
}

export function buildCopyText(year, month, sessions) {
  const header = `${month}月の抽選結果（抽選落ちのみ）です`;
  const footer = 'ご確認のほどおねがいします。';
  const blocks = (sessions || [])
    .filter(sessionHasWaitlisted)
    .slice()
    .sort((a, b) => new Date(a.sessionDate) - new Date(b.sessionDate))
    .map(buildSessionBlock);
  if (blocks.length === 0) {
    return `${header}\n\n${footer}`;
  }
  return `${header}\n\n${blocks.join('\n\n')}\n\n${footer}`;
}
