import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { practiceAPI, venueAPI } from '../../api';
import { isSuperAdmin } from '../../utils/auth';
import { ChevronLeft, ChevronRight, X, MapPin, Save, Trash2, FileText } from 'lucide-react';

// ========== 編集用フォーム（既存） ==========
const PracticeEditForm = ({ id }) => {
  const navigate = useNavigate();
  const [venues, setVenues] = useState([]);
  const [formData, setFormData] = useState({
    sessionDate: '',
    venueId: null,
    totalMatches: 10,
    notes: ''
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [venueRes, sessionRes] = await Promise.all([
          venueAPI.getAll(),
          practiceAPI.getById(id),
        ]);
        setVenues(venueRes.data);
        const session = sessionRes.data;
        setFormData({
          sessionDate: session.sessionDate,
          venueId: session.venueId || null,
          totalMatches: session.totalMatches,
          notes: session.notes || ''
        });
      } catch (err) {
        console.error('Error fetching data:', err);
        setError('データの取得に失敗しました');
      }
    };
    fetchData();
  }, [id]);

  const handleVenueChange = (e) => {
    const venueId = e.target.value ? parseInt(e.target.value) : null;
    const selectedVenue = venues.find(v => v.id === venueId);
    setFormData(prev => ({
      ...prev,
      venueId,
      totalMatches: selectedVenue ? selectedVenue.defaultMatchCount : prev.totalMatches
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await practiceAPI.update(id, {
        ...formData,
        totalMatches: parseInt(formData.totalMatches)
      });
      navigate('/practice');
    } catch (err) {
      setError(err.response?.data?.message || '保存に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto">
      {error && (
        <div className="mb-4 p-4 bg-status-danger-surface border border-status-danger/20 text-status-danger rounded-lg">{error}</div>
      )}
      <form onSubmit={handleSubmit} className="bg-bg shadow-md rounded-lg p-6 space-y-6">
        <div>
          <label className="block text-sm font-medium text-text mb-2">練習日</label>
          <input type="date" value={formData.sessionDate} disabled
            className="w-full px-3 py-2 border border-border-strong rounded-md bg-bg" />
        </div>
        <div>
          <label className="block text-sm font-medium text-text mb-2">会場</label>
          <select value={formData.venueId || ''} onChange={handleVenueChange}
            className="w-full px-3 py-2 border border-border-strong rounded-md focus:outline-none focus:ring-2 focus:ring-focus">
            <option value="">会場を選択してください</option>
            {venues.map(v => <option key={v.id} value={v.id}>{v.name} ({v.defaultMatchCount}試合)</option>)}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-text mb-2">メモ</label>
          <textarea value={formData.notes} onChange={e => setFormData(prev => ({ ...prev, notes: e.target.value }))}
            rows="3" maxLength={1000} placeholder="練習の内容や特記事項など"
            className="w-full px-3 py-2 border border-border-strong rounded-md focus:outline-none focus:ring-2 focus:ring-focus" />
        </div>
        <div className="flex justify-end space-x-4">
          <button type="button" onClick={() => navigate('/practice')}
            className="px-6 py-2 border border-border-strong text-text rounded-lg hover:bg-bg">キャンセル</button>
          <button type="submit" disabled={loading}
            className="px-6 py-2 bg-primary text-text-inverse rounded-lg hover:bg-primary-hover disabled:bg-surface-disabled disabled:text-text-disabled">
            {loading ? '保存中...' : '更新'}
          </button>
        </div>
      </form>
    </div>
  );
};

// ========== 新規登録用カレンダーUI ==========
const PracticeForm = () => {
  const navigate = useNavigate();
  const { id } = useParams();
  const isEdit = Boolean(id);

  // 権限チェック
  useEffect(() => {
    if (!isSuperAdmin()) {
      alert('この機能はスーパー管理者のみ利用できます');
      navigate('/practice');
    }
  }, [navigate]);

  // 編集モードは既存フォームを使用
  if (isEdit) return <PracticeEditForm id={id} />;

  // --- 新規登録モード ---
  const [currentDate, setCurrentDate] = useState(new Date());
  const [venues, setVenues] = useState([]);
  const [entries, setEntries] = useState({}); // { 'YYYY-MM-DD': { venueId, venueName, totalMatches, notes } }
  const [existingDates, setExistingDates] = useState([]); // 既に登録済みの日付
  const [editingDate, setEditingDate] = useState(null); // モーダル表示中の日付
  const [modalVenueId, setModalVenueId] = useState(null);
  const [modalNotes, setModalNotes] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [venueRes, sessionsRes] = await Promise.all([
          venueAPI.getAll(),
          practiceAPI.getSessionSummaries(year, month + 1),
        ]);
        setVenues(venueRes.data);
        setExistingDates((sessionsRes.data || []).map(s => s.sessionDate));
      } catch (err) {
        console.error('Error fetching data:', err);
      }
    };
    fetchData();
  }, [year, month]);

  // カレンダー生成
  const generateCalendar = () => {
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const daysInMonth = lastDay.getDate();
    const startDayOfWeek = firstDay.getDay();
    const calendar = [];
    let week = new Array(7).fill(null);

    for (let day = 1; day <= daysInMonth; day++) {
      const dayOfWeek = (startDayOfWeek + day - 1) % 7;
      week[dayOfWeek] = day;
      if (dayOfWeek === 6 || day === daysInMonth) {
        calendar.push([...week]);
        week = new Array(7).fill(null);
      }
    }
    return calendar;
  };

  const getDateStr = (day) => {
    return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  };

  const isToday = (day) => {
    if (!day) return false;
    const today = new Date();
    return day === today.getDate() && month === today.getMonth() && year === today.getFullYear();
  };

  // 日付タップ
  const handleDayClick = (day) => {
    if (!day) return;
    const dateStr = getDateStr(day);
    if (existingDates.includes(dateStr)) return; // 登録済みはタップ不可

    // 既にエントリーがあれば編集、なければ新規
    const existing = entries[dateStr];
    setModalVenueId(existing?.venueId || null);
    setModalNotes(existing?.notes || '');
    setEditingDate(dateStr);
  };

  // モーダルで確定
  const handleModalConfirm = () => {
    if (!modalVenueId) return; // 会場未選択

    const venue = venues.find(v => v.id === modalVenueId);
    setEntries(prev => ({
      ...prev,
      [editingDate]: {
        venueId: modalVenueId,
        venueName: venue?.name || '',
        totalMatches: venue?.defaultMatchCount || 7,
        notes: modalNotes
      }
    }));
    setEditingDate(null);
  };

  // エントリー削除
  const handleRemoveEntry = (dateStr) => {
    setEntries(prev => {
      const next = { ...prev };
      delete next[dateStr];
      return next;
    });
    setEditingDate(null);
  };

  // 一括保存
  const handleSave = async () => {
    const entryList = Object.entries(entries);
    if (entryList.length === 0) return;

    setSaving(true);
    setError('');
    setSuccess('');

    try {
      // 順次作成（並列だとDB負荷が高い可能性）
      for (const [dateStr, entry] of entryList) {
        await practiceAPI.create({
          sessionDate: dateStr,
          venueId: entry.venueId,
          totalMatches: entry.totalMatches,
          notes: entry.notes || null,
        });
      }
      setSuccess(`${entryList.length}件の練習日を登録しました`);
      setTimeout(() => navigate('/practice'), 1200);
    } catch (err) {
      console.error('Error saving:', err);
      setError(err.response?.data?.message || '保存に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  const changeMonth = (offset) => {
    setCurrentDate(new Date(year, month + offset, 1));
  };

  const calendar = generateCalendar();
  const monthStr = `${year}年${month + 1}月`;
  const entryCount = Object.keys(entries).length;

  // 会場名の省略表示
  const abbreviateVenue = (name) => {
    if (!name) return '';
    if (name.length <= 3) return name;
    return name.substring(0, 3);
  };

  return (
    <div className="min-h-screen bg-bg pb-32">
      {/* ナビゲーションバー */}
      <div className="bg-surface border-b border-border-subtle shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <button onClick={() => changeMonth(-1)}
            className="p-2 hover:bg-surface rounded-full transition-colors">
            <ChevronLeft className="w-6 h-6 text-text" />
          </button>
          <h1 className="text-lg font-semibold text-text">{monthStr}</h1>
          <button onClick={() => changeMonth(1)}
            className="p-2 hover:bg-surface rounded-full transition-colors">
            <ChevronRight className="w-6 h-6 text-text" />
          </button>
        </div>
      </div>

      <div className="pt-20 px-4">
        {/* 操作説明 */}
        <p className="text-sm text-text-muted mb-3 text-center">
          日付をタップして会場を選択してください
        </p>

        {error && (
          <div className="mb-4 p-3 bg-status-danger-surface border border-status-danger/20 text-status-danger rounded-lg text-sm">{error}</div>
        )}
        {success && (
          <div className="mb-4 p-3 bg-status-success-surface border border-status-success/20 text-status-success rounded-lg text-sm">{success}</div>
        )}

        {/* カレンダー */}
        <div className="bg-surface shadow-md rounded-lg overflow-hidden">
          <table className="w-full border-collapse table-fixed">
            <thead className="bg-surface">
              <tr>
                {['日', '月', '火', '水', '木', '金', '土'].map(d => (
                  <th key={d} className="py-3 text-center text-sm font-medium border">{d}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {calendar.map((week, wi) => (
                <tr key={wi}>
                  {week.map((day, di) => {
                    const dateStr = day ? getDateStr(day) : null;
                    const entry = dateStr ? entries[dateStr] : null;
                    const isExisting = dateStr ? existingDates.includes(dateStr) : false;
                    const today = isToday(day);

                    let bgColor = 'bg-surface';
                    let cursor = 'cursor-default';
                    let borderColor = 'border-border-subtle';

                    if (day) {
                      if (isExisting) {
                        bgColor = 'bg-surface-disabled';
                        cursor = 'cursor-not-allowed';
                      } else if (entry) {
                        bgColor = 'bg-surface';
                        cursor = 'cursor-pointer';
                        borderColor = 'border-secondary';
                      } else {
                        cursor = 'cursor-pointer';
                        bgColor = 'bg-surface hover:bg-surface';
                      }
                    }

                    return (
                      <td
                        key={di}
                        className={`px-1 py-2 border ${bgColor} ${borderColor} ${cursor} align-top h-20 relative`}
                        onClick={() => day && !isExisting && handleDayClick(day)}
                      >
                        {day && (
                          <div className="text-center flex flex-col items-center">
                            <div className={`text-lg leading-tight ${today ? 'font-bold bg-primary text-text-inverse w-8 h-8 rounded-full flex items-center justify-center mx-auto' : ''}`}>
                              {day}
                            </div>
                            {isExisting && (
                              <div className="mt-0.5 text-[10px] text-text-placeholder leading-tight">登録済</div>
                            )}
                            {entry && (
                              <div className="mt-0.5 text-[10px] text-secondary font-medium leading-tight">
                                {abbreviateVenue(entry.venueName)}
                              </div>
                            )}
                          </div>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* 選択済みリスト */}
        {entryCount > 0 && (
          <div className="mt-4 bg-bg rounded-lg shadow-sm overflow-hidden">
            <div className="px-4 py-3 bg-surface text-sm font-semibold text-text">
              選択中の練習日 ({entryCount}件)
            </div>
            <div className="divide-y divide-border-subtle">
              {Object.entries(entries)
                .sort(([a], [b]) => a.localeCompare(b))
                .map(([dateStr, entry]) => (
                  <div key={dateStr} className="px-4 py-3 flex items-center justify-between">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-text">
                          {new Date(dateStr + 'T00:00:00').toLocaleDateString('ja-JP', {
                            month: 'numeric', day: 'numeric', weekday: 'short'
                          })}
                        </span>
                        <span className="text-xs text-text-muted flex items-center gap-1">
                          <MapPin className="w-3 h-3" />{entry.venueName}
                        </span>
                      </div>
                      {entry.notes && (
                        <div className="text-xs text-text-placeholder mt-0.5 flex items-center gap-1 truncate">
                          <FileText className="w-3 h-3 flex-shrink-0" />{entry.notes}
                        </div>
                      )}
                    </div>
                    <button
                      onClick={() => handleRemoveEntry(dateStr)}
                      className="ml-2 p-1.5 text-text-placeholder hover:text-red-500 transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
            </div>
          </div>
        )}
      </div>

      {/* 固定保存ボタン */}
      {entryCount > 0 && (
        <div className="fixed left-0 right-0 z-40 px-4 py-3 bg-bg border-t border-border-subtle shadow-lg"
          style={{ bottom: 'calc(3.5rem + env(safe-area-inset-bottom, 0px))' }}>
          <button
            onClick={handleSave}
            disabled={saving}
            className="w-full flex items-center justify-center gap-2 px-6 py-3 bg-primary text-text-inverse rounded-lg hover:bg-primary-hover transition-colors disabled:opacity-50 font-medium"
          >
            <Save className="w-5 h-5" />
            {saving ? '保存中...' : `${entryCount}件の練習日を登録する`}
          </button>
        </div>
      )}

      {/* 会場選択モーダル */}
      {editingDate && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-end justify-center z-50"
          onClick={() => setEditingDate(null)}>
          <div className="bg-bg rounded-t-2xl w-full max-w-md pb-8 animate-slide-up"
            onClick={e => e.stopPropagation()}>
            {/* ヘッダー */}
            <div className="px-6 pt-5 pb-3 flex justify-between items-center border-b border-border-subtle">
              <h3 className="text-lg font-bold text-text">
                {new Date(editingDate + 'T00:00:00').toLocaleDateString('ja-JP', {
                  month: 'long', day: 'numeric', weekday: 'short'
                })}
              </h3>
              <button onClick={() => setEditingDate(null)} className="text-text-muted hover:text-text">
                <X size={20} />
              </button>
            </div>

            <div className="px-6 pt-4 space-y-4">
              {/* 会場選択 */}
              <div>
                <label className="block text-sm font-medium text-text mb-2">
                  会場 <span className="text-red-500">*</span>
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {venues.map(venue => (
                    <button
                      key={venue.id}
                      type="button"
                      onClick={() => setModalVenueId(venue.id)}
                      className={`px-3 py-3 rounded-lg text-sm font-medium transition-all border ${
                        modalVenueId === venue.id
                          ? 'bg-primary text-text-inverse border-primary shadow-sm'
                          : 'bg-bg text-text border-border-subtle hover:border-secondary'
                      }`}
                    >
                      <div>{venue.name}</div>
                      <div className={`text-xs mt-0.5 ${modalVenueId === venue.id ? 'text-green-100' : 'text-text-placeholder'}`}>
                        {venue.defaultMatchCount}試合
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              {/* メモ */}
              <div>
                <label className="block text-sm font-medium text-text mb-2">メモ</label>
                <input
                  type="text"
                  value={modalNotes}
                  onChange={e => setModalNotes(e.target.value)}
                  maxLength={1000}
                  placeholder="任意"
                  className="w-full px-3 py-2.5 border border-border-subtle rounded-lg focus:outline-none focus:ring-2 focus:ring-focus focus:border-transparent text-sm"
                />
              </div>

              {/* ボタン */}
              <div className="flex gap-3 pt-2">
                {entries[editingDate] && (
                  <button
                    onClick={() => handleRemoveEntry(editingDate)}
                    className="px-4 py-3 text-red-500 border border-red-300 rounded-lg hover:bg-status-danger-surface transition-colors"
                  >
                    <Trash2 className="w-5 h-5" />
                  </button>
                )}
                <button
                  onClick={handleModalConfirm}
                  disabled={!modalVenueId}
                  className="flex-1 py-3 bg-primary text-text-inverse rounded-lg hover:bg-primary-hover transition-colors disabled:bg-surface-disabled disabled:text-text-disabled disabled:cursor-not-allowed font-medium"
                >
                  {entries[editingDate] ? '更新する' : '追加する'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PracticeForm;
