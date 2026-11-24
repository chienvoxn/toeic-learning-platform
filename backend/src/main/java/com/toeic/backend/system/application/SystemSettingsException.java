package com.toeic.backend.system.application;

public class SystemSettingsException extends RuntimeException {
    private final int status;private final String code;
    public SystemSettingsException(int status,String code,String message){super(message);this.status=status;this.code=code;}
    public int status(){return status;}public String code(){return code;}
    public static SystemSettingsException invalid(){return new SystemSettingsException(400,"VALIDATION_ERROR","Giá trị hoặc phiên bản cấu hình không hợp lệ.");}
    public static SystemSettingsException missing(){return new SystemSettingsException(404,"SETTING_NOT_FOUND","Không có cấu hình được phép quản trị với key này.");}
    public static SystemSettingsException stale(){return new SystemSettingsException(409,"SETTING_VERSION_CONFLICT","Cấu hình đã được cập nhật. Hãy tải lại trước khi lưu.");}
    public static SystemSettingsException unavailable(){return new SystemSettingsException(503,"SETTINGS_UNAVAILABLE","Chưa đọc/lưu được cấu hình. Vui lòng thử lại.");}
}
