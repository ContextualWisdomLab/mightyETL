# Documentation Testing Guide

## Overview

Comprehensive unit tests have been generated for all documentation files
added in the recent documentation initiative. These tests ensure
documentation quality, accuracy, and consistency.

## Test Suite Location

**Primary Test File:**
`etl-service/src/test/java/com/xtrmetl/etl/documentation/DocumentationValidationTest.java`

For detailed information about the tests, including what each test validates
and how to run them, see:
[Test Documentation README](etl-service/src/test/java/com/xtrmetl/etl/documentation/README.md)

## Quick Start

Run all documentation tests:

```bash
cd etl-service
mvn test -Dtest=DocumentationValidationTest
```
