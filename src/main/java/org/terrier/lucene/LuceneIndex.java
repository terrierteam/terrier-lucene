package org.terrier.lucene;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.SmallFloat;
import org.terrier.structures.BasicDocumentIndexEntry;
import org.terrier.structures.BasicLexiconEntry;
import org.terrier.structures.CollectionStatistics;
import org.terrier.structures.DocumentIndex;
import org.terrier.structures.DocumentIndexEntry;
import org.terrier.structures.Index;
import org.terrier.structures.Lexicon;
import org.terrier.structures.LexiconEntry;
import org.terrier.structures.MetaIndex;
import org.terrier.structures.Pointer;
import org.terrier.structures.PostingIndex;
import org.terrier.structures.SimpleBitIndexPointer;
import org.terrier.structures.postings.BasicPostingImpl;
import org.terrier.structures.postings.IterablePosting;
import org.terrier.structures.postings.IterablePostingImpl;
import org.terrier.structures.postings.WritablePosting;

public class LuceneIndex extends Index {

    static final String DEFAULT_FIELD = "contents";

    class PostingEnumIterablePosting extends IterablePostingImpl {
        PostingsEnum pe;
        NumericDocValues ndv;
        int docid;
        int f;

        public PostingEnumIterablePosting(PostingsEnum _pe, NumericDocValues _ndv) {
            pe = _pe;
            ndv = _ndv;
        }

        @Override
        public void close() throws IOException {}

        @Override
        public boolean endOfPostings() {
            return docid != EOL;
        }

        @Override
        public int next() throws IOException {
            docid = pe.nextDoc();
            if (docid == DocIdSetIterator.NO_MORE_DOCS)
                return docid = EOL;
            f = pe.freq();
            ndv.advance(docid);
            return docid;
        }

        @Override
        public WritablePosting asWritablePosting() {
            return new BasicPostingImpl(this.getId(), this.getFrequency());
        }

        @Override
        public int getDocumentLength() {
            try{
                return (int) SmallFloat.longToInt4(ndv.longValue());
                //return getDocumentIndex().getDocumentLength(this.getId());
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        public int getFrequency() {
            return f;
        }

        @Override
        public int getId() {
            return docid;
        }

        @Override
        public void setId(int id) {
           throw new UnsupportedOperationException();
        }


    }

    LeafReader ir;

    LuceneIndex(LeafReader _lr) {
        this.ir = _lr;
    }

    @Override
    public void close() throws IOException {
        ir.close();
    }

    @Override
    public void flush() throws IOException {
        
    }

    @Override
    public CollectionStatistics getCollectionStatistics() {
        try{
            final int numDocs = ir.numDocs();
            final int numTerms = (int) ir.terms(DEFAULT_FIELD).size();
            final long numTokens = ir.getSumTotalTermFreq(DEFAULT_FIELD);
            final long numPointers = ir.getSumDocFreq(DEFAULT_FIELD);
            final long[] fieldTokens = new long[0];
            final String[] fieldNames = new String[0];
            return new CollectionStatistics(numDocs, numTerms, numTokens, numPointers, fieldTokens, fieldNames);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Override
    public PostingIndex<?> getDirectIndex() {
        return null;
        // return new PostingIndex<BitIndexPointer>()
        // {
        // @Override
        // public void close() throws IOException {}

        // @Override
        // public IterablePosting getPostings(Pointer lEntry) throws IOException {
        // Document d = ir.document((int) ((BitIndexPointer) lEntry).getOffset());
        // //TODO make a IterablePosting from this
        // }

        // }
    }

    @Override
    public DocumentIndex getDocumentIndex() {
        return new DocumentIndex() {

            @Override
            public int getNumberOfDocuments() {
                return ir.numDocs();
            }

            @Override
            public int getDocumentLength(final int docid) throws IOException {
                
                //TODO not sure this works.
                Terms terms = ir.getTermVector(docid, DEFAULT_FIELD);
                if (terms == null)
                {
                    return 0;
                }
                long total = terms.getSumTotalTermFreq();
                long length = SmallFloat.longToInt4(total);
                return (int) length;

            }

            @Override
            public DocumentIndexEntry getDocumentEntry(final int docid) throws IOException {
                int numTerms = (int) ir.getTermVector(docid, DEFAULT_FIELD).size();
                return new BasicDocumentIndexEntry(
                    getDocumentLength(docid), 
                    new SimpleBitIndexPointer((byte)0, (long) docid, (byte)0, 
                        numTerms));
            }
        };
    }

    @Override
    public Object getIndexStructure(final String structureName) {
        switch (structureName) {
            case "lexicon": return getLexicon();
            case "document": return getDocumentIndex();
            case "direct": return getDirectIndex();
            case "inverted": return getInvertedIndex();
            case "meta": return getMetaIndex();
            default:
                break;
        }
        return null;
    }

    @Override
    public Object getIndexStructureInputStream(final String structureName) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public PostingIndex<?> getInvertedIndex() {
        return new PostingIndex<LuceneLexiconEntry>() {

            @Override
            public void close() throws IOException {}

            @Override
            public IterablePosting getPostings(Pointer _lEntry) throws IOException {
                LuceneLexiconEntry lEntry = (LuceneLexiconEntry)_lEntry;
                PostingsEnum pe = ir.postings(lEntry.t);

                return new PostingEnumIterablePosting(pe, ir.getNormValues(DEFAULT_FIELD));
            }

        };
    }

    

    static class LuceneLexiconEntry extends BasicLexiconEntry
    {
        private static final long serialVersionUID = 1L;
        Term t;
    }

    @Override
    public Lexicon<String> getLexicon() {
        return new Lexicon<String>() {

            @Override
            public void close() throws IOException {}

            @Override
            public Iterator<Entry<String, LexiconEntry>> iterator() {
                return null;
            //     try{
            //     Terms terms = ir.terms(DEFAULT_FIELD);
            //     final TermsEnum te = terms.iterator();

            //     return new Iterator<Entry<String, LexiconEntry>>()
            //     {

            //     };     
            // }           
            }

            @Override
            public int numberOfEntries() {
                return getCollectionStatistics().getNumberOfUniqueTerms();
            }

            LexiconEntry entryFromTerm(Term t) {
                try{
                    long F;
                    if ( (F=ir.totalTermFreq(t)) == (long) 0)
                        return null;
                    final LuceneLexiconEntry lie = new LuceneLexiconEntry();
                    lie.t = t;
                    lie.setStatistics(ir.docFreq(t), (int)F);
                    return lie;
                } catch (final IOException ioe ) {
                    throw new RuntimeException(ioe);
                }
            }

            @Override
            public LexiconEntry getLexiconEntry(final String term) {
                final Term t = new Term(DEFAULT_FIELD, term);
                return  entryFromTerm(t);  
                
            }

            @Override
            public Entry<String, LexiconEntry> getLexiconEntry(final int termid) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Entry<String, LexiconEntry> getIthLexiconEntry(final int index) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Iterator<Entry<String, LexiconEntry>> getLexiconEntryRange(final String from, final String to) {
                // TODO Auto-generated method stub
                return null;
            }

        };
    }

    @Override
    public MetaIndex getMetaIndex() {
        return new MetaIndex(){
        
            @Override
            public void close() throws IOException {}
        
            @Override
            public String[] getKeys() {
                return new String[]{"docno"};
            }
        
            //TODO - these could go as default implementation in MetaIndex.
            @Override
            public String[][] getItems(String[] Keys, int[] docids) throws IOException {
                String[][] rtr = new String[Keys.length][];
                for(int i=0;i<Keys.length;i++)
                {
                    rtr[i] = getItems(Keys[i], docids);
                }
                return rtr;
            }
        
            @Override
            public String[] getItems(String[] Keys, int docid) throws IOException {
                String[] rtr = new String[Keys.length];
                for(int i=0;i<Keys.length;i++)
                {
                    rtr[i] = getItem(Keys[i], docid);
                }
                return rtr;
            }
        
            @Override
            public String[] getItems(String Key, int[] docids) throws IOException {
                String[] rtr = new String[docids.length];
                for(int i=0;i<docids.length;i++)
                {
                    rtr[i] = getItem(Key, docids[i]);
                }
                return rtr;
            }
        
            @Override
            public String getItem(String Key, int docid) throws IOException {
                return ir.document(docid).get("id");
            }
        
            @Override
            public int getDocument(String key, String value) throws IOException {
                return -1;
            }
        
            @Override
            public String[] getAllItems(int docid) throws IOException {
                // TODO Auto-generated method stub
                return null;
            }
        };
    }

    @Override
    public String toString() {
        // TODO Auto-generated method stub
        return null;
    }

    


}