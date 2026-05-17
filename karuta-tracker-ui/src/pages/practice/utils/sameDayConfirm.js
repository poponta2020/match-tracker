const formatYmdLocal = (date) => {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
};

const sortedClone = (arr) => [...(arr || [])].sort();

// 当日12:00以降にSAME_DAYタイプの「当日」セッションへ実変更がある場合のみ true。
// 当日を触っていない（別日のみ変更）保存では管理者連絡確認ダイアログは不要。
export const needsSameDayConfirm = ({
  sessions,
  orgMap,
  participations,
  initialParticipations,
  now,
}) => {
  if (now.getHours() < 12) return false;
  const todayStr = formatYmdLocal(now);

  return sessions.some((session) => {
    const org = orgMap[session.organizationId];
    if (!org || org.deadlineType !== 'SAME_DAY') return false;
    if (session.sessionDate !== todayStr) return false;

    const current = sortedClone(participations[session.id]);
    const initial = sortedClone(initialParticipations[session.id]);
    return JSON.stringify(current) !== JSON.stringify(initial);
  });
};
