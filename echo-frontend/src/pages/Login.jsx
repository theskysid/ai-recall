import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { GoogleLogin } from "@react-oauth/google";
import { authService } from "../services/authService.js";
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
    <div className="login-container">
      <div className="login-box">
        <div className="login-header">
          <h1>Login</h1>
          <p>Welcome back to Echo</p>
        </div>

        <div className="auth-tabs">
          <button
            className={`auth-tab ${activeTab === "password" ? "active" : ""}`}
            onClick={() => handleTabChange("password")}
          >
            🔒 Password
          </button>
          <button
            className={`auth-tab ${activeTab === "otp" ? "active" : ""}`}
            onClick={() => handleTabChange("otp")}
          >
            🔑 OTP
          </button>
        </div>

        {activeTab === "password" && (
          <form onSubmit={handlePasswordLogin} className="login-form">
            <input
              type="text"
              placeholder="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="auth-input"
              maxLength={20}
              required
              disabled={isLoading}
            />
            <input
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="auth-input"
              maxLength={20}
              required
              disabled={isLoading}
            />
            <button
              type="submit"
              disabled={!username.trim() || !password.trim() || isLoading}
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
            <input
              type="text"
              placeholder="Email or phone number"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              className="auth-input"
              required
              disabled={isLoading || otpSent}
            />
            {otpSent && (
              <div className="otp-section">
                <p className="otp-label">
                  Enter the 6-digit code sent to your email or phone
                </p>
                <input
                  type="text"
                  placeholder="Enter OTP"
                  value={otp}
                  onChange={(e) =>
                    setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))
                  }
                  className="auth-input otp-input"
                  maxLength={6}
                  required
                  disabled={isLoading}
                  autoFocus
                />
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
          <p
            className="auth-message"
            style={{ color: isSuccess ? "#4CAF50" : "#ff6b6b" }}
          >
            {message}
          </p>
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
            shape="pill"
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
  );
};

export default Login;
