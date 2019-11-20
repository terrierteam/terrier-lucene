package org.terrier.lucene;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.rules.TemporaryFolder;

public class LuceneIndexTestUtils
{
    TemporaryFolder tempLocation;
    boolean positions;
    boolean direct;

    public LuceneIndexTestUtils(TemporaryFolder tf){
        this.tempLocation = tf;
    }
    public LuceneIndexTestUtils(TemporaryFolder _tf, boolean _positions, boolean _direct) {
        this(_tf);
        positions = _positions;
        direct = _direct;
    }

    // Path buildTestIndex(String[] docs, String[] docnos) throws IOException {
    //     Path indexDir = tempLocation.newFolder("index").toPath();
    //     Directory dir = FSDirectory.open(indexDir);
    
    //     Analyzer analyzer = new StandardAnalyzer(new CharArraySet(Arrays.asList(), true));
    //     IndexWriterConfig config = new IndexWriterConfig(analyzer);
    //     config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
    
    //     IndexWriter writer = new IndexWriter(dir, config);
    
    //     FieldType textOptions = new FieldType();
    //     textOptions.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
    //     textOptions.setStored(true);
    //     textOptions.setTokenized(true);
    //     textOptions.setStoreTermVectors(true);
    //     textOptions.setStoreTermVectorPositions(true);
    
    //     for(int i=0;i<docs.length;i++)
    //     {
    //         Document doc1 = new Document();
    //         doc1.add(new StringField("id", docnos[i], Field.Store.YES));
    //         doc1.add(new Field("contents", docs[i], textOptions));
    //         writer.addDocument(doc1);
    //     }
    
    //     writer.commit();
    //     writer.forceMerge(1);
    //     writer.close();
    //     return indexDir;
    // }

    // IndexReader buildTestIndexReader(String[] docs, String[] docnos) throws IOException {
    //     Path index = buildTestIndex(docs, docnos);
    //     System.err.println(index.toString());
    //     return DirectoryReader.open(FSDirectory.open(index));
    // }

    final Path makeIndex(String[] docs, String[] docnos) throws IOException {
    
        final StandardAnalyzer analyzer = new StandardAnalyzer();
        Path indexDir = tempLocation.newFolder("index").toPath();
        Directory index = FSDirectory.open(indexDir);
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        IndexWriter writer = new IndexWriter(index, config);

        FieldType type = new FieldType();
        type.setStored(true);
        type.setTokenized(true);
        if (positions)
            type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        else
           type.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        if (direct)
        {
            type.setStoreTermVectors(true);
            if (positions)
                type.setStoreTermVectorPositions(true);
        }

        for(int i=0;i<docs.length;i++)
        {
            addDoc(writer, type, docs[i], docnos[i]);
        }
        writer.commit();
        writer.forceMerge(1);
        writer.close();
        return indexDir;
    }
    
    final IndexReader makeIndexReader(String[] docs, String[] docnos) throws IOException {
        Path index = makeIndex(docs, docnos);
        System.err.println(index.toString());
        return DirectoryReader.open(FSDirectory.open(index));
    }

    void addDoc(IndexWriter w, FieldType type, String content, String docno) throws IOException {
        Document doc = new Document();
        doc.add(new Field(LuceneIndex.DEFAULT_FIELD, content, type));
        // Here, we use a string field for docno to avoid tokenizing.
        doc.add(new StringField("id", docno, Field.Store.YES));
        w.addDocument(doc);
    }

}

