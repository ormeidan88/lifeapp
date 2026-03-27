package app.dynamo;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TablesTest {

    @Test
    void producesAllTables() {
        DynamoDbClient client = mock(DynamoDbClient.class);
        var tables = new Tables(client);

        assertThat(tables.inboxItems()).isNotNull();
        assertThat(tables.projects()).isNotNull();
        assertThat(tables.tasks()).isNotNull();
        assertThat(tables.lists()).isNotNull();
        assertThat(tables.listItems()).isNotNull();
        assertThat(tables.pages()).isNotNull();
        assertThat(tables.pageContent()).isNotNull();
        assertThat(tables.habits()).isNotNull();
        assertThat(tables.habitEntries()).isNotNull();
        assertThat(tables.decks()).isNotNull();
        assertThat(tables.cards()).isNotNull();
        assertThat(tables.books()).isNotNull();
        assertThat(tables.calendarEvents()).isNotNull();
    }
}
