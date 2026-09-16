#!/bin/sh
set -eu

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

MAVEN_VERSION=3.9.11
CACHE_DIR="${HOME}/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
MAVEN_HOME="${CACHE_DIR}/apache-maven-${MAVEN_VERSION}"
ARCHIVE="${CACHE_DIR}/apache-maven-${MAVEN_VERSION}-bin.zip"
TEMP_ARCHIVE="${ARCHIVE}.part"
URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip"

if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  mkdir -p "${CACHE_DIR}"

  if [ -f "${ARCHIVE}" ] && ! unzip -tq "${ARCHIVE}" >/dev/null 2>&1; then
    echo "Cache do Maven corrompido detectado. Removendo ${ARCHIVE}"
    rm -f "${ARCHIVE}"
  fi

  if [ ! -f "${ARCHIVE}" ]; then
    rm -f "${TEMP_ARCHIVE}"
    echo "Baixando Maven ${MAVEN_VERSION}..."

    if command -v curl >/dev/null 2>&1; then
      curl -fL "${URL}" -o "${TEMP_ARCHIVE}"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "${TEMP_ARCHIVE}" "${URL}"
    else
      echo "Maven nao encontrado e curl/wget indisponiveis para baixar o Maven ${MAVEN_VERSION}." >&2
      exit 1
    fi

    if ! unzip -tq "${TEMP_ARCHIVE}" >/dev/null 2>&1; then
      rm -f "${TEMP_ARCHIVE}"
      echo "O arquivo baixado nao e um ZIP Maven valido: ${URL}" >&2
      exit 1
    fi

    mv "${TEMP_ARCHIVE}" "${ARCHIVE}"
  fi

  rm -rf "${MAVEN_HOME}"
  unzip -q -o "${ARCHIVE}" -d "${CACHE_DIR}"
fi

exec "${MAVEN_HOME}/bin/mvn" "$@"
