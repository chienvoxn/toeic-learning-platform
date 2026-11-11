export function authErrorMessage(error) {
  const messages = {
    INVALID_CREDENTIALS: 'Email hoặc mật khẩu không đúng, hoặc tài khoản chưa xác minh/chưa sẵn sàng.',
    INVALID_TOKEN: 'Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.',
    UNAUTHENTICATED: 'Vui lòng đăng nhập lại.',
    CURRENT_PASSWORD_INVALID: 'Mật khẩu hiện tại không đúng. Phiên đăng nhập vẫn được giữ.',
    OTP_INVALID: 'Email hoặc mã OTP không đúng.',
    OTP_EXPIRED: 'Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.',
    OTP_UNAVAILABLE: 'Mã OTP đã dùng hoặc vượt số lần thử. Vui lòng yêu cầu mã mới.',
    ACCOUNT_NOT_PENDING: 'Tài khoản không còn chờ xác minh. Hãy thử đăng nhập.',
    OTP_RESEND_TOO_SOON: 'Bạn gửi lại quá sớm. Vui lòng chờ ít nhất 60 giây giữa các lần gửi.',
    EMAIL_EXISTS: 'Email đã được sử dụng. Bạn có thể đăng nhập hoặc xác minh email.',
    MAIL_UNAVAILABLE: 'Máy chủ chưa gửi được email. Vui lòng thử lại sau hoặc kiểm tra SMTP local.',
    PASSWORD_TOO_LONG: 'Mật khẩu không được vượt quá 72 byte UTF-8.',
    VALIDATION_ERROR: 'Dữ liệu không hợp lệ. Kiểm tra tên, email, mật khẩu và điểm mục tiêu.',
    FORBIDDEN: 'Tài khoản hiện không có quyền thực hiện thao tác này.',
  };
  return messages[error?.code] || (error?.status ? 'Không thể xử lý yêu cầu. Vui lòng thử lại.'
    : 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối và thử lại.');
}

export function passwordError(value, minimum = 8) {
  return !value.trim() || value.length < minimum || value.length > 72 || new TextEncoder().encode(value).length > 72
    ? `Mật khẩu cần ${minimum}–72 ký tự và tối đa 72 byte UTF-8.` : '';
}
