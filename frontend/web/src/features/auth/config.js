// Auth can use Identity while the remaining modules stay in demo mode.
export const authMode = import.meta.env?.VITE_AUTH_MODE || 'remote';
if (!['remote', 'mock'].includes(authMode)) throw new Error('VITE_AUTH_MODE must be remote or mock');
export const isRemoteAuth = authMode === 'remote';
export const demoModules = import.meta.env?.VITE_USE_MOCK === 'true';
