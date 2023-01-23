#!/bin/bash

echo ">> starting mongo replica set init..."
echo "rs.initiate(
  {
     _id: \"rs0\",
     members: [
        { _id: 0, host: \"mongo_rs_primary:27017\", priority: 1000 }
     ]
  }
);

db = new Mongo().getDB(\"example1\");
db.foo.insert({\"test\":\"test\"});

db.createUser(
 {
   user: \"app\",
   pwd:  \"password\",
   roles: [
     { role: \"readWrite\", db: \"example1\" }
   ]
 }
);
" | docker exec -i mongo_rs_primary mongosh --eval

echo ">> mongo rs init done, getting status..."
echo "rs.status();" | docker exec -i mongo_rs_primary mongosh --eval

echo ">> getting mongo rs dbs: "
echo "show dbs;" | docker exec -i mongo_rs_primary mongosh --eval

echo ">> return"
