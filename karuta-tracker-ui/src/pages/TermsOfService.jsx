import { Link } from 'react-router-dom';
import { ArrowLeft, FileText } from 'lucide-react';

const TermsOfService = () => {
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
            <FileText className="w-8 h-8 text-blue-600" />
            <h1 className="text-2xl font-bold text-gray-900">
              利用規約
            </h1>
          </div>
        </div>

        <div className="bg-white rounded-lg shadow-sm p-6 space-y-8">
          {/* 総則 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第1条（適用）
            </h2>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                本利用規約（以下「本規約」）は、土居悠太（以下「運営者」）が提供する「わすらログ」（以下「本アプリ」）の利用に関する条件を定めるものです。
              </li>
              <li>
                ユーザーは、本アプリを利用することにより、本規約に同意したものとみなします。
              </li>
            </ul>
          </section>

          {/* サービス内容 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第2条（サービス内容）
            </h2>
            <p className="text-gray-700 mb-3">
              本アプリは、競技かるたの練習・試合に関する以下の機能を提供します。
            </p>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>練習日程の管理・参加登録</li>
              <li>対戦組み合わせの作成</li>
              <li>試合結果の記録・閲覧</li>
              <li>練習予定のGoogleカレンダーへの同期</li>
              <li>選手情報の管理</li>
            </ul>
          </section>

          {/* アカウント */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第3条（アカウント）
            </h2>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                ユーザーのアカウントは、運営者または管理者が登録します。ユーザーが自らアカウントを作成することはできません。
              </li>
              <li>
                ユーザーは、自己の責任においてアカウント（選手名・パスワード）を管理するものとします。
              </li>
              <li>
                アカウントの不正利用により生じた損害について、運営者は一切の責任を負いません。
              </li>
            </ul>
          </section>

          {/* 禁止事項 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第4条（禁止事項）
            </h2>
            <p className="text-gray-700 mb-3">
              ユーザーは、以下の行為を行ってはなりません。
            </p>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>他のユーザーのアカウントを不正に使用する行為</li>
              <li>虚偽の情報を登録・入力する行為</li>
              <li>本アプリのサーバーやネットワークに過度の負荷をかける行為</li>
              <li>本アプリの運営を妨害する行為</li>
              <li>その他、運営者が不適切と判断する行為</li>
            </ul>
          </section>

          {/* サービスの変更・停止 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第5条（サービスの変更・停止）
            </h2>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                運営者は、事前の通知なく、本アプリの内容を変更、または提供を停止・中断することができます。
              </li>
              <li>
                運営者は、サービスの変更・停止・中断により生じた損害について、一切の責任を負いません。
              </li>
            </ul>
          </section>

          {/* 免責事項 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第6条（免責事項）
            </h2>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                本アプリは現状有姿で提供されます。運営者は、本アプリの完全性、正確性、有用性等について保証しません。
              </li>
              <li>
                本アプリの利用により生じた損害について、運営者は一切の責任を負いません。
              </li>
              <li>
                本アプリは個人が運営する非営利のサービスであり、商用利用を目的としたものではありません。
              </li>
            </ul>
          </section>

          {/* データの取扱い */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第7条（データの取扱い）
            </h2>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                ユーザーが登録したデータ（試合結果、練習記録等）の取扱いについては、
                <Link to="/privacy" className="text-blue-600 hover:underline">
                  プライバシーポリシー
                </Link>
                に定めるところによります。
              </li>
              <li>
                本アプリは、練習予定をユーザーの Google カレンダーに同期する機能において、Google Calendar API を利用しています。Google API を通じて取得したデータの取扱いについては、プライバシーポリシーに定めるとおりとします。
              </li>
              <li>
                運営者は、データのバックアップについて最善を尽くしますが、データの消失について保証するものではありません。
              </li>
            </ul>
          </section>

          {/* アカウントの停止・削除 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第8条（アカウントの停止・削除）
            </h2>
            <ul className="list-disc ml-6 space-y-2 text-gray-700">
              <li>
                運営者は、ユーザーが本規約に違反した場合、事前の通知なくアカウントを停止または削除することができます。
              </li>
              <li>
                長期間ログインのないアカウントは、運営者の判断により削除される場合があります。
              </li>
            </ul>
          </section>

          {/* 規約の変更 */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第9条（規約の変更）
            </h2>
            <p className="text-gray-700">
              運営者は、必要に応じて本規約を変更することができます。変更後の利用規約は、本アプリ上に掲載した時点で効力を生じるものとします。
            </p>
          </section>

          {/* お問い合わせ */}
          <section>
            <h2 className="text-lg font-semibold text-gray-900 mb-3 border-b pb-2">
              第10条（お問い合わせ）
            </h2>
            <p className="text-gray-700">
              本規約に関するお問い合わせは、以下までご連絡ください。
            </p>
            <p className="mt-2">
              <a
                href="mailto:poponta2020@gmail.com"
                className="text-blue-600 hover:underline"
              >
                poponta2020@gmail.com
              </a>
            </p>
          </section>

          {/* 施行日 */}
          <section className="pt-4 border-t">
            <p className="text-gray-500 text-sm">
              制定日：2026年3月19日
            </p>
          </section>
        </div>
      </div>
    </div>
  );
};

export default TermsOfService;
