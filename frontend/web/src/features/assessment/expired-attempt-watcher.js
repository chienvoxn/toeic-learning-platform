// One read at a time. A network failure never means the worker or an in-flight save failed to commit.
export function watchExpiredAttempt({ read, onSnapshot, onError, schedule = setTimeout, cancel = clearTimeout, pollMs = 2000 }) {
  let stopped = false, timer = null, delay = pollMs;
  async function poll() {
    try {
      const snapshot = await read();
      if (stopped) return;
      onSnapshot(snapshot); delay = pollMs;
      if (snapshot.status === 'GRADED') { stopped = true; return; }
    } catch (error) {
      if (stopped) return;
      onError(error); delay = Math.min(15000, delay * 2);
    }
    if (!stopped) timer = schedule(poll, delay);
  }
  void poll();
  return () => { stopped = true; if (timer !== null) cancel(timer); };
}
