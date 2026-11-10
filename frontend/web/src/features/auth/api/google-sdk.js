let sdk;
// Chỉ tải SDK chính thức; không lưu credential hoặc giải mã JWT để tự tin profile.
export function loadGoogleSdk() {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id);
  if (sdk) return sdk;
  sdk = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client?hl=vi';
    script.async = true;
    const timeout = setTimeout(() => { script.remove(); sdk = undefined; reject(new Error('Không tải được đăng nhập Google. Vui lòng kiểm tra mạng và thử lại.')); }, 15000);
    script.onload = () => {
      clearTimeout(timeout);
      if (window.google?.accounts?.id) resolve(window.google.accounts.id);
      else { script.remove(); sdk = undefined; reject(new Error('SDK Google chưa sẵn sàng.')); }
    };
    script.onerror = () => { clearTimeout(timeout); script.remove(); sdk = undefined; reject(new Error('Không tải được Google. Vui lòng thử lại hoặc đăng nhập bằng email.')); };
    document.head.append(script);
  });
  return sdk;
}
