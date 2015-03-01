;;   Copyright (c) 2012 Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns penumbra.app
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer (pprint)]
            [clojure.walk :refer (postwalk-replace)]
            [com.stuartsierra.component :as component]
            #_[penumbra.time :as time]
            [penumbra.app
             [controller :as controller]
             [core :as app]
             [event :as event]
             [loop :as loop]
             [queue :as queue]
             [window :as window]]
            [penumbra.opengl
             [context :as context]
             [slate :as slate]]
            [penumbra.opengl :refer :all]
            [penumbra.opengl.core :refer :all]
            [penumbra.utils :refer (defmacro-) :as util]
            [schema.core :as s])
  (:import [clojure.lang Atom]
           [org.lwjgl.glfw GLFW GLFWErrorCallback]
           [penumbra.app.controller Controller]
           [penumbra.app.event EventHandler]
           [penumbra.app.queue ActionQueue]
           [penumbra.app.window Window]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;; Q: What are these, really?
(def time-delta {:delta Float
                 :time Float})
(def position-2d {:x s/Int, :y s/Int})
(def rect (assoc position-2d :width s/Int :height s/Int))
(def delta-2d {:d position-2d
               :p position-2d})

;;; Q: How does Schema handle multiple arities?
;;; A: Doesn't matter here
;;; This is based on the initial docstring for (start)
(def callback-map {(s/optional-key :close) (s/=> s/Any Window util/state)  ;  ???
                   (s/optional-key :display) (s/=> s/Any Window time-delta util/state)
                   ;; TODO: This should go away. I think.
                   (s/optional-key :init) (s/=> util/state util/state)
                   ;; Key and mouse events realistically need to move into the channel-map
                   ;; Or maybe something more specific, like an input-channel-map
                   ;; TODO: Figure out how to make that work correctly
                   (s/optional-key :key-press) (s/=> util/state s/Int util/state)
                   (s/optional-key :key-release) (s/=> util/state s/Int util/state)
                   (s/optional-key :key-type) (s/=> util/state s/Int util/state)
                   (s/optional-key :mouse-click) (s/=> util/state position-2d s/Int util/state)
                   (s/optional-key :mouse-down) (s/=> util/state position-2d s/Int util/state)
                   (s/optional-key :mouse-up) (s/=> util/state position-2d s/Int util/state)
                   (s/optional-key :mouse-drag) (s/=> util/state delta-2d s/Int util/state)
                   (s/optional-key :mouse-move) (s/=> util/state delta-2d util/state)
                   ;; This seems like it should also feed through a channel
                   (s/optional-key :reshape) (s/=> s/Any Window rect util/state)
                   ;; TODO: Mark this obsolete
                   (s/optional-key :update) (s/=> util/state time-delta util/state)
                   ;; TODO: Switch everything to use these instead.
                   ;; Except that the basic premise is shaky...
                   ;; Games need an event loop for updating their world periodically.
                   ;; Especially the physics parts. And animations.
                   ;; That's sort-of what these are for, but it probably doesn't matter much
                   ;; whether they're visible or not.
                   ;; They're either based on "real" time deltas or arbitrary game ticks,
                   ;; and this part needs to be totally independent of anything related to
                   ;; drawing.
                   #_(comment
                     (s/optional-key :update-hidden) (s/=> util/state time-delta util/state)
                     (s/optional-key :update-visible) (s/=> util/state time-delta util/state))})

(def stage {:title s/Str
            :state util/state
            :callbacks callback-map
            :channels util/channel-map})

(declare start-single-thread)
(s/defrecord App
    [callbacks :- callback-map
     channels :- util/input-channel-map
     clock
     controller :- Controller
     done :- util/promise-class
     event-handler :- EventHandler
     main-loop
     parent
     queue :- ActionQueue
     ;; TODO: These next two need to move elsewhere
     state :- Atom
     state-stack :- Atom
     threading
     window :- Window]
  clojure.lang.IDeref
  (deref
   [_]
   ;; Doc strings aren't really legal
   "Q: Do we really want to do this?"
   @state)

  component/Lifecycle
  (start
   [this]
   (println "Starting the app")
   ;; Seems like it would be silly to start with an initial
   ;; stack, but it seems sillier to not allow for that
   ;; possibility
   (let [initial-state-stack (or state-stack (atom nil))
         stateful (assoc this :state-stack initial-state-stack)]
     (assoc stateful :main-loop (start-single-thread stateful loop/basic-loop))))
  (stop
   [this]
   (when event-handler
     (event/publish! event-handler :close))
   (when controller
     (controller/stop! controller))
   ;; It's tempting to leave this in place if it isn't
   ;; empty, but that seems to violate a huge part of the
   ;; point to the Component architecture
   (into this {:controller nil
               :state-stack (atom nil)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(s/defn init!
  "This seems like it should be subsumed by Components.
  But there's at least one subtlety:
  the actual event loop does something I'm not clear about
  where it seems to pause the clock (probably when the clock
  actually gets paused), calls this, then resumes that clock.

  So leave this in place until I get a grasp on what's
  actually happening.

  Except that I've eliminated most of these. So that seems
  like a poor approach.
  New plan: dig into the original to try to figure out what
  was/is going on around that event loop."
  [this :- App]
  (window/init! (:window this))
  (input/init! (:input this))
  (queue/init! (:queue this))
  (controller/init! (:controller this))
  (event/publish! app :init))

(defmethod print-method App [app writer]
  (.write writer (str "#orangelet " (into {} app))))
(defmethod print-dup App [app writer]
  (.write writer (str "#orangelet " (into {} app))))
(.addMethod clojure.pprint/simple-dispatch App
            (fn [x]
              (print-method x *out*)))

;; Q: What's this for? How does it interact w/ name-with-attributes?
;; A: It seems to be a helper to auto-extend.
;; It looks like it's all about taking the function signatures associated
;; with an instance the App contains (based upon protocols) so the
;; auto-extend macro below can automatically extend that instance.
;; Which happens to be exactly what the docstring for that macro
;; has said all along.
(defn- transform-extend-arglists [protocol name arglists template]
  (list*
   `fn
   (map
    (fn [args]
      (let [template (postwalk-replace {'this (first args)} template)]
        (list
         (vec args)
         (list*
          (intern (symbol (namespace protocol)) name)
          template
          (next args)))))
    arglists)))

(defmacro- auto-extend
  "Classic Container pattern

  If an instance contains another instance that implements a protocol,
  the container might as well implement that same protocol by calling the
  instance it contains

  Original docstring:
  Lets the application, which contains an implementation of a protocol, automatically extend that protocol.

  My current approach trashes this pretty thoroughly, which really seems
  like a shame. It's something I've frequently wished I could do back when
  I lived in OOP land.

  Q: So...what happens when an object implements several protocols at once?"
  [type protocol template & explicit]
  (let [protocol (eval protocol)
        sigs (eval `(vals (:sigs ~protocol)))]
    (list
     `extend
     type
     protocol
     (merge
      (->> (map
            (fn [{name :name arglists :arglists}]
              [(keyword name)
               (transform-extend-arglists protocol name arglists template)])
            sigs)
           (apply concat)
           (apply hash-map))
      (apply hash-map explicit)))))

;;;

(defn- update-
  "Updates the state of the application."
  [app state f args]
  ;; TODO: Still need to remember to pprint App.
  ;; state here *must* be an Atom.
  (comment (println "Running update on app: " app "\nState: " state "\nfn: " f "\nArgs: " args))
  (swap! state
         (fn [state]
           (if-let [state* (if (empty? args)
                             (f state)
                             (apply f (concat args [state])))]
             (do (controller/invalidated! app true) state*)
             state))))

(defn- alter-callbacks
  "Updates the update and display callbacks using timed-fn"
  [clock callbacks]
  (let [wrap (partial loop/timed-fn clock)
        callbacks (if (:update callbacks)
                    (update-in callbacks [:update] wrap)
                    callbacks)
        callbacks (if (:display callbacks)
                    (update-in callbacks [:display] wrap)
                    callbacks)]
    callbacks))

(defmethod print-method penumbra.app.App [app ^java.io.Writer writer]
  (.write writer "App"))

(comment (defn create
  "Creates an application."
  [callbacks state]
  ;; This is breaking compilation from the wiki clock2.clj example.
  (throw (ex-info "obsolete" {:problem "Use Components instead"}))
  ;; I'm guessing that this next check allows nested idempotent creation
  (if (instance? App callbacks)
    callbacks
    (do
      (let [window (atom nil)
            input (atom nil)
            queue (atom nil)
            event (event/create)
            clock (time/clock)
            ;; Q: Why would you do this?
            ;; Am I missing something basic?
            ;; A: Verdict seems to be "yes." Not
            ;; doing it causes a ClassCastException because I'm trying
            ;; to cast a Map to an Atom.
            state-atom (atom state)
            controller (controller/create)
            app (comment (App. state-atom clock event queue window input controller app/*app*))]
        (let [top (get state :top 0)
              left (get state :left 0)
              width (get state :width 800)
              height (get state :height 600)
              resizable (get state :fluid false)]
          (println "Creating a window @ " left top width height resizable)
          (println "State: " state)
          (reset! window (window/create-window app left top width height resizable)))
        (comment (reset! input (input/create app)))
        (reset! queue (queue/create app))
        (doseq [[event f] (alter-callbacks clock callbacks)]
          (comment (println "Adding a callback for " event))
          (if (= event :display)
            (event/subscribe! app :display (fn [& args] (f @state-atom)))
            (event/subscribe! app event (fn [& args] (update- app state-atom f args)))))
        (println "App created")
        app)))))

(defn- transform-import-arglists [protocol name doc arglists]
  (list*
   `defn name
   doc
   (map
    (fn [args]
      (list
       (vec (next args))
       (list*
        (intern (symbol (namespace protocol)) name)
        'penumbra.app.core/*app*
        (next args))))
    arglists)))

(defmacro- auto-import
  "Creates a function which automatically fills in app with *app*"
  [protocol & imports]
  (let [protocol (eval protocol)
        sigs (eval `(vals (:sigs ~protocol)))]
    (list*
     'do
     (map
      (fn [{name :name arglists :arglists doc :doc}]
        (transform-import-arglists protocol name doc arglists))
      (let [imports (set imports)]
        (filter #(imports (:name %)) sigs))))))

(defn clock
  "Returns the application clock."
  ([]
   (throw (ex-info "obsolete" {:problem "No more dynamic vars"}))
   (comment (clock app/*app*)))
  ([app] (:clock app)))

(s/defn now :- double
  "Returns the elapsed clock time, in seconds, since GLFW was initialized, unless
  you've called glfwSetTime.
  Resolution is system-dependent. Typically it's in nanoseconds or microseconds."
  []
  (GLFW/glfwGetTime))

(comment (defn speed!
  "Sets the application clock speed."
  ([speed] (speed! app/*app* speed))
  ([app speed] (time/speed! (clock app) speed))))

(s/defn periodic-update!
  "Starts a recurring update, which is called 'hz' times a second.

   OpenGL calls cannot be made within this callback."
  [app :- App
   hz :- s/Int
   f :- (s/=> s/Any s/Any)]
  (queue/periodic-enqueue! app hz #(update- app (:state app) f nil)))

(s/defn delay!
  "Enqueues an update to be executed in 'delay' milliseconds.

   OpenGL calls cannot be made within this callback."
  [app :- App
   delay :- s/Int
   f] (queue/enqueue! app clock delay #(update- app (:state app) f nil)))

(defn update!
  "Enqueues an update to happen immediately.

   OpenGL calls cannot be made within the callback."
  [app f] (delay! (clock app) 0 f))

(s/defn enqueue!
  "Enqueues an update to happen before the next frame is rendered.

   OpenGL calls in this callback are okay."
  [app :- App
   ;; TODO: Figure out the method signature
   ;; Looking at update-, it seems to be a straight
   ;; State => State transformation
   ;; Which is going to be specific to each App
   f]
  ;; This is going to blow up w/out app extending the EventHandler protocol
  ;; TODO: Figure out a better approach. First idea is to move this into
  ;; that namespace
  (event/subscribe-once! app :enqueue #(update- app (:state app) f nil)))

(defn repaint!
  "Forces the application to repaint."
  [app]
  ;; As far as I can tell, there's a glfwRefresh handler that will almost
  ;; never get called on modern window managers.
  ;; This sort of thing makes sense for apps which are mostly static...
  ;; but that doesn't really fit with this library at all.
  ;; Then again, this is perfectly in keeping with more traditional apps,
  ;; like, say, clocks that only need to update once a second or forms
  ;; where data is being entered.
  ;; It might be a very friendly optimization to the rest of the system
  ;; to check for the dirty flag at the top of the rendering loop of
  ;; visible windows and only do the drawing if it's set.
  ;; It's definitely something to consider and tinker with.
  (controller/invalidated! app true))

(defn frequency! [hz]
     "Updates the update frequency of a periodic update.

   This can only be called from within the periodic callback.  A frequency of 0 or less will halt the update."
     (throw (RuntimeException. "Obsolete"))
     (reset! app/*hz* hz))

;;;

(defmacro with-gl
  "Creates a valid OpenGL context within the scope.

  TODO: This seems like it should probably go away"
  [& body]
  `(slate/with-slate
     (context/with-context nil
       ~@body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; App state

(defn once-through-single-threaded-event-loop
  "Does everything in one pass."
  ([app]
   (GLFW/glfwPollEvents)

   (if (and (window/visible? (:window app))
            ;; Don't want to redraw the window if nothing's changed
            ;; TODO: Find something equivalent to window/invalidated?
            (or
             ((some-fn #_window/invalidated? controller/invalidated?) app)
             true))
     (do
       (doto app
         (event/publish! :enqueue)
         (event/publish! :update)
         (controller/invalidated! false))
       ;; N.B.: push-matrix is pretty much totally obsolete
       (push-matrix
        (clear)
        (event/publish! app :display))
       ;; Q: What's this for?
       ;; Preliminary guess: to interrupt flow so that event has
       ;; a chance to propagate
       (Thread/sleep 1)
       (window/update! app))
     (Thread/sleep 1))
   (if (window/close? app)
     (controller/stop! app :requested-by-user))))

(defn start-single-thread
  "This is being called from start. With an App instance and penumbra.app.loop/basic-loop"
  [app loop-fn]
  ;; Instead, set this up during component/start
  (println "Kicking off a single thread")
  (let [outer-fn (fn [looper-fn]
                   ;; This gets called from the initial loop-fn
                   ;; parameter. It sets up
                   ;; the app's environment and calls looper-fn,
                   ;; which is really just an error-protecting wrapper
                   ;; that loops over inner-fn until it exits
                   (doto app
                     (app/speed! 0)
                     ;; This next line looks like it should have been
                     ;; invoking a bunch of the original auto-extend
                     ;; magic, but it isn't doing anything of the sort.
                     ;; It was originally just a function that called
                     ;; the (init!) of each of the important members of
                     ;; the App instance in turn.
                     ;; Q: Do I want this to happen outside the basic
                     ;; Component Lifecycle?
                     init!
                     (app/speed! 1))
                   (looper-fn)
                   (app/speed! app 0))
        ;; Q: Why is this a partial?
        ;; A: It's being called in penumbra.app.loop/basic-loop with
        ;; no args.
        inner-fn (partial once-through-single-threaded-event-loop app)
        event-thread (async/thread (context/with-context nil
                                     ;; Can't pprint app. It's both an IDeref and an IPersistentMap.
                                     ;; And this doesn't seem worth preferring one over the other.
                                     ;; More importantly: this isn't actually happening
                                     ;; in the background (when it gets called from a REPL, 
                                     ;; control doesn't return until it exits.
                                     ;; FIXME: What am I understanding incorrectly?
                                     (println "Entering a window loop in a background thread\nApp:" app)
                                     (try
                                       (loop-fn
                                        (:controller app)
                                        outer-fn
                                        inner-fn)
                                 (catch RuntimeException ex
                                   ;; TODO: More error-handling info!
                                   (let [stack (.getStackTrace ex)
                                         frames (map #(str % "\n") stack)]
                                     (println "Unhandled exception from the drawing loop:\n" ex
                                              map identity frames))))
                               (println "Cleaning up after main loop. app:" app)
                               (when (controller/stopped? (:controller app))
                                 ;; Q: What's going on here?
                                 (app/destroy! app))))]
    (assoc app :event-loop event-thread)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Obsolete, at least in their current forms

(defn pause!
  [app]
  (controller/pause! (:controller app)))

(defn speed!
  [app speed]
  ;; This really seems like something to do at
  ;; a higher level.
  ;; It's easy to mix up the distinctions between what might be
  ;; happening in a full-blown App vs. what's happening in that
  ;; App's individual windows.
  ;; This needs some hammock time.
  (comment (time/speed! (:clock app) speed))
  (throw (ex-info "Need to think about this"
                  {:not-implemented "It would be very useful to be able to dynamically alter speed at the REPL to see what's going on"})))

(defn now
  [app]
  @(:clock app))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn title!
  "This doesn't really belong here.
  But it is convenient, and it matches the way
  the current API works."
  [app :- App
   s :- s/Str]
  (window/title! (:window app) s))

(defn callback-
  [app event args]
  ((-> app :callbacks event) args))

(comment (defn start
  "Starts a window from scratch, or from a closed state.
  Supported callbacks are:
  :close          [state]
  :display        [[delta time] state]
  :init           [state]
  :key-press      [key state]
  :key-release    [key state]
  :key-type       [key state]
  :mouse-click    [[x y] button state]
  :mouse-down     [[x y] button state]
  :mouse-drag     [[[dx dy] [x y]] button state]
  :mouse-move     [[[dx dy] [x y]] state]
  :mouse-up       [[x y] button state]
  :reshape        [[x y width height] state]
  :update         [[delta time] state]
"
  ([callbacks]
   (start callbacks {}))
  ([callbacks state]
   (throw (ex-info "Trying to do this creates circular dependencies" {:obsolete "Use Components instead"}))
   (comment (let [default-system (system/init {:callbacks callbacks, :main-loop loop/basic-loop, :state state, :threading :single})]
              (component/start default-system)))
   (println "Returning from graphics thread creation"))))

(s/defn push-state!
  "Push a new 'state' onto a stack to switch what's being drawn"
  [app :- App
   updated-state]
  (swap! (:state-stack app) (fn [current]
                              (concat (list updated-state) current))))

(s/defn pop-state!
  "Remove the current state from the App's state-stack frame and return it"
  [app :- App]
  (let [current-stack-atom (:state-stack app)
        current-state (first @current-stack-atom)]
    (swap! current-stack-atom #(drop 1 %))
    ;; TODO: If there's no current state left, should exit.
    ;; Or something along those lines
    current-state))

(defn ctor
  [defaults]
  (map->App defaults))

(s/defn create-stage
  "Build a 'stage' for Apps to play on
   Or something like that. I'm still wrestling with terminology.

   This should be idempotent. At least in theory. Except that, at
   the very least, we're creating a Window. Definitely don't want
   *that* happening inside a transaction."
  ([{:keys [title initial-state callbacks channels]}]
   (create-stage (util/random-uuid) title initial-state callbacks channels))
  ([title :- s/Str
    initial-state :- util/state
    callbacks :- callback-map
    channels :- util/input-channel-map]
   (create-stage (util/random-uuid) title initial-state callbacks channels))
  ([id :- s/Uuid
    title :- s/Str
    initial-state :- util/state
    callbacks :- callback-map
    channels :- util/channel-map]
   (let [base
         (component/system-map
          :controller (controller/ctor {})
          :orangelet (ctor {:callbacks callbacks
                            :channels channels
                            :state (atom initial-state)})
          :window (window/ctor {:title title}))
         dependencies {:orangelet [:controller :window]}]
     (println "Setting up System map, using baseline\n"
              (into {} base))
     (with-meta (component/system-using base
                                        dependencies)
       dependencies))))

