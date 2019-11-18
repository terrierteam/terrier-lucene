
# Terrier-Lucene

This package intends to allow Terrier to read an index created by Lucene. Our particular use case is indices created by Anserini.

## Requirements

- You need the current Github version of Terrier, i.e. 5.2-SNAPSHOT
- You need an Anserini index

## Compiling

Usual Maven installation method
```
mvn -DskipTests install
```

## Usage

In general, once the terrier-lucene package is available through a Maven repository (i.e. `mvn install`) has been exectuted, then the usage is two-fold:
1. Telling Terrier to load the terrier-lucene package

2. Telling Terrier the location of the index. The location should have a "lucene:" prefix.

Example usages follow below.

### Printing index statistics
```
bin/terrier indexstats -P org.terrier:terrier-lucene:0.0.1-SNAPSHOT -I lucene:/Users/craigm/Documents/2019/TREC2019/anserini/index_vaswani/
```

### Interactive Retrival
```
bin/terrier interactive -P org.terrier:terrier-lucene:0.0.1-SNAPSHOT -I lucene:/Users/craigm/Documents/2019/TREC2019/anserini/index_vaswani/
```


## Contributors

Craig Macdonald, University of Glasgow

## TODO

- How is an IndexRef passed to MultiIndex?
- Does MultiIndex record the mulitple "pointers" appropriately
- docids - do they need to be aligned for multiple leaf lucene indices?
- better unit testing
- direct index support
