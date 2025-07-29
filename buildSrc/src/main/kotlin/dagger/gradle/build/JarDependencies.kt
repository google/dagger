/*
 * Copyright (C) 2025 The Dagger Authors.
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

package dagger.gradle.build

import org.gradle.api.Project
import org.gradle.api.file.RegularFile

fun Project.findBootstrapCompilerJar(): RegularFile =
  rootProject.layout.projectDirectory
    .dir("dagger-compiler/main/java/dagger/internal/codegen/bootstrap")
    .file("bootstrap_compiler_deploy.jar")

fun Project.findXProcessingJar(): RegularFile =
  rootProject.layout.projectDirectory
    .dir("dagger-compiler/main/java/dagger/internal/codegen/xprocessing")
    .file("xprocessing-internal.jar")

fun Project.findXProcessingTestingJar(): RegularFile =
  rootProject.layout.projectDirectory
    .dir("dagger-compiler/main/java/dagger/internal/codegen/xprocessing")
    .file("xprocessing-testing-internal.jar")
