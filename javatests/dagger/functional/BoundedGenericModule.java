/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional;

import dagger.Module;
import dagger.Provides;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Module
class BoundedGenericModule {

  @Provides
  Integer provideInteger() {
    return 1;
  }

  @Provides
  Double provideDouble() {
    return 2d;
  }

  @Provides
  ArrayList<String> provideArrayListString() {
    ArrayList<String> list = new ArrayList<>();
    list.add("arrayListOfString");
    return list;
  }

  @Provides
  LinkedList<String> provideLinkedListString() {
    LinkedList<String> list = new LinkedList<>();
    list.add("linkedListOfString");
    return list;
  }

  @Provides
  LinkedList<CharSequence> provideLinkedListCharSeq() {
    LinkedList<CharSequence> list = new LinkedList<>();
    list.add("linkedListOfCharSeq");
    return list;
  }

  @Provides
  @SuppressWarnings("unchecked")
  LinkedList<Comparable<String>> provideArrayListOfComparableString() {
    LinkedList<Comparable<String>> list = new LinkedList<>();
    list.add("arrayListOfComparableOfString");
    return list;
  }

  @Provides
  List<Integer> provideListOfInteger() {
    LinkedList<Integer> list = new LinkedList<>();
    list.add(3);
    return list;
  }

  @Provides
  Set<Double> provideSetOfDouble() {
    Set<Double> set = new HashSet<>();
    set.add(4d);
    return set;
  }
}
