import {BrowserRouter as Router, Routes, Route, Navigate} from "react-router-dom";
import Navbar from './components/Navbar';
import MainPage from "./pages/MainPage";
import Login from "./pages/Login";
import Signup from "./pages/Signup";
import Profile from "./pages/Profile";
import ProtectedRoute from './components/ProtectedRoute';
import SessionGuard from './components/SessionGuard';
import Chat from "./pages/ChatArea.jsx";

function App() {
    return (
        <Router>
            <div className="App">
                <Navbar/>
                <SessionGuard>
                    <Routes>
                        <Route path="/" element={<MainPage/>} />
                        <Route path="/login" element={<Login/>} />
                        <Route path="/signup" element={<Signup/>} />
                        <Route path="/chatarea" element={
                            <ProtectedRoute>
                                <Chat/>
                            </ProtectedRoute>
                        }/>
                        <Route path="/profile" element={
                            <ProtectedRoute>
                                <Profile/>
                            </ProtectedRoute>
                        }/>
                        <Route path="*" element={<Navigate to="/" replace />} />
                    </Routes>
                </SessionGuard>
            </div>
        </Router>
    );
}
export default App;