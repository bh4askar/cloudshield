package edu.gla.cloudshield.worker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FunctionsTest {
  @Test
  void firstTimerInvocationUsesNextAndNotUnsetLast() {
    assertEquals(
        "2026-11-13T06:51:00Z",
        Functions.scheduledOccurrence(
            "{\"ScheduleStatus\":{\"Last\":\"0001-01-01T00:00:00Z\",\"Next\":\"2026-11-13T06:51:00Z\"}}"));
  }

  @Test
  void pastDueTimerUsesOriginalOccurrenceAcrossRetries() {
    String timer =
        "{\"scheduleStatus\":{\"last\":\"2026-11-12T20:30:00Z\",\"next\":\"2026-11-13T20:30:00+00:00\"},\"isPastDue\":true}";
    assertEquals("2026-11-13T20:30:00Z", Functions.scheduledOccurrence(timer));
    assertEquals(Functions.scheduledOccurrence(timer), Functions.scheduledOccurrence(timer));
  }

  @Test
  void scheduledDeliveryIsStableAndSeparatesOperations() {
    String t = "2026-11-13T20:30:00Z";
    assertEquals(Functions.scheduledId("BACKUP", t), Functions.scheduledId("BACKUP", t));
    assertNotEquals(Functions.scheduledId("BACKUP", t), Functions.scheduledId("TEST", t));
    assertNotEquals(
        Functions.scheduledId("BACKUP", t),
        Functions.scheduledId("BACKUP", "2026-11-14T20:30:00Z"));
  }

  @Test
  void malformedTimerEventsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Functions.scheduledId("DELETE", "2026-11-13T20:30:00Z"));
    assertThrows(
        java.time.format.DateTimeParseException.class,
        () -> Functions.scheduledId("BACKUP", "not-a-date"));
  }
}
