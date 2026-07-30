import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { GoogleLogin } from "@react-oauth/google";
import { authService } from "../services/authService.js";
import "../styles/Profile.css";

const Profile = () => {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [message, setMessage] = useState("");
  const [messageType, setMessageType] = useState(""); // "success" | "error"

  // Editable fields
  const [displayName, setDisplayName] = useState("");
  const [bio, setBio] = useState("");
  const [username, setUsername] = useState("");
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  // Link email state
  const [linkEmail, setLinkEmail] = useState("");
  const [linkEmailOtp, setLinkEmailOtp] = useState("");
  const [emailOtpSent, setEmailOtpSent] = useState(false);
  const [showLinkEmail, setShowLinkEmail] = useState(false);

  // Link phone state
  const [linkPhone, setLinkPhone] = useState("");
  const [linkPhoneOtp, setLinkPhoneOtp] = useState("");
  const [phoneOtpSent, setPhoneOtpSent] = useState(false);
  const [showLinkPhone, setShowLinkPhone] = useState(false);

  const [actionLoading, setActionLoading] = useState("");

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    try {
      setIsLoading(true);
      const data = await authService.getProfile();
      setProfile(data);
      setDisplayName(data.displayName || "");
      setBio(data.bio || "");
      setUsername(data.username || "");
    } catch (error) {
      showMessage(error.message || "Failed to load profile", "error");
      if (error.response?.status === 401) {
        navigate("/login");
      }
    } finally {
      setIsLoading(false);
    }
  };

  const showMessage = (msg, type) => {
    setMessage(msg);
    setMessageType(type);
    setTimeout(() => setMessage(""), 4000);
  };

  const handleSaveProfile = async () => {
    setIsSaving(true);
    try {
      const data = await authService.updateProfile({
        displayName: displayName.trim(),
        bio: bio.trim(),
        username: username.trim(),
      });
      setProfile(data);
      setIsEditing(false);
      showMessage("Profile updated successfully!", "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancelEdit = () => {
    setDisplayName(profile.displayName || "");
    setBio(profile.bio || "");
    setUsername(profile.username || "");
    setIsEditing(false);
  };

  // ── Link Email ──────────────────────────────────────────
  const handleSendLinkEmailOtp = async () => {
    setActionLoading("link-email-send");
    try {
      await authService.sendLinkEmailOtp(linkEmail);
      setEmailOtpSent(true);
      showMessage("OTP sent to " + linkEmail, "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setActionLoading("");
    }
  };

  const handleVerifyLinkEmail = async () => {
    setActionLoading("link-email-verify");
    try {
      const data = await authService.verifyLinkEmail(linkEmail, linkEmailOtp);
      setProfile(data);
      setShowLinkEmail(false);
      setLinkEmail("");
      setLinkEmailOtp("");
      setEmailOtpSent(false);
      showMessage("Email linked successfully!", "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setActionLoading("");
    }
  };

  const handleUnlinkEmail = async () => {
    if (!confirm("Are you sure you want to unlink your email?")) return;
    setActionLoading("unlink-email");
    try {
      const data = await authService.unlinkEmail();
      setProfile(data);
      showMessage("Email unlinked", "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setActionLoading("");
    }
  };

  // ── Link Phone ──────────────────────────────────────────
  const handleSendLinkPhoneOtp = async () => {
    setActionLoading("link-phone-send");
    try {
      await authService.sendLinkPhoneOtp(linkPhone);
      setPhoneOtpSent(true);
      showMessage("OTP sent to " + linkPhone, "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setActionLoading("");
    }
  };

  const handleVerifyLinkPhone = async () => {
    setActionLoading("link-phone-verify");
    try {
      const data = await authService.verifyLinkPhone(linkPhone, linkPhoneOtp);
      setProfile(data);
      setShowLinkPhone(false);
      setLinkPhone("");
      setLinkPhoneOtp("");
      setPhoneOtpSent(false);
      showMessage("Phone linked successfully!", "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setActionLoading("");
    }
  };

  const handleUnlinkPhone = async () => {
    if (!confirm("Are you sure you want to unlink your phone?")) return;
    setActionLoading("unlink-phone");
    try {
      const data = await authService.unlinkPhone();
      setProfile(data);
      showMessage("Phone unlinked", "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setActionLoading("");
    }
  };

  // ── Link/Unlink Google ──────────────────────────────────
  const handleLinkGoogle = async (credentialResponse) => {
    setActionLoading("link-google");
    try {
      const data = await authService.linkGoogle(credentialResponse.credential);
      setProfile(data);
      showMessage("Google account linked!", "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setActionLoading("");
    }
  };

  const handleUnlinkGoogle = async () => {
    if (!confirm("Are you sure you want to unlink Google?")) return;
    setActionLoading("unlink-google");
    try {
      const data = await authService.unlinkGoogle();
      setProfile(data);
      showMessage("Google unlinked", "success");
    } catch (error) {
      showMessage(error.message, "error");
    } finally {
      setActionLoading("");
    }
  };

  if (isLoading) {
    return (
      <div className="profile-container">
        <div className="profile-loading">
          <div className="loading-spinner"></div>
          <p>Loading profile...</p>
        </div>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="profile-container">
        <div className="profile-box">
          <p>Unable to load profile. Please try again.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="profile-container">
      <div className="profile-box">
        {/* ── Header ─────────────────────────────────── */}
        <div className="profile-header">
          <div className="profile-avatar-large">
            {(profile.displayName || profile.username || "?")
              .charAt(0)
              .toUpperCase()}
          </div>
          <div className="profile-header-info">
            <h1>{profile.displayName || profile.username}</h1>
            <p className="profile-username">@{profile.username}</p>
            {profile.bio && <p className="profile-bio">{profile.bio}</p>}
          </div>
        </div>

        {/* ── Message ────────────────────────────────── */}
        {message && (
          <div className={`profile-message ${messageType}`}>
            {messageType === "success" ? "✓" : "✕"} {message}
          </div>
        )}

        {/* ── Edit Profile ───────────────────────────── */}
        <div className="profile-section">
          <div className="section-header">
            <h2>Profile Information</h2>
            {!isEditing && (
              <button
                className="edit-btn"
                onClick={() => setIsEditing(true)}
              >
                ✏️ Edit
              </button>
            )}
          </div>

          {isEditing ? (
            <div className="edit-form">
              <div className="form-group">
                <label>Display Name</label>
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="Your display name"
                  className="profile-input"
                  maxLength={50}
                />
              </div>
              <div className="form-group">
                <label>Username</label>
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Username"
                  className="profile-input"
                  maxLength={20}
                />
              </div>
              <div className="form-group">
                <label>Bio</label>
                <textarea
                  value={bio}
                  onChange={(e) => setBio(e.target.value)}
                  placeholder="Tell us about yourself..."
                  className="profile-textarea"
                  maxLength={200}
                  rows={3}
                />
                <span className="char-count">{bio.length}/200</span>
              </div>
              <div className="edit-actions">
                <button
                  className="save-btn"
                  onClick={handleSaveProfile}
                  disabled={isSaving || !username.trim()}
                >
                  {isSaving ? "Saving..." : "Save Changes"}
                </button>
                <button
                  className="cancel-btn"
                  onClick={handleCancelEdit}
                  disabled={isSaving}
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <div className="profile-details">
              <div className="detail-row">
                <span className="detail-label">Display Name</span>
                <span className="detail-value">
                  {profile.displayName || "Not set"}
                </span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Username</span>
                <span className="detail-value">@{profile.username}</span>
              </div>
              <div className="detail-row">
                <span className="detail-label">Bio</span>
                <span className="detail-value">
                  {profile.bio || "No bio yet"}
                </span>
              </div>
            </div>
          )}
        </div>

        {/* ── Connected Accounts ──────────────────────── */}
        <div className="profile-section">
          <h2>Connected Accounts</h2>
          <p className="section-subtitle">
            Link multiple login methods to your account
          </p>

          <div className="accounts-grid">
            {/* ── Email Card ──────────────────────── */}
            <div
              className={`account-card ${profile.email ? "connected" : "disconnected"}`}
            >
              <div className="account-card-header">
                <span className="account-icon">✉️</span>
                <div className="account-info">
                  <h3>Email</h3>
                  <p>
                    {profile.email || "Not connected"}
                  </p>
                </div>
                <span
                  className={`status-badge ${profile.email ? "active" : "inactive"}`}
                >
                  {profile.email ? "✓" : "✕"}
                </span>
              </div>
              {profile.email ? (
                <button
                  className="unlink-btn"
                  onClick={handleUnlinkEmail}
                  disabled={actionLoading === "unlink-email"}
                >
                  {actionLoading === "unlink-email"
                    ? "Unlinking..."
                    : "Unlink Email"}
                </button>
              ) : (
                <>
                  {!showLinkEmail ? (
                    <button
                      className="link-btn"
                      onClick={() => setShowLinkEmail(true)}
                    >
                      Link Email
                    </button>
                  ) : (
                    <div className="link-form">
                      <input
                        type="email"
                        value={linkEmail}
                        onChange={(e) => setLinkEmail(e.target.value)}
                        placeholder="Enter email"
                        className="profile-input compact"
                        disabled={emailOtpSent}
                      />
                      {emailOtpSent && (
                        <input
                          type="text"
                          value={linkEmailOtp}
                          onChange={(e) =>
                            setLinkEmailOtp(
                              e.target.value.replace(/\D/g, "").slice(0, 6)
                            )
                          }
                          placeholder="Enter OTP"
                          className="profile-input compact otp"
                          maxLength={6}
                          autoFocus
                        />
                      )}
                      <div className="link-actions">
                        {!emailOtpSent ? (
                          <button
                            className="link-btn"
                            onClick={handleSendLinkEmailOtp}
                            disabled={
                              !linkEmail.trim() ||
                              actionLoading === "link-email-send"
                            }
                          >
                            {actionLoading === "link-email-send"
                              ? "Sending..."
                              : "Send OTP"}
                          </button>
                        ) : (
                          <button
                            className="link-btn"
                            onClick={handleVerifyLinkEmail}
                            disabled={
                              linkEmailOtp.length !== 6 ||
                              actionLoading === "link-email-verify"
                            }
                          >
                            {actionLoading === "link-email-verify"
                              ? "Verifying..."
                              : "Verify & Link"}
                          </button>
                        )}
                        <button
                          className="cancel-link-btn"
                          onClick={() => {
                            setShowLinkEmail(false);
                            setLinkEmail("");
                            setLinkEmailOtp("");
                            setEmailOtpSent(false);
                          }}
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>

            {/* ── Phone Card ──────────────────────── */}
            <div
              className={`account-card ${profile.phone ? "connected" : "disconnected"}`}
            >
              <div className="account-card-header">
                <span className="account-icon">📱</span>
                <div className="account-info">
                  <h3>Phone</h3>
                  <p>
                    {profile.phone || "Not connected"}
                  </p>
                </div>
                <span
                  className={`status-badge ${profile.phone ? "active" : "inactive"}`}
                >
                  {profile.phone ? "✓" : "✕"}
                </span>
              </div>
              {profile.phone ? (
                <button
                  className="unlink-btn"
                  onClick={handleUnlinkPhone}
                  disabled={actionLoading === "unlink-phone"}
                >
                  {actionLoading === "unlink-phone"
                    ? "Unlinking..."
                    : "Unlink Phone"}
                </button>
              ) : (
                <>
                  {!showLinkPhone ? (
                    <button
                      className="link-btn"
                      onClick={() => setShowLinkPhone(true)}
                    >
                      Link Phone
                    </button>
                  ) : (
                    <div className="link-form">
                      <input
                        type="tel"
                        value={linkPhone}
                        onChange={(e) => setLinkPhone(e.target.value)}
                        placeholder="+91XXXXXXXXXX"
                        className="profile-input compact"
                        disabled={phoneOtpSent}
                      />
                      {phoneOtpSent && (
                        <input
                          type="text"
                          value={linkPhoneOtp}
                          onChange={(e) =>
                            setLinkPhoneOtp(
                              e.target.value.replace(/\D/g, "").slice(0, 6)
                            )
                          }
                          placeholder="Enter OTP"
                          className="profile-input compact otp"
                          maxLength={6}
                          autoFocus
                        />
                      )}
                      <div className="link-actions">
                        {!phoneOtpSent ? (
                          <button
                            className="link-btn"
                            onClick={handleSendLinkPhoneOtp}
                            disabled={
                              !linkPhone.trim() ||
                              actionLoading === "link-phone-send"
                            }
                          >
                            {actionLoading === "link-phone-send"
                              ? "Sending..."
                              : "Send OTP"}
                          </button>
                        ) : (
                          <button
                            className="link-btn"
                            onClick={handleVerifyLinkPhone}
                            disabled={
                              linkPhoneOtp.length !== 6 ||
                              actionLoading === "link-phone-verify"
                            }
                          >
                            {actionLoading === "link-phone-verify"
                              ? "Verifying..."
                              : "Verify & Link"}
                          </button>
                        )}
                        <button
                          className="cancel-link-btn"
                          onClick={() => {
                            setShowLinkPhone(false);
                            setLinkPhone("");
                            setLinkPhoneOtp("");
                            setPhoneOtpSent(false);
                          }}
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>

            {/* ── Google Card ─────────────────────── */}
            <div
              className={`account-card ${profile.googleId ? "connected" : "disconnected"}`}
            >
              <div className="account-card-header">
                <span className="account-icon">🔵</span>
                <div className="account-info">
                  <h3>Google</h3>
                  <p>
                    {profile.googleId ? "Connected" : "Not connected"}
                  </p>
                </div>
                <span
                  className={`status-badge ${profile.googleId ? "active" : "inactive"}`}
                >
                  {profile.googleId ? "✓" : "✕"}
                </span>
              </div>
              {profile.googleId ? (
                <button
                  className="unlink-btn"
                  onClick={handleUnlinkGoogle}
                  disabled={actionLoading === "unlink-google"}
                >
                  {actionLoading === "unlink-google"
                    ? "Unlinking..."
                    : "Unlink Google"}
                </button>
              ) : (
                <div className="google-link-wrapper">
                  <GoogleLogin
                    onSuccess={handleLinkGoogle}
                    onError={() => showMessage("Google linking failed", "error")}
                    theme="outline"
                    size="medium"
                    text="signin_with"
                    shape="pill"
                  />
                </div>
              )}
            </div>
          </div>
        </div>

        {/* ── Account Info ────────────────────────────── */}
        <div className="profile-section account-meta">
          <h2>Account</h2>
          <div className="profile-details">
            <div className="detail-row">
              <span className="detail-label">Auth Provider</span>
              <span className="detail-value badge">
                {profile.authProvider || "Unknown"}
              </span>
            </div>
            <div className="detail-row">
              <span className="detail-label">User ID</span>
              <span className="detail-value muted">#{profile.id}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Profile;
