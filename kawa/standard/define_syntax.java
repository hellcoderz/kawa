// Copyright (C) 2005 Per M.A. Bothner.
// This is free software;  for terms and warranty disclaimer see ../../COPYING.

package kawa.standard;
import kawa.lang.*;
import gnu.expr.*;
import gnu.bytecode.ClassType;
import gnu.mapping.*;
import gnu.lists.*;

public class define_syntax extends Syntax
{
    int flags;

    public static final define_syntax define_macro
        = new define_syntax("%define-macro", false);

    public static final define_syntax define_syntax
        = new define_syntax("%define-syntax", true);

    public static final define_syntax define_rewrite_syntax
        = new define_syntax("define-rewrite-syntax", Macro.HYGIENIC|Macro.SKIP_SCAN_FORM);

    public define_syntax() {
        flags = Macro.HYGIENIC;
    }

    public define_syntax(Object name, int flags) {
        super(name);
        this.flags = flags;
    }

    public define_syntax(Object name, boolean hygienic) {
        this(name, hygienic ? Macro.HYGIENIC : 0);
    }

    static ClassType typeMacro = ClassType.make("kawa.lang.Macro");
    static PrimProcedure makeHygienic
        = new PrimProcedure(typeMacro.getDeclaredMethod("make", 3));
    static PrimProcedure makeNonHygienic
        = new PrimProcedure(typeMacro.getDeclaredMethod("makeNonHygienic", 3));
    static PrimProcedure makeSkipScanForm
        = new PrimProcedure(typeMacro.getDeclaredMethod("makeSkipScanForm", 3));
    static PrimProcedure setCapturedScope
        = new PrimProcedure(typeMacro.getDeclaredMethod("setCapturedScope", 1));
    static {
        makeHygienic.setSideEffectFree();
        makeNonHygienic.setSideEffectFree();
        makeSkipScanForm.setSideEffectFree();
    }

    public void scanForm(Pair st, ScopeExp defs, Translator tr) {
        SyntaxForm syntax = null;
        Object st_cdr = st.getCdr();
        while (st_cdr instanceof SyntaxForm) {
            syntax = (SyntaxForm) st_cdr;
            st_cdr = syntax.getDatum();
        }
        Object p = st_cdr;
        Object name;
        if (p instanceof Pair) {
            Pair pp = (Pair) p;
            name = pp.getCar();
            p = pp.getCdr();
        }
        else
            name = null;
        SyntaxForm nameSyntax = syntax;
        while (name instanceof SyntaxForm) {
            nameSyntax = (SyntaxForm) name;
            name = nameSyntax.getDatum();
        }
        name = tr.namespaceResolve(name);
        if (! (name instanceof Symbol)) {
            tr.pushForm(tr.syntaxError("missing macro name for "+Translator.safeCar(st)));
            return;
        }
        if (p == null || Translator.safeCdr(p) != LList.Empty) {
            tr.pushForm(tr.syntaxError("invalid syntax for "+getName()));
            return;
        }

        Declaration decl = tr.define(name, nameSyntax, defs);
        decl.setType(typeMacro); 
        tr.push(decl);

        Macro savedMacro = tr.currentMacroDefinition;
        Macro macro = Macro.make(decl);
        macro.setFlags(flags);
        Expression rule;
        ScopeExp scope = syntax != null ? syntax.getScope() : tr.currentScope();
        rule = new LangExp(new Object[]{ p, tr, scope });
        macro.expander = rule;
        // A top-level macro needs (in general) to be compiled into the
        // class-file, but for a non-top-level macro it is better to use
        // the quoted macro directly to get the right nesting, as we
        // do for letrec-syntax.
        rule = new QuoteExp(macro);
        decl.noteValue(rule);
        decl.setProcedureDecl(true);
  
        if (decl.context instanceof ModuleExp) {
            SetExp result = new SetExp (decl, rule);
            result.setDefining (true);
            if (tr.getLanguage().hasSeparateFunctionNamespace())
                result.setFuncDef(true);
            Object ret = Translator.makePair(st, this,
                                         Translator.makePair(st, result, LList.Empty));
            tr.pushForm(ret);

            if (tr.immediate) {
                Expression[] args =
                    { new ReferenceExp(decl), new QuoteExp(defs) };
                tr.pushForm(new ApplyExp(setCapturedScope, args));
            }
        } else {
            macro.rewriteIfNeeded();
        }
    }

    public Expression rewriteForm(Pair form, Translator tr) {
        if (form instanceof Pair) {
            Pair p1 = (Pair) form.getCdr();
            Object x1 = p1.getCar();
            if (x1 instanceof SetExp) {
                SetExp sexp = (SetExp) x1;
                Object val = sexp.getNewValue().valueIfConstant();
                Declaration decl = sexp.getBinding();
                Object name = decl.getSymbol();
                ScopeExp defs = decl.getContext();
                if (val instanceof Macro) {
                    Macro macro = (Macro) val;
                    macro.rewriteIfNeeded();
                    Expression rule = (Expression) macro.expander;
                    Procedure makeMacroProc =
                        (flags & Macro.SKIP_SCAN_FORM) != 0 ? makeSkipScanForm
                        : (flags & Macro.HYGIENIC) != 0 ? makeHygienic
                        : makeNonHygienic;
                    if (defs instanceof ModuleExp)
                        rule = new ApplyExp(makeMacroProc,
                                            new QuoteExp(name),
                                            rule,
                                            ThisExp.makeGivingContext(defs));
                    sexp.setNewValue(rule);
                    decl.setValue(rule);
                }
                return (SetExp) x1;
            }
        }
        return tr.syntaxError("define-syntax not in a body");
    }
}
