package org.terrier.lucene;

import java.io.IOException;
import java.util.Map.Entry;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.terrier.structures.Lexicon;
import org.terrier.structures.LexiconEntry;
import org.terrier.structures.Pointer;
import org.terrier.structures.PostingIndex;
import org.terrier.structures.postings.ArrayOfBasicIterablePosting;
import org.terrier.structures.postings.ArrayOfBlockIterablePosting;
import org.terrier.structures.postings.IterablePosting;

import gnu.trove.TIntArrayList;

public class DirectLuceneIndex extends LuceneIndex {

    BidiMap<String, Integer> term2termid = new DualHashBidiMap<>();
    BidiMap<Integer, String> termid2term;

    public DirectLuceneIndex(LeafReader _lr) {
        super(_lr);
        if (super.getCollectionStatistics().getNumberOfDocuments() == 0)
        {
            throw new UnsupportedOperationException("zero document indices not supported");
        }
        try {
            //check that the lucene index is suitable
            if (ir.getTermVectors(0) == null)
            {
                throw new IllegalArgumentException("Index has no term vectors (aka direct index)");
            }

            //build a mapping from term <-> "termids"
            Terms terms = ir.terms(DEFAULT_FIELD);
            TermsEnum tes = terms.iterator();
            int id = 0;
            while (tes.next() != null) {
                String term = tes.term().utf8ToString();
                term2termid.put(term, id++);
            }
            termid2term = term2termid.inverseBidiMap();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    class DirectLuceneLexicon extends LuceneLexicon {

        @Override
        LuceneLexiconEntry entryFromTerm(Term t) {
            LuceneLexiconEntry lle = super.entryFromTerm(t);
            //term not found
            if (lle == null)
                return null;
            lle.termId = term2termid.get(t.text());
            return lle;
        }

        @Override
        public Entry<String, LexiconEntry> getLexiconEntry(int termid) {
            String term = termid2term.get(termid);
            return Pair.of(term,super.getLexiconEntry(term));
        }

    }

    @Override
    public Lexicon<String> getLexicon() {
        return new DirectLuceneLexicon();
    }

    @Override
    public PostingIndex<?> getDirectIndex() {
        return new PostingIndex<LuceneDocumentIndexEntry>() {

            @Override
            public void close() throws IOException {}

            @Override
            public IterablePosting getPostings(Pointer pointer) throws IOException {
                int docid = ((LuceneDocumentIndexEntry)pointer).docid;
                
                Terms t = ir.getTermVector(docid, DEFAULT_FIELD);
                TermsEnum iterator = t.iterator();
                TIntArrayList termids = new TIntArrayList();
                TIntArrayList freqs = new TIntArrayList();
                TIntArrayList positions = new TIntArrayList();
                PostingsEnum p = null;
                while(iterator.next() != null)
                {
                    String term = iterator.term().utf8ToString();
                    termids.add(term2termid.get(term));
                    p = iterator.postings( p, PostingsEnum.ALL );
                    p.nextDoc();
                    final int f = p.freq();

                    //should this be totalTermFreq()?
                    //final int f = iterator.docFreq();
                    freqs.add(f);
                    if (blocks) {
                        for(int pi=0;pi<f;pi++)
                            positions.add(p.nextPosition());
                    }
                }
                if (blocks)
                    return new ArrayOfBlockIterablePosting(termids.toNativeArray(), freqs.toNativeArray(), freqs.toNativeArray(), positions.toNativeArray());
                return new ArrayOfBasicIterablePosting(termids.toNativeArray(), freqs.toNativeArray());
            }
            
        };
    }   
}