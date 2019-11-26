package org.terrier.lucene;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

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

    public static final String PREFIX = "lucene:";
    public static final String DIRECTPREFIX = "directlucene:";
    

    final static Map<String,Class<? extends LuceneIndex>> PREFIX2IMPL = 
        ImmutableMap.<String,Class<? extends LuceneIndex>> builder()
            .put(PREFIX, LuceneIndex.class)
            .put(DIRECTPREFIX, DirectLuceneIndex.class)
            .build();

    @Override
    public boolean supports(IndexRef ref) {
        boolean rtr = PREFIX2IMPL.keySet().stream().anyMatch(p -> ref.toString().startsWith(p));
        //System.err.println("ref supported by " + this.getClass() + " " + rtr);
        return rtr;
    }

    @Override
    public Index load(IndexRef ref) {
        try{
            Map.Entry<String,Class<? extends LuceneIndex>> selKVEntry = 
                PREFIX2IMPL.entrySet().stream().filter(kv -> ref.toString().startsWith(kv.getKey())).findFirst().get();
            String dirname = ref.toString().replace(selKVEntry.getKey(), "");
            return loadLuceneIndex(dirname, selKVEntry.getValue());
        } catch (Exception e) {
            System.err.println("Could not loadLuceneIndex: " + e);
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Class<? extends Index> indexImplementor(IndexRef ref) {
       return MultiIndex.class;
    }

    static Index loadLuceneIndex(String dirname, Class<? extends LuceneIndex> clz) throws Exception
    {
        SimpleFSDirectory dir = new SimpleFSDirectory(Paths.get(dirname));
        CompositeReader cir = DirectoryReader.open(dir);
        List<LuceneIndex> indices = new ArrayList<>();
        boolean blocks = cir.leaves().get(0).reader().getFieldInfos().hasProx();
        for(LeafReaderContext lrc : cir.leaves())
        {
            LeafReader lr = lrc.reader();
            indices.add(clz.getConstructor(LeafReader.class).newInstance(lr));
                //equiv to new LuceneIndex(lr));
        }
        System.err.println("Lucene index has " + indices.size() + " segments (leaves)");
        if (indices.size() > 1)
        {
            System.err.println("using multiindex");
            return new MultiIndex(indices.toArray(new Index[0]), blocks, false);
        }
        return indices.get(0);
    } 

}