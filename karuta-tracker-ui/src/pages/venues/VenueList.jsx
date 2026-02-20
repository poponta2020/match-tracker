import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../../api/client';
import './VenueList.css';

function VenueList() {
  const [venues, setVenues] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    fetchVenues();
  }, []);

  const fetchVenues = async () => {
    try {
      setLoading(true);
      const response = await apiClient.get('/venues');
      setVenues(response.data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id, name) => {
    if (!window.confirm(`会場「${name}」を削除してもよろしいですか？`)) {
      return;
    }

    try {
      await apiClient.delete(`/venues/${id}`);

      // 削除成功後、リストを再取得
      await fetchVenues();
    } catch (err) {
      setError(err.message);
    }
  };

  const formatTime = (time) => {
    return time.substring(0, 5); // "HH:MM:SS" -> "HH:MM"
  };

  if (loading) {
    return <div className="venue-list-container">読み込み中...</div>;
  }

  return (
    <div className="venue-list-container">
      <div className="venue-list-header">
        <h2>会場管理</h2>
        <button
          className="btn-primary"
          onClick={() => navigate('/venues/new')}
        >
          新規会場登録
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}

      {venues.length === 0 ? (
        <div className="no-venues">
          <p>登録されている会場はありません</p>
          <button
            className="btn-primary"
            onClick={() => navigate('/venues/new')}
          >
            最初の会場を登録
          </button>
        </div>
      ) : (
        <div className="venues-grid">
          {venues.map((venue) => (
            <div key={venue.id} className="venue-card">
              <div className="venue-card-header">
                <h3>{venue.name}</h3>
                <div className="venue-card-actions">
                  <button
                    className="btn-edit"
                    onClick={() => navigate(`/venues/edit/${venue.id}`)}
                  >
                    編集
                  </button>
                  <button
                    className="btn-delete"
                    onClick={() => handleDelete(venue.id, venue.name)}
                  >
                    削除
                  </button>
                </div>
              </div>

              <div className="venue-card-body">
                <div className="venue-info">
                  <span className="info-label">標準試合数:</span>
                  <span className="info-value">{venue.defaultMatchCount}試合</span>
                </div>

                <div className="venue-schedules">
                  <h4>試合時間割</h4>
                  <table className="schedule-table">
                    <thead>
                      <tr>
                        <th>試合</th>
                        <th>開始時刻</th>
                        <th>終了時刻</th>
                      </tr>
                    </thead>
                    <tbody>
                      {venue.schedules.map((schedule) => (
                        <tr key={schedule.id}>
                          <td>第{schedule.matchNumber}試合</td>
                          <td>{formatTime(schedule.startTime)}</td>
                          <td>{formatTime(schedule.endTime)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default VenueList;
