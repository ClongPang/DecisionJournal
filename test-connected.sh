#!/usr/bin/env bash

set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
wrapper_properties="$project_dir/gradle/wrapper/gradle-wrapper.properties"
distribution_version="$(sed -n 's/^distributionUrl=.*gradle-\([^/]*\)-bin\.zip$/\1/p' "$wrapper_properties")"
gradle_home="${GRADLE_USER_HOME:-${HOME}/.gradle}"
gradle_bin="$(find "$gradle_home/wrapper/dists" -type f -path "*/gradle-${distribution_version}/bin/gradle" -print -quit 2>/dev/null || true)"

if [[ -z "$gradle_bin" ]]; then
    echo "未找到本地 Gradle ${distribution_version}，请先运行 ./build-debug.sh 完成依赖下载。" >&2
    exit 1
fi

exec env \
    GRADLE_USER_HOME="$project_dir/.gradle-user-home" \
    ANDROID_USER_HOME="$project_dir/.android-user-home" \
    "$gradle_bin" \
    :app:testDebugUnitTest \
    :app:connectedDebugAndroidTest \
    --offline \
    --no-daemon \
    "$@"
