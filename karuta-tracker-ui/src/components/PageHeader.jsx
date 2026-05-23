import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

const PageHeader = ({ title, backTo, rightActions = null }) => {
  const navigate = useNavigate();
  return (
    <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
      <div className="max-w-7xl mx-auto flex items-center gap-3">
        <button
          type="button"
          onClick={() => navigate(backTo)}
          className="p-1 -ml-1 hover:bg-[#3d5a4c] rounded-full transition-colors"
          aria-label="戻る"
        >
          <ArrowLeft className="w-5 h-5 text-white" />
        </button>
        <h1 className="text-lg font-semibold text-white truncate flex-1 m-0">
          {title}
        </h1>
        {rightActions}
      </div>
    </div>
  );
};

export default PageHeader;
