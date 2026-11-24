package com.toeic.backend.system.application;

import java.util.*;

/** Danh sách sản phẩm đã chốt; không phải cấu hình môi trường hay registry động. */
public enum SettingDefinition {
    RECOMMENDATION_LIMIT("vocabulary.recommendations.defaultLimit",10,1,100),
    PROGRESS_DAYS("learning.progress.defaultDays",7,1,90);
    private final String key;
    private final int defaultValue,minValue,maxValue;
    SettingDefinition(String key,int value,int min,int max){this.key=key;this.defaultValue=value;this.minValue=min;this.maxValue=max;}
    public String key(){return key;}
    public int defaultValue(){return defaultValue;}
    public int minValue(){return minValue;}
    public int maxValue(){return maxValue;}
    public boolean accepts(int value){return value>=minValue && value<=maxValue;}
    public static Optional<SettingDefinition> find(String key){return Arrays.stream(values()).filter(v -> v.key.equals(key)).findFirst();}
    public static SettingDefinition require(String key){return find(key).orElseThrow(SystemSettingsException::missing);}
}
