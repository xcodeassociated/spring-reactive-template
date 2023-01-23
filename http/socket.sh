#java -jar rsc/rsc-0.9.1.jar --data '{"symbol": "aaa"}' --route stock --stream ws://localhost:8081/rsocket
#java -jar rsc/rsc-0.9.1.jar --data '{"symbol": "aaa"}' --route stock.error  ws://localhost:8081/rsocket
#java -jar rsc/rsc-0.9.1.jar --data '{"symbol": "aaa"}' --route stock.coroutine  ws://localhost:8081/rsocket
#java -jar rsc/rsc-0.9.1.jar --sm "client-id\"123"  --smmt "application/json" --data '{"symbol": "aaa"}' --route stock.single  ws://localhost:8081/rsocket
#java -jar rsc/rsc-0.9.1.jar --channel --route number.channel --data -  ws://localhost:8081/rsocket

java -jar rsc/rsc-0.9.1.jar --channel --route chat.channel --data -  ws://localhost:8081/rsocket