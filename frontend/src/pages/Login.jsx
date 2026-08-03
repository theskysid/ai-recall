import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { GoogleLogin } from "@react-oauth/google";
import { authService } from "../services/authService.js";
import Icon from "../components/ui/Icon";
import "../styles/Login.css";

const Login = () => {
  const [activeTab, setActiveTab] = useState("password");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [otp, setOtp] = useState("");
  const [message, setMessage] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [otpSent, setOtpSent] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const navigate = useNavigate();

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  const resetFields = useCallback(() => {
    setMessage("");
    setOtp("");
    setOtpSent(false);
    setCountdown(0);
  }, []);

  const handleTabChange = (tab) => {
    setActiveTab(tab);
    resetFields();
    setUsername("");
    setPassword("");
    setIdentifier("");
  };

  const handlePasswordLogin = async (e) => {
    e.preventDefault();
    setMessage("");
    setIsLoading(true);
    try {
      const result = await authService.login(username, password);
      if (result.success) {
        setMessage("Login successful!");
        setTimeout(() => navigate("/chatarea"), 1500);
      }
    } catch (error) {
      setMessage(error.message || "Login failed, please try again.");
    } finally {
      setIsLoading(false);
    }
  };

  const handleSendOtp = async (e) => {
    e.preventDefault();
    setMessage("");
    setIsLoading(true);
    try {
      await authService.sendOtp(identifier);
      setOtpSent(true);
      setCountdown(60);
      setMessage("OTP sent successfully!");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setMessage("");
    setIsLoading(true);
    try {
      const result = await authService.verifyOtp(identifier, otp);
      if (result.success) {
        setMessage("Login successful!");
        setTimeout(() => navigate("/chatarea"), 1500);
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleSuccess = async (credentialResponse) => {
    setMessage("");
    setIsLoading(true);
    try {
      const result = await authService.googleLogin(
        credentialResponse.credential
      );
      if (result.success) {
        setMessage("Login successful!");
        setTimeout(() => navigate("/chatarea"), 1500);
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const isSuccess =
    message.toLowerCase().includes("successful") ||
    message.toLowerCase().includes("sent");

  return (
    <div className="login-container nb">
      <div className="fl-bind" aria-hidden="true" />

      <div className="fl-sheet nb-sheet">
        <div className="login-box">
          <div className="login-header">
            <h1>Welcome back.</h1>
            <p>Sign in to pick up where your channels left off.</p>
          </div>

          <div className="fl-col">
            <div className="auth-tabs">
              <button
                type="button"
                aria-pressed={activeTab === "password"}
                className={`auth-tab ${activeTab === "password" ? "active" : ""}`}
                onClick={() => handleTabChange("password")}
              >
                <Icon name="lock" size={13} />
                Password
              </button>
              <button
                type="button"
                aria-pressed={activeTab === "otp"}
                className={`auth-tab ${activeTab === "otp" ? "active" : ""}`}
                onClick={() => handleTabChange("otp")}
              >
                <Icon name="key" size={13} />
                Email code
              </button>
            </div>

            {activeTab === "password" && (
              <form onSubmit={handlePasswordLogin} className="login-form">
                <div className="fl-field">
                  <label className="fl-label" htmlFor="login-username">
                    Username
                  </label>
                  <input
                    id="login-username"
                    name="username"
                    type="text"
                    autoComplete="username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="fl-input"
                    maxLength={20}
                    required
                    disabled={isLoading}
                  />
                </div>
                <div className="fl-field">
                  <label className="fl-label" htmlFor="login-password">
                    Password
                  </label>
                  <input
                    id="login-password"
                    name="password"
                    type="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="fl-input"
                    maxLength={20}
                    required
                    disabled={isLoading}
                  />
                </div>
                <button
                  type="submit"
                  disabled={!username.trim() || !password.trim() || isLoading}
                  aria-busy={isLoading}
                  className="auth-submit-btn"
                >
                  {isLoading ? "Logging in..." : "Login"}
                </button>
              </form>
            )}

            {activeTab === "otp" && (
              <form
                onSubmit={otpSent ? handleVerifyOtp : handleSendOtp}
                className="login-form"
              >
                <div className="fl-field">
                  <label className="fl-label" htmlFor="login-email">
                    Email
                  </label>
                  <input
                    id="login-email"
                    name="email"
                    type="email"
                    autoComplete="email"
                    value={identifier}
                    onChange={(e) => setIdentifier(e.target.value)}
                    className="fl-input"
                    required
                    disabled={isLoading || otpSent}
                  />
                </div>
                {otpSent && (
                  <div className="otp-section">
                    <div className="fl-field">
                      <label className="fl-label" htmlFor="login-otp">
                        Enter the 6-digit code sent to your email
                      </label>
                      <input
                        id="login-otp"
                        name="otp"
                        type="text"
                        inputMode="numeric"
                        autoComplete="one-time-code"
                        value={otp}
                        onChange={(e) =>
                          setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))
                        }
                        className="fl-input otp-input"
                        maxLength={6}
                        required
                        disabled={isLoading}
                        autoFocus
                      />
                    </div>
                    {countdown > 0 ? (
                      <p className="otp-countdown">
                        Resend OTP in <span>{countdown}s</span>
                      </p>
                    ) : (
                      <button
                        type="button"
                        className="resend-btn"
                        onClick={handleSendOtp}
                        disabled={isLoading}
                      >
                        Resend OTP
                      </button>
                    )}
                  </div>
                )}
                <button
                  type="submit"
                  disabled={
                    otpSent
                      ? otp.length !== 6 || isLoading
                      : !identifier.trim() || isLoading
                  }
                  aria-busy={isLoading}
                  className="auth-submit-btn"
                >
                  {isLoading
                    ? "Please wait..."
                    : otpSent
                    ? "Verify & Login"
                    : "Send OTP"}
                </button>
              </form>
            )}

            {message && (
              <div
                className={`fl-note ${isSuccess ? "is-ok" : "is-error"}`}
                role={isSuccess ? "status" : "alert"}
              >
                <p className="fl-note-text">{message}</p>
                {!isSuccess && (
                  <p className="fl-note-fix">
                    You are not signed in yet. Check the entries above and try
                    again, or use the other sign-in method.
                  </p>
                )}
              </div>
            )}

            <div className="auth-divider">
              <span>or</span>
            </div>

            <div className="google-btn-wrapper">
              <GoogleLogin
                onSuccess={handleGoogleSuccess}
                onError={() => setMessage("Google sign-in failed")}
                theme="outline"
                size="large"
                width="100%"
                text="signin_with"
                shape="rectangular"
              />
            </div>

            <div className="toggle-auth">
              <p>
                Don't have an account?
                <a href="/signup" className="toggle-auth-btn">
                  Sign up
                </a>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
