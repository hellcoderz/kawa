package kawa.lang;
import gnu.mapping.*;
import gnu.expr.*;
import gnu.lists.*;
import gnu.bytecode.Type;
import gnu.kawa.lispexpr.LangObjType;
import gnu.kawa.lispexpr.LispLanguage;
import java.util.ArrayList;
import kawa.standard.object;

/**
 * The Syntax transformer that re-writes the lambda builtin.
 * @author	Per Bothner
 */

public class Lambda extends Syntax
{

    public boolean handlePatterns;

  public Object optionalKeyword;
  public Object restKeyword;
  public Object keyKeyword;

  public static final Keyword nameKeyword = Keyword.make("name");

  // This should technically have the type of Scheme.booleanType.
  public Expression defaultDefault = QuoteExp.falseExp;

  public void setKeywords(Object optional, Object rest, Object key)
  {
    optionalKeyword = optional;
    restKeyword = rest;
    keyKeyword = key;
  }

  @Override
  public Expression rewriteForm (Pair form, Translator tr)
  {
    Expression exp = rewrite(form.getCdr(), tr);
    Translator.setLine(exp, form);
    return exp;
  }

  @Override
  public Expression rewrite (Object obj, Translator tr)
  {
    if (! (obj instanceof Pair))
      return tr.syntaxError ("missing formals in lambda");
    int old_errors = tr.getMessages().getErrorCount();
    LambdaExp lexp = new LambdaExp();
    Pair pair = (Pair) obj;
    Translator.setLine(lexp, pair);
    rewrite(lexp, pair.getCar(), pair.getCdr(), tr, null);
    if (tr.getMessages().getErrorCount() > old_errors)
      return new ErrorExp("bad lambda expression");
    return lexp;
  }

  /**
   * Higher-level constructor, that does the re-writing.
   * @param formals the formal parameter list (or symbol)
   * @param body the body of the procedure
   * @param tr the (Scheme) Translator
   */
  public void rewrite(LambdaExp lexp, Object formals, Object body,
		      Translator tr, TemplateScope templateScopeRest)
  {
    lexp.setCallConvention(tr);
    rewriteFormals(lexp, formals, tr, templateScopeRest);
    if (body instanceof PairWithPosition)
      lexp.setFile(((PairWithPosition) body).getFileName());
    body = rewriteAttrs(lexp, body, tr);
    rewriteBody(lexp, body, tr);
  }

  public void rewriteFormals(LambdaExp lexp, Object formals,
		      Translator tr, TemplateScope templateScopeRest)
  {
    if (handlePatterns)
        tr.pushScope(lexp);
    if (lexp.getSymbol() == null)
      {
        String filename = lexp.getFileName();
        int line = lexp.getLineNumber();
        if (filename != null && line > 0)
          lexp.setSourceLocation(filename, line);
      }
    Object bindings = formals;
    int opt_args = -1;
    int rest_args = -1;
    int key_args = -1;
    Pair pair;
    bindings = formals;
    opt_args = -1;
    key_args = -1;
    ArrayList<Expression> defaultArgs = null;
    ArrayList<Keyword> keywords = null;
    Object mode = null;
    Object next = null;
    for (; ;  bindings = next)
      {
	if (bindings instanceof SyntaxForm)
	  {
	    SyntaxForm sf = (SyntaxForm) bindings;
	    bindings = sf.getDatum();
	    // The SyntaxForm "surrounds" both the current binding (the car),
	    // as well as the cdr - i.e. the remaining bindings.
	    templateScopeRest = sf.getScope();
	  }
	if (! (bindings instanceof Pair))
	  break;
	TemplateScope templateScope = templateScopeRest;
	pair = (Pair) bindings;
	Object pair_car = pair.getCar();
        next = pair.getCdr();
	if (pair_car instanceof SyntaxForm)
	  {
	    SyntaxForm sf = (SyntaxForm) pair_car;
	    pair_car = sf.getDatum();
	    templateScope = sf.getScope();
	  }
	if (pair_car == optionalKeyword)
	  {
	    if (opt_args >= 0)
              tr.syntaxError ("multiple "+optionalKeyword+" keywords in parameter list");
	    else if (rest_args >= 0 || key_args >= 0)
              tr.syntaxError (optionalKeyword.toString()+" after " + restKeyword + " or " + keyKeyword);
	    opt_args = 0;
	  }
	else if (pair_car == restKeyword)
	  {
	    if (rest_args >= 0)
              tr.syntaxError ("multiple " + restKeyword
                              + " keywords in parameter list");
	    else if (key_args >= 0)
              tr.syntaxError (restKeyword.toString()
                              + " after " + keyKeyword);
	    rest_args = 0;
	  }
	else if (pair_car == keyKeyword)
	  {
	    if (key_args >= 0)
              tr.syntaxError ("multiple " + keyKeyword
                              + " keywords in parameter list");
	    key_args = 0;
	  }
	else if (key_args >= 0)
	  key_args++;
	else if (rest_args >= 0)
	  rest_args++;
	else if (opt_args >= 0)
	  opt_args++;
	else
	  lexp.min_args++;
	if (pair_car == optionalKeyword
	    || pair_car == restKeyword || pair_car == keyKeyword)
	  {
	    mode = pair_car;
	    continue;
	  }
	Object savePos = tr.pushPositionOf(pair);
	Object name = null;
	Object defaultValue = defaultDefault;
	Pair typeSpecPair = null;
        Pair p;
	if (tr.matches(pair_car, "::"))
	  {
	    tr.syntaxError("'::' must follow parameter name");
            break;
	  }
        pair_car = tr.namespaceResolve(pair_car);
	if (pair_car instanceof Symbol
            && ! (handlePatterns && mode == null))
          {
            name = pair_car;
            if (pair.getCdr() instanceof Pair
                && tr.matches((p = (Pair) pair.getCdr()).getCar(), "::"))
              {
                if (! (p.getCdr() instanceof Pair))
                  {
                    tr.syntaxError("'::' not followed by a type specifier"
                                   + " (for parameter '" + name + "')");
                    bindings = LList.Empty;
                    break;
                  }
                p = (Pair) p.getCdr();
                typeSpecPair = p;
                pair = p;
                next = pair.getCdr();
              }
          }
	else if (pair_car instanceof Pair
                 && ! (handlePatterns && mode == null))
	  {
	    p = (Pair) pair_car;
	    pair_car = p.getCar();
	    if (pair_car instanceof SyntaxForm)
	      {
		SyntaxForm sf = (SyntaxForm) pair_car;
		pair_car = sf.getDatum();
		templateScope = sf.getScope();
	      }
            pair_car = tr.namespaceResolve(pair_car);
	    if (pair_car instanceof Symbol
		&& p.getCdr() instanceof Pair)
	      {
		name = pair_car;
		p = (Pair) p.getCdr();
		if (tr.matches(p.getCar(), "::"))
		  {
		    if (! (p.getCdr() instanceof Pair))
		      {
			tr.syntaxError("'::' not followed by a type specifier"
				       + " (for parameter '" + name + "')");
                        break;
		      }
		    p = (Pair) p.getCdr();
		    typeSpecPair = p;
		    if (p.getCdr() instanceof Pair)
		      p = (Pair) p.getCdr();
		    else if (p.getCdr() == LList.Empty)
		      p = null;
		    else
		      {
			tr.syntaxError("improper list in specifier for parameter '"
				       + name + "')");
                        break;
		      }
		  }
		if (p != null && mode != null)
		  {
		    defaultValue = p.getCar();
		    if (p.getCdr() instanceof Pair)
		      p = (Pair) p.getCdr();
		    else if (p.getCdr() == LList.Empty)
		      p = null;
		    else
		      {
			tr.syntaxError("improper list in specifier for parameter '"
				       + name + "')");
                        break;
		      }
		  }
		if (p != null)
		  {
		    if (typeSpecPair != null)
		      {
			tr.syntaxError("duplicate type specifier for parameter '"
				       + name + '\'');
                        break;
		      }
		    typeSpecPair = p;
		    if (p.getCdr() != LList.Empty)
		      {
			tr.syntaxError("junk at end of specifier for parameter '"
				       + name + '\''+" after type "+p.getCar());
			break;
		      }
                    tr.error('w', "deprecated type specifier syntax - use (VAR :: TYPE) rather than (VAR TYPE)");
		  }
	      }
	  }
        Declaration decl;
        if (handlePatterns && mode == null) {
            p = (Pair) pair;
            pair_car = p.getCar();
            boolean extraParens = false;
            if (pair_car instanceof Pair) {
                Object pair_car_cdr = ((Pair) pair_car).getCdr();
                if (pair_car_cdr instanceof Pair
                    && tr.matches(((Pair) pair_car_cdr).getCar(), "::"))
                    extraParens = true;
            }
            Object[] r = BindDecls.instance.parsePatternCar(extraParens ? (Pair) pair_car : p, 0, lexp, tr);
            if (! extraParens)
                next = r[0];
            else if (r[0] != LList.Empty) {
                Object savePos1 = tr.pushPositionOf(r[0]);
                tr.syntaxError("junk at end of specifier for parameter");
                tr.popPositionOf(savePos1);
            }
            decl = (Declaration) r[1];
            if (decl == null)
                decl = new Declaration("<error>");
            name = decl == null ? null : decl.getSymbol();
        } else {
            if (name == null) {
                tr.syntaxError ("parameter is neither name nor (name :: type) nor (name default)"+": "+pair);
                break;
            }
            decl = new Declaration(name);
        }
        decl.setFlag(Declaration.IS_PARAMETER);
	if (mode == optionalKeyword || mode == keyKeyword)
          {
            decl.setInitValue(new LangExp(defaultValue));
            if (mode == keyKeyword)
              {
                if (keywords == null)
                  keywords = new ArrayList<Keyword>();
              keywords.add(Keyword.make(name instanceof Symbol ? ((Symbol) name).getName() : name.toString()));
              }
          }
	Translator.setLine(decl, bindings);
	if (typeSpecPair != null)
	  {
            decl.setType(new LangExp(typeSpecPair), null);
	    decl.setFlag(Declaration.TYPE_SPECIFIED);
	  }
        if (mode == restKeyword)
	  {
	    decl.setFlag(Declaration.IS_REST_PARAMETER);
	    if (! decl.getFlag(Declaration.TYPE_SPECIFIED))
	      decl.setType(LangObjType.listType);
	  }
        decl.setFlag(Declaration.IS_SINGLE_VALUE);
        if (! handlePatterns || mode != null)
            addParam(decl, templateScope, lexp, tr);
	tr.popPositionOf(savePos);
      }
    if (bindings instanceof SyntaxForm)
      {
	SyntaxForm sf = (SyntaxForm) bindings;
	bindings = sf.getDatum();
	templateScopeRest = sf.getScope();
      }
    if (bindings instanceof Symbol)
      {
	if (opt_args >= 0 || key_args >= 0 || rest_args >= 0)
	  {
	    tr.syntaxError ("dotted rest-arg after " + optionalKeyword
                            +", " + restKeyword + ", or " + keyKeyword);
	  }
        else
          {
            rest_args = 1;
            Declaration decl = new Declaration(bindings);
            decl.setType(LangObjType.listType);
            decl.setFlag(Declaration.IS_SINGLE_VALUE
                         |Declaration.IS_PARAMETER
                         |Declaration.IS_REST_PARAMETER);
            decl.noteValueUnknown();
            addParam(decl, templateScopeRest, lexp, tr);
          }
      }
    else if (bindings != LList.Empty)
      {
	tr.syntaxError ("misformed formals in lambda");
      }
    if (rest_args > 1)
      {
	tr.syntaxError ("multiple " + restKeyword + " parameters");
        rest_args = 1;
      }
    if (opt_args < 0)
      opt_args = 0;
    if (rest_args < 0)
      rest_args = 0;
    if (key_args < 0)
      key_args = 0;
    if (rest_args > 0)
      lexp.max_args = -1;
    else   // Is this useful?
      lexp.max_args = lexp.min_args + opt_args + 2 * key_args;
    lexp.opt_args = opt_args;
    if (keywords != null)
      lexp.keywords = keywords.toArray(new Keyword[keywords.size()]);
  }

  protected void addParam (Declaration decl, ScopeExp templateScope,
                           LambdaExp lexp, Translator tr)
  {
    if (templateScope != null)
      decl = tr.makeRenamedAlias(decl, templateScope);
    lexp.addDeclaration(decl);
    if (templateScope != null)
      decl.context = templateScope;
    if (handlePatterns)
        tr.push(decl);
  }

  public Object rewriteAttrs(LambdaExp lexp, Object body, Translator tr)
  {
    String allocationFlagName = null;
    long accessFlag = 0;
    int allocationFlag = 0;
    SyntaxForm syntax0 = null;
    for (;;)
      {
	while (body instanceof SyntaxForm)
	  {
	    syntax0 = (SyntaxForm) body;
	    body = syntax0.getDatum();
	  }
	if (! (body instanceof Pair))
	  break;
	Pair pair1 = (Pair) body;
	Object attrName = Translator.stripSyntax(pair1.getCar());
	if (tr.matches(attrName, "::"))
	  attrName = null;
        else if (attrName instanceof Pair
                 && isAnnotationSymbol(((Pair)attrName).getCar()))
          {
            if (lexp.nameDecl == null)
              tr.error('e', "annotation for anonymous function");
            else
              lexp.nameDecl.addAnnotation(new LangExp(pair1));
            body = pair1.getCdr();
            continue;
          }
	else if (! (attrName instanceof Keyword))
	  break;

        SyntaxForm syntax1 = syntax0;
	Object pair1_cdr = pair1.getCdr();
	while (pair1_cdr instanceof SyntaxForm)
	  {
	    syntax1 = (SyntaxForm) pair1_cdr;
	    pair1_cdr = syntax1.getDatum();
	  }
	if (! (pair1_cdr instanceof Pair))
	  break;
	Pair pair2 = (Pair) pair1_cdr;

	Object attrValue;
	if (attrName == null)
	  {
            if (lexp.isClassMethod() && "*init*".equals(lexp.getName()))
              tr.error('e', "explicit return type for '*init*' method");
            else
              // Defer rewrite until rewriteBody.
              lexp.body = new LangExp(new Object[] { pair2, syntax1 });
	  }
	else if (attrName == kawa.standard.object.accessKeyword)
	  {
            accessFlag = object.addAccessFlags(pair2.getCar(),
                                               accessFlag,
                                               Declaration.METHOD_ACCESS_FLAGS,
                                               "method", tr);
	  }
	else if (attrName == kawa.standard.object.allocationKeyword)
	  {
	    Expression attrExpr = tr.rewrite_car(pair2, syntax1);
	    if (! (attrExpr instanceof QuoteExp)
		|| ! ((attrValue = ((QuoteExp) attrExpr).getValue()) instanceof SimpleSymbol
                      /* #ifdef use:java.lang.CharSequence */
		      || attrValue instanceof CharSequence
                      /* #else */
		      // || attrValue instanceof String
                      // || attrValue instanceof CharSeq
                      /* #endif */
		      ))
	      tr.error('e', "allocation: value not a constant symbol or string");
	    else if (lexp.nameDecl == null)
	      tr.error('e', "allocation: not allowed for anonymous function");
	    else
	      {
		String value = attrValue.toString();
		if ("class".equals(value) || "static".equals(value))
		  allocationFlag = Declaration.STATIC_SPECIFIED;
		else if ("instance".equals(value))
		  allocationFlag = Declaration.NONSTATIC_SPECIFIED;
		else
		  tr.error('e', "unknown allocation specifier");
		if (allocationFlagName != null && value != null)
		  {
		    tr.error('e', "duplicate allocation specifiers - "
			     + allocationFlagName + " and "
			     + value);
		  }
		allocationFlagName = value;
	      }
	  }
	else if (attrName == kawa.standard.object.throwsKeyword)
	  {
	    attrValue = pair2.getCar();
	    int count = Translator.listLength(attrValue);
	    if (count < 0)
	      tr.error('e', "throws: not followed by a list");
	    else
	      {
		Expression[] exps = new Expression[count];
		SyntaxForm syntax2 = syntax1;
		for (int i = 0;  i < count; i++)
		  {
		    while (attrValue instanceof SyntaxForm)
		      {
			syntax2 = (SyntaxForm) attrValue;
			attrValue = syntax2.getDatum();
		      }
		    Pair pair3 = (Pair) attrValue;
		    exps[i] = tr.rewrite_car(pair3, syntax2);
                    // Error-checking is done later.
                    Translator.setLine(exps[i], pair3);
		    attrValue = pair3.getCdr(); 
		  }
		lexp.setExceptions(exps);
	      }
	  }
        else if (attrName == nameKeyword)
          {
            Expression attrExpr = tr.rewrite_car(pair2, syntax1);
            if (attrExpr instanceof QuoteExp)
              lexp.setName(((QuoteExp) attrExpr).getValue().toString());
          }
	else
	  {
            Expression attrExpr = tr.rewrite_car(pair2, syntax1);
            attrName = ((Keyword) attrName).asSymbol();
            lexp.setProperty(attrName, attrExpr);
	  }
	body = pair2.getCdr();
      }
    accessFlag |= allocationFlag;
    if (accessFlag != 0)
      lexp.nameDecl.setFlag(accessFlag);
    if (syntax0 != null)
      body = SyntaxForms.fromDatumIfNeeded(body, syntax0);
    return body;
  }

  public Object skipAttrs(LambdaExp lexp, Object body, Translator tr)
  {
    while (body instanceof Pair)
      {
	Pair pair = (Pair) body;
	if (! (pair.getCdr() instanceof Pair))
	  break;
	Object attrName = pair.getCar();
	if (tr.matches(attrName, "::"))
	  attrName = null;
	else if (! (attrName instanceof Keyword))
	  break;
	body = ((Pair) pair.getCdr()).getCdr();
      }
    return body;
  }

  public void rewriteBody(LambdaExp lexp, Object body, Translator tr)
  {
    int numRenamedAlias = 0;
    // We view a top-level named function as a method, in the sense that the
    // form (this) is allowed, if the supertype is explicitly specified.
    if (tr.curMethodLambda == null
        && lexp.nameDecl != null
        && tr.getModule().getFlag(ModuleExp.SUPERTYPE_SPECIFIED))
      tr.curMethodLambda = lexp;
    ScopeExp curs = tr.currentScope();
    if (! handlePatterns)
        tr.pushScope(lexp);
    if (lexp.nameDecl != null)
      rewriteAnnotations(lexp.nameDecl, tr);
    Declaration prev = null;
    int key_args = lexp.keywords == null ? 0 : lexp.keywords.length;
    int opt_args = lexp.opt_args;
    int arg_i = 0;
    for (Declaration cur = lexp.firstDecl(); cur != null; cur = cur.nextDecl())
      {
	if (cur.isAlias())
	  {
	    Declaration param = Translator.getOriginalRef(cur).getBinding();
	    lexp.replaceFollowing(prev, param);
	    param.context = lexp;
	    tr.pushRenamedAlias(cur);
	    numRenamedAlias++;
	    cur = param;
	  }
        Expression texp = cur.getTypeExpRaw();
        if (texp instanceof LangExp)
          {
            Pair typeSpecPair = (Pair) ((LangExp) texp).getLangValue(); 
            Type t = tr.exp2Type(typeSpecPair, cur, null/*FIXME*/);
            if (t != null)
                cur.setType(t);
          }
	prev = cur;

        if (cur.getFlag(Declaration.IS_PARAMETER)) {

        if (arg_i >= lexp.min_args
            && (arg_i < lexp.min_args + opt_args
                || lexp.max_args >= 0
                || arg_i != lexp.min_args + opt_args))
          {
            cur.setInitValue(tr.rewrite(cur.getInitValue()));
          }
        arg_i++;
        }

        if (! handlePatterns)
            tr.lexical.push(cur);
      }

    if (lexp.isClassMethod()
        && ! lexp.nameDecl.getFlag(Declaration.STATIC_SPECIFIED))
      {
        // We set the type of this in ClassExp.walkChildren.
        lexp.add(null, new Declaration(ThisExp.THIS_NAME));
      }

    LambdaExp saveLambda = tr.curLambda;
    tr.curLambda = lexp;
    Type rtype = lexp.returnType;
    Object[] tform = lexp.body instanceof LangExp
        ? (Object[]) ((LangExp) lexp.body).getLangValue()
        : null;
    lexp.body = auxillaryRewrite(body, tr);
    tr.curLambda = saveLambda;
    Expression[] exps;
    int len;
    Object val;
    if (tform != null)
      {
        Expression texp = tr.rewrite_car((Pair) tform[0],
                                         (SyntaxForm) tform[1]);
        lexp.setCoercedReturnValue(texp, tr.getLanguage());
      }
    else if (lexp.body instanceof BeginExp
        && body instanceof Pair
        && ((Pair) body).getCar() instanceof Symbol
        && (len = (exps = ((BeginExp) lexp.body).getExpressions()).length) > 1
        && (exps[0] instanceof ReferenceExp
            || ((val = exps[0].valueIfConstant()) instanceof Type
                || val instanceof Class)))
      {
	// Handle '<TYPENAME> BODY':
        tr.error('w', "deprecated return-type specifier - use '::TYPE'");
        Expression rexp = exps[0];
        len--;
        if (len == 1)
          lexp.body = exps[1];
        else
          {
            Expression[] new_body = new Expression[len];
            System.arraycopy(exps, 1, new_body, 0, len);
            lexp.body = BeginExp.canonicalize(new_body);
          }
        lexp.setCoercedReturnValue(rexp, tr.getLanguage());
      }
    else
      lexp.setCoercedReturnType(rtype);
    tr.pop(lexp);
    lexp.countDecls();
    tr.popRenamedAlias(numRenamedAlias);
    lexp.countDecls();
    if (tr.curMethodLambda == lexp)
      tr.curMethodLambda = null;
  }
  
  public Expression auxillaryRewrite(Object body, Translator tr)
  {
    return tr.rewrite_body(body);
  }

  @Override
  public void print (Consumer out)
  {
    out.write("#<builtin lambda>");
  }

  public static boolean isAnnotationSymbol (Object key)
  {
    if (key instanceof Pair)
      {
        Pair keyp = (Pair) key;
        if (keyp.getCar() == LispLanguage.splice_sym)
          return true;
      }
    if (key instanceof SimpleSymbol) // Deprecated: Symbol starting with '@'
      {
        String name = ((SimpleSymbol) key).getName();
        if (name.length() > 1 && name.charAt(0) == '@')
          return true;
      }
    return false;
  }

  public static void rewriteAnnotations (Declaration decl, Translator tr)
  {
    int n = decl.numAnnotations();
    for (int i = 0;  i < n;  i++)
      {
        Expression ann = decl.getAnnotation(i);
        if (ann instanceof LangExp)
          {
            ann = tr.rewrite_car((Pair) ((LangExp) ann).getLangValue(), false);
            decl.setAnnotation(i, ann);
          }
      }
  }
}
