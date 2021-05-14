/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import com.google.common.collect.ImmutableList;
import javax.lang.model.element.Element;

/**
 * Exception to throw when input code has caused an error.
 * Includes elements to point to for the cause of the error
 */
public final class BadInputException extends RuntimeException {
  private final ImmutableList<Element> badElements;

  public BadInputException(String message, Element badElement) {
    super(message);
    this.badElements = ImmutableList.of(badElement);
  }

  public BadInputException(String message, Iterable<? extends Element> badElements) {
    super(message);
    this.badElements = ImmutableList.copyOf(badElements);
  }

  public BadInputException(String message) {
    super(message);
    this.badElements = ImmutableList.of();
  }

  public ImmutableList<Element> getBadElements() {
    return badElements;
  }
}
