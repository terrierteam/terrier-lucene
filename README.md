[![](https://jitpack.io/v/terrierteam/terrier-lucene.svg)](https://jitpack.io/#terrierteam/terrier-lucene)

# Terrier-Lucene

This package intends to allow Terrier to read an index created by Lucene. Our particular use case is indices created by Anserini.

## Requirements

- You need Terrier 5.3 or newer
- You need a Lucene index, in particular with an "id" field (containing the docno of the document) and a "contents" textual field.

## Usage

You can use terrier-lucene without installing it. Terrier can pick it up from the Jitpack Maven repository. To do so, ensure that your terrier commands have `-P com.github.terrierteam:terrier-lucene:-SNAPSHOT` in the argument list. 

In general, once the terrier-lucene package is available through a Maven repository (i.e. `mvn install`) has been exectuted, then the usage is two-fold:
1. Telling Terrier to load the terrier-lucene package

2. Telling Terrier the location of the index. The location should have a "lucene:" prefix. If you need a direct index, and the Lucene index has been generated using term vectors, you should used the "directlucene:" prefix.

Example usages follow below.

### Printing index statistics
```
bin/terrier indexstats -P com.github.terrierteam:terrier-lucene:-SNAPSHOT -I lucene:/path/to/lucene/index/
```

### Interactive Retrival
```
bin/terrier interactive -P com.github.terrierteam:terrier-lucene:-SNAPSHOT -I lucene:/path/to/lucene/index/
```

(The -P option was added in Terrier 5.2; the ability to omit the version number was added in 5.3; using Jitpack was also added in 5.3)

## Batch Retrieval
```
bin/terrier batchretrieve  -P com.github.terrierteam:terrier-lucene:-SNAPSHOT -I lucene:/path/to/lucene/index/ -t /path/to/topics.trec
```


## Contributors

Code: Craig Macdonald, University of Glasgow

Useful debugging and input from Jimmy Lin, University of Waterloo

## TODO and Known Issues

- We do not have any correspondence between Lucene fields and Terrier fields. This is not planned for the foreseeable future.
