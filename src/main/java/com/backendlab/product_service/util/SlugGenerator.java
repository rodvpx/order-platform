package com.backendlab.product_service.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugGenerator {

  private static final Pattern ACCENT_MARKS = Pattern.compile("\\p{M}+");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

  private SlugGenerator() {}

  public static String generate(String input) {
    String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
    String withoutAccents = ACCENT_MARKS.matcher(decomposed).replaceAll("");
    String lowerCase = withoutAccents.toLowerCase(Locale.ROOT);
    String hyphenated = NON_ALPHANUMERIC.matcher(lowerCase).replaceAll("-");

    return EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
  }
}
