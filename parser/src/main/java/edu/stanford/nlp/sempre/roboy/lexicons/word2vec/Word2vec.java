package edu.stanford.nlp.sempre.roboy.lexicons.word2vec;

import edu.stanford.nlp.sempre.roboy.config.ConfigManager;
import edu.stanford.nlp.sempre.roboy.utils.NLULoggerController;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.util.*;


/**
 * Neural net that processes text into word-vectors.
 *
 * Adapted from org.deeplearning4j.examples.nlp.word2vec.Word2VecRawTextExample
 */
public class Word2vec {
    private final ToyDataGetter dataGetter;
    private String modelPath;
    private final Word2Vec vec;
    private final WeightLookupTable weightLookupTable;
    private final Iterator<INDArray> vectors;
    private double threshold;

    public Word2vec() throws Exception {
        threshold = ConfigManager.W2V_THRES;

        // Get all files paths
        // LogController.logs("Loading files...");
        this.dataGetter = new ToyDataGetter(false);
        // LogController.logs("Loading files...");
        this.dataGetter.ensureToyModelIsPresent();
        // LogController.logs("Loading files...");
        this.modelPath = this.dataGetter.getToyModelFilePath();

        // Load model
        // LogController.logs("Loading models...");
        File gModel = new File(modelPath);
        this.vec = WordVectorSerializer.readWord2VecModel(gModel);

        // Weight tables
        // LogController.logs("Weight tables...");
        this.weightLookupTable = this.vec.lookupTable();
        this.vectors = weightLookupTable.vectors();

        NLULoggerController.begin_track("Tests -> ");
        NLULoggerController.logs("Closest words: %s", this.getClosest("queen",10));
        NLULoggerController.logs("Closest word to female from : \"women\",\"queen\",\"elisabeth\" -> %s", this.getBest("female", Arrays.asList("women","queen","elisabeth")));
        NLULoggerController.logs("Closest word to swimming from : \"literature\",\"activity\",\"sports\" -> : %s",this.getBest("swimming", Arrays.asList("literature","activity","sports")));
        NLULoggerController.end_track();
    }

    public void setThreshold(double t){
        threshold = t;
    }

    public Collection<String> getClosest(String word, int number){
        return this.vec.wordsNearest(word, number);
    }

    public double getSimilarity(String arg1, String arg2){
        return this.vec.similarity(arg1, arg2);
    }

    public List<String> getBest(String arg1, List<String> list_words){
        List<String> best = new ArrayList<String>();
        for (String word: list_words){
            if (threshold < this.vec.similarity(arg1, word)){
                best.add(word);
            }
        }
        return best;
    }

    public INDArray getMatrix(String word){
        return this.vec.getWordVectorMatrix(word);
    }

    public double[] getWordVector(String word){
        return this.vec.getWordVector(word);
    }

    public static void main(String[] args) throws Exception {
        try{
            Word2vec vec = new Word2vec();
            if (ConfigManager.DEBUG > 3) {
                NLULoggerController.logs("Tests -> ");
                vec.setThreshold(0);
                NLULoggerController.logs("Closest words: %s", vec.getClosest("queen", 10).toString());
                NLULoggerController.logs("Closest word to queen from : " +
                        "\"people\",\"activities\",\"politics\",\"culture\" -> %s",
                        vec.getBest("queen",
                                Arrays.asList("people", "activities", "politics", "culture")).toString());
                NLULoggerController.logs("Closest word to swimming from : \"literature\",\"music\",\"sports\" -> : %s",
                        vec.getBest("swimming",
                                Arrays.asList("literature", "music", "sports")).toString());
            }
        } catch(Exception e){
            NLULoggerController.errors("Exception in Word2Vec: %s", e.getMessage());
        }

    }

}
