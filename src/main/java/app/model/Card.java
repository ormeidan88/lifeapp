package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Card {
    private String deckId;
    private String cardId;
    private String front;
    private String back;
    private Double stability;
    private Double difficulty;
    private String due;
    private String lastReview;
    private Integer reps;
    private Integer lapses;
    private String state; // NEW, LEARNING, REVIEW, RELEARNING
    private String createdAt;

    @DynamoDbPartitionKey
    public String getDeckId() { return deckId; }
    public void setDeckId(String deckId) { this.deckId = deckId; }

    @DynamoDbSortKey
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getFront() { return front; }
    public void setFront(String front) { this.front = front; }

    public String getBack() { return back; }
    public void setBack(String back) { this.back = back; }

    public Double getStability() { return stability; }
    public void setStability(Double stability) { this.stability = stability; }

    public Double getDifficulty() { return difficulty; }
    public void setDifficulty(Double difficulty) { this.difficulty = difficulty; }

    @DynamoDbSecondarySortKey(indexNames = "deckId-due-index")
    public String getDue() { return due; }
    public void setDue(String due) { this.due = due; }

    public String getLastReview() { return lastReview; }
    public void setLastReview(String lastReview) { this.lastReview = lastReview; }

    public Integer getReps() { return reps; }
    public void setReps(Integer reps) { this.reps = reps; }

    public Integer getLapses() { return lapses; }
    public void setLapses(Integer lapses) { this.lapses = lapses; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
