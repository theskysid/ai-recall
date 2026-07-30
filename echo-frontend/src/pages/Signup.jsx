import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { GoogleLogin } from "@react-oauth/google";
import { authService } from "../services/authService.js";
import "../styles/Signup.css";

const Signup = () => {
  const [username, setUsername] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
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

  const handleSendOtp = async (e) => {
    e.preventDefault();
    setMessage("");
    setIsLoading(true);
    try {
      if (!username.trim()) {
        throw new Error("Username is required");
      }
      if (!password.trim()) {
        throw new Error("Password is required");
      }
      await authService.sendSignupOtp(identifier);
      setOtpSent(true);
      setCountdown(60);
      setMessage("OTP sent! Check your email or phone.");
    } catch (error) {
      setMessage(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifySignup = async (e) => {
    e.preventDefault();
    setMessage("");
    setIsLoading(true);
    try {
      const result = await authService.verifySignupOtp({
        username,
        identifier,
        password,
        otp,
      });
      if (result.success) {
        setMessage("Account created! Redirecting...");
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
        setMessage("Account created! Redirecting...");
        setTimeout(() => navigate("/chatarea"), 1500);
      }
    } catch (error) {
      setMessage(error.message);
    } finally {
      setIsLoading(false);
    }
  };

  const isSuccess =
    message.toLowerCase().includes("successfully") ||
    message.toLowerCase().includes("created") ||
    message.toLowerCase().includes("sent");

  return (
    <div className="signup-container">
      <div className="signup-box">
        <div className="signup-header">
          <h1>Sign Up</h1>
          <p>Create an account to start chatting</p>
        </div>

        <div className="sandbox-notice">
          <span className="sandbox-notice-icon">ℹ️</span>
          <p>
            <strong>Note on Phone OTP:</strong> Mobile SMS is sandboxed in Twilio (only registered numbers can receive messages without credits). We recommend using <strong>Email OTP</strong>, Google, or Password sign up instead.
          </p>
        </div>

        <form
          onSubmit={otpSent ? handleVerifySignup : handleSendOtp}
          className="signup-form"
        >
          <input
            type="text"
            placeholder="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="auth-input"
            maxLength={20}
            required
            disabled={isLoading || otpSent}
          />
          <input
            type="text"
            placeholder="Email or phone number"
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            className="auth-input"
            required
            disabled={isLoading || otpSent}
          />
          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="auth-input"
            maxLength={20}
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
                : !username.trim() ||
                  !identifier.trim() ||
                  !password.trim() ||
                  isLoading
            }
            className="auth-submit-btn"
          >
            {isLoading
              ? "Please wait..."
              : otpSent
              ? "Verify & Create Account"
              : "Send OTP"}
          </button>
        </form>

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
            text="signup_with"
            shape="pill"
          />
        </div>

        <div className="toggle-auth">
          <p>
            Already have an account?
            <a href="/login" className="toggle-auth-btn">
              Login
            </a>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Signup;
