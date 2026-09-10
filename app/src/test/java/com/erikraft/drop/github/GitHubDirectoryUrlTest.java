package com.erikraft.drop.github;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class GitHubDirectoryUrlTest {
    @Test public void parsesCanonicalDirectoryUrl() {
        GitHubDirectoryUrl url = GitHubDirectoryUrl.parse("https://github.com/OWNER/repository/tree/main/path/to%20folder");
        assertEquals("OWNER", url.getOwner());
        assertEquals("repository", url.getRepository());
        assertEquals("main", url.getRef());
        assertEquals("path/to folder", url.getPath());
    }

    @Test public void rejectsNonCanonicalOrUnsafeUrls() {
        String[] unsafe = {
                "http://github.com/a/b/tree/main/folder",
                "https://evil.example/a/b/tree/main/folder",
                "https://github.com/a/b/blob/main/file",
                "https://github.com/a/b/tree/main",
                "https://github.com/a/b/tree/main/%2E%2E/private",
                "https://github.com/a/b/tree/main/a%2Fb",
                "https://github.com/a/b/tree/main/folder?token=secret"
        };
        for (String value : unsafe) {
            try {
                GitHubDirectoryUrl.parse(value);
                fail("Expected rejection for " + value);
            } catch (IllegalArgumentException expected) {
                // Expected: pasted URLs must not become arbitrary network or archive paths.
            }
        }
    }
}
