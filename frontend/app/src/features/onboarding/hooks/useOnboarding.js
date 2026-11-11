import { useState } from "react";

export default function useOnboarding({ navigate }) {
  const [step, setStep] = useState(1);
  const [proficiency, setProficiency] = useState("basic");
  const [dailyGoal, setDailyGoal] = useState(5);

  const handleFinish = () => {
    navigate("home");
  };

  return {
    step,
    setStep,
    proficiency,
    setProficiency,
    dailyGoal,
    setDailyGoal,
    handleFinish,
  };
}
