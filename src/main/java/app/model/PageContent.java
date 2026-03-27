package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class PageContent {
    private String pageId;
    private String version;
    private String content; // JSON string of TipTap doc

    @DynamoDbPartitionKey
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }

    @DynamoDbSortKey
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
