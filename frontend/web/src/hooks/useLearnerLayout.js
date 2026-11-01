import { useContext } from 'react';
import { LearnerLayoutContext } from '@/app/layout-context';

export function useLearnerLayout() {
  const layout = useContext(LearnerLayoutContext);
  if (!layout) throw new Error('useLearnerLayout requires AppProviders');
  return layout;
}
