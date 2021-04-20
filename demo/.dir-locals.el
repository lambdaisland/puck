((nil . ((cider-clojure-cli-global-options     . "-A:dev:test")
         (cider-custom-cljs-repl-init-form     . "(user/cljs-repl)")
         (cider-default-cljs-repl              . custom)
         (cider-preferred-build-tool           . clojure-cli)
         (cider-redirect-server-output-to-repl . t)
         (cider-repl-display-help-banner       . nil)
         (clojure-toplevel-inside-comment-form . t)
         (eval . (progn
                   (make-variable-buffer-local 'cider-jack-in-nrepl-middlewares)
                   (add-to-list 'cider-jack-in-nrepl-middlewares "shadow.cljs.devtools.server.nrepl/middleware")))
         (eval . (define-clojure-indent
                   (assoc 0)
                   (ex-info 0))))))
