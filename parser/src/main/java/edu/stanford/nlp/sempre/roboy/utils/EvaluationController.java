package edu.stanford.nlp.sempre.roboy.utils;

import fig.basic.*;

public class EvaluationController extends Evaluation{
    public void logStats(String prefix) {
        if(NLULoggerController.isALL())super.logStats(prefix);
    }

}
