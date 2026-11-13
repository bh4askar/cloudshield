package edu.gla.cloudshield.core;

import java.util.List;

public interface BlobStore {
  record Blob(String key, long size, String version) {}

  List<Blob> list(String prefix);

  byte[] read(String key, String version);

  default byte[] read(String key) {
    return read(key, null);
  }

  void write(String key, byte[] bytes);

  void deletePrefix(String prefix);
}
