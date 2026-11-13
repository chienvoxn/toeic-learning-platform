package com.toeic.backend.system.application;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SystemSettingView(String key,String type,int value,int defaultValue,int minValue,int maxValue,
                                long revision,OffsetDateTime updatedAt,UUID updatedBy) {}
