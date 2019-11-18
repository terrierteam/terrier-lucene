package org.terrier.lucene;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.CompositeReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.SimpleFSDirectory;
import org.terrier.querying.IndexRef;
import org.terrier.realtime.multi.MultiIndex;
import org.terrier.structures.Index;
import org.terrier.structures.IndexFactory.IndexLoader;

public class LuceneIndexFactory implements IndexLoader {
//TODO declare this in the services file

    final static String PREFIX = "lucene:";

    @Override
    public boolean supports(IndexRef ref) {
        return ref.toString().startsWith(PREFIX);
    }

    @Override
    public Index load(IndexRef ref) {
        try{
            String dirname = ref.toString().replace(PREFIX, "");
            return loadLuceneIndex(dirname);
        } catch (IOException ioe) {
            return null;
        }
    }

    @Override
    public Class<? extends Index> indexImplementor(IndexRef ref) {
       return MultiIndex.class;
    }

    static Index loadLuceneIndex(String dirname) throws IOException
    {
        
        SimpleFSDirectory dir = new SimpleFSDirectory(Paths.get(dirname));
        CompositeReader cir = DirectoryReader.open(dir);
        List<LuceneIndex> indices = new ArrayList<>();
        for(LeafReaderContext lrc : cir.leaves())
        {
            LeafReader lr = lrc.reader();
            indices.add(new LuceneIndex(lr));
        }
        System.err.println("Lucene index has " + indices.size() + " segments (leaves)");
        if (indices.size() > 1)
        {
            System.err.println("using multiindex");
            return new MultiIndex(indices.toArray(new Index[0]));
        }
        return indices.get(0);
    } 

}