#!/bin/bash

ssh -L 8090:$1:8090 $2@$1
