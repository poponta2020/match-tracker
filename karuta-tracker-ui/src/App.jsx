import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import PrivateRoute from './components/PrivateRoute';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';
import MatchList from './pages/matches/MatchList';
import MatchForm from './pages/matches/MatchForm';
import MatchDetail from './pages/matches/MatchDetail';
import PracticeList from './pages/practice/PracticeList';
import PracticeForm from './pages/practice/PracticeForm';
import PracticeDetail from './pages/practice/PracticeDetail';
import PracticeParticipation from './pages/practice/PracticeParticipation';
import PairingGenerator from './pages/pairings/PairingGenerator';
import PlayerList from './pages/players/PlayerList';
import PlayerDetail from './pages/players/PlayerDetail';
import PlayerEdit from './pages/players/PlayerEdit';

function App() {
  return (
    <Router>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route
            path="/"
            element={
              <PrivateRoute>
                <Layout>
                  <Home />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/matches"
            element={
              <PrivateRoute>
                <Layout>
                  <MatchList />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/matches/new"
            element={
              <PrivateRoute>
                <Layout>
                  <MatchForm />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/matches/:id"
            element={
              <PrivateRoute>
                <Layout>
                  <MatchDetail />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/matches/:id/edit"
            element={
              <PrivateRoute>
                <Layout>
                  <MatchForm />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/practice"
            element={
              <PrivateRoute>
                <Layout>
                  <PracticeList />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/practice/new"
            element={
              <PrivateRoute>
                <Layout>
                  <PracticeForm />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/practice/:id"
            element={
              <PrivateRoute>
                <Layout>
                  <PracticeDetail />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/practice/:id/edit"
            element={
              <PrivateRoute>
                <Layout>
                  <PracticeForm />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/practice/participation"
            element={
              <PrivateRoute>
                <Layout>
                  <PracticeParticipation />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/statistics"
            element={
              <PrivateRoute>
                <Layout>
                  <div className="text-center py-20">
                    <h2 className="text-2xl font-bold text-gray-900 mb-4">
                      統計ページ
                    </h2>
                    <p className="text-gray-600">実装中...</p>
                  </div>
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/pairings"
            element={
              <PrivateRoute>
                <Layout>
                  <PairingGenerator />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/players"
            element={
              <PrivateRoute>
                <Layout>
                  <PlayerList />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/players/new"
            element={
              <PrivateRoute>
                <Layout>
                  <PlayerEdit />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/players/:id"
            element={
              <PrivateRoute>
                <Layout>
                  <PlayerDetail />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/players/:id/edit"
            element={
              <PrivateRoute>
                <Layout>
                  <PlayerEdit />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </Router>
  );
}

export default App;
