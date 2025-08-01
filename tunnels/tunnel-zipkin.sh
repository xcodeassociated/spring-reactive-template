#!/bin/bash

ssh -L 9411:$1:9411 $2@$1

