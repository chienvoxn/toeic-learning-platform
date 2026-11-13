package com.toeic.backend.system.application;

import com.toeic.backend.system.api.SettingUpdateRequest;
import com.toeic.backend.system.infrastructure.SystemSettingsRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemSettingsInputTest {
    @ParameterizedTest @ValueSource(strings={"{}","{\"value\":10}","{\"value\":null,\"expectedRevision\":0}","{\"value\":\"10\",\"expectedRevision\":0}","{\"value\":10.0,\"expectedRevision\":0}","{\"value\":true,\"expectedRevision\":0}","{\"value\":10,\"expectedRevision\":-1}","{\"value\":10,\"expectedRevision\":\"0\"}","{\"value\":10,\"expectedRevision\":0,\"secret\":\"no\"}","{\"value\":10,\"value\":20,\"expectedRevision\":0}","{\"value\":2147483648,\"expectedRevision\":0}","{\"value\":10,\"expectedRevision\":9223372036854775808}"})
    void rejectsCoercionMissingUnknownAndDuplicateFields(String payload){assertThrows(RuntimeException.class,() -> JsonMapper.builder().build().readValue(payload,SettingUpdateRequest.class).validate());}
    @Test void acceptsOnlyTypedValueAndRevision(){assertEquals(new SettingUpdateRequest.Update(11,3),JsonMapper.builder().build().readValue("{\"value\":11,\"expectedRevision\":3}",SettingUpdateRequest.class).validate());}
    @Test void consumerExplicitInputWinsAndEmptyIsNotOmission(){
        var repository=mock(SystemSettingsRepository.class);var defaults=new SystemSettingDefaults(repository);
        assertEquals(12,defaults.resolve(SettingDefinition.PROGRESS_DAYS,List.of("12")));assertEquals(8,defaults.resolve(SettingDefinition.RECOMMENDATION_LIMIT,List.of(" +08 ")));
        for(var value:List.of(List.of(""),List.of("1.5"),List.of("1000"),List.of("2","3")))assertThrows(SystemSettingsException.class,() -> defaults.resolve(SettingDefinition.PROGRESS_DAYS,value));
        verifyNoInteractions(repository);
        var definition=SettingDefinition.PROGRESS_DAYS;
        when(repository.read(definition,false)).thenReturn(new SystemSettingView(definition.key(),"INTEGER",30,7,1,90,0,OffsetDateTime.now(ZoneOffset.UTC),null));
        assertEquals(30,defaults.resolve(definition,null));verify(repository).read(definition,false);
    }
    @Test void registryIsExactlyProductAllowlist(){assertEquals(2,SettingDefinition.values().length);assertThrows(SystemSettingsException.class,() -> SettingDefinition.require("spring.datasource.url"));assertFalse(SettingDefinition.PROGRESS_DAYS.accepts(91));}
}
