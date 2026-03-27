package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Task {
    private String projectId;
    private String taskId;
    private String title;
    private Boolean done;
    private String parentTaskId;
    private Integer position;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    @DynamoDbSortKey
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Boolean getDone() { return done; }
    public void setDone(Boolean done) { this.done = done; }

    @DynamoDbSecondaryPartitionKey(indexNames = "parentTaskId-index")
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
