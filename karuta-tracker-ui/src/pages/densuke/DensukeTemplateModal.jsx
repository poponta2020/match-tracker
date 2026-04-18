import { useState, useEffect } from 'react';
import { X, Loader2, AlertCircle, CheckCircle } from 'lucide-react';
import { densukeTemplateAPI } from '../../api/densukeTemplates';

/**
 * 伝助テンプレート編集モーダル
 *
 * 団体ごとの伝助ページ作成時デフォルト値（タイトル・説明・連絡先メアド）を編集する。
 */
const DensukeTemplateModal = ({
  isOpen,
  onClose,
  organizationId,
  orgName,
  orgColor,
  onSaved,
}) => {
  const [titleTemplate, setTitleTemplate] = useState('');
  const [description, setDescription] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  useEffect(() => {
    if (!isOpen) return;
    setError(null);
    setSuccess(null);
    const loadTemplate = async () => {
      setLoading(true);
      try {
        const res = await densukeTemplateAPI.get(organizationId);
        setTitleTemplate(res.data?.titleTemplate || '{year}年{month}月 練習出欠');
        setDescription(res.data?.description || '');
        setContactEmail(res.data?.contactEmail || '');
      } catch {
        setError('テンプレートの読み込みに失敗しました');
      } finally {
        setLoading(false);
      }
    };
    loadTemplate();
  }, [isOpen, organizationId]);

  const handleSave = async () => {
    if (!titleTemplate.trim()) {
      setError('タイトルテンプレートは必須です');
      return;
    }
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await densukeTemplateAPI.update(organizationId, {
        titleTemplate: titleTemplate.trim(),
        description,
        contactEmail,
      });
      setSuccess('テンプレートを保存しました');
      onSaved?.();
      setTimeout(() => {
        setSuccess(null);
        onClose();
      }, 1200);
    } catch (err) {
      setError(err.response?.data?.message || 'テンプレートの保存に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  const now = new Date();
  const previewTitle = titleTemplate
    .replace('{year}', String(now.getFullYear()))
    .replace('{month}', String(now.getMonth() + 1))
    .replace('{organization_name}', orgName || '');

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl max-w-md w-full max-h-[90vh] overflow-y-auto">
        <div
          className="flex items-center justify-between px-5 py-4 rounded-t-xl"
          style={{ backgroundColor: orgColor }}
        >
          <h2 className="text-base font-semibold text-white">
            伝助テンプレート編集（{orgName}）
          </h2>
          <button
            onClick={onClose}
            disabled={saving}
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
              {success && (
                <div className="mb-4 p-3 bg-green-50 border border-green-200 rounded-lg flex items-start gap-2 text-green-700">
                  <CheckCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                  <span className="text-sm">{success}</span>
                </div>
              )}

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-[#374151] mb-1">
                    タイトルテンプレート <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={titleTemplate}
                    onChange={(e) => setTitleTemplate(e.target.value)}
                    disabled={saving}
                    maxLength={200}
                    className="w-full px-3 py-2 text-sm border border-[#d4ddd7] rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a] disabled:bg-gray-50"
                  />
                  <p className="mt-1.5 text-xs text-[#6b7280]">
                    プレースホルダー:{' '}
                    <code className="bg-[#f9f6f2] px-1 rounded">{'{year}'}</code>、{' '}
                    <code className="bg-[#f9f6f2] px-1 rounded">{'{month}'}</code>、{' '}
                    <code className="bg-[#f9f6f2] px-1 rounded">{'{organization_name}'}</code>
                  </p>
                  <p className="mt-1 text-xs text-[#6b7280]">
                    今月で展開したプレビュー:{' '}
                    <span className="font-medium text-[#374151]">{previewTitle}</span>
                  </p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#374151] mb-1">
                    説明
                  </label>
                  <textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    disabled={saving}
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
                    disabled={saving}
                    maxLength={255}
                    placeholder="省略可"
                    className="w-full px-3 py-2 text-sm border border-[#d4ddd7] rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a] disabled:bg-gray-50"
                  />
                </div>
              </div>

              <div className="mt-6 flex gap-2">
                <button
                  onClick={onClose}
                  disabled={saving}
                  className="flex-1 py-2.5 text-sm border border-[#d4ddd7] rounded-lg text-[#374151] hover:bg-[#f9f6f2] disabled:opacity-50 font-medium"
                >
                  キャンセル
                </button>
                <button
                  onClick={handleSave}
                  disabled={saving || !titleTemplate.trim()}
                  className="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-sm text-white rounded-lg disabled:opacity-50 font-medium"
                  style={{ backgroundColor: orgColor }}
                >
                  {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                  {saving ? '保存中...' : '保存'}
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default DensukeTemplateModal;
