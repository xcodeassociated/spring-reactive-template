#!/bin/bash

echo ">> down started..."
docker-compose -f ./docker-compose.yml down && rm -rf volumes/mongodb && mkdir volumes/mongodb
echo ">> finished"