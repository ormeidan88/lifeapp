package app.dynamo;

import app.model.*;
import com.github.f4b6a3.ulid.UlidCreator;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

@ApplicationScoped
@Startup
public class SeedData {

    private static final Logger LOG = Logger.getLogger(SeedData.class);
    private static final String USER = "default";
    private final Random rng = new Random(42);

    @ConfigProperty(name = "app.auth.dev-mode", defaultValue = "false")
    boolean devMode;

    @Inject TableInitializer tableInitializer; // ensures tables are created first

    @Inject @Named("inboxItems") DynamoDbTable<InboxItem> inboxTable;
    @Inject @Named("projects") DynamoDbTable<Project> projectsTable;
    @Inject @Named("tasks") DynamoDbTable<Task> tasksTable;
    @Inject @Named("lists") DynamoDbTable<ListEntity> listsTable;
    @Inject @Named("listItems") DynamoDbTable<ListItem> listItemsTable;
    @Inject @Named("pages") DynamoDbTable<Page> pagesTable;
    @Inject @Named("pageContent") DynamoDbTable<PageContent> pageContentTable;
    @Inject @Named("habits") DynamoDbTable<Habit> habitsTable;
    @Inject @Named("habitEntries") DynamoDbTable<HabitEntry> habitEntriesTable;
    @Inject @Named("decks") DynamoDbTable<Deck> decksTable;
    @Inject @Named("cards") DynamoDbTable<Card> cardsTable;
    @Inject @Named("books") DynamoDbTable<Book> booksTable;
    @Inject @Named("calendarEvents") DynamoDbTable<CalendarEvent> calendarTable;

    @PostConstruct
    void init() {
        if (!devMode) return;
        // Check if already seeded
        if (inboxTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build())).items().stream().findAny().isPresent()) {
            LOG.info("Dev mode: seed data already exists, skipping.");
            return;
        }
        LOG.info("Dev mode: loading seed data...");
        seedInbox();
        var listIds = seedLists();
        var projectIds = seedProjects();
        seedTasks(projectIds, listIds);
        seedStandalonePages();
        seedHabits();
        seedDecks();
        seedBooks();
        seedCalendar();
        LOG.info("Dev mode: seed data loaded.");
    }

    private void seedInbox() {
        for (String text : List.of("Research vacation spots in Italy", "Buy new running shoes", "Call dentist for appointment", "Read chapter 5 of Design Patterns", "Organize garage this weekend")) {
            var item = new InboxItem();
            item.setUserId(USER); item.setItemId(ulid()); item.setText(text); item.setCreatedAt(now());
            inboxTable.putItem(item);
        }
    }

    private String[] seedLists() {
        String todayId = ulid(), somedayId = ulid();
        var today = new ListEntity(); today.setUserId(USER); today.setListId(todayId); today.setName("Today"); today.setType("SYSTEM"); today.setCreatedAt(now());
        var someday = new ListEntity(); someday.setUserId(USER); someday.setListId(somedayId); someday.setName("Someday"); someday.setType("SYSTEM"); someday.setCreatedAt(now());
        listsTable.putItem(today); listsTable.putItem(someday);
        return new String[]{todayId, somedayId};
    }

    private String[] seedProjects() {
        String[][] projects = {
                {"Italy Trip Planning", "NOT_STARTED", "Plan our summer trip to Italy"},
                {"Personal Website Redesign", "IN_PROGRESS", "Redesign my personal portfolio site"},
                {"Kitchen Renovation", "DONE", "Renovate the kitchen cabinets and countertops"}
        };
        String[] ids = new String[3];
        for (int i = 0; i < projects.length; i++) {
            String pageId = ulid();
            ids[i] = ulid();
            var page = new Page(); page.setUserId(USER); page.setPageId(pageId); page.setTitle(projects[i][0]);
            page.setOwnerType("project"); page.setOwnerId(ids[i]); page.setCreatedAt(now()); page.setUpdatedAt(now());
            pagesTable.putItem(page);
            saveContent(pageId, samplePageContent(projects[i][0]));

            var p = new Project(); p.setUserId(USER); p.setProjectId(ids[i]); p.setName(projects[i][0]);
            p.setStatus(projects[i][1]); p.setDescription(projects[i][2]); p.setPageId(pageId); p.setCreatedAt(now());
            projectsTable.putItem(p);
        }
        return ids;
    }

    private void seedTasks(String[] projectIds, String[] listIds) {
        String[][] taskSets = {
                {"Book flights to Rome", "Research hotels in Florence", "Create packing list", "Get travel insurance", "Learn basic Italian phrases"},
                {"Choose color palette", "Design homepage mockup", "Implement responsive nav", "Write about page content"},
                {"Select countertop material", "Get contractor quotes", "Order cabinet hardware"}
        };
        for (int p = 0; p < projectIds.length; p++) {
            for (int t = 0; t < taskSets[p].length; t++) {
                String taskId = ulid();
                var task = new Task(); task.setProjectId(projectIds[p]); task.setTaskId(taskId);
                task.setTitle(taskSets[p][t]); task.setDone(p == 2 || (p == 1 && t < 2));
                task.setPosition(t); task.setCreatedAt(now());
                tasksTable.putItem(task);
                // Add first 3 tasks of project 1 to Today list
                if (p == 0 && t < 3) {
                    var li = new ListItem(); li.setListId(listIds[0]); li.setTaskId(taskId);
                    listItemsTable.putItem(li);
                }
                // Add 2 tasks to Someday
                if (p == 1 && t >= 2) {
                    var li = new ListItem(); li.setListId(listIds[1]); li.setTaskId(taskId);
                    listItemsTable.putItem(li);
                }
            }
        }
    }

    private void seedStandalonePages() {
        String[] titles = {"Weekly Review Template", "Meeting Notes", "Reading List"};
        for (int i = 0; i < titles.length; i++) {
            String pageId = ulid();
            var page = new Page(); page.setUserId(USER); page.setPageId(pageId); page.setTitle(titles[i]);
            page.setOwnerType("standalone"); page.setCreatedAt(now()); page.setUpdatedAt(now());
            pagesTable.putItem(page);
            saveContent(pageId, samplePageContent(titles[i]));
            // Add a child page to the first one
            if (i == 0) {
                String childId = ulid();
                var child = new Page(); child.setUserId(USER); child.setPageId(childId); child.setTitle("Week 12 Review");
                child.setOwnerType("standalone"); child.setParentPageId(pageId); child.setCreatedAt(now()); child.setUpdatedAt(now());
                pagesTable.putItem(child);
                saveContent(childId, "{\"type\":\"doc\",\"content\":[{\"type\":\"heading\",\"attrs\":{\"level\":2},\"content\":[{\"type\":\"text\",\"text\":\"Week 12 Review\"}]},{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Completed 8 out of 10 planned tasks. Good progress on the website redesign.\"}]}]}");
            }
        }
    }

    private void seedHabits() {
        String[][] habits = {{"Morning Meditation", "#8B9E7C"}, {"Exercise", "#C4836A"}, {"Read 30 min", "#7B9EB2"}};
        LocalDate today = LocalDate.now();
        for (String[] h : habits) {
            String habitId = ulid();
            var habit = new Habit(); habit.setUserId(USER); habit.setHabitId(habitId); habit.setName(h[0]); habit.setColor(h[1]); habit.setCreatedAt(now());
            habitsTable.putItem(habit);
            for (int d = 29; d >= 0; d--) {
                String date = today.minusDays(d).toString();
                String value = rng.nextInt(10) < 7 ? "YES" : (rng.nextInt(10) < 5 ? "NO" : "SKIP");
                var entry = new HabitEntry(); entry.setHabitId(habitId); entry.setDate(date); entry.setValue(value);
                habitEntriesTable.putItem(entry);
            }
        }
    }

    private void seedDecks() {
        String[][] deckData = {{"Spanish Vocabulary", "Hola", "Hello", "Gracias", "Thank you", "Por favor", "Please", "Buenos días", "Good morning", "Adiós", "Goodbye", "Casa", "House", "Agua", "Water", "Comida", "Food", "Libro", "Book", "Amigo", "Friend"},
                {"Design Principles", "Gestalt", "Principles of visual perception and grouping", "Affordance", "Quality suggesting how an object should be used", "Hierarchy", "Organization of elements by importance", "Contrast", "Difference between elements to create emphasis", "Alignment", "Arrangement of elements along a common edge", "Proximity", "Grouping related items together", "Repetition", "Reuse of elements for consistency", "White Space", "Empty space that gives elements room to breathe", "Typography", "Art of arranging type for readability", "Color Theory", "Framework for combining colors effectively"}};
        for (String[] dd : deckData) {
            String deckId = ulid();
            var deck = new Deck(); deck.setUserId(USER); deck.setDeckId(deckId); deck.setName(dd[0]); deck.setCreatedAt(now());
            decksTable.putItem(deck);
            for (int i = 1; i < dd.length; i += 2) {
                var card = new Card(); card.setDeckId(deckId); card.setCardId(ulid());
                card.setFront(dd[i]); card.setBack(dd[i + 1]);
                card.setStability(rng.nextDouble() * 5); card.setDifficulty(rng.nextDouble() * 0.5);
                // Some cards due now, some in the future
                card.setDue(i < 8 ? Instant.now().minusSeconds(3600).toString() : Instant.now().plusSeconds(86400L * rng.nextInt(7)).toString());
                card.setState(i < 4 ? "NEW" : "REVIEW"); card.setReps(i < 4 ? 0 : rng.nextInt(5));
                card.setLapses(0); card.setCreatedAt(now());
                cardsTable.putItem(card);
            }
        }
    }

    private void seedBooks() {
        String[][] bookData = {
                {"dEaWzQEACAAJ", "Deep Work", "Cal Newport", "https://covers.openlibrary.org/b/isbn/1455586692-L.jpg"},
                {"lZMKzgEACAAJ", "Atomic Habits", "James Clear", "https://covers.openlibrary.org/b/isbn/0735211299-L.jpg"},
                {"SJiO0AEACAAJ", "Slow Productivity", "Cal Newport", "https://covers.openlibrary.org/b/isbn/0593544854-L.jpg"}
        };
        for (String[] bd : bookData) {
            String bookId = ulid(), pageId = ulid();
            var page = new Page(); page.setUserId(USER); page.setPageId(pageId); page.setTitle(bd[1] + " - Notes");
            page.setOwnerType("book"); page.setOwnerId(bookId); page.setCreatedAt(now()); page.setUpdatedAt(now());
            pagesTable.putItem(page);
            saveContent(pageId, samplePageContent(bd[1]));

            var book = new Book(); book.setUserId(USER); book.setBookId(bookId); book.setGoogleBooksId(bd[0]);
            book.setTitle(bd[1]); book.setAuthors(List.of(bd[2])); book.setCoverUrl(bd[3]);
            book.setPageId(pageId); book.setCreatedAt(now());
            booksTable.putItem(book);
        }
    }

    private void seedCalendar() {
        LocalDate today = LocalDate.now();
        String[][] events = {
                {"0", "09:00", "10:00", "Team standup", "manual"}, {"0", "14:00", "15:30", "Design review", "manual"},
                {"1", "08:00", "09:00", "Morning run", "manual"}, {"1", "11:00", "12:00", "1:1 with manager", "external"},
                {"2", "10:00", "11:30", "Sprint planning", "external"}, {"2", "15:00", "16:00", "Deep work block", "manual"},
                {"3", "09:00", "09:30", "Daily journal", "manual"}, {"3", "13:00", "14:00", "Lunch with Alex", "manual"},
                {"4", "10:00", "12:00", "Focus time", "manual"}, {"5", "08:00", "09:00", "Weekend workout", "manual"}
        };
        String[] colors = {"#7B9EB2", "#8B9E7C", "#C4836A", "#D4C5A9"};
        int dow = today.getDayOfWeek().getValue() - 1; // 0=Mon
        for (String[] ev : events) {
            int dayOffset = Integer.parseInt(ev[0]) - dow;
            String date = today.plusDays(dayOffset).toString();
            var event = new CalendarEvent(); event.setUserId(USER); event.setEventId(ulid());
            event.setTitle(ev[3]); event.setDate(date); event.setStartTime(ev[1]); event.setEndTime(ev[2]);
            event.setSource(ev[4]); event.setColor(colors[rng.nextInt(colors.length)]); event.setCreatedAt(now());
            calendarTable.putItem(event);
        }
    }

    private void saveContent(String pageId, String json) {
        var pc = new PageContent(); pc.setPageId(pageId); pc.setVersion(now()); pc.setContent(json);
        pageContentTable.putItem(pc);
    }

    private String samplePageContent(String title) {
        return "{\"type\":\"doc\",\"content\":[{\"type\":\"heading\",\"attrs\":{\"level\":1},\"content\":[{\"type\":\"text\",\"text\":\"" + title + "\"}]},{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Notes and ideas for this topic.\"}]},{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Key point one\"}]}]},{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Key point two\"}]}]}]}]}";
    }

    private String ulid() { return UlidCreator.getUlid().toString(); }
    private String now() { return Instant.now().toString(); }
}
