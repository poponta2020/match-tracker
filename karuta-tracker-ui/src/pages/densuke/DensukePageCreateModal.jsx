import { useState, useEffect } from 'react';
import { X, Loader2, AlertCircle } from 'lucide-react';
import { practiceAPI } from '../../api';
import { densukeTemplateAPI } from '../../api/densukeTemplates';

/**
 * 伝助ページ作成モーダル
 *
 * テンプレートをロードして初期値を表示し、ユーザーが編集・確定すると
 * POST /api/practice-sessions/densuke/create-page を呼び出す。
 */
const DensukePageCreateModal = ({
  isOpen,
  onClose,
  year,
  month,
  organizationId,
  orgName,
  orgColor,
  onSuccess,
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!isOpen) return;
    setError(null);
    const loadTemplate = async () => {
      setLoading(true);
      try {
        const res = await densukeTemplateAPI.get(organizationId);
        const tpl = res.data || {};
        const tplTitle = tpl.titleTemplate || '{year}年{month}月 練習出欠';
        const resolvedTitle = tplTitle
          .replace('{year}', String(year))
          .replace('{month}', String(month))
          .replace('{organization_name}', orgName || '');
        setTitle(resolvedTitle);
        setDescription(tpl.description || '');
        setContactEmail(tpl.contactEmail || '');
      } catch {
        setError('テンプレートの読み込みに失敗しました');
      } finally {
        setLoading(false);
      }
    };
    loadTemplate();
  }, [isOpen, year, month, organizationId, orgName]);

  const handleCreate = async () => {
    if (!title.trim()) {
      setError('タイトルは必須です');
      return;
    }
    setCreating(true);
    setError(null);
    try {
      const res = await practiceAPI.createDensukePage(year, month, organizationId, {
        title: title.trim(),
        description,
        contactEmail,
      });
      onSuccess?.(res.data);
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || '伝助ページの作成に失敗しました');
    } finally {
      setCreating(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl max-w-md w-full max-h-[90vh] overflow-y-auto">
        <div
          className="flex items-center justify-between px-5 py-4 rounded-t-xl"
          style={{ backgroundColor: orgColor }}
        >
          <h2 className="text-base font-semibold text-white">
            伝助ページ作成（{year}年{month}月）
          </h2>
          <button
            onClick={onClose}
            disabled={creating}
            className="text-white/80 hover:text-white disabled:opacity-50"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5">
          {loading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="w-6 h-6 animate-spin" style={{ color: orgColor }} />
            </div>
          ) : (
            <>
              {error && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-start gap-2 text-red-700">
                  <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                  <span className="text-sm">{error}</span>
                </div>
              )}

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-[#374151] mb-1">
                    タイトル <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    disabled={creating}
                    maxLength={200}
                    className="w-full px-3 py-2 text-sm border border-[#d4ddd7] rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a] disabled:bg-gray-50"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#374151] mb-1">
                    説明
                  </label>
                  <textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    disabled={creating}
                    rows={4}
                    className="w-full px-3 py-2 text-sm border border-[#d4ddd7] rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a] disabled:bg-gray-50"
                    placeholder="伝助ページ先頭に表示される説明文"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#374151] mb-1">
                    連絡先メアド
                  </label>
                  <input
                    type="email"
                    value={contactEmail}
                    onChange={(e) => setContactEmail(e.target.value)}
                    disabled={creating}
                    maxLength={255}
                    placeholder="省略可"
                    className="w-full px-3 py-2 text-sm border border-[#d4ddd7] rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a] disabled:bg-gray-50"
                  />
                  <p className="mt-1 text-xs text-[#6b7280]">
                    伝助に登録されると主催者に控えメールが送られます
                  </p>
                </div>
              </div>

              <div className="mt-6 flex gap-2">
                <button
                  onClick={onClose}
                  disabled={creating}
                  className="flex-1 py-2.5 text-sm border border-[#d4ddd7] rounded-lg text-[#374151] hover:bg-[#f9f6f2] disabled:opacity-50 font-medium"
                >
                  キャンセル
                </button>
                <button
                  onClick={handleCreate}
                  disabled={creating || !title.trim()}
                  className="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-sm text-white rounded-lg disabled:opacity-50 font-medium"
                  style={{ backgroundColor: orgColor }}
                >
                  {creating && <Loader2 className="w-4 h-4 animate-spin" />}
                  {creating ? '作成中...' : '作成'}
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default DensukePageCreateModal;
