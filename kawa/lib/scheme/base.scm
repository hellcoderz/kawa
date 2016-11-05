(module-name (scheme base))

(require kawa.lib.bytevectors)
(require kawa.lib.case_syntax)
(require kawa.lib.characters)
(require kawa.lib.exceptions)
(require kawa.lib.lists)
(require kawa.lib.misc)
(require kawa.lib.numbers)
(require kawa.lib.parameters)
(require kawa.lib.parameterize)
(require kawa.lib.ports)
(require kawa.lib.prim_syntax)
(require kawa.lib.strings)
(require kawa.lib.std_syntax)
(require kawa.lib.syntax)
(require kawa.lib.vectors)
(require kawa.lib.misc_syntax) ;; FIXME only for deprecated include
(require kawa.lib.DefineRecordType)
(require kawa.lib.prim_imports)
(import (only (kawa lib prim_syntax) (letrec letrec*)))
(include "base-exports")