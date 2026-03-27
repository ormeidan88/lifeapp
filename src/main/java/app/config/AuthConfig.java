package app.config;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.ssm.SsmClient;

@ApplicationScoped
@Startup
public class AuthConfig {

    private String passwordHash;
    private String apiKey;

    @ConfigProperty(name = "app.auth.dev-mode", defaultValue = "false")
    boolean devMode;

    @ConfigProperty(name = "app.auth.password-hash", defaultValue = "")
    String devPasswordHash;

    @ConfigProperty(name = "app.auth.api-key", defaultValue = "")
    String devApiKey;

    @Inject
    Instance<SsmClient> ssmClientInstance;

    @PostConstruct
    void init() {
        if (devMode) {
            this.passwordHash = devPasswordHash;
            this.apiKey = devApiKey;
        } else {
            if (ssmClientInstance.isUnsatisfied()) {
                throw new IllegalStateException("SSM client required in prod mode");
            }
            SsmClient ssm = ssmClientInstance.get();
            this.passwordHash = ssm.getParameter(r -> r.name("/lifeapp/auth/password-hash").withDecryption(true)).parameter().value();
            this.apiKey = ssm.getParameter(r -> r.name("/lifeapp/auth/api-key").withDecryption(true)).parameter().value();
        }
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isDevMode() {
        return devMode;
    }
}
