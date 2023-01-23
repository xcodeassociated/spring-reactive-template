#!/bin/bash

echo ">> starting..."
docker-compose -f ./docker-compose.yml up -d && sleep 2 && ./init.sh
echo ">> finished"
