import { useState } from 'react';

export default function usePreferences() {
  const [dailyGoal, setDailyGoal] = useState(15);
  const [accent, setAccent] = useState('us');
  const [notifications, setNotifications] = useState(true);

  return { dailyGoal, setDailyGoal, accent, setAccent, notifications, setNotifications };
}
