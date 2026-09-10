package com.erikraft.drop.github;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Strict, side-effect-free parser for public GitHub directory URLs.
 *
 * <p>The parser deliberately accepts only the github.com {@code /owner/repository/tree/ref/path}
 * form. It neither follows redirects nor accepts an arbitrary download URL, so callers can use its
 * result to construct GitHub API requests without turning a pasted WebView value into an SSRF
 * request. Authentication is intentionally outside this value object.</p>
 */
public final class GitHubDirectoryUrl {
    private static final Pattern ACCOUNT = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})");
    private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9_.-]{1,100}");

    private final String owner;
    private final String repository;
    private final String ref;
    private final List<String> pathSegments;

    private GitHubDirectoryUrl(String owner, String repository, String ref, List<String> pathSegments) {
        this.owner = owner;
        this.repository = repository;
        this.ref = ref;
        this.pathSegments = Collections.unmodifiableList(new ArrayList<>(pathSegments));
    }

    public String getOwner() { return owner; }
    public String getRepository() { return repository; }
    public String getRef() { return ref; }
    public List<String> getPathSegments() { return pathSegments; }

    /** Returns a safe, slash-separated repository-relative path (never begins with a slash). */
    public String getPath() { return String.join("/", pathSegments); }

    /**
     * Parses a canonical GitHub tree URL. GitHub's UI does not delimit refs containing slashes;
     * this method therefore treats the first segment following {@code tree} as the ref. A caller
     * supporting slash-containing branch names must resolve that ambiguity against GitHub's API,
     * rather than guessing and downloading a different directory.
     */
    public static GitHubDirectoryUrl parse(String value) throws IllegalArgumentException {
        if (value == null || value.length() > 8_192) throw new IllegalArgumentException("Invalid GitHub directory URL");
        final URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid GitHub directory URL", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Only canonical https://github.com tree URLs are supported");
        }

        String[] raw = uri.getRawPath().split("/", -1);
        if (raw.length < 6 || !raw[0].isEmpty() || !"tree".equals(raw[3])) {
            throw new IllegalArgumentException("Expected /OWNER/REPOSITORY/tree/BRANCH/PATH");
        }
        String owner = decodeSegment(raw[1]);
        String repository = decodeSegment(raw[2]);
        String ref = decodeSegment(raw[4]);
        if (!ACCOUNT.matcher(owner).matches() || !REPOSITORY.matcher(repository).matches() || !isSafeSegment(ref)) {
            throw new IllegalArgumentException("Invalid GitHub owner, repository, or branch");
        }

        List<String> path = new ArrayList<>();
        for (int i = 5; i < raw.length; i++) {
            String segment = decodeSegment(raw[i]);
            if (!isSafeSegment(segment)) throw new IllegalArgumentException("Invalid GitHub directory path");
            path.add(segment);
        }
        if (path.isEmpty()) throw new IllegalArgumentException("A directory path is required");
        return new GitHubDirectoryUrl(owner, repository, ref, path);
    }

    private static String decodeSegment(String raw) {
        final String decoded;
        try {
            decoded = URLDecoder.decode(raw, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 is required by the platform", e);
        }
        if (decoded.indexOf('/') >= 0 || decoded.indexOf('\\') >= 0 || decoded.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("Encoded path separators are not allowed");
        }
        return decoded;
    }

    private static boolean isSafeSegment(String value) {
        return !value.isEmpty() && !".".equals(value) && !"..".equals(value)
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0;
    }
}
