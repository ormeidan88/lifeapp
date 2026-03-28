package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Persona {
    private String userId;
    private String personaId;
    private String name;
    private String systemPrompt;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }

    @DynamoDbSortKey
    public String getPersonaId() { return personaId; }
    public void setPersonaId(String v) { this.personaId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String v) { this.systemPrompt = v; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { this.createdAt = v; }
}
