
package org.terrier.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.terrier.structures.Index;
import org.terrier.structures.LexiconEntry;
import org.terrier.structures.postings.IterablePosting;
import org.terrier.tests.ApplicationSetupBasedTest;

public class TestLuceneIndex extends ApplicationSetupBasedTest
{
    int[] NEWS_DOCIDS = new int[]{
        5679, 6056,
    };

    @Test public void testA() throws Exception
    {
        Index index = LuceneIndexFactory.loadLuceneIndex("/Users/craigm/Documents/2019/TREC2019/anserini/index_vaswani/");
        
        assertNotNull(index.getCollectionStatistics());

        assertNotNull(index.getLexicon());

        assertNotNull(index.getInvertedIndex());

        assertNotNull(index.getMetaIndex());
        
        assertEquals(11429, index.getCollectionStatistics().getNumberOfDocuments());

        LexiconEntry le = index.getLexicon().getLexiconEntry("news");
        assertNotNull(le);
        assertEquals(2, le.getDocumentFrequency());
        assertEquals(2, le.getFrequency());
        //unlikely to be present
        assertEquals(1, le.getMaxFrequencyInDocuments());
        
        IterablePosting ip = index.getInvertedIndex().getPostings(le);
        assertNotNull(ip);
        
        assertEquals(NEWS_DOCIDS[0], ip.next());
        assertEquals(NEWS_DOCIDS[0], ip.getId());
        assertEquals(NEWS_DOCIDS[1], ip.next());
        assertEquals(NEWS_DOCIDS[1], ip.getId());
        assertEquals(IterablePosting.EOL, ip.next());
        index.close();

        assertEquals("1", index.getMetaIndex().getItem("docno", 0));
    }
}