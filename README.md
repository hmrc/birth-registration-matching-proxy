# birth-registration-matching-proxy

[![Build Status](https://travis-ci.org/hmrc/birth-registration-matching-proxy.svg)](https://travis-ci.org/hmrc/birth-registration-matching-proxy) [![Download](https://api.bintray.com/packages/hmrc/releases/birth-registration-matching-proxy/images/download.svg)](https://bintray.com/hmrc/releases/birth-registration-matching-proxy/_latestVersion)

## Running

```bash
sbt -Dmicroservice.services.birth-registration-matching.username=XXXX -Dmicroservice.services.birth-registration-matching.key=XXXX "run 9006"
```

## API Documentation

Base endpoint ```/birth-registration-matching-proxy```

| PATH | Method | Description |
| ---- | ------ | ----------  |
| ```/match/:ref``` | ```GET``` | Return a childs record for the birth reference number ```:ref``` (England and Wales) |


## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
