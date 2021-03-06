package edu.stanford.nlp.sempre;

import fig.basic.LispTree;
/**
 * Takes two strings and returns their concatenation.
 *
 * @author Percy Liang
 */
public class ConcatFn extends SemanticFn {
  String delim;

  public ConcatFn() { }

  public ConcatFn(String delim) {
    this.delim = delim;
  }

  public void init(LispTree tree) {
    super.init(tree);
    delim = tree.child(1).value;
  }

  public DerivationStream call(Example ex, final Callable c) {
    return new SingleDerivationStream() {
      @Override
      public Derivation createDerivation() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < c.getChildren().size(); i++) {
          if (i > 0) out.append(delim);
          if (c.childStringValue(i) != null)
              out.append(c.childStringValue(i));
          else
              out.append(c.child(i).getFormula().toString());
        }
        return new Derivation.Builder()
                .withCallable(c)
                .withStringFormulaFrom(out.toString())
                .createDerivation();
      }
    };
  }
//  public DerivationStream call(Example ex, final Callable c) {
//    return new SingleDerivationStream() {
//      @Override
//      public Derivation createDerivation() {
//        StringBuilder out = new StringBuilder();
//        if (!delim.equals(",")) out.append("(");
//          for (int i = 0; i < c.getChildren().size(); i++) {
//            if (i > 0) {
//              if (delim.equals(" or ") || delim.equals(" and ")) out.append(")");
//              out.append(delim);
//              if (delim.equals(" or ") || delim.equals(" and ")) out.append("(");
//            }
//            if (c.childStringValue(i) != null)
//              out.append(c.childStringValue(i));
//            else
//              out.append(c.child(i).getFormula().toString());
//          }
//        if (!delim.equals(","))  out.append(")");
//        return new Derivation.Builder()
//                .withCallable(c)
//                .withStringFormulaFrom(out.toString())
//                .createDerivation();
//      }
//    };
//  }
}
