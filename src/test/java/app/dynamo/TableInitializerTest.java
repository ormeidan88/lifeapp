package app.dynamo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableInitializerTest {

    DynamoDbClient dynamoDb;
    TableInitializer initializer;

    @BeforeEach
    void setup() {
        dynamoDb = mock(DynamoDbClient.class);
        initializer = new TableInitializer(dynamoDb);
    }

    @Test
    void skipsWhenNotDevMode() {
        initializer.devMode = false;
        initializer.init();
        verify(dynamoDb, never()).listTables();
    }

    @Test
    void createsAllTablesWhenEmpty() {
        initializer.devMode = true;
        when(dynamoDb.listTables()).thenReturn(ListTablesResponse.builder().tableNames(List.of()).build());
        when(dynamoDb.createTable(any(CreateTableRequest.class)))
                .thenReturn(CreateTableResponse.builder().build());

        initializer.init();

        // Should create 13 tables
        verify(dynamoDb, times(13)).createTable(any(CreateTableRequest.class));
    }

    @Test
    void skipsExistingTables() {
        initializer.devMode = true;
        when(dynamoDb.listTables()).thenReturn(ListTablesResponse.builder()
                .tableNames(List.of("inbox_items", "projects", "tasks", "lists", "list_items",
                        "pages", "page_content", "habits", "habit_entries", "decks", "cards", "books", "calendar_events"))
                .build());

        initializer.init();

        verify(dynamoDb, never()).createTable(any(CreateTableRequest.class));
    }

    @Test
    void createsOnlyMissingTables() {
        initializer.devMode = true;
        when(dynamoDb.listTables()).thenReturn(ListTablesResponse.builder()
                .tableNames(List.of("inbox_items", "projects")) // only 2 exist
                .build());
        when(dynamoDb.createTable(any(CreateTableRequest.class)))
                .thenReturn(CreateTableResponse.builder().build());

        initializer.init();

        verify(dynamoDb, times(11)).createTable(any(CreateTableRequest.class));
    }
}
