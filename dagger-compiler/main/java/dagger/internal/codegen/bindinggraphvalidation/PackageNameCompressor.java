/*
 * Copyright (C) 2020 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen.bindinggraphvalidation;

import static java.util.Comparator.comparing;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Munges an error message to remove/shorten package names and adds a legend at the end.
 */
final class PackageNameCompressor {

  static final String LEGEND_HEADER =
      "\n\n======================\nFull classname legend:\n======================\n";
  static final String LEGEND_FOOTER =
      "========================\nEnd of classname legend:\n========================\n";

  private static final ImmutableSet<String> PACKAGES_SKIPPED_IN_LEGEND = ImmutableSet.of(
      "java.lang.",
      "java.util.");

  private static final Splitter PACKAGE_SPLITTER = Splitter.on('.');

  private static final Joiner PACKAGE_JOINER = Joiner.on('.');

  // TODO(erichang): Consider validating this regex by also passing in all of the known types from
  // keys, module names, component names, etc and checking against that list. This may have some
  // extra complications with taking apart types like List<Foo> to get the inner class names.
  private static final Pattern CLASSNAME_PATTERN =
      // Match lowercase package names with trailing dots. Start with a non-word character so we
      // don't match substrings in like Bar.Foo and match the ar.Foo. Start a group to not include
      // the non-word character.
      Pattern.compile("[\\W](([a-z_0-9]++[.])++"
          // Then match a name starting with an uppercase letter. This is the outer class name.
          + "[A-Z][\\w$]++)");

  /**
   * Compresses an error message by stripping the packages out of class names and adding them
   * to a legend at the bottom of the error.
   */
  static String compressPackagesInMessage(String input) {
    Matcher matcher = CLASSNAME_PATTERN.matcher(input);

    Set<String> names = new HashSet<>();
    // Find all classnames in the error. Note that if our regex isn't complete, it just means the
    // classname is left in the full form, which is a fine fallback.
    while (matcher.find()) {
      String name = matcher.group(1);
      names.add(name);
    }
    // Now dedupe any conflicts. Use a TreeMap since we're going to need the legend sorted anyway.
    // This map is from short name to full name.
    Map<String, String> replacementMap = shortenNames(names);

    // If we have nothing to replace, just return the original.
    if (replacementMap.isEmpty()) {
      return input;
    }

    // Find the longest key for building the legend
    int longestKey = replacementMap.keySet().stream().max(comparing(String::length)).get().length();

    String replacedString = input;
    StringBuilder legendBuilder = new StringBuilder();
    for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
      String shortName = entry.getKey();
      String fullName = entry.getValue();
      // Do the replacements in the message
      replacedString = replacedString.replace(fullName, shortName);

      // Skip certain prefixes. We need to check the shortName for a . though in case
      // there was some type of conflict like java.util.concurrent.Future and
      // java.util.foo.Future that got shortened to concurrent.Future and foo.Future.
      // In those cases we do not want to skip the legend. We only skip if the class
      // is directly in that package.
      String prefix = fullName.substring(0, fullName.length() - shortName.length());
      if (PACKAGES_SKIPPED_IN_LEGEND.contains(prefix) && !shortName.contains(".")) {
        continue;
      }

      // Add to the legend
      legendBuilder
          .append(shortName)
          .append(": ")
          // Add enough spaces to adjust the columns
          .append(Strings.repeat(" ", longestKey - shortName.length()))
          .append(fullName)
          .append("\n");
    }

    return legendBuilder.length() == 0 ? replacedString
        : replacedString + LEGEND_HEADER + legendBuilder + LEGEND_FOOTER;
  }

  /**
   * Returns a map from short name to full name after resolving conflicts. This resolves conflicts
   * by adding on segments of the package name until they are unique. For example, com.foo.Baz and
   * com.bar.Baz will conflict on Baz and then resolve with foo.Baz and bar.Baz as replacements.
   */
  private static Map<String, String> shortenNames(Collection<String> names) {
    HashMultimap<String, List<String>> shortNameToPartsMap = HashMultimap.create();
    for (String name : names) {
      List<String> parts = new ArrayList<>(PACKAGE_SPLITTER.splitToList(name));
      // Start with the just the class name as the simple name
      String className = parts.remove(parts.size() - 1);
      shortNameToPartsMap.put(className, parts);
    }

    // Iterate through looking for conflicts adding the next part of the package until there are no
    // more conflicts
    while (true) {
      // Save the keys with conflicts to avoid concurrent modification issues
      List<String> conflictingShortNames = new ArrayList<>();
      for (Map.Entry<String, Collection<List<String>>> entry
          : shortNameToPartsMap.asMap().entrySet()) {
        if (entry.getValue().size() > 1) {
          conflictingShortNames.add(entry.getKey());
        }
      }

      if (conflictingShortNames.isEmpty()) {
        break;
      }

      // For all conflicts, add in the next part of the package
      for (String conflictingShortName : conflictingShortNames) {
        Set<List<String>> partsCollection = shortNameToPartsMap.removeAll(conflictingShortName);
        for (List<String> parts : partsCollection) {
          String newShortName = parts.remove(parts.size() - 1) + "." + conflictingShortName;
          // If we've removed the last part of the package, then just skip it entirely because
          // now we're not shortening it at all.
          if (!parts.isEmpty()) {
            shortNameToPartsMap.put(newShortName, parts);
          }
        }
      }
    }

    // Turn the multimap into a regular map now that conflicts have been resolved. Use a TreeMap
    // since we're going to need the legend sorted anyway. This map is from short name to full name.
    Map<String, String> replacementMap = new TreeMap<>();
    for (Map.Entry<String, Collection<List<String>>> entry
        : shortNameToPartsMap.asMap().entrySet()) {
      replacementMap.put(
          entry.getKey(),
          PACKAGE_JOINER.join(Iterables.getOnlyElement(entry.getValue())) + "." + entry.getKey());
    }
    return replacementMap;
  }

  private PackageNameCompressor() {}
}
