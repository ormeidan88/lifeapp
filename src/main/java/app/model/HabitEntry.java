package app.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class HabitEntry {
    private String habitId;
    private String date;
    private String value; // YES, NO, SKIP

    @DynamoDbPartitionKey
    public String getHabitId() { return habitId; }
    public void setHabitId(String habitId) { this.habitId = habitId; }

    @DynamoDbSortKey
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
