package edu.stanford.nlp.sempre.roboy.score;

import com.google.gson.Gson;
import edu.stanford.nlp.sempre.ContextValue;
import edu.stanford.nlp.sempre.ErrorInfo;
import edu.stanford.nlp.sempre.roboy.lexicons.word2vec.Word2vec;

import java.util.*;

/**
 * ContextScore creates a score based on context fitting
 *
 * @author emlozin
 */
public class ContextScore implements ScoringFunction {
    public static Properties prop = new Properties();   /**< Read properties */
    public static Gson gson = new Gson();               /**< Gson object */

    private final Word2vec vec;                         /**< Word2Vec handler */

    public Map<String, Map<String, Double>> current_score;   /**< Current calculated scores */

    /**
     * A constructor.
     * Initializes Word2Vec needed to calculate scores
     */
    public ContextScore(Word2vec vec){
        current_score = new HashMap<>();
        this.vec = vec;
    }

    /**
     * Scoring function.
     * Takes ErrorInfo as well as ContextValue objects and calculates score of each
     * candidate for unknown terms.
     */
    public Map<String, Map<String, Double>> score(ErrorInfo errorInfo, ContextValue context){
        // Clear current score
        current_score.clear();
        // Check for all unknown terms
        for (String key: errorInfo.getCandidates().keySet()){
            Map<String, Double> key_scores = new HashMap<>();
            List<String> candidates = errorInfo.getCandidates().get(key);
            // Check for all candidates for checked unknown term
            for (String candidate: candidates){
                double max = 0;
                // Check for all keywords
                for (String keyword: context.exchanges.get(context.exchanges.size() - 1).keywords) {
                    double score = this.vec.getSimilarity(keyword, candidate);
                    if (!Double.isNaN(score) && score > max)
                        max = score;
                }
                key_scores.put(candidate,max);
            }
            current_score.put(key,key_scores);
        }
        return current_score;
    }

}