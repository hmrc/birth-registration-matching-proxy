#!/usr/bin/env bash

sbt clean scalafmtAll compile coverage test coverageOff dependencyUpdates coverageReport
