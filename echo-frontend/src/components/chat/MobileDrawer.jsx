import React from 'react';

const MobileDrawer = ({
    friends,
    ui,
    layout,
    mobileMenuOpen: propMenuOpen,
    setMobileMenuOpen: propSetMenuOpen,
    username: propUsername,
    navigate: propNavigate,
    loadFriendsData: propLoadFriendsData,
    authService: propAuthService
}) => {
    const mobileMenuOpen = propMenuOpen ?? layout?.mobileMenuOpen;
    const setMobileMenuOpen = propSetMenuOpen ?? layout?.setMobileMenuOpen;
    const username = propUsername ?? ui?.username ?? '';
    const navigate = propNavigate ?? ui?.navigate;
    const authService = propAuthService ?? ui?.authService;
    const loadFriendsData = propLoadFriendsData ?? friends?.loadFriendsData;

    if (!mobileMenuOpen) {
        return null;
    }

    return (
        <>
            <div className="tg-drawer-overlay" onClick={() => setMobileMenuOpen(false)} />
            <div className="tg-drawer">
                <div className="tg-drawer-header">
                    <div className="tg-drawer-avatar">
                        {username ? username.charAt(0).toUpperCase() : 'U'}
                    </div>
                    <div className="tg-drawer-user-info">
                        <span className="tg-drawer-username">{username}</span>
                        <span className="tg-drawer-subtitle">Echo User</span>
                    </div>
                </div>
                <nav className="tg-drawer-nav">
                    <button
                        className="tg-drawer-item"
                        onClick={() => {
                            setMobileMenuOpen(false);
                            if (navigate) navigate('/profile');
                        }}
                    >
                        <span className="tg-drawer-item-icon">👤</span>
                        My Profile
                    </button>
                    <button
                        className="tg-drawer-item"
                        onClick={() => {
                            setMobileMenuOpen(false);
                            if (loadFriendsData) loadFriendsData(true);
                        }}
                    >
                        <span className="tg-drawer-item-icon">🔄</span>
                        Refresh
                    </button>
                    <div className="tg-drawer-divider" />
                    <button
                        className="tg-drawer-item tg-drawer-logout"
                        onClick={() => {
                            if (authService) authService.logout();
                            if (navigate) navigate('/login');
                        }}
                    >
                        <span className="tg-drawer-item-icon">🚪</span>
                        Log Out
                    </button>
                </nav>
            </div>
        </>
    );
};

export default MobileDrawer;
