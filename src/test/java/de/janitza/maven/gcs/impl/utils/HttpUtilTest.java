package de.janitza.maven.gcs.impl.utils;

import org.testng.annotations.Test;

import java.nio.file.Paths;

import static org.testng.Assert.assertEquals;

public class HttpUtilTest {

    public HttpUtilTest() {
    }

    @Test
    public void testGetContentDisposition() throws Exception {
        assertEquals(
                HttpUtil.getContentDisposition("test.txt"),
                "attachment; filename=\"test.txt\""
        );
    }

    @Test
    public void testGetMimeType() throws Exception {
        assertEquals(
                HttpUtil.getMimeType(
                        Paths.get("src/test/de/janitza/maven/gcs/utils/HttpUtilTest.java")
                ),
                "text/x-java"
        );
    }
}