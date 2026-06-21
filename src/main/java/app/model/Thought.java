package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Thought {
    private String userId;
    private String thoughtId;
    private String title;
    private String kind; // daily, subject
    private String parentThoughtId;
    private String day; // DD/MM/YYYY for daily thoughts, null for subjects
    private String createdAt;
    private String updatedAt;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSortKey
    public String getThoughtId() { return thoughtId; }
    public void setThoughtId(String thoughtId) { this.thoughtId = thoughtId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    @DynamoDbSecondaryPartitionKey(indexNames = "parentThoughtId-index")
    public String getParentThoughtId() { return parentThoughtId; }
    public void setParentThoughtId(String parentThoughtId) { this.parentThoughtId = parentThoughtId; }

    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
