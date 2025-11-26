import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import './VenueForm.css';

function VenueForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditMode = Boolean(id);

  const [formData, setFormData] = useState({
    name: '',
    defaultMatchCount: 3,
    schedules: [
      { matchNumber: 1, startTime: '13:00', endTime: '13:30' },
      { matchNumber: 2, startTime: '13:30', endTime: '14:00' },
      { matchNumber: 3, startTime: '14:00', endTime: '14:30' },
    ],
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isEditMode) {
      fetchVenue();
    }
  }, [id]);

  const fetchVenue = async () => {
    try {
      setLoading(true);
      const response = await fetch(`http://localhost:8080/api/venues/${id}`);
      if (!response.ok) {
        throw new Error('会場の取得に失敗しました');
      }
      const data = await response.json();

      // 時刻を "HH:MM" 形式に変換
      const schedules = data.schedules.map((s) => ({
        matchNumber: s.matchNumber,
        startTime: s.startTime.substring(0, 5),
        endTime: s.endTime.substring(0, 5),
      }));

      setFormData({
        name: data.name,
        defaultMatchCount: data.defaultMatchCount,
        schedules,
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleNameChange = (e) => {
    setFormData({ ...formData, name: e.target.value });
  };

  const handleMatchCountChange = (e) => {
    const count = parseInt(e.target.value);
    if (count < 1 || count > 20) return;

    const currentSchedules = [...formData.schedules];
    const newSchedules = [];

    // 既存のスケジュールを保持
    for (let i = 1; i <= count; i++) {
      const existing = currentSchedules.find((s) => s.matchNumber === i);
      if (existing) {
        newSchedules.push(existing);
      } else {
        // 新しい試合の初期値を設定（前の試合の終了時刻を開始時刻に）
        const prevSchedule = newSchedules[i - 2];
        const startTime = prevSchedule ? prevSchedule.endTime : '13:00';
        const endTime = calculateEndTime(startTime, 30); // 30分後
        newSchedules.push({
          matchNumber: i,
          startTime,
          endTime,
        });
      }
    }

    setFormData({
      ...formData,
      defaultMatchCount: count,
      schedules: newSchedules,
    });
  };

  const calculateEndTime = (startTime, minutes) => {
    const [hours, mins] = startTime.split(':').map(Number);
    const totalMins = hours * 60 + mins + minutes;
    const newHours = Math.floor(totalMins / 60) % 24;
    const newMins = totalMins % 60;
    return `${String(newHours).padStart(2, '0')}:${String(newMins).padStart(2, '0')}`;
  };

  const handleScheduleChange = (matchNumber, field, value) => {
    const newSchedules = formData.schedules.map((s) =>
      s.matchNumber === matchNumber ? { ...s, [field]: value } : s
    );
    setFormData({ ...formData, schedules: newSchedules });
  };

  const validateForm = () => {
    if (!formData.name.trim()) {
      setError('会場名を入力してください');
      return false;
    }

    if (formData.name.length > 200) {
      setError('会場名は200文字以内で入力してください');
      return false;
    }

    if (formData.defaultMatchCount < 1 || formData.defaultMatchCount > 20) {
      setError('標準試合数は1〜20の範囲で入力してください');
      return false;
    }

    for (const schedule of formData.schedules) {
      if (!schedule.startTime || !schedule.endTime) {
        setError(`第${schedule.matchNumber}試合の時刻を入力してください`);
        return false;
      }

      if (schedule.startTime >= schedule.endTime) {
        setError(`第${schedule.matchNumber}試合の終了時刻は開始時刻より後にしてください`);
        return false;
      }
    }

    return true;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!validateForm()) {
      return;
    }

    try {
      setLoading(true);

      // 時刻を "HH:MM:SS" 形式に変換
      const requestData = {
        name: formData.name.trim(),
        defaultMatchCount: formData.defaultMatchCount,
        schedules: formData.schedules.map((s) => ({
          matchNumber: s.matchNumber,
          startTime: `${s.startTime}:00`,
          endTime: `${s.endTime}:00`,
        })),
      };

      const url = isEditMode
        ? `http://localhost:8080/api/venues/${id}`
        : 'http://localhost:8080/api/venues';

      const method = isEditMode ? 'PUT' : 'POST';

      const response = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestData),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || '会場の保存に失敗しました');
      }

      navigate('/venues');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  if (loading && isEditMode) {
    return <div className="venue-form-container">読み込み中...</div>;
  }

  return (
    <div className="venue-form-container">
      <div className="venue-form-header">
        <h2>{isEditMode ? '会場編集' : '会場登録'}</h2>
      </div>

      {error && <div className="error-message">{error}</div>}

      <form onSubmit={handleSubmit} className="venue-form">
        <div className="form-section">
          <h3>基本情報</h3>

          <div className="form-group">
            <label htmlFor="name">
              会場名 <span className="required">*</span>
            </label>
            <input
              type="text"
              id="name"
              value={formData.name}
              onChange={handleNameChange}
              placeholder="例: 〇〇公民館"
              maxLength={200}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="defaultMatchCount">
              標準試合数 <span className="required">*</span>
            </label>
            <input
              type="number"
              id="defaultMatchCount"
              value={formData.defaultMatchCount}
              onChange={handleMatchCountChange}
              min={1}
              max={20}
              required
            />
            <small>1〜20の範囲で入力してください</small>
          </div>
        </div>

        <div className="form-section">
          <h3>試合時間割</h3>
          <p className="section-description">各試合の開始時刻と終了時刻を入力してください</p>

          <div className="schedules-container">
            {formData.schedules.map((schedule) => (
              <div key={schedule.matchNumber} className="schedule-row">
                <div className="schedule-label">第{schedule.matchNumber}試合</div>
                <div className="schedule-inputs">
                  <div className="time-input-group">
                    <label>開始</label>
                    <input
                      type="time"
                      value={schedule.startTime}
                      onChange={(e) =>
                        handleScheduleChange(
                          schedule.matchNumber,
                          'startTime',
                          e.target.value
                        )
                      }
                      required
                    />
                  </div>
                  <span className="time-separator">〜</span>
                  <div className="time-input-group">
                    <label>終了</label>
                    <input
                      type="time"
                      value={schedule.endTime}
                      onChange={(e) =>
                        handleScheduleChange(
                          schedule.matchNumber,
                          'endTime',
                          e.target.value
                        )
                      }
                      required
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="form-actions">
          <button
            type="button"
            className="btn-cancel"
            onClick={() => navigate('/venues')}
            disabled={loading}
          >
            キャンセル
          </button>
          <button type="submit" className="btn-submit" disabled={loading}>
            {loading ? '保存中...' : isEditMode ? '更新' : '登録'}
          </button>
        </div>
      </form>
    </div>
  );
}

export default VenueForm;
