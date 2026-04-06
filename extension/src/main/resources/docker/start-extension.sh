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

CONF_FOLDER="/config"

if [ -f "${CONF_FOLDER}/logback.xml" ]; then
  LOGGING_CONFIG="${CONF_FOLDER}/logback.xml"
fi

if [ -n "${LOGGING_CONFIG}" ]; then
  JAVA_OPTS="${JAVA_OPTS} -Dlogging.config=${LOGGING_CONFIG}"
fi

echo "Starting ThingsBoard Extension ..."

cd /home/thingsboard

# shellcheck disable=SC2086
exec java ${JAVA_OPTS} -jar thingsboard-extension.jar
