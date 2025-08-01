#!/bin/bash

ssh -L 27017:$1:27017 $2@$1

