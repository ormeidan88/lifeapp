package app.config;

import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthConfigTest {

    @Test
    void devModeUsesProperties() {
        var config = new AuthConfig();
        config.devMode = true;
        config.devPasswordHash = "hash123";
        config.devApiKey = "key123";
        config.ssmClientInstance = mock(Instance.class);

        config.init();

        assertThat(config.getPasswordHash()).isEqualTo("hash123");
        assertThat(config.getApiKey()).isEqualTo("key123");
        assertThat(config.isDevMode()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void prodModeUsesSSM() {
        var config = new AuthConfig();
        config.devMode = false;
        config.devPasswordHash = "";
        config.devApiKey = "";

        SsmClient ssm = mock(SsmClient.class);
        when(ssm.getParameter(any(java.util.function.Consumer.class)))
                .thenReturn(GetParameterResponse.builder().parameter(Parameter.builder().value("ssm-hash").build()).build())
                .thenReturn(GetParameterResponse.builder().parameter(Parameter.builder().value("ssm-key").build()).build());

        Instance<SsmClient> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.get()).thenReturn(ssm);
        config.ssmClientInstance = instance;

        config.init();

        assertThat(config.getPasswordHash()).isEqualTo("ssm-hash");
        assertThat(config.getApiKey()).isEqualTo("ssm-key");
        assertThat(config.isDevMode()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void prodModeThrowsWhenNoSSM() {
        var config = new AuthConfig();
        config.devMode = false;
        Instance<SsmClient> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(true);
        config.ssmClientInstance = instance;

        assertThatThrownBy(config::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SSM client required");
    }
}
