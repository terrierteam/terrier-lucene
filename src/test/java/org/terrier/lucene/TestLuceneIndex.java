
package org.terrier.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.terrier.querying.IndexRef;
import org.terrier.structures.DocumentIndexEntry;
import org.terrier.structures.Index;
import org.terrier.structures.IndexFactory;
import org.terrier.structures.LexiconEntry;
import org.terrier.structures.postings.BlockPosting;
import org.terrier.structures.postings.IterablePosting;
import org.terrier.structures.postings.PostingUtil;
import org.terrier.tests.ApplicationSetupBasedTest;

import gnu.trove.TIntHashSet;

public class TestLuceneIndex extends ApplicationSetupBasedTest
{
    static final String[] DOCS = new String[]{"hello there fox", "the lazy fox"};
    static final String[] DOCNOS = new String[]{"doc1", "doc2"};

    @Rule public TemporaryFolder tempLocation = new TemporaryFolder();

    int[] FOX_DOCIDS = new int[]{
        0, 1,
    };

    @Test public void testBasicIndexFactory() throws Exception
    {
        Path indexLoc = new LuceneIndexTestUtils(tempLocation, false, LuceneIndex.DOCLEN_FROM_TERM_VECTORS).makeIndex(
            DOCS,
            DOCNOS);
        Index index = IndexFactory.of(IndexRef.of(LuceneIndexFactory.PREFIX + indexLoc.toString()));
        assertNotNull(index);
        assertTrue(index instanceof LuceneIndex);
        LuceneIndex li = (LuceneIndex)index;
        checkIndex(li);
        index.close();
        
    }

    @Test public void testBasic() throws Exception
    {
        IndexReader ir = new LuceneIndexTestUtils(tempLocation, false, LuceneIndex.DOCLEN_FROM_TERM_VECTORS).makeIndexReader(
            DOCS,
            DOCNOS);
        
        LuceneIndex index = new LuceneIndex(ir.leaves().get(0).reader());
        assertFalse(index.blocks);
        checkIndex(index);
        index.close();
        
    }

    @Test public void testBlocks() throws Exception
    {
        IndexReader ir = new LuceneIndexTestUtils(tempLocation, true, true)
            .makeIndexReader(DOCS,DOCNOS);
        assertEquals(1, ir.leaves().size());

        PostingsEnum pe = MultiTerms.getTermPostingsEnum(ir, "contents", new BytesRef("fox"));//, PostingsEnum.FREQS & PostingsEnum.POSITIONS);
        assertEquals(0, pe.nextDoc());
        assertEquals(0, pe.docID());
        assertEquals(1, pe.freq());
        assertEquals(2, pe.nextPosition());
        assertEquals(1, pe.nextDoc());
        assertEquals(1, pe.docID());
        assertEquals(1, pe.freq());
        assertEquals(2, pe.nextPosition());
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, pe.nextDoc());

        LuceneIndex index = new DirectLuceneIndex(ir.leaves().get(0).reader());
        assertTrue(index.blocks);
        checkIndex(index);

        //check positions in inverted index.
        IterablePosting ip = index.getInvertedIndex().getPostings(index.getLexicon().getLexiconEntry("fox"));
        assertTrue(ip instanceof BlockPosting);
        BlockPosting bp = (BlockPosting)ip;
        assertEquals(0, ip.next());
        assertEquals(2, bp.getPositions()[0]);
        assertEquals(1, ip.next());
        assertEquals(2, bp.getPositions()[0]);
        assertEquals(IterablePosting.EOL, ip.next());
        
        //check direct index works
        checkDocContents(0, index, new String[]{"hello", "there", "fox"});
        checkDocContents(1, index, new String[]{"the", "lazy", "fox"});

        //check positions in direct index.
        IterablePosting ip0 = index.getDirectIndex().getPostings(index.getDocumentIndex().getDocumentEntry(0));
        assertTrue(ip0 instanceof BlockPosting);
        int foxTermId = index.getLexicon().getLexiconEntry("fox").getTermId();
        while(ip.next() < foxTermId)
        {}
        if (ip.getId() == foxTermId)
        {
            assertEquals(foxTermId, ip0.next(foxTermId));
            assertEquals(2, ((BlockPosting)ip0).getPositions()[0]);
        }
        index.close();
    }

    @Test public void testBasicDirect() throws Exception
    {
        IndexReader ir = new LuceneIndexTestUtils(tempLocation, false, true).makeIndexReader(
            DOCS,
            DOCNOS);
        
        LuceneIndex index = new DirectLuceneIndex(ir.leaves().get(0).reader());
        checkIndex(index);
        
        checkDocContents(0, index, new String[]{"hello", "there", "fox"});
        checkDocContents(1, index, new String[]{"the", "lazy", "fox"});
        index.close();
    }

    static void checkDocContents(int docid, Index index, String[] terms) throws Exception {
        assertNotNull(index.getDirectIndex());
        DocumentIndexEntry die = index.getDocumentIndex().getDocumentEntry(docid);
        assertNotNull(die);
        IterablePosting ip = index.getDirectIndex().getPostings(die);
        assertNotNull(ip);
        TIntHashSet id1 = new TIntHashSet( PostingUtil.getIds(ip) );
        assertEquals(terms.length, id1.size());
        for(String t : terms)
        {
            LexiconEntry le = index.getLexicon().getLexiconEntry(t);
            assertNotNull("LexiconEntry for term " + t + " not found", le);
            assertTrue("Term " + t + " is missing in document " +docid, id1.contains(le.getTermId()));
        }
    }
 
    private void checkIndex(LuceneIndex index) throws IOException {
        assertNotNull(index.getCollectionStatistics());
        assertNotNull(index.getLexicon());
        assertNotNull(index.getInvertedIndex());
        assertNotNull(index.getMetaIndex());     
        assertEquals(2, index.getCollectionStatistics().getNumberOfDocuments());

        LexiconEntry le = index.getLexicon().getLexiconEntry("fox");
        assertNotNull(le);
        assertEquals(2, le.getDocumentFrequency());
        assertEquals(2, le.getFrequency());
        //unlikely to be present
        //assertEquals(1, le.getMaxFrequencyInDocuments());

        Iterator<Entry<String, LexiconEntry>> iter = index.getLexicon().iterator();
       //"fox", "hello", "lazy", "the", "there"
        assertTrue(iter.hasNext());
        assertEquals("fox", iter.next().getKey());
        assertTrue(iter.hasNext());
        assertEquals("hello", iter.next().getKey());
        assertTrue(iter.hasNext());
        assertEquals("lazy", iter.next().getKey());
        assertTrue(iter.hasNext());
        assertEquals("the", iter.next().getKey());
        assertTrue(iter.hasNext());
        assertEquals("there", iter.next().getKey());
        assertFalse(iter.hasNext());

        Iterator<Entry<String, LexiconEntry>> iterRange = index.getLexicon().getLexiconEntryRange("l", "m");
        assertTrue(iterRange.hasNext());
        assertEquals("lazy", iterRange.next().getKey());
        assertFalse(iterRange.hasNext());

        //check documentindex
        assertNotNull(index.getDocumentIndex());
        // assertEquals(3, index.getDocumentIndex().getDocumentEntry(0).getDocumentLength());
        // assertEquals(3, index.getDocumentIndex().getDocumentEntry(1).getDocumentLength());
        // assertEquals(3, index.getDocumentIndex().getDocumentLength(0));
        // assertEquals(3, index.getDocumentIndex().getDocumentLength(1));
        
        

        IterablePosting ip = index.getInvertedIndex().getPostings(le);
        assertNotNull(ip);
        
        assertEquals(FOX_DOCIDS[0], ip.next());
        assertEquals(FOX_DOCIDS[0], ip.getId());
        assertEquals(3, ip.getDocumentLength());
        assertEquals(FOX_DOCIDS[1], ip.next());
        assertEquals(FOX_DOCIDS[1], ip.getId());
        assertEquals(3, ip.getDocumentLength());
        assertEquals(IterablePosting.EOL, ip.next());
        
        assertEquals("doc1", index.getMetaIndex().getItem("docno", 0));
        assertEquals("doc2", index.getMetaIndex().getItem("docno", 1));
    }
}