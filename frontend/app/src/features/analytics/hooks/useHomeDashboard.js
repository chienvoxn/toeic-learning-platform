import { useState } from 'react';

export default function useHomeDashboard() {
  const [checkedIn, setCheckedIn] = useState(false);
  const [streakDays, setStreakDays] = useState(14);

  const handleCheckin = () => {
    if (!checkedIn) {
      setCheckedIn(true);
      setStreakDays(prev => prev + 1);
    }
  };

  return { checkedIn, streakDays, handleCheckin };
}
