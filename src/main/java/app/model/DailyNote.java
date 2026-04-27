package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class DailyNote {
    private String userId;
    private String date;
    private String content;
    private String updatedAt;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSortKey
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
