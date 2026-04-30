import { useNavigate } from 'react-router-dom';
import { X, UserPlus, UserMinus } from 'lucide-react';

const AttendanceRegisterModal = ({ isOpen, onClose, year, month }) => {
  const navigate = useNavigate();

  if (!isOpen) {
    return null;
  }

  const handleParticipation = () => {
    navigate(`/practice/participation?year=${year}&month=${month}`);
    onClose();
  };

  const handleCancel = () => {
    navigate(`/practice/cancel?year=${year}&month=${month}`);
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
      <div className="bg-[#f9f6f2] rounded-2xl shadow-xl max-w-md w-full overflow-hidden flex flex-col">
        {/* ヘッダー */}
        <div className="px-6 pt-5 pb-3 flex justify-between items-start flex-shrink-0">
          <div>
            <h2 className="text-lg font-bold text-[#5f3a2d]">出欠登録</h2>
            <p className="text-sm text-[#8a7568] mt-1">
              {year}年{month}月の出欠登録を行います。
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-[#8a7568] hover:text-[#5f3a2d] -mt-1"
            aria-label="閉じる"
          >
            <X size={20} />
          </button>
        </div>

        {/* コンテンツ */}
        <div className="px-6 pb-4 flex flex-col gap-2">
          <button
            onClick={handleParticipation}
            className="w-full flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium text-white bg-[#82655a] rounded-lg hover:bg-[#6b5048] transition-colors"
          >
            <UserPlus size={16} />
            参加登録
          </button>
          <button
            onClick={handleCancel}
            className="w-full flex items-center justify-center gap-2 px-4 py-3 text-sm font-medium text-[#82655a] bg-white border border-[#82655a] rounded-lg hover:bg-[#e2d9d0] transition-colors"
          >
            <UserMinus size={16} />
            キャンセル登録
          </button>
        </div>

        {/* フッター */}
        <div className="px-6 py-4 border-t border-[#e2d9d0] flex justify-end flex-shrink-0">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-[#8a7568] border border-[#c5b8ab] rounded-lg hover:bg-[#e2d9d0] transition-colors"
          >
            閉じる
          </button>
        </div>
      </div>
    </div>
  );
};

export default AttendanceRegisterModal;
