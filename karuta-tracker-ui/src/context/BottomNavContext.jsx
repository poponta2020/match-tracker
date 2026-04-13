import { createContext, useContext, useState } from 'react';

const BottomNavContext = createContext(null);

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
