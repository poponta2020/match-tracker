import { Link } from 'react-router-dom';
import { ArrowLeft, Shield } from 'lucide-react';

const PrivacyPolicy = () => {
  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-3xl mx-auto px-4 py-8">
        {/* ヘッダー */}
        <div className="mb-8">
          <Link
            to="/"
            className="inline-flex items-center text-blue-600 hover:text-blue-800 mb-4"
          >
            <ArrowLeft className="w-4 h-4 mr-1" />
            トップページへ
          </Link>
          <div className="flex items-center gap-3">
            <Shield className="w-8 h-8 text-blue-600" />
            <h1 className="text-2xl font-bold text-gray-900">
              プライバシーポリシー
            </h1>
          </div>
        </div>

        <div className="bg-white rounded-lg shadow-sm p-6 space-y-8">
          {/* アプリ名・運営者情報 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              1. アプリケーション情報
            </h2>
            <dl className="space-y-2 text-gray-700">
              <div>
                <dt className="font-medium">アプリ名</dt>
                <dd className="ml-4">競技かるた記録</dd>
              </div>
              <div>
                <dt className="font-medium">運営者</dt>
                <dd className="ml-4">土居悠太</dd>
              </div>
              <div>
                <dt className="font-medium">お問い合わせ</dt>
                <dd className="ml-4">
                  <a
                    href="mailto:poponta2020@gmail.com"
                    className="text-blue-600 hover:underline"
                  >
                    poponta2020@gmail.com
                  </a>
                </dd>
              </div>
            </dl>
          </section>

          {/* 取得する情報 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              2. 取得する情報
            </h2>
            <p className="text-gray-700 mb-3">
              本アプリでは、以下の情報を取得・利用します。
            </p>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                <span className="font-medium">ユーザー登録情報：</span>
                選手名、パスワード（暗号化して保存）、性別、利き手、段位・級位、所属かるた会
              </li>
              <li>
                <span className="font-medium">Googleアカウント情報：</span>
                Googleカレンダー同期機能を利用する際に、Googleアカウントのメールアドレスを取得します
              </li>
              <li>
                <span className="font-medium">Googleカレンダーへのアクセス権：</span>
                練習予定をGoogleカレンダーに同期するため、カレンダーの予定の読み書き権限（
                <code className="bg-gray-100 px-1 rounded text-sm">
                  calendar.events
                </code>
                スコープ）を取得します
              </li>
            </ul>
          </section>

          {/* 利用目的 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              3. 利用目的
            </h2>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>競技かるたの試合結果・練習記録の管理</li>
              <li>
                練習予定をユーザーのGoogleカレンダーに同期するため
                <span className="text-gray-500">
                  （ユーザーが手動で「同期」ボタンを押した際にのみ実行されます）
                </span>
              </li>
              <li>上記以外の目的でGoogleカレンダーのデータを利用することはありません</li>
            </ul>
          </section>

          {/* データの保存 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              4. データの保存
            </h2>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                <span className="font-medium">アクセストークン：</span>
                Googleから取得するアクセストークンはサーバーに保存しません。同期処理中のみ一時的に使用し、処理完了後に破棄します。
              </li>
              <li>
                <span className="font-medium">同期マッピング情報：</span>
                どの練習がどのGoogleカレンダーイベントに対応するかの情報のみ、データベースに保存します。
              </li>
              <li>
                <span className="font-medium">パスワード：</span>
                BCryptによるハッシュ化を行い、平文のパスワードは保存しません。
              </li>
            </ul>
          </section>

          {/* 第三者提供 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              5. 第三者への提供
            </h2>
            <p className="text-gray-700">
              ユーザーの個人情報およびGoogleアカウントに関するデータを、第三者に提供・販売・共有することはありません。
            </p>
          </section>

          {/* データの削除 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              6. データの削除・アクセス権の取り消し
            </h2>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                ユーザーはいつでもGoogleアカウントの設定画面
                （
                <a
                  href="https://myaccount.google.com/permissions"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-blue-600 hover:underline"
                >
                  myaccount.google.com/permissions
                </a>
                ）から、本アプリへのアクセス権を取り消すことができます。
              </li>
              <li>
                アカウント削除時には、関連する同期マッピング情報もすべて削除されます。
              </li>
              <li>
                データの削除をご希望の場合は、上記のお問い合わせ先までご連絡ください。
              </li>
            </ul>
          </section>

          {/* 改定 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              7. プライバシーポリシーの改定
            </h2>
            <p className="text-gray-700">
              本ポリシーは、必要に応じて改定することがあります。重要な変更がある場合は、アプリ内でお知らせします。
            </p>
          </section>

          {/* 施行日 */}
          <section className="pt-4 border-t">
            <p className="text-gray-500 text-sm">
              制定日：2026年3月16日
            </p>
          </section>
        </div>
      </div>
    </div>
  );
};

export default PrivacyPolicy;
