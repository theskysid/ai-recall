import { useState } from "react";
import {Link, useNavigate, useLocation} from "react-router-dom";
import {authService} from "../services/authService.js";
import '../styles/Navbar.css';

const Navbar = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const isAuthenticated = authService.isAuthenticated();
    const currentUser = authService.getCurrentUser();
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

    const isChatPage = location.pathname === '/chatarea';

    const handleLogout = async () => {
        try {
            await authService.logout();
            navigate('/login');
        }
        catch (error) {
            console.error('Logout failed', error);
            localStorage.clear();
            navigate('/login');
        }
    };

    const closeMobileMenu = () => setMobileMenuOpen(false);

    return (
        <nav className={`navbar ${isChatPage ? 'navbar-compact' : ''}`}>
            <div className="navbar-container">
                <Link to="/" className="navbar-brand" onClick={closeMobileMenu}>
                    <span className="navbar-brand-text">Echo</span>
                </Link>

                <button
                    className={`navbar-hamburger ${mobileMenuOpen ? 'open' : ''}`}
                    onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                    aria-label="Toggle navigation menu"
                >
                    <span></span>
                    <span></span>
                    <span></span>
                </button>

                <div className={`navbar-menu ${mobileMenuOpen ? 'menu-open' : ''}`}>
                    {isAuthenticated ? (
                        <>
                            <Link
                                to="/chatarea"
                                className={`navbar-link ${location.pathname === '/chatarea' ? 'active' : ''}`}
                                onClick={closeMobileMenu}
                            >
                                Chat area
                            </Link>
                            <Link
                                to="/profile"
                                className={`navbar-link ${location.pathname === '/profile' ? 'active' : ''}`}
                                onClick={closeMobileMenu}
                            >
                                Profile
                            </Link>
                            <div className="navbar-user">
                                <span className="user-status-dot"></span>
                                <span className="user-info">
                                    {currentUser?.username}
                                </span>
                                <button className="logout-btn" onClick={() => { handleLogout(); closeMobileMenu(); }}>
                                    Logout
                                </button>
                            </div>
                        </>
                    ) : (
                        <div className="navbar-auth-group">
                            <Link to='/login' className={`navbar-link ${location.pathname === '/login' ? 'active' : ''}`} onClick={closeMobileMenu}>
                                Login
                            </Link>
                            <Link to='/signup' className="navbar-btn-signup" onClick={closeMobileMenu}>
                                Signup
                            </Link>
                        </div>
                    )}
                </div>

                {/* Mobile overlay backdrop */}
                {mobileMenuOpen && <div className="navbar-overlay" onClick={closeMobileMenu} />}
            </div>
        </nav>
    );
};

export default Navbar;
