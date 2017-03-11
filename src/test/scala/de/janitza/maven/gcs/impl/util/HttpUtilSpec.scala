package de.janitza.maven.gcs.impl.util

import java.nio.file.Paths

import org.scalatest.FreeSpec

class HttpUtilSpec extends FreeSpec {

  "The content disposition should look like \"attachment; filename=\\\"test.txt\\\"\"" in {
    assert(
      HttpUtil.getContentDisposition("test.txt") == "attachment; filename=\"test.txt\""
    )
  }

  "The mime tpye should be text/x-scala" in {
    assert(
      HttpUtil.getMimeType(Paths.get("src/test/scala/de/janitza/maven/gcs/utils/HttpUtilSpec.scala")) == "text/x-scala"
    )
  }
}
