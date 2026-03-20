import { Link } from 'react-router-dom';
import { Calendar, Users, Swords, ClipboardList, LogIn, Shield, FileText } from 'lucide-react';

const Landing = () => {
  return (
    <div className="min-h-screen bg-bg">
      {/* ヘッダー */}
      <header className="bg-primary text-text-inverse">
        <div className="max-w-4xl mx-auto px-4 py-12 text-center">
          <h1 className="text-4xl font-bold mb-3">わすらログ</h1>
          <p className="text-text-inverse/70 text-lg">
            競技かるたの練習・試合記録管理アプリ
          </p>
        </div>
      </header>

      {/* メインコンテンツ */}
      <main className="max-w-4xl mx-auto px-4 py-10">
        {/* アプリ概要 */}
        <section className="bg-surface rounded-xl shadow-sm p-6 mb-8">
          <h2 className="text-xl font-bold text-text mb-4">アプリについて</h2>
          <p className="text-text-muted leading-relaxed">
            わすらログは、競技かるたの練習記録や試合結果を管理するためのアプリです。
            サークルのメンバー間で練習日程の共有、対戦組み合わせの作成、試合結果の記録・閲覧を行うことができます。
          </p>
        </section>

        {/* 機能紹介 */}
        <section className="mb-8">
          <h2 className="text-xl font-bold text-text mb-4">主な機能</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="bg-surface rounded-xl shadow-sm p-5 flex items-start gap-4">
              <Calendar className="w-8 h-8 text-secondary flex-shrink-0 mt-0.5" />
              <div>
                <h3 className="font-semibold text-text mb-1">練習日程管理</h3>
                <p className="text-sm text-text-muted">
                  練習日の登録・参加登録。Googleカレンダーへの同期にも対応しています。
                </p>
              </div>
            </div>
            <div className="bg-surface rounded-xl shadow-sm p-5 flex items-start gap-4">
              <Swords className="w-8 h-8 text-secondary flex-shrink-0 mt-0.5" />
              <div>
                <h3 className="font-semibold text-text mb-1">対戦組み合わせ作成</h3>
                <p className="text-sm text-text-muted">
                  参加者から自動で対戦組み合わせを作成。過去の対戦履歴を考慮した組み合わせが可能です。
                </p>
              </div>
            </div>
            <div className="bg-surface rounded-xl shadow-sm p-5 flex items-start gap-4">
              <ClipboardList className="w-8 h-8 text-secondary flex-shrink-0 mt-0.5" />
              <div>
                <h3 className="font-semibold text-text mb-1">試合結果の記録</h3>
                <p className="text-sm text-text-muted">
                  試合結果の入力・閲覧。級別の統計情報も確認できます。
                </p>
              </div>
            </div>
            <div className="bg-surface rounded-xl shadow-sm p-5 flex items-start gap-4">
              <Users className="w-8 h-8 text-secondary flex-shrink-0 mt-0.5" />
              <div>
                <h3 className="font-semibold text-text mb-1">選手管理</h3>
                <p className="text-sm text-text-muted">
                  メンバーの登録・管理。段位・級位の情報を記録できます。
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* ログインボタン */}
        <section className="bg-surface rounded-xl shadow-sm p-6 mb-8 text-center">
          <p className="text-text-muted mb-4">
            メンバーの方はこちらからログインしてください
          </p>
          <Link
            to="/login"
            className="inline-flex items-center gap-2 bg-primary text-text-inverse px-6 py-3 rounded-lg hover:bg-primary-hover transition-colors font-medium"
          >
            <LogIn className="w-5 h-5" />
            ログイン
          </Link>
        </section>

        {/* ポリシーリンク */}
        <footer className="text-center space-y-3 pb-8">
          <div className="flex justify-center gap-6">
            <Link
              to="/privacy"
              className="inline-flex items-center gap-1.5 text-secondary hover:underline text-sm"
            >
              <Shield className="w-4 h-4" />
              プライバシーポリシー
            </Link>
            <Link
              to="/terms"
              className="inline-flex items-center gap-1.5 text-secondary hover:underline text-sm"
            >
              <FileText className="w-4 h-4" />
              利用規約
            </Link>
          </div>
          <p className="text-xs text-text-placeholder">
            &copy; 2026 わすらログ
          </p>
        </footer>
      </main>
    </div>
  );
};

export default Landing;
