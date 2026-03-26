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

#
# Builds a Docker image for the ThingsBoard extension service.
#
# Usage:
#   ./build-docker-image.sh
#
# Environment variables:
#   IMAGE_NAME  Docker image name (default: thingsboard-extension)
#
# The version tag comes from the latest git tag, or the short git SHA
# if no tag exists. Two tags are created: IMAGE:VERSION and IMAGE:latest.
#
# Example:
#   IMAGE_NAME=myorg/thingsboard-extension ./build-docker-image.sh
#

set -e

cd "$(dirname "$0")"

IMAGE="${IMAGE_NAME:-thingsboard-extension}"
VERSION=${VERSION:-$(git describe --tags --abbrev=0 2>/dev/null || git rev-parse --short HEAD)}

echo "Building JAR..."
./mvnw package -DskipTests -q

docker build -t "${IMAGE}:${VERSION}" -t "${IMAGE}:latest" .

echo ""
echo "Built successfully:"
echo "  ${IMAGE}:${VERSION}"
echo "  ${IMAGE}:latest"
echo ""
echo "Next step: push with ./publish-docker-image.sh"
