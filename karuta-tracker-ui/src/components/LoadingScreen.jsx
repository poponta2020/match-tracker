const LoadingScreen = ({ message = '読み込み中...', subMessage = null }) => {
  return (
    <div className="flex flex-col items-center justify-center min-h-96 gap-3">
      <div className="w-8 h-8 border-3 border-[#1A3654]/20 border-t-[#1A3654] rounded-full animate-spin" />
      <div className="text-center">
        <p className="text-[#4a6b5a] font-medium">{message}</p>
        {subMessage && (
          <p className="text-sm text-gray-400 mt-1">{subMessage}</p>
        )}
      </div>
    </div>
  );
};

export default LoadingScreen;
