import { Navigate } from 'react-router-dom';

// Registration is ACTIVE immediately after the remote V25 policy change.
export default function VerifyEmailPage() { return <Navigate to="/login" replace />; }
