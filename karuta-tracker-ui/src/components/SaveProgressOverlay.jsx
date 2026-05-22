import { useEffect, useRef } from 'react';
import { Loader2, CheckCircle2, AlertCircle } from 'lucide-react';

const FOCUSABLE_SELECTOR =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

const SaveProgressOverlay = ({
  state,
  savingMessage,
  successMessage,
  errorMessage,
  errorDetail = '',
  onSuccessConfirm,
  onErrorClose,
}) => {
  const dialogRef = useRef(null);
  const primaryButtonRef = useRef(null);

  useEffect(() => {
    if (state === 'idle') return;
    if (primaryButtonRef.current) {
      primaryButtonRef.current.focus();
    } else if (dialogRef.current) {
      dialogRef.current.focus();
    }
  }, [state]);

  useEffect(() => {
    if (state === 'idle') return undefined;
    const handleKeyDown = (event) => {
      if (event.key !== 'Tab') return;
      const root = dialogRef.current;
      if (!root) return;
      const focusables = Array.from(root.querySelectorAll(FOCUSABLE_SELECTOR));
      if (focusables.length === 0) {
        event.preventDefault();
        root.focus();
        return;
      }
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      const active = document.activeElement;
      if (event.shiftKey) {
        if (active === first || !root.contains(active)) {
          event.preventDefault();
          last.focus();
        }
      } else if (active === last || !root.contains(active)) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [state]);

  if (state === 'idle') return null;

  return (
    <div
      ref={dialogRef}
      tabIndex={-1}
      className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center px-4 outline-none"
      role="dialog"
      aria-modal="true"
      aria-busy={state === 'saving'}
    >
      <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full">
        {state === 'saving' && (
          <div className="flex flex-col items-center">
            <Loader2 className="w-12 h-12 text-blue-500 animate-spin mb-3" aria-hidden="true" />
            <p className="text-base font-medium text-gray-800">{savingMessage}</p>
          </div>
        )}
        {state === 'success' && (
          <div className="flex flex-col items-center">
            <CheckCircle2 className="w-12 h-12 text-green-500 mb-3" aria-hidden="true" />
            <p className="text-base font-medium text-gray-800 mb-4 text-center">{successMessage}</p>
            <button
              ref={primaryButtonRef}
              type="button"
              onClick={onSuccessConfirm}
              className="w-full px-4 py-2 bg-blue-500 text-white rounded font-medium hover:bg-blue-600"
            >
              カレンダーに戻る
            </button>
          </div>
        )}
        {state === 'error' && (
          <div className="flex flex-col items-center">
            <AlertCircle className="w-12 h-12 text-red-500 mb-3" aria-hidden="true" />
            <p className="text-base font-medium text-gray-800 mb-2 text-center">{errorMessage}</p>
            {errorDetail && (
              <p className="text-sm text-gray-600 mb-4 text-center break-words">{errorDetail}</p>
            )}
            <button
              ref={primaryButtonRef}
              type="button"
              onClick={onErrorClose}
              className="w-full px-4 py-2 bg-gray-200 text-gray-800 rounded font-medium hover:bg-gray-300"
            >
              閉じる
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default SaveProgressOverlay;
