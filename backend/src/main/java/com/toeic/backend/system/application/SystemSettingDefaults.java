package com.toeic.backend.system.application;

import com.toeic.backend.system.infrastructure.SystemSettingsRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consumer nội bộ chỉ dùng key enum; tham số có mặt phải hợp lệ, không bị thay bằng default. */
@Service
public class SystemSettingDefaults {
    private final SystemSettingsRepository repository;
    public SystemSettingDefaults(SystemSettingsRepository repository){this.repository=repository;}
    @Transactional(readOnly=true)
    public int resolve(SettingDefinition definition,List<String> values){
        if(values==null)return repository.read(definition,false).value();
        if(values.size()!=1 || values.getFirst()==null || values.getFirst().isBlank())throw SystemSettingsException.invalid();
        try {int value=Integer.parseInt(values.getFirst().strip());if(!definition.accepts(value))throw SystemSettingsException.invalid();return value;}
        catch(NumberFormatException exception){throw SystemSettingsException.invalid();}
    }
}
