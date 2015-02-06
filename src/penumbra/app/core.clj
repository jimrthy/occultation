;;   Copyright 2012 (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app.core)

;; TODO: These next 2 dynamic vars need to go away
(comment (def ^:dynamic *app*
  "Current application."
  nil))

;; TODO: Refactor this into app.queue
;; TODO: Make this go away completely
(def ^:dynamic *hz*
  "Refresh rate of update-loop"
  nil)

;;;

(defprotocol App
  (init! [app])
  (destroy! [app])
  (speed! [app speed])
  (now [app]))
;; Should probably just break down and do something like
(comment (.addMethod pprint/simple-dispatch App pprint-App))
;; But that involves knowing about pprint in here (which
;; seems pretty reasonable) and implementing the pretty
;; print function (which I'd rather not get into just now,
;; though it's probably purty simple)

;; Actually, this basic idea is pretty tempting.
;; Create a system here and now.
;; Wipe it away when the associated windows exit.
;; Except that the caller must have created that system and is
;; passing it in, so this is really a Common Lisp approach
;; that doesn't seem to match well with functional programming
(comment (defmacro with-app [app & body]
           `(binding [*app* ~app]
              ~@body)))
