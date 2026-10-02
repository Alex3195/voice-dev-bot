package com.alex.voicedevbot.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Foydalanuvchi yuborgan repo havolasi: {@code https://github.com/egasi/nomi}, {@code
 * https://git.example.uz/guruh/ichki/nomi}, oxirida {@code .git}, {@code /-/issues/5} bilan yoki
 * SSH shaklida ({@code git@host:guruh/nomi.git}). Server qayerda tugab, repo yo'li qayerdan
 * boshlanishini havolaning o'zi aytmaydi — buni ulanish manzili ({@link #repoPath}) hal qiladi.
 */
public record RepoUrl(ServerAddress server, List<String> segments) {

  private static final Pattern SCP_LIKE = Pattern.compile("[\\w.-]+@([\\w.-]+):(.+)");
  private static final String GITLAB_SEPARATOR = "/-/";
  private static final String GIT_SUFFIX = ".git";
  private static final int GITHUB_SEGMENTS = 2;

  public RepoUrl {
    segments = List.copyOf(segments);
    if (segments.size() < 2 || segments.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("Repository URL must contain owner and name");
    }
  }

  public static RepoUrl parse(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Repository URL must not be blank");
    }
    String trimmed = text.strip();
    Matcher scp = SCP_LIKE.matcher(trimmed);
    if (!trimmed.contains("://") && scp.matches()) {
      return of("https://" + scp.group(1), scp.group(2));
    }
    URI uri;
    try {
      uri = new URI(trimmed.contains("://") ? trimmed : "https://" + trimmed);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Repository URL is not a valid URL", e);
    }
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    if (scheme.equals("ssh") && uri.getHost() != null) {
      return of("https://" + uri.getHost(), rawPath(uri));
    }
    if (uri.getHost() == null || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Repository URL must be a plain web URL");
    }
    String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
    return of(scheme + "://" + uri.getHost() + port, rawPath(uri));
  }

  /**
   * Shu manzildagi server uchun repo yo'li ({@code guruh/nomi}); havola boshqa serverniki yoki yo'l
   * to'liq bo'lmasa — bo'sh. GitHub'da yo'l har doim {@code egasi/nomi}, qolgani ({@code
   * /tree/main}, {@code /issues}) tashlanadi.
   */
  public Optional<String> repoPath(ServerAddress address, Provider provider) {
    URI uri = address.uri();
    if (!uri.getHost().equalsIgnoreCase(server.uri().getHost())
        || uri.getPort() != server.uri().getPort()) {
      return Optional.empty();
    }
    List<String> prefix = split(uri.getPath());
    if (segments.size() < prefix.size() + 2 || !segments.subList(0, prefix.size()).equals(prefix)) {
      return Optional.empty();
    }
    List<String> path = segments.subList(prefix.size(), segments.size());
    if (provider == Provider.GITHUB) {
      path = path.subList(0, GITHUB_SEGMENTS);
    }
    return Optional.of(String.join("/", path));
  }

  private static RepoUrl of(String server, String path) {
    String repoPath = path;
    int separator = repoPath.indexOf(GITLAB_SEPARATOR);
    if (separator >= 0) {
      repoPath = repoPath.substring(0, separator);
    }
    List<String> parts = new ArrayList<>(split(repoPath));
    if (!parts.isEmpty() && parts.getLast().endsWith(GIT_SUFFIX)) {
      String last = parts.removeLast();
      parts.add(last.substring(0, last.length() - GIT_SUFFIX.length()));
    }
    return new RepoUrl(ServerAddress.parse(server), parts);
  }

  private static String rawPath(URI uri) {
    return uri.getPath() == null ? "" : uri.getPath();
  }

  private static List<String> split(String path) {
    if (path == null) {
      return List.of();
    }
    return Arrays.stream(path.split("/")).filter(part -> !part.isEmpty()).toList();
  }
}
