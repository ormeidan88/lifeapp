package app.dynamo;

import app.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@ApplicationScoped
public class Tables {

    private final DynamoDbEnhancedClient enhanced;

    public Tables(DynamoDbClient client) {
        this.enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }

    @Produces @Named("inboxItems")
    DynamoDbTable<InboxItem> inboxItems() { return enhanced.table("inbox_items", TableSchema.fromBean(InboxItem.class)); }

    @Produces @Named("projects")
    DynamoDbTable<Project> projects() { return enhanced.table("projects", TableSchema.fromBean(Project.class)); }

    @Produces @Named("tasks")
    DynamoDbTable<Task> tasks() { return enhanced.table("tasks", TableSchema.fromBean(Task.class)); }

    @Produces @Named("lists")
    DynamoDbTable<ListEntity> lists() { return enhanced.table("lists", TableSchema.fromBean(ListEntity.class)); }

    @Produces @Named("listItems")
    DynamoDbTable<ListItem> listItems() { return enhanced.table("list_items", TableSchema.fromBean(ListItem.class)); }

    @Produces @Named("pages")
    DynamoDbTable<Page> pages() { return enhanced.table("pages", TableSchema.fromBean(Page.class)); }

    @Produces @Named("pageContent")
    DynamoDbTable<PageContent> pageContent() { return enhanced.table("page_content", TableSchema.fromBean(PageContent.class)); }

    @Produces @Named("habits")
    DynamoDbTable<Habit> habits() { return enhanced.table("habits", TableSchema.fromBean(Habit.class)); }

    @Produces @Named("habitEntries")
    DynamoDbTable<HabitEntry> habitEntries() { return enhanced.table("habit_entries", TableSchema.fromBean(HabitEntry.class)); }

    @Produces @Named("decks")
    DynamoDbTable<Deck> decks() { return enhanced.table("decks", TableSchema.fromBean(Deck.class)); }

    @Produces @Named("cards")
    DynamoDbTable<Card> cards() { return enhanced.table("cards", TableSchema.fromBean(Card.class)); }

    @Produces @Named("books")
    DynamoDbTable<Book> books() { return enhanced.table("books", TableSchema.fromBean(Book.class)); }

    @Produces @Named("calendarEvents")
    DynamoDbTable<CalendarEvent> calendarEvents() { return enhanced.table("calendar_events", TableSchema.fromBean(CalendarEvent.class)); }

    @Produces @Named("waitingForItems")
    DynamoDbTable<WaitingForItem> waitingForItems() { return enhanced.table("waiting_for_items", TableSchema.fromBean(WaitingForItem.class)); }

    @Produces @Named("dailyNotes")
    DynamoDbTable<DailyNote> dailyNotes() { return enhanced.table("daily_notes", TableSchema.fromBean(DailyNote.class)); }

    @Produces @Named("personas")
    DynamoDbTable<Persona> personas() { return enhanced.table("personas", TableSchema.fromBean(Persona.class)); }
}
