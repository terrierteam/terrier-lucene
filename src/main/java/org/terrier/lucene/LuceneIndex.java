package org.terrier.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;
import org.terrier.structures.BasicDocumentIndexEntry;
import org.terrier.structures.BasicLexiconEntry;
import org.terrier.structures.BitIndexPointer;
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
import org.terrier.structures.postings.BlockPosting;
import org.terrier.structures.postings.IterablePosting;
import org.terrier.structures.postings.IterablePostingImpl;
import org.terrier.structures.postings.WritablePosting;

public class LuceneIndex extends Index {

    static final boolean DOCLEN_FROM_TERM_VECTORS = false;
    static final String DEFAULT_FIELD = "contents";

    class LuceneLexicon extends Lexicon<String> {
        @Override
        public void close() throws IOException {
        }

        @Override
        public Iterator<Entry<String, LexiconEntry>> iterator() {
            // TODO: this implementation doesnt stream, it loads into memory
            try {
                TermsEnum te = ir.terms(DEFAULT_FIELD).iterator();
                List<Entry<String, LexiconEntry>> terms = new ArrayList<>();
                while (te.next() != null) {
                    terms.add(makePair(te));
                }
                return terms.iterator();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        public int numberOfEntries() {
            return getCollectionStatistics().getNumberOfUniqueTerms();
        }

        LuceneLexiconEntry entryFromTerm(Term t) {
            try {
                long F;
                if ((F = ir.totalTermFreq(t)) == (long) 0)
                    return null;
                final LuceneLexiconEntry lie = new LuceneLexiconEntry();
                lie.t = t;
                lie.setStatistics(ir.docFreq(t), (int) F);
                return lie;
            } catch (final IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        public LexiconEntry getLexiconEntry(final String term) {
            final Term t = new Term(DEFAULT_FIELD, term);
            return entryFromTerm(t);

        }

        @Override
        public Entry<String, LexiconEntry> getLexiconEntry(final int termid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entry<String, LexiconEntry> getIthLexiconEntry(final int index) {
            throw new UnsupportedOperationException();
        }

        Entry<String, LexiconEntry> makePair(TermsEnum te) throws IOException {

            LuceneLexiconEntry lie = new LuceneLexiconEntry();
            lie.t = new Term(DEFAULT_FIELD, te.term());
            lie.setStatistics(te.docFreq(), (int) te.totalTermFreq());
            return Pair.of(lie.t.text(), lie);
        }

        @Override
        public Iterator<Entry<String, LexiconEntry>> getLexiconEntryRange(final String from, final String to) {
            try {
                TermsEnum te = ir.terms(DEFAULT_FIELD).iterator();
                final Term firstTerm = new Term(DEFAULT_FIELD, from);
                TermsEnum.SeekStatus seek = te.seekCeil(firstTerm.bytes());
                List<Entry<String, LexiconEntry>> terms = new ArrayList<>();
                if (seek == TermsEnum.SeekStatus.END) {
                    return terms.iterator();
                }
                terms.add(makePair(te));

                while (te.next() != null) {
                    Entry<String, LexiconEntry> entry = makePair(te);
                    if (entry.getKey().compareTo(to) > 0) {
                        break;
                    }
                    terms.add(entry);
                }
                return terms.iterator();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    class PostingEnumIterablePosting extends IterablePostingImpl {
        PostingsEnum pe;
        NumericDocValues ndv;
        int docid;
        int f;

        public PostingEnumIterablePosting(PostingsEnum _pe, NumericDocValues _ndv) {
            pe = _pe;
            ndv = _ndv;
            if (pe == null)
                throw new IllegalArgumentException("PostingsEnum cannot be null");
        }

        @Override
        public void close() throws IOException {
        }

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
            try {
                if (DOCLEN_FROM_TERM_VECTORS)
                    return (int) ir.getTermVector(this.getId(), DEFAULT_FIELD).getSumTotalTermFreq();
                return (int) SmallFloat.byte4ToInt((byte) ndv.longValue());
                // return getDocumentIndex().getDocumentLength(this.getId());
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

    class PositionsPostingEnumIterablePosting extends PostingEnumIterablePosting implements BlockPosting {
        int[] positions;

        public PositionsPostingEnumIterablePosting(PostingsEnum _pe, NumericDocValues _ndv) {
            super(_pe, _ndv);
        }

        @Override
        public int[] getPositions() {
            try {
                if (positions == null)
                    positions = new int[f];
                for (int p = 0; p < f; p++)
                    positions[p] = pe.nextPosition();
                return positions;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        public int next() throws IOException {
            positions = null;
            return super.next();
        }

    }

    final LeafReader ir;
    final boolean blocks;
    final String loc;

    public LuceneIndex(LeafReader _lr, String _loc) {
        this.ir = _lr;
        this.loc = _loc;
        blocks = ir.getFieldInfos().hasProx();
        if (ir.getFieldInfos().fieldInfo(DEFAULT_FIELD) == null)
            throw new IllegalArgumentException(
                    "We assume that the Lucene index should have a field named 'contents' for the text of the documents");
        if (ir.getFieldInfos().fieldInfo("id") == null)
            throw new IllegalArgumentException(
                    "We assume that the Lucene index should have a field named 'id' for the docnos");
        try {
            if (DOCLEN_FROM_TERM_VECTORS && ir.getTermVector(0, DEFAULT_FIELD) == null)
                throw new IllegalArgumentException(
                        "We assume that the Lucene index should have term vectors in order to get document lengths");
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
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
        try {
            assert ir.terms(DEFAULT_FIELD) != null;

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
        // only DirectLuceneIndex implements a DirectIndex
        return null;
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

                // TODO not sure this works.
                // Terms terms = ir.getTermVector(docid, DEFAULT_FIELD);
                // if (terms == null) {
                // return 0;
                // }
                // long total = terms.getSumTotalTermFreq();
                // long length = SmallFloat.longToInt4(total);
                // return (int) length;
                // throw new UnsupportedOperationException();
                return 0;
            }

            @Override
            public DocumentIndexEntry getDocumentEntry(final int docid) throws IOException {
                int numTerms = (int) ir.getTermVector(docid, DEFAULT_FIELD).size();
                return new LuceneDocumentIndexEntry(getDocumentLength(docid),
                        new SimpleBitIndexPointer((byte) 0, (long) docid, (byte) 0, numTerms), docid);
            }
        };
    }

    @Override
    public Object getIndexStructure(final String structureName) {
        switch (structureName) {
        case "lexicon":
            return getLexicon();
        case "document":
            return getDocumentIndex();
        case "direct":
            return getDirectIndex();
        case "inverted":
            return getInvertedIndex();
        case "meta":
            return getMetaIndex();
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
            public void close() throws IOException {
            }

            @Override
            public IterablePosting getPostings(Pointer _lEntry) throws IOException {
                LuceneLexiconEntry lEntry = (LuceneLexiconEntry) _lEntry;
                PostingsEnum pe = null;
                if (blocks) {
                    pe = MultiTerms.getTermPostingsEnum(ir, DEFAULT_FIELD, new BytesRef(lEntry.t.text())
                    /* , PostingsEnum.FREQS & PostingsEnum.POSITIONS */ // these arent needed it seem
                    );
                    // pe = ir.postings(lEntry.t, PostingsEnum.FREQS & PostingsEnum.POSITIONS);
                    return new PositionsPostingEnumIterablePosting(pe, ir.getNormValues(DEFAULT_FIELD));
                }
                pe = MultiTerms.getTermPostingsEnum(ir, DEFAULT_FIELD, new BytesRef(lEntry.t.text()));
                // pe = ir.postings(lEntry.t);
                return new PostingEnumIterablePosting(pe, ir.getNormValues(DEFAULT_FIELD));
            }
        };
    }

    static class LuceneDocumentIndexEntry extends BasicDocumentIndexEntry {
        int docid;

        public LuceneDocumentIndexEntry(int length, BitIndexPointer pointer, int docid) {
            super(length, pointer);
            this.docid = docid;
        }

        public LuceneDocumentIndexEntry(int length, byte fileId, long byteOffset, byte bitOffset, int numberOfTerms,
                int docid) {
            super(length, fileId, byteOffset, bitOffset, numberOfTerms);
            this.docid = docid;
        }
    }

    static class LuceneLexiconEntry extends BasicLexiconEntry {
        private static final long serialVersionUID = 1L;
        Term t;
    }

    @Override
    public Lexicon<String> getLexicon() {
        return new LuceneLexicon();
    }

    @Override
    public MetaIndex getMetaIndex() {
        return new MetaIndex() {

            @Override
            public void close() throws IOException {
            }

            @Override
            public String[] getKeys() {
                return new String[] { "docno" };
            }

            // TODO - these could go as default implementation in MetaIndex.
            @Override
            public String[][] getItems(String[] Keys, int[] docids) throws IOException {
                String[][] rtr = new String[Keys.length][];
                for (int i = 0; i < Keys.length; i++) {
                    rtr[i] = getItems(Keys[i], docids);
                }
                return rtr;
            }

            @Override
            public String[] getItems(String[] Keys, int docid) throws IOException {
                String[] rtr = new String[Keys.length];
                for (int i = 0; i < Keys.length; i++) {
                    rtr[i] = getItem(Keys[i], docid);
                }
                return rtr;
            }

            @Override
            public String[] getItems(String Key, int[] docids) throws IOException {
                String[] rtr = new String[docids.length];
                for (int i = 0; i < docids.length; i++) {
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
                return new String[] { getItem("docno", docid) };
            }
        };
    }

    @Override
    public String toString() {
        return LuceneIndexFactory.PREFIX + loc;
    }

    @Override
    public boolean hasIndexStructure(String structureName) {
        switch (structureName) {
            case "lexicon": return true;
            case "inverted": return true;
            case "meta": return true;
            case "document": return true;        
            default: return false;
        }
    }

    


}
