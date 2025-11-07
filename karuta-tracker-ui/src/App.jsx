import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import PrivateRoute from './components/PrivateRoute';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';

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
                  <div className="text-center py-20">
                    <h2 className="text-2xl font-bold text-gray-900 mb-4">
                      試合記録ページ
                    </h2>
                    <p className="text-gray-600">実装中...</p>
                  </div>
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/practices"
            element={
              <PrivateRoute>
                <Layout>
                  <div className="text-center py-20">
                    <h2 className="text-2xl font-bold text-gray-900 mb-4">
                      練習記録ページ
                    </h2>
                    <p className="text-gray-600">実装中...</p>
                  </div>
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
                  <div className="text-center py-20">
                    <h2 className="text-2xl font-bold text-gray-900 mb-4">
                      対戦組み合わせページ
                    </h2>
                    <p className="text-gray-600">実装中...</p>
                  </div>
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/players"
            element={
              <PrivateRoute>
                <Layout>
                  <div className="text-center py-20">
                    <h2 className="text-2xl font-bold text-gray-900 mb-4">
                      選手一覧ページ
                    </h2>
                    <p className="text-gray-600">実装中...</p>
                  </div>
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
