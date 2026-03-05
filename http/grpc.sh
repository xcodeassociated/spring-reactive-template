#!/bin/bash

# Unary
grpcurl -d '{ "data":"123" }' -plaintext localhost:9080 com.softeno.template.grpc.SampleGrpcService.Echo

# Server Streaming
grpcurl -plaintext \
  -d '{ "data":"hello" }' \
  localhost:9080 \
  com.softeno.template.grpc.SampleGrpcService.EchoServerStream

# Client Streaming: { "data": "one" } <enter> (...) ctrl-d
grpcurl -plaintext \
  -d @ \
  localhost:9080 \
  com.softeno.template.grpc.SampleGrpcService.EchoClientStream

#Bidirectional Streaming
grpcurl -plaintext \
  -d @ \
  localhost:9080 \
  com.softeno.template.grpc.SampleGrpcService.EchoBidirectional
