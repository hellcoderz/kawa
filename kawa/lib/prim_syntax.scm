;; Definitions for some primitives before we define anything else.

(module-export
 define-syntax define define-private define-constant define-early-constant
 define-variable report-syntax-error syntax->expression syntax-body->expression
 if try-catch letrec)

(import (only (kawa standard define) (defineRaw %define)))
(import (only (kawa standard define_syntax) (define_syntax %define-syntax)))
(import (only (kawa standard let) (let %let)))
(import (only (kawa standard set_b) (set set!)))
(import (only (kawa standard begin) begin))
(import (only (kawa standard SchemeCompilation) mlambda))

(%define-syntax define-syntax
  (syntax-rules ($lookup$)
    ((define-syntax (($lookup$ part1 'part2) . pattern) . forms)
     ;; Should deprecate - incompatible with SRFI-72
     (%define-syntax ($lookup$ part1 'part2) (lambda pattern . forms)))
    ((define-syntax ($lookup$ part1 'part2) function)
     (%define-syntax ($lookup$ part1 'part2) function))
    ((define-syntax (name . pattern) . forms)
     ;; Should deprecate - incompatible with SRFI-72
     (%define-syntax name (lambda pattern . forms)))
    ((define-syntax name function)
     (%define-syntax name function))))

(%define-syntax define
  (syntax-rules (:: $lookup$)
    ((define ($lookup$ part1 'part2) :: type value)
     (%define ($lookup$ part1 'part2) 1 type value))
    ((define ($lookup$ part1 'part2) value)
     (%define ($lookup$ part1 'part2) 0 #!null value))
    ((define (name . formals) . body)
     (%define name 2 #t formals . body))
    ((define name :: type value)
     (%define name 1 type value))
    ((define name value)
     (%define name 0 #!null value))))

(%define-syntax define-private
  (syntax-rules (:: $lookup$)
    ((define-private ($lookup$ part1 'part2) :: type value)
     (%define ($lookup$ part1 'part2) 5 type value))
    ((define-private ($lookup$ part1 'part2) value)
     (%define ($lookup$ part1 'part2) 4 #!null value))
    ((define-private (name . formals) . body)
     (%define name 6 #t formals . body))
    ((define-private name :: type value)
     (%define name 5 type value))
    ((define-private name value)
     (%define name 4 #!null value))))

(%define-syntax define-constant
  (syntax-rules (:: $lookup$)
    ((define-constant ($lookup$ part1 'part2) :: type value)
     (%define ($lookup$ part1 'part2) 9 type value))
    ((define-constant ($lookup$ part1 'part2) value)
     (%define ($lookup$ part1 'part2) 8 #!null value))
    ((define-constant name :: type value)
     (%define name 9 type value))
    ((define-constant name value)
     (%define name 8 #!null value))))

(%define-syntax define-early-constant
  (syntax-rules (:: $lookup$)
    ((define-early-constant ($lookup$ part1 'part2) :: type value)
     (%define ($lookup$ part1 'part2) 25 type value))
    ((define-early-constant ($lookup$ part1 'part2) value)
     (%define ($lookup$ part1 'part2) 24 #!null value))
    ((define-early-constant name :: type value)
     (%define name 25 type value))
    ((define-early-constant name value)
     (%define name 24 #!null value))))

(%define-syntax define-variable
  (syntax-rules (:: $lookup$)
    ((define-variable name :: type)
     (define-variable name :: type #!undefined))
    ((define-variable name)
     (define-variable name #!undefined))
    ((define-variable ($lookup$ part1 'part2) :: type value)
     (%define ($lookup$ part1 'part2) 33 type value))
    ((define-variable ($lookup$ part1 'part2) value)
     (%define ($lookup$ part1 'part2) 32 #!null value))
    ((define-variable name :: type value)
     (%define name 33 type value))
    ((define-variable name value)
     (%define name 32 #!null value))))

(%define report-syntax-error 2 #!null (id #!rest (msg :: <Object[]>))
  (invoke-static <kawa.standard.syntax_error> 'error id msg))

(%define-syntax syntax->expression
  (syntax-rules ()
    ((syntax->expression x)
     (kawa.lang.SyntaxForms:rewrite x))))

(%define-syntax syntax-body->expression
  (syntax-rules ()
    ((syntax-body->expression x)
     (kawa.lang.SyntaxForms:rewriteBody x))))

(define-syntax %if-and-x
 (lambda (x)
   (syntax-case x (? :: and)
    ((_ k then)
     (gnu.expr.ExitExp (syntax->expression #'then) #'k))
    ((_ k then test1 . rest)
     #`(if test1
         (%if-and-x k then . rest))))))

(define-rewrite-syntax if
  (lambda (x)
    (syntax-case x (? :: and)
      ((_ (and . tests) then else)
       (%let ((bl (gnu.expr.BlockExp)))
             (bl:setRunFinallyBlocks #f)
             (bl:setBody
              (gnu.expr.BeginExp
               (syntax->expression #`(%if-and-x #,bl then . tests))
               (syntax->expression #`else)))
             bl))
      ((_ (? . rest) then)
       #'(if (? . rest) then #!void))
      ((_ (and . rest) then)
       #'(if (and . rest) then #!void))
      ((_ (? pattern :: type init) then else)
       #`(#,gnu.kawa.reflect.TypeSwitch:typeSwitch
          init
          (mlambda (pattern :: type) then)
          (lambda (unused) else)))
      ((_ (? pattern init) then else)
       #`(#,gnu.kawa.reflect.TypeSwitch:typeSwitch
          init
          (mlambda (pattern) then)
          (lambda (unused) else)))
      ((_ test then)
       (make <gnu.expr.IfExp>
         (syntax->expression (syntax test))
         (syntax->expression (syntax then))
         #!null))
      ((_ test then else)
       (make <gnu.expr.IfExp>
         (syntax->expression (syntax test))
         (syntax->expression (syntax then))
         (syntax->expression (syntax else))))
      ((_ e1 e2 e3 . rest)
       (report-syntax-error #'rest
                            "too many expressions for 'if'"))
      ((_ . rest)
       (report-syntax-error #'rest
                            "too few expressions for 'if'")))))

(define-rewrite-syntax try-catch
  (lambda (x)
    (syntax-case x ()
		 ((_ try-part (var type . catch-body) ...)
		  (invoke-static <kawa.standard.try_catch> 'rewrite
				 (syntax try-part)
				 (syntax
				  #((((var :: type)) . catch-body) ...)))))))

(%define-syntax letrec
  (lambda (form)
    (%let ((out-bindings '()) (out-inits '()))
      (syntax-case form ()
	((_ bindings . body)
         ;; Simulate a (letrec ((process-binding (lambda ...) ...)) ...)
         ;; which we can't use in here, because that is what we're defining.
	 (%let ((process-binding #!undefined))
	       (set! process-binding
		     (lambda (b)
		       (syntax-case b ()
			 (() #!void)
			 (((name init) . rest)
			  (begin
			    (set! out-bindings
				  (make <pair> (syntax (name #!undefined)) out-bindings))
			    (set! out-inits (make <pair> (syntax (set! name init)) out-inits))
			    (process-binding (syntax rest))))
			 (((name :: type init) . rest)
			  (begin
			    (set! out-bindings (make <pair> (syntax (name :: type #!undefined)) out-bindings))
			    (set! out-inits (make <pair> (syntax (set! name init)) out-inits))
			    (process-binding (syntax rest))))
			 (((name) . rest)
			  (report-syntax-error b "missing initializion in letrec"))
			 (whatever
			  (report-syntax-error b "invalid bindings syntax in letrec")))))
	       (process-binding (syntax bindings))
	       (set! out-bindings (gnu.lists.LList:reverseInPlace out-bindings))
	       (set! out-inits (gnu.lists.LList:reverseInPlace out-inits))
	       #`(%let #,out-bindings #,@out-inits . body)))))))
