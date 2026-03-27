package app.dynamo;

import app.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.stream.Stream;

import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class SeedDataTest {

    SeedData seedData;
    DynamoDbTable<InboxItem> inboxTable;
    DynamoDbTable<Project> projectsTable;
    DynamoDbTable<Task> tasksTable;
    DynamoDbTable<ListEntity> listsTable;
    DynamoDbTable<ListItem> listItemsTable;
    DynamoDbTable<Page> pagesTable;
    DynamoDbTable<PageContent> pageContentTable;
    DynamoDbTable<Habit> habitsTable;
    DynamoDbTable<HabitEntry> habitEntriesTable;
    DynamoDbTable<Deck> decksTable;
    DynamoDbTable<Card> cardsTable;
    DynamoDbTable<Book> booksTable;
    DynamoDbTable<CalendarEvent> calendarTable;

    @BeforeEach
    void setup() {
        seedData = new SeedData();
        seedData.devMode = true;
        seedData.tableInitializer = mock(TableInitializer.class);
        inboxTable = mock(DynamoDbTable.class); seedData.inboxTable = inboxTable;
        projectsTable = mock(DynamoDbTable.class); seedData.projectsTable = projectsTable;
        tasksTable = mock(DynamoDbTable.class); seedData.tasksTable = tasksTable;
        listsTable = mock(DynamoDbTable.class); seedData.listsTable = listsTable;
        listItemsTable = mock(DynamoDbTable.class); seedData.listItemsTable = listItemsTable;
        pagesTable = mock(DynamoDbTable.class); seedData.pagesTable = pagesTable;
        pageContentTable = mock(DynamoDbTable.class); seedData.pageContentTable = pageContentTable;
        habitsTable = mock(DynamoDbTable.class); seedData.habitsTable = habitsTable;
        habitEntriesTable = mock(DynamoDbTable.class); seedData.habitEntriesTable = habitEntriesTable;
        decksTable = mock(DynamoDbTable.class); seedData.decksTable = decksTable;
        cardsTable = mock(DynamoDbTable.class); seedData.cardsTable = cardsTable;
        booksTable = mock(DynamoDbTable.class); seedData.booksTable = booksTable;
        calendarTable = mock(DynamoDbTable.class); seedData.calendarTable = calendarTable;
    }

    private void mockEmptyQuery(DynamoDbTable<?> table) {
        var pageIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        var sdkIterable = mock(software.amazon.awssdk.core.pagination.sync.SdkIterable.class);
        when(sdkIterable.stream()).thenReturn(Stream.empty());
        when(pageIterable.items()).thenReturn(sdkIterable);
        when(table.query(any(QueryConditional.class))).thenReturn(pageIterable);
    }

    @Test
    void skipsWhenNotDevMode() {
        seedData.devMode = false;
        seedData.init();
        verifyNoInteractions(inboxTable);
    }

    @Test
    void seedsAllModulesWhenEmpty() {
        mockEmptyQuery(inboxTable);
        seedData.init();
        // Verify each table got writes — use atLeastOnce since exact counts vary
        verify(inboxTable, atLeastOnce()).putItem((InboxItem) any());
        verify(listsTable, atLeastOnce()).putItem((ListEntity) any());
        verify(projectsTable, atLeastOnce()).putItem((Project) any());
        verify(tasksTable, atLeastOnce()).putItem((Task) any());
        verify(pagesTable, atLeastOnce()).putItem((Page) any());
        verify(pageContentTable, atLeastOnce()).putItem((PageContent) any());
        verify(habitsTable, atLeastOnce()).putItem((Habit) any());
        verify(habitEntriesTable, atLeastOnce()).putItem((HabitEntry) any());
        verify(decksTable, atLeastOnce()).putItem((Deck) any());
        verify(cardsTable, atLeastOnce()).putItem((Card) any());
        verify(booksTable, atLeastOnce()).putItem((Book) any());
        verify(calendarTable, atLeastOnce()).putItem((CalendarEvent) any());
        verify(listItemsTable, atLeastOnce()).putItem((ListItem) any());
    }

    @Test
    void skipsWhenAlreadySeeded() {
        var existingItem = new InboxItem();
        existingItem.setUserId("default"); existingItem.setItemId("x");
        var pageIterable = mock(software.amazon.awssdk.enhanced.dynamodb.model.PageIterable.class);
        var sdkIterable = mock(software.amazon.awssdk.core.pagination.sync.SdkIterable.class);
        when(sdkIterable.stream()).thenReturn(Stream.of(existingItem));
        when(pageIterable.items()).thenReturn(sdkIterable);
        when(inboxTable.query(any(QueryConditional.class))).thenReturn(pageIterable);

        seedData.init();
        verify(projectsTable, never()).putItem((Project) any());
    }
}
