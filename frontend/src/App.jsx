import {BrowserRouter as Router, Routes, Route, Navigate, useLocation} from "react-router-dom";
import Navbar from './components/Navbar';
import MainPage from "./pages/MainPage";
import Login from "./pages/Login";
import Signup from "./pages/Signup";
import Profile from "./pages/Profile";
import ProtectedRoute from './components/ProtectedRoute';
import SessionGuard from './components/SessionGuard';
import Chat from "./pages/ChatArea.jsx";
import useMobileFullscreen from './hooks/useMobileFullscreen';

function AppContent() {
    const location = useLocation();
    
    // We only want to mount ChatArea when the user is actually using the app (chat or profile)
    // By keeping it mounted while on /profile, we prevent it from unmounting and losing its state (which causes loading buffers).
    const isAppActive = location.pathname === '/chatarea' || location.pathname === '/profile';
    const showChat = location.pathname === '/chatarea';

    return (
        <div className="App">
            <Navbar/>
            <SessionGuard>
                <Routes>
                    <Route path="/" element={<MainPage/>} />
                    <Route path="/login" element={<Login/>} />
                    <Route path="/signup" element={<Signup/>} />
                    <Route path="/profile" element={
                        <ProtectedRoute>
                            <Profile/>
                        </ProtectedRoute>
                    }/>
                    {/* We handle /chatarea manually below to prevent unmounting */}
                    {showChat && <Route path="/chatarea" element={<div/>} />}
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>

                {isAppActive && (
                    <div style={{ display: showChat ? 'block' : 'none', height: '100%' }}>
                        <ProtectedRoute>
                            <Chat/>
                        </ProtectedRoute>
                    </div>
                )}
            </SessionGuard>
        </div>
    );
}

function App() {
    useMobileFullscreen();
    return (
        <Router>
            <AppContent />
        </Router>
    );
}
export default App;