db = new Mongo().getDB("example1");
db.foo.insert({"test":"test"});

db.createUser(
  {
    user: "app",
    pwd:  "password",
    roles: [
      { role: "readWrite", db: "example1" }
    ]
  }
);