package app.resource;

import app.model.Deck;
import app.model.Card;
import com.github.f4b6a3.ulid.UlidCreator;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.CardAndReviewLog;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.*;

@Path("/api/decks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeckResource {

    private static final String USER = "default";
    private final Scheduler scheduler = Scheduler.builder().build();

    @Inject @Named("decks") DynamoDbTable<Deck> table;
    @Inject @Named("cards") DynamoDbTable<Card> cardsTable;

    @POST
    public Response create(Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) return Response.status(400).entity(Map.of("error", "name is required")).build();
        var deck = new Deck();
        deck.setUserId(USER);
        deck.setDeckId(UlidCreator.getUlid().toString());
        deck.setName(name);
        deck.setCreatedAt(Instant.now().toString());
        table.putItem(deck);
        return Response.status(201).entity(Map.of("id", deck.getDeckId(), "name", deck.getName(), "cardCount", 0, "createdAt", deck.getCreatedAt())).build();
    }

    @GET
    public Response list() {
        String now = Instant.now().toString();
        var decks = table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(USER).build()))
                .items().stream().map(d -> {
                    var cards = cardsTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(d.getDeckId()).build()))
                            .items().stream().toList();
                    long due = cards.stream().filter(c -> c.getDue() != null && c.getDue().compareTo(now) <= 0).count();
                    return Map.of("id", d.getDeckId(), "name", d.getName(), "cardCount", cards.size(), "dueCount", due, "createdAt", d.getCreatedAt());
                }).toList();
        return Response.ok(Map.of("decks", decks)).build();
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        cardsTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(id).build()))
                .items().forEach(c -> cardsTable.deleteItem(Key.builder().partitionValue(id).sortValue(c.getCardId()).build()));
        table.deleteItem(Key.builder().partitionValue(USER).sortValue(id).build());
        return Response.noContent().build();
    }

    @POST @Path("/{deckId}/cards")
    public Response createCard(@PathParam("deckId") String deckId, Map<String, String> body) {
        String front = body.get("front");
        String back = body.get("back");
        if (front == null || front.isBlank() || back == null || back.isBlank()) {
            return Response.status(400).entity(Map.of("error", "front and back are required")).build();
        }
        // Create a new FSRS card
        io.github.openspacedrepetition.Card fsrsCard = io.github.openspacedrepetition.Card.builder().build();

        String now = Instant.now().toString();
        var card = new Card();
        card.setDeckId(deckId);
        card.setCardId(UlidCreator.getUlid().toString());
        card.setFront(front);
        card.setBack(back);
        applyFsrsState(card, fsrsCard);
        card.setCreatedAt(now);
        cardsTable.putItem(card);

        return Response.status(201).entity(cardToMap(card)).build();
    }

    @GET @Path("/{deckId}/cards")
    public Response listCards(@PathParam("deckId") String deckId) {
        var cards = cardsTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(deckId).build()))
                .items().stream().map(this::cardToMap).toList();
        return Response.ok(Map.of("cards", cards)).build();
    }

    @GET @Path("/{deckId}/review")
    public Response reviewCards(@PathParam("deckId") String deckId) {
        String now = Instant.now().toString();
        var due = cardsTable.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(deckId).build()))
                .items().stream()
                .filter(c -> c.getDue() != null && c.getDue().compareTo(now) <= 0)
                .sorted(Comparator.comparing(Card::getDue))
                .limit(50)
                .map(this::cardToMap).toList();
        return Response.ok(Map.of("cards", due)).build();
    }

    @POST @Path("/{deckId}/cards/{cardId}/review")
    public Response review(@PathParam("deckId") String deckId, @PathParam("cardId") String cardId, Map<String, String> body) {
        var card = cardsTable.getItem(Key.builder().partitionValue(deckId).sortValue(cardId).build());
        if (card == null) return Response.status(404).entity(Map.of("error", "Card not found")).build();

        String ratingStr = body.get("rating");
        Rating rating;
        try {
            rating = switch (ratingStr) {
                case "AGAIN" -> Rating.AGAIN;
                case "HARD" -> Rating.HARD;
                case "GOOD" -> Rating.GOOD;
                case "EASY" -> Rating.EASY;
                default -> throw new IllegalArgumentException();
            };
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", "Invalid rating. Use AGAIN, HARD, GOOD, or EASY")).build();
        }

        // Reconstruct FSRS card from stored state
        io.github.openspacedrepetition.Card fsrsCard = toFsrsCard(card);

        // Review with FSRS scheduler
        CardAndReviewLog result = scheduler.reviewCard(fsrsCard, rating);
        io.github.openspacedrepetition.Card updatedFsrs = result.card();

        // Apply updated FSRS state back to our model
        applyFsrsState(card, updatedFsrs);
        card.setLastReview(Instant.now().toString());
        card.setReps(card.getReps() + 1);
        if (rating == Rating.AGAIN) card.setLapses(card.getLapses() + 1);
        cardsTable.putItem(card);

        return Response.ok(cardToMap(card)).build();
    }

    private void applyFsrsState(Card card, io.github.openspacedrepetition.Card fsrs) {
        card.setStability(fsrs.getStability());
        card.setDifficulty(fsrs.getDifficulty());
        card.setDue(fsrs.getDue().toString());
        card.setState(fsrs.getState().name());
        if (card.getReps() == null) card.setReps(0);
        if (card.getLapses() == null) card.setLapses(0);
    }

    private io.github.openspacedrepetition.Card toFsrsCard(Card card) {
        // Use FSRS Card's JSON serialization to reconstruct
        // Build a minimal card from our stored state
        io.github.openspacedrepetition.Card fsrs = io.github.openspacedrepetition.Card.builder().build();
        // The FSRS library Card is immutable after creation via builder,
        // so we reconstruct via JSON round-trip
        String json = String.format(
                "{\"due\":\"%s\",\"stability\":%f,\"difficulty\":%f,\"elapsed_days\":0,\"scheduled_days\":0,\"reps\":%d,\"lapses\":%d,\"state\":\"%s\",\"last_review\":\"%s\"}",
                card.getDue(),
                card.getStability() != null ? card.getStability() : 0.0,
                card.getDifficulty() != null ? card.getDifficulty() : 0.0,
                card.getReps() != null ? card.getReps() : 0,
                card.getLapses() != null ? card.getLapses() : 0,
                card.getState() != null ? card.getState() : "NEW",
                card.getLastReview() != null ? card.getLastReview() : Instant.now().toString()
        );
        try {
            return io.github.openspacedrepetition.Card.fromJson(json);
        } catch (Exception e) {
            // Fallback: return a fresh card
            return io.github.openspacedrepetition.Card.builder().build();
        }
    }

    private Map<String, Object> cardToMap(Card c) {
        var m = new HashMap<String, Object>();
        m.put("id", c.getCardId()); m.put("deckId", c.getDeckId());
        m.put("front", c.getFront()); m.put("back", c.getBack());
        m.put("fsrs", Map.of(
                "stability", c.getStability() != null ? c.getStability() : 0.0,
                "difficulty", c.getDifficulty() != null ? c.getDifficulty() : 0.0,
                "due", c.getDue() != null ? c.getDue() : "",
                "state", c.getState() != null ? c.getState() : "NEW"
        ));
        m.put("createdAt", c.getCreatedAt());
        return m;
    }
}
