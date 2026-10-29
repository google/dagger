FROM docker.io/library/ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive

# Tool and SDK versions (keep aligned with .github/workflows/ci.yml).
ARG JAVA_MIN_VERSION=11
ARG JAVA_PLUGIN_VERSION=17
ARG JAVA_VERSION=21
ARG BAZELISK_VERSION=v1.25.0
ARG CMDLINE_TOOLS_VERSION=11076708
ARG ANDROID_API_LEVEL=36
ARG ANDROID_BUILD_TOOLS_VERSION=36.0.0

# 1. Install base build tools and Maven.
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    ca-certificates \
    curl \
    git \
    gnupg \
    jq \
    maven \
    python-is-python3 \
    python3 \
    unzip \
    zip \
  && rm -rf /var/lib/apt/lists/*

# 2. Install Azul Zulu JDKs matching .github/workflows/ci.yml.
RUN curl -fsSL https://repos.azul.com/azul-repo.key \
    | gpg --dearmor -o /usr/share/keyrings/azul.gpg \
  && echo "deb [signed-by=/usr/share/keyrings/azul.gpg]" \
    "https://repos.azul.com/zulu/deb stable main" \
    > /etc/apt/sources.list.d/zulu.list \
  && apt-get update && apt-get install -y --no-install-recommends \
    "zulu${JAVA_MIN_VERSION}-jdk" \
    "zulu${JAVA_PLUGIN_VERSION}-jdk" \
    "zulu${JAVA_VERSION}-jdk" \
  && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/zulu${JAVA_VERSION}

# 3. Install Bazelisk as /usr/local/bin/bazel so .bazelversion is honored.
RUN ARCH="$(dpkg --print-architecture)" \
  && BAZELISK_URL="https://github.com/bazelbuild/bazelisk/releases/download" \
  && curl -fsSL "${BAZELISK_URL}/${BAZELISK_VERSION}/bazelisk-linux-${ARCH}" \
    -o /usr/local/bin/bazel \
  && chmod +x /usr/local/bin/bazel

# 4. Install Android SDK components required by Bazel and Gradle builds.
# Standalone commandlinetools zip provides sdkmanager (bare ubuntu:22.04 has no
# SDK installed, and sdkmanager requires being unpacked under
# $ANDROID_HOME/cmdline-tools/latest).
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin"
ENV PATH="${PATH}:${ANDROID_HOME}/platform-tools"

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
  && REPO_URL="https://dl.google.com/android/repository" \
  && ZIP="commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
  && curl -fsSL "${REPO_URL}/${ZIP}" -o /tmp/cmdline-tools.zip \
  && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
  && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" \
    "${ANDROID_HOME}/cmdline-tools/latest" \
  && rm /tmp/cmdline-tools.zip \
  && yes | sdkmanager --licenses > /dev/null \
  && sdkmanager \
    "platform-tools" \
    "platforms;android-${ANDROID_API_LEVEL}" \
    "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" > /dev/null \
  && chmod -R a+rwX "${ANDROID_HOME}"

WORKDIR /workspace
