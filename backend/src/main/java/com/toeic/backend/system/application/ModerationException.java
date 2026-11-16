package com.toeic.backend.system.application;

public class ModerationException extends RuntimeException {
    private final int status;private final String code;
    public ModerationException(int status,String code,String message){super(message);this.status=status;this.code=code;}
    public int status(){return status;}public String code(){return code;}
    public static ModerationException invalid(){return new ModerationException(400,"VALIDATION_ERROR","Du lieu moderation khong hop le.");}
    public static ModerationException missing(){return new ModerationException(404,"MODERATION_NOT_FOUND","Khong tim thay phien ban trong pham vi.");}
    public static ModerationException conflict(String code){return new ModerationException(409,code,"Trang thai, revision hoac quyen tai su dung khong con hop le.");}
}
