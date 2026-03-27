package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.util.List;

@DynamoDbBean
public class Book {
    private String userId;
    private String bookId;
    private String googleBooksId;
    private String title;
    private List<String> authors;
    private String coverUrl;
    private String pageId;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSortKey
    public String getBookId() { return bookId; }
    public void setBookId(String bookId) { this.bookId = bookId; }

    public String getGoogleBooksId() { return googleBooksId; }
    public void setGoogleBooksId(String googleBooksId) { this.googleBooksId = googleBooksId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getAuthors() { return authors; }
    public void setAuthors(List<String> authors) { this.authors = authors; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
