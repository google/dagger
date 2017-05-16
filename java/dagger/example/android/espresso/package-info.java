/*
 * Copyright (C) 2017 The Dagger Authors.
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
/**
 * An application (with a build variant) that demonstrates how to swap out dependencies for testing
 * purposes. The main package holds the code for the stubbable application and the stubbed package holds
 * the code for the stubbed build variant of the application.
 *
 * This example follows the testing guidelines outlined in <a href="https://google.github.io/dagger/testing.html">
 *   the Testing section of the documentation</a>.
 */
package dagger.example.android.espresso;

