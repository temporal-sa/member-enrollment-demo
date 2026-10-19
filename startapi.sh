#!/bin/bash

ENCRYPT_PAYLOADS=${ENCRYPT_PAYLOADS:-true} ./gradlew bootRun --args='--spring.profiles.active=api'