package com.yanban.api.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.core.user.UserAccountPolicy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    SysUserSettingsRepository repository;

    @Mock
    UserModelRepository userModelRepository;

    @Mock
    SettingsCryptoService cryptoService;

    @Mock
    ModelDiscoveryService modelDiscoveryService;

    @Mock
    UserAccountPolicy accountPolicy;

    @Mock
    UserSettingsInitializer initializer;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    UserSettingsService service;

    @Test
    void getFallsBackToExistingSettingsWhenConcurrentInsertWins() {
        Long userId = 2L;
        SysUserSettings existing = new SysUserSettings(
                userId,
                UserSettingsService.DEFAULT_PROVIDER,
                null,
                null,
                UserSettingsService.DEFAULT_DEEPSEEK_MODEL,
                UserSettingsService.DEFAULT_GLM_MODEL,
                null,
                "[]",
                "[]",
                UserSettingsService.DEFAULT_TEMPERATURE,
                UserSettingsService.DEFAULT_MAX_STEPS,
                UserSettingsService.DEFAULT_RAG_ENABLED
        );

        when(repository.findById(userId)).thenReturn(Optional.empty(), Optional.of(existing));
        when(initializer.createDefaultSettings(eq(userId), any(SysUserSettings.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(userModelRepository.findByUserIdOrderBySortOrderAscIdAsc(userId)).thenReturn(List.of());

        UserSettingsResponse response = service.get(userId);

        assertThat(response.defaultProvider()).isEqualTo(UserSettingsService.DEFAULT_PROVIDER);
        verify(initializer).createDefaultSettings(eq(userId), any(SysUserSettings.class));
    }
}
