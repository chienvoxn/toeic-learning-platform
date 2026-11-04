import { useState } from 'react';

export default function useDemoLogin({ navigate }) {
  const [emailMode, setEmailMode] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleLogin = () => {
    // Navigate to home upon simulated login
    navigate('home');
  };

  const handleGoogleLogin = () => {
    // Navigate to home upon simulated Google login
    navigate('home');
  };

  return { emailMode, setEmailMode, email, setEmail, password, setPassword, handleLogin, handleGoogleLogin };
}
