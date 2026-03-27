package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class ListItem {
    private String listId;
    private String taskId;

    @DynamoDbPartitionKey
    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }

    @DynamoDbSortKey
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
}
