import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { User } from 'lucide-react';

const NavigationMenu = () => {
  const { currentPlayer } = useAuth();

  return (
    <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-lg font-semibold text-white">{currentPlayer?.name}</span>
        </div>
        <div className="flex items-center gap-1">
          <Link to="/profile" className="p-2 hover:bg-[#3d5a4c] rounded-full transition-colors">
            <User className="w-5 h-5 text-white" />
          </Link>
        </div>
      </div>
    </div>
  );
};

export default NavigationMenu;
