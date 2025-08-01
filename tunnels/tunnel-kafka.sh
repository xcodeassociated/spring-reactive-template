#!/bin/bash

ssh -L 9094:$1:9094 $2@$1

