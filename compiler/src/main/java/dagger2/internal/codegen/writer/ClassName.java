/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger2.internal.codegen.writer;

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.element.NestingKind.MEMBER;
import static javax.lang.model.element.NestingKind.TOP_LEVEL;

/**
 * Represents a fully-qualified class name for {@link NestingKind#TOP_LEVEL} and
 * {@link NestingKind#MEMBER} classes.
 *
 * @since 2.0
 */
public final class ClassName implements TypeName, Comparable<ClassName> {
  private String fullyQualifiedName = null;
  private final String packageName;
  /* From top to bottom.  E.g.: this field will contain ["A", "B"] for pgk.A.B.C */
  private final ImmutableList<String> enclosingSimpleNames;
  private final String simpleName;

  private ClassName(String packageName, ImmutableList<String> enclosingSimpleNames,
      String simpleName) {
    this.packageName = packageName;
    this.enclosingSimpleNames = enclosingSimpleNames;
    this.simpleName = simpleName;
  }

  public String packageName() {
    return packageName;
  }

  public ImmutableList<String> enclosingSimpleNames() {
    return enclosingSimpleNames;
  }

  public Optional<ClassName> enclosingClassName() {
    return enclosingSimpleNames.isEmpty()
        ? Optional.<ClassName>absent()
        : Optional.of(new ClassName(packageName,
            enclosingSimpleNames.subList(0, enclosingSimpleNames.size() - 1),
            enclosingSimpleNames.get(enclosingSimpleNames.size() - 1)));
  }

  public String simpleName() {
    return simpleName;
  }

  public String canonicalName() {
    if (fullyQualifiedName == null) {
      StringBuilder builder = new StringBuilder(packageName());
      if (builder.length() > 0) {
        builder.append('.');
      }
      for (String enclosingSimpleName : enclosingSimpleNames()) {
        builder.append(enclosingSimpleName).append('.');
      }
      fullyQualifiedName = builder.append(simpleName()).toString();
    }
    return fullyQualifiedName;
  }

  public String classFileName() {
    StringBuilder builder = new StringBuilder();
    Joiner.on('$').appendTo(builder, enclosingSimpleNames());
    if (!enclosingSimpleNames().isEmpty()) {
      builder.append('$');
    }
    return builder.append(simpleName()).toString();
  }

  public ClassName topLevelClassName() {
    Iterator<String> enclosingIterator = enclosingSimpleNames().iterator();
    return enclosingIterator.hasNext()
        ? new ClassName(packageName(), ImmutableList.<String>of(),
            enclosingIterator.next())
        : this;
  }

  public ClassName nestedClassNamed(String memberClassName) {
    checkNotNull(memberClassName);
    checkArgument(SourceVersion.isIdentifier(memberClassName));
    checkArgument(Ascii.isUpperCase(memberClassName.charAt(0)));
    return new ClassName(packageName(),
        new ImmutableList.Builder<String>()
            .addAll(enclosingSimpleNames())
            .add(simpleName())
            .build(),
        memberClassName);
  }

  public ClassName peerNamed(String peerClassName) {
    checkNotNull(peerClassName);
    checkArgument(SourceVersion.isIdentifier(peerClassName));
    checkArgument(Ascii.isUpperCase(peerClassName.charAt(0)));
    return new ClassName(packageName(), enclosingSimpleNames(), peerClassName);
  }

  private static final ImmutableSet<NestingKind> ACCEPTABLE_NESTING_KINDS =
      Sets.immutableEnumSet(TOP_LEVEL, MEMBER);

  public static ClassName fromTypeElement(TypeElement element) {
    checkNotNull(element);
    checkArgument(ACCEPTABLE_NESTING_KINDS.contains(element.getNestingKind()));
    String simpleName = element.getSimpleName().toString();
    List<String> enclosingNames = new ArrayList<String>();
    Element current = element.getEnclosingElement();
    while (current.getKind().isClass() || current.getKind().isInterface()) {
      checkArgument(ACCEPTABLE_NESTING_KINDS.contains(element.getNestingKind()));
      enclosingNames.add(current.getSimpleName().toString());
      current = current.getEnclosingElement();
    }
    PackageElement packageElement = getPackage(current);
    Collections.reverse(enclosingNames);
    return new ClassName(packageElement.getQualifiedName().toString(),
        ImmutableList.copyOf(enclosingNames), simpleName);
  }

  public static ClassName fromClass(Class<?> clazz) {
    checkNotNull(clazz);
    List<String> enclosingNames = new ArrayList<String>();
    Class<?> current = clazz.getEnclosingClass();
    while (current != null) {
      enclosingNames.add(current.getSimpleName());
      current = current.getEnclosingClass();
    }
    Collections.reverse(enclosingNames);
    return create(clazz.getPackage().getName(), enclosingNames, clazz.getSimpleName());
  }

  private static PackageElement getPackage(Element type) {
    while (type.getKind() != ElementKind.PACKAGE) {
      type = type.getEnclosingElement();
    }
    return (PackageElement) type;
  }

  /**
   * Returns a new {@link ClassName} instance for the given fully-qualified class name string. This
   * method assumes that the input is ASCII and follows typical Java style (lower-case package
   * names, upper-camel-case class names) and may produce incorrect results or throw
   * {@link IllegalArgumentException} otherwise. For that reason, {@link #fromClass(Class)} and
   * {@link #fromClass(Class)} should be preferred as they can correctly create {@link ClassName}
   * instances without such restrictions.
   */
  public static ClassName bestGuessFromString(String classNameString) {
    checkNotNull(classNameString);
    List<String> parts = Splitter.on('.').splitToList(classNameString);
    int firstClassPartIndex = -1;
    for (int i = 0; i < parts.size(); i++) {
      String part = parts.get(i);
      checkArgument(SourceVersion.isIdentifier(part));
      char firstChar = part.charAt(0);
      if (Ascii.isLowerCase(firstChar)) {
        // looks like a package part
        if (firstClassPartIndex >= 0) {
          throw new IllegalArgumentException("couldn't make a guess for " + classNameString);
        }
      } else if (Ascii.isUpperCase(firstChar)) {
        // looks like a class part
        if (firstClassPartIndex < 0) {
          firstClassPartIndex = i;
        }
      } else {
        throw new IllegalArgumentException("couldn't make a guess for " + classNameString);
      }
    }
    int lastIndex = parts.size() - 1;
    return new ClassName(
        Joiner.on('.').join(parts.subList(0, firstClassPartIndex)),
        firstClassPartIndex == lastIndex
            ? ImmutableList.<String>of()
            : ImmutableList.copyOf(parts.subList(firstClassPartIndex, lastIndex)),
        parts.get(lastIndex));
  }

  public static ClassName create(
      String packageName, List<String> enclosingSimpleNames, String simpleName) {
    return new ClassName(packageName, ImmutableList.copyOf(enclosingSimpleNames),
        simpleName);
  }

  public static ClassName create(String packageName, String simpleName) {
    return new ClassName(packageName, ImmutableList.<String>of(), simpleName);
  }

  @Override
  public String toString() {
    return canonicalName();
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    appendable.append(context.sourceReferenceForClassName(this));
    return appendable;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof ClassName) {
      ClassName that = (ClassName) obj;
      return this.packageName.equals(that.packageName)
          && this.enclosingSimpleNames.equals(that.enclosingSimpleNames)
          && this.simpleName.equals(that.simpleName);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(packageName, enclosingSimpleNames, simpleName);
  }

  @Override
  public int compareTo(ClassName o) {
    return canonicalName().compareTo(o.canonicalName());
  }

  @Override
  public Set<ClassName> referencedClasses() {
    return ImmutableSet.of(this);
  }
}
