package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class ThoughtContent {
    private String thoughtId;
    private String version;
    private String content; // JSON string of TipTap doc

    @DynamoDbPartitionKey
    public String getThoughtId() { return thoughtId; }
    public void setThoughtId(String thoughtId) { this.thoughtId = thoughtId; }

    @DynamoDbSortKey
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
