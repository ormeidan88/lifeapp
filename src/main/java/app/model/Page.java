package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Page {
    private String userId;
    private String pageId;
    private String title;
    private String parentPageId;
    private String ownerType; // standalone, project, book
    private String ownerId;
    private String createdAt;
    private String updatedAt;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSortKey
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    @DynamoDbSecondaryPartitionKey(indexNames = "parentPageId-index")
    public String getParentPageId() { return parentPageId; }
    public void setParentPageId(String parentPageId) { this.parentPageId = parentPageId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "ownerType-ownerId-index")
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    @DynamoDbSecondarySortKey(indexNames = "ownerType-ownerId-index")
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
