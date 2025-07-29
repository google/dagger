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
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

internal val Project.versionCatalog: VersionCatalog
    get() = project.extensions.getByType(VersionCatalogsExtension::class.java).find("libs").get()

internal fun Project.getVersionByName(name: String): String {
    val version = versionCatalog.findVersion(name)
    return if (version.isPresent) {
        version.get().requiredVersion
    } else {
        error("Could not find a version for `$name`")
    }
}

internal fun Project.getPluginIdByName(name: String): String {
    val plugin = versionCatalog.findPlugin(name)
    return if (plugin.isPresent) {
        plugin.get().map { it.pluginId }.get()
    } else {
        error("Could not find plugin id for `$name`")
    }
}