import { createContext, useContext, useState } from 'react';

const BottomNavContext = createContext(null);

// eslint-disable-next-line react-refresh/only-export-components -- useAuthと同方針でファイル分離を見送り（呼び出し元はLayout.jsx/MatchCommentThread.jsxの2箇所のみ）
export const useBottomNav = () => {
  const context = useContext(BottomNavContext);
  if (!context) {
    throw new Error('useBottomNav must be used within a BottomNavProvider');
  }
  return context;
};

export const BottomNavProvider = ({ children }) => {
  const [isVisible, setVisible] = useState(true);

  return (
    <BottomNavContext.Provider value={{ isVisible, setVisible }}>
      {children}
    </BottomNavContext.Provider>
  );
};
