package app.dynamo;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

@ApplicationScoped
@Startup
public class TableInitializer {

    private static final Logger LOG = Logger.getLogger(TableInitializer.class);

    private final DynamoDbClient dynamoDb;

    @ConfigProperty(name = "app.auth.dev-mode", defaultValue = "false")
    boolean devMode;

    public TableInitializer(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    public void ready() {} // called by SeedData to trigger this bean's @PostConstruct before seeding

    @PostConstruct
    void init() {
        if (!devMode) return;
        LOG.info("Dev mode: auto-creating DynamoDB tables...");
        List<String> existing = dynamoDb.listTables().tableNames();

        createIfMissing(existing, "inbox_items", "userId", "itemId", null);
        createIfMissing(existing, "projects", "userId", "projectId", null);
        createIfMissing(existing, "tasks", "projectId", "taskId",
                List.of(gsi("parentTaskId-index", "parentTaskId", "taskId")));
        createIfMissing(existing, "lists", "userId", "listId", null);
        createIfMissing(existing, "list_items", "listId", "taskId", null);
        createIfMissing(existing, "pages", "userId", "pageId",
                List.of(
                        gsi("ownerType-ownerId-index", "ownerType", "ownerId"),
                        gsi("parentPageId-index", "parentPageId", "pageId")
                ));
        createIfMissing(existing, "page_content", "pageId", "version", null);
        createIfMissing(existing, "habits", "userId", "habitId", null);
        createIfMissing(existing, "habit_entries", "habitId", "date", null);
        createIfMissing(existing, "decks", "userId", "deckId", null);
        createIfMissing(existing, "cards", "deckId", "cardId",
                List.of(gsi("deckId-due-index", "deckId", "due")));
        createIfMissing(existing, "books", "userId", "bookId", null);
        createIfMissing(existing, "calendar_events", "userId", "eventId",
                List.of(gsi("userId-date-index", "userId", "date")));

        LOG.info("Dev mode: all tables ready.");
    }

    private void createIfMissing(List<String> existing, String table, String pk, String sk,
                                  List<GlobalSecondaryIndex> gsis) {
        if (existing.contains(table)) return;
        var attrs = new java.util.ArrayList<>(List.of(
                AttributeDefinition.builder().attributeName(pk).attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName(sk).attributeType(ScalarAttributeType.S).build()
        ));

        var req = CreateTableRequest.builder()
                .tableName(table)
                .keySchema(
                        KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName(sk).keyType(KeyType.RANGE).build()
                )
                .billingMode(BillingMode.PAY_PER_REQUEST);

        if (gsis != null && !gsis.isEmpty()) {
            for (var gsi : gsis) {
                for (var ks : gsi.keySchema()) {
                    if (attrs.stream().noneMatch(a -> a.attributeName().equals(ks.attributeName()))) {
                        attrs.add(AttributeDefinition.builder()
                                .attributeName(ks.attributeName())
                                .attributeType(ScalarAttributeType.S).build());
                    }
                }
            }
            req.globalSecondaryIndexes(gsis);
        }

        req.attributeDefinitions(attrs);
        dynamoDb.createTable(req.build());
        LOG.infof("Created table: %s", table);
    }

    private GlobalSecondaryIndex gsi(String name, String pk, String sk) {
        return GlobalSecondaryIndex.builder()
                .indexName(name)
                .keySchema(
                        KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName(sk).keyType(KeyType.RANGE).build()
                )
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }
}
