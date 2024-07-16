#!/bin/bash
# Copyright (C) 2024 The Dagger Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

JAVA_HOME="$(cd "${JAVA_HOME}" && pwd)" # this is used outside of the root

TMPDIR="$(mktemp -d)"
for jar in "$@"; do
  unzip -qq -B "${jar}" -d "${TMPDIR}"
done

pushd "${TMPDIR}" &>/dev/null

find=(find)
if [[ "$(uname -s)" == "Darwin" ]]; then
  # Mac uses BSD find, which requires extra args for regex matching.
  find+=(-E)
  suffix='(~[0-9]*)?'
else
  # Default to GNU find, which must escape parentheses.
  suffix='\(~[0-9]*\)?'
fi

# Concatenate similar files in META-INF that allow it.
for meta_inf_pattern in services/.* ${MERGE_META_INF_FILES}; do
  regex="META-INF/${meta_inf_pattern}${suffix}"
  for file in $("${find[@]}" META-INF -regex "${regex}"); do
    original="$(sed s/"~[0-9]*$"// <<< "${file}")"
    if [[ "${file}" != "${original}" ]]; then
      cat "${file}" >> "${original}"
      rm "${file}"
    fi
  done
done

# build-data.properties is emitted by Bazel with target information that can
# cause conflicts. Delete it since it doesn't make sense to keep in the merged
# jar anyway.
rm build-data.properties*
rm META-INF/MANIFEST.MF*
rm -rf META-INF/maven/
duplicate_files="$(find * -type f -regex '.*~[0-9]*$')"
if [[ -n "${duplicate_files}" ]]; then
  echo "Error: duplicate files in merged jar: ${duplicate_files}"
  exit 1
fi
"${JAVA_HOME}/bin/jar" cf combined.jar *

popd &>/dev/null

"${JARJAR}" process "${RULES_FILE}" "${TMPDIR}/combined.jar" "${OUTFILE}"

rm -rf "${TMPDIR}"
