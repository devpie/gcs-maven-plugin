package de.janitza.maven.gcs.impl.util

import org.scalatest.freespec.AnyFreeSpec

/**
  * The joined path becomes the object name in the bucket, and the object name is
  * what the public URL is built from. A doubled separator does not collapse in GCS:
  * it creates an empty path segment and a different URL.
  */
class StoragePathSpec extends AnyFreeSpec {

  "join" - {

    "separates prefix and file name with a single slash" in {
      assert(StoragePath.join("releases", "foo.jar") == "releases/foo.jar")
    }

    "does not add a second slash when the prefix already ends with one" in {
      assert(StoragePath.join("releases/", "foo.jar") == "releases/foo.jar")
    }

    "keeps a nested prefix intact" in {
      assert(StoragePath.join("releases/2026", "foo.jar") == "releases/2026/foo.jar")
    }

    // An empty prefix produces a leading slash, which would create an unnamed first
    // path segment in the bucket. GoogleCloudStorageService.getStoragePath never lets
    // this happen — it checks for the empty prefix first. This test states why that
    // check has to stay.
    "produces a leading slash for an empty prefix, which is why callers screen it out" in {
      assert(StoragePath.join("", "foo.jar") == "/foo.jar")
    }
  }
}
