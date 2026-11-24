package com.toeic.backend.system.infrastructure;

import com.toeic.backend.system.application.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Đọc/ghi đúng key đã định nghĩa; revision CAS và khóa row không làm lộ key/giá trị khác. */
@Repository
public class SystemSettingsRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public SystemSettingsRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public SystemSettingView read(SettingDefinition definition,boolean lock){
        var rows=jdbc.query("SELECT setting_value,revision,updated_at,updated_by FROM system_settings WHERE setting_key=?"+(lock?" FOR UPDATE":""),(rs,n) -> {
            var value=json.readTree(rs.getString("setting_value"));
            if(!value.isIntegralNumber() || !value.canConvertToInt() || !definition.accepts(value.asInt()) || rs.getLong("revision")<0)throw SystemSettingsException.unavailable();
            return new SystemSettingView(definition.key(),"INTEGER",value.asInt(),definition.defaultValue(),definition.minValue(),definition.maxValue(),rs.getLong("revision"),
                    rs.getObject("updated_at",OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC),rs.getObject("updated_by",UUID.class));
        },definition.key());
        return rows.stream().findFirst().orElseThrow(SystemSettingsException::unavailable);
    }
    public void update(SettingDefinition key,int value,long expected,UUID actor,OffsetDateTime now){
        if(jdbc.update("UPDATE system_settings SET setting_value=CAST(? AS jsonb),revision=revision+1,updated_by=?,updated_at=? WHERE setting_key=? AND revision=?",
                Integer.toString(value),actor,now,key.key(),expected)!=1)throw SystemSettingsException.stale();
    }
}
