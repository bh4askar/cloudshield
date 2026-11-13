package edu.gla.cloudshield.core;

import java.util.*;

public final class SampleData {
  private SampleData() {}

  public static void seed(BlobStore source) {
    if (!source.list("application/").isEmpty()) return;
    source.write(
        "application/registrations.json",
        Json.bytes(
            Map.of(
                "schemaVersion",
                1,
                "registrations",
                List.of(
                    Map.of(
                        "id",
                        "REG-101",
                        "student",
                        "Aarav Sharma",
                        "course",
                        "Cloud Computing",
                        "status",
                        "Confirmed"),
                    Map.of(
                        "id",
                        "REG-102",
                        "student",
                        "Diya Mehta",
                        "course",
                        "Computer Networks",
                        "status",
                        "Confirmed"),
                    Map.of(
                        "id",
                        "REG-103",
                        "student",
                        "Rohan Verma",
                        "course",
                        "Database Systems",
                        "status",
                        "Pending"),
                    Map.of(
                        "id",
                        "REG-104",
                        "student",
                        "Ananya Singh",
                        "course",
                        "Cloud Computing",
                        "status",
                        "Confirmed")))));
    source.write(
        "application/catalog.json",
        Json.bytes(
            Map.of(
                "schemaVersion",
                1,
                "courses",
                List.of("Cloud Computing", "Computer Networks", "Database Systems"))));
    source.write(
        "application/settings.json",
        Json.bytes(
            Map.of(
                "schemaVersion",
                1,
                "application",
                "Campus Register",
                "semester",
                "VII",
                "academicYear",
                "2026-27")));
  }

  public static void addRegistration(BlobStore source) {
    try {
      var root =
          (com.fasterxml.jackson.databind.node.ObjectNode)
              Json.MAPPER.readTree(source.read("application/registrations.json"));
      var rows = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("registrations");
      rows.addObject()
          .put("id", "REG-" + UUID.randomUUID().toString().substring(0, 8))
          .put("student", "Demo Student " + (rows.size() + 1))
          .put("course", "Cloud Computing")
          .put("status", "Confirmed");
      source.write("application/registrations.json", Json.bytes(root));
    } catch (Exception e) {
      throw new IllegalStateException("Could not update sample application", e);
    }
  }
}
