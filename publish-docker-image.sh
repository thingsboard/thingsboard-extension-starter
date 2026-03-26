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
# Pushes the Docker image to a container registry.
#
# Usage:
#   ./publish-docker-image.sh
#
# Environment variables:
#   IMAGE_NAME  Docker image name (default: thingsboard-extension)
#   REGISTRY    Registry hostname prefix (optional). When set, the image
#               is tagged as REGISTRY/IMAGE_NAME before pushing.
#
# Examples:
#   # Docker Hub (no REGISTRY needed, IMAGE_NAME includes your Docker Hub user)
#   IMAGE_NAME=myuser/thingsboard-extension ./publish-docker-image.sh
#
#   # Private registry
#   REGISTRY=registry.example.com ./publish-docker-image.sh
#

set -e

cd "$(dirname "$0")"

IMAGE="${IMAGE_NAME:-thingsboard-extension}"
VERSION=${VERSION:-$(git describe --tags --abbrev=0 2>/dev/null || git rev-parse --short HEAD)}

if [ -n "${REGISTRY}" ]; then
    REMOTE="${REGISTRY}/${IMAGE}"
else
    REMOTE="${IMAGE}"  # Docker Hub: no prefix required
fi

if ! docker image inspect "${IMAGE}:${VERSION}" > /dev/null 2>&1; then
    echo "Error: Image ${IMAGE}:${VERSION} not found. Run ./build-docker-image.sh first."
    exit 1
fi

docker tag "${IMAGE}:${VERSION}" "${REMOTE}:${VERSION}"
docker tag "${IMAGE}:latest" "${REMOTE}:latest"

docker push "${REMOTE}:${VERSION}"
docker push "${REMOTE}:latest"

echo ""
echo "Published:"
echo "  ${REMOTE}:${VERSION}"
echo "  ${REMOTE}:latest"
