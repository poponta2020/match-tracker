import { useState, useEffect } from 'react';

const LOADING_IMAGES = [
  '/img/loading/loading1.png',
  '/img/loading/loading2.png',
];

const LoadingScreen = ({ message = '書き出し中' }) => {
  const [imgIndex, setImgIndex] = useState(0);
  const [dots, setDots] = useState(1);

  useEffect(() => {
    const interval = setInterval(() => {
      setImgIndex((prev) => (prev + 1) % LOADING_IMAGES.length);
      setDots((prev) => (prev % 3) + 1);
    }, 500);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="flex flex-col items-center justify-center" style={{ minHeight: '70vh' }}>
      <img
        src={LOADING_IMAGES[imgIndex]}
        alt="読み込み中"
        style={{ width: '40vw', height: '40vw' }}
      />
      <span className="text-sm text-[#6b7280] mt-2">
        <span>{message}</span>
        <span className="inline-block w-6 text-left">{'.'.repeat(dots)}</span>
      </span>
    </div>
  );
};

export default LoadingScreen;
