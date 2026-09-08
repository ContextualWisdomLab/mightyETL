#!/bin/sh
set -eu
repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
verification_root=$(mktemp -d)
trap 'rm -rf "$verification_root"' EXIT HUP INT TERM
source_root="$repository_root/etl-service/src/main/java/com/xtrmetl/etl/stock_data"
test_root="$repository_root/etl-service/src/test/java/com/xtrmetl/etl/stock_data"
java -version
javac -Xlint:all -Werror -d "$verification_root/classes" \
  "$source_root"/*.java "$test_root/StockDataContractChecks.java"
java -cp "$verification_root/classes" com.xtrmetl.etl.stock_data.StockDataContractChecks
javadoc -quiet -Werror -Xdoclint:all -d "$verification_root/javadoc" "$source_root"/*.java
