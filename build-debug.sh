#!/usr/bin/env bash

set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
wrapper_properties="$project_dir/gradle/wrapper/gradle-wrapper.properties"

distribution_url="$(sed -n 's/^distributionUrl=.*gradle-\([^/]*\)-bin\.zip$/\1/p' "$wrapper_properties")"
if [[ -z "$distribution_url" ]]; then
    echo "无法从 gradle-wrapper.properties 读取 Gradle 版本。" >&2
    exit 1
fi

gradle_home="${GRADLE_USER_HOME:-${HOME}/.gradle}"
local_gradle="$(find "$gradle_home/wrapper/dists" -type f -path "*/gradle-${distribution_url}/bin/gradle" -print -quit 2>/dev/null || true)"
project_gradle_home="$project_dir/.gradle-user-home"
project_android_home="$project_dir/.android-user-home"

if [[ -n "$local_gradle" ]]; then
    echo "使用本地 Gradle ${distribution_url}: $local_gradle"
    GRADLE_USER_HOME="$project_gradle_home" ANDROID_USER_HOME="$project_android_home" exec "$local_gradle" -p "$project_dir" :app:assembleDebug "$@"
fi

echo "未找到本地 Gradle ${distribution_url}，回退到 Gradle Wrapper。"
GRADLE_USER_HOME="$project_gradle_home" ANDROID_USER_HOME="$project_android_home" exec "$project_dir/gradlew" :app:assembleDebug "$@"
