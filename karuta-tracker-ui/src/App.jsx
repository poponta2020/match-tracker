import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ErrorBoundary from './components/ErrorBoundary';
import PrivateRoute from './components/PrivateRoute';
import Layout from './components/Layout';
import Login from './pages/Login';
import InviteRegister from './pages/InviteRegister';
import Home from './pages/Home';
import MatchList from './pages/matches/MatchList';
import MatchForm from './pages/matches/MatchForm';
import MatchDetail from './pages/matches/MatchDetail';
import BulkResultInput from './pages/matches/BulkResultInput';
import MatchResultsView from './pages/matches/MatchResultsView';
import PracticeList from './pages/practice/PracticeList';
import PracticeForm from './pages/practice/PracticeForm';
import PracticeDetail from './pages/practice/PracticeDetail';
import PracticeParticipation from './pages/practice/PracticeParticipation';
import PracticeCancelPage from './pages/practice/PracticeCancelPage';
import PairingGenerator from './pages/pairings/PairingGenerator';
import PairingSummary from './pages/pairings/PairingSummary';
import PlayerList from './pages/players/PlayerList';
import PlayerDetail from './pages/players/PlayerDetail';
import PlayerEdit from './pages/players/PlayerEdit';
import Profile from './pages/Profile';
import ProfileEdit from './pages/ProfileEdit';
import VenueList from './pages/venues/VenueList';
import VenueForm from './pages/venues/VenueForm';
import PrivacyPolicy from './pages/PrivacyPolicy';
import TermsOfService from './pages/TermsOfService';
import Landing from './pages/Landing';
import AuthRoute from './components/AuthRoute';
import LotteryResults from './pages/lottery/LotteryResults';
import WaitlistStatus from './pages/lottery/WaitlistStatus';
import OfferResponse from './pages/lottery/OfferResponse';
import NotificationList from './pages/notifications/NotificationList';
import NotificationSettings from './pages/notifications/NotificationSettings';
import LineChannelAdmin from './pages/line/LineChannelAdmin';
import LineScheduleAdmin from './pages/line/LineScheduleAdmin';
import DensukeManagement from './pages/densuke/DensukeManagement';
import SystemSettings from './pages/settings/SystemSettings';
import OrganizationSettings from './pages/settings/OrganizationSettings';

const ProtectedPage = ({ children }) => (
  <PrivateRoute>
    <Layout>{children}</Layout>
  </PrivateRoute>
);

function App() {
  return (
    <ErrorBoundary>
      <Router>
        <AuthProvider>
          <Routes>
            {/* 公開ページ */}
            <Route path="/login" element={<Login />} />
            <Route path="/register/:token" element={<InviteRegister />} />
            <Route path="/privacy" element={<PrivacyPolicy />} />
            <Route path="/terms" element={<TermsOfService />} />

            {/* ホーム（認証状態で分岐） */}
            <Route
              path="/"
              element={
                <AuthRoute
                  authenticated={<ProtectedPage><Home /></ProtectedPage>}
                  unauthenticated={<Landing />}
                />
              }
            />

            {/* 試合 */}
            <Route path="/matches" element={<ProtectedPage><MatchList /></ProtectedPage>} />
            <Route path="/matches/new" element={<ProtectedPage><MatchForm /></ProtectedPage>} />
            <Route path="/matches/:id" element={<ProtectedPage><MatchDetail /></ProtectedPage>} />
            <Route path="/matches/:id/edit" element={<ProtectedPage><MatchForm /></ProtectedPage>} />
            <Route path="/matches/bulk-input/:sessionId" element={<ProtectedPage><BulkResultInput /></ProtectedPage>} />
            <Route path="/matches/results/:sessionId?" element={<ProtectedPage><MatchResultsView /></ProtectedPage>} />

            {/* 練習 */}
            <Route path="/practice" element={<ProtectedPage><PracticeList /></ProtectedPage>} />
            <Route path="/practice/new" element={<ProtectedPage><PracticeForm /></ProtectedPage>} />
            <Route path="/practice/:id" element={<ProtectedPage><PracticeDetail /></ProtectedPage>} />
            <Route path="/practice/:id/edit" element={<ProtectedPage><PracticeForm /></ProtectedPage>} />
            <Route path="/practice/participation" element={<ProtectedPage><PracticeParticipation /></ProtectedPage>} />
            <Route path="/practice/cancel" element={<ProtectedPage><PracticeCancelPage /></ProtectedPage>} />

            {/* 抽選 */}
            <Route path="/lottery/results" element={<ProtectedPage><LotteryResults /></ProtectedPage>} />
            <Route path="/lottery/waitlist" element={<ProtectedPage><WaitlistStatus /></ProtectedPage>} />
            <Route path="/lottery/offer-response" element={<ProtectedPage><OfferResponse /></ProtectedPage>} />

            {/* 組み合わせ */}
            <Route path="/pairings" element={<ProtectedPage><PairingGenerator /></ProtectedPage>} />
            <Route path="/pairings/summary" element={<ProtectedPage><PairingSummary /></ProtectedPage>} />

            {/* 選手 */}
            <Route path="/players" element={<ProtectedPage><PlayerList /></ProtectedPage>} />
            <Route path="/players/new" element={<ProtectedPage><PlayerEdit /></ProtectedPage>} />
            <Route path="/players/:id" element={<ProtectedPage><PlayerDetail /></ProtectedPage>} />
            <Route path="/players/:id/edit" element={<ProtectedPage><PlayerEdit /></ProtectedPage>} />

            {/* プロフィール */}
            <Route path="/profile" element={<ProtectedPage><Profile /></ProtectedPage>} />
            <Route path="/profile/edit" element={<PrivateRoute><ProfileEdit /></PrivateRoute>} />

            {/* 会場 */}
            <Route path="/venues" element={<ProtectedPage><VenueList /></ProtectedPage>} />
            <Route path="/venues/new" element={<ProtectedPage><VenueForm /></ProtectedPage>} />
            <Route path="/venues/edit/:id" element={<ProtectedPage><VenueForm /></ProtectedPage>} />

            {/* 通知 */}
            <Route path="/notifications" element={<ProtectedPage><NotificationList /></ProtectedPage>} />

            {/* 伝助管理 */}
            <Route path="/admin/densuke" element={<ProtectedPage><DensukeManagement /></ProtectedPage>} />

            {/* システム設定 */}
            <Route path="/admin/settings" element={<ProtectedPage><SystemSettings /></ProtectedPage>} />

            {/* 設定 */}
            <Route path="/settings/organizations" element={<ProtectedPage><OrganizationSettings /></ProtectedPage>} />
            <Route path="/settings/notifications" element={<ProtectedPage><NotificationSettings /></ProtectedPage>} />
            <Route path="/admin/line/channels" element={<ProtectedPage><LineChannelAdmin /></ProtectedPage>} />
            <Route path="/admin/line/schedule" element={<ProtectedPage><LineScheduleAdmin /></ProtectedPage>} />

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </Router>
    </ErrorBoundary>
  );
}

export default App;
