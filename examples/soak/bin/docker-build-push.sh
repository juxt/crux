#!/usr/bin/env bash
cd $(dirname "$0")/..
ECR=955308952094.dkr.ecr.eu-west-2.amazonaws.com/crux-soak
lein uberjar
docker build -t $ECR:latest .
docker push $ECR:latest
cd -
