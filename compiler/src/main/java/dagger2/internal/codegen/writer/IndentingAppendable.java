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

import com.google.common.collect.AbstractIterator;
import java.io.IOException;
import java.util.Iterator;

final class IndentingAppendable implements Appendable {
  private final String indentation;
  private final Appendable delegate;
  private boolean requiresIndent = true;

  IndentingAppendable(Appendable delegate) {
    this("  ", delegate);
  }

  IndentingAppendable(String indentation, Appendable delegate) {
    this.indentation = indentation;
    this.delegate = delegate;
  }

  @Override
  public Appendable append(CharSequence csq) throws IOException {
    return append(csq, 0, csq.length());
  }

  @Override
  public Appendable append(CharSequence csq, int start, int end) throws IOException {
    Iterator<CharSequence> lines = lines(csq, start, end);
    while (lines.hasNext()) {
      CharSequence line = lines.next();
      maybeIndent();
      delegate.append(line);
      if (line.charAt(line.length() - 1) == '\n') {
        requiresIndent = true;
      }
    }
    return this;
  }

  @Override
  public Appendable append(char c) throws IOException {
    maybeIndent();
    delegate.append(c);
    if (c == '\n') {
      requiresIndent = true;
    }
    return this;
  }

  void maybeIndent() throws IOException {
    if (requiresIndent) {
      delegate.append(indentation);
    }
    requiresIndent = false;
  }

  private static Iterator<CharSequence> lines(
      final CharSequence csq, final int start, final int end) {
    return new AbstractIterator<CharSequence>() {
      int index = start;

      @Override protected CharSequence computeNext() {
        int nextStart = index;
        while (index < end && csq.charAt(index) != '\n') {
          index++;
        }
        if (index < end && csq.charAt(index) == '\n') {
          index++;
        }
        int nextEnd = index;
        return nextStart >= end
            ? endOfData()
            : csq.subSequence(nextStart, nextEnd);
      }
    };
  }
}
