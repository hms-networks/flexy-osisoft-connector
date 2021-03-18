#!/bin/bash

RELEASE_DIR="./osisoft_connector/"
RELEASE_JS="${RELEASE_DIR}js/"
RELEASE_CSS="${RELEASE_DIR}css/"

mkdir "$RELEASE_DIR"
mkdir "$RELEASE_JS"
mkdir "$RELEASE_CSS"

cp ./config/* "$RELEASE_DIR"
cp ./web/js/external/* "$RELEASE_JS"
cp ./web/bootstrap/js/*min.js "$RELEASE_JS"
cp ./web/bootstrap/css/*min.css "$RELEASE_CSS"
cp ./web/html/*.html "$RELEASE_DIR"
cp ./mini_css/*min.css "$RELEASE_CSS"
cp ./mini_js/*min.js "$RELEASE_JS"
cp ./build/*.jar "$RELEASE_DIR"
cp ./scripts/* "$RELEASE_DIR"
mv ./docs/CHANGELOG.md ./