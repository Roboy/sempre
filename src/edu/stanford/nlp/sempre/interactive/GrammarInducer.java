package edu.stanford.nlp.sempre.interactive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.ImmutableList;

import edu.stanford.nlp.sempre.ConstantFn;
import edu.stanford.nlp.sempre.Derivation;
import edu.stanford.nlp.sempre.DerivationStream;
import edu.stanford.nlp.sempre.Example;
import edu.stanford.nlp.sempre.Formula;
import edu.stanford.nlp.sempre.Formulas;
import edu.stanford.nlp.sempre.IdentityFn;
import edu.stanford.nlp.sempre.LambdaFormula;
import edu.stanford.nlp.sempre.Rule;
import edu.stanford.nlp.sempre.SemanticFn;
import edu.stanford.nlp.sempre.VariableFormula;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.StopWatchSet;

/**
 * Takes two examples, and induce some Rules
 *
 * @author Sida Wang
 */

public class GrammarInducer {
  public static class Options {
    @Option(gloss = "categories that can serve as rules")
    public Set<String> filteredCats = new HashSet<String>();
    public int maxRulesPerExample = 3;
  }

  public static Options opts = new Options();

  public static enum DefStatus {
    Cover, // some (including all) covers in the definiendum is accounted for
    NoCover, // cover is empty after checking with definition, so nothing would generalize
    NoParse, // definition does not parse, should we look for partials?
  }

  // this depends on the chart!
  public static enum ParseStatus {
    Nothing, // nothing at all parses in the utterance
    Float, // something parse
    Induced, // redefining known utterance
    Core; // define known utterance in core, should reject
  }

  public DefStatus defStatus;
  public ParseStatus parseStatus;

  Map<String, List<Derivation>>[][] chart;
  List<Rule> inducedRules;
  int numTokens;
  List<String> tokens;
  String id;
  Derivation defderiv;
  List<Derivation> chartList = Lists.newArrayList();

  // induce rule is possible,
  // otherwise set the correct status
  public GrammarInducer(Example origEx, Derivation def,  List<Derivation> chartList) {
    id = origEx.id;
    
    this.chartList = chartList;
    
    LogInfo.logs("got %d in chartList", this.chartList.size());
    numTokens = origEx.numTokens();
    tokens = origEx.getTokens();
    
    inducedRules = new ArrayList<>();

    addMatches(def);
    buildFormula(def);

    inducedRules.addAll(induceRules(def));
  }
  
  // label the derivation tree with what it matches in chartList
  private void addMatches(Derivation deriv) {
    for (Derivation anchored : chartList) {
      if (deriv.formula.equals(anchored.formula) && deriv.cat.equals(anchored.cat)) {
        deriv.grammarInfo.matches.add(anchored);
        LogInfo.dbgs("Replaced %s <- %s", anchored, uniqueCoverName(anchored));
        deriv.grammarInfo.formula = new VariableFormula(uniqueCoverName(anchored));
        break; // just adding the first match, could do highest scoring
      }
    }
    for (Derivation d : deriv.children) {
      addMatches(d);
    }
  }
  
  // covers need to be ordered
  private void collectCovers(Derivation deriv, List<Derivation> covers) {
    if (deriv.grammarInfo.matches.size() > 0) {
      if(!covers.contains(deriv.grammarInfo.matches.get(0)))
        covers.add(deriv.grammarInfo.matches.get(0));
    } else {
      for (Derivation d : deriv.children) {
        collectCovers(d, covers);
      }
    }
  }
  
  public List<Rule> getRules() {
    return inducedRules;
  }
  
  private List<Rule> induceRules(Derivation defDeriv) {
    List<Derivation> covers = Lists.newArrayList();
    collectCovers(defDeriv, covers);
    covers.sort((s,t) -> s.start < t.start? -1: 1);
    LogInfo.dbgs("covers: %s", covers);
    List<Rule> inducedRules = new ArrayList<>();
    List<String> RHS = getRHS(defDeriv, covers);
    SemanticFn sem = getSemantics(defDeriv, covers);
    String cat = getTopCat(defDeriv);
    Rule inducedRule = new Rule(cat, RHS, sem);
    inducedRule.addInfo(id, 1.0);
    inducedRule.addInfo("induced", 1.0);
    if (!inducedRule.isCatUnary()) {
      inducedRules.add(inducedRule);
    }
    return inducedRules;
  }

  private String getTopCat(Derivation def) {
    return def.getCat();
  }

  // populate grammarInfo.formula, replacing everything that can be replaced
  private void buildFormula(Derivation deriv){
    //LogInfo.log("building " + deriv.grammarInfo.formula);
    if (deriv.grammarInfo.formula != null) return;
    if (deriv.grammarInfo.formula instanceof VariableFormula) return;
    if (deriv.children.size() == 0) {
      deriv.grammarInfo.formula = deriv.formula;
    }
    
    for (Derivation c : deriv.children) {
      buildFormula(c);
    }
    Rule rule = deriv.rule;
    List<Derivation> args = deriv.children;
    
    // cant use the standard thing because formula is final mostly
    if (rule.sem instanceof ApplyFn) {
      Formula f = Formulas.fromLispTree(((ApplyFn)rule.sem).formula.toLispTree());
      for (Derivation arg : args) {
        if (!(f instanceof LambdaFormula))
          throw new RuntimeException("Expected LambdaFormula, but got " + f);
        f = Formulas.lambdaApply((LambdaFormula)f, arg.grammarInfo.formula);
      }
      deriv.grammarInfo.formula = f;
    } else if (rule.sem instanceof IdentityFn) {
      deriv.grammarInfo.formula = args.get(0).grammarInfo.formula;
    } else {
      deriv.grammarInfo.formula = deriv.formula;
    }

    //LogInfo.log("built " + deriv.grammarInfo.formula);
  }
  
  private String uniqueCoverName(Derivation anchored) {
    return anchored.cat + anchored.start + "_" + anchored.end;
  }
  
  private SemanticFn getSemantics(final Derivation def, List<Derivation> covers) {
    Formula baseFormula = def.grammarInfo.formula;
    if (covers.size() == 0) {
      SemanticFn constantFn = new ConstantFn();
      LispTree newTree = LispTree.proto.newList();
      newTree.addChild("ConstantFn");
      newTree.addChild(baseFormula.toLispTree());
      constantFn.init(newTree);
      return constantFn;
    }
    
    for (int i = covers.size() -1; i >= 0; i--) {
      baseFormula = new LambdaFormula( uniqueCoverName(covers.get(i)), Formulas.fromLispTree(baseFormula.toLispTree()));
    }
    SemanticFn applyFn = new ApplyFn();
    LispTree newTree = LispTree.proto.newList();
    newTree.addChild("interactive.ApplyFn");
    newTree.addChild(baseFormula.toLispTree());
    applyFn.init(newTree);
    return applyFn;
  }

  private List<String> getRHS(Derivation def, List<Derivation> covers) {
    List<String> rhs = new ArrayList<>(tokens);
    int start = 0;
    for (Derivation deriv : covers) {
      // LogInfo.logs("got (%d,%d):%s:%s", deriv.start, deriv.end, deriv.formula, deriv.cat);
      rhs.set(deriv.start, deriv.cat);
      for (int i = deriv.start + 1; i<deriv.end; i++) {
        rhs.set(i, null);
      }
    }
    return rhs.stream().filter(s -> s!=null).collect(Collectors.toList());
  }

  public static ParseStatus getParseStatus(Example ex) {
    if (ex.predDerivations.size() > 0) {
      for (Derivation deriv : ex.predDerivations) {
        if (deriv.allAnchored()) {
          return ParseStatus.Core;
        }
      }
      return ParseStatus.Induced;
    }
    // could check the chart here set partial, but no need for now
    return ParseStatus.Nothing;
  }

}
