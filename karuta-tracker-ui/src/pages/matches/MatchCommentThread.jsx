import { useState, useEffect, useRef } from 'react';
import { matchCommentsAPI } from '../../api/matchComments';
import { useAuth } from '../../context/AuthContext';
import { Send, Pencil, Trash2, X, Check, MessageCircle } from 'lucide-react';

export default function MatchCommentThread({ matchId, menteeId }) {
  const { currentPlayer } = useAuth();
  const [comments, setComments] = useState([]);
  const [newComment, setNewComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editContent, setEditContent] = useState('');
  const [error, setError] = useState(null);
  const bottomRef = useRef(null);
  const textareaRef = useRef(null);

  const fetchComments = async () => {
    try {
      const res = await matchCommentsAPI.getComments(matchId, menteeId);
      setComments(res.data);
    } catch {
      // No mentor relationship or other error - silently ignore
    }
  };

  useEffect(() => {
    if (matchId && menteeId) {
      fetchComments();
    }
  }, [matchId, menteeId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [comments]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!newComment.trim() || submitting) return;
    try {
      setSubmitting(true);
      setError(null);
      await matchCommentsAPI.createComment(matchId, menteeId, newComment.trim());
      setNewComment('');
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto';
      }
      await fetchComments();
    } catch (err) {
      setError(err.response?.data?.message || 'コメントの投稿に失敗しました');
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpdate = async (commentId) => {
    if (!editContent.trim()) return;
    try {
      setError(null);
      await matchCommentsAPI.updateComment(matchId, commentId, editContent.trim());
      setEditingId(null);
      setEditContent('');
      await fetchComments();
    } catch (err) {
      setError(err.response?.data?.message || 'コメントの更新に失敗しました');
    }
  };

  const handleDelete = async (commentId) => {
    if (!window.confirm('このコメントを削除しますか？')) return;
    try {
      setError(null);
      await matchCommentsAPI.deleteComment(matchId, commentId);
      await fetchComments();
    } catch (err) {
      setError(err.response?.data?.message || 'コメントの削除に失敗しました');
    }
  };

  const formatDateTime = (dateStr) => {
    const d = new Date(dateStr);
    const month = d.getMonth() + 1;
    const day = d.getDate();
    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    return `${month}/${day} ${hours}:${minutes}`;
  };

  const handleInputChange = (e) => {
    setNewComment(e.target.value);
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.style.height = 'auto';
      textarea.style.height = Math.min(textarea.scrollHeight, 96) + 'px';
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-sm flex flex-col h-[28rem]">
      <div className="p-3 border-b">
        <h3 className="text-base font-semibold text-gray-800 flex items-center gap-2">
          <MessageCircle size={18} className="text-[#4a6b5a]" />
          コメント
        </h3>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 p-2 rounded text-sm mx-3 mt-2">
          {error}
        </div>
      )}

      {/* コメント一覧 */}
      <div className="flex-1 overflow-y-auto min-h-0 p-4 space-y-3 bg-[#f0ebe4]">
        {comments.length === 0 ? (
          <p className="text-gray-400 text-sm text-center py-4">まだコメントはありません</p>
        ) : (
          comments.map((comment) => {
            const isOwn = comment.authorId === currentPlayer?.id;
            const isEditing = editingId === comment.id;

            return (
              <div
                key={comment.id}
                className={`flex flex-col ${isOwn ? 'items-end' : 'items-start'}`}
              >
                <span className="text-xs text-gray-500 mb-0.5 px-1">
                  {comment.authorName}
                </span>
                <div
                  className={`max-w-[80%] rounded-lg px-3 py-2 ${
                    isOwn
                      ? 'bg-[#4a6b5a] text-white'
                      : 'bg-gray-100 text-gray-800'
                  }`}
                >
                  {isEditing ? (
                    <div className="space-y-2">
                      <textarea
                        value={editContent}
                        onChange={(e) => setEditContent(e.target.value)}
                        className="w-full border rounded p-2 text-sm text-gray-800 resize-none"
                        rows={2}
                        autoFocus
                      />
                      <div className="flex gap-1 justify-end">
                        <button
                          onClick={() => handleUpdate(comment.id)}
                          className="p-1 text-green-600 hover:bg-green-50 rounded"
                        >
                          <Check size={14} />
                        </button>
                        <button
                          onClick={() => { setEditingId(null); setEditContent(''); }}
                          className="p-1 text-gray-500 hover:bg-gray-50 rounded"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    </div>
                  ) : (
                    <p className="text-sm whitespace-pre-wrap">{comment.content}</p>
                  )}
                </div>
                <div className="flex items-center gap-1 mt-0.5 px-1">
                  <span className="text-xs text-gray-400">{formatDateTime(comment.createdAt)}</span>
                  {isOwn && !isEditing && (
                    <>
                      <button
                        onClick={() => { setEditingId(comment.id); setEditContent(comment.content); }}
                        className="p-0.5 text-gray-400 hover:text-gray-600"
                      >
                        <Pencil size={12} />
                      </button>
                      <button
                        onClick={() => handleDelete(comment.id)}
                        className="p-0.5 text-gray-400 hover:text-red-500"
                      >
                        <Trash2 size={12} />
                      </button>
                    </>
                  )}
                </div>
              </div>
            );
          })
        )}
        <div ref={bottomRef} />
      </div>

      {/* 投稿フォーム */}
      <div className="p-3 border-t bg-white">
        <form onSubmit={handleSubmit} className="flex gap-2">
          <textarea
            ref={textareaRef}
            value={newComment}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            placeholder="コメントを入力..."
            rows={1}
            className="flex-1 border border-gray-300 rounded-full px-4 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-[#4a6b5a] resize-none overflow-y-auto"
            style={{ height: 'auto' }}
          />
          <button
            type="submit"
            disabled={!newComment.trim() || submitting}
            className="bg-[#4a6b5a] text-white p-2 rounded-full disabled:opacity-50"
          >
            <Send size={18} />
          </button>
        </form>
      </div>
    </div>
  );
}
