(ns user
  "Scratch for the development REPL: rig nrepl -p dev.

  When the app grows a component system, wire the reloaded workflow to it:

    (require '[reloaded.repl :as reloaded])
    (reloaded/set-init! #'start-system)
    (reloaded/go)     ;; start, or reload changed namespaces and restart
    (reloaded/stop)
    (reloaded/reset)  ;; stop, unload every namespace, start again
  ")
