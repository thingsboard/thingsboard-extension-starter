#!/bin/bash
#
# Copyright © 2026-2026 ThingsBoard, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

cd "$(dirname "$0")"

echo "Starting ThingsBoard Extension..."

# Install sibling modules (e.g. examples) into the local repo so the
# extension can resolve them when run via -f below. Harmless if there
# are no siblings — installs only the extension jar in that case.
./mvnw -pl extension -am install -DskipTests -q

# Run extension directly via -f so spring-boot:run isn't applied to the
# parent pom (which would fail with "Unable to find a suitable main class").
./mvnw -f extension/pom.xml spring-boot:run
