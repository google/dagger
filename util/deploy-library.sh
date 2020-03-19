#!/bin/bash

set -eu

# Builds and deploys the given artifacts to a configured maven goal.
# @param {string} library the library to deploy.
# @param {string} pomfile the pom file to deploy.
# @param {string} srcjar the sources jar of the library. This is an optional
# parameter, if provided then javadoc must also be provided.
# @param {string} javadoc the java doc jar of the library. This is an optional
# parameter, if provided then srcjar must also be provided.
deploy_library() {
  local library=$1
  local pomfile=$2
  local srcjar=$3
  local javadoc=$4
  local mvn_goal=$5
  local version_name=$6
  local extra_maven_args=${@:7}

  bazel build --define=pom_version="$version_name" \
    $library $pomfile

  # TODO(user): Consider moving this into the "gen_maven_artifact" macro, this
  # requires having the version checked-in for the build system.
  add_tracking_version \
    $(bazel_output_file $library) \
    $(bazel_output_file $pomfile) \
    $version_name

  if [ -n "$srcjar" ] && [ -n "$javadoc" ] ; then
    bazel build --define=pom_version="$version_name" \
      $srcjar $javadoc
    mvn $mvn_goal \
      -Dfile=$(bazel_output_file $library) \
      -Djavadoc=$(bazel_output_file $javadoc) \
      -DpomFile=$(bazel_output_file $pomfile) \
      -Dsources=$(bazel_output_file $srcjar) \
      "${extra_maven_args[@]:+${extra_maven_args[@]}}"
  else
    mvn $mvn_goal \
      -Dfile=$(bazel_output_file $library) \
      -DpomFile=$(bazel_output_file $pomfile) \
      "${extra_maven_args[@]:+${extra_maven_args[@]}}"
  fi
}

bazel_output_file() {
  local library=$1
  local output_file=bazel-bin/$library
  if [[ ! -e $output_file ]]; then
     output_file=bazel-genfiles/$library
  fi
  if [[ ! -e $output_file ]]; then
    echo "Could not find bazel output file for $library"
    exit 1
  fi
  echo -n $output_file
}

add_tracking_version() {
  local library=$1
  local pomfile=$2
  local version_name=$3
  local group_id=$(find_pom_value $pomfile "groupId")
  local artifact_id=$(find_pom_value $pomfile "artifactId")
  local temp_dir=$(mktemp -d)
  local version_file="META-INF/${group_id}_${artifact_id}.version"
  mkdir -p "$temp_dir/META-INF/"
  echo $version_name >> "$temp_dir/$version_file"
  if [[ $library =~ \.jar$ ]]; then
    jar uf $library -C $temp_dir $version_file
  elif [[ $library =~ \.aar$ ]]; then
    unzip $library classes.jar -d $temp_dir
    jar uf $temp_dir/classes.jar -C $temp_dir $version_file
    jar uf $library -C $temp_dir classes.jar
  else
    echo "Could not add tracking version file to $library"
    exit 1
  fi
}

find_pom_value() {
  local pomfile=$1
  local attribute=$2
  # Using Python here because `mvn help:evaluate` doesn't work with our gen pom
  # files since they don't include the aar packaging plugin.
  python $(dirname $0)/find_pom_value.py $pomfile $attribute
}

deploy_library "$@"
